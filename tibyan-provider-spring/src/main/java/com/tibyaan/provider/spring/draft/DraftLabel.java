package com.tibyaan.provider.spring.draft;

/**
 * The label a draft candidate carries (spike DL-0, Volume 13 section 3, "v2 direction"). A label is an
 * honest statement of what the tool could establish about a service method; it is never a proof of the
 * method's business meaning, and the two proven tiers are the only ones the zero invariants cover.
 */
public enum DraftLabel {
    /** The method is the binding of a discovered capability: proven by the discovery rules. */
    VERIFIED,
    /** The method is the target of a confirmed attestation that passed intake. */
    ATTESTED,
    /** Read-safety found no mutation visible in the slice; binding and shape are not proven. */
    READ,
    /** A mutation is visible: a write-set call, a mutating query, or a setter on a loaded entity. */
    WRITE,
    /** Read-safety could not decide: no analyzable body, or a receiver whose origin is not traceable. */
    UNKNOWN
}
