package com.tibyaan.cli.describe;

import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.provider.spring.SpringCapabilityProvider;
import com.tibyaan.provider.spring.draft.DraftEntry;
import com.tibyaan.provider.spring.draft.DraftInput;
import com.tibyaan.provider.spring.draft.DraftLabel;
import com.tibyaan.provider.spring.draft.DraftsFile;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Phase Describe (Volume 5 section 4.15.5.2): drafts a description for the pending entries of a drafts file from
 * the slice the tool fingerprints, under the AT-4 boundary. Rules DS-1 to DS-5: only a pending entry with no
 * description, or with an ai-draft description older than its fingerprint, is described; the model sees the
 * fingerprinted slice, the label and the house rules; the words come back verbatim, bounded, and are never
 * printed on a tool surface; a human's words and an approved or rejected entry are never touched.
 */
public final class Describer {

    /** The bound DS-3 puts on a drafted description. */
    static final int MAX_LENGTH = 600;

    public record Options(Path path, Path draftsFile, int limit, Set<String> only, DraftLabel label, boolean dryRun) {
    }

    /** What a run did, for the summary and the tests: counts by outcome, refusals by name, prompts on a dry run. */
    public record Outcome(int described, Map<String, Integer> skipped, Map<String, String> refused,
                          List<String> prompts, boolean written) {
    }

    private final DescriptionModel model;
    private final SpringCapabilityProvider provider;
    private final PrintStream out;

    public Describer(DescriptionModel model, SpringCapabilityProvider provider, PrintStream out) {
        this.model = model;
        this.provider = provider;
        this.out = out;
    }

    public Outcome run(Options options) {
        List<DraftEntry> entries = DraftsFile.read(options.draftsFile());
        SpringCapabilityProvider.SliceReader slices = provider.sliceReader(new ProviderInput(options.path()));
        List<DraftEntry> result = new ArrayList<>(entries.size());
        Map<String, Integer> skipped = new LinkedHashMap<>();
        Map<String, String> refused = new LinkedHashMap<>();
        List<String> prompts = new ArrayList<>();
        int described = 0;
        for (DraftEntry entry : entries) {
            Optional<String> skip = skipReason(entry, options);
            if (skip.isPresent()) {
                skipped.merge(skip.get(), 1, Integer::sum);
                result.add(entry);
                continue;
            }
            if (described >= options.limit()) {
                skipped.merge("over the limit of " + options.limit(), 1, Integer::sum);
                result.add(entry);
                continue;
            }
            Optional<SpringCapabilityProvider.Slice> slice = slices.slice(entry.target());
            if (slice.isEmpty()) {
                refused.put(entry.name(), "the target '" + entry.target() + "' does not resolve to one declaration");
                result.add(entry);
                continue;
            }
            String prompt = prompt(entry, slice.get());
            if (options.dryRun()) {
                prompts.add(prompt);
                result.add(entry);
                described++;
                continue;
            }
            String words;
            try {
                words = model.describe(prompt).trim();
            } catch (DescriptionModel.ModelException e) {
                refused.put(entry.name(), e.getMessage());
                result.add(entry);
                continue;
            }
            Optional<String> malformed = malformed(words);
            if (malformed.isPresent()) {
                refused.put(entry.name(), malformed.get());
                result.add(entry);
                continue;
            }
            result.add(entry.described(words, model.id()));
            described++;
            out.println("  described " + entry.name() + " [" + entry.label() + "]");
        }
        boolean written = false;
        if (described > 0 && !options.dryRun()) {
            DraftsFile.write(options.draftsFile(), result);
            written = true;
        }
        return new Outcome(described, skipped, refused, prompts, written);
    }

    // DS-1: what is described, and why the rest is not.
    private static Optional<String> skipReason(DraftEntry entry, Options options) {
        if (!options.only().isEmpty() && !options.only().contains(entry.name())) {
            return Optional.of("not named by --only");
        }
        if (options.label() != null && entry.label() != options.label()) {
            return Optional.of("not labeled " + options.label());
        }
        if (entry.stale()) {
            return Optional.of("stale entry");
        }
        if (DraftEntry.APPROVED.equals(entry.review())) {
            return Optional.of("approved");
        }
        if (DraftEntry.REJECTED.equals(entry.review())) {
            return Optional.of("rejected");
        }
        boolean empty = entry.description() == null || entry.description().isBlank();
        if (empty) {
            return Optional.empty();
        }
        if (!DraftEntry.ORIGIN_AI_DRAFT.equals(entry.descriptionOrigin())) {
            return Optional.of("described by a person");
        }
        boolean outdated = entry.descriptionOutdated()
                || (entry.describedFrom() != null && !entry.describedFrom().equals(entry.fingerprint()));
        return outdated ? Optional.empty() : Optional.of("already described from the current fingerprint");
    }

    // DS-3: the words are taken verbatim or refused; the tool never edits, truncates or reflows them.
    private static Optional<String> malformed(String words) {
        if (words.isEmpty()) {
            return Optional.of("the model returned no words");
        }
        if (words.length() > MAX_LENGTH) {
            return Optional.of("the model returned " + words.length() + " characters; the bound is " + MAX_LENGTH);
        }
        if (words.indexOf('\n') >= 0 || words.indexOf('\r') >= 0) {
            return Optional.of("the model returned more than one line");
        }
        return Optional.empty();
    }

    // DS-2: the slice the tool fingerprints, the label and its reason, and the house rules the first eleven
    // approved descriptions followed. The model is asked for the description and nothing else.
    static String prompt(DraftEntry entry, SpringCapabilityProvider.Slice slice) {
        StringBuilder p = new StringBuilder();
        p.append("You are drafting the description of one tool for a human reviewer. The tool invokes the Java method ")
                .append(entry.target()).append(" directly.\n")
                .append("A static analysis labeled it ").append(entry.label()).append(": ").append(entry.reason()).append("\n")
                .append("Its return type is ").append(entry.returnType()).append(". Its inputs: ");
        if (entry.inputs().isEmpty()) {
            p.append("none");
        } else {
            for (int i = 0; i < entry.inputs().size(); i++) {
                DraftInput input = entry.inputs().get(i);
                p.append(i > 0 ? ", " : "").append(input.name()).append(" (").append(input.javaType()).append(")");
            }
        }
        p.append(".\n\nRules for the description:\n")
                .append("1. One paragraph of plain sentences, at most 600 characters, no line breaks, no markdown, no code.\n")
                .append("2. Say what the method returns or does, what narrows or filters it, what it refuses and when, ")
                .append("what inputs it ignores, and what side effects it has (writes, messages, events, remote calls).\n")
                .append("3. Name only what the code below shows. Do not guess, do not describe code style, do not mention ")
                .append("that you are a model, and do not address the reviewer.\n")
                .append("4. Default values and hardcoded filters are facts worth stating.\n\n")
                .append("The code the tool read (the same slice it fingerprints):\n\n");
        slice.members().forEach((label, source) -> p.append("--- ").append(label).append(" ---\n").append(source).append("\n\n"));
        p.append("Reply with the description only.");
        return p.toString();
    }
}
