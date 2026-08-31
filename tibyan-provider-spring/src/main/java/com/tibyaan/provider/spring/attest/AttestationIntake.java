package com.tibyaan.provider.spring.attest;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import com.tibyaan.core.ir.Attestation;
import com.tibyaan.core.ir.AttestationCategory;
import com.tibyaan.core.ir.AttestationOrigin;
import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Type;
import com.tibyaan.provider.spring.JavaTypeMapper;
import com.tibyaan.provider.spring.attest.IntakeRefusal.Cause;
import com.tibyaan.provider.spring.extractors.RepositoryCatalog;
import com.tibyaan.provider.spring.extractors.SpringDataRepo;
import com.tibyaan.provider.spring.extractors.TypeReferenceResolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The attestation intake for the PREDICATE category (AT-1) and the SHAPE category (AT-2), Volume 5
 * section 4.15.4. It reads the adopter's
 * committed record entries and, for each, either builds an attested {@link Capability} or refuses it with a
 * located cause. The boundary it enforces is the phase's soul: AN ASSERTION FILLS ONLY A SEMANTIC HOLE,
 * NEVER A STRUCTURAL ONE (section 4.15.2). The binding, the parameter types, the read-safety of the target,
 * and the output entity tie are all verified structurally around every assertion; the human's words supply
 * only the meaning structure cannot reach, and every refusal condition of section 4.15.4 is checked here,
 * loudly, at intake, never discovered later at generation time. ZERO ATTESTATION DRIFT is enforced by
 * pinning each entry to the fingerprint of the slice intake read: a mismatch is a loud STALE_ATTESTATION,
 * a missing fingerprint refuses, and carriage is gated on the comparison in the same pass that emits.
 */
public final class AttestationIntake {

    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
    private static final String ATTESTED_PREFIX = "attested_";

    // Framework paging types, excluded from the modeled input set exactly as E4 excludes a lone Pageable.
    private static final Set<String> PAGING_TYPES = Set.of("Pageable", "Sort");
    // Manual-paging idiom the tool recognizes structurally, so the paging mark can never hide a domain
    // parameter (Volume 5 section 4.15.2): an Integer page/size/offset, or a String/enum ordering name.
    private static final Set<String> PAGE_NUMBER_NAMES = Set.of(
            "page", "pagenum", "pagenumber", "pagesize", "size", "pageoffset", "offset", "limit");
    private static final Set<String> ORDER_NAMES = Set.of(
            "orderby", "sortby", "orderdir", "sortdir", "direction");
    private static final Set<String> CONTAINERS =
            Set.of("List", "Set", "Collection", "Iterable", "Page", "Slice");
    private static final Set<String> PK_READS = Set.of("findById", "getById", "getReferenceById");
    private static final Set<Type> ID_TYPES =
            Set.of(Primitive.LONG, Primitive.UUID, Primitive.STRING, Primitive.INTEGER);

    private final List<CompilationUnit> units;
    private final TypeReferenceResolver resolver;
    private final RepositoryCatalog catalog;
    private final JavaTypeMapper typeMapper;
    private final ReadSafety readSafety;
    private final Map<String, String> displayByKey;

    public AttestationIntake(List<CompilationUnit> units, TypeReferenceResolver resolver,
                             RepositoryCatalog catalog, JavaTypeMapper typeMapper,
                             Map<String, String> displayByKey) {
        this.units = units;
        this.resolver = resolver;
        this.catalog = catalog;
        this.typeMapper = typeMapper;
        this.readSafety = new ReadSafety(catalog);
        this.displayByKey = displayByKey;
    }

    /** The outcome of an intake pass: the attested capabilities and every located refusal. */
    public record Result(List<Capability> attested, List<IntakeRefusal> refusals) {
    }

    /**
     * Computes the current structural fingerprint of a target's slice (Volume 5 section 4.15.4). This is
     * what the {@code tibyan attest} CLI stamps into an entry against the current code; intake recomputes
     * and compares it on every scan. Empty when the target does not resolve.
     */
    public Optional<String> fingerprintFor(String target) {
        return resolveTarget(target).map(resolved -> SliceFingerprint.compute(sliceMembers(resolved)));
    }

