package com.tibyaan.runtime.spring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The review a reviewed tool carries in the generated capabilities manifest (Volume 6 section 3.9; phase
 * DL-1, Volume 5 section 4.17.5): the tool's own read-safety label at approval time, the named human who
 * approved the entry, the date, and who wrote the description's words ({@code human} or {@code ai-draft}).
 *
 * <p>The runtime acts on none of these fields. They are carried so an operator reading the manifest, or a
 * later surface, can see who signed for a served write. The gate itself keys on the tool's kind, its tier
 * and its exact name (Volume 7 section 11). Unknown fields are tolerated, the additive-field discipline.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviewDescriptor(
        String label,
        String reviewedBy,
        String reviewedOn,
        String descriptionOrigin) {
}
