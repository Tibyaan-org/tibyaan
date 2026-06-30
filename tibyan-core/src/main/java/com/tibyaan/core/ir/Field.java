package com.tibyaan.core.ir;

import java.util.List;

/**
 * A field of an entity (Volume 4 section 3.3).
 *
 * @param name        unique field name within its entity
 * @param type        a primitive or a reference to another entity
 * @param nullable    whether the field may be absent
 * @param annotations generic extension hooks, never null
 */
public record Field(String name, Type type, boolean nullable, List<Annotation> annotations) {

    public Field {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("field name must not be null or blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("field '" + name + "' must have a type");
        }
        annotations = annotations == null ? List.of() : List.copyOf(annotations);
    }

    /** A non null field with no annotations, the common case. */
    public static Field of(String name, Type type, boolean nullable) {
        return new Field(name, type, nullable, List.of());
    }
}
