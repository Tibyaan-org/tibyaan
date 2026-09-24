package com.tibyaan.cli;

import com.tibyaan.cli.describe.AnthropicMessagesModel;
import com.tibyaan.cli.describe.Describer;
import com.tibyaan.cli.describe.DescriptionModel;
import com.tibyaan.provider.spring.ProviderException;
import com.tibyaan.provider.spring.SpringCapabilityProvider;
import com.tibyaan.provider.spring.draft.DraftLabel;
import com.tibyaan.provider.spring.draft.DraftsFile;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Phase Describe (Volume 5 section 4.15.5.2; Volume 11 section 3.6; Volume 16 section 7): the one command that
 * makes a network call. It drafts descriptions for pending entries of {@code tibyan/drafts.json} and writes them
 * back under origin {@code ai-draft}; nothing it writes reaches a tool surface until a named human approves the
 * entry. The key is read from {@code ANTHROPIC_API_KEY} only.
 */
@Command(name = "describe",
        description = "Draft a description for every pending entry of tibyan/drafts.json from the code the tool "
                + "fingerprints (phase Describe). The only command that calls a model; reads ANTHROPIC_API_KEY.")
final class DescribeCommand implements Callable<Integer> {

    static final String KEY_VARIABLE = "ANTHROPIC_API_KEY";
    static final String DEFAULT_MODEL = "claude-sonnet-5";

    @Parameters(index = "0", paramLabel = "<path>", description = "Path to the system whose drafts file to describe.")
    Path path;

    @Option(names = "--model", paramLabel = "<id>", description = "Model id (default: ${DEFAULT-VALUE}).")
    String model = DEFAULT_MODEL;

    @Option(names = "--limit", paramLabel = "<n>", description = "At most this many entries per run (default: ${DEFAULT-VALUE}).")
    int limit = 50;

    @Option(names = "--only", paramLabel = "<name>", split = ",", description = "Describe only these entry names.")
    List<String> only = List.of();

    @Option(names = "--label", paramLabel = "READ|WRITE|UNKNOWN", description = "Describe only entries with this label.")
    DraftLabel label;

    @Option(names = "--dry-run", description = "Print every prompt and write nothing; needs no key.")
    boolean dryRun;

    @Option(names = "--drafts", paramLabel = "<drafts.json>",
            description = "The drafts file (default: <path>/tibyan/drafts.json).")
    Path drafts;

    @Override
    public Integer call() {
        Path draftsFile = drafts != null ? drafts : DraftsFile.defaultLocation(path);
        if (!Files.isRegularFile(draftsFile)) {
            System.err.println("No drafts file at " + draftsFile + "; run tibyan draft first.");
            return 3;
        }
        DescriptionModel modelClient;
        if (dryRun) {
            modelClient = new DryRunModel(model);
        } else {
            String key = System.getenv(KEY_VARIABLE);
            if (key == null || key.isBlank()) {
                System.err.println(KEY_VARIABLE + " is not set; tibyan describe calls a model and needs it. Nothing was "
                        + "written. Use --dry-run to see the prompts without a key.");
                return 3;
            }
            modelClient = new AnthropicMessagesModel(key, model);
        }
        System.out.println((dryRun ? "Dry run over " : "Describing ") + draftsFile + " with " + model);
        Describer.Outcome outcome;
        try {
            outcome = new Describer(modelClient, new SpringCapabilityProvider(), System.out)
                    .run(new Describer.Options(path, draftsFile, limit, Set.copyOf(only), label, dryRun));
        } catch (ProviderException e) {
            System.err.println("Provider error: " + e.getMessage());
            return 3;
        }
        if (dryRun) {
            outcome.prompts().forEach(prompt -> System.out.println("==== prompt ====\n" + prompt + "\n"));
        }
        StringBuilder skipped = new StringBuilder();
        outcome.skipped().forEach((reason, n) -> skipped.append(skipped.length() == 0 ? "" : ", ").append(n).append(' ').append(reason));
        System.out.println((dryRun ? "Would describe " : "Described ") + outcome.described() + ", skipped "
                + outcome.skipped().values().stream().mapToInt(Integer::intValue).sum()
                + (skipped.length() == 0 ? "" : " (" + skipped + ")") + ", refused " + outcome.refused().size());
        outcome.refused().forEach((name, reason) -> System.out.println("  ! " + name + ": " + reason));
        System.out.println(outcome.written() ? "Wrote " + draftsFile : "Nothing written");
        return 0;
    }

    /** A dry run never calls anything; the prompts are printed instead. */
    private static final class DryRunModel implements DescriptionModel {
        private final String id;

        DryRunModel(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String describe(String prompt) {
            throw new IllegalStateException("a dry run never calls the model");
        }
    }
}
