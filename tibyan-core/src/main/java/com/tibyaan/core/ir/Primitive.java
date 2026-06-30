package com.tibyaan.core.ir;

/**
 * The closed set of primitive types (Volume 4 section 4).
 *
 * <p>A closed set is deliberate: it forces every provider to map source types into a small,
 * well understood vocabulary, which keeps backends simple and the IR portable.
 */
public enum Primitive implements Type {

    /** Text. */
    STRING,
    /** 64 bit integer, common for identifiers. */
    LONG,
    /** 32 bit integer. */
    INTEGER,
    /** Exact decimal, for money and quantities. */
    DECIMAL,
    /** True or false. */
    BOOLEAN,
    /** Universally unique identifier. */
    UUID,
    /** A point in time. */
    TIMESTAMP;

    @Override
    public String canonical() {
        return name();
    }
}
