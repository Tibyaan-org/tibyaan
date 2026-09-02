package com.tibyaan.provider.spring;

import com.tibyaan.core.ir.Attestation;
import com.tibyaan.core.ir.AttestationCategory;
import com.tibyaan.core.ir.AttestationOrigin;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.Type;
import com.tibyaan.core.semantic.SemanticValidator;
import com.tibyaan.provider.spring.attest.IntakeRefusal;
import com.tibyaan.plugin.ProviderInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase Attestation AT-1: the PREDICATE intake, end to end through the provider (Volume 5 section 4.15).
 * The fixture is copied to a temp source root so a real {@code tibyan/attestations.json} can be authored
 * beside it and stamped with the real slice fingerprint the tool computes, exactly as {@code tibyan attest}
 * would. This proves the mechanism: intake validation, the fingerprint lifecycle, the read-safety refusal,
 * the coverage guards, and the carriage of the human's words into an attested capability. Fixtures ATT-1,
 * ATT-3, ATT-6, ATT-7, ATT-8, and the intake refusal battery.
 */
class AttestationIntakeProviderTest {

    private static final Path FIXTURE =
            Path.of("src", "test", "resources", "fixtures", "attest-predicate", "AttestPredicate.java");
    private static final Path RECEIVER_FIXTURE = Path.of("src", "test", "resources", "fixtures",
            "read-safety-receiver", "ReadSafetyReceiver.java");
    private static final String FILTER_TARGET =
            "com.example.attest.TenantServiceImpl#getAllByFilter(String,Integer)";
    private static final String GUARD_TARGET =
            "com.example.attest.TenantServiceImpl#getActiveTenant(Long)";
    private static final String UNSAFE_TARGET =
            "com.example.attest.TenantServiceImpl#getAllByFilterUnsafe(String)";

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();
    private final SemanticValidator validator = new SemanticValidator();

    // ---------- positive: the reference attestation carries and validates ----------

    @Test
    void att1_aFilteredReadWithACompletePredicateAttestationDiscoversAnAttestedCapability(@TempDir Path root)
            throws IOException {
        copyFixture(root);
        String fingerprint = fingerprint(root, FILTER_TARGET);
        writeAttestations(root, filterEntry(fingerprint));

        ScanReport report = provider.scan(new ProviderInput(root));

        Capability attested = capability(report, "attested_searchTenants");
        assertThat(attested.binding().value()).isEqualTo("TenantServiceImpl#getAllByFilter");
        assertThat(attested.output()).isEqualTo(Type.entityRef("Tenant"));
        assertThat(attested.outputMultiple()).isTrue();
        assertThat(report.attestationRefusals()).isEmpty();
        assertThat(report.attestedCount()).isEqualTo(1);

        Attestation attestation = attested.attestation();
        assertThat(attestation.category()).isEqualTo(AttestationCategory.PREDICATE);
        assertThat(attestation.origin()).isEqualTo(AttestationOrigin.HUMAN_AUTHORED);
        assertThat(attestation.assertedBy()).isEqualTo("a.elseginy");
        assertThat(attestation.resultSemantic())
                .isEqualTo("Returns the tenants matching the given filters.");
        assertThat(attestation.parameterMeanings())
                .containsEntry("fullName",
                        "Matches tenants whose full name contains this text, case-insensitive.")
                .containsEntry("pageNum", Attestation.PAGING_MARK);
        assertThat(attestation.fingerprint()).isEqualTo(fingerprint);

        // The attested capability is a valid model member (rules 8 and 9 hold), and it is NOT summed into
        // discovered: no by-id capability was minted for this refused read.
        assertThat(validator.validate(report.model()).isValid()).isTrue();
        assertThat(report.discoveredCount()).isZero();
    }

    @Test
    void att3_aGuardedByIdViewReadAttestsAsAnAttestedSingleRead(@TempDir Path root) throws IOException {
        copyFixture(root);
        String fingerprint = fingerprint(root, GUARD_TARGET);
        writeAttestations(root, "[" + guardEntry(fingerprint) + "]");

        ScanReport report = provider.scan(new ProviderInput(root));

        Capability attested = capability(report, "attested_getActiveTenant");
        assertThat(attested.binding().value()).isEqualTo("TenantServiceImpl#getActiveTenant");
        assertThat(attested.output()).isEqualTo(Type.entityRef("Tenant"));
        assertThat(attested.outputMultiple()).as("a by-id read is single, not a list").isFalse();
        assertThat(attested.attestation().parameterMeanings()).containsEntry("id", Attestation.ID_MARK);
        assertThat(validator.validate(report.model()).isValid()).isTrue();
    }

