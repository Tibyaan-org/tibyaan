package com.tibyaan.provider.spring.attest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * One raw entry from the adopter's committed {@code tibyan/attestations.json} (Volume 5 section 4.15.3),
 * exactly as authored, before intake validation. It is the hand-editable record shape; intake (section
 * 4.15.4) resolves and validates it, and only a passing entry becomes an attested capability. Unknown
 * fields are ignored so a typo surfaces as a located missing-field refusal at intake rather than a parse
 * abort that would take down the whole file.
 *
 * @param target         the structural anchor: {@code <FQCN>#<method>(<paramTypes>)}
 * @param category       the token: {@code predicate}, {@code shape}, or {@code both}
 * @param name           the human-minted capability name (the tool prefixes it {@code attested_})
 * @param resultSemantic the one-sentence result claim
 * @param parameters     per-input coverage: a sentence, the {@code paging} mark, or the {@code id} mark
 * @param subjectEntity  the asserted subject entity for a shape or both claim, else null
 * @param securityOwner  AT-3: the named owner of the security decision a boundary entry makes
 * @param readOnlyAffirmed AT-3: the explicit read-only affirmation for a delegation; null or false refuses
 * @param assertedAuthorities AT-3: the authorities asserted for a securityUnverifiable contract (C1)
 * @param provenance     who asserted, how, when, and the tool-stamped fingerprint
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AttestationRecord(
        String target,
        String category,
        String name,
        String resultSemantic,
        Map<String, String> parameters,
        String subjectEntity,
        String securityOwner,
        Boolean readOnlyAffirmed,
        List<String> assertedAuthorities,
        Provenance provenance) {

    /** The provenance block of a raw entry (Volume 5 section 4.15.3). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Provenance(String assertedBy, String origin, String date, String fingerprint) {
    }

    /** The parameter coverage map, never null. */
    public Map<String, String> parameters() {
        return parameters == null ? Map.of() : parameters;
    }

    /** The asserted authorities, never null (AT-3). */
    public List<String> assertedAuthorities() {
        return assertedAuthorities == null ? List.of() : assertedAuthorities;
    }

    /** Whether the entry explicitly affirmed read-only-ness; silence is never an affirmation (AT-3). */
    public boolean affirmedReadOnly() {
        return Boolean.TRUE.equals(readOnlyAffirmed);
    }
}
