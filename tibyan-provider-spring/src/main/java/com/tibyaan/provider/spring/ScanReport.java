package com.tibyaan.provider.spring;

import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.provider.spring.EntityOutcome.Cause;
import com.tibyaan.provider.spring.EntityOutcome.Status;
import com.tibyaan.provider.spring.attest.IntakeRefusal;
import com.tibyaan.provider.spring.extractors.SkippedField;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The result of a resilient scan (Volume 5 section 4, phase E1): the IR of what was discovered,
 * plus a complete account of what the scan saw.
 *
 * <p>Unlike the strict {@code provide}, a scan never aborts on the first problem. It walks the whole
 * source, parses collect-and-continue, and classifies every entity it could parse. The report is
 * the survey: the discovered IR ({@link #model}), one {@link EntityOutcome} per entity found, and
 * one {@link ParseSkip} per file it could not parse. Discovered rising while nothing is mis-bound is
 * the health metric (Volume 13 section 5); a mis-binding would be a wrong capability in
 * {@link #model}, which the strict discovery rule (Volume 5 section 4.1) is designed never to emit.
 *
 * <p>Phase E4 adds the list dimension (Volume 5 section 4.10): {@link #listOutcomes} carries one entry
 * per entity that has at least one list-all candidate (discovered, or refused as LIST_AMBIGUOUS), and
 * {@link #deferredListReads} names every collection read the phase does not model (filtered, view, or a
 * non-framework terminal). Both are additive and orthogonal to {@link #entityOutcomes}, which keeps its
 * historical by-id meaning so the entity partition stays comparable across phases (Volume 11 section 3.3).
 *
 * @param model            the IR holding the discovered entities and capabilities (by-id and list)
 * @param entityOutcomes   one by-id verdict per entity the scan parsed, never null
 * @param parseSkips       one entry per file the scan could not parse, never null
 * @param skippedFields    one entry per field skipped by tolerance or dropped by closure, never null
 *                         (Volume 5 sections 4.8, 4.7)
 * @param listOutcomes     one entry per entity with a list-all candidate (discovered or ambiguous), never null
 * @param deferredListReads one entry per collection read E4 does not model, never null
 * @param enumParsedInBody one signature per read whose closed set is NOT in its signature because a String
 *                         parameter is parsed into an in-tree enum inside the body (phase Enum Inputs,
 *                         Volume 5 section 4.16.4). These reads carry NO constraint and are NOT refused on
 *                         that account: the declared contract genuinely is a string, so an unconstrained
 *                         STRING is the truthful rendering. The ledger names them anyway, so the case is
 *                         visible rather than silently unconstrained
 */
public record ScanReport(
        CapabilityModel model,
        List<EntityOutcome> entityOutcomes,
        List<ParseSkip> parseSkips,
        List<SkippedField> skippedFields,
        List<ListOutcome> listOutcomes,
        List<DeferredListRead> deferredListReads,
        List<IntakeRefusal> attestationRefusals,
        List<String> enumParsedInBody) {

    /** The pre-Enum-Inputs shape, kept so existing construction sites stay correct and unchanged. */
    public ScanReport(CapabilityModel model, List<EntityOutcome> entityOutcomes, List<ParseSkip> parseSkips,
            List<SkippedField> skippedFields, List<ListOutcome> listOutcomes,
            List<DeferredListRead> deferredListReads, List<IntakeRefusal> attestationRefusals) {
        this(model, entityOutcomes, parseSkips, skippedFields, listOutcomes, deferredListReads,
                attestationRefusals, List.of());
    }

    public ScanReport {
        if (model == null) {
            throw new IllegalArgumentException("a scan report must carry a model");
        }
        entityOutcomes = entityOutcomes == null ? List.of() : List.copyOf(entityOutcomes);
        parseSkips = parseSkips == null ? List.of() : List.copyOf(parseSkips);
        skippedFields = skippedFields == null ? List.of() : List.copyOf(skippedFields);
        listOutcomes = listOutcomes == null ? List.of() : List.copyOf(listOutcomes);
        deferredListReads = deferredListReads == null ? List.of() : List.copyOf(deferredListReads);
        attestationRefusals = attestationRefusals == null ? List.of() : List.copyOf(attestationRefusals);
        enumParsedInBody = enumParsedInBody == null ? List.of() : List.copyOf(enumParsedInBody);
    }

    /**
     * The attested capabilities in the model (phase Attestation, Volume 5 section 4.15). Counted
     * SEPARATELY from discovered and never summed into it: the verified count stays pure forever.
     */
    public long attestedCount() {
        return model.capabilities().stream().filter(com.tibyaan.core.ir.Capability::hasAttestation).count();
    }

    /** The number of entities for which a by-id capability was discovered. */
    public long discoveredCount() {
        return entityOutcomes.stream().filter(EntityOutcome::isDiscovered).count();
    }

    /** The number of list capabilities discovered (Volume 5 section 4.10). */
    public long listDiscoveredCount() {
        return listOutcomes.stream().filter(ListOutcome::isDiscovered).count();
    }

    /** The number of list-all candidates refused as ambiguous (LIST_AMBIGUOUS). */
    public long listRefusedCount() {
        return listOutcomes.stream().filter(o -> !o.isDiscovered()).count();
    }

    /** Total discovered capabilities across both kinds: {@code by-id + list}. */
    public long totalDiscoveredCount() {
        return discoveredCount() + listDiscoveredCount();
    }

    /** The number of entities that failed, across all causes. */
    public long failedCount() {
        return entityOutcomes.stream().filter(o -> o.status() == Status.FAILED).count();
    }

    /** The number of files that could not be parsed and were skipped. */
    public long skippedByParseCount() {
        return parseSkips.size();
    }

    /** The number of entities whose shape is partial: they lost at least one field (section 4.9). */
    public long partialEntityCount() {
        return entityOutcomes.stream().filter(EntityOutcome::isPartial).count();
    }

    /** The total number of fields skipped by tolerance or dropped by closure across all entities. */
    public long skippedFieldCount() {
        return skippedFields.size();
    }

    /** Failed entity counts grouped by cause, in a stable order, omitting causes with no failures. */
    public Map<Cause, Long> failuresByCause() {
        Map<Cause, Long> counts = new TreeMap<>();
        for (EntityOutcome outcome : entityOutcomes) {
            if (outcome.status() == Status.FAILED) {
                counts.merge(outcome.cause(), 1L, Long::sum);
            }
        }
        return counts;
    }
}
