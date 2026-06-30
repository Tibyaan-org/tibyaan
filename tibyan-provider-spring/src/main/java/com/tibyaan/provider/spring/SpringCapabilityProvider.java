package com.tibyaan.provider.spring;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Type;
import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.plugin.SourceAnalyzer;
import com.tibyaan.provider.spring.extractors.EntityExtraction;
import com.tibyaan.provider.spring.extractors.EntityExtractor;
import com.tibyaan.provider.spring.extractors.ReadIntent;
import com.tibyaan.provider.spring.extractors.ServiceExtractor;
import com.tibyaan.provider.spring.extractors.ServiceMethod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A {@link SourceAnalyzer} that reads a Spring and Java project from source with JavaParser and
 * produces a Capability IR (Volume 5 section 4, ADR-0010).
 *
 * <p>It loads every {@code .java} file under the source root into an AST, then runs the two
 * enabled extractors and joins their results:
 *
 * <ul>
 *   <li>{@link EntityExtractor} reads the entities, their fields, and a binding-free read intent
 *       for each entity that has an identifier.
 *   <li>{@link ServiceExtractor} catalogs the public methods on every {@code @Service} class.
 * </ul>
 *
 * <p>The provider is the only place that joins the two: for each read intent it discovers the one
 * service method that backs it and emits the capability with that real binding (Volume 5
 * section 4.1). The extractors never depend on each other; the matching rule lives here. If a read
 * intent has no backing method, or more than one, the provider fails loudly rather than guess.
 *
 * <p>The provider produces IR and stops. It does not validate (that is the pipeline's and the
 * tests' job), generate output, or know that any backend or runtime exists.
 */
public final class SpringCapabilityProvider implements SourceAnalyzer {

    private static final String IR_VERSION = "0.1";

    private final EntityExtractor entityExtractor = new EntityExtractor();
    private final ServiceExtractor serviceExtractor = new ServiceExtractor();
    private final JavaTypeMapper typeMapper = new JavaTypeMapper();

    @Override
    public CapabilityModel provide(ProviderInput input) {
        Path sourceRoot = input.sourceRoot();
        if (!Files.isDirectory(sourceRoot)) {
            throw new ProviderException("source root is not a directory: " + sourceRoot);
        }

        List<CompilationUnit> units = parseJavaSources(sourceRoot);

        EntityExtraction entities = entityExtractor.extract(units);
        List<ServiceMethod> serviceMethods = serviceExtractor.discover(units);

        List<Capability> capabilities = new ArrayList<>();
        for (ReadIntent intent : entities.readIntents()) {
            capabilities.add(discoverCapability(intent, serviceMethods));
        }

        return new CapabilityModel(
                moduleName(sourceRoot), IR_VERSION, entities.entities(), capabilities, List.of(),
                Metadata.empty());
    }

    // The join (Volume 5 section 4.1): find the one @Service method whose shape backs this read,
    // and build the capability with its real binding. Zero or many matches fail loudly: the
    // binding is discovered, never assumed, so an unresolved or ambiguous read is an error here,
    // not a convention fallback.
    private Capability discoverCapability(ReadIntent intent, List<ServiceMethod> serviceMethods) {
        List<ServiceMethod> matches = new ArrayList<>();
        for (ServiceMethod method : serviceMethods) {
            if (backsRead(method, intent)) {
                matches.add(method);
            }
        }
        if (matches.isEmpty()) {
            throw new ProviderException(noBackingMethodMessage(intent, serviceMethods));
        }
        if (matches.size() > 1) {
            throw new ProviderException(ambiguousBindingMessage(intent, matches));
        }

        BindingRef binding = new BindingRef(matches.get(0).toBinding());
        Map<String, Type> inputs = new LinkedHashMap<>();
        inputs.put(intent.idInputName(), intent.idType());
        return Capability.read(
                intent.capabilityName(), inputs, Type.entityRef(intent.entityName()), binding);
    }

    // The matching rule, in shapes not names (Volume 5 section 4.1): the method returns the
    // entity, and takes exactly one parameter whose type maps to the entity's id type. The owning
    // class being a @Service and the method being public are already guaranteed by the catalog.
    private boolean backsRead(ServiceMethod method, ReadIntent intent) {
        if (!method.returnTypeSimpleName().equals(intent.entityName())) {
            return false;
        }
        if (method.parameterTypes().size() != 1) {
            return false;
        }
        Optional<Type> parameterType = typeMapper.map(method.parameterTypes().get(0));
        return parameterType.isPresent() && parameterType.get().equals(intent.idType());
    }

    private static String noBackingMethodMessage(ReadIntent intent, List<ServiceMethod> serviceMethods) {
        String seen = serviceMethods.isEmpty()
                ? "no @Service methods were found"
                : "the @Service methods found were "
                        + serviceMethods.stream().map(ServiceMethod::signature).toList();
        return "no service method backs read capability '" + intent.capabilityName()
                + "' for entity '" + intent.entityName() + "'. Looked for a public method on an "
                + "@Service class whose return type is '" + intent.entityName()
                + "' and whose single parameter maps to " + intent.idType().canonical() + "; "
                + seen + ". The binding is discovered, not assumed: add or expose a finder of that "
                + "shape, or this entity cannot be read.";
    }

    private static String ambiguousBindingMessage(ReadIntent intent, List<ServiceMethod> matches) {
        return "read capability '" + intent.capabilityName() + "' for entity '" + intent.entityName()
                + "' is backed by more than one service method: "
                + matches.stream().map(ServiceMethod::signature).toList()
                + ". The backing method must be unambiguous; the provider will not guess. "
                + "Disambiguate the service API so exactly one method has this shape.";
    }

    private static List<CompilationUnit> parseJavaSources(Path sourceRoot) {
        JavaParser parser = new JavaParser();
        List<CompilationUnit> units = new ArrayList<>();
        for (Path file : javaFilesUnder(sourceRoot)) {
            try (InputStream in = Files.newInputStream(file)) {
                ParseResult<CompilationUnit> result = parser.parse(in);
                if (!result.isSuccessful() || result.getResult().isEmpty()) {
                    throw new ProviderException("failed to parse " + file + ": " + result.getProblems());
                }
                units.add(result.getResult().get());
            } catch (IOException e) {
                throw new ProviderException("failed to read " + file, e);
            }
        }
        return units;
    }

    // Sorted for determinism: the same source tree always yields the same IR (Volume 5 rule 3).
    private static List<Path> javaFilesUnder(Path sourceRoot) {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> !hasSegment(path, "target"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new ProviderException("failed to walk sources under " + sourceRoot, e);
        }
    }

    private static boolean hasSegment(Path path, String segment) {
        for (Path part : path) {
            if (part.toString().equals(segment)) {
                return true;
            }
        }
        return false;
    }

    // The module name is the source root directory name, the system being modeled. For the
    // example app this is "legacy-spring-app", the equivalent of Volume 4's "inventory".
    private static String moduleName(Path sourceRoot) {
        Path name = sourceRoot.toAbsolutePath().normalize().getFileName();
        return name == null ? "module" : name.toString();
    }
}
