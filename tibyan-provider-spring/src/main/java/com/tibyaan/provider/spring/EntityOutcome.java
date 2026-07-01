package com.tibyaan.provider.spring;

/**
 * The verdict for one entity a scan found (Volume 5 section 4, phase E1). A resilient scan accounts
 * for every {@code @Entity} it could parse with exactly one of these, so the survey is complete and
 * honest: an entity either yields a capability or it does not, and if it does not the reason is
 * located, never silent.
 *
 * <p>This does not weaken fail-loud. A {@link Status#FAILED} outcome is the same located error the
 * strict provider raises; the only difference is that a survey records it and moves on instead of
 * aborting. The strict {@code provide} path still refuses the whole scan on any hard failure.
 *
 * @param entityName the entity the outcome is about, for example {@code Product}
 * @param status     whether a capability was discovered for it
 * @param cause      why it failed, or {@link Cause#NONE} when discovered
 * @param detail     for a discovered entity, the capability and its binding; for a failed entity,
 *                   the located reason
 */
public record EntityOutcome(String entityName, Status status, Cause cause, String detail) {

    /** Whether a read capability was discovered for the entity. */
    public enum Status {
        DISCOVERED,
        FAILED
    }

    /**
     * Why an entity produced no capability. Each value is one of the loud causes the provider
     * already distinguishes (Volume 5 section 4.1, and the second-app trial's cause codes), so a
     * survey can tally failures by cause without parsing messages.
     */
    public enum Cause {
        /** Discovered: no failure. */
        NONE,
        /** A field's Java type has no equivalent in the closed primitive set (trial cause f3). */
        UNMAPPED_FIELD_TYPE,
        /** The entity has no single {@code @Id} identifier, so no read-by-id is modeled (trial cause c). */
        NO_IDENTIFIER,
        /** No {@code @Service} method has the read-by-id shape that backs this entity (trial cause d). */
        NO_BACKING_METHOD,
        /** More than one {@code @Service} method backs this entity; the binding is ambiguous (cause e). */
        AMBIGUOUS_BINDING
    }

    public EntityOutcome {
        if (entityName == null || entityName.isBlank()) {
            throw new IllegalArgumentException("an entity outcome must name its entity");
        }
        if (status == null) {
            throw new IllegalArgumentException("an entity outcome must have a status");
        }
        cause = cause == null ? Cause.NONE : cause;
        detail = detail == null ? "" : detail;
    }

    static EntityOutcome discovered(String entityName, String capabilityName, String binding) {
        return new EntityOutcome(entityName, Status.DISCOVERED, Cause.NONE,
                capabilityName + " -> " + binding);
    }

    static EntityOutcome failed(String entityName, Cause cause, String reason) {
        return new EntityOutcome(entityName, Status.FAILED, cause, reason);
    }

    public boolean isDiscovered() {
        return status == Status.DISCOVERED;
    }
}
