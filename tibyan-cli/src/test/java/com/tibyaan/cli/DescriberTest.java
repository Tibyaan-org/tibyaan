package com.tibyaan.cli;

import com.tibyaan.cli.describe.Describer;
import com.tibyaan.cli.describe.DescriptionModel;
import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.provider.spring.SpringCapabilityProvider;
import com.tibyaan.provider.spring.draft.DraftEntry;
import com.tibyaan.provider.spring.draft.DraftLabel;
import com.tibyaan.provider.spring.draft.DraftReport;
import com.tibyaan.provider.spring.draft.DraftsFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Phase Describe (Volume 5 section 4.15.5.2, fixtures DS-1 to DS-6), against a fake model over a copy of the
 * example app: a pending empty entry is described with its provenance recorded; an approved entry and a human's
 * words are untouched; a missing key refuses and writes nothing; a fingerprint change marks the draft stale on
 * the next merge and describe replaces it; a dry run prints the prompt and writes nothing; the limit holds.
 */
class DescriberTest {

    private static final Path EXAMPLE_APP = Path.of("..", "examples", "legacy-spring-app");
    private static final String RENAME = "ProductService_rename";
    private static final String FIND = "ProductService_findById";

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();
    private final PrintStream quiet = new PrintStream(new ByteArrayOutputStream());

    /** A fake that answers a fixed sentence and remembers the prompts it saw. */
    private static final class FakeModel implements DescriptionModel {
        final List<String> prompts = new ArrayList<>();
        String answer = "Renames the product with the given id and returns it.";
        String failure;

        @Override
        public String id() {
            return "fake-model-1";
        }

        @Override
        public String describe(String prompt) throws ModelException {
            prompts.add(prompt);
            if (failure != null) {
                throw new ModelException(failure);
            }
            return answer;
        }
    }

    @Test
    void ds1_aPendingEmptyEntryIsDescribedWithItsProvenanceRecorded(@TempDir Path root) throws IOException {
        Path drafts = draftedCopy(root);
        FakeModel model = new FakeModel();

        Describer.Outcome outcome = new Describer(model, provider, quiet)
                .run(new Describer.Options(root, drafts, 50, Set.of(RENAME), null, false));

        assertThat(outcome.described()).isEqualTo(1);
        assertThat(outcome.written()).isTrue();
        DraftEntry rename = byName(DraftsFile.read(drafts), RENAME);
        assertThat(rename.description()).isEqualTo(model.answer);
        assertThat(rename.descriptionOrigin()).isEqualTo(DraftEntry.ORIGIN_AI_DRAFT);
        assertThat(rename.describedBy()).isEqualTo("fake-model-1");
        assertThat(rename.describedFrom()).isEqualTo(rename.fingerprint());
        assertThat(rename.review()).isEqualTo(DraftEntry.PENDING);
        // DS-2: the model saw the fingerprinted slice, the label and the house rules, and nothing else.
        assertThat(model.prompts).hasSize(1);
        assertThat(model.prompts.get(0)).contains("public Product rename(").contains("labeled WRITE")
                .contains("at most 600 characters").doesNotContain("findById(Long");
        // The other pending entry was skipped by name, and stays untouched.
        assertThat(byName(DraftsFile.read(drafts), FIND).description()).isEmpty();
        // The file still reads back as the same entries.
        assertThat(DraftsFile.read(drafts)).hasSize(2);
    }

    @Test
    void ds2_anApprovedEntryAndAHumansWordsAreNeverTouched(@TempDir Path root) throws IOException {
        Path drafts = draftedCopy(root);
        List<DraftEntry> edited = new ArrayList<>();
        for (DraftEntry d : DraftsFile.read(drafts)) {
            edited.add(d.name().equals(RENAME)
                    ? new DraftEntry(d.name(), d.target(), d.binding(), d.label(), d.reason(), d.capability(),
                            d.returnType(), d.inputs(), "A person wrote this.", DraftEntry.APPROVED, "a.reviewer",
                            "2026-09-24", DraftEntry.ORIGIN_HUMAN, d.fingerprint(), false, null)
                    : new DraftEntry(d.name(), d.target(), d.binding(), d.label(), d.reason(), d.capability(),
                            d.returnType(), d.inputs(), "Reads a product.", DraftEntry.PENDING, null, null,
                            DraftEntry.ORIGIN_HUMAN, d.fingerprint(), false, null));
        }
        DraftsFile.write(drafts, edited);
        FakeModel model = new FakeModel();

        Describer.Outcome outcome = new Describer(model, provider, quiet)
                .run(new Describer.Options(root, drafts, 50, Set.of(), null, false));

        assertThat(outcome.described()).isZero();
        assertThat(outcome.written()).isFalse();
        assertThat(outcome.skipped()).containsEntry("approved", 1).containsEntry("described by a person", 1);
        assertThat(model.prompts).isEmpty();
        assertThat(DraftsFile.read(drafts)).isEqualTo(edited);
    }

    @Test
    void ds3_aMissingKeyRefusesAndWritesNothing(@TempDir Path root) throws IOException {
        assumeTrue(System.getenv(DescribeCommand.KEY_VARIABLE) == null, "the key is set in this environment");
        Path drafts = draftedCopy(root);
        String before = Files.readString(drafts);
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream original = System.err;
        int code;
        try {
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            code = new CommandLine(new TibyanCli()).execute("describe", root.toString());
        } finally {
            System.setErr(original);
        }
        assertThat(code).isEqualTo(3);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains(DescribeCommand.KEY_VARIABLE).contains("Nothing was written");
        assertThat(Files.readString(drafts)).isEqualTo(before);
    }

