package com.tibyaan.core.ir;

/**
 * How an {@link Attestation}'s words were produced (Volume 4 section 3.8; Volume 5 section 4.15.5). The
 * two shipping origins have the same authority (a human owns both), but the provenance fact of HOW they
 * were produced is recorded permanently and shown verbatim on every surface. The {@code ai-draft} state
 * is deliberately NOT a member: a draft never reaches the IR (intake refuses it, Volume 5 section 4.15.4
 * condition 6), so it can never be constructed here.
 */
public enum AttestationOrigin {

    /** Typed by a person. */
    HUMAN_AUTHORED("human-authored"),
    /** Drafted by the AI assist (AT-4), then reviewed and confirmed by a named person. */
    AI_DRAFTED_HUMAN_CONFIRMED("ai-drafted-human-confirmed");

    private final String token;

    AttestationOrigin(String token) {
        this.token = token;
    }

    /** The canonical token as it appears in {@code attestations.json} and on every surface. */
    public String token() {
        return token;
    }

    /** Resolves a shipping-origin token, or throws for an unknown or draft token. */
    public static AttestationOrigin fromToken(String token) {
        for (AttestationOrigin origin : values()) {
            if (origin.token.equals(token)) {
                return origin;
            }
        }
        throw new IllegalArgumentException("unknown attestation origin token: '" + token + "'");
    }
}
