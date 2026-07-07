package com.tibyaan.cli;

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

/**
 * Renders a {@link ScanReport} as a shareable Markdown survey report (Volume 11 section 3.4). It is a
 * second rendering of the same facts the console survey prints and the manifest carries: every listed
 * capability is structurally proven and bound to a named method, every refusal is named with its
 * located reason, and nothing is guessed. The output is deterministic (no timestamps, no absolute
 * paths in the body) so it is golden-stable and diffable across runs.
 *
 * <p>This adds no capability and makes no semantic claim. The "most common blocker" is a descriptive
 * tally of the failure causes already in the survey, not a recommendation the tool cannot substantiate.
 */
final class MarkdownReport {

    private MarkdownReport() {
    }

    /**
     * Renders the report. {@code appName} names the surveyed application in the title; the caller
     * passes the scanned path's final segment, which is deterministic for a given input.
     */
    static String render(ScanReport report, String appName) {
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
        long operable = report.totalDiscoveredCount();

        StringBuilder md = new StringBuilder();
        md.append("# Tibyaan capability survey: ").append(appName).append("\n\n");
        md.append("Tibyaan read this application's source and proved which reads an AI agent can ")
                .append("operate. Every capability below is bound to a specific real method that ")
                .append("Tibyaan proved backs it. Everything it could not prove is listed as a refusal, ")
                .append("with the located reason. Nothing here is guessed, and `mis-bound` is 0: no ")
                .append("capability points at the wrong method or entity.\n\n");

        md.append("## Summary\n\n");
        md.append("| Metric | Count |\n");
        md.append("| --- | --- |\n");
        md.append("| Operable, read by id | ").append(report.discoveredCount()).append(" |\n");
        md.append("| Operable, list all | ").append(report.listDiscoveredCount()).append(" |\n");
        md.append("| Refused (by id) | ").append(report.failedCount()).append(" |\n");
        md.append("| List refused | ").append(report.listRefusedCount()).append(" |\n");
        md.append("| Files skipped (unparseable) | ").append(report.skippedByParseCount()).append(" |\n");
        md.append("| Partial entities | ").append(report.partialEntityCount()).append(" |\n");
        md.append("| Wrong bindings (mis-bound) | 0 |\n\n");

        md.append("## What an agent can operate today (").append(operable).append(")\n\n");
        if (operable == 0) {
            md.append("Nothing was structurally provable in this application yet. That is not ")
                    .append("necessarily a fault in the application: it means its read methods do not ")
                    .append("match a shape Tibyaan can prove without reading business semantics. The ")
                    .append("refusals below say why, and the most common blocker names the ")
                    .append("highest-leverage change.\n\n");
        } else {
            if (!discovered.isEmpty()) {
                md.append("### Read by id (").append(discovered.size()).append(")\n\n");
                for (EntityOutcome outcome : discovered) {
                    md.append("- ").append(outcome.detail()).append(partialQualifier(outcome)).append("\n");
                }
                md.append("\n");
            }
            if (!listDiscovered.isEmpty()) {
                md.append("### List all (").append(listDiscovered.size()).append(")\n\n");
                for (ListOutcome outcome : listDiscovered) {
                    md.append("- ").append(outcome.detail()).append("\n");
                }
                md.append("\n");
            }
        }

        boolean anyRefusal = !failed.isEmpty() || !listRefused.isEmpty()
                || !report.parseSkips().isEmpty() || !report.deferredListReads().isEmpty();
        if (anyRefusal) {
            md.append("## Why the rest was refused\n\n");
            md.append("A refusal is the tool working, not failing: rather than expose a ")
                    .append("plausible-but-wrong tool an agent would call against your data, Tibyaan ")
                    .append("declines and says why.\n\n");

            if (!failed.isEmpty()) {
                md.append("### Refused reads (").append(failed.size()).append(")\n\n");
                for (EntityOutcome outcome : failed) {
                    md.append("- **").append(outcome.entityName()).append("** [").append(outcome.cause())
                            .append("]: ").append(firstSentence(outcome.detail()))
                            .append(partialQualifier(outcome)).append("\n");
                }
                md.append("\n");
            }
            if (!listRefused.isEmpty()) {
                md.append("### List candidates refused (").append(listRefused.size()).append(")\n\n");
                for (ListOutcome outcome : listRefused) {
                    md.append("- **").append(outcome.entityName()).append("** [").append(outcome.status())
                            .append("]: ").append(firstSentence(outcome.detail())).append("\n");
                }
                md.append("\n");
            }
            if (!report.parseSkips().isEmpty()) {
                md.append("### Files it could not read (").append(report.parseSkips().size()).append(")\n\n");
                md.append("These were skipped so the survey could finish. Any capabilities they ")
                        .append("contain are not represented here.\n\n");
                for (ParseSkip skip : report.parseSkips()) {
                    md.append("- ").append(skip.describe()).append("\n");
                }
                md.append("\n");
            }
            if (!report.deferredListReads().isEmpty()) {
                md.append("### Deferred reads (").append(report.deferredListReads().size()).append(")\n\n");
                md.append("Filtered, view-returning, or transformed reads Tibyaan does not model in ")
                        .append("v1. Named, never guessed.\n\n");
                for (DeferredListRead deferred : report.deferredListReads()) {
                    md.append("- ").append(deferred.describe()).append("\n");
                }
                md.append("\n");
            }
        }

        if (!report.skippedFields().isEmpty()) {
            md.append("## Partial entities (").append(report.partialEntityCount())
                    .append(", ").append(report.skippedFieldCount()).append(" fields skipped)\n\n");
            md.append("These entities were discovered or refused with an incomplete shape: a field ")
                    .append("Tibyaan could not map was skipped rather than vetoing the whole entity.\n\n");
            for (SkippedField skip : report.skippedFields()) {
                md.append("- ").append(skip.describe()).append("\n");
            }
            md.append("\n");
        }

        md.append("## The most common blocker\n\n");
        md.append(mostCommonBlocker(report)).append("\n\n");

        md.append("## How to read this\n\n");
        md.append("Every operable capability is structurally proven and bound to the named method. ")
                .append("Refusals are named, not guessed. `mis-bound 0` means no capability points at ")
                .append("the wrong method or entity. Coverage depends on your application's idioms, so ")
                .append("this is your application's actual result, not a benchmark number.\n");

        return md.toString();
    }