    /**
     * Runs intake over the record entries. Names are checked for uniqueness against the already-emitted
     * verified capability names plus every attested name minted earlier in this pass.
     */
    public Result run(List<AttestationRecord> records, Set<String> existingCapabilityNames) {
        List<Capability> attested = new ArrayList<>();
        List<IntakeRefusal> refusals = new ArrayList<>();
        Set<String> takenNames = new java.util.HashSet<>(existingCapabilityNames);
        Set<String> takenTargets = new java.util.HashSet<>();

        for (AttestationRecord record : records) {
            evaluate(record, attested, refusals, takenNames, takenTargets);
        }
        return new Result(attested, refusals);
    }

    private void evaluate(AttestationRecord record, List<Capability> attested, List<IntakeRefusal> refusals,
                          Set<String> takenNames, Set<String> takenTargets) {
        String target = record.target() == null ? "(none)" : record.target();

        // Condition 1: the target resolves to exactly one in-tree declaration.
        Optional<ResolvedTarget> resolved = resolveTarget(target);
        if (resolved.isEmpty()) {
            refusals.add(refusal(target, record.name(), Cause.UNRESOLVED_TARGET,
                    "the target '" + target + "' does not resolve to exactly one in-tree method "
                            + "declaration (section 4.15.4, condition 1)"));
            return;
        }
        ResolvedTarget resolvedTarget = resolved.get();
        if (!takenTargets.add(resolvedTarget.binding())) {
            refusals.add(refusal(target, record.name(), Cause.DUPLICATE_TARGET,
                    "another entry already targets '" + resolvedTarget.binding() + "'; at most one "
                            + "attestation per read (section 4.15.4, condition 1)"));
            return;
        }

        // Condition 2: category is known, and its sub-phase is ratified and built. AT-1 built predicate,
        // AT-2 builds shape. The compound entry (both), which fills a predicate hole and a shape hole at
        // one target with one minted name, is its own step and stays refused: AT-2's ratified scope is
        // "adds one category", and a compound target is not that category.
        AttestationCategory category;
        try {
            category = AttestationCategory.fromToken(record.category());
        } catch (RuntimeException e) {
            refusals.add(refusal(target, record.name(), Cause.CATEGORY_MISMATCH,
                    "unknown category token '" + record.category() + "' (section 4.15.4, condition 2)"));
            return;
        }
        if (category == AttestationCategory.BOTH) {
            refusals.add(refusal(target, record.name(), Cause.CATEGORY_NOT_BUILT,
                    "category 'both' is not yet ratified and built; AT-1 built predicate and AT-2 builds "
                            + "shape, and the compound entry that fills both holes at one target is a "
                            + "separate step (section 4.15.4, condition 2)"));
            return;
        }

        // Condition 6, provenance (checked early so a draft or unattested entry never reaches structure).
        AttestationRecord.Provenance provenance = record.provenance();
        if (provenance == null || isBlank(provenance.assertedBy())) {
            refusals.add(refusal(target, record.name(), Cause.PROVENANCE,
                    "provenance.assertedBy is empty (section 4.15.4, condition 6)"));
            return;
        }
        AttestationOrigin origin;
        try {
            origin = AttestationOrigin.fromToken(provenance.origin());
        } catch (RuntimeException e) {
            refusals.add(refusal(target, record.name(), Cause.PROVENANCE,
                    "origin '" + provenance.origin() + "' is not a shipping origin; a draft ('ai-draft') "
                            + "or unknown origin is refused (section 4.15.4, condition 6; the AT-4 boundary "
                            + "binds now even though the assist is unbuilt)"));
            return;
        }
        if (isBlank(provenance.date())) {
            refusals.add(refusal(target, record.name(), Cause.PROVENANCE,
                    "provenance.date is empty (section 4.15.4, condition 6)"));
            return;
        }
        if (isBlank(provenance.fingerprint())) {
            refusals.add(refusal(target, record.name(), Cause.MISSING_FINGERPRINT,
                    "the entry has no fingerprint; run tibyan attest to stamp it against the current code "
                            + "(section 4.15.4, condition 6). It does not ship on this run."));
            return;
        }

        // Condition 3, part 1: every parameter type maps; the modeled inputs are built in declared order,
        // framework paging types (Pageable, Sort) excluded exactly as E4 excludes a lone Pageable.
        Map<String, Type> inputs = new LinkedHashMap<>();
        for (Parameter parameter : resolvedTarget.method().getParameters()) {
            String typeSimple = lastSegment(parameter.getType().asString());
            if (PAGING_TYPES.contains(typeSimple)) {
                continue;
            }
            Optional<Type> mapped = typeMapper.map(parameter.getType().asString());
            if (mapped.isEmpty()) {
                refusals.add(refusal(target, record.name(), Cause.STRUCTURAL_FRAME,
                        "parameter '" + parameter.getNameAsString() + "' has type '"
                                + parameter.getType().asString() + "', which does not map to a modeled "
                                + "type (section 4.15.4, condition 3)"));
                return;
            }
            inputs.put(parameter.getNameAsString(), mapped.get());
        }

        // Condition 3, part 2: read-safety over the fingerprinted slice. A visible write refuses; a
        // mutating body is a structural contradiction (section 4.15.2, section 4.15.7).
        Optional<String> unsafe = readSafety.check(resolvedTarget.declaringType(), resolvedTarget.method(),
                List.of());
        if (unsafe.isPresent()) {
            refusals.add(refusal(target, record.name(), Cause.READ_SAFETY, unsafe.get()));
            return;
        }

        // Condition 3, part 3: the output. For a PREDICATE entry the element tie must be structurally
        // provable, because a predicate assertion fills the predicate hole only; a target whose tie is
        // unprovable has a shape hole as well and belongs to category shape. For a SHAPE entry (AT-2) the
        // tie is exactly the hole the assertion fills, so the frame is category B's own: a local
        // persistence tie, an asserted subject entity that exists, no structural contradiction, and, when
        // the entry claims a by-primary-key identifier, a load of that entity actually present.
        Type output;
        boolean multiple;
        if (category == AttestationCategory.SHAPE) {
            ShapeFrame frame = shapeFrame(record, resolvedTarget, target, inputs);
            if (frame.isRefused()) {
                refusals.add(frame.refusal());
                return;
            }
            output = frame.output();
            multiple = frame.multiple();
        } else {
            Optional<EntityTie> tie = resolveOutputTie(resolvedTarget);
            if (tie.isEmpty()) {
                refusals.add(refusal(target, record.name(), Cause.CATEGORY_MISMATCH,
                        "the read's output entity tie is not structurally provable, so it has a shape hole "
                                + "a predicate assertion cannot fill; use category shape (AT-2), or both "
                                + "for a target that also has a predicate hole (section 4.15.4, "
                                + "condition 2; section 4.15.2)"));
                return;
            }
            output = Type.entityRef(displayByKey.getOrDefault(tie.get().entityKey(), tie.get().entityKey()));
            multiple = tie.get().multiple();
        }

        // Condition 4: coverage is complete and exact. Keyset(parameterMeanings) equals the input set; each
        // input carries a sentence, a restricted paging mark, or the id mark; no key names a non-input.
        Optional<IntakeRefusal> coverage = checkCoverage(record, inputs, resolvedTarget, target);
        if (coverage.isPresent()) {
            refusals.add(coverage.get());
            return;
        }

        // Condition 5: the minted name is valid, unprefixed by the human, and unique across all capabilities.
        String rawName = record.name();
        if (isBlank(rawName) || !NAME.matcher(rawName).matches() || rawName.startsWith(ATTESTED_PREFIX)) {
            refusals.add(refusal(target, rawName, Cause.NAME,
                    "the minted name '" + rawName + "' is empty, not a valid identifier, or already carries "
                            + "the reserved '" + ATTESTED_PREFIX + "' prefix (section 4.15.4, condition 5)"));
            return;
        }
        String minted = ATTESTED_PREFIX + rawName;
        if (!takenNames.add(minted)) {
            refusals.add(refusal(target, minted, Cause.NAME,
                    "the minted name '" + minted + "' collides with an existing capability (section 4.15.4, "
                            + "condition 5)"));
            return;
        }

        // The lifecycle: recompute the fingerprint over the current slice and compare. A mismatch is a loud
        // STALE_ATTESTATION; a match carries. There is no path that emits from an entry the tool did not
        // itself fingerprint on this run's code.
        String currentFingerprint = SliceFingerprint.compute(sliceMembers(resolvedTarget));
        if (!currentFingerprint.equals(provenance.fingerprint())) {
            refusals.add(refusal(target, minted, Cause.STALE_ATTESTATION,
                    "the code changed since attestation: the slice fingerprint is now '" + currentFingerprint
                            + "', not the stored '" + provenance.fingerprint() + "'. The capability returns "
                            + "to refused; re-run tibyan attest to re-confirm (section 4.15.4, lifecycle)."));
            return;
        }

        Map<String, String> parameterMeanings = new LinkedHashMap<>();
        for (String input : inputs.keySet()) {
            parameterMeanings.put(input, record.parameters().get(input));
        }
        // The subject entity rides the component only where it is the assertion (shape); a predicate entry
        // tied structurally carries none, so its serialized form is unchanged by AT-2.
        String subject = category == AttestationCategory.SHAPE ? record.subjectEntity() : null;
        Attestation attestation = new Attestation(category, record.resultSemantic(),
                parameterMeanings, subject, provenance.assertedBy(), origin, provenance.date(),
                provenance.fingerprint());
        attested.add(Capability.attested(minted, inputs, output, multiple,
                new BindingRef(resolvedTarget.binding()), List.of(), attestation));
    }

