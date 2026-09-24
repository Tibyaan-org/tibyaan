package com.tibyaan.core.ir;

/**
 * A reference to a {@link Shape} in the same model, by display name (Volume 4 section 3.12, phase DL-2b).
 * Legal on a capability INPUT and on a shape FIELD, never on an output and never on an entity field
 * (validation rule 15). Its canonical form is {@code SHAPE_REF(name)}.
 *
 * @param shapeName the shape's display name
 */
public record ShapeRef(String shapeName) implements Type {

    public ShapeRef {
        if (shapeName == null || shapeName.isBlank()) {
            throw new IllegalArgumentException("shape reference must name a shape");
        }
    }

    @Override
    public String canonical() {
        return Type.SHAPE_REF_PREFIX + shapeName + ")";
    }
}
