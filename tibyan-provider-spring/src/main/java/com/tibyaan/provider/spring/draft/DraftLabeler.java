package com.tibyaan.provider.spring.draft;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.provider.spring.JavaTypeMapper;
import com.tibyaan.provider.spring.attest.AttestationIntake;
import com.tibyaan.provider.spring.attest.ReadSafety;
import com.tibyaan.provider.spring.extractors.ServiceMethodSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Labels every public method of every concrete service class (spike DL-0, Volume 13 section 3, "v2 direction";
 * the stamp of phase DL-1, Volume 5 section 4.17.2).
 *
 * <p>The labeler adds no new proof. VERIFIED and ATTESTED are read off the scan: a method whose binding is a
 * discovered capability's binding is VERIFIED, one whose binding is a confirmed attestation's binding is
 * ATTESTED. Everything else is classified by the existing read-safety check (Volume 5 sections 4.15.2 and
 * 4.15.2.1), which is the only mutation analysis the tool has: a clean slice is READ, a visible mutation is
 * WRITE, and a slice the check cannot decide is UNKNOWN.
 *
 * <p>The WRITE versus UNKNOWN split is a classification of read-safety's located reasons, recorded here so it is
 * inspectable. Its bias is the mis-safe bias: a write-set-named call on any receiver the check could not prove
 * local is labeled WRITE, because a false WRITE costs a reviewer one look and a false READ ships a mutation as a
 * read. Two known blind spots are carried from the check itself and recorded in the spike: the check returns the
 * FIRST reason only, and it follows one hop of private helpers and no calls into other beans.
 *
 * <p>DL-1 adds the stamp: every entry carries the structural fingerprint of its slice (section 4.15.4), computed
 * by the same function the attestation intake uses, so an approval is pinned to the code the reviewer read and
 * {@code SpringCapabilityProvider.attestationFingerprint(input, target)} returns the same value for the same
 * target.
 */
public final class DraftLabeler {

    // Read-safety reason markers that mean a mutation is visible (label WRITE). Everything else the check
    // refuses is UNKNOWN. The codes are the ones section 4.15.2.1 prints at the end of each reason.
    private static final List<String> WRITE_MARKERS = List.of(
            ", R1)", ", R2)", ", R3)", ", R5)", ", R9)", ", L1, R9)", ", L6, R8)",
            "on a loaded entity", "is @Modifying", "runs a mutating @Query");

    private final ReadSafety readSafety;
    private final JavaTypeMapper typeMapper;
    private final AttestationIntake fingerprints;

    /**
     * @param readSafety   the read-safety check the READ, WRITE and UNKNOWN labels come from
     * @param typeMapper   the Java-to-primitive mapper for the input types
     * @param fingerprints the intake whose slice fingerprint stamps every entry; null leaves entries unstamped
     */
    public DraftLabeler(ReadSafety readSafety, JavaTypeMapper typeMapper, AttestationIntake fingerprints) {
        this.readSafety = readSafety;
        this.typeMapper = typeMapper;
        this.fingerprints = fingerprints;
    }

    /** The label of one method with its located reason and, for the two proven labels, the capability. */
    public record Verdict(DraftLabel label, String reason, String capability) {
    }

    /**
     * Labels the given service methods against the given discovered and attested capabilities.
     *
     * @param sources      every public instance method of every {@code @Service} class, in extractor order
     * @param capabilities the scan's capabilities, discovered and attested (never reviewed ones: a reviewed
     *                     capability is not a proof, so it labels nothing)
     * @return one entry per method of a concrete service class, in the same order, with unique minted names
     */
    public List<DraftEntry> label(List<ServiceMethodSource> sources, List<Capability> capabilities) {
        Labeling labeling = labeling(sources, capabilities);
        List<DraftEntry> entries = new ArrayList<>();
        Map<String, Integer> nameUses = new HashMap<>();
        for (ServiceMethodSource source : sources) {
            if (source.service().isAbstract()) {
                continue;
            }
            entries.add(entryFor(source, labeling.of(source), nameUses));
        }
        return entries;
    }

