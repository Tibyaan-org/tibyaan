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
 * Phase Attestation AT-2: the SHAPE intake, end to end through the provider (Volume 5 section 4.15.2,
 * category B). Fixture ATT-2 and the category B refusal battery.
 *
 * <p>What a shape assertion supplies is what the read RETURNS, and nothing else. Everything around that
 * claim is still proven here, so these tests are mostly about the FRAME: the local-tie precondition that
 * routes a delegation to AT-3 instead of accepting it, the asserted subject entity's existence in the
 * model, structural non-contradiction, and the by-primary-key obligation that comes with the reserved id
 * mark. The two positives prove carriage; the six refusals prove the frame is not negotiable.
 */
class AttestationShapeProviderTest {

    private static final Path FIXTURE =
            Path.of("src", "test", "resources", "fixtures", "attest-shape", "AttestShape.java");
    private static final String PKG = "com.example.attestshape.BuildingServiceImpl#";
    private static final String AGGREGATE_TARGET = PKG + "getBuilding(Long)";
    private static final String WRAPPER_TARGET = PKG + "getUnitSummaries()";
    private static final String DELEGATION_TARGET = PKG + "getRemoteBuilding(Long)";
    private static final String CONTRADICTION_TARGET = PKG + "getUnitBundle(Long)";
    private static final String FOREIGN_KEY_TARGET = PKG + "getBuildingByOwner(Long)";
    private static final String UNSAFE_TARGET = PKG + "getUnitSummariesUnsafe()";

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();
    private final SemanticValidator validator = new SemanticValidator();

    // ---------- ATT-2, the positives ----------

    @Test
    void att2a_anAggregateByIdReadAttestsItsEnrichmentAndKeepsItsProvenLoad(@TempDir Path root)
            throws IOException {
        copyFixture(root);
        String fingerprint = fingerprint(root, AGGREGATE_TARGET);
        writeAttestations(root, "[" + entry(AGGREGATE_TARGET, "shape", "getBuilding",
                "Returns the building with this id, enriched with its units.",
                "\"id\":\"id\"", "Building", fingerprint) + "]");

        ScanReport report = provider.scan(new ProviderInput(root));

        assertThat(report.attestedCount()).isEqualTo(1);
        assertThat(report.attestationRefusals()).isEmpty();
        Capability capability = attested(report, "attested_getBuilding");
        assertThat(capability.binding().value()).isEqualTo("BuildingServiceImpl#getBuilding");
        assertThat(capability.output()).isEqualTo(Type.entityRef("Building"));
        assertThat(capability.outputMultiple()).isFalse();

        Attestation attestation = capability.attestation();
        assertThat(attestation.category()).isEqualTo(AttestationCategory.SHAPE);
        assertThat(attestation.subjectEntity()).isEqualTo("Building");
        assertThat(attestation.resultSemantic())
                .isEqualTo("Returns the building with this id, enriched with its units.");
        assertThat(attestation.parameterMeanings()).containsExactly(
                org.assertj.core.api.Assertions.entry("id", Attestation.ID_MARK));
        assertThat(validator.validate(report.model()).isValid()).isTrue();
    }

    @Test
    void att2b_anOpaqueWrapperOverAnInTreeRepositoryAccessAttestsWhatItReturns(@TempDir Path root)
            throws IOException {
        // The element type is not an in-tree entity, so no structural rule can tie the output. That is
        // exactly the hole the assertion fills, and the repository access is the local tie that makes
        // asserting it honest rather than blind.
        copyFixture(root);
        String fingerprint = fingerprint(root, WRAPPER_TARGET);
        writeAttestations(root, "[" + entry(WRAPPER_TARGET, "shape", "listUnitSummaries",
                "Returns a summary row per unit.", "", "Unit", fingerprint) + "]");

        ScanReport report = provider.scan(new ProviderInput(root));

        assertThat(report.attestedCount()).isEqualTo(1);
        Capability capability = attested(report, "attested_listUnitSummaries");
        assertThat(capability.output()).isEqualTo(Type.entityRef("Unit"));
        assertThat(capability.attestation().subjectEntity()).isEqualTo("Unit");
        assertThat(validator.validate(report.model()).isValid()).isTrue();
    }

