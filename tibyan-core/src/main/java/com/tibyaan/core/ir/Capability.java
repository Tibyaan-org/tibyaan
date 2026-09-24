package com.tibyaan.core.ir;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * An action the system can perform, named in business terms (Volume 4 section 3.4).
 *
 * <p>The MVP produces read only capabilities of kind {@link CapabilityKind#READ} only.
 * Insertion order of {@code inputs} is preserved so that downstream serialization and
 * generation stay deterministic (CLAUDE.md rule 10).
 *
 * @param name           unique capability name within the module, for example {@code getProduct}
 * @param kind           READ, WRITE, or ACTION (MVP uses READ)
 * @param inputs         named, typed inputs, never null, insertion order preserved
 * @param output         the result type; when {@code outputMultiple} is true this is the element type
 *                       of the collection, not the collection itself
 * @param outputMultiple true if the capability returns a collection of {@code output} (a list read,
 *                       Volume 4 section 4 and Volume 5 section 4.10); default false
 * @param readOnly       true if the capability has no side effects
 * @param binding        an opaque reference the runtime resolves to real logic
 * @param annotations    generic extension hooks, never null
 * @param attestation    the human-attested claim and its provenance (Volume 4 section 3.8), or null on
 *                       every tool-verified capability; an attested capability's name carries the
 *                       reserved {@code attested_} prefix (rule 9)
 * @param inputValues    per-input closed value sets (Volume 4 section 3.9; phase Enum Inputs), keyed by
 *                       input name, each set non-empty, ordered, and distinct. EMPTY on every capability
 *                       that has none, and omitted from the serialized IR when empty, so a constraint-free
 *                       model is byte-identical. A set here is a CONTRACT the backends render and the
 *                       runtime enforces, never a hint: it is carried only when the provider READ it from
 *                       a resolved in-tree enum declaration (Volume 5 section 4.16.4). The omission of an
 *                       empty map from the serialized IR is the serializer's job, not this record's:
 *                       {@code tibyan-core} carries no Jackson annotations because it carries no
 *                       dependencies (CLAUDE.md rule 1)
 */
public record Capability(
        String name,
        CapabilityKind kind,
        Map<String, Type> inputs,
        Type output,
        boolean outputMultiple,
        boolean readOnly,
        BindingRef binding,
        List<Annotation> annotations,
        Attestation attestation,
        Map<String, List<String>> inputValues,
        Review review,
        Set<String> multipleInputs) {

    /**
     * A capability with no input value constraints, which is every capability the structural rules emit.
     * A static factory rather than a second constructor: a record with two constructors is ambiguous to a
     * record-aware JSON mapper, and the IR round-trip is load-bearing.
     */
    public static Capability of(String name, CapabilityKind kind, Map<String, Type> inputs, Type output,
            boolean outputMultiple, boolean readOnly, BindingRef binding, List<Annotation> annotations,
            Attestation attestation) {
        return new Capability(name, kind, inputs, output, outputMultiple, readOnly, binding, annotations,
                attestation, Map.of(), null, Set.of());
    }

    public Capability {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("capability name must not be null or blank");
        }
        if (kind == null) {
            throw new IllegalArgumentException("capability '" + name + "' must have a kind");
        }
        if (output == null) {
            throw new IllegalArgumentException("capability '" + name + "' must have an output type");
        }
        if (binding == null) {
            throw new IllegalArgumentException("capability '" + name + "' must have a binding");
        }
        inputs = inputs == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
        annotations = annotations == null ? List.of() : List.copyOf(annotations);
        // Never null, and each set copied immutably in its declaration order. The record enforces its own
        // internal shape; completeness against the capability's inputs is core rule 10, which needs both.
        if (inputValues == null || inputValues.isEmpty()) {
            inputValues = Map.of();
        } else {
            Map<String, List<String>> copy = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : inputValues.entrySet()) {
                copy.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            inputValues = Collections.unmodifiableMap(copy);
        }
        // Phase DL-2a (Volume 4 section 3.11): the input names whose wire form is an array of the input's
        // type. Never null, copied immutably in declaration order; well-formedness against the inputs is
        // core rule 14.
        multipleInputs = multipleInputs == null || multipleInputs.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(multipleInputs));
    }

    /** True when at least one input is carried as an array on the wire (Volume 4 section 3.11). */
    public boolean hasMultipleInputs() {
        return !multipleInputs.isEmpty();
    }

    /** Whether this capability constrains any input to a closed value set (Volume 4 section 3.9). */
    public boolean hasInputValues() {
        return !inputValues.isEmpty();
    }

    // Named hasAttestation, not isAttested, deliberately: an is-prefixed method would be auto-detected as
    // a serialized boolean property by the IR JSON mapper and break the record round-trip.
    /** Whether this capability carries a human attestation (Volume 5 section 4.15). */
    public boolean hasAttestation() {
        return attestation != null;
    }

    // Named hasReview for the same reason as hasAttestation: an is-prefixed method would be serialized.
    /** Whether this capability is a reviewed draft (Volume 4 section 3.10; phase DL-1). */
    public boolean hasReview() {
        return review != null;
    }

    /**
     * A reviewed capability (Volume 5 section 4.17.3): kind from the label (RV-H), readOnly only for READ,
     * no attestation. The name must already carry the reserved {@code reviewed_} prefix (core rule 13).
     */
    public static Capability reviewed(String name, Map<String, Type> inputs, Type output,
            boolean outputMultiple, BindingRef binding, List<Annotation> annotations,
            Map<String, List<String>> inputValues, Review review) {
        return reviewed(name, inputs, output, outputMultiple, binding, annotations, inputValues, review, Set.of());
    }

    /** A reviewed capability whose inputs may include multiple (array-carried) inputs (phase DL-2a). */
    public static Capability reviewed(String name, Map<String, Type> inputs, Type output,
            boolean outputMultiple, BindingRef binding, List<Annotation> annotations,
            Map<String, List<String>> inputValues, Review review, Set<String> multipleInputs) {
        CapabilityKind kind = review.label().kind();
        return new Capability(name, kind, inputs, output, outputMultiple, kind == CapabilityKind.READ, binding,
                annotations, null, inputValues, review, multipleInputs);
    }

    /** A read only capability of kind READ, with no annotations, the MVP singular shape. */
    public static Capability read(String name, Map<String, Type> inputs, Type output, BindingRef binding) {
        return Capability.of(name, CapabilityKind.READ, inputs, output, false, true, binding, List.of(), null);
    }

    /**
     * A read only list capability (Volume 5 section 4.10): the output is the element type and
     * {@code outputMultiple} is true, so the capability returns a collection of that element.
     */
    public static Capability readList(String name, Map<String, Type> inputs, Type element,
            BindingRef binding, List<Annotation> annotations) {
        return Capability.of(name, CapabilityKind.READ, inputs, element, true, true, binding, annotations, null);
    }

    /**
     * A human-attested read capability (Volume 5 section 4.15). The name must already carry the reserved
     * {@code attested_} prefix (core rule 9); the caller mints it from the human's name at intake.
     */
    public static Capability attested(String name, Map<String, Type> inputs, Type output,
            boolean outputMultiple, BindingRef binding, List<Annotation> annotations,
            Attestation attestation) {
        return Capability.of(name, CapabilityKind.READ, inputs, output, outputMultiple, true, binding,
                annotations, attestation);
    }
}
