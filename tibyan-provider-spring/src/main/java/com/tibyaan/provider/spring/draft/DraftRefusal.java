package com.tibyaan.provider.spring.draft;

/**
 * A located refusal of one approved draft entry at intake (phase DL-1, Volume 5 section 4.17.3). A refused
 * entry produces NO capability; its refusal is data the survey reports with its cause and target, so the
 * reviewer can act on it. A pending entry is not a refusal (it is counted in its own queue) and a rejected
 * entry is not a refusal either (it produces nothing, as the reviewer asked).
 *
 * @param target the entry's target string, or {@code (tibyan/drafts.json)} for a whole-file failure
 * @param name   the entry's name, or null when the file itself failed
 * @param cause  the refusal cause
 * @param detail the located human-readable message
 */
public record DraftRefusal(String target, String name, Cause cause, String detail) {

    /** The ratified intake conditions RV-A to RV-G that refuse (section 4.17.3), plus the malformed-file case. */
    public enum Cause {
        /** The drafts file exists but could not be read or parsed. */
        FILE_ERROR,
        /** RV-A: the target does not resolve to exactly one public instance method of one concrete service. */
        TARGET_UNRESOLVED,
        /** RV-B: the recomputed fingerprint differs from the stamped one; the code changed since approval. */
        STALE_DRAFT,
        /** RV-C: the recomputed label differs from the recorded one; the approval no longer attaches to what was read. */
        LABEL_DRIFT,
        /** RV-D: the target is already a discovered binding or an admitted attestation target; the proven tier wins. */
        SUPERSEDED,
        /** RV-E: a parameter neither maps to a primitive, nor is an in-tree enum, nor is a paging parameter. */
        INPUT_UNMAPPED,
        /** RV-G: the human half is missing or malformed: description, reviewer, date, origin, review state, or name. */
        REVIEW_INCOMPLETE
    }
}
