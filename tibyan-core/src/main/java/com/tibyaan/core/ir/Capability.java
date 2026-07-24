package com.tibyaan.core.ir;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        Attestation attestation) {

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
    }

    // Named hasAttestation, not isAttested, deliberately: an is-prefixed method would be auto-detected as
    // a serialized boolean property by the IR JSON mapper and break the record round-trip.
    /** Whether this capability carries a human attestation (Volume 5 section 4.15). */
    public boolean hasAttestation() {
        return attestation != null;
    }

    /** A read only capability of kind READ, with no annotations, the MVP singular shape. */
    public static Capability read(String name, Map<String, Type> inputs, Type output, BindingRef binding) {
        return new Capability(name, CapabilityKind.READ, inputs, output, false, true, binding, List.of(), null);
    }

    /**
     * A read only list capability (Volume 5 section 4.10): the output is the element type and
     * {@code outputMultiple} is true, so the capability returns a collection of that element.
     */
    public static Capability readList(String name, Map<String, Type> inputs, Type element,
            BindingRef binding, List<Annotation> annotations) {
        return new Capability(name, CapabilityKind.READ, inputs, element, true, true, binding, annotations, null);
    }

    /**
     * A human-attested read capability (Volume 5 section 4.15). The name must already carry the reserved
     * {@code attested_} prefix (core rule 9); the caller mints it from the human's name at intake.
     */
    public static Capability attested(String name, Map<String, Type> inputs, Type output,
            boolean outputMultiple, BindingRef binding, List<Annotation> annotations,
            Attestation attestation) {
        return new Capability(name, CapabilityKind.READ, inputs, output, outputMultiple, true, binding,
                annotations, attestation);
    }
}
