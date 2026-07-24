package com.tibyaan.core.ir;

/**
 * Which attestable holes an {@link Attestation} fills (Volume 4 section 3.8; Volume 5 section 4.15.2).
 * A closed set: validation refuses an unknown token. {@code BOUNDARY} (category C) is deliberately not a
 * member yet: it is added only when AT-3 is ratified, which also amends the component shape, so admitting
 * the token before its fields exist would let an incomplete boundary attestation validate.
 */
public enum AttestationCategory {

    /** What a filter, guard, or parameter SELECTS (AT-1, the PREDICATE category). */
    PREDICATE("predicate"),
    /** What a read RETURNS when structure cannot tie it (AT-2). */
    SHAPE("shape"),
    /** A filtered read whose element type is also unresolvable: one entry filling both holes (AT-2). */
    BOTH("both");

    private final String token;

    AttestationCategory(String token) {
        this.token = token;
    }

    /** The canonical lowercase token as it appears in {@code attestations.json} and on every surface. */
    public String token() {
        return token;
    }

    /** Whether this category asserts a subject entity (a shape claim), so the field is required. */
    public boolean requiresSubjectEntity() {
        return this == SHAPE || this == BOTH;
    }

    /** Resolves a token to its category, or throws for an unknown token (the closed-set guard). */
    public static AttestationCategory fromToken(String token) {
        for (AttestationCategory category : values()) {
            if (category.token.equals(token)) {
                return category;
            }
        }
        throw new IllegalArgumentException("unknown attestation category token: '" + token + "'");
    }
}
