package com.tibyaan.provider.spring.attest;

/**
 * A draft assertion awaiting human confirmation (phase Attestation AT-4, Volume 5 section 4.15.5): an
 * entry whose {@code origin} is the reserved draft token {@code ai-draft}.
 *
 * <p>A draft is NOT a refusal and NOT an attestation, and keeping it a third thing is the whole point of
 * this record. It is not a refusal because nothing is wrong with it: it is a proposal doing exactly what
 * the flow asks of it, waiting for a person. It is not an attestation because NOTHING HAS BEEN CLAIMED
 * YET: no human has confirmed a word of it, so it produces no capability, enters no count of attested,
 * and reaches no IR, manifest, document, or schema. AI MAY PROPOSE, ONLY A HUMAN MAY CONFIRM, ONLY
 * CONFIRMED CLAIMS SHIP.
 *
 * <p>What this record deliberately does NOT carry is the draft's WORDS. The proposed result semantic and
 * parameter meanings stay in the file, and the survey names only the target, the proposed capability
 * name, and the category. A drafted sentence rendered on a tool surface is a sentence that can be read,
 * quoted, or pasted as though someone had claimed it, and no one has. Review happens in the file or in
 * the confirm dialogue, where the reviewer is looking at a proposal and knows it.
 *
 * @param target   the entry's target string, exactly as authored
 * @param name     the proposed capability name, before any prefix the tool would mint
 * @param category the proposed category token
 */
public record DraftProposal(String target, String name, String category) {

    /** The reserved origin token that marks an entry as an unconfirmed draft. */
    public static final String DRAFT_ORIGIN = "ai-draft";
}
