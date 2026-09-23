package com.tibyaan.cli;

import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.provider.spring.ProviderException;
import com.tibyaan.provider.spring.SpringCapabilityProvider;
import com.tibyaan.provider.spring.draft.DraftEntry;
import com.tibyaan.provider.spring.draft.DraftReport;
import com.tibyaan.provider.spring.draft.DraftsFile;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * {@code tibyan draft} (spike DL-0, Volume 13 section 3, "v2 direction"): drafts a candidate tool for every
 * public method of every concrete service class, reads and writes alike, labels each by what the tool could
 * establish, and writes the reviewable {@code tibyan/drafts.json}. It serves nothing and changes nothing in the
 * IR or the manifest; a reviewer fills the descriptions and approves entries in the file.
 *
 * <p>Exit codes: 0 when the drafts file was written; 3 when the source could not be read at all.
 */
@Command(name = "draft",
        description = "Draft a candidate tool for every public service method, reads and writes, labeled by what "
                + "the tool can prove (spike DL-0).")
final class DraftCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "<path>", description = "Path to the system to draft from.")
    Path path;

    @Option(names = "--out", paramLabel = "<drafts.json>",
            description = "Write the drafts file here (default: <path>/tibyan/drafts.json).")
    Path out;

    @Override
    public Integer call() {
        System.out.println("Drafting " + path);
        Path target = out != null ? out : DraftsFile.defaultLocation(path);
        DraftReport report;
        try {
            // Phase DL-1 (Volume 5 section 4.17.2): a re-run MERGES into the existing file and never overwrites a
            // reviewer's fields; an approved entry keeps the fingerprint the reviewer approved.
            List<DraftEntry> existing = DraftsFile.read(target);
            report = new SpringCapabilityProvider().draft(new ProviderInput(path), existing);
            if (!existing.isEmpty()) {
                System.out.println("Merged into " + existing.size() + " existing entr" + (existing.size() == 1 ? "y" : "ies")
                        + " (reviewer fields kept, " + report.drafts().stream().filter(DraftEntry::stale).count()
                        + " stale)");
            }
        } catch (ProviderException e) {
            System.err.println("Provider error: " + e.getMessage());
            return 3;
        }

        DraftsFile.write(target, report);

        Map<String, Integer> counts = report.counts();
        StringBuilder census = new StringBuilder();
        counts.forEach((label, n) -> census.append(census.length() == 0 ? "" : ", ").append(label).append(' ').append(n));
        System.out.println("Drafts: " + report.drafts().size() + " (" + census + ")");
        System.out.println("Wrote drafts to " + target);
        return 0;
    }
}
