package com.tibyaan.core.ir;

/**
 * A reference to another entity defined in the same model (Volume 4 section 4,
 * the {@code ENTITY_REF(name)} type).
 *
 * <p>The reference holds only the target entity name. Whether that name resolves to a real
 * entity is a semantic question, checked by the validator, not a structural one.
 *
 * @param entityName the name of the referenced entity
 */
public record EntityRef(String entityName) implements Type {

    public EntityRef {
        if (entityName == null || entityName.isBlank()) {
            throw new IllegalArgumentException("entityName must not be null or blank");
        }
    }

    @Override
    public String canonical() {
        return ENTITY_REF_PREFIX + entityName + ")";
    }
}
