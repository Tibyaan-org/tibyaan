package com.tibyaan.core.ir;

import java.util.List;

/**
 * A generic extension hook: a key with zero or more string values (Volume 4 section 3.6).
 *
 * <p>Annotations let the IR carry hints without growing new typed fields prematurely. They
 * must never smuggle framework concepts into the IR's semantics; they are hints, not meaning.
 *
 * @param key    the annotation key
 * @param values the annotation values, never null (empty when the key is a bare flag)
 */
public record Annotation(String key, List<String> values) {

    public Annotation {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("annotation key must not be null or blank");
        }
        values = values == null ? List.of() : List.copyOf(values);
    }

    /** An annotation that is a bare flag, with a key and no values. */
    public static Annotation flag(String key) {
        return new Annotation(key, List.of());
    }
}