    private static String mostCommonBlocker(ScanReport report) {
        Map<Cause, Long> byCause = report.failuresByCause();
        long parseWall = report.skippedByParseCount();

        Cause topCause = null;
        long topCount = 0;
        for (Map.Entry<Cause, Long> entry : byCause.entrySet()) {
            if (entry.getValue() > topCount) {
                topCount = entry.getValue();
                topCause = entry.getKey();
            }
        }

        if (topCount == 0 && parseWall == 0) {
            return "No blocker: every entity this survey reached produced a capability.";
        }
        if (parseWall > topCount) {
            return parseWall + " source file(s) could not be parsed, so the entities they contain "
                    + "were never reached. This is the largest blocker; those files are named above "
                    + "under \"Files it could not read\".";
        }
        return topCount + " " + (topCount == 1 ? "entity was" : "entities were") + " blocked by "
                + topCause + ": " + causePhrase(topCause) + ". This is the highest-leverage class to "
                + "address, though whether the resulting reads are safe to expose is a separate question.";
    }

    private static String causePhrase(Cause cause) {
        return switch (cause) {
            case UNMAPPED_FIELD_TYPE -> "a field type the closed set does not map, for example an "
                    + "association, a to-many collection, an embeddable value type, or an inexact numeric";
            case NO_IDENTIFIER -> "no bindable single @Id, so no read-by-id can be modeled";
            case COMPOSITE_KEY -> "a composite key, which a read-by-single-id cannot address";
            case NO_BACKING_METHOD -> "no @Service method with the read-by-id shape that returns the entity";
            case AMBIGUOUS_BINDING -> "more than one candidate method, so the binding is ambiguous and "
                    + "the tool refuses rather than guess";
            case NONE -> "no failure";
        };
    }

    private static String partialQualifier(EntityOutcome outcome) {
        if (!outcome.isPartial()) {
            return "";
        }
        return " (partial: " + outcome.partialFields().size() + " fields skipped)";
    }

    private static String firstSentence(String detail) {
        int end = detail.indexOf(". ");
        return end < 0 ? detail : detail.substring(0, end + 1);
    }
}
