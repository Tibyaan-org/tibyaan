package com.tibyaan.core.ir;

/**
 * A value type in the Capability IR.
 *
 * <p>The type system is closed (Volume 4 section 4): a type is either one of the
 * {@link Primitive} values or an {@link EntityRef} to an entity defined in the same model.
 * Modeling this as a sealed interface, rather than the plain enum sketched in Volume 17,
 * lets an entity reference carry the target name while keeping the primitive set closed and
 * exhaustive for {@code switch}.
 *
 * <p>Every type has a single canonical string form, which is the form used in the IR JSON
 * (for example {@code "LONG"} or {@code "ENTITY_REF(Product)"}). The canonical form lives in
 * core so the vocabulary is owned here, with zero dependency on any serialization library.
 */
public sealed interface Type permits Primitive, EntityRef, ShapeRef {

    /** Prefix that marks the canonical string form of an entity reference. */
    String ENTITY_REF_PREFIX = "ENTITY_REF(";

    /** Prefix that marks the canonical string form of a shape reference (Volume 4 section 3.12, DL-2b). */
    String SHAPE_REF_PREFIX = "SHAPE_REF(";

    /**
     * The canonical string form of this type, for example {@code "LONG"} or
     * {@code "ENTITY_REF(Product)"}.
     */
    String canonical();

    /** A reference to an entity by name. */
    static EntityRef entityRef(String entityName) {
        return new EntityRef(entityName);
    }

    static ShapeRef shapeRef(String shapeName) {
        return new ShapeRef(shapeName);
    }

    /**
     * Parses a canonical string form back into a {@link Type}.
     *
     * <p>This is the inverse of {@link #canonical()}. A string of the form
     * {@code ENTITY_REF(Name)} yields an {@link EntityRef}; any other string must name a
     * {@link Primitive}. An unrecognized string is rejected, which is how the closed type
     * system stops unknown types from entering the IR through deserialization.
     *
     * @throws IllegalArgumentException if the string is neither a known primitive nor a
     *                                  well formed entity reference
     */
    static Type fromCanonical(String canonical) {
        if (canonical == null) {
            throw new IllegalArgumentException("type string must not be null");
        }
        String trimmed = canonical.trim();
        if (trimmed.startsWith(ENTITY_REF_PREFIX) && trimmed.endsWith(")")) {
            String name = trimmed.substring(ENTITY_REF_PREFIX.length(), trimmed.length() - 1).trim();
            return new EntityRef(name);
        }
        if (trimmed.startsWith(SHAPE_REF_PREFIX) && trimmed.endsWith(")")) {
            String name = trimmed.substring(SHAPE_REF_PREFIX.length(), trimmed.length() - 1).trim();
            return new ShapeRef(name);
        }
        try {
            return Primitive.valueOf(trimmed);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("unknown type: '" + canonical + "'");
        }
    }
}
