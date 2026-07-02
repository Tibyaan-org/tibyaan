package com.tibyaan.provider.spring;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.tibyaan.core.ir.Annotation;
import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityKind;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Relation;
import com.tibyaan.core.ir.Type;
import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.plugin.SourceAnalyzer;
import com.tibyaan.provider.spring.EntityOutcome.Cause;
import com.tibyaan.provider.spring.extractors.EntityExtractor;
import com.tibyaan.provider.spring.extractors.ExtractedEntity;
import com.tibyaan.provider.spring.extractors.MatchResult;
import com.tibyaan.provider.spring.extractors.ReadIntent;
import com.tibyaan.provider.spring.extractors.RepositoryCatalog;
import com.tibyaan.provider.spring.extractors.ServiceExtractor;
import com.tibyaan.provider.spring.extractors.ServiceMethod;
import com.tibyaan.provider.spring.extractors.ServiceMethodSource;
import com.tibyaan.provider.spring.extractors.TypeRegistry;
import com.tibyaan.provider.spring.extractors.ViewReadMatcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
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
            if (outcome.status() == EntityOutcome.Status.FAILED
                    && outcome.cause() != Cause.NO_IDENTIFIER
                    && outcome.cause() != Cause.COMPOSITE_KEY) {
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
        TypeRegistry registry = TypeRegistry.from(parsed.units());
        RepositoryCatalog repositories = RepositoryCatalog.from(parsed.units());
        ViewReadMatcher viewMatcher = new ViewReadMatcher(repositories, typeMapper);
        List<ServiceMethodSource> serviceSources = serviceExtractor.sources(parsed.units());
        int skipCount = parsed.skips().size();

        List<Entity> entities = new ArrayList<>();
        List<Capability> capabilities = new ArrayList<>();
        List<EntityOutcome> outcomes = new ArrayList<>();
        List<Relation> relations = new ArrayList<>();

        Discovery discovery = new Discovery(serviceSources, viewMatcher, skipCount);
        for (ClassOrInterfaceDeclaration type : entityExtractor.entityTypes(parsed.units())) {
            classifyEntity(type, registry, discovery, entities, capabilities, relations, outcomes);
        }

        CapabilityModel model = new CapabilityModel(
                moduleName(sourceRoot), IR_VERSION, entities, capabilities, dedupeAndSort(relations),
                Metadata.empty());
        return new ScanReport(model, outcomes, parsed.skips());
    }

    // Relations are the entity-level summary (Volume 5 section 4.4, rule 5): identical (from, to,
    // kind) triples are emitted once, in a deterministic order, so the same source always yields the
    // same model. The fields carry the references; this is the deduplicated roll-up.
    private static List<Relation> dedupeAndSort(List<Relation> relations) {
        return relations.stream()
                .distinct()
                .sorted(Comparator.comparing(Relation::from)
                        .thenComparing(Relation::to)
                        .thenComparing(relation -> relation.kind().name()))
                .toList();
    }

    // One entity's journey through extraction, eligibility, and binding discovery, recording a
    // single outcome. Each step that fails records a located reason and stops for this entity; the
    // scan continues (phase E1).
    private void classifyEntity(
            ClassOrInterfaceDeclaration type,
            TypeRegistry registry,
            Discovery discovery,
            List<Entity> entities,
            List<Capability> capabilities,
            List<Relation> relations,
            List<EntityOutcome> outcomes) {
        String entityName = type.getNameAsString();

        ExtractedEntity extracted;
        try {
            extracted = entityExtractor.extractEntity(type, registry);
        } catch (ProviderException e) {
            outcomes.add(EntityOutcome.failed(entityName, Cause.UNMAPPED_FIELD_TYPE, e.getMessage()));
            return;
        }
        entities.add(extracted.entity());
        relations.addAll(extracted.relations());

        // Condition 0: a composite key forms no read intent and is never read by part of its key.
        if (entityExtractor.hasCompositeKey(type)) {
            outcomes.add(EntityOutcome.failed(
                    entityName, Cause.COMPOSITE_KEY, entityExtractor.compositeKeyReason(type)));
            return;
        }

        Optional<ReadIntent> intent = entityExtractor.readIntent(type);
        if (intent.isEmpty()) {
            outcomes.add(EntityOutcome.failed(
                    entityName, Cause.NO_IDENTIFIER, entityExtractor.noReadIntentReason(type)));
            return;
        }

        outcomes.add(discovery.discover(intent.get(), capabilities));
    }

    // The candidate pool (Volume 5 section 4.2, condition 10): 4.1 entity-returning and 4.2
    // view-returning candidates pool with no precedence. Exactly one discovers; zero refuses with
    // near-misses named; more than one is ambiguous. The provider never picks.
    private final class Discovery {

        private final List<ServiceMethodSource> serviceSources;
        private final ViewReadMatcher viewMatcher;
        private final int skipCount;

        Discovery(List<ServiceMethodSource> serviceSources, ViewReadMatcher viewMatcher, int skipCount) {
            this.serviceSources = serviceSources;
            this.viewMatcher = viewMatcher;
            this.skipCount = skipCount;
        }

        EntityOutcome discover(ReadIntent intent, List<Capability> capabilities) {
            List<Candidate> candidates = new ArrayList<>();
            List<String> nearMisses = new ArrayList<>();
            for (ServiceMethodSource source : serviceSources) {
                evaluate(source, intent, candidates, nearMisses);
            }

            if (candidates.size() == 1) {
                Candidate winner = candidates.get(0);
                Capability capability = buildCapability(intent, winner);
                capabilities.add(capability);
                return EntityOutcome.discovered(intent.entityName(), discoveredDetail(capability, winner));
            }
            if (candidates.isEmpty()) {
                return EntityOutcome.failed(intent.entityName(), Cause.NO_BACKING_METHOD,
                        noBackingMethodMessage(intent, serviceSources, nearMisses, skipCount));
            }
            return EntityOutcome.failed(intent.entityName(), Cause.AMBIGUOUS_BINDING,
                    ambiguousBindingMessage(intent, candidates));
        }

        // Conditions 1 to 3 frame every candidate; a return equal to the entity is a section 4.1
        // (entity-returning) candidate, and any other non-void return is evaluated as a section 4.2
        // (view-returning) candidate. A 4.2 method that passed 1 to 3 but failed later is a near miss.
        private void evaluate(ServiceMethodSource source, ReadIntent intent,
                              List<Candidate> candidates, List<String> nearMisses) {
            ServiceMethod method = source.method();
            if (method.parameterTypes().size() != 1) {
                return;
            }
            Optional<Type> parameterType = typeMapper.map(method.parameterTypes().get(0));
            if (parameterType.isEmpty() || !parameterType.get().equals(intent.idType())) {
                return;
            }
            if (method.returnTypeSimpleName().equals("void")) {
                return;
            }
            if (method.returnTypeSimpleName().equals(intent.entityName())) {
                candidates.add(new Candidate(method.toBinding(), null,
                        "returns the entity " + intent.entityName() + " directly (section 4.1)"));
                return;
            }
            MatchResult result = viewMatcher.evaluate(source.declaration(), source.service(), intent);
            if (result.matched()) {
                candidates.add(new Candidate(method.toBinding(), result.view(), result.explanation()));
            } else {
                nearMisses.add(method.signature() + ": " + result.reason());
            }
        }
    }

    // A backing candidate: its binding, the view type simple name (null when entity-returning), and
    // the structural explanation (section 4.2.3).
    private record Candidate(String binding, String view, String explanation) {
    }

    private Capability buildCapability(ReadIntent intent, Candidate candidate) {
        BindingRef binding = new BindingRef(candidate.binding());
        Map<String, Type> inputs = new LinkedHashMap<>();
        inputs.put(intent.idInputName(), intent.idType());
        Type output = Type.entityRef(intent.entityName());
        if (candidate.view() == null) {
            return Capability.read(intent.capabilityName(), inputs, output, binding);
        }
        // Section 4.2.5: output stays ENTITY_REF(E); the view's concrete shape is recorded as a hint.
        List<Annotation> annotations = List.of(new Annotation("view", List.of(candidate.view())));
        return new Capability(intent.capabilityName(), CapabilityKind.READ, inputs, output, true,
                binding, annotations);
    }

    private static String discoveredDetail(Capability capability, Candidate candidate) {
        String base = capability.name() + " -> " + capability.binding().value();
        if (candidate.view() != null) {
            base += " (view " + candidate.view() + ")";
        }
        return base + "; " + candidate.explanation();
    }

    // Section 4.2.4: zero candidates stays NO_BACKING_METHOD, listing the @Service methods seen and,
    // added here, the near-misses (methods that passed conditions 1 to 3 but failed a later one) and
    // the count of unparseable files, so an absence of candidates is never mistaken for an absence of
    // code. The binding is discovered, not assumed.
    private static String noBackingMethodMessage(ReadIntent intent, List<ServiceMethodSource> sources,
                                                 List<String> nearMisses, int skipCount) {
        String seen = sources.isEmpty()
                ? "no @Service methods were found"
                : "the @Service methods found were "
                        + sources.stream().map(source -> source.method().signature()).toList();
        StringBuilder message = new StringBuilder()
                .append("no service method backs read capability '").append(intent.capabilityName())
                .append("' for entity '").append(intent.entityName())
                .append("'. Looked for a public @Service method taking a single parameter mapping to ")
                .append(intent.idType().canonical())
                .append(" that returns '").append(intent.entityName())
                .append("' or a view derived from a by-primary-key load of it; ").append(seen).append(".");
        if (!nearMisses.isEmpty()) {
            message.append(" Near misses: ").append(nearMisses).append(".");
        }
        if (skipCount > 0) {
            message.append(" Note: ").append(skipCount).append(" source file(s) could not be parsed and "
                    + "were skipped; a backing method may live in one of them.");
        }
        message.append(" The binding is discovered, not assumed: add or expose a finder of that shape, "
                + "or this entity cannot be read.");
        return message.toString();
    }

    private static String ambiguousBindingMessage(ReadIntent intent, List<Candidate> candidates) {
        List<String> labelled = candidates.stream()
                .map(candidate -> candidate.binding()
                        + (candidate.view() == null ? " (entity-returning)" : " (view-returning "
                                + candidate.view() + ")"))
                .toList();
        return "read capability '" + intent.capabilityName() + "' for entity '" + intent.entityName()
                + "' is backed by more than one candidate: " + labelled
                + ". The backing method must be unambiguous; the provider will not guess. "
                + "Disambiguate the service API so exactly one method backs this read.";
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
