package com.tibyaan.provider.spring;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Relation;
import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.plugin.SourceAnalyzer;
import com.tibyaan.provider.spring.extractors.EntityExtractor;
import com.tibyaan.provider.spring.extractors.Extractor;
import com.tibyaan.provider.spring.extractors.ExtractorContribution;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A {@link SourceAnalyzer} that reads a Spring and Java project from source with JavaParser and
 * produces a Capability IR (Volume 5 section 4, ADR-0010).
 *
 * <p>It loads every {@code .java} file under the source root into an AST, runs the enabled
 * extractors, and assembles their contributions into a single {@link CapabilityModel}. Only
 * {@link EntityExtractor} is enabled in the MVP; the repository, service, controller, and
 * annotation extractors exist as placeholders and are intentionally not wired in here.
 *
 * <p>The provider produces IR and stops. It does not validate (that is a separate concern, run
 * by the pipeline and the tests), generate output, or know that any backend or runtime exists.
 */
public final class SpringCapabilityProvider implements SourceAnalyzer {

    private static final String IR_VERSION = "0.1";

    // ENABLE ONLY EntityExtractor (ADR-0011). The placeholders RepositoryExtractor,
    // ServiceExtractor, ControllerExtractor, and AnnotationExtractor are deliberately absent
    // from this list until their phase enables them.
    private final List<Extractor> enabledExtractors = List.of(new EntityExtractor());

    @Override
    public CapabilityModel provide(ProviderInput input) {
        Path sourceRoot = input.sourceRoot();
        if (!Files.isDirectory(sourceRoot)) {
            throw new ProviderException("source root is not a directory: " + sourceRoot);
        }

        List<CompilationUnit> units = parseJavaSources(sourceRoot);

        List<Entity> entities = new ArrayList<>();
        List<Capability> capabilities = new ArrayList<>();
        List<Relation> relations = new ArrayList<>();
        for (Extractor extractor : enabledExtractors) {
            ExtractorContribution contribution = extractor.extract(units);
            entities.addAll(contribution.entities());
            capabilities.addAll(contribution.capabilities());
            relations.addAll(contribution.relations());
        }

        return new CapabilityModel(
                moduleName(sourceRoot), IR_VERSION, entities, capabilities, relations, Metadata.empty());
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
