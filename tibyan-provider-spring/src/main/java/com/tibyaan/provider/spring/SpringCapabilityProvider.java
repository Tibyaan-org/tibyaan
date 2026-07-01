package com.tibyaan.provider.spring;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Type;
import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.plugin.SourceAnalyzer;
import com.tibyaan.provider.spring.EntityOutcome.Cause;
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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link SourceAnalyzer} that reads a Spring and Java project from source with JavaParser and
 * produces a Capability IR (Volume 5 section 4, ADR-0010).
 *
 * <p>Two entry points, for two audiences:
 *
 * <ul>
 *   <li>{@link #scan(ProviderInput)} is the resilient survey (Volume 5 section 4, phase E1). It
 *       walks the whole source, parses collect-and-continue, and classifies every entity it can
 *       parse as discovered or failed, listing every file it could not parse. It never aborts on a
 *       single bad file, so it can report what a large, messy, real app actually contains. This is
 *       what {@code tibyan scan} runs.
 *   <li>{@link #provide(ProviderInput)} is the strict plugin-api contract used to produce an
 *       artifact. It delegates to the survey and then refuses the whole scan on any hard failure or
 *       any unparseable file, returning a clean IR or throwing. This is what the build and
 *       {@code tibyan generate} run: you do not ship a manifest that silently missed a tool.
 * </ul>
 *
 * <p>Both share one discovery rule (Volume 5 section 4.1): a capability is emitted only when exactly
 * one {@code @Service} method has the read-by-id shape that backs an entity. Zero or many matches is
 * a located failure, never a guessed binding. Collect-and-continue changes only whether such a
 * failure is recorded and the scan continues, or aborts; it never makes the provider guess.
 */
public final class SpringCapabilityProvider implements SourceAnalyzer {

    private static final String IR_VERSION = "0.1";

    // Java 17 is the app's language level (Volume 16 section 3.1), so the parser must accept
    // Java 17 syntax: records, text blocks, switch expressions, and instanceof type patterns.
    // Parsing below the source's level rejects legitimate modern code as if it were malformed.
    private static final LanguageLevel LANGUAGE_LEVEL = LanguageLevel.JAVA_17;

    // Directory names that are build output or tool metadata, not source to model. Scoping the
    // walk to skip these is a simple, stated rule (Volume 5 section 4): relevance is decided by the
    // @Entity and @Service annotations, not by guessing paths, so anything else is walked and a
    // file that will not parse becomes a located skip rather than a clever exclusion.
    private static final Set<String> EXCLUDED_DIRECTORIES =
            Set.of("target", "build", "out", "bin", ".git", ".idea", ".mvn");

    private final EntityExtractor entityExtractor = new EntityExtractor();
    private final ServiceExtractor serviceExtractor = new ServiceExtractor();
    private final JavaTypeMapper typeMapper = new JavaTypeMapper();

    /**
     * The strict contract (Volume 5 section 3): a clean, validated-shape IR or a loud failure.
     *
     * <p>It runs the same survey as {@link #scan} and then refuses the scan if any file could not be
     * parsed or any entity failed for a hard cause, so no caller can generate from a model that
     * silently missed source. An entity with no single {@code @Id} is tolerated (it yields no
     * capability but is not an error), matching the read-by-id scope.
     */
    @Override
    public CapabilityModel provide(ProviderInput input) {
        ScanReport report = scan(input);

        if (!report.parseSkips().isEmpty()) {
            ParseSkip first = report.parseSkips().get(0);
            throw new ProviderException("failed to parse " + first.path() + ": " + first.reason());
        }
        for (EntityOutcome outcome : report.entityOutcomes()) {
            if (outcome.status() == EntityOutcome.Status.FAILED && outcome.cause() != Cause.NO_IDENTIFIER) {
                throw new ProviderException(outcome.detail());
            }
        }
        return report.model();
    }

    /**
     * The resilient survey (phase E1). Walks the scoped source, parses collect-and-continue, and
     * returns the discovered IR together with a per-entity outcome for everything it could parse and
     * a named skip for everything it could not. It aborts only when it cannot run at all: the source
     * root is missing, is not a directory, or cannot be walked.
     */
    public ScanReport scan(ProviderInput input) {
        Path sourceRoot = input.sourceRoot();
        if (!Files.isDirectory(sourceRoot)) {
            throw new ProviderException("source root is not a directory: " + sourceRoot);
        }

        ParseResults parsed = parseJavaSources(sourceRoot);
        List<ServiceMethod> serviceMethods = serviceExtractor.discover(parsed.units());

        List<Entity> entities = new ArrayList<>();
        List<Capability> capabilities = new ArrayList<>();
        List<EntityOutcome> outcomes = new ArrayList<>();

        for (ClassOrInterfaceDeclaration type : entityExtractor.entityTypes(parsed.units())) {
            classifyEntity(type, serviceMethods, entities, capabilities, outcomes);
        }

        CapabilityModel model = new CapabilityModel(
                moduleName(sourceRoot), IR_VERSION, entities, capabilities, List.of(), Metadata.empty());
        return new ScanReport(model, outcomes, parsed.skips());
    }

    // One entity's journey through extraction and binding discovery, recording a single outcome.
    // Each step that fails records a located reason and stops for this entity; the scan continues.
    private void classifyEntity(
            ClassOrInterfaceDeclaration type,
            List<ServiceMethod> serviceMethods,
            List<Entity> entities,
            List<Capability> capabilities,
            List<EntityOutcome> outcomes) {
        String entityName = type.getNameAsString();

        Entity entity;
        try {
            entity = entityExtractor.extractEntity(type);
        } catch (ProviderException e) {
            outcomes.add(EntityOutcome.failed(entityName, Cause.UNMAPPED_FIELD_TYPE, e.getMessage()));
            return;
        }
        entities.add(entity);

        Optional<ReadIntent> intent;
        try {
            intent = entityExtractor.readIntent(type);
        } catch (ProviderException e) {
            outcomes.add(EntityOutcome.failed(entityName, Cause.UNMAPPED_FIELD_TYPE, e.getMessage()));
            return;
        }
        if (intent.isEmpty()) {
            outcomes.add(EntityOutcome.failed(entityName, Cause.NO_IDENTIFIER, noIdentifierMessage(entityName)));
            return;
        }

        List<ServiceMethod> matches = matchesFor(intent.get(), serviceMethods);
        if (matches.isEmpty()) {
            outcomes.add(EntityOutcome.failed(
                    entityName, Cause.NO_BACKING_METHOD, noBackingMethodMessage(intent.get(), serviceMethods)));
            return;
        }
        if (matches.size() > 1) {
            outcomes.add(EntityOutcome.failed(
                    entityName, Cause.AMBIGUOUS_BINDING, ambiguousBindingMessage(intent.get(), matches)));
            return;
        }

        Capability capability = buildCapability(intent.get(), matches.get(0));
        capabilities.add(capability);
        outcomes.add(EntityOutcome.discovered(
                entityName, capability.name(), capability.binding().value()));
    }

    // The join (Volume 5 section 4.1): the @Service methods whose shape backs this read. Discovery
    // is by shape, never by name; zero or many is a loud failure upstream, never a guessed binding.
    private List<ServiceMethod> matchesFor(ReadIntent intent, List<ServiceMethod> serviceMethods) {
        List<ServiceMethod> matches = new ArrayList<>();
        for (ServiceMethod method : serviceMethods) {
            if (backsRead(method, intent)) {
                matches.add(method);
            }
        }
        return matches;
    }

    private Capability buildCapability(ReadIntent intent, ServiceMethod method) {
        BindingRef binding = new BindingRef(method.toBinding());
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

    private static String noIdentifierMessage(String entityName) {
        return "entity '" + entityName + "' has no single @Id identifier, so no read-by-id capability "
                + "is modeled. Only single-id-parameter reads are in scope (Volume 5 section 4.1); "
                + "composite or non-@Id keys are a later shape, not an error here.";
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

    // Collect-and-continue (phase E1): parse every walked file at the source's language level; a
    // file that fails to parse, or cannot be read, is recorded as a located skip and the walk goes
    // on. One bad file among hundreds is a logged skip, never a total failure. The skips are part
    // of the result, so a partial scan can never masquerade as a complete one.
    private static ParseResults parseJavaSources(Path sourceRoot) {
        JavaParser parser = new JavaParser(new ParserConfiguration().setLanguageLevel(LANGUAGE_LEVEL));
        List<CompilationUnit> units = new ArrayList<>();
        List<ParseSkip> skips = new ArrayList<>();
        for (Path file : javaFilesUnder(sourceRoot)) {
            try (InputStream in = Files.newInputStream(file)) {
                ParseResult<CompilationUnit> result = parser.parse(in);
                if (result.isSuccessful() && result.getResult().isPresent()) {
                    units.add(result.getResult().get());
                } else {
                    skips.add(new ParseSkip(file.toString(), describeProblems(result.getProblems())));
                }
            } catch (IOException e) {
                skips.add(new ParseSkip(file.toString(), "could not read file: " + e.getMessage()));
            }
        }
        return new ParseResults(units, skips);
    }

    // Sorted for determinism: the same source tree always yields the same IR (Volume 5 rule 3).
    // Build output and tool metadata directories are skipped (Volume 5 section 4, the walk rule).
    private static List<Path> javaFilesUnder(Path sourceRoot) {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> !isExcludedFromWalk(path))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new ProviderException("failed to walk sources under " + sourceRoot, e);
        }
    }

    // A concise, located reason from the parser's problems: the location and message of each
    // problem, first line only. JavaParser's own toString embeds the cause's full stack trace,
    // which is noise in a survey; the location and the "found X, expecting Y" message are the
    // diagnosable part, and they are what the second-app trial recorded.
    private static String describeProblems(List<Problem> problems) {
        if (problems.isEmpty()) {
            return "unparseable (no problem detail reported)";
        }
        return problems.stream()
                .map(problem -> firstLine(problem.getVerboseMessage()))
                .collect(Collectors.joining("; "));
    }

    private static String firstLine(String text) {
        int newline = text.indexOf('\n');
        return (newline < 0 ? text : text.substring(0, newline)).trim();
    }

    // A file is excluded when any directory on its path is build output or tool metadata. Kept
    // simple and stated on purpose: no path-based guess at which files are "relevant" (only
    // @Entity and @Service classes contribute regardless of where they sit), just the directories
    // that never hold source worth modeling.
    static boolean isExcludedFromWalk(Path path) {
        for (Path part : path) {
            if (EXCLUDED_DIRECTORIES.contains(part.toString())) {
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

    // The output of the parse pass: the units that parsed, and the files that did not.
    private record ParseResults(List<CompilationUnit> units, List<ParseSkip> skips) {
    }
}