    @Test
    void att5_attestedIsNeverSummedIntoDiscovered(@TempDir Path root) throws IOException {
        copyFixture(root);
        String fingerprint = fingerprint(root, AGGREGATE_TARGET);
        writeAttestations(root, "[" + entry(AGGREGATE_TARGET, "shape", "getBuilding",
                "Returns the building with this id, enriched with its units.",
                "\"id\":\"id\"", "Building", fingerprint) + "]");

        ScanReport report = provider.scan(new ProviderInput(root));

        // Every read in this fixture is structurally refused, so a shape attestation must move the
        // attested count and leave the verified count exactly where it was: at zero.
        assertThat(report.discoveredCount()).isZero();
        assertThat(report.attestedCount()).isEqualTo(1);
        assertThat(report.model().capabilities()).filteredOn(c -> !c.hasAttestation()).isEmpty();
    }

    // ---------- the category B refusal battery ----------

    @Test
    void aDelegationWithNoLocalTieRoutesToAt3ByNameAndIsNeverAcceptedAsShape(@TempDir Path root)
            throws IOException {
        copyFixture(root);
        String fingerprint = fingerprint(root, DELEGATION_TARGET);
        writeAttestations(root, "[" + entry(DELEGATION_TARGET, "shape", "getRemoteBuilding",
                "Returns the building from the catalog service.", "\"id\":\"id\"", "Building",
                fingerprint) + "]");

        IntakeRefusal refusal = onlyRefusal(root);
        assertThat(refusal.cause()).isEqualTo(IntakeRefusal.Cause.CATEGORY_NOT_BUILT);
        assertThat(refusal.detail())
                .contains("no in-tree persistence access")
                .contains("AT-3");
    }

    @Test
    void aSingleAccessReadingADifferentEntityContradictsTheAssertionAndRefuses(@TempDir Path root)
            throws IOException {
        copyFixture(root);
        String fingerprint = fingerprint(root, CONTRADICTION_TARGET);
        writeAttestations(root, "[" + entry(CONTRADICTION_TARGET, "shape", "getUnitBundle",
                "Returns the building bundle.", "\"buildingId\":\"id\"", "Building", fingerprint) + "]");

        IntakeRefusal refusal = onlyRefusal(root);
        assertThat(refusal.cause()).isEqualTo(IntakeRefusal.Cause.STRUCTURAL_CONTRADICTION);
        assertThat(refusal.detail()).contains("Building").contains("Unit");
    }

    @Test
    void theIdMarkObligesAProvenByPrimaryKeyLoadOfTheAssertedEntity(@TempDir Path root) throws IOException {
        // The parameter is a foreign key and the access is a filter query. The reserved id mark means a
        // structurally-proven identifier; claiming it where no such load exists is filling a structural
        // hole by assertion, which is the one thing the category boundary forbids.
        copyFixture(root);
        String fingerprint = fingerprint(root, FOREIGN_KEY_TARGET);
        writeAttestations(root, "[" + entry(FOREIGN_KEY_TARGET, "shape", "getBuildingByOwner",
                "Returns the owner's building.", "\"ownerId\":\"id\"", "Building", fingerprint) + "]");

        IntakeRefusal refusal = onlyRefusal(root);
        assertThat(refusal.cause()).isEqualTo(IntakeRefusal.Cause.STRUCTURAL_FRAME);
        assertThat(refusal.detail()).contains("by-primary-key").contains("never the load");
    }

    @Test
    void aSentenceCoveredDomainParameterMeansTheTargetHasAPredicateHoleToo(@TempDir Path root)
            throws IOException {
        // Only the reserved paging and id marks are structural coverage. A meaning sentence is a predicate
        // claim, so a target carrying one has both holes and belongs to the compound category.
        copyFixture(root);
        String fingerprint = fingerprint(root, FOREIGN_KEY_TARGET);
        writeAttestations(root, "[" + entry(FOREIGN_KEY_TARGET, "shape", "getBuildingByOwner",
                "Returns the owner's building.", "\"ownerId\":\"Matches the owner.\"", "Building",
                fingerprint) + "]");

        IntakeRefusal refusal = onlyRefusal(root);
        assertThat(refusal.cause()).isEqualTo(IntakeRefusal.Cause.CATEGORY_MISMATCH);
        assertThat(refusal.detail()).contains("predicate").contains("both");
    }