    // ---------- the fingerprint lifecycle (ATT-8) ----------

    @Test
    void att8_changingTheFingerprintedBodyInvalidatesTheAttestationLoudly(@TempDir Path root)
            throws IOException {
        copyFixture(root);
        String fingerprint = fingerprint(root, FILTER_TARGET);
        writeAttestations(root, filterEntry(fingerprint));
        assertThat(provider.scan(new ProviderInput(root)).attestedCount()).isEqualTo(1);

        // Change the fingerprinted method body: append a statement to getAllByFilter.
        Path source = root.resolve("AttestPredicate.java");
        String changed = Files.readString(source).replace(
                "return new TenantResultSet(tenantRepository.findByFilters(fullName, pageNum));",
                "int touched = pageNum + 1;\n        return new TenantResultSet("
                        + "tenantRepository.findByFilters(fullName, touched));");
        Files.writeString(source, changed);

        ScanReport report = provider.scan(new ProviderInput(root));
        assertThat(report.attestedCount()).as("a stale attestation never carries").isZero();
        assertThat(report.attestationRefusals())
                .anySatisfy(refusal -> {
                    assertThat(refusal.cause()).isEqualTo(IntakeRefusal.Cause.STALE_ATTESTATION);
                    assertThat(refusal.detail()).contains("the code changed since attestation");
                });
    }

    @Test
    void readSafetyDoesNotCrashOnACallToANonFieldReceiver(@TempDir Path root) throws IOException {
        // Regression for the first-measurement finding: a read whose body calls a static factory and a
        // local-variable method (non-field receivers) crashed read-safety with a NullPointerException,
        // because MUTATING_INFRA_TYPES is an immutable Set whose contains(null) throws and a non-field
        // receiver has no field type. The scan must now produce a clean outcome, never throw. The fixture
        // repository ties cleanly, so this read attests.
        copyFixture(root);
        String target = "com.example.attest.TenantServiceImpl#getAllByBuilder(String)";
        String fingerprint = fingerprint(root, target);
        String entry = entry(target, "predicate", "searchByBuilder", "Returns tenants matching the name.",
                "\"fullName\":\"Matches the full name.\"", null, "a.elseginy", "human-authored", fingerprint);
        writeAttestations(root, "[" + entry + "]");

        ScanReport report = provider.scan(new ProviderInput(root));
        assertThat(report.attestedCount()).isEqualTo(1);
        assertThat(report.attestationRefusals()).isEmpty();
    }

    @Test
    void att10_aMisattachedAttestationIsCaughtByTheFingerprint(@TempDir Path root) throws IOException {
        copyFixture(root);
        // The entry targets getAllByFilter but carries the fingerprint of a DIFFERENT method (the guarded
        // read): a misattached attestation, the right content pinned to the wrong element. Intake recomputes
        // the fingerprint over the target's own slice, and the mismatch refuses STALE, so a misattached
        // claim can never carry (the phase's mis-bind guard).
        String wrongFingerprint = fingerprint(root, GUARD_TARGET);
        String entry = entry(FILTER_TARGET, "predicate", "searchTenants",
                "Returns the tenants matching the given filters.",
                "\"fullName\":\"Matches the full name.\",\"pageNum\":\"paging\"", null, "a.elseginy",
                "human-authored", wrongFingerprint);
        writeAttestations(root, "[" + entry + "]");
        assertRefused(root, IntakeRefusal.Cause.STALE_ATTESTATION);
    }

    // ---------- the intake refusal battery (ATT-6, ATT-7) ----------

    @Test
    void att6_missingFingerprintRefuses_neverSilentlyCompleted(@TempDir Path root) throws IOException {
        copyFixture(root);
        writeAttestations(root, filterEntry(null));
        assertRefused(root, IntakeRefusal.Cause.MISSING_FINGERPRINT);
    }

