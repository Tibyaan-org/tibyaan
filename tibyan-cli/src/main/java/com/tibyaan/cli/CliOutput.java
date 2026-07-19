package com.tibyaan.cli;

import com.tibyaan.core.ir.Annotation;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.provider.spring.DeferredListRead;
import com.tibyaan.provider.spring.EntityOutcome;
import com.tibyaan.provider.spring.EntityOutcome.Cause;
import com.tibyaan.provider.spring.ListOutcome;
import com.tibyaan.provider.spring.ParseSkip;
import com.tibyaan.provider.spring.ScanReport;
import com.tibyaan.provider.spring.extractors.SkippedField;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Small shared formatting for the CLI commands. */
final class CliOutput {

    private CliOutput() {
    }

    /**
     * Prints a resilient scan's survey (Volume 5 section 4, phase E1): the provider, the parse
     * tally, a per-entity outcome for everything parsed, every file skipped, and the totals. The
     * mis-bound count is stated and is always zero by construction: a capability is emitted only on
     * an exact single-shape match (Volume 5 section 4.1), so a wrong binding cannot be produced.
     */
    static void printScanReport(ScanReport report) {
        System.out.println("Provider: spring (entity and service extractors)");

        long parsed = report.entityOutcomes().size();
        System.out.println("Entities found (in parseable files): " + parsed);

        List<EntityOutcome> discovered = report.entityOutcomes().stream()
                .filter(EntityOutcome::isDiscovered)
                .collect(Collectors.toList());
        List<EntityOutcome> failed = report.entityOutcomes().stream()
                .filter(o -> o.status() == EntityOutcome.Status.FAILED)
                .collect(Collectors.toList());
        List<ListOutcome> listDiscovered = report.listOutcomes().stream()
                .filter(ListOutcome::isDiscovered)
                .collect(Collectors.toList());
        List<ListOutcome> listRefused = report.listOutcomes().stream()
                .filter(o -> !o.isDiscovered())
                .collect(Collectors.toList());

        // Discovered, split by kind (Volume 11 section 3.3): by-id A stays comparable to every
        // historical row; the list subsection appears only when there are list discoveries.
        long total = discovered.size() + listDiscovered.size();
        if (total > 0) {
            System.out.println("Discovered (" + total + "):");
            if (listDiscovered.isEmpty()) {
                for (EntityOutcome outcome : discovered) {
                    System.out.println("  + " + outcome.entityName() + ": " + outcome.detail()
                            + partialQualifier(outcome));
                }
            } else {
                System.out.println("  by-id (" + discovered.size() + "):");
                for (EntityOutcome outcome : discovered) {
                    System.out.println("    + " + outcome.entityName() + ": " + outcome.detail()
                            + partialQualifier(outcome));
                }
                System.out.println("  list (" + listDiscovered.size() + "):");
                for (ListOutcome outcome : listDiscovered) {
                    System.out.println("    + " + outcome.detail());
                }
            }
        }
        if (!failed.isEmpty()) {
            System.out.println("Failed (" + failed.size() + "):");
            for (EntityOutcome outcome : failed) {
                System.out.println("  - " + outcome.entityName() + " [" + outcome.cause() + "]: "
                        + firstSentence(outcome.detail()) + partialQualifier(outcome));
            }
        }
        // List-refused is its own line, never folded into the by-id failure ledger, so the entity
        // partition (by-id discovered plus failed) stays comparable across phases (Volume 11 section 3.3).
        if (!listRefused.isEmpty()) {
            System.out.println("List-refused (" + listRefused.size() + "):");
            for (ListOutcome outcome : listRefused) {
                System.out.println("  - " + outcome.entityName() + " [" + outcome.status() + "]: "
                        + firstSentence(outcome.detail()));
            }
        }
        if (!report.parseSkips().isEmpty()) {
            System.out.println("Skipped, unparseable (" + report.parseSkips().size() + "):");
            for (ParseSkip skip : report.parseSkips()) {
                System.out.println("  ~ " + skip.describe());
            }
        }
        // Partial-model integrity (Volume 5 section 4.9): every field skipped by tolerance or dropped
        // by closure is disclosed here with its located note; the entity extracted without it.
        if (!report.skippedFields().isEmpty()) {
            System.out.println("Skipped fields (" + report.skippedFieldCount() + "):");
            for (SkippedField skip : report.skippedFields()) {
                System.out.println("  * " + skip.describe());
            }
        }
        // Deferred list reads (Volume 5 section 4.10.6): collection reads E4 does not model, named with
        // their reason. They are not failures and never count as missed capabilities; they are the
        // evidence for the successor filter-semantics phase.
        if (!report.deferredListReads().isEmpty()) {
            System.out.println("Deferred list reads (" + report.deferredListReads().size() + "):");
            for (DeferredListRead deferred : report.deferredListReads()) {
                System.out.println("  ~ " + deferred.describe());
            }
        }

        // Declared security (Volume 5 section 4.14.5): one summary line, emitted only when a contract was
        // declared on at least one discovered read, plus a per-capability line naming its compiled
        // sequence or its unverifiable reason. A security-free app emits nothing here and stays identical.
        printSecurity(report.model());

        System.out.println("Totals: discovered " + report.totalDiscoveredCount()
                + " (by-id " + report.discoveredCount() + ", list " + report.listDiscoveredCount() + ")"
                + ", failed " + report.failedCount() + byCause(report.failuresByCause())
                + ", list-refused " + report.listRefusedCount()
                + ", skipped-by-parse " + report.skippedByParseCount()
                + ", partial " + report.partialEntityCount()
                + " (" + report.skippedFieldCount() + " fields skipped)"
                + ", mis-bound 0");
    }

