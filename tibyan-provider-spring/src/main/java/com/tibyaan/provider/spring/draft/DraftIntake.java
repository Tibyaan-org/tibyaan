package com.tibyaan.provider.spring.draft;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.tibyaan.core.ir.Annotation;
import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Review;
import com.tibyaan.core.ir.ReviewLabel;
import com.tibyaan.core.ir.Type;
import com.tibyaan.core.semantic.SemanticValidator;
import com.tibyaan.provider.spring.JavaTypeMapper;
import com.tibyaan.provider.spring.attest.AttestationIntake;
import com.tibyaan.provider.spring.attest.ReadSafety;
import com.tibyaan.provider.spring.draft.DraftRefusal.Cause;
import com.tibyaan.provider.spring.extractors.EnumInputValues;
import com.tibyaan.provider.spring.extractors.RepositoryCatalog;
import com.tibyaan.provider.spring.extractors.ServiceMethodSource;
import com.tibyaan.provider.spring.extractors.TypeReferenceResolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The draft intake (phase DL-1, Volume 5 section 4.17.3): what the provider proves about an APPROVED entry of
 * {@code tibyan/drafts.json} before it enters the IR as a reviewed capability. It runs at scan, after the
 * attestation intake and before the controller-authority composition, and for every approved entry it either
 * admits a {@code reviewed_} capability or refuses with a located cause. A pending entry is counted in its own
 * queue and produces nothing; a rejected entry produces nothing and is not a refusal.
 *
 * <p>What the tool guarantees for a reviewed capability is carriage, the fingerprint pin, label drift and the
 * gate, never truth (section 4.17.9): it never upgrades a label to a proof and never authors a description.
 * The conditions, checked in order, the FIRST failure refusing: RV-A the target resolves (to one public
 * instance method of one concrete service); RV-B the stamped fingerprint still matches; RV-C the recomputed
 * label equals the recorded one; RV-D the target is not already proven (a discovered binding or an admitted
 * attestation target); RV-E every input binds; RV-F the output is describable (never a refusal); RV-G the
 * human half is present; RV-H the label maps to the kind.
 */
public final class DraftIntake {

    /** The reserved name prefix the tool applies (core rule 13). */
    public static final String REVIEWED_PREFIX = SemanticValidator.REVIEWED_PREFIX;

    /**
     * The annotation that carries the reviewer's description to the backends, its single value the sentence
     * verbatim. An attested capability carries its sentence inside the Attestation component; a reviewed one
     * has no such component slot, so the existing Annotation hook carries it, no IR growth.
     */
    public static final String DESCRIPTION_ANNOTATION = "description";

    /** The flag on a reviewed capability whose method returns {@code void}: there is no output to describe. */
    public static final String VOID_ANNOTATION = "void";

    /**
     * The annotation on a reviewed capability whose output is neither a primitive nor an in-tree entity; its
     * single value is the Java return type as written. The output renders as the permissive object (Volume 6
     * section 4.1, decision D5): a reviewer who approved a DTO-returning read accepted that shape.
     */
    public static final String OPAQUE_OUTPUT_ANNOTATION = "opaqueOutput";

    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
    private static final Set<String> PAGING_TYPES = Set.of("Pageable", "Sort");
    private static final Set<String> CONTAINERS = Set.of("List", "Set", "Collection");
    private static final Set<String> ORIGINS = Set.of(DraftEntry.ORIGIN_HUMAN, DraftEntry.ORIGIN_AI_DRAFT);

    private final AttestationIntake attestationIntake;
    private final TypeReferenceResolver resolver;
    private final JavaTypeMapper typeMapper;
    private final EnumInputValues enumInputValues;
    private final Map<String, String> displayByKey;
    private final DraftLabeler labeler;
    private final List<ServiceMethodSource> sources;

    public DraftIntake(List<CompilationUnit> units, TypeReferenceResolver resolver, RepositoryCatalog catalog,
                       JavaTypeMapper typeMapper, Map<String, String> displayByKey,
                       List<ServiceMethodSource> sources) {
        this.attestationIntake = new AttestationIntake(units, resolver, catalog, typeMapper, displayByKey);
        this.resolver = resolver;
        this.typeMapper = typeMapper;
        this.enumInputValues = new EnumInputValues(resolver, units);
        this.displayByKey = displayByKey;
        this.labeler = new DraftLabeler(new ReadSafety(catalog), typeMapper, attestationIntake);
        this.sources = sources;
    }

    /**
     * The outcome of an intake pass: the reviewed capabilities, every located refusal, and the count of
     * entries still pending review. The three are separate because they are three different things: a decision
     * a named human made and the tool carried, an approval the tool refused, and a queue nobody has decided.
     */
    public record Result(List<Capability> reviewed, List<DraftRefusal> refusals, int pending) {