    @Test
    void att7_aMutatingBodyRefusesReadSafety(@TempDir Path root) throws IOException {
        copyFixture(root);
        String fingerprint = fingerprint(root, UNSAFE_TARGET);
        String entry = entry(UNSAFE_TARGET, "predicate", "searchUnsafe",
                "Returns tenants, and also deletes.",
                "\"fullName\":\"Matches the full name.\"", null, "a.elseginy", "human-authored", fingerprint);
        writeAttestations(root, "[" + entry + "]");
        assertRefused(root, IntakeRefusal.Cause.READ_SAFETY);
    }

    // ---------- RS-1, RS-2, RS-3 end to end: the receiver-aware write-set clause (section 4.15.2.1) ----

    @Test
    void rs1_aggregationIntoAFreshLocalMapPassesReadSafetyAndAttests(@TempDir Path root) throws IOException {
        // The pms aggregate shape: one load, then merge/put/remove into a locally constructed map. Before
        // the refinement this refused READ_SAFETY on the merge; it is read-only, so it must now attest.
        copyReceiverFixture(root);
        String target = "com.example.readsafety.CostReportServiceImpl#totalsByCategory(Long)";
        String fingerprint = fingerprint(root, target);
        writeAttestations(root, "[" + entry(target, "predicate", "costTotalsByCategory",
                "Totals for one building's costs, summed per category in the application.",
                "\"buildingId\":\"The building whose costs are totalled.\"", null, "a.elseginy",
                "human-authored", fingerprint) + "]");

        ScanReport report = provider.scan(new ProviderInput(root));

        assertThat(report.attestationRefusals()).isEmpty();
        assertThat(report.attestedCount()).isEqualTo(1);
        assertThat(capability(report, "attested_costTotalsByCategory").output())
                .isEqualTo(Type.entityRef("Cost"));
    }

    @Test
    void rs2_passingReadSafetyGrantsNothingByItself(@TempDir Path root) throws IOException {
        // The same provable-local aggregation, tied only through a hand-written repository. Read-safety
        // now passes, and the entry still refuses, at the output entity tie. Removing a false refusal is
        // not the same as granting an attestation.
        copyReceiverFixture(root);
        String target = "com.example.readsafety.CostReportServiceImpl#ledgerTotals(Long)";
        String fingerprint = fingerprint(root, target);
        writeAttestations(root, "[" + entry(target, "predicate", "ledgerTotals",
                "Totals for one building's ledger rows.",
                "\"buildingId\":\"The building whose rows are totalled.\"", null, "a.elseginy",
                "human-authored", fingerprint) + "]");

        ScanReport report = provider.scan(new ProviderInput(root));

        assertThat(report.attestedCount()).isZero();
        assertThat(report.attestationRefusals())
                .anySatisfy(refusal -> {
                    assertThat(refusal.cause()).isEqualTo(IntakeRefusal.Cause.CATEGORY_MISMATCH);
                    assertThat(refusal.detail()).contains("output entity tie is not structurally provable");
                });
    }

    @Test
    void rs3_aRepositoryWriteBesideALocalAggregationStillRefusesEndToEnd(@TempDir Path root)
            throws IOException {
        // THE PRIMARY SAFETY GUARD, end to end. The carve-out must never rescue a body that also writes.
        copyReceiverFixture(root);
        String target = "com.example.readsafety.CostReportServiceImpl#totalsAndPurge(Long)";
        String fingerprint = fingerprint(root, target);
        writeAttestations(root, "[" + entry(target, "predicate", "totalsAndPurge",
                "Totals, and also deletes.",
                "\"buildingId\":\"The building.\"", null, "a.elseginy", "human-authored", fingerprint) + "]");

        assertRefused(root, IntakeRefusal.Cause.READ_SAFETY);
    }

    @Test
    void anUnresolvableTargetRefuses(@TempDir Path root) throws IOException {
        copyFixture(root);
        String entry = entry("com.example.attest.Nope#ghost(String)", "predicate", "x", "y.",
                "\"a\":\"b\"", null, "a.elseginy", "human-authored", "sha256:whatever");
        writeAttestations(root, "[" + entry + "]");
        assertRefused(root, IntakeRefusal.Cause.UNRESOLVED_TARGET);
    }

