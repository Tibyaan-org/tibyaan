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
 * <p>Phase Entity Identity (Volume 5 section 4.12.4): the intent carries the entity's {@code entityKey}
 * (its FQCN) alongside its {@code entityName} (the simple name). Identity, whether a service method
 * or repository backs THIS entity, is decided by key; the simple name drives the matcher's AST-level
 * reasoning and diagnostics; the emitted capability name is minted later from the entity's display
 * name (section 4.12.4, item 2), which equals the simple name unless it collided.
 *
 * @param entityName     the entity this read returns by simple name, for example {@code Product}
 * @param entityKey      the entity's key, its fully-qualified class name (section 4.12.2)
 * @param capabilityName the capability name, the {@code get<Entity>} convention, for example
 *                       {@code getProduct} (a pre-display placeholder; the emitted name is minted
 *                       from the display name)
 * @param idInputName    the single input's name, the entity's id field name, for example {@code id}
 * @param idType         the id's mapped primitive type, the type the backing method must take
 */
public record ReadIntent(String entityName, String entityKey, String capabilityName, String idInputName,
                         Type idType) {

    public ReadIntent {
        if (entityName == null || entityName.isBlank()) {
            throw new IllegalArgumentException("read intent must name its entity");
        }
        if (entityKey == null || entityKey.isBlank()) {
            throw new IllegalArgumentException("read intent must carry its entity key");
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