        public static Result empty() {
            return new Result(List.of(), List.of(), 0);
        }
    }

    /**
     * Runs intake over the file's entries.
     *
     * @param entries the entries as read from the file, in file order
     * @param proven  the capabilities already in the model, discovered and attested; RV-D and name uniqueness
     *                are checked against them
     */
    public Result run(List<DraftEntry> entries, List<Capability> proven) {
        List<Capability> reviewed = new ArrayList<>();
        List<DraftRefusal> refusals = new ArrayList<>();
        int pending = 0;

        Map<String, Capability> provenByBinding = new HashMap<>();
        Set<String> takenNames = new HashSet<>();
        for (Capability capability : proven) {
            provenByBinding.putIfAbsent(capability.binding().value(), capability);
            takenNames.add(capability.name());
        }
        Set<String> admittedBindings = new HashSet<>();
        DraftLabeler.Labeling labeling = labeler.labeling(sources, proven);

        for (DraftEntry entry : entries) {
            String review = entry.review();
            if (DraftEntry.PENDING.equals(review)) {
                pending++;
                continue;
            }
            if (DraftEntry.REJECTED.equals(review)) {
                continue;
            }
            if (!DraftEntry.APPROVED.equals(review)) {
                refusals.add(refusal(entry, Cause.REVIEW_INCOMPLETE, "the review state '" + review
                        + "' is not one of pending, approved or rejected (section 4.17.2)"));
                continue;
            }
            evaluate(entry, labeling, provenByBinding, admittedBindings, takenNames, reviewed, refusals);
        }
        return new Result(reviewed, refusals, pending);
    }

    private void evaluate(DraftEntry entry, DraftLabeler.Labeling labeling, Map<String, Capability> provenByBinding,
                          Set<String> admittedBindings, Set<String> takenNames, List<Capability> reviewed,
                          List<DraftRefusal> refusals) {
        String target = entry.target() == null ? "(none)" : entry.target();

        // RV-A, RESOLVE: exactly one in-tree declaration (the attestation resolver, condition 1), and that
        // declaration is a public instance method of a concrete @Service class, which is the only thing the
        // runtime can bind and the only thing the labeler ever drafted.
        Optional<AttestationIntake.ResolvedTarget> resolved = attestationIntake.resolveTarget(target);
        if (resolved.isEmpty()) {
            refusals.add(refusal(entry, Cause.TARGET_UNRESOLVED, "the target '" + target + "' does not resolve "
                    + "to exactly one in-tree method declaration (section 4.17.3, RV-A)"));
            return;
        }
        Optional<ServiceMethodSource> source = sourceOf(resolved.get().method());
        if (source.isEmpty()) {
            refusals.add(refusal(entry, Cause.TARGET_UNRESOLVED, "the target '" + target + "' resolves, but not "
                    + "to a public instance method of a concrete @Service class, so nothing can bind it "
                    + "(section 4.17.3, RV-A)"));
            return;
        }

        // RV-B, NOT STALE: the approval is pinned to the slice the reviewer read.
        String currentFingerprint = attestationIntake.fingerprintOf(resolved.get());
        if (entry.fingerprint() == null || entry.fingerprint().isBlank()) {
            refusals.add(refusal(entry, Cause.STALE_DRAFT, "the entry carries no fingerprint; run tibyan draft to "
                    + "stamp it against the current code (section 4.17.3, RV-B). It does not ship on this run."));
            return;
        }
        if (!currentFingerprint.equals(entry.fingerprint())) {
            refusals.add(refusal(entry, Cause.STALE_DRAFT, "the code changed since approval: the slice fingerprint "
                    + "is now '" + currentFingerprint + "', not the stamped '" + entry.fingerprint()
                    + "'. Re-run tibyan draft and review the entry again (section 4.17.3, RV-B)."));
            return;
        }

        // RV-C, NO LABEL DRIFT: a READ label is a read-safety verdict, and drift in it is drift in the safety
        // claim; the guard that keeps a reviewer's approval attached to what they read.
        DraftLabeler.Verdict verdict = labeling.of(source.get());
        if (entry.label() == null || verdict.label() != entry.label()) {
            refusals.add(refusal(entry, Cause.LABEL_DRIFT, "the recorded label is " + entry.label()
                    + " but the tool now labels the target " + verdict.label() + ": " + verdict.reason()
                    + " (section 4.17.3, RV-C)"));
            return;
        }

        // RV-D, NOT ALREADY PROVEN: the proven tier wins. A reviewed capability never shadows a verified or
        // attested one, and one target is admitted at most once.
        String binding = resolved.get().binding();
        Capability proven = provenByBinding.get(binding);
        if (proven != null) {
            refusals.add(refusal(entry, Cause.SUPERSEDED, "the target's binding '" + binding + "' is already the "
                    + "binding of the " + (proven.hasAttestation() ? "attested" : "discovered") + " capability '"
                    + proven.name() + "'; the proven tier wins, delete this entry (section 4.17.3, RV-D)"));
            return;
        }
        if (!admittedBindings.add(binding)) {
            refusals.add(refusal(entry, Cause.SUPERSEDED, "another approved entry already admitted the binding '"
                    + binding + "'; one reviewed capability per target (section 4.17.3, RV-D)"));
            return;
        }

        // RV-E, EVERY INPUT BINDS: a primitive, an in-tree enum with its closed set (the EI-1 path), or a
        // framework paging parameter the runtime already binds. Anything else refuses, naming the parameter.
        Map<String, Type> inputs = new LinkedHashMap<>();
        Map<String, List<String>> inputValues = new LinkedHashMap<>();
        Optional<DraftRefusal> unmapped = bindInputs(entry, resolved.get().method(), inputs, inputValues);
        if (unmapped.isPresent()) {
            admittedBindings.remove(binding);
            refusals.add(unmapped.get());
            return;
        }

        // RV-F, THE OUTPUT IS DESCRIBABLE: never a refusal.
        Output output = output(resolved.get().method());

        // RV-G, THE HUMAN HALF IS PRESENT, and the name the tool will prefix is usable.
        Optional<DraftRefusal> incomplete = checkHumanHalf(entry, takenNames);
        if (incomplete.isPresent()) {
            admittedBindings.remove(binding);
            refusals.add(incomplete.get());
            return;
        }
        String minted = REVIEWED_PREFIX + entry.name();
        takenNames.add(minted);

        // RV-H, LABEL TO KIND: READ is kind READ and read-only; WRITE is kind WRITE; UNKNOWN is kind ACTION
        // (decision D4). VERIFIED and ATTESTED never reach here: RV-D refused them by binding.
        List<Annotation> annotations = new ArrayList<>();
        annotations.add(new Annotation(DESCRIPTION_ANNOTATION, List.of(entry.description())));
        if (output.annotation() != null) {
            annotations.add(output.annotation());
        }
        Review review = new Review(ReviewLabel.valueOf(entry.label().name()), entry.reviewedBy(),
                entry.reviewedOn(), entry.descriptionOrigin(), entry.fingerprint());
        reviewed.add(Capability.reviewed(minted, inputs, output.type(), output.multiple(), new BindingRef(binding),
                annotations, inputValues, review));
    }

