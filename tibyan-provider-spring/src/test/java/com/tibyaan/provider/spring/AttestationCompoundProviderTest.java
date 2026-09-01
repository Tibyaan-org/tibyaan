package com.tibyaan.provider.spring;

import com.tibyaan.core.ir.Attestation;
import com.tibyaan.core.ir.AttestationCategory;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.Type;
import com.tibyaan.core.semantic.SemanticValidator;
import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.provider.spring.attest.IntakeRefusal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The COMPOUND category, `both` (Volume 5 section 4.15.3): one entry, one minted name, one capability,
 * whose {@code parameterMeanings} fills the predicate hole and whose {@code resultSemantic} plus subject
 * entity fills the shape hole.
 *
 * <p>The tests are mostly about the sentence the design turns on: THE CATEGORY MUST MATCH THE HOLES THE
 * TOOL FOUND. That is what stops `both` from becoming a catch-all that skips the narrower categories'
 * checks, and it is enforced in BOTH directions: a target with no predicate hole is a shape target, and a
 * target with no shape hole is a predicate target. Neither may be attested as compound.
 */
class AttestationCompoundProviderTest {

    private static final Path FIXTURE =
            Path.of("src", "test", "resources", "fixtures", "attest-both", "AttestBoth.java");
    private static final String PKG = "com.example.attestboth.TicketServiceImpl#";
    private static final String COMPOUND_TARGET = PKG + "searchSummaries(String)";
    private static final String SHAPE_ONLY_TARGET = PKG + "allSummaries()";
    private static final String PREDICATE_ONLY_TARGET = PKG + "byOwner(String)";

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();
    private final SemanticValidator validator = new SemanticValidator();

    // ---------- the positive: one entry closes a two-hole target ----------

    @Test
    void aTwoHoleTargetIsClosedByOneCompoundEntry(@TempDir Path root) throws IOException {
        copyFixture(root);
        String fingerprint = fingerprint(root, COMPOUND_TARGET);
        writeAttestations(root, "[" + entry(COMPOUND_TARGET, "both", "searchTicketSummaries",
                "Returns a summary row per ticket owned by the given owner.",
                "\"owner\":\"Matches tickets whose owner is this login, exactly.\"", "Ticket",
                fingerprint) + "]");

        ScanReport report = provider.scan(new ProviderInput(root));

        assertThat(report.attestationRefusals()).isEmpty();
        assertThat(report.attestedCount()).isEqualTo(1);

        Capability capability = attested(report, "attested_searchTicketSummaries");
        assertThat(capability.binding().value()).isEqualTo("TicketServiceImpl#searchSummaries");
        // The shape half: the output is the ASSERTED subject, because structure could not tie it.
        assertThat(capability.output()).isEqualTo(Type.entityRef("Ticket"));

        Attestation attestation = capability.attestation();
        assertThat(attestation.category()).isEqualTo(AttestationCategory.BOTH);
        assertThat(attestation.subjectEntity()).isEqualTo("Ticket");
        // The predicate half: the domain filter carries its meaning sentence, character-exact.
        assertThat(attestation.parameterMeanings())
                .containsEntry("owner", "Matches tickets whose owner is this login, exactly.");
        assertThat(validator.validate(report.model()).isValid()).isTrue();
    }

    @Test
    void oneEntryMintsOneCapability_theCompoundTargetIsNotSplitInTwo(@TempDir Path root)
            throws IOException {
        // The reason the compound category exists at all: a two-hole target must not need two entries,
        // because two entries against one declaration is exactly what condition 1 forbids.
        copyFixture(root);
        String fingerprint = fingerprint(root, COMPOUND_TARGET);
        writeAttestations(root, "[" + entry(COMPOUND_TARGET, "both", "searchTicketSummaries",
                "Returns a summary row per ticket owned by the given owner.",
                "\"owner\":\"Matches the owner.\"", "Ticket", fingerprint) + "]");

        ScanReport report = provider.scan(new ProviderInput(root));

        assertThat(report.model().capabilities()).hasSize(1);
        assertThat(report.discoveredCount()).isZero();
        assertThat(report.attestedCount()).isEqualTo(1);
    }

    // ---------- the category must match the holes, in both directions ----------

    @Test
    void aCompoundEntryOnATargetWithNoPredicateHoleRefusesAndNamesShape(@TempDir Path root)
            throws IOException {
        copyFixture(root);
        String fingerprint = fingerprint(root, SHAPE_ONLY_TARGET);
        writeAttestations(root, "[" + entry(SHAPE_ONLY_TARGET, "both", "listTicketSummaries",
                "Returns a summary row per ticket.", "", "Ticket", fingerprint) + "]");

        IntakeRefusal refusal = onlyRefusal(root);
        assertThat(refusal.cause()).isEqualTo(IntakeRefusal.Cause.CATEGORY_MISMATCH);
        assertThat(refusal.detail())
                .contains("no predicate hole")
                .contains("'shape'");
    }