    // The declared-security survey (Volume 5 section 4.14.5). Counts the capabilities carrying a compiled
    // contract and those marked unverifiable, then lists each with its authorities or its reason. Emitted
    // only when a contract was declared somewhere, so a security-free app's survey is byte-identical.
    private static void printSecurity(CapabilityModel model) {
        List<Capability> compiled = model.capabilities().stream()
                .filter(c -> annotationValues(c, "requiredAuthorities") != null)
                .collect(Collectors.toList());
        List<Capability> unverifiable = model.capabilities().stream()
                .filter(c -> annotationValues(c, "securityUnverifiable") != null)
                .collect(Collectors.toList());
        int declared = compiled.size() + unverifiable.size();
        if (declared == 0) {
            return;
        }
        System.out.println("security: declared " + declared + " (compiled " + compiled.size()
                + ", unverifiable " + unverifiable.size() + ")");
        for (Capability capability : compiled) {
            System.out.println("  " + capability.name() + ": requires any of "
                    + annotationValues(capability, "requiredAuthorities"));
        }
        for (Capability capability : unverifiable) {
            List<String> reason = annotationValues(capability, "securityUnverifiable");
            System.out.println("  " + capability.name() + ": unverifiable ("
                    + (reason.isEmpty() ? "" : reason.get(0)) + ")");
        }
    }

    private static List<String> annotationValues(Capability capability, String key) {
        for (Annotation annotation : capability.annotations()) {
            if (annotation.key().equals(key)) {
                return annotation.values();
            }
        }
        return null;
    }

    // The partial qualifier on a discovered or failed line (Volume 5 section 4.9): the entity
    // extracted but its shape is incomplete. Absent when the entity is complete.
    private static String partialQualifier(EntityOutcome outcome) {
        if (!outcome.isPartial()) {
            return "";
        }
        return " (partial: " + outcome.partialFields().size() + " fields skipped)";
    }

    private static String byCause(Map<Cause, Long> failuresByCause) {
        if (failuresByCause.isEmpty()) {
            return "";
        }
        String causes = failuresByCause.entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue())
                .collect(Collectors.joining(", "));
        return " (by cause: " + causes + ")";
    }

    // The located reason can be long (a no-backing message lists the service methods it looked at);
    // the survey shows only the first sentence, which is the concise, located summary. The full
    // detail is what the strict provide path raises.
    private static String firstSentence(String detail) {
        int end = detail.indexOf(". ");
        return end < 0 ? detail : detail.substring(0, end + 1);
    }

    /** A one-line count of what an IR holds, for example "1 entity (Product), 1 capability (getProduct), 0 relations". */
    static String summary(CapabilityModel ir) {
        int entities = ir.entities().size();
        int capabilities = ir.capabilities().size();
        int relations = ir.relations().size();
        return entities + " " + plural(entities, "entity", "entities")
                + " (" + names(ir.entities().stream().map(Entity::name).collect(Collectors.toList())) + "), "
                + capabilities + " " + plural(capabilities, "capability", "capabilities")
                + " (" + names(ir.capabilities().stream().map(Capability::name).collect(Collectors.toList())) + "), "
                + relations + " " + plural(relations, "relation", "relations");
    }

    static void printValidationFailure(List<String> errors) {
        System.err.println("Validation: FAILED");
        for (String error : errors) {
            System.err.println("  - " + error);
        }
    }

    private static String names(List<String> values) {
        return values.isEmpty() ? "none" : String.join(", ", values);
    }

    private static String plural(int count, String one, String many) {
        return count == 1 ? one : many;
    }
}
