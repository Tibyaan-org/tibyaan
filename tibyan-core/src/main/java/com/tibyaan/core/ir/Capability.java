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
 * @param name        unique capability name within the module, for example {@code getProduct}
 * @param kind        READ, WRITE, or ACTION (MVP uses READ)
 * @param inputs      named, typed inputs, never null, insertion order preserved
 * @param output      the result type, often an entity reference
 * @param readOnly    true if the capability has no side effects
 * @param binding     an opaque reference the runtime resolves to real logic
 * @param annotations generic extension hooks, never null
 */
public record Capability(
        String name,
        CapabilityKind kind,
        Map<String, Type> inputs,
        Type output,
        boolean readOnly,
        BindingRef binding,
        List<Annotation> annotations) {

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

    /** A read only capability of kind READ, with no annotations, the MVP shape. */
    public static Capability read(String name, Map<String, Type> inputs, Type output, BindingRef binding) {
        return new Capability(name, CapabilityKind.READ, inputs, output, true, binding, List.of());
    }
}