    // The extractor's catalog entry whose declaration IS the resolved one (same AST node), which is what
    // makes the resolved target a public instance method of a @Service class; an abstract class is not a
    // callable bean and the labeler never drafted its methods.
    private Optional<ServiceMethodSource> sourceOf(MethodDeclaration method) {
        for (ServiceMethodSource source : sources) {
            if (source.declaration() == method && !source.service().isAbstract()) {
                return Optional.of(source);
            }
        }
        return Optional.empty();
    }

    // RV-E, exactly the attestation intake's input rule (section 4.15.4 condition 3 with EI-1): paging types
    // are excluded from the modeled set, an in-tree enum maps to STRING with its closed set, an enum the tool
    // cannot read refuses rather than shipping unconstrained, and a primitive maps. A collection, a DTO, or an
    // unresolvable type refuses naming the parameter and its type (decision D6: collections are DL-2's).
    private Optional<DraftRefusal> bindInputs(DraftEntry entry, MethodDeclaration method, Map<String, Type> inputs,
                                              Map<String, List<String>> inputValues) {
        Optional<CompilationUnit> unit = method.findCompilationUnit();
        for (Parameter parameter : method.getParameters()) {
            String declaredType = parameter.getType().asString();
            if (PAGING_TYPES.contains(lastSegment(declaredType))) {
                continue;
            }
            String parameterName = parameter.getNameAsString();
            EnumInputValues.Outcome enumOutcome = unit
                    .map(cu -> enumInputValues.resolve(declaredType, cu))
                    .orElseGet(EnumInputValues.Outcome::notAnEnum);
            if (enumOutcome.isRefused()) {
                return Optional.of(refusal(entry, Cause.INPUT_UNMAPPED, "parameter '" + parameterName
                        + "' of type '" + declaredType + "': " + enumOutcome.refusal()
                        + ". No value set is carried and the entry is refused rather than advertised "
                        + "unconstrained (section 4.17.3, RV-E)"));
            }
            if (enumOutcome.isProvable()) {
                inputs.put(parameterName, Primitive.STRING);
                inputValues.put(parameterName, enumOutcome.values());
                continue;
            }
            Optional<Type> mapped = typeMapper.map(declaredType);
            if (mapped.isEmpty()) {
                return Optional.of(refusal(entry, Cause.INPUT_UNMAPPED, "parameter '" + parameterName
                        + "' has type '" + declaredType + "', which is neither a modeled primitive, nor an "
                        + "in-tree enum, nor a paging parameter; collection and DTO inputs are deferred to DL-2 "
                        + "by name (section 4.17.3, RV-E, decision D6)"));
            }
            inputs.put(parameterName, mapped.get());
        }
        return Optional.empty();
    }

