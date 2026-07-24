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
 * The attestation intake for the PREDICATE category (AT-1, Volume 5 section 4.15.4). It reads the adopter's
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

        // Condition 2: category is known, and its sub-phase is ratified and built (AT-1 = predicate only).
        AttestationCategory category;
        try {
            category = AttestationCategory.fromToken(record.category());
        } catch (RuntimeException e) {
            refusals.add(refusal(target, record.name(), Cause.CATEGORY_MISMATCH,
                    "unknown category token '" + record.category() + "' (section 4.15.4, condition 2)"));
            return;
        }
        if (category != AttestationCategory.PREDICATE) {
            refusals.add(refusal(target, record.name(), Cause.CATEGORY_NOT_BUILT,
                    "category '" + category.token() + "' is not yet ratified and built; AT-1 builds only "
                            + "the predicate category (section 4.15.4, condition 2)"));
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

        // Condition 3, part 3: the output entity tie. A predicate entry against a target whose element tie
        // is not structurally provable has a shape hole too, and refuses to shape or both (AT-2).
        Optional<EntityTie> tie = resolveOutputTie(resolvedTarget);
        if (tie.isEmpty()) {
            refusals.add(refusal(target, record.name(), Cause.CATEGORY_MISMATCH,
                    "the read's output entity tie is not structurally provable, so it has a shape hole a "
                            + "predicate assertion cannot fill; use category shape or both (AT-2, not yet "
                            + "built) (section 4.15.4, condition 2; section 4.15.2)"));
            return;
        }
        String subjectDisplay = displayByKey.getOrDefault(tie.get().entityKey(), tie.get().entityKey());
        Type output = Type.entityRef(subjectDisplay);

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
        Attestation attestation = new Attestation(AttestationCategory.PREDICATE, record.resultSemantic(),
                parameterMeanings, null, provenance.assertedBy(), origin, provenance.date(),
                provenance.fingerprint());
        attested.add(Capability.attested(minted, inputs, output, tie.get().multiple(),
                new BindingRef(resolvedTarget.binding()), List.of(), attestation));
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

    // The fingerprinted slice for a PREDICATE target (Volume 5 section 4.15.4): the target method
    // declaration, the compilation-unit imports, and, for every repository-typed field the body calls,
    // that field declaration and the repository interface declaration. If the frame changes, the hash
    // changes.
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
        return members;
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