    @Test
    void aCompoundEntryThatContradictsTheProvenTieRefuses(@TempDir Path root) throws IOException {
        // The shape-hole half of the match is NOT separately enforced, because the E6a transparency tie
        // can resolve a wrapper's subject even when its element type is not an entity, so "the element
        // type is unresolvable" is not cleanly decidable. NON-CONTRADICTION is the guard that matters
        // instead, and it applies to a compound entry exactly as it does to a shape one: an assertion
        // that disagrees with what the single repository access provably reads refuses.
        copyFixture(root);
        String fingerprint = fingerprint(root, PREDICATE_ONLY_TARGET);
        writeAttestations(root, "[" + entry(PREDICATE_ONLY_TARGET, "both", "searchTicketsByOwner",
                "Returns the summaries owned by this owner.", "\"owner\":\"Matches the owner.\"",
                "TicketSummaryHolder", fingerprint) + "]");

        IntakeRefusal refusal = onlyRefusal(root);
        assertThat(refusal.cause()).isEqualTo(IntakeRefusal.Cause.STRUCTURAL_FRAME);
        assertThat(refusal.detail()).contains("not an entity in the model");
    }

    @Test
    void aShapeEntryOnTheTwoHoleTargetStillRefusesAndNamesBoth(@TempDir Path root) throws IOException {
        // The mirror of the first refusal, and the one AT-2 already enforced: a shape entry never carries
        // a predicate assertion. It now names a category that exists.
        copyFixture(root);
        String fingerprint = fingerprint(root, COMPOUND_TARGET);
        writeAttestations(root, "[" + entry(COMPOUND_TARGET, "shape", "searchTicketSummaries",
                "Returns a summary row per ticket.", "\"owner\":\"Matches the owner.\"", "Ticket",
                fingerprint) + "]");

        IntakeRefusal refusal = onlyRefusal(root);
        assertThat(refusal.cause()).isEqualTo(IntakeRefusal.Cause.CATEGORY_MISMATCH);
        assertThat(refusal.detail()).contains("PREDICATE claim").contains("'both'");
    }

    @Test
    void aCompoundEntryStillObeysEveryShapeCheck_anUnmodelledSubjectRefuses(@TempDir Path root)
            throws IOException {
        // `both` adds a hole to fill, it never removes a check: the shape frame runs unchanged, so an
        // asserted subject that is not in the model refuses exactly as it does for a shape entry.
        copyFixture(root);
        String fingerprint = fingerprint(root, COMPOUND_TARGET);
        writeAttestations(root, "[" + entry(COMPOUND_TARGET, "both", "searchTicketSummaries",
                "Returns a summary row per ticket.", "\"owner\":\"Matches the owner.\"", "Warehouse",
                fingerprint) + "]");

        IntakeRefusal refusal = onlyRefusal(root);
        assertThat(refusal.cause()).isEqualTo(IntakeRefusal.Cause.STRUCTURAL_FRAME);
        assertThat(refusal.detail()).contains("Warehouse").contains("not an entity in the model");
    }

    @Test
    void aCompoundEntryCarryingBoundaryFieldsRefuses(@TempDir Path root) throws IOException {
        // `both` is a DATA category, so core rule 11's class separation applies to it unchanged.
        copyFixture(root);
        String fingerprint = fingerprint(root, COMPOUND_TARGET);
        String withOwner = entry(COMPOUND_TARGET, "both", "searchTicketSummaries",
                "Returns a summary row per ticket.", "\"owner\":\"Matches the owner.\"", "Ticket",
                fingerprint).replace("\"provenance\"", "\"securityOwner\":\"s.owner\",\"provenance\"");
        writeAttestations(root, "[" + withOwner + "]");

        IntakeRefusal refusal = onlyRefusal(root);
        assertThat(refusal.cause()).isEqualTo(IntakeRefusal.Cause.CATEGORY_MISMATCH);
        assertThat(refusal.detail()).contains("boundary fields");
    }

    // ---------- helpers ----------

    private IntakeRefusal onlyRefusal(Path root) {
        ScanReport report = provider.scan(new ProviderInput(root));
        assertThat(report.attestedCount()).as("a refused entry produces no capability").isZero();
        assertThat(report.attestationRefusals()).hasSize(1);
        return report.attestationRefusals().get(0);
    }

    private static Capability attested(ScanReport report, String name) {
        return report.model().capabilities().stream()
                .filter(capability -> capability.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no capability named " + name
                        + "; capabilities were " + report.model().capabilities().stream()
                        .map(Capability::name).toList()));
    }

    private String fingerprint(Path root, String target) {
        Optional<String> fingerprint = provider.attestationFingerprint(new ProviderInput(root), target);
        assertThat(fingerprint).as("the target must resolve to compute a fingerprint").isPresent();
        return fingerprint.get();
    }

    private void copyFixture(Path root) throws IOException {
        Path destination = root.resolve("AttestBoth.java");
        if (!Files.exists(destination)) {
            Files.copy(FIXTURE, destination);
        }
    }

    private static void writeAttestations(Path root, String json) throws IOException {
        Path dir = root.resolve("tibyan");
        Files.createDirectories(dir);
        Files.write(dir.resolve("attestations.json"), json.getBytes(StandardCharsets.UTF_8));
    }

    private static String entry(String target, String category, String name, String resultSemantic,
                                String parameters, String subjectEntity, String fingerprint) {
        return "{"
                + "\"target\":\"" + target + "\","
                + "\"category\":\"" + category + "\","
                + "\"name\":\"" + name + "\","
                + "\"resultSemantic\":\"" + resultSemantic + "\","
                + "\"parameters\":{" + parameters + "},"
                + "\"subjectEntity\":\"" + subjectEntity + "\","
                + "\"provenance\":{\"assertedBy\":\"a.elseginy\",\"origin\":\"human-authored\","
                + "\"date\":\"2026-09-01\",\"fingerprint\":\"" + fingerprint + "\"}}";
    }
}
