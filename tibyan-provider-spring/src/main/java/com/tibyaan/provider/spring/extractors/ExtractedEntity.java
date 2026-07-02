package com.tibyaan.provider.spring.extractors;

import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Relation;

import java.util.List;

/**
 * What {@link EntityExtractor#extractEntity} produces for one entity: the entity itself, and the
 * relations its association fields contributed (Volume 5 section 4.4).
 *
 * <p>The relations are this entity's own, before the model-level deduplication and ordering the
 * provider applies (section 4.4, rule 5). Returning them here keeps a single field walk responsible
 * for both the entity shape and the relations that walk discovered.
 *
 * @param entity    the extracted entity, never null
 * @param relations the relations this entity's associations contributed, never null
 */
public record ExtractedEntity(Entity entity, List<Relation> relations) {

    public ExtractedEntity {
        if (entity == null) {
            throw new IllegalArgumentException("extracted entity must not be null");
        }
        relations = relations == null ? List.of() : List.copyOf(relations);
    }
}
