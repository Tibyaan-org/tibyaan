package com.tibyaan.provider.spring.draft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * One draft candidate tool (spike DL-0; the record of phase DL-1, Volume 5 section 4.17.2): a public service
 * method, labeled by what the tool could establish about it, awaiting a reviewer's description and approval.
 * Field order is the file order. DL-0's fields come first, unchanged; DL-1 appends the review fields.
 *
 * <p>Who writes what: the tool writes {@code name} (a minted default the reviewer may rename), {@code target},
 * {@code binding}, {@code label}, {@code reason}, {@code capability}, {@code returnType}, {@code inputs},
 * {@code fingerprint}, {@code stale} and {@code previousLabel}. The reviewer writes {@code description},
 * {@code descriptionOrigin}, {@code review}, {@code reviewedBy} and {@code reviewedOn}. Re-running
 * {@code tibyan draft} merges and never overwrites a reviewer field (section 4.17.2).
 *
 * @param name              the minted tool name, unique within the drafts file; the reviewer may rename it, and
 *                          the tool applies the {@code reviewed_} prefix itself at intake
 * @param target            the method in the attestation target format, {@code <FQCN>#<method>(<simple param types>)}
 * @param binding           the runtime binding, {@code <ServiceSimpleName>#<method>}
 * @param label             the label, see {@link DraftLabel}
 * @param reason            the located reason for the label, in the words of the rule that produced it
 * @param capability        the existing capability name when the label is VERIFIED or ATTESTED, else null
 * @param returnType        the return type exactly as written in source
 * @param inputs            the parameters in declaration order
 * @param description       empty until a reviewer or an agent writes what the method means; the tool never authors it
 * @param review            the review state: {@code pending} when written by the tool, {@code approved} or
 *                          {@code rejected} when written by a reviewer
 * @param reviewedBy        the named human who approved or rejected the entry, null while pending
 * @param reviewedOn        the date of that review, null while pending
 * @param descriptionOrigin who produced the description's words, {@code human} (the default) or {@code ai-draft}
 * @param fingerprint       TOOL-STAMPED at draft time over the structural slice section 4.15.4 fingerprints, so an
 *                          approval is pinned to the code it approved; null when the target did not resolve
 * @param stale             true when a re-run of {@code tibyan draft} no longer finds the target among the
 *                          candidates; the entry is kept in place with its old fingerprint, never deleted
 * @param describedBy       the model that drafted the description (phase Describe), else null
 * @param describedFrom     the fingerprint the description was drafted from, else null
 * @param descriptionStale  true when a drafted description predates the current fingerprint (DS-4), else null
 * @param previousLabel     the label the entry carried before a re-run recomputed a different one, else null; a
 *                          changed label resets {@code review} to {@code pending}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DraftEntry(
        String name,
        String target,
        String binding,
        DraftLabel label,
        String reason,
        @JsonInclude(JsonInclude.Include.NON_NULL) String capability,
        String returnType,
        List<DraftInput> inputs,
        String description,
        String review,
        @JsonInclude(JsonInclude.Include.NON_NULL) String reviewedBy,
        @JsonInclude(JsonInclude.Include.NON_NULL) String reviewedOn,
        String descriptionOrigin,
        String fingerprint,
        @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean stale,
        @JsonInclude(JsonInclude.Include.NON_NULL) DraftLabel previousLabel,
        @JsonInclude(JsonInclude.Include.NON_NULL) String describedBy,
        @JsonInclude(JsonInclude.Include.NON_NULL) String describedFrom,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean descriptionStale) {

    /** The pre-Describe shape (phase Describe, Volume 5 section 4.15.5.2): no drafter provenance. */
    public DraftEntry(String name, String target, String binding, DraftLabel label, String reason, String capability,
                      String returnType, List<DraftInput> inputs, String description, String review,
                      String reviewedBy, String reviewedOn, String descriptionOrigin, String fingerprint,
                      boolean stale, DraftLabel previousLabel) {
        this(name, target, binding, label, reason, capability, returnType, inputs, description, review, reviewedBy,
                reviewedOn, descriptionOrigin, fingerprint, stale, previousLabel, null, null, null);
    }

    /** The review state the tool writes; approval and rejection are a reviewer's words, never the tool's. */
    public static final String PENDING = "pending";

    /** The reviewer's approval; the entry is admitted at scan when every intake condition holds. */
    public static final String APPROVED = "approved";

    /** The reviewer's rejection; the entry produces nothing and is not a refusal. */
    public static final String REJECTED = "rejected";

    /** The default description origin: a person wrote the words. */
    public static final String ORIGIN_HUMAN = "human";

    /** The description was drafted by an agent; only a named human's approval ships it. */
    public static final String ORIGIN_AI_DRAFT = "ai-draft";

    public DraftEntry {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        description = description == null ? "" : description;
        review = review == null ? PENDING : review;
        descriptionOrigin = descriptionOrigin == null ? ORIGIN_HUMAN : descriptionOrigin;
    }

    /** The DL-0 shape: a freshly labeled, pending candidate with no review fields and no stamp yet. */
    public static DraftEntry fresh(String name, String target, String binding, DraftLabel label, String reason,
                                   String capability, String returnType, List<DraftInput> inputs,
                                   String fingerprint) {
        return new DraftEntry(name, target, binding, label, reason, capability, returnType, inputs, "", PENDING,
                null, null, ORIGIN_HUMAN, fingerprint, false, null);
    }

    /**
     * Phase Describe (Volume 5 section 4.15.5.2, DS-3): the same entry carrying a drafted description verbatim,
     * its origin ai-draft, the model that drafted it and the fingerprint it was drafted from. The review state
     * is untouched: a draft is a third thing until a named human approves it.
     */
    public DraftEntry described(String words, String model) {
        return new DraftEntry(name, target, binding, label, reason, capability, returnType, inputs, words, review,
                reviewedBy, reviewedOn, ORIGIN_AI_DRAFT, fingerprint, stale, previousLabel, model, fingerprint, null);
    }

    /** True when a drafted description was written from a fingerprint the entry no longer carries (DS-4). */
    public boolean descriptionOutdated() {
        return Boolean.TRUE.equals(descriptionStale);
    }

    // No is-prefixed convenience accessor here on purpose: Jackson would auto-detect it as a serialized
    // boolean property and write it into the file (the same trap Capability.hasAttestation documents).
    // Callers compare review() to the three constants directly.
}