    /** The RV-F outcome: the output type, whether it is a collection, and the disclosing annotation if any. */
    private record Output(Type type, boolean multiple, Annotation annotation) {
    }

    // RV-F: void gives no output to describe (STRING plus the void flag); a primitive maps; a type resolving
    // to an in-tree entity gives ENTITY_REF of its display name exactly as the existing entity resolution
    // does; anything else gives the permissive object, STRING plus opaqueOutput naming the Java type as
    // written (decision D5). A List, Set or Collection of the element sets outputMultiple.
    private Output output(MethodDeclaration method) {
        com.github.javaparser.ast.type.Type returnType = method.getType();
        String written = returnType.asString();
        if (returnType.isVoidType()) {
            return new Output(Primitive.STRING, false, Annotation.flag(VOID_ANNOTATION));
        }
        boolean multiple = false;
        String element = written;
        if (returnType instanceof ClassOrInterfaceType container
                && CONTAINERS.contains(container.getNameAsString())) {
            Optional<String> argument = container.getTypeArguments()
                    .filter(args -> args.size() == 1)
                    .map(args -> args.get(0).asString());
            if (argument.isPresent()) {
                multiple = true;
                element = argument.get();
            }
        }
        Optional<Type> primitive = typeMapper.map(element);
        if (primitive.isPresent()) {
            return new Output(primitive.get(), multiple, null);
        }
        String elementType = element;
        Optional<String> entityKey = method.findCompilationUnit()
                .flatMap(cu -> resolver.resolveEntityKey(elementType, cu));
        if (entityKey.isPresent()) {
            String key = entityKey.get();
            return new Output(Type.entityRef(displayByKey.getOrDefault(key, key)), multiple, null);
        }
        return new Output(Primitive.STRING, multiple, new Annotation(OPAQUE_OUTPUT_ANNOTATION, List.of(written)));
    }

    // RV-G: description, reviewedBy and reviewedOn non-empty, the origin from the closed set, and the name the
    // tool prefixes usable: a valid identifier, not already prefixed (the tool applies the tier prefix
    // itself), and unique across every capability of the model.
    private Optional<DraftRefusal> checkHumanHalf(DraftEntry entry, Set<String> takenNames) {
        if (isBlank(entry.description())) {
            return Optional.of(refusal(entry, Cause.REVIEW_INCOMPLETE, "the description is empty; the tool never "
                    + "authors it, and an empty one is not approvable (section 4.17.3, RV-G)"));
        }
        if (isBlank(entry.reviewedBy())) {
            return Optional.of(refusal(entry, Cause.REVIEW_INCOMPLETE, "reviewedBy is empty; an approval names "
                    + "the human who made it (section 4.17.3, RV-G)"));
        }
        if (isBlank(entry.reviewedOn())) {
            return Optional.of(refusal(entry, Cause.REVIEW_INCOMPLETE, "reviewedOn is empty; an approval carries "
                    + "its date (section 4.17.3, RV-G)"));
        }
        if (!ORIGINS.contains(entry.descriptionOrigin())) {
            return Optional.of(refusal(entry, Cause.REVIEW_INCOMPLETE, "descriptionOrigin '"
                    + entry.descriptionOrigin() + "' is not one of " + ORIGINS + " (section 4.17.3, RV-G)"));
        }
        String name = entry.name();
        if (isBlank(name) || !NAME.matcher(name).matches() || name.startsWith(REVIEWED_PREFIX)) {
            return Optional.of(refusal(entry, Cause.REVIEW_INCOMPLETE, "the name '" + name + "' is empty, not a "
                    + "valid identifier, or already carries the reserved '" + REVIEWED_PREFIX + "' prefix, which "
                    + "the tool applies itself (section 4.17.2; core rule 13)"));
        }
        if (takenNames.contains(REVIEWED_PREFIX + name)) {
            return Optional.of(refusal(entry, Cause.REVIEW_INCOMPLETE, "the name '" + REVIEWED_PREFIX + name
                    + "' collides with an existing capability; names are unique across the model "
                    + "(section 4.17.2)"));
        }
        return Optional.empty();
    }

    private static DraftRefusal refusal(DraftEntry entry, Cause cause, String detail) {
        return new DraftRefusal(entry.target() == null ? "(none)" : entry.target(), entry.name(), cause, detail);
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
}