    /**
     * The frame a CATEGORY B (SHAPE) assertion is allowed to sit inside (AT-2, Volume 5 section 4.15.2).
     * The assertion says what the read RETURNS; everything around that claim is still proven here, and a
     * frame that does not verify keeps the read refused with a located reason.
     *
     * <p>The four checks, in the order a refusal is most informative: the LOCAL-TIE PRECONDITION (a shape
     * assertion is only honest over a body whose persistence the read-safety check could actually inspect,
     * so a delegation-built wrapper routes to AT-3 by name, never to AT-2); the asserted subject entity
     * exists in the model; no structural contradiction (a body whose SINGLE repository access provably
     * reads a different entity refuses, section 4.15.7); and, when the entry claims a by-primary-key
     * identifier through the reserved id mark, that load is structurally present, because the assertion
     * covers the enrichment and never the load.
     */
    private ShapeFrame shapeFrame(AttestationRecord record, ResolvedTarget resolvedTarget, String target,
                                  Map<String, Type> inputs) {
        // The category boundary, checked first because it is the most actionable refusal: each hole is
        // filled by its own category or stays refused, and a predicate assertion may not ride in on a
        // shape entry. A parameter covered by a MEANING SENTENCE is a predicate claim; only the reserved
        // paging and id marks are structural coverage. So a target with any sentence-covered input has a
        // predicate hole too, and belongs to the compound category rather than to shape alone.
        for (String input : inputs.keySet()) {
            String coverage = record.parameters().get(input);
            if (coverage != null && !Attestation.PAGING_MARK.equals(coverage)
                    && !Attestation.ID_MARK.equals(coverage)) {
                return ShapeFrame.refused(refusal(target, record.name(), Cause.CATEGORY_MISMATCH,
                        "input '" + input + "' is covered by a meaning sentence, which is a PREDICATE "
                                + "claim, so this target has a predicate hole as well as a shape hole. One "
                                + "entry fills both only as category 'both', which is a separate step and "
                                + "not yet built; a shape entry never carries a predicate assertion "
                                + "(section 4.15.2, the category taxonomy)"));
            }
        }

        // The asserted subject: present, and an entity of this model. The tool never invents it, and core
        // rule 8 re-checks it, but intake refuses first so the failure is located at the entry.
        String asserted = record.subjectEntity();
        if (isBlank(asserted)) {
            return ShapeFrame.refused(refusal(target, record.name(), Cause.STRUCTURAL_FRAME,
                    "a shape assertion must name the subject entity it claims the read returns, and this "
                            + "entry has no subjectEntity (section 4.15.2, category B)"));
        }
        Optional<String> assertedKey = keyForDisplay(asserted);
        if (assertedKey.isEmpty()) {
            return ShapeFrame.refused(refusal(target, record.name(), Cause.STRUCTURAL_FRAME,
                    "the asserted subjectEntity '" + asserted + "' is not an entity in the model; an "
                            + "assertion names what the tool already modeled, it never introduces an "
                            + "entity (section 4.15.2, category B)"));
        }

        // The local-tie precondition. Every repository-typed access the read-safety check can inspect
        // counts; zero of them means the real work is a delegation the tool never saw, so its read-safety
        // is structurally unprovable and only a human's own read-only affirmation could carry it. That is
        // category C, by name, and it stays refused until AT-3 is ratified and built.
        List<RepoAccess> accesses = repositoryAccesses(resolvedTarget);
        if (accesses.isEmpty()) {
            return ShapeFrame.refused(refusal(target, record.name(), Cause.CATEGORY_NOT_BUILT,
                    "the target has no in-tree persistence access, so there is no local tie for the "
                            + "read-safety check to inspect and its read-only-ness rests entirely on a "
                            + "human's word. That is category C (BOUNDARY, AT-3), which is deferred by "
                            + "name and not yet ratified; it is never accepted as shape (section 4.15.2, "
                            + "category B, the local-tie precondition)"));
        }

        // Non-contradiction, in exactly the ratified scope: when the body has a SINGLE repository access
        // and that access provably reads a different entity, structure contradicts the assertion. An
        // aggregate legitimately touches other repositories to enrich, so a multi-access body is not a
        // contradiction and is not treated as one.
        if (accesses.size() == 1) {
            String tied = accesses.get(0).entityKey();
            if (tied != null && !tied.equals(assertedKey.get())) {
                return ShapeFrame.refused(refusal(target, record.name(), Cause.STRUCTURAL_CONTRADICTION,
                        "the assertion says this read returns '" + asserted + "', but the body's only "
                                + "repository access provably reads '"
                                + displayByKey.getOrDefault(tied, tied) + "'. Structure contradicts the "
                                + "assertion, so the assertion is refused, not carried (section 4.15.7)"));
            }
        }

        // The aggregate obligation. The reserved id mark means "a structurally-proven by-primary-key
        // identifier", so an entry that claims it must have that load actually present, of the asserted
        // entity. Without this the mark would be a second place a semantic hole could be filled by
        // assertion, which is the one thing the category boundary forbids.
        if (claimsIdMark(record, inputs)) {
            boolean loaded = accesses.stream().anyMatch(access ->
                    PK_READS.contains(access.methodName()) && assertedKey.get().equals(access.entityKey()));
            if (!loaded) {
                return ShapeFrame.refused(refusal(target, record.name(), Cause.STRUCTURAL_FRAME,
                        "an input carries the reserved id mark, so this entry claims a by-primary-key read "
                                + "of '" + asserted + "', but no by-primary-key load of that entity is "
                                + "present in the body. The assertion covers the enrichment, never the "
                                + "load (section 4.15.2, category B)"));
            }
        }

        // Multiplicity is never asserted. Structure decides it: the proven tie where there is one, else the
        // declared return type being a recognized container.
        Optional<EntityTie> tie = resolveOutputTie(resolvedTarget);
        boolean multiple = tie.isPresent() && assertedKey.get().equals(tie.get().entityKey())
                ? tie.get().multiple()
                : returnsContainer(resolvedTarget);
        return ShapeFrame.of(Type.entityRef(asserted), multiple);
    }

