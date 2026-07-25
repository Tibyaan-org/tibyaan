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
import com.tibyaan.core.ir.EntityRef;
import com.tibyaan.core.ir.Field;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Relation;
import com.tibyaan.core.ir.Type;
import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.plugin.SourceAnalyzer;
import com.tibyaan.provider.spring.EntityOutcome.Cause;
import com.tibyaan.provider.spring.attest.AttestationFile;
import com.tibyaan.provider.spring.attest.AttestationIntake;
import com.tibyaan.provider.spring.attest.AttestationRecord;
import com.tibyaan.provider.spring.attest.IntakeRefusal;
import com.tibyaan.provider.spring.extractors.ControllerSecurity;
import com.tibyaan.provider.spring.extractors.DisplayNames;
import com.tibyaan.provider.spring.extractors.EntityExtractor;
import com.tibyaan.provider.spring.extractors.ExtractedEntity;
import com.tibyaan.provider.spring.extractors.IdentifierResolver;
import com.tibyaan.provider.spring.extractors.ListReadMatcher;
import com.tibyaan.provider.spring.extractors.MatchResult;
import com.tibyaan.provider.spring.extractors.ReadIntent;
import com.tibyaan.provider.spring.extractors.RepositoryCatalog;
import com.tibyaan.provider.spring.extractors.ServiceExtractor;
import com.tibyaan.provider.spring.extractors.ServiceMethod;
import com.tibyaan.provider.spring.extractors.ServiceMethodSource;
import com.tibyaan.provider.spring.extractors.SkippedField;
import com.tibyaan.provider.spring.extractors.TypeReferenceResolver;
import com.tibyaan.provider.spring.extractors.TypeRegistry;
import com.tibyaan.provider.spring.extractors.ViewReadMatcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
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
        // The two-stage key resolver (section 4.12.4): the single index of in-tree types every place
        // that ties a Java type to an entity now resolves through, by key rather than by simple name.
        TypeReferenceResolver resolver = TypeReferenceResolver.from(parsed.units());
        TypeRegistry registry = TypeRegistry.from(parsed.units());
        // The identifier resolver (section 4.13.2): reads an entity's own @Id, or follows the in-tree
        // superclass chain to a single inherited one, refusing what it cannot read.
        IdentifierResolver identifiers = IdentifierResolver.from(parsed.units(), resolver);
        RepositoryCatalog repositories = RepositoryCatalog.from(parsed.units(), resolver);
        ViewReadMatcher viewMatcher = new ViewReadMatcher(repositories, typeMapper, resolver);
        List<ServiceMethodSource> serviceSources = serviceExtractor.sources(parsed.units());
        int skipCount = parsed.skips().size();

        // Pass 1 (extraction). Extract every @Entity keyed by FQCN (section 4.12.2), collecting a
        // per-entity plan: a terminal outcome (composite key, no identifier) or a read intent to
        // discover. References are carried by key; each key's source file is tracked for the
        // split-package check. Discovery is deferred to pass 2, once display names are known.
        List<Entity> entities = new ArrayList<>();
        List<Relation> relations = new ArrayList<>();
        List<SkippedField> skippedFields = new ArrayList<>();
        List<EntityPlan> plans = new ArrayList<>();
        Map<String, Set<String>> filesByKey = new LinkedHashMap<>();
        for (ClassOrInterfaceDeclaration type : entityExtractor.entityTypes(parsed.units())) {
            ExtractedEntity extracted = entityExtractor.extractEntity(type, registry, resolver);
            entities.add(extracted.entity());
            relations.addAll(extracted.relations());
            skippedFields.addAll(extracted.skipped());
            recordSourceFile(filesByKey, extracted.entity().key(), type);
            plans.add(planFor(type, extracted.entity().key(), identifiers));
        }

        // Section 4.12.2, rule 3: a key claimed by two distinct source files is a split-package clash;
        // fail loud, never merge. A genuine same-file duplicate key is guarded by core validation.
        failOnSplitPackage(filesByKey);

        // Section 4.12.3: assign each entity a display name from its key by the batch fixpoint. A
        // residual display collision fails loud here; otherwise the assignment is a pure function of
        // the key set. Remapping the key-carrying references to display names lets the rest of the
        // pipeline (closure, finalize, generation) run on unique display names, byte-identical to the
        // pre-phase pipeline whenever every simple name was already unique.
        Map<String, String> displayByKey = DisplayNames.assign(keysOf(entities));
        entities = remapEntities(entities, displayByKey);
        relations = remapRelations(relations, displayByKey);
        skippedFields = remapSkips(skippedFields, displayByKey);

        // Pass 2 (discovery). Each plan yields an outcome, minting capability names from display names
        // and matching identity by key (section 4.12.4). By-id capabilities are added in walk order.
        List<Capability> capabilities = new ArrayList<>();
        List<EntityOutcome> outcomes = new ArrayList<>();
        Discovery discovery = new Discovery(serviceSources, viewMatcher, skipCount, resolver, displayByKey);
        for (EntityPlan plan : plans) {
            outcomes.add(plan.toOutcome(discovery, capabilities, displayByKey));
        }

        // List reads (phase E4, section 4.10): a separate pool over the same service catalog. It appends
        // list<E> capabilities and reports its own outcomes, disjoint from by-id discovery above (a list
        // candidate returns a collection of E, a by-id candidate returns E singular), so it cannot move
        // any get<E> result. Identifier-independent: an entity with no usable id can still yield list<E>.
        ListReads listReads = discoverListReads(repositories, serviceSources, resolver, capabilities,
                displayByKey);

        // Attestation intake (phase Attestation, section 4.15): read the adopter's committed
        // tibyan/attestations.json and, for each entry, either add an attested capability or record a
        // located refusal. Run after both discovery pools are final (so name uniqueness is checked against
        // every verified capability) and BEFORE controller-authority decoration, so an attested read's
        // declared-security contract composes exactly as a verified read's does (section 4.15.8). Additive:
        // with no record file the model and every outcome are byte-identical (fixture ATT-11).
        AttestationIntake.Result intake = runAttestationIntake(sourceRoot, parsed.units(), resolver,
                repositories, capabilities, displayByKey);
        capabilities.addAll(intake.attested());
        List<IntakeRefusal> attestationRefusals = intake.refusals();

        // Controller-authority (phase Controller-Authority, section 4.14): a decorator pass over the
        // discovered pool. It never mints or removes a capability; it attaches the declared security
        // contract each read's controller surface carries (a compiled any-of authority sequence, an
        // unverifiable marker, or nothing), so a security-free app is byte-identical. Run last, after
        // both discovery pools are final, because it reads capabilities and adds nothing to them but an
        // annotation. Identity and binding are re-anchored through the key resolver and the single
        // @Service implementation rule, so no read ever carries another read's authorities.
        capabilities = ControllerSecurity.decorate(parsed.units(), serviceSources, capabilities, typeMapper);

        // Partial-model integrity: close the model (drop references to absent entities, section 4.7),
        // then finalize (mark partial entities and enrich their outcomes, section 4.9). Both run once
        // over the whole model, after every entity has been through tolerant extraction (section 4.8).
        Finalized finalized = closeAndFinalize(entities, relations, outcomes, skippedFields);

        CapabilityModel model = new CapabilityModel(
                moduleName(sourceRoot), IR_VERSION, finalized.entities(), capabilities,
                dedupeAndSort(finalized.relations()), Metadata.empty());
        return new ScanReport(model, finalized.outcomes(), parsed.skips(), finalized.skippedFields(),
                listReads.outcomes(), listReads.deferrals(), attestationRefusals);
    }

    /**
     * Computes the current structural fingerprint of an attestation target's slice (phase Attestation,
     * Volume 5 section 4.15.4). This is the value the {@code tibyan attest} CLI stamps into an entry
     * against the current code; the scan then recomputes and compares it. Empty when the target does not
     * resolve to exactly one in-tree declaration.
     */
    public Optional<String> attestationFingerprint(ProviderInput input, String target) {
        ParseResults parsed = parseJavaSources(input.sourceRoot());
        TypeReferenceResolver resolver = TypeReferenceResolver.from(parsed.units());
        RepositoryCatalog repositories = RepositoryCatalog.from(parsed.units(), resolver);
        return new AttestationIntake(parsed.units(), resolver, repositories, typeMapper, Map.of())
                .fingerprintFor(target);
    }

    /** The outcome of stamping one entry: its target, whether it resolved, and the fingerprint if so. */
    public record StampOutcome(String target, boolean resolved, String fingerprint) {
    }

    /**
     * Stamps the fingerprint (and, when a date is given, the date) into every resolvable entry of the
     * adopter's record and writes it back (the {@code tibyan attest} action, Volume 5 section 4.15.3). The
     * fingerprint is computed over the current code, so it co-arrives with the code it pins; an unresolved
     * entry is left untouched and reported. This is the only path that stamps a fingerprint; a
     * hand-authored entry without one is refused at intake, never silently completed.
     */
    public List<StampOutcome> stampAttestations(ProviderInput input, String date) {
        Path root = input.sourceRoot();
        List<AttestationRecord> records = AttestationFile.read(root);
        ParseResults parsed = parseJavaSources(root);
        TypeReferenceResolver resolver = TypeReferenceResolver.from(parsed.units());
        RepositoryCatalog repositories = RepositoryCatalog.from(parsed.units(), resolver);
        AttestationIntake intake = new AttestationIntake(parsed.units(), resolver, repositories, typeMapper,
                Map.of());
        List<AttestationRecord> stamped = new ArrayList<>();
        List<StampOutcome> outcomes = new ArrayList<>();
        for (AttestationRecord record : records) {
            Optional<String> fingerprint = record.target() == null
                    ? Optional.empty()
                    : intake.fingerprintFor(record.target());
            if (fingerprint.isPresent()) {
                AttestationRecord.Provenance prior = record.provenance();
                String assertedBy = prior == null ? null : prior.assertedBy();
                String origin = prior == null || prior.origin() == null ? "human-authored" : prior.origin();
                String stampedDate = date != null ? date : (prior == null ? null : prior.date());
                AttestationRecord.Provenance provenance = new AttestationRecord.Provenance(assertedBy,
                        origin, stampedDate, fingerprint.get());
                stamped.add(new AttestationRecord(record.target(), record.category(), record.name(),
                        record.resultSemantic(), record.parameters(), record.subjectEntity(), provenance));
                outcomes.add(new StampOutcome(record.target(), true, fingerprint.get()));
            } else {
                stamped.add(record);
                outcomes.add(new StampOutcome(record.target(), false, null));
            }
        }
        if (!stamped.isEmpty()) {
            AttestationFile.write(root, stamped);
        }
        return outcomes;
    }

    // Runs the attestation intake (phase Attestation, section 4.15.4). A malformed record file is a single
    // located refusal, never a silent skip; an absent file is no attestations at all. The intake never
    // guesses: it either verifies an entry's structural frame and carries the human's words, or refuses.
    private AttestationIntake.Result runAttestationIntake(Path sourceRoot, List<CompilationUnit> units,
                                                          TypeReferenceResolver resolver,
                                                          RepositoryCatalog repositories,
                                                          List<Capability> discovered,
                                                          Map<String, String> displayByKey) {
        List<AttestationRecord> records;
        try {
            records = AttestationFile.read(sourceRoot);
        } catch (AttestationFile.AttestationFileException e) {
            return new AttestationIntake.Result(List.of(), List.of(new IntakeRefusal(
                    "(" + AttestationFile.RELATIVE_PATH + ")", null,
                    IntakeRefusal.Cause.MALFORMED_FILE, e.getMessage())));
        }
        if (records.isEmpty()) {
            return new AttestationIntake.Result(List.of(), List.of());
        }
        Set<String> existingNames = new HashSet<>();
        for (Capability capability : discovered) {
            existingNames.add(capability.name());
        }
        AttestationIntake attestationIntake = new AttestationIntake(units, resolver, repositories,
                typeMapper, displayByKey);
        return attestationIntake.run(records, existingNames);
    }

    // A per-entity plan from pass 1: the entity key and simple name, and either a terminal failure
    // reason (composite key, no identifier) or a read intent to discover in pass 2. The outcome is
    // built in pass 2 so its entity name is the display name, which the disambiguation fixpoint
    // (section 4.12.3) has by then assigned.
    private record EntityPlan(String entityKey, String entitySimple, Kind kind, String reason,
                              ReadIntent intent) {

        enum Kind { COMPOSITE, NO_IDENTIFIER, DISCOVER }

        EntityOutcome toOutcome(Discovery discovery, List<Capability> capabilities,
                                Map<String, String> displayByKey) {
            String display = displayByKey.getOrDefault(entityKey, entitySimple);
            return switch (kind) {
                case COMPOSITE -> EntityOutcome.failed(display, Cause.COMPOSITE_KEY, reason);
                case NO_IDENTIFIER -> EntityOutcome.failed(display, Cause.NO_IDENTIFIER, reason);
                case DISCOVER -> discovery.discover(intent, capabilities);
            };
        }
    }

    // Eligibility, computed in pass 1 (section 4.2, condition 0, widened by section 4.13.2). The
    // identifier is resolved directly or through the in-tree superclass chain. A composite key (direct
    // or inherited) forms no read intent and reports COMPOSITE_KEY; every other refusal (no identifier
    // anywhere, an unreadable superclass, an ambiguous hierarchy, an inexact or unmappable id type)
    // reports NO_IDENTIFIER with its own located message. Otherwise the entity carries a read intent,
    // and discovery (sections 4.1 and 4.2) runs over it unchanged.
    private EntityPlan planFor(ClassOrInterfaceDeclaration type, String key,
                               IdentifierResolver identifiers) {
        String simple = type.getNameAsString();
        IdentifierResolver.Result identifier = identifiers.resolve(type);
        if (identifier.kind() == IdentifierResolver.Kind.COMPOSITE) {
            return new EntityPlan(key, simple, EntityPlan.Kind.COMPOSITE, identifier.reason(), null);
        }
        Optional<ReadIntent> intent = entityExtractor.readIntent(type, identifier);
        if (intent.isEmpty()) {
            return new EntityPlan(key, simple, EntityPlan.Kind.NO_IDENTIFIER,
                    entityExtractor.noReadIntentReason(type, identifier), null);
        }
        return new EntityPlan(key, simple, EntityPlan.Kind.DISCOVER, null, intent.get());
    }

    // Records the source file that declares an entity key, for the split-package check (section
    // 4.12.2, rule 3). Units are parsed from their paths, so the storage path is present.
    private static void recordSourceFile(Map<String, Set<String>> filesByKey, String key,
                                         ClassOrInterfaceDeclaration type) {
        String file = type.findCompilationUnit()
                .flatMap(CompilationUnit::getStorage)
                .map(storage -> storage.getPath().toString())
                .orElse("(unknown source)");
        filesByKey.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(file);
    }

    // Section 4.12.2, rule 3: two distinct entity classes sharing a package and simple name in
    // different source files yield identical keys. The FQCN alone cannot tell this from a genuine
    // duplicate, so it is distinguished by source file and refused loud, worded distinctly, never
    // merged. Folding the module coordinate into the key is the deferred option (no benchmark needs it).
    private static void failOnSplitPackage(Map<String, Set<String>> filesByKey) {
        for (Map.Entry<String, Set<String>> entry : filesByKey.entrySet()) {
            if (entry.getValue().size() > 1) {
                throw new ProviderException("split-package clash: the entity key '" + entry.getKey()
                        + "' is declared by more than one source file " + entry.getValue()
                        + ". Two distinct classes sharing a package and simple name in different modules "
                        + "cannot be told apart by their fully-qualified name (Volume 5 section 4.12.2, "
                        + "rule 3); they are not merged. Rename one class or split the package to resolve "
                        + "it.");
            }
        }
    }

    private static List<String> keysOf(List<Entity> entities) {
        List<String> keys = new ArrayList<>();
        for (Entity entity : entities) {
            keys.add(entity.key());
        }
        return keys;
    }

    // Remap every key-carrying reference an entity holds to a display name (section 4.12.3): the
    // entity's own name from its key, and each ENTITY_REF field's target from the resolved target key.
    // A unique simple name maps to itself, so a no-collision model is unchanged.
    private static List<Entity> remapEntities(List<Entity> entities, Map<String, String> displayByKey) {
        List<Entity> remapped = new ArrayList<>();
        for (Entity entity : entities) {
            String display = displayByKey.getOrDefault(entity.key(), entity.name());
            List<Field> fields = new ArrayList<>();
            for (Field field : entity.fields()) {
                if (field.type() instanceof EntityRef ref) {
                    String targetDisplay = displayByKey.getOrDefault(ref.entityName(), ref.entityName());
                    fields.add(new Field(field.name(), Type.entityRef(targetDisplay), field.nullable(),
                            field.annotations()));
                } else {
                    fields.add(field);
                }
            }
            remapped.add(new Entity(entity.key(), display, fields, entity.annotations(), entity.metadata()));
        }
        return remapped;
    }

    private static List<Relation> remapRelations(List<Relation> relations, Map<String, String> displayByKey) {
        List<Relation> remapped = new ArrayList<>();
        for (Relation relation : relations) {
            remapped.add(new Relation(displayByKey.getOrDefault(relation.from(), relation.from()),
                    displayByKey.getOrDefault(relation.to(), relation.to()), relation.kind()));
        }
        return remapped;
    }

    private static List<SkippedField> remapSkips(List<SkippedField> skips, Map<String, String> displayByKey) {
        List<SkippedField> remapped = new ArrayList<>();
        for (SkippedField skip : skips) {
            remapped.add(new SkippedField(displayByKey.getOrDefault(skip.entity(), skip.entity()),
                    skip.field(), skip.note()));
        }
        return remapped;
    }

    // Model closure (section 4.7) plus the partial disclosure (section 4.9), as one pass over the
    // extracted model. Every ENTITY_REF field and Relation targeting an entity absent from the model
    // is dropped and disclosed, so validation rules 3 and 4 hold by construction and the model is
    // producible. Every entity that lost a field (to tolerance or to closure) is marked partial with
    // a `partial` annotation naming those fields, and its outcome is enriched with them.
    //
    // Package-private for a direct unit test: given tolerance (section 4.8) every scanned @Entity
    // extracts, and an ENTITY_REF is only emitted for a present @Entity, so the scan path never
    // produces an absent target and closure never drops anything on real input (a recorded finding,
    // Volume 5 section 4.7). Closure is retained as the standing invariant guarantee and is exercised
    // in isolation on a synthetic model.
    static Finalized closeAndFinalize(List<Entity> entities, List<Relation> relations,
                                      List<EntityOutcome> outcomes, List<SkippedField> toleranceSkips) {
        Set<String> present = new HashSet<>();
        for (Entity entity : entities) {
            present.add(entity.name());
        }

        List<SkippedField> allSkips = new ArrayList<>(toleranceSkips);
        Map<String, List<String>> partialByEntity = new LinkedHashMap<>();
        for (SkippedField skip : toleranceSkips) {
            partialByEntity.computeIfAbsent(skip.entity(), k -> new ArrayList<>()).add(skip.field());
        }

        List<Entity> closedEntities = new ArrayList<>();
        for (Entity entity : entities) {
            List<Field> keptFields = new ArrayList<>();
            for (Field field : entity.fields()) {
                if (field.type() instanceof EntityRef ref && !present.contains(ref.entityName())) {
                    allSkips.add(new SkippedField(entity.name(), field.name(),
                            "reference to entity '" + ref.entityName() + "' dropped: the target is not "
                                    + "in the model (Volume 5 section 4.7, model closure)."));
                    partialByEntity.computeIfAbsent(entity.name(), k -> new ArrayList<>())
                            .add(field.name());
                } else {
                    keptFields.add(field);
                }
            }
            List<String> partialFields = partialByEntity.getOrDefault(entity.name(), List.of());
            List<Annotation> annotations = partialFields.isEmpty()
                    ? List.of()
                    : List.of(new Annotation("partial", partialFields));
            closedEntities.add(new Entity(entity.key(), entity.name(), keptFields, annotations,
                    entity.metadata()));
        }

        List<Relation> closedRelations = new ArrayList<>();
        for (Relation relation : relations) {
            if (present.contains(relation.from()) && present.contains(relation.to())) {
                closedRelations.add(relation);
            }
        }

        List<EntityOutcome> enrichedOutcomes = new ArrayList<>();
        for (EntityOutcome outcome : outcomes) {
            List<String> partialFields = partialByEntity.get(outcome.entityName());
            enrichedOutcomes.add(partialFields == null ? outcome : outcome.withPartialFields(partialFields));
        }

        return new Finalized(closedEntities, closedRelations, enrichedOutcomes, allSkips);
    }

    // The result of the closure and finalize pass: the closed entities and relations, the outcomes
    // enriched with partial fields, and every skip and drop for the survey.
    record Finalized(List<Entity> entities, List<Relation> relations,
                     List<EntityOutcome> outcomes, List<SkippedField> skippedFields) {
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

    // The candidate pool (Volume 5 section 4.2, condition 10): 4.1 entity-returning and 4.2
    // view-returning candidates pool with no precedence. Exactly one discovers; zero refuses with
    // near-misses named; more than one is ambiguous. The provider never picks.
    //
    // Phase Entity Identity (section 4.12.4): a candidate returns THIS entity when its return type
    // resolves, by key, to the intent's entity key (not merely by simple name), so a method returning
    // one context's Note is never a candidate for another's. The emitted capability's name and output
    // are minted from the entity's display name; the intent's simple name still drives the view
    // matcher's AST-level reasoning.
    private final class Discovery {

        private final List<ServiceMethodSource> serviceSources;
        private final ViewReadMatcher viewMatcher;
        private final int skipCount;
        private final TypeReferenceResolver resolver;
        private final Map<String, String> displayByKey;

        Discovery(List<ServiceMethodSource> serviceSources, ViewReadMatcher viewMatcher, int skipCount,
                  TypeReferenceResolver resolver, Map<String, String> displayByKey) {
            this.serviceSources = serviceSources;
            this.viewMatcher = viewMatcher;
            this.skipCount = skipCount;
            this.resolver = resolver;
            this.displayByKey = displayByKey;
        }

        EntityOutcome discover(ReadIntent intent, List<Capability> capabilities) {
            List<Candidate> candidates = new ArrayList<>();
            List<String> nearMisses = new ArrayList<>();
            for (ServiceMethodSource source : serviceSources) {
                evaluate(source, intent, candidates, nearMisses);
            }

            // The display-named view of the intent: the emitted capability name (get<display>), output
            // (ENTITY_REF(display)), and every diagnostic use the display name, which equals the simple
            // name unless it collided. Identity is still the intent's key.
            String display = displayByKey.getOrDefault(intent.entityKey(), intent.entityName());
            ReadIntent displayIntent = new ReadIntent(display, intent.entityKey(), "get" + display,
                    intent.idInputName(), intent.idType());

            if (candidates.size() == 1) {
                Candidate winner = candidates.get(0);
                Capability capability = buildCapability(displayIntent, winner);
                capabilities.add(capability);
                return EntityOutcome.discovered(display, discoveredDetail(capability, winner));
            }
            if (candidates.isEmpty()) {
                return EntityOutcome.failed(display, Cause.NO_BACKING_METHOD,
                        noBackingMethodMessage(displayIntent, serviceSources, nearMisses, skipCount));
            }
            return EntityOutcome.failed(display, Cause.AMBIGUOUS_BINDING,
                    ambiguousBindingMessage(displayIntent, candidates));
        }

        // Conditions 1 to 3 frame every candidate; a return that resolves by key to the entity is a
        // section 4.1 (entity-returning) candidate, and any other non-void return is evaluated as a
        // section 4.2 (view-returning) candidate. A 4.2 method that passed 1 to 3 but failed later is
        // a near miss.
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
            // Section 4.12.4: entity-returning iff the return type resolves by key to the intent's
            // entity, against the service's compilation unit. Byte-identical to the simple-name check
            // when the return type's simple name is unique in the tree (the resolver's fast path).
            if (returnsEntityByKey(source, intent.entityKey())) {
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

        // Whether the method's return type resolves, by the two-stage resolver against the service's
        // compilation unit, to the entity with this key (section 4.12.4).
        private boolean returnsEntityByKey(ServiceMethodSource source, String entityKey) {
            String returnType = source.declaration().getType().asString();
            return source.declaration().findCompilationUnit()
                    .flatMap(cu -> resolver.resolveEntityKey(returnType, cu))
                    .map(entityKey::equals)
                    .orElse(false);
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
        return new Capability(intent.capabilityName(), CapabilityKind.READ, inputs, output, false, true,
                binding, annotations, null);
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

    // List-read discovery (section 4.10): the list pool over the whole service catalog. Each service
    // method is evaluated once; clean list-all matches are grouped by their element entity, so exactly
    // one per entity discovers list<E>, two or more refuse as LIST_AMBIGUOUS (paging is unmodeled, so
    // overloads are indistinguishable), and every collection read the phase does not model is a named
    // deferral. The pool is disjoint from by-id discovery, so this only ever adds capabilities.
    private ListReads discoverListReads(RepositoryCatalog repositories,
                                        List<ServiceMethodSource> serviceSources,
                                        TypeReferenceResolver resolver, List<Capability> capabilities,
                                        Map<String, String> displayByKey) {
        ListReadMatcher matcher = new ListReadMatcher(repositories);

        // The element entity is resolved to its key by the matcher (section 4.12.4); grouping by
        // display name is grouping by identity (display names are globally unique) and keeps the
        // per-entity iteration in the same alphabetical order as before.
        Map<String, List<ListCandidate>> matchesByEntity = new TreeMap<>();
        List<DeferredListRead> deferrals = new ArrayList<>();
        for (ServiceMethodSource source : serviceSources) {
            ListReadMatcher.Result result = matcher.evaluate(source, resolver);
            switch (result.kind()) {
                case MATCH -> matchesByEntity
                        .computeIfAbsent(displayByKey.getOrDefault(result.entity(), result.entity()),
                                key -> new ArrayList<>())
                        .add(new ListCandidate(source.method().toBinding(), result.paged()));
                case DEFERRED, OPAQUE -> deferrals.add(
                        new DeferredListRead(source.method().signature(), result.reason()));
                case NOT_LIST -> { }
            }
        }

        List<ListOutcome> listOutcomes = new ArrayList<>();
        for (Map.Entry<String, List<ListCandidate>> entry : matchesByEntity.entrySet()) {
            String entity = entry.getKey();
            List<ListCandidate> candidates = entry.getValue();
            if (candidates.size() == 1) {
                ListCandidate winner = candidates.get(0);
                Capability capability = buildListCapability(entity, winner);
                capabilities.add(capability);
                listOutcomes.add(ListOutcome.discovered(entity, listDiscoveredDetail(capability, winner)));
            } else {
                listOutcomes.add(ListOutcome.ambiguous(entity,
                        candidates.stream().map(ListCandidate::binding).toList()));
            }
        }
        return new ListReads(listOutcomes, deferrals);
    }

    // Section 4.10.3: output is the element type ENTITY_REF(E), outputMultiple is true, inputs are empty
    // (a forwarded Pageable is framework paging, not a capability input), and the paged variant carries a
    // `paged` annotation disclosing the page-envelope wire shape.
    private static Capability buildListCapability(String entity, ListCandidate candidate) {
        List<Annotation> annotations = candidate.paged()
                ? List.of(Annotation.flag("paged"))
                : List.of();
        return Capability.readList("list" + entity, Map.of(), Type.entityRef(entity),
                new BindingRef(candidate.binding()), annotations);
    }

    private static String listDiscoveredDetail(Capability capability, ListCandidate candidate) {
        return capability.name() + " -> " + capability.binding().value()
                + (candidate.paged() ? " (paged)" : "");
    }

    // A clean list-all candidate: its binding and whether the wire is a page envelope.
    private record ListCandidate(String binding, boolean paged) {
    }

    // The result of list discovery: the per-entity list outcomes and the named deferrals.
    private record ListReads(List<ListOutcome> outcomes, List<DeferredListRead> deferrals) {
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
            try {
                // Parse from the path (not a stream) so each unit carries its storage: the split-package
                // check (section 4.12.2, rule 3) needs the declaring source file of each entity key.
                ParseResult<CompilationUnit> result = parser.parse(file);
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
