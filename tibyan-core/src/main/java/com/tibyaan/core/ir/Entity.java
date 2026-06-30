package com.tibyaan.core.ir;

import java.util.List;

/**
 * A business object in the model (Volume 4 section 3.2).
 *
 * @param name        unique entity name within the module
 * @param fields      the entity's fields, never null
 * @param annotations generic extension hooks, never null
 * @param metadata    free form, non semantic data, never null
 */
public record Entity(String name, List<Field> fields, List<Annotation> annotations, Metadata metadata) {

    public Entity {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("entity name must not be null or blank");
        }
        fields = fields == null ? List.of() : List.copyOf(fields);
        annotations = annotations == null ? List.of() : List.copyOf(annotations);
        metadata = metadata == null ? Metadata.empty() : metadata;
    }

    /** An entity with the given fields, no annotations, and empty metadata. */
    public static Entity of(String name, List<Field> fields) {
        return new Entity(name, fields, List.of(), Metadata.empty());
    }
}
