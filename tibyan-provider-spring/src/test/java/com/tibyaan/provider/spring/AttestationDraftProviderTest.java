package com.tibyaan.provider.spring;

import com.tibyaan.core.ir.AttestationOrigin;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.provider.spring.SpringCapabilityProvider.ConfirmOutcome;
import com.tibyaan.provider.spring.attest.DraftProposal;
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
 * Phase Attestation AT-4, the draft lifecycle (Volume 5 section 4.15.5). Fixture ATT-9.
 *
 * <p>AI MAY PROPOSE. ONLY A HUMAN MAY CONFIRM. ONLY CONFIRMED CLAIMS SHIP. These tests are that sentence
 * made checkable: a draft reaches no output and is reported as its own third thing (neither attested nor
 * refused); confirmation is per entry, names the confirmer, and rewrites only the provenance; and a
 * confirmed entry then travels the ordinary intake path with its origin visible on every surface.
 */
class AttestationDraftProviderTest {

    private static final Path FIXTURE =
            Path.of("src", "test", "resources", "fixtures", "attest-predicate", "AttestPredicate.java");
    private static final String FILTER_TARGET =
            "com.example.attest.TenantServiceImpl#getAllByFilter(String,Integer)";
    private static final String GUARD_TARGET =
            "com.example.attest.TenantServiceImpl#getActiveTenant(Long)";

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();

    // ---------- ATT-9: a draft never reaches any output ----------

    @Test
    void att9_aDraftProducesNoCapabilityAndIsReportedAsItsOwnThing(@TempDir Path root) throws IOException {
        copyFixture(root);
        writeAttestations(root, "[" + entry(FILTER_TARGET, "searchTenants", DraftProposal.DRAFT_ORIGIN,
                "the.assist", fingerprint(root, FILTER_TARGET)) + "]");

        ScanReport report = provider.scan(new ProviderInput(root));

        // It ships nothing: no capability, no attested count, nothing in the model at all.
        assertThat(report.attestedCount()).isZero();
        assertThat(report.model().capabilities()).noneMatch(Capability::hasAttestation);

        // And it is NOT a refusal, because nothing is wrong with it. Calling a proposal a failure would
        // train a team to ignore the one queue this phase exists to keep honest.
        assertThat(report.attestationRefusals()).isEmpty();
        assertThat(report.attestationDrafts()).hasSize(1);
        DraftProposal draft = report.attestationDrafts().get(0);
        assertThat(draft.target()).isEqualTo(FILTER_TARGET);
        assertThat(draft.name()).isEqualTo("searchTenants");
        assertThat(draft.category()).isEqualTo("predicate");
    }

    @Test
    void anUnknownOriginIsStillARefusal_notADraft(@TempDir Path root) throws IOException {
        // The draft token is reserved and exact. Anything else in the origin field is a defect in the
        // entry, and it must keep reporting as one rather than being quietly parked in the review queue.
        copyFixture(root);
        writeAttestations(root, "[" + entry(FILTER_TARGET, "searchTenants", "ai-generated", "the.assist",
                fingerprint(root, FILTER_TARGET)) + "]");

        ScanReport report = provider.scan(new ProviderInput(root));

        assertThat(report.attestationDrafts()).isEmpty();
        assertThat(report.attestationRefusals()).hasSize(1);
        assertThat(report.attestationRefusals().get(0).cause())
                .isEqualTo(IntakeRefusal.Cause.PROVENANCE);
    }

    // ---------- confirmation is a per-entry human act ----------

    @Test
    void confirmingADraftRewritesItsOriginAndNamesTheConfirmer(@TempDir Path root) throws IOException {
        copyFixture(root);
        writeAttestations(root, "[" + entry(FILTER_TARGET, "searchTenants", DraftProposal.DRAFT_ORIGIN,
                "the.assist", fingerprint(root, FILTER_TARGET)) + "]");

        ConfirmOutcome outcome = provider.confirmDraft(new ProviderInput(root), FILTER_TARGET,
                "a.elseginy");

        assertThat(outcome.confirmed()).isTrue();
        String file = Files.readString(root.resolve("tibyan/attestations.json"), StandardCharsets.UTF_8);
        assertThat(file).contains(AttestationOrigin.AI_DRAFTED_HUMAN_CONFIRMED.token());
        assertThat(file).doesNotContain("\"" + DraftProposal.DRAFT_ORIGIN + "\"");
        assertThat(file).contains("a.elseginy");

        // And now it ships, through the ordinary intake path, with its origin visible.
        ScanReport report = provider.scan(new ProviderInput(root));
        assertThat(report.attestationDrafts()).isEmpty();
        assertThat(report.attestedCount()).isEqualTo(1);
        Capability capability = report.model().capabilities().stream()
                .filter(Capability::hasAttestation).findFirst().orElseThrow();
        assertThat(capability.attestation().origin())
                .isEqualTo(AttestationOrigin.AI_DRAFTED_HUMAN_CONFIRMED);
        assertThat(capability.attestation().assertedBy()).isEqualTo("a.elseginy");
    }

