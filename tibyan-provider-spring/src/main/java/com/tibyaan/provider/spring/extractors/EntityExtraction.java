package com.tibyaan.provider.spring.extractors;

import com.tibyaan.core.ir.Entity;

import java.util.List;

/**
 * What {@link EntityExtractor} produces: the entities and, for each entity that has an
 * identifier, the read intent that wants a {@code get<Entity>} capability (Volume 5 section 4.1).
 *
 * <p>The entities are finished IR. The read intents are not: they carry no binding, because the
 * binding is discovered later by joining them against the service-method catalog. Returning both
 * here keeps a single AST walk responsible for everything the entity side knows.
 *
 * @param entities    the entities found, never null
 * @param readIntents one read intent per entity that has an identifier, never null
 */
public record EntityExtraction(List<Entity> entities, List<ReadIntent> readIntents) {

    public EntityExtraction {
        entities = entities == null ? List.of() : List.copyOf(entities);
        readIntents = readIntents == null ? List.of() : List.copyOf(readIntents);
    }
}
