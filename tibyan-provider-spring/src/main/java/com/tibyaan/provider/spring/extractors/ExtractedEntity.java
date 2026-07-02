package com.tibyaan.provider.spring.extractors;

import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Relation;

import java.util.List;

/**
 * What {@link EntityExtractor#extractEntity} produces for one entity: the entity itself, the
 * relations its association fields contributed (Volume 5 section 4.4), and the fields tolerance
 * skipped (Volume 5 section 4.8).
 *
 * <p>The relations are this entity's own, before the model-level deduplication and ordering the
 * provider applies (section 4.4, rule 5). The skipped fields are the fields whose type the closed
 * set and the association and enum rules could not map; the entity extracts without them, and each
 * is disclosed. Returning all three here keeps a single field walk responsible for the entity shape,
 * the relations it discovered, and the fields it had to leave out.
 *
 * @param entity    the extracted entity, never null
 * @param relations the relations this entity's associations contributed, never null
 * @param skipped   the fields skipped by tolerance, never null
 */
public record ExtractedEntity(Entity entity, List<Relation> relations, List<SkippedField> skipped) {

    public ExtractedEntity {
        if (entity == null) {
            throw new IllegalArgumentException("extracted entity must not be null");
        }
        relations = relations == null ? List.of() : List.copyOf(relations);
        skipped = skipped == null ? List.of() : List.copyOf(skipped);
    }
}