    /** The outcome of the category B frame check: the attested output, or the refusal that stops it. */
    private record ShapeFrame(Type output, boolean multiple, IntakeRefusal refusal) {

        static ShapeFrame refused(IntakeRefusal refusal) {
            return new ShapeFrame(null, false, refusal);
        }

        static ShapeFrame of(Type output, boolean multiple) {
            return new ShapeFrame(output, multiple, null);
        }

        boolean isRefused() {
            return refusal != null;
        }
    }

    /** One repository-typed access in the target's body: the method called and the entity it reads. */
    private record RepoAccess(String methodName, String entityKey) {
    }

    // Every call in the target's body whose receiver is a repository-typed field of the declaring class.
    // The entity key is null when the repository's type argument does not resolve to an in-tree entity,
    // which is not a disqualification: an unresolvable element type is exactly the hole a shape assertion
    // fills, and the ACCESS still counts as the local tie the read-safety check inspected.
    private List<RepoAccess> repositoryAccesses(ResolvedTarget resolvedTarget) {
        Map<String, String> repoFields = repositoryFields(resolvedTarget.declaringType());
        List<RepoAccess> accesses = new ArrayList<>();
        for (MethodCallExpr call : resolvedTarget.method().findAll(MethodCallExpr.class)) {
            String field = receiverFieldName(call);
            String fieldType = field == null ? null : repoFields.get(field);
            // repositoryFields maps EVERY field to its declared type, so the receiver has to be checked
            // against the repository catalog here. Without this a call on a service or client field would
            // count as a local persistence tie, and a delegation would be accepted as category B instead
            // of routing to AT-3, which is the precise thing the precondition exists to prevent.
            if (fieldType == null || !catalog.isRepositoryType(fieldType)) {
                continue;
            }
            String entityKey = catalog.springDataRepo(fieldType)
                    .map(SpringDataRepo::entityKey)
                    .orElse(null);
            accesses.add(new RepoAccess(call.getNameAsString(), entityKey));
        }
        return accesses;
    }