    @Test
    void anUncoveredDomainParameterRefuses(@TempDir Path root) throws IOException {
        copyFixture(root);
        String fingerprint = fingerprint(root, FILTER_TARGET);
        String entry = entry(FILTER_TARGET, "predicate", "searchTenants", "Returns tenants.",
                "\"pageNum\":\"paging\"", null, "a.elseginy", "human-authored", fingerprint);
        writeAttestations(root, "[" + entry + "]");
        assertRefused(root, IntakeRefusal.Cause.COVERAGE);
    }

    @Test
    void aMeaningKeyThatIsNotAnInputRefuses(@TempDir Path root) throws IOException {
        copyFixture(root);
        String fingerprint = fingerprint(root, FILTER_TARGET);
        String entry = entry(FILTER_TARGET, "predicate", "searchTenants", "Returns tenants.",
                "\"fullName\":\"m\",\"pageNum\":\"paging\",\"ghost\":\"x\"", null, "a.elseginy",
                "human-authored", fingerprint);
        writeAttestations(root, "[" + entry + "]");
        assertRefused(root, IntakeRefusal.Cause.COVERAGE);
    }

    @Test
    void thePagingMarkOnADomainParameterRefuses(@TempDir Path root) throws IOException {
        copyFixture(root);
        String fingerprint = fingerprint(root, FILTER_TARGET);
        String entry = entry(FILTER_TARGET, "predicate", "searchTenants", "Returns tenants.",
                "\"fullName\":\"paging\",\"pageNum\":\"paging\"", null, "a.elseginy", "human-authored",
                fingerprint);
        writeAttestations(root, "[" + entry + "]");
        assertRefused(root, IntakeRefusal.Cause.COVERAGE);
    }

    @Test
    void anUnknownCategoryTokenRefuses_theClosedSetGuard(@TempDir Path root) throws IOException {
        // Every ratified category is now built: predicate (AT-1), shape (AT-2), both (the compound), and
        // boundary (AT-3). What remains permanently true of condition 2 is the CLOSED SET: a token outside
        // the four is refused rather than guessed at. This test asserted the not-yet-built case of shape,
        // then of both, and each build in turn made that statement false; the closed-set guard is the part
        // of condition 2 that no later phase can retire.
        copyFixture(root);
        String fingerprint = fingerprint(root, FILTER_TARGET);
        String entry = entry(FILTER_TARGET, "semantic", "searchTenants", "Returns tenants.",
                "\"fullName\":\"m\",\"pageNum\":\"paging\"", "Tenant", "a.elseginy", "human-authored",
                fingerprint);
        writeAttestations(root, "[" + entry + "]");
        assertRefused(root, IntakeRefusal.Cause.CATEGORY_MISMATCH);
    }

    @Test
    void att9_anAiDraftShipsNothing_theAssistBoundaryBindsHere(@TempDir Path root) throws IOException {
        // ATT-9's enduring claim: an ai-draft entry NEVER reaches any output. AT-1 enforced that by
        // refusing the entry under PROVENANCE, because the draft state had nowhere else to go. AT-4 gives
        // it somewhere to go, a review queue, so the mechanism changed and the claim did not: a draft
        // still produces no capability and enters no count. The test now asserts the claim rather than
        // the mechanism, which is why it survives the next build too.
        copyFixture(root);
        String fingerprint = fingerprint(root, FILTER_TARGET);
        String entry = entry(FILTER_TARGET, "predicate", "searchTenants", "Returns tenants.",
                "\"fullName\":\"m\",\"pageNum\":\"paging\"", null, "a.elseginy", "ai-draft", fingerprint);
        writeAttestations(root, "[" + entry + "]");

        ScanReport report = provider.scan(new ProviderInput(root));

        assertThat(report.attestedCount()).isZero();
        assertThat(report.model().capabilities()).noneMatch(Capability::hasAttestation);
        assertThat(report.attestationDrafts()).hasSize(1);
    }

    @Test
    void twoEntriesTargetingTheSameReadRefuseTheSecond(@TempDir Path root) throws IOException {
        copyFixture(root);
        String fingerprint = fingerprint(root, FILTER_TARGET);
        String one = entry(FILTER_TARGET, "predicate", "searchTenants", "Returns tenants.",
                "\"fullName\":\"m\",\"pageNum\":\"paging\"", null, "a.elseginy", "human-authored", fingerprint);
        String two = entry(FILTER_TARGET, "predicate", "searchAgain", "Returns tenants again.",
                "\"fullName\":\"m\",\"pageNum\":\"paging\"", null, "a.elseginy", "human-authored", fingerprint);
        writeAttestations(root, "[" + one + "," + two + "]");
        assertThat(provider.scan(new ProviderInput(root)).attestationRefusals())
                .anySatisfy(r -> assertThat(r.cause()).isEqualTo(IntakeRefusal.Cause.DUPLICATE_TARGET));
    }