    @Test
    void confirmationIsPerEntry_theOtherDraftIsUntouched(@TempDir Path root) throws IOException {
        // There is no confirm-all, and this is what that means in practice: confirming one target leaves
        // every other draft exactly where it was, still awaiting its own human.
        copyFixture(root);
        writeAttestations(root, "["
                + entry(FILTER_TARGET, "searchTenants", DraftProposal.DRAFT_ORIGIN, "the.assist",
                        fingerprint(root, FILTER_TARGET))
                + "," + guardEntry(fingerprint(root, GUARD_TARGET)) + "]");

        provider.confirmDraft(new ProviderInput(root), FILTER_TARGET, "a.elseginy");

        ScanReport report = provider.scan(new ProviderInput(root));
        assertThat(report.attestedCount()).isEqualTo(1);
        assertThat(report.attestationDrafts()).hasSize(1);
        assertThat(report.attestationDrafts().get(0).target()).isEqualTo(GUARD_TARGET);
    }

    @Test
    void anAnonymousConfirmationRefuses(@TempDir Path root) throws IOException {
        copyFixture(root);
        writeAttestations(root, "[" + entry(FILTER_TARGET, "searchTenants", DraftProposal.DRAFT_ORIGIN,
                "the.assist", fingerprint(root, FILTER_TARGET)) + "]");

        ConfirmOutcome outcome = provider.confirmDraft(new ProviderInput(root), FILTER_TARGET, "  ");

        assertThat(outcome.confirmed()).isFalse();
        assertThat(outcome.reason()).contains("no confirmer named");
        assertThat(provider.scan(new ProviderInput(root)).attestationDrafts()).hasSize(1);
    }

    @Test
    void confirmingSomethingThatIsNotADraftRefuses_relabellingWouldBeAProvenanceLie(@TempDir Path root)
            throws IOException {
        // A hand-authored claim confirmed as a draft would be relabelled AI-drafted, which is a lie in
        // the one field whose entire job is to be true about how the words were produced.
        copyFixture(root);
        writeAttestations(root, "[" + entry(FILTER_TARGET, "searchTenants", "human-authored", "a.elseginy",
                fingerprint(root, FILTER_TARGET)) + "]");

        ConfirmOutcome outcome = provider.confirmDraft(new ProviderInput(root), FILTER_TARGET, "someone");

        assertThat(outcome.confirmed()).isFalse();
        assertThat(outcome.reason()).contains("provenance lie");

        ScanReport report = provider.scan(new ProviderInput(root));
        assertThat(report.model().capabilities().stream().filter(Capability::hasAttestation).findFirst()
                .orElseThrow().attestation().origin()).isEqualTo(AttestationOrigin.HUMAN_AUTHORED);
    }

    @Test
    void confirmingAnUnknownTargetRefuses(@TempDir Path root) throws IOException {
        copyFixture(root);
        writeAttestations(root, "[" + entry(FILTER_TARGET, "searchTenants", DraftProposal.DRAFT_ORIGIN,
                "the.assist", fingerprint(root, FILTER_TARGET)) + "]");

        ConfirmOutcome outcome = provider.confirmDraft(new ProviderInput(root),
                "com.example.attest.Nope#missing(Long)", "a.elseginy");

        assertThat(outcome.confirmed()).isFalse();
        assertThat(outcome.reason()).contains("no entry targets");
    }

    // ---------- helpers ----------

    private String fingerprint(Path root, String target) {
        Optional<String> fingerprint = provider.attestationFingerprint(new ProviderInput(root), target);
        assertThat(fingerprint).as("the target must resolve to compute a fingerprint").isPresent();
        return fingerprint.get();
    }

    private void copyFixture(Path root) throws IOException {
        Path destination = root.resolve("AttestPredicate.java");
        if (!Files.exists(destination)) {
            Files.copy(FIXTURE, destination);
        }
    }

    private static void writeAttestations(Path root, String json) throws IOException {
        Path dir = root.resolve("tibyan");
        Files.createDirectories(dir);
        Files.write(dir.resolve("attestations.json"), json.getBytes(StandardCharsets.UTF_8));
    }

    private static String entry(String target, String name, String origin, String assertedBy,
                                String fingerprint) {
        return "{"
                + "\"target\":\"" + target + "\","
                + "\"category\":\"predicate\","
                + "\"name\":\"" + name + "\","
                + "\"resultSemantic\":\"Returns the tenants matching the filters.\","
                + "\"parameters\":{\"fullName\":\"Matches the full name.\",\"pageNum\":\"paging\"},"
                + "\"provenance\":{\"assertedBy\":\"" + assertedBy + "\",\"origin\":\"" + origin + "\","
                + "\"date\":\"2026-09-01\",\"fingerprint\":\"" + fingerprint + "\"}}";
    }

    private static String guardEntry(String fingerprint) {
        return "{"
                + "\"target\":\"" + GUARD_TARGET + "\","
                + "\"category\":\"predicate\","
                + "\"name\":\"getActiveTenant\","
                + "\"resultSemantic\":\"Returns the tenant with this id when it is not deleted.\","
                + "\"parameters\":{\"id\":\"id\"},"
                + "\"provenance\":{\"assertedBy\":\"the.assist\",\"origin\":\""
                + DraftProposal.DRAFT_ORIGIN + "\",\"date\":\"2026-09-01\",\"fingerprint\":\""
                + fingerprint + "\"}}";
    }
}
