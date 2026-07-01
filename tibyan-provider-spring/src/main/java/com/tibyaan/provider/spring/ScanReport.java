package com.tibyaan.provider.spring;

import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.provider.spring.EntityOutcome.Cause;
import com.tibyaan.provider.spring.EntityOutcome.Status;

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
 * @param model         the IR holding only successfully discovered entities and capabilities
 * @param entityOutcomes one verdict per entity the scan parsed, never null
 * @param parseSkips    one entry per file the scan could not parse, never null
 */
public record ScanReport(
        CapabilityModel model,
        List<EntityOutcome> entityOutcomes,
        List<ParseSkip> parseSkips) {

    public ScanReport {
        if (model == null) {
            throw new IllegalArgumentException("a scan report must carry a model");
        }
        entityOutcomes = entityOutcomes == null ? List.of() : List.copyOf(entityOutcomes);
        parseSkips = parseSkips == null ? List.of() : List.copyOf(parseSkips);
    }

    /** The number of entities for which a capability was discovered. */
    public long discoveredCount() {
        return entityOutcomes.stream().filter(EntityOutcome::isDiscovered).count();
    }

    /** The number of entities that failed, across all causes. */
    public long failedCount() {
        return entityOutcomes.stream().filter(o -> o.status() == Status.FAILED).count();
    }

    /** The number of files that could not be parsed and were skipped. */
    public long skippedByParseCount() {
        return parseSkips.size();
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