    /**
     * The labeling context over a whole catalog, for callers that need the verdict of individual methods
     * without minting names or stamping (the draft intake's RV-C, section 4.17.3). The verdict of a method is
     * the same one {@link #label} would write for it.
     */
    public Labeling labeling(List<ServiceMethodSource> sources, List<Capability> capabilities) {
        Map<String, List<Capability>> byBinding = new HashMap<>();
        for (Capability capability : capabilities) {
            byBinding.computeIfAbsent(capability.binding().value(), k -> new ArrayList<>()).add(capability);
        }
        Map<String, Integer> methodsPerBinding = new HashMap<>();
        for (ServiceMethodSource source : sources) {
            methodsPerBinding.merge(source.method().toBinding(), 1, Integer::sum);
        }
        return new Labeling(byBinding, methodsPerBinding);
    }

    /** A prepared labeling: the per-method verdict against one capability set. */
    public final class Labeling {

        private final Map<String, List<Capability>> byBinding;
        private final Map<String, Integer> methodsPerBinding;

        private Labeling(Map<String, List<Capability>> byBinding, Map<String, Integer> methodsPerBinding) {
            this.byBinding = byBinding;
            this.methodsPerBinding = methodsPerBinding;
        }

        /** The verdict of one method: its label, the located reason, and the capability for a proven label. */
        public Verdict of(ServiceMethodSource source) {
            return verdict(source, byBinding, methodsPerBinding);
        }
    }

    private Verdict verdict(ServiceMethodSource source, Map<String, List<Capability>> byBinding,
                            Map<String, Integer> methodsPerBinding) {
        MethodDeclaration declaration = source.declaration();
        ClassOrInterfaceDeclaration service = source.service();
        String binding = source.method().toBinding();

        Optional<Capability> matched = matchCapability(source, byBinding.getOrDefault(binding, List.of()),
                methodsPerBinding.getOrDefault(binding, 0));
        if (matched.isPresent()) {
            Capability capability = matched.get();
            if (capability.hasAttestation()) {
                return new Verdict(DraftLabel.ATTESTED, "the target of the confirmed attestation carried by '"
                        + capability.name() + "' (attestation intake, section 4.15.3)", capability.name());
            }
            return new Verdict(DraftLabel.VERIFIED, "the binding of the discovered capability '"
                    + capability.name() + "' (discovery rules, sections 4.1 to 4.11)", capability.name());
        }

        Optional<String> refusal = readSafety.check(service, declaration, directPrivateHelpers(service, declaration));
        if (refusal.isEmpty() && declaration.getType().isVoidType()) {
            // A void method cannot be a read tool, and read-safety only sees persistence: a side effect
            // outside it (a message, a remote call, a file) is invisible, so the honest label is UNKNOWN.
            return new Verdict(DraftLabel.UNKNOWN, "returns void, so it is not a read; no persistence mutation "
                    + "is visible in the slice, and side effects outside persistence are not analyzed "
                    + "(read-safety, section 4.15.2)", null);
        }
        if (refusal.isEmpty()) {
            return new Verdict(DraftLabel.READ, "no mutation visible in the slice: the body, its one-hop private "
                    + "helpers, and the repository declarations it calls (read-safety, section 4.15.2); binding "
                    + "and shape not proven", null);
        }
        if (isWrite(refusal.get())) {
            return new Verdict(DraftLabel.WRITE, refusal.get(), null);
        }
        return new Verdict(DraftLabel.UNKNOWN, refusal.get(), null);
    }

    private DraftEntry entryFor(ServiceMethodSource source, Verdict verdict, Map<String, Integer> nameUses) {
        MethodDeclaration declaration = source.declaration();
        ClassOrInterfaceDeclaration service = source.service();
        String fingerprint = fingerprints == null ? null : fingerprints.fingerprintOf(service, declaration);
        return DraftEntry.fresh(
                mintName(source, nameUses),
                target(service, declaration),
                source.method().toBinding(),
                verdict.label(),
                verdict.reason(),
                verdict.capability(),
                declaration.getType().asString(),
                inputs(declaration),
                fingerprint);
    }

