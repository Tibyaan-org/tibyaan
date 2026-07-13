package com.tibyaan.core.ir;

import java.util.List;

/**
 * A business object in the model (Volume 4 section 3.2).
 *
 * <p>An entity has two identifiers, split by phase Entity Identity (Volume 4 section 3.7):
 * a {@code key}, its stable qualified identity (for the Spring provider the entity class's
 * fully-qualified name), and a {@code name}, its display name that every consumer surface derives
 * from. The two coincide when the simple name is unique across the model; they differ only when a
 * simple name is reused across bounded contexts and must be disambiguated (Volume 5 section 4.12).
 *
 * @param key         the entity's stable qualified identity, unique within the model
 * @param name        the entity's display name, unique within the model
 * @param fields      the entity's fields, never null
 * @param annotations generic extension hooks, never null
 * @param metadata    free form, non semantic data, never null
 */
public record Entity(String key, String name, List<Field> fields, List<Annotation> annotations, Metadata metadata) {

    public Entity {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("entity key must not be null or blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("entity name must not be null or blank");
        }
        fields = fields == null ? List.of() : List.copyOf(fields);
        annotations = annotations == null ? List.of() : List.copyOf(annotations);
        metadata = metadata == null ? Metadata.empty() : metadata;
    }

    /**
     * An entity with the given fields, no annotations, and empty metadata. The key defaults to the
     * name: for a manually built or single-context entity the display name already is the qualified
     * identity, so key and name coincide (Volume 4 section 3.7).
     */
    public static Entity of(String name, List<Field> fields) {
        return new Entity(name, name, fields, List.of(), Metadata.empty());
    }

    /**
     * An entity with an explicit qualified key distinct from its display name, the shape the Spring
     * provider produces when a simple name is disambiguated (Volume 5 section 4.12).
     */
    public static Entity of(String key, String name, List<Field> fields) {
        return new Entity(key, name, fields, List.of(), Metadata.empty());
    }
}
