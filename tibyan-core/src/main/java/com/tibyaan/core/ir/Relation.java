package com.tibyaan.core.ir;

/**
 * A relationship between two entities (Volume 4 section 3.5).
 *
 * @param from source entity name
 * @param to   target entity name
 * @param kind the cardinality of the relation
 */
public record Relation(String from, String to, RelationKind kind) {

    public Relation {
        if (from == null || from.isBlank()) {
            throw new IllegalArgumentException("relation 'from' must not be null or blank");
        }
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("relation 'to' must not be null or blank");
        }
        if (kind == null) {
            throw new IllegalArgumentException("relation kind must not be null");
        }
    }
}
