package com.tibyaan.cli;

import com.tibyaan.cli.describe.AnthropicMessagesModel;
import com.tibyaan.cli.describe.Describer;
import com.tibyaan.cli.describe.DescriptionModel;
import com.tibyaan.cli.describe.OpenAiResponsesModel;
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
 * entry. Amendment DS-P (Volume 5 section 4.15.5.5) adds a second provider: {@code --provider} selects it
 * explicitly, never inferred, and each provider reads its own key variable only, {@code ANTHROPIC_API_KEY} or
 * {@code OPENAI_API_KEY}.
 */
@Command(name = "describe",
        description = "Draft a description for every pending entry of tibyan/drafts.json from the code the tool "
                + "fingerprints (phase Describe). The only command that calls a model; reads ANTHROPIC_API_KEY, or "
                + "OPENAI_API_KEY under --provider openai.")
final class DescribeCommand implements Callable<Integer> {

    static final String KEY_VARIABLE = "ANTHROPIC_API_KEY";
    static final String OPENAI_KEY_VARIABLE = "OPENAI_API_KEY";
    static final String DEFAULT_MODEL = "claude-sonnet-5";

    /** DS-P1: the provider is named, never inferred from which key is set or from the model id. */
    enum Provider {
        anthropic, openai
    }

    @Option(names = "--provider", paramLabel = "anthropic|openai",
            description = "The model provider (default: ${DEFAULT-VALUE}). Each reads its own key variable only.")
    Provider provider = Provider.anthropic;

    @Parameters(index = "0", paramLabel = "<path>", description = "Path to the system whose drafts file to describe.")
    Path path;

    @Option(names = "--model", paramLabel = "<id>",
            description = "Model id (default under anthropic: " + DEFAULT_MODEL + "; required under openai).")
    String model;

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
        String modelId = model;
        if (modelId == null || modelId.isBlank()) {
            if (provider == Provider.openai) {
                // DS-P6: no recorded OpenAI default; the run names the model before anything is called.
                System.err.println("--model is required under --provider openai: no default is recorded. Nothing was "
                        + "written.");
                return 3;
            }
            modelId = DEFAULT_MODEL;
        }
        DescriptionModel modelClient;
        if (dryRun) {
            modelClient = new DryRunModel(provider + ":" + modelId);
        } else {
            String variable = provider == Provider.openai ? OPENAI_KEY_VARIABLE : KEY_VARIABLE;
            String key = System.getenv(variable);
            if (key == null || key.isBlank()) {
                System.err.println(variable + " is not set; tibyan describe calls a model and needs it. Nothing was "
                        + "written. Use --dry-run to see the prompts without a key.");
                return 3;
            }
            modelClient = provider == Provider.openai
                    ? new OpenAiResponsesModel(key, modelId)
                    : new AnthropicMessagesModel(key, modelId);
        }
        System.out.println((dryRun ? "Dry run over " : "Describing ") + draftsFile + " with " + modelClient.id());
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
