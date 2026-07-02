package com.tibyaan.provider.spring.extractors;

import com.tibyaan.core.ir.Type;

/**
 * The desire for a read-by-id capability, derived from an entity but carrying no binding yet
 * (Volume 5 section 4.1).
 *
 * <p>{@link EntityExtractor} produces one read intent per entity that has an identifier. The
 * provider later finds the service method that backs it (the join) and emits the finished
 * {@link com.tibyaan.core.ir.Capability} with the discovered binding. Keeping the intent
 * binding-free is what lets the Entity and Service extractors stay independent of each other:
 * the entity side states what it wants, the service side supplies how to invoke it.
 *
 * @param entityName     the entity this read returns, for example {@code Product}
 * @param capabilityName the capability name, the {@code get<Entity>} convention, for example
 *                       {@code getProduct}
 * @param idInputName    the single input's name, the entity's id field name, for example {@code id}
 * @param idType         the id's mapped primitive type, the type the backing method must take
 */
public record ReadIntent(String entityName, String capabilityName, String idInputName, Type idType) {

    public ReadIntent {
        if (entityName == null || entityName.isBlank()) {
            throw new IllegalArgumentException("read intent must name its entity");
        }
        if (capabilityName == null || capabilityName.isBlank()) {
            throw new IllegalArgumentException("read intent must name its capability");
        }
        if (idInputName == null || idInputName.isBlank()) {
            throw new IllegalArgumentException("read intent must name its id input");
        }
        if (idType == null) {
            throw new IllegalArgumentException("read intent must carry an id type");
        }
    }
}
