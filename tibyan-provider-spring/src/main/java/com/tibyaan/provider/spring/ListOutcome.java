package com.tibyaan.provider.spring;

import java.util.List;

/**
 * The list-read verdict for one entity (Volume 5 section 4.10). E4 breaks the one-capability-per-entity
 * denominator: a list outcome is a separate axis from the by-id {@link EntityOutcome}, so an entity can
 * be discovered by-id and also have a list outcome (discovered, refused, or none).
 *
 * <p>Only entities with at least one list-all candidate get a {@code ListOutcome}. Exactly one candidate
 * discovers; two or more refuse as {@link Status#LIST_AMBIGUOUS} (paging is unmodeled, so overloads are
 * indistinguishable, section 4.10.5). A refused list outcome is reported on its own line, never folded
 * into the by-id failure ledger, so the entity partition stays comparable across phases (Volume 11
 * section 3.3).
 *
 * @param entityName the entity the list capability is of, for example {@code Contact}
 * @param status     discovered or refused
 * @param detail     the capability and binding when discovered, or the located ambiguity when refused
 */
public record ListOutcome(String entityName, Status status, String detail) {

    /** Whether a list capability was discovered, or refused as ambiguous. */
    public enum Status {
        DISCOVERED,
        LIST_AMBIGUOUS
    }

    public ListOutcome {
        if (entityName == null || entityName.isBlank()) {
            throw new IllegalArgumentException("a list outcome must name its entity");
        }
        if (status == null) {
            throw new IllegalArgumentException("a list outcome must have a status");
        }
        detail = detail == null ? "" : detail;
    }

    static ListOutcome discovered(String entityName, String detail) {
        return new ListOutcome(entityName, Status.DISCOVERED, detail);
    }

    static ListOutcome ambiguous(String entityName, List<String> candidates) {
        return new ListOutcome(entityName, Status.LIST_AMBIGUOUS,
                "list capability 'list" + entityName + "' has more than one list-all candidate: "
                        + candidates + ". Paging is unmodeled, so these are indistinguishable; the "
                        + "provider will not prefer one (Volume 5 section 4.10.5).");
    }

    public boolean isDiscovered() {
        return status == Status.DISCOVERED;
    }
}
