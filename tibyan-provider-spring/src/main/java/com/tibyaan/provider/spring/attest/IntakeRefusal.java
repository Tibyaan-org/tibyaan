package com.tibyaan.provider.spring.attest;

/**
 * A located refusal of one attestation entry at intake (Volume 5 section 4.15.4). A refused entry produces
 * NO capability and no partial carriage; its refusal is data the survey worklist reports. The most valuable
 * findings of the first measurement are intake refusals of a sincere human's first attempts, so each
 * carries its cause and a located message naming the entry and what failed.
 *
 * @param target the entry's target string, or {@code (file)} for a whole-file failure
 * @param name   the entry's minted-or-raw name, for the worklist, or null when unresolvable
 * @param cause  the refusal cause
 * @param detail the located human-readable message
 */
public record IntakeRefusal(String target, String name, Cause cause, String detail) {

    /** The ratified refusal conditions (Volume 5 section 4.15.4) plus the malformed-file case. */
    public enum Cause {
        /** The file exists but could not be parsed as a JSON array of entries. */
        MALFORMED_FILE,
        /** The target resolves to zero or many in-tree declarations (condition 1). */
        UNRESOLVED_TARGET,
        /** Another entry already targets this declaration (condition 1, one attestation per read). */
        DUPLICATE_TARGET,
        /** The category token is unknown, or the target's holes do not match it (condition 2). */
        CATEGORY_MISMATCH,
        /** The category's sub-phase is not yet ratified and built (condition 2). */
        CATEGORY_NOT_BUILT,
        /** The signature, a parameter, or a type does not verify structurally (condition 3). */
        STRUCTURAL_FRAME,
        /** The read-safety check saw a visible write (condition 3, section 4.15.2). */
        READ_SAFETY,
        /** The parameter coverage is incomplete, over-complete, or a mark is misused (condition 4). */
        COVERAGE,
        /** The minted name is invalid or collides with an existing capability (condition 5). */
        NAME,
        /** Provenance is incomplete or the origin is a draft or unknown token (condition 6). */
        PROVENANCE,
        /** The entry carries no fingerprint; run tibyan attest to stamp it (condition 6). */
        MISSING_FINGERPRINT,
        /** The code changed since attestation; the fingerprint no longer matches (the lifecycle). */
        STALE_ATTESTATION,
        /** Structure contradicts the assertion (a mutating body, a wrong-entity tie; section 4.15.7). */
        STRUCTURAL_CONTRADICTION
    }
}
