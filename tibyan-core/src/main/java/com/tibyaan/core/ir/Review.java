package com.tibyaan.core.ir;

import java.util.Set;

/**
 * The reviewed third tier (Volume 4 section 3.10; phase DL-1, Volume 5 section 4.17): a named human's
 * approval of a drafted capability, carried beside the tool's own label so neither is mistaken for the
 * other. The tool guarantees carriage, the fingerprint pin and label drift; the human owns the truth of the
 * description. Plain record, no dependencies (CLAUDE.md rule 1).
 *
 * @param label             the tool's read-safety label at the time of approval, from the closed set
 * @param reviewedBy        the named human who approved the entry, never blank
 * @param reviewedOn        the date of the approval, never blank
 * @param descriptionOrigin who produced the description's words: {@code human} or {@code ai-draft}
 * @param fingerprint       the structural fingerprint of the approved slice, never blank
 */
public record Review(
        ReviewLabel label,
        String reviewedBy,
        String reviewedOn,
        String descriptionOrigin,
        String fingerprint) {

    /** The description was written by a person. */
    public static final String ORIGIN_HUMAN = "human";

    /** The description was drafted by an agent and approved by the named reviewer. */
    public static final String ORIGIN_AI_DRAFT = "ai-draft";

    /** The closed set of description origins (core rule 12). */
    public static final Set<String> ORIGINS = Set.of(ORIGIN_HUMAN, ORIGIN_AI_DRAFT);

    public Review {
        if (label == null) {
            throw new IllegalArgumentException("a review must carry the tool's label");
        }
    }

    // Named complete, not isComplete, for the reason Capability.hasAttestation records: an is-prefixed method is
    // auto-detected as a serialized boolean property by the IR JSON mapper and breaks the record round-trip.
    /** Whether every field is present and the origin is from the closed set (core rule 12). */
    public boolean complete() {
        return notBlank(reviewedBy) && notBlank(reviewedOn) && notBlank(fingerprint)
                && descriptionOrigin != null && ORIGINS.contains(descriptionOrigin);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