    // A capability's binding has no signature, so overloads share one binding. When the binding names exactly
    // one method in the tree, the match is unambiguous; when overloads exist, the one whose arity equals the
    // capability's input count is the match, and any other overload falls to read-safety labeling.
    private static Optional<Capability> matchCapability(ServiceMethodSource source, List<Capability> candidates,
                                                        int methodsWithBinding) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        int arity = source.declaration().getParameters().size();
        if (methodsWithBinding == 1 && candidates.size() == 1) {
            return Optional.of(candidates.get(0));
        }
        for (Capability candidate : candidates) {
            if (candidate.inputs().size() == arity) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static boolean isWrite(String reason) {
        for (String marker : WRITE_MARKERS) {
            if (reason.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    // One hop: the private (non-public) methods of the same class that the body calls with no receiver or
    // with 'this' as the receiver. Read-safety reads their bodies too, which closes the hidden-helper write
    // that dg-refusals/MutService demonstrates. Deeper hops and calls into other beans are not followed.
    private static List<MethodDeclaration> directPrivateHelpers(ClassOrInterfaceDeclaration service,
                                                                MethodDeclaration declaration) {
        Set<String> calledNames = new HashSet<>();
        declaration.getBody().ifPresent(body -> body.findAll(MethodCallExpr.class).forEach(call -> {
            boolean unqualified = call.getScope().isEmpty() || call.getScope().get().isThisExpr();
            if (unqualified) {
                calledNames.add(call.getNameAsString());
            }
        }));
        List<MethodDeclaration> helpers = new ArrayList<>();
        for (MethodDeclaration candidate : service.getMethods()) {
            if (candidate != declaration && !candidate.isPublic() && calledNames.contains(candidate.getNameAsString())) {
                helpers.add(candidate);
            }
        }
        return helpers;
    }

    // The attestation target format (AttestationRecord): <FQCN>#<method>(<simple param types>), comma-space
    // separated as the first real record wrote it (intake accepts both spellings), generics stripped, so a
    // draft's target can be pasted into tibyan/attestations.json as it stands and compared to it by string.
    static String target(ClassOrInterfaceDeclaration service, MethodDeclaration declaration) {
        String fqcn = service.getFullyQualifiedName().orElse(service.getNameAsString());
        List<String> params = new ArrayList<>();
        for (Parameter parameter : declaration.getParameters()) {
            params.add(simpleTypeName(parameter.getType().asString()));
        }
        return fqcn + "#" + declaration.getNameAsString() + "(" + String.join(", ", params) + ")";
    }

    static String simpleTypeName(String typeAsWritten) {
        String noGenerics = typeAsWritten;
        int lt = noGenerics.indexOf('<');
        if (lt >= 0) {
            noGenerics = noGenerics.substring(0, lt);
        }
        int dot = noGenerics.lastIndexOf('.');
        return dot >= 0 ? noGenerics.substring(dot + 1) : noGenerics;
    }

    private List<DraftInput> inputs(MethodDeclaration declaration) {
        List<DraftInput> inputs = new ArrayList<>();
        for (Parameter parameter : declaration.getParameters()) {
            String javaType = parameter.getType().asString();
            String mapped = typeMapper.map(javaType).map(type -> type.canonical()).orElse(null);
            inputs.add(new DraftInput(parameter.getNameAsString(), javaType, mapped));
        }
        return inputs;
    }

    // <ServiceSimpleName>_<method>, with a numeric suffix on the second and later collisions (overloads, or
    // two services with one simple name), assigned in extractor order so the same tree mints the same names.
    private static String mintName(ServiceMethodSource source, Map<String, Integer> nameUses) {
        String base = source.method().serviceSimpleName() + "_" + source.method().methodName();
        int uses = nameUses.merge(base, 1, Integer::sum);
        return uses == 1 ? base : base + "_" + uses;
    }

    /** A view of the label census for callers that do not hold a report. */
    public static Map<String, Integer> census(List<DraftEntry> entries) {
        return new LinkedHashMap<>(DraftReport.count(entries));
    }
}