    @Test
    void ds4_aFingerprintChangeMarksTheDraftStaleOnMergeAndDescribeReplacesIt(@TempDir Path root) throws IOException {
        Path drafts = draftedCopy(root);
        FakeModel model = new FakeModel();
        new Describer(model, provider, quiet).run(new Describer.Options(root, drafts, 50, Set.of(RENAME), null, false));
        String firstFingerprint = byName(DraftsFile.read(drafts), RENAME).fingerprint();

        // The code moves: the rename gains a statement, so its fingerprint changes.
        Path service = find(root, "ProductService.java");
        String source = Files.readString(service, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(source).contains("public Product rename(");
        int open = source.indexOf('{', source.indexOf("public Product rename("));
        Files.writeString(service, source.substring(0, open + 1) + "\n        java.util.Objects.requireNonNull(id);"
                + source.substring(open + 1), StandardCharsets.UTF_8);
        DraftReport merged = provider.draft(new ProviderInput(root), DraftsFile.read(drafts));
        DraftsFile.write(drafts, merged);
        DraftEntry afterMerge = byName(DraftsFile.read(drafts), RENAME);
        assertThat(afterMerge.fingerprint()).isNotEqualTo(firstFingerprint);
        assertThat(afterMerge.description()).isEqualTo(model.answer);
        assertThat(afterMerge.descriptionOutdated()).as("kept and marked stale, never deleted").isTrue();
        assertThat(afterMerge.describedFrom()).isEqualTo(firstFingerprint);

        // A second run replaces the stale draft and only that.
        model.answer = "Renames the product with the given non-null id and returns it.";
        Describer.Outcome second = new Describer(model, provider, quiet)
                .run(new Describer.Options(root, drafts, 50, Set.of(RENAME), null, false));
        assertThat(second.described()).isEqualTo(1);
        DraftEntry replaced = byName(DraftsFile.read(drafts), RENAME);
        assertThat(replaced.description()).isEqualTo(model.answer);
        assertThat(replaced.describedFrom()).isEqualTo(replaced.fingerprint());
        assertThat(replaced.descriptionOutdated()).isFalse();
    }

    @Test
    void ds5_aDryRunPrintsThePromptAndWritesNothing(@TempDir Path root) throws IOException {
        Path drafts = draftedCopy(root);
        String before = Files.readString(drafts);
        FakeModel model = new FakeModel();

        Describer.Outcome outcome = new Describer(model, provider, quiet)
                .run(new Describer.Options(root, drafts, 50, Set.of(), DraftLabel.WRITE, true));

        assertThat(outcome.described()).isEqualTo(1);
        assertThat(outcome.prompts()).hasSize(1);
        assertThat(outcome.prompts().get(0)).contains("public Product rename(");
        assertThat(outcome.written()).isFalse();
        assertThat(model.prompts).as("a dry run never calls the model").isEmpty();
        assertThat(Files.readString(drafts)).isEqualTo(before);
    }

    @Test
    void ds6_theLimitHoldsAndARefusalOrMalformedAnswerNamesItsReason(@TempDir Path root) throws IOException {
        Path drafts = draftedCopy(root);
        FakeModel model = new FakeModel();

        Describer.Outcome limited = new Describer(model, provider, quiet)
                .run(new Describer.Options(root, drafts, 1, Set.of(), null, false));
        assertThat(limited.described()).isEqualTo(1);
        assertThat(limited.skipped()).containsEntry("over the limit of 1", 1);

        Path again = draftedCopy(root.resolve("again"));
        model.answer = "x".repeat(601);
        Describer.Outcome tooLong = new Describer(model, provider, quiet)
                .run(new Describer.Options(root.resolve("again"), again, 50, Set.of(RENAME), null, false));
        assertThat(tooLong.described()).isZero();
        assertThat(tooLong.refused().get(RENAME)).contains("601 characters");
        assertThat(tooLong.written()).isFalse();

        model.answer = "line one\nline two";
        assertThat(new Describer(model, provider, quiet)
                .run(new Describer.Options(root.resolve("again"), again, 50, Set.of(RENAME), null, false))
                .refused().get(RENAME)).contains("more than one line");

        model.failure = "HTTP 401 from the endpoint";
        assertThat(new Describer(model, provider, quiet)
                .run(new Describer.Options(root.resolve("again"), again, 50, Set.of(RENAME), null, false))
                .refused().get(RENAME)).contains("HTTP 401");
        assertThat(byName(DraftsFile.read(again), RENAME).description()).isEmpty();
    }

    // A copy of the example app under root, with a fresh drafts file, so the describe run can edit it.
    private Path draftedCopy(Path root) throws IOException {
        Path sources = EXAMPLE_APP.resolve("src").resolve("main").resolve("java");
        try (Stream<Path> files = Files.walk(sources)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path target = root.resolve(sources.relativize(file).toString());
                Files.createDirectories(target.getParent());
                Files.copy(file, target);
            }
        }
        DraftReport report = provider.draft(new ProviderInput(root));
        Path drafts = DraftsFile.defaultLocation(root);
        DraftsFile.write(drafts, report);
        return drafts;
    }

    private static Path find(Path root, String fileName) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.getFileName().toString().equals(fileName)).findFirst().orElseThrow();
        }
    }

    private static DraftEntry byName(List<DraftEntry> entries, String name) {
        return entries.stream().filter(e -> e.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no entry " + name + " among "
                        + entries.stream().map(DraftEntry::name).toList()));
    }
}
