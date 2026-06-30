package com.tibyaan.core.ir;

/**
 * The cardinality of a relation between two entities (Volume 4 section 3.5).
 */
public enum RelationKind {
    ONE_TO_ONE,
    ONE_TO_MANY,
    MANY_TO_ONE,
    MANY_TO_MANY
}
