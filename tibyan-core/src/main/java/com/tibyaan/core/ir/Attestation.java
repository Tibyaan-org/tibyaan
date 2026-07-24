package com.tibyaan.core.ir;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A human-attested claim carried on a {@link Capability}, the third category of knowledge (Volume 4
 * section 3.8; Volume 5 section 4.15). It is absent on every tool-verified capability and, when present,
 * carries the human's claim, its per-input coverage, and its provenance. This is the ONE first-class
 * component the Attestation phase adds; it is never the Annotation hook, because completeness is itself
 * the phase's invariant (zero attestation drift) and a validator must be able to see the structure to
 * enforce it (core rule 8).
 *
 * <p>The component carries only what structure cannot reach: meaning. The binding, the parameter types,
 * and the read-safety of the target stay structurally verified around every attestation; an assertion
 * fills a semantic hole, never a structural one (Volume 5 section 4.15.2). This record enforces only its
 * own internal invariants (a category and origin from the closed enums, an immutable meanings map);
 * completeness against the capability's inputs is core rule 8, which needs the capability too.
 *
 * @param category          which holes the entry fills (predicate, shape, both)
 * @param resultSemantic    the human's one-sentence result claim, carried character-exactly
 * @param parameterMeanings one entry per capability input; keyset equals the input set (rule 8). Each
 *                          value is a human meaning sentence, the reserved {@link #PAGING_MARK}, or the
 *                          reserved {@link #ID_MARK}
 * @param subjectEntity     the asserted subject entity (a display name in the model) for shape or both;
 *                          null for a pure predicate
 * @param assertedBy        the named asserter, non-empty
 * @param origin            how the words were produced (human-authored or ai-drafted-human-confirmed)
 * @param date              the assertion date, from the file, never a clock (determinism, rule 10)
 * @param fingerprint       the SHA-256 of the structural slice the assertion was validated against
 */
public record Attestation(
        AttestationCategory category,
        String resultSemantic,
        Map<String, String> parameterMeanings,
        String subjectEntity,
        String assertedBy,
        AttestationOrigin origin,
        String date,
        String fingerprint) {

    /** The reserved coverage mark for a framework paging or ordering parameter (Volume 5 section 4.15.2). */
    public static final String PAGING_MARK = "paging";

    /** The reserved coverage mark for a structurally-proven by-primary-key identifier. */
    public static final String ID_MARK = "id";

    public Attestation {
        if (category == null) {
            throw new IllegalArgumentException("an attestation must carry a category");
        }
        if (origin == null) {
            throw new IllegalArgumentException("an attestation must carry an origin");
        }
        parameterMeanings = parameterMeanings == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(parameterMeanings));
    }

    /** Whether a coverage value is one of the two reserved structural marks (not a human sentence). */
    public static boolean isReservedMark(String coverage) {
        return PAGING_MARK.equals(coverage) || ID_MARK.equals(coverage);
    }
}