    @Test
    void anAssertedSubjectEntityOutsideTheModelRefuses(@TempDir Path root) throws IOException {
        copyFixture(root);
        String fingerprint = fingerprint(root, WRAPPER_TARGET);
        writeAttestations(root, "[" + entry(WRAPPER_TARGET, "shape", "listUnitSummaries",
                "Returns a summary row per unit.", "", "Warehouse", fingerprint) + "]");

        IntakeRefusal refusal = onlyRefusal(root);
        assertThat(refusal.cause()).isEqualTo(IntakeRefusal.Cause.STRUCTURAL_FRAME);
        assertThat(refusal.detail()).contains("Warehouse").contains("not an entity in the model");
    }

    @Test
    void aShapeEntryWithNoSubjectEntityRefuses(@TempDir Path root) throws IOException {
        copyFixture(root);
        String fingerprint = fingerprint(root, WRAPPER_TARGET);
        writeAttestations(root, "[" + entry(WRAPPER_TARGET, "shape", "listUnitSummaries",
                "Returns a summary row per unit.", "", null, fingerprint) + "]");

        IntakeRefusal refusal = onlyRefusal(root);
        assertThat(refusal.cause()).isEqualTo(IntakeRefusal.Cause.STRUCTURAL_FRAME);
        assertThat(refusal.detail()).contains("subjectEntity");
    }

    @Test
    void att7_readSafetyIsNotSoftenedByAShapeAssertion(@TempDir Path root) throws IOException {
        copyFixture(root);
        String fingerprint = fingerprint(root, UNSAFE_TARGET);
        writeAttestations(root, "[" + entry(UNSAFE_TARGET, "shape", "listUnitSummaries",
                "Returns a summary row per unit.", "", "Unit", fingerprint) + "]");

        IntakeRefusal refusal = onlyRefusal(root);
        assertThat(refusal.cause()).isEqualTo(IntakeRefusal.Cause.READ_SAFETY);
    }

    @Test
    void att8_aShapeAttestationGoesStaleWhenItsFingerprintedSliceChanges(@TempDir Path root)
            throws IOException {
        copyFixture(root);
        String fingerprint = fingerprint(root, AGGREGATE_TARGET);
        writeAttestations(root, "[" + entry(AGGREGATE_TARGET, "shape", "getBuilding",
                "Returns the building with this id, enriched with its units.",
                "\"id\":\"id\"", "Building", fingerprint) + "]");
        assertThat(provider.scan(new ProviderInput(root)).attestedCount()).isEqualTo(1);

        // Change the fingerprinted slice: a new field on the RETURN TYPE, which AT-2 pins because the
        // wrapper or view class is what a shape claim describes.
        Path source = root.resolve("AttestShape.java");
        String changed = Files.readString(source, StandardCharsets.UTF_8)
                .replace("class BuildingView {\n}", "class BuildingView {\n    String addedField;\n}");
        Files.writeString(source, changed, StandardCharsets.UTF_8);

        ScanReport report = provider.scan(new ProviderInput(root));
        assertThat(report.attestedCount()).isZero();
        assertThat(report.attestationRefusals())
                .anySatisfy(r -> assertThat(r.cause()).isEqualTo(IntakeRefusal.Cause.STALE_ATTESTATION));
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
        Files.copy(FIXTURE, root.resolve("AttestShape.java"));
    }

    private static void writeAttestations(Path root, String json) throws IOException {
        Path dir = root.resolve("tibyan");
        Files.createDirectories(dir);
        Files.write(dir.resolve("attestations.json"), json.getBytes(StandardCharsets.UTF_8));
    }

    private static String entry(String target, String category, String name, String resultSemantic,
                                String parameters, String subjectEntity, String fingerprint) {
        StringBuilder entry = new StringBuilder("{")
                .append("\"target\":\"").append(target).append("\",")
                .append("\"category\":\"").append(category).append("\",")
                .append("\"name\":\"").append(name).append("\",")
                .append("\"resultSemantic\":\"").append(resultSemantic).append("\",")
                .append("\"parameters\":{").append(parameters).append("},");
        if (subjectEntity != null) {
            entry.append("\"subjectEntity\":\"").append(subjectEntity).append("\",");
        }
        entry.append("\"provenance\":{\"assertedBy\":\"a.elseginy\",\"origin\":\"human-authored\",")
                .append("\"date\":\"2026-09-01\",\"fingerprint\":\"").append(fingerprint).append("\"}}");
        return entry.toString();
    }
}