    @Test
    void att11_noAttestationsFileMeansTheModelIsUnchanged(@TempDir Path root) throws IOException {
        copyFixture(root);
        ScanReport report = provider.scan(new ProviderInput(root));
        assertThat(report.attestedCount()).isZero();
        assertThat(report.attestationRefusals()).isEmpty();
        assertThat(report.model().capabilities()).noneMatch(Capability::hasAttestation);
    }

    // ---------- helpers ----------

    private void assertRefused(Path root, IntakeRefusal.Cause cause) {
        ScanReport report = provider.scan(new ProviderInput(root));
        assertThat(report.attestedCount()).as("a refused entry produces no capability").isZero();
        assertThat(report.attestationRefusals())
                .as("expected a refusal with cause " + cause)
                .anySatisfy(refusal -> assertThat(refusal.cause()).isEqualTo(cause));
    }

    private String fingerprint(Path root, String target) {
        Optional<String> fingerprint = provider.attestationFingerprint(new ProviderInput(root), target);
        assertThat(fingerprint).as("the target must resolve to compute a fingerprint").isPresent();
        return fingerprint.get();
    }

    private static Capability capability(ScanReport report, String name) {
        return report.model().capabilities().stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no capability named " + name + "; capabilities were "
                        + report.model().capabilities().stream().map(Capability::name).toList()
                        + "; refusals were " + report.attestationRefusals()));
    }

    private static void copyFixture(Path root) throws IOException {
        Files.write(root.resolve("AttestPredicate.java"), Files.readAllBytes(FIXTURE));
    }

    private static void copyReceiverFixture(Path root) throws IOException {
        Files.write(root.resolve("ReadSafetyReceiver.java"), Files.readAllBytes(RECEIVER_FIXTURE));
    }

    private static void writeAttestations(Path root, String json) throws IOException {
        Path dir = root.resolve("tibyan");
        Files.createDirectories(dir);
        Files.write(dir.resolve("attestations.json"), json.getBytes(StandardCharsets.UTF_8));
    }

    private static String filterEntry(String fingerprint) {
        return "[" + entry(FILTER_TARGET, "predicate", "searchTenants",
                "Returns the tenants matching the given filters.",
                "\"fullName\":\"Matches tenants whose full name contains this text, case-insensitive.\","
                        + "\"pageNum\":\"paging\"",
                null, "a.elseginy", "human-authored", fingerprint) + "]";
    }

    private static String guardEntry(String fingerprint) {
        return entry(GUARD_TARGET, "predicate", "getActiveTenant",
                "Returns the tenant with this id only if it is not deleted; otherwise not found.",
                "\"id\":\"id\"", null, "a.elseginy", "human-authored", fingerprint);
    }

    // Builds one raw entry as JSON. A null fingerprint omits the field entirely (the missing-fingerprint
    // case); a null subjectEntity omits it (a pure predicate).
    private static String entry(String target, String category, String name, String resultSemantic,
                                String parameters, String subjectEntity, String assertedBy, String origin,
                                String fingerprint) {
        StringBuilder provenance = new StringBuilder("{\"assertedBy\":\"" + assertedBy + "\",\"origin\":\""
                + origin + "\",\"date\":\"2026-07-24\"");
        if (fingerprint != null) {
            provenance.append(",\"fingerprint\":\"").append(fingerprint).append("\"");
        }
        provenance.append("}");
        StringBuilder entry = new StringBuilder("{")
                .append("\"target\":\"").append(target).append("\",")
                .append("\"category\":\"").append(category).append("\",")
                .append("\"name\":\"").append(name).append("\",")
                .append("\"resultSemantic\":\"").append(resultSemantic).append("\",")
                .append("\"parameters\":{").append(parameters).append("},");
        if (subjectEntity != null) {
            entry.append("\"subjectEntity\":\"").append(subjectEntity).append("\",");
        }
        entry.append("\"provenance\":").append(provenance).append("}");
        return entry.toString();
    }
}
