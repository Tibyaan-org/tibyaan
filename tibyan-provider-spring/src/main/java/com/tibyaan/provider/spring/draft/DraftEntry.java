package com.tibyaan.provider.spring.draft;

import java.util.List;

/**
 * One draft candidate tool (spike DL-0): a public service method, labeled by what the tool could establish
 * about it, awaiting a reviewer's description and approval. Field order is the file order.
 *
 * @param name        the minted tool name, unique within the drafts file
 * @param target      the method in the attestation target format, {@code <FQCN>#<method>(<simple param types>)}
 * @param binding     the runtime binding, {@code <ServiceSimpleName>#<method>}
 * @param label       the label, see {@link DraftLabel}
 * @param reason      the located reason for the label, in the words of the rule that produced it
 * @param capability  the existing capability name when the label is VERIFIED or ATTESTED, else null
 * @param returnType  the return type exactly as written in source
 * @param inputs      the parameters in declaration order
 * @param description empty until a reviewer or an agent writes what the method means; the tool never authors it
 * @param review      the review state, {@code pending} when written by the tool
 */
public record DraftEntry(
        String name,
        String target,
        String binding,
        DraftLabel label,
        String reason,
        String capability,
        String returnType,
        List<DraftInput> inputs,
        String description,
        String review) {

    /** The review state the tool writes; approval and rejection are a reviewer's words, never the tool's. */
    public static final String PENDING = "pending";

    public DraftEntry {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        description = description == null ? "" : description;
        review = review == null ? PENDING : review;
    }
}