    private boolean claimsIdMark(AttestationRecord record, Map<String, Type> inputs) {
        for (String input : inputs.keySet()) {
            if (Attestation.ID_MARK.equals(record.parameters().get(input))) {
                return true;
            }
        }
        return false;
    }

    private boolean returnsContainer(ResolvedTarget resolvedTarget) {
        return resolvedTarget.method().getType() instanceof ClassOrInterfaceType returnClass
                && CONTAINERS.contains(returnClass.getNameAsString());
    }

    // The model key of an entity by its display name (the reverse of displayByKey). A key that carries no
    // display entry is matched directly, so an unrenamed entity resolves by its own key too.
    private Optional<String> keyForDisplay(String display) {
        for (Map.Entry<String, String> entry : displayByKey.entrySet()) {
            if (entry.getValue().equals(display) || entry.getKey().equals(display)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    // Condition 4, the coverage check. The keyset must equal the input set; a paging mark is permitted only
    // on a structurally recognized paging parameter; the id mark only on an id-typed parameter; anything
    // else must be a non-empty human sentence.
    private Optional<IntakeRefusal> checkCoverage(AttestationRecord record, Map<String, Type> inputs,
                                                  ResolvedTarget resolvedTarget, String target) {
        Map<String, String> parameters = record.parameters();
        for (String key : parameters.keySet()) {
            if (!inputs.containsKey(key)) {
                return Optional.of(refusal(target, record.name(), Cause.COVERAGE,
                        "parameterMeanings covers '" + key + "', which is not a modeled input of the target "
                                + "(section 4.15.4, condition 4)"));
            }
        }
        for (Map.Entry<String, Type> input : inputs.entrySet()) {
            String name = input.getKey();
            String coverage = parameters.get(name);
            if (isBlank(coverage)) {
                return Optional.of(refusal(target, record.name(), Cause.COVERAGE,
                        "input '" + name + "' is uncovered in parameterMeanings; an uncovered name in a "
                                + "schema is the modeled-opaque claim the design rejects (condition 4)"));
            }
            if (Attestation.PAGING_MARK.equals(coverage)) {
                if (!isRecognizedPaging(name, input.getValue())) {
                    return Optional.of(refusal(target, record.name(), Cause.COVERAGE,
                            "input '" + name + "' carries the paging mark but is not a structurally "
                                    + "recognized paging or ordering parameter; a domain parameter cannot "
                                    + "hide behind the mark (section 4.15.2, condition 4)"));
                }
            } else if (Attestation.ID_MARK.equals(coverage)) {
                if (!ID_TYPES.contains(input.getValue())) {
                    return Optional.of(refusal(target, record.name(), Cause.COVERAGE,
                            "input '" + name + "' carries the id mark but its type is not an identifier "
                                    + "type (section 4.15.2, condition 4)"));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isRecognizedPaging(String name, Type type) {
        String lower = name.toLowerCase();
        if (type == Primitive.INTEGER && PAGE_NUMBER_NAMES.contains(lower)) {
            return true;
        }
        return type == Primitive.STRING && ORDER_NAMES.contains(lower);
    }

    // The fingerprinted slice of a target (Volume 5 section 4.15.4): the target method declaration, the
    // compilation-unit imports, for every repository-typed field the body calls that field declaration and
    // the repository interface declaration, and the target's own return type where it resolves to an
    // in-tree declaration (the wrapper or projection class, a listed slice member). If the frame changes,
    // the hash changes.
    //
    // The slice is ONE function of the target, not of the asserting category (AT-2 build decision). A
    // category-dependent slice would give one target two fingerprints, so changing an entry's category
    // would silently re-stamp it, and the CLI's fingerprintFor(target) contract assumes a target has one
    // slice. The return type is frame-load-bearing for a predicate entry too, since the output tie reads
    // it, so pinning it strengthens AT-1 rather than perturbing it.
    private Map<String, Node> sliceMembers(ResolvedTarget resolvedTarget) {
        Map<String, Node> members = new TreeMap<>();
        String fqcn = resolvedTarget.declaringType().getFullyQualifiedName().orElse(resolvedTarget.binding());
        members.put(fqcn + "#" + resolvedTarget.method().getNameAsString(), resolvedTarget.method());
        resolvedTarget.method().findCompilationUnit().ifPresent(cu -> {
            for (ImportDeclaration importDeclaration : cu.getImports()) {
                members.put("import:" + importDeclaration.getNameAsString(), importDeclaration);
            }
        });
        Map<String, String> repoFields = repositoryFields(resolvedTarget.declaringType());
        resolvedTarget.method().findAll(MethodCallExpr.class).forEach(call -> {
            String field = receiverFieldName(call);
            String repoType = field == null ? null : repoFields.get(field);
            if (repoType != null) {
                repositoryFieldDeclaration(resolvedTarget.declaringType(), field)
                        .ifPresent(decl -> members.put(fqcn + "#field:" + field, decl));
                catalog.declaration(repoType).ifPresent(decl -> members.put(
                        decl.getFullyQualifiedName().orElse(repoType), decl));
            }
        });
        returnTypeDeclaration(resolvedTarget).ifPresent(decl -> members.put(
                decl.getFullyQualifiedName().orElse(decl.getNameAsString()), decl));
        return members;
    }

    // The target's return type resolved to an in-tree class or interface declaration: the wrapper, the
    // projection, or the view whose shape a category B assertion describes. Empty for a library type or a
    // primitive, and empty when the name does not resolve to exactly one in-tree type.
    private Optional<ClassOrInterfaceDeclaration> returnTypeDeclaration(ResolvedTarget resolvedTarget) {
        Optional<CompilationUnit> cu = resolvedTarget.method().findCompilationUnit();
        if (cu.isEmpty()) {
            return Optional.empty();
        }
        String simple = lastSegment(stripTypeArguments(resolvedTarget.method().getType().asString()));
        List<ClassOrInterfaceDeclaration> matches = new ArrayList<>();
        for (CompilationUnit unit : units) {
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (type.getNameAsString().equals(simple)) {
                    matches.add(type);
                }
            }
        }
        // Exactly one, or nothing. A colliding simple name is not narrowed by guessing a unit order: the
        // member is left out of the slice rather than pinned to an arbitrary one of two declarations,
        // which would make the fingerprint depend on scan order and break determinism (rule 10).
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private static String stripTypeArguments(String type) {
        int open = type.indexOf('<');
        return open < 0 ? type : type.substring(0, open);
    }

    // The output entity tie: a direct entity return, a collection of an entity, or (the wrapper case) a
    // single Spring Data repository-of-E access in the body (the T2 tie). Null when unprovable.
    private Optional<EntityTie> resolveOutputTie(ResolvedTarget resolvedTarget) {
        MethodDeclaration method = resolvedTarget.method();
        Optional<CompilationUnit> cu = method.findCompilationUnit();
        if (cu.isEmpty()) {
            return Optional.empty();
        }
        String returnType = method.getType().asString();
        Optional<String> direct = resolver.resolveEntityKey(returnType, cu.get());
        if (direct.isPresent()) {
            return Optional.of(new EntityTie(direct.get(), false));
        }
        if (method.getType() instanceof ClassOrInterfaceType returnClass
                && CONTAINERS.contains(returnClass.getNameAsString())) {
            Optional<String> element = singleTypeArgument(returnClass)
                    .flatMap(arg -> resolver.resolveEntityKey(arg, cu.get()));
            if (element.isPresent()) {
                return Optional.of(new EntityTie(element.get(), true));
            }
        }
        Map<String, String> repoFields = repositoryFields(resolvedTarget.declaringType());
        String tiedKey = null;
        boolean multiple = false;
        for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
            String field = receiverFieldName(call);
            String repoType = field == null ? null : repoFields.get(field);
            if (repoType == null) {
                continue;
            }
            Optional<SpringDataRepo> repo = catalog.springDataRepo(repoType);
            if (repo.isEmpty() || repo.get().entityKey() == null) {
                continue;
            }
            if (tiedKey != null && !tiedKey.equals(repo.get().entityKey())) {
                return Optional.empty();
            }
            tiedKey = repo.get().entityKey();
            multiple = multiple || accessReturnsCollection(repoType, call.getNameAsString());
        }
        return tiedKey == null ? Optional.empty() : Optional.of(new EntityTie(tiedKey, multiple));
    }

    // Whether a repository access returns a collection (so the tied read is a list), decided from the
    // framework contract or the method's declared return type: findAll is a collection, a by-primary-key
    // read is a single, any other method is a collection only when its declared return is a container.
    private boolean accessReturnsCollection(String repoType, String accessName) {
        if (accessName.equals("findAll")) {
            return true;
        }
        if (PK_READS.contains(accessName)) {
            return false;
        }
        return catalog.declaration(repoType)
                .map(decl -> decl.getMethods().stream()
                        .filter(m -> m.getNameAsString().equals(accessName))
                        .anyMatch(m -> CONTAINERS.contains(lastSegment(m.getType().asString()))))
                .orElse(false);
    }

    private Optional<ResolvedTarget> resolveTarget(String target) {
        int hash = target.indexOf('#');
        int open = target.indexOf('(', hash);
        int close = target.lastIndexOf(')');
        if (hash < 0 || open < 0 || close < open) {
            return Optional.empty();
        }
        String fqcn = target.substring(0, hash).trim();
        String methodName = target.substring(hash + 1, open).trim();
        String paramList = target.substring(open + 1, close).trim();
        List<String> paramSimple = new ArrayList<>();
        if (!paramList.isEmpty()) {
            for (String part : paramList.split(",")) {
                paramSimple.add(lastSegment(part.trim()));
            }
        }
        List<ResolvedTarget> matches = new ArrayList<>();
        for (CompilationUnit unit : units) {
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (!type.getFullyQualifiedName().map(fqcn::equals).orElse(false)) {
                    continue;
                }
                for (MethodDeclaration method : type.getMethods()) {
                    if (method.getNameAsString().equals(methodName)
                            && parameterTypesMatch(method, paramSimple)) {
                        matches.add(new ResolvedTarget(type, method,
                                type.getNameAsString() + "#" + methodName));
                    }
                }
            }
        }
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private static boolean parameterTypesMatch(MethodDeclaration method, List<String> expected) {
        if (method.getParameters().size() != expected.size()) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            String actual = lastSegment(method.getParameter(i).getType().asString());
            if (!actual.equals(expected.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, String> repositoryFields(ClassOrInterfaceDeclaration type) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldDeclaration declaration : type.getFields()) {
            String fieldType = lastSegment(declaration.getElementType().asString());
            for (VariableDeclarator variable : declaration.getVariables()) {
                fields.put(variable.getNameAsString(), fieldType);
            }
        }
        return fields;
    }

    private static Optional<FieldDeclaration> repositoryFieldDeclaration(ClassOrInterfaceDeclaration type,
                                                                         String fieldName) {
        for (FieldDeclaration declaration : type.getFields()) {
            for (VariableDeclarator variable : declaration.getVariables()) {
                if (variable.getNameAsString().equals(fieldName)) {
                    return Optional.of(declaration);
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> singleTypeArgument(ClassOrInterfaceType type) {
        return type.getTypeArguments()
                .filter(args -> args.size() == 1)
                .map(args -> args.get(0).asString());
    }

    private static String receiverFieldName(MethodCallExpr call) {
        Optional<Expression> scope = call.getScope();
        if (scope.isEmpty()) {
            return null;
        }
        Expression s = scope.get();
        if (s.isNameExpr()) {
            return s.asNameExpr().getNameAsString();
        }
        if (s.isFieldAccessExpr() && s.asFieldAccessExpr().getScope().isThisExpr()) {
            return s.asFieldAccessExpr().getNameAsString();
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String lastSegment(String name) {
        int open = name.indexOf('<');
        String base = open >= 0 ? name.substring(0, open) : name;
        int dot = base.lastIndexOf('.');
        return (dot >= 0 ? base.substring(dot + 1) : base).trim();
    }

    private static IntakeRefusal refusal(String target, String name, Cause cause, String detail) {
        return new IntakeRefusal(target, name, cause, detail);
    }

    private record ResolvedTarget(ClassOrInterfaceDeclaration declaringType, MethodDeclaration method,
                                  String binding) {
    }

    private record EntityTie(String entityKey, boolean multiple) {
    }
}
