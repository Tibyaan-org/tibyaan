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
 * Phase Attestation AT-3: the BOUNDARY intake, member C2, end to end through the provider (Volume 5
 * section 4.15.2.3). Fixtures ATT-14 to ATT-19.
 *
 * <p>This is the elevated-stakes category, and the tests are mostly about what it REFUSES. For every other
 * attested capability the tool proved no write is visible in what it read; for a delegation it proved
 * nothing of the kind, so the read-only property rests on a human's word. Each guard below is the design
 * refusing to let that word be implied, defaulted, or mistaken for a data claim.
 */
class AttestationBoundaryProviderTest {

    private static final Path FIXTURE =
            Path.of("src", "test", "resources", "fixtures", "attest-boundary", "AttestBoundary.java");
    private static final String PKG = "com.example.attestboundary.InvoiceServiceImpl#";
    private static final String DELEGATION_TARGET = PKG + "getRemoteInvoice(Long)";
    private static final String DELEGATION_LIST_TARGET = PKG + "listRemoteInvoices()";
    private static final String LOCAL_TARGET = PKG + "findLocalByNumber(String)";
    private static final String UNSAFE_TARGET = PKG + "getRemoteInvoiceUnsafe(Long)";

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();
    private final SemanticValidator validator = new SemanticValidator();

    // ---------- ATT-14, the C2 positive ----------

    @Test
    void att14_aDelegationWithAnExplicitReadOnlyAffirmationEmitsAsABoundaryCapability(@TempDir Path root)
            throws IOException {
        copyFixture(root);
        String fingerprint = fingerprint(root, DELEGATION_TARGET);
        writeAttestations(root, "[" + boundaryEntry(DELEGATION_TARGET, "getRemoteInvoice",
                "Returns the invoice with this id from the billing service.", "\"id\":\"id\"", "Invoice",
                "s.owner", true, fingerprint) + "]");

        ScanReport report = provider.scan(new ProviderInput(root));

        assertThat(report.attestationRefusals()).isEmpty();
        assertThat(report.attestedCount()).isEqualTo(1);

        // Guard 4: the class is readable from the name, and it still begins with the reserved prefix so
        // core rule 9 is satisfied without amendment.
        Capability capability = attested(report, "attested_boundary_getRemoteInvoice");
        assertThat(capability.name()).startsWith("attested_");
        assertThat(capability.output()).isEqualTo(Type.entityRef("Invoice"));

        Attestation attestation = capability.attestation();
        assertThat(attestation.category()).isEqualTo(AttestationCategory.BOUNDARY);
        assertThat(attestation.hasBoundaryClass()).isTrue();
        assertThat(attestation.securityOwner()).isEqualTo("s.owner");
        assertThat(attestation.readOnlyAffirmed()).isTrue();
        assertThat(attestation.assertedAuthorities()).isEmpty();
        assertThat(validator.validate(report.model()).isValid()).isTrue();
    }

    // ---------- ATT-15, the affirmation guard (guard 2) ----------

    @Test
    void att15_anAbsentOrFalseReadOnlyAffirmationRefuses(@TempDir Path root) throws IOException {
        copyFixture(root);
        String fingerprint = fingerprint(root, DELEGATION_TARGET);

        // Absent: silence is never an affirmation.
        writeAttestations(root, "[" + boundaryEntry(DELEGATION_TARGET, "getRemoteInvoice",
                "Returns the invoice.", "\"id\":\"id\"", "Invoice", "s.owner", null, fingerprint) + "]");
        IntakeRefusal absent = onlyRefusal(root);
        assertThat(absent.cause()).isEqualTo(IntakeRefusal.Cause.STRUCTURAL_FRAME);
        assertThat(absent.detail()).contains("readOnlyAffirmed").contains("no write ANYWHERE");

        // Explicitly false: the same refusal, because the entry is stating the opposite of what it needs.
        writeAttestations(root, "[" + boundaryEntry(DELEGATION_TARGET, "getRemoteInvoice",
                "Returns the invoice.", "\"id\":\"id\"", "Invoice", "s.owner", false, fingerprint) + "]");
        assertThat(onlyRefusal(root).cause()).isEqualTo(IntakeRefusal.Cause.STRUCTURAL_FRAME);
    }

    // ---------- ATT-16, the distinguished asserter (guard 1), both directions ----------

    @Test
    void att16_aBoundaryEntryWithoutASecurityOwnerRefuses(@TempDir Path root) throws IOException {
        copyFixture(root);
        String fingerprint = fingerprint(root, DELEGATION_LIST_TARGET);
        writeAttestations(root, "[" + boundaryEntry(DELEGATION_LIST_TARGET, "listRemoteInvoices",
                "Returns every invoice from the billing service.", "", "Invoice", null, true,
                fingerprint) + "]");

        IntakeRefusal refusal = onlyRefusal(root);
        assertThat(refusal.cause()).isEqualTo(IntakeRefusal.Cause.STRUCTURAL_FRAME);
        assertThat(refusal.detail()).contains("securityOwner").contains("SECURITY");
    }

    @Test
    void att16_aDataCategoryCarryingBoundaryFieldsRefuses(@TempDir Path root) throws IOException {
        // The other direction, and the one that keeps the classes from blurring: a shape entry wearing a
        // security owner would read as a security claim to any consumer.
        copyFixture(root);
        String fingerprint = fingerprint(root, LOCAL_TARGET);
        writeAttestations(root, "[" + boundaryEntry(LOCAL_TARGET, "findLocalByNumber", "Returns invoices.",
                "\"number\":\"Matches the number.\"", "Invoice", "s.owner", null, fingerprint)
                .replace("\"category\":\"boundary\"", "\"category\":\"shape\"") + "]");

        IntakeRefusal refusal = onlyRefusal(root);
        assertThat(refusal.cause()).isEqualTo(IntakeRefusal.Cause.CATEGORY_MISMATCH);
        assertThat(refusal.detail()).contains("boundary fields").contains("securityOwner");
    }

    // ---------- ATT-17, the partition guard (the local-tie mirror) ----------

    @Test
    void att17_aBoundaryEntryOverATargetWithALocalTieRefusesAndNamesShape(@TempDir Path root)
            throws IOException {
        copyFixture(root);
        String fingerprint = fingerprint(root, LOCAL_TARGET);
        writeAttestations(root, "[" + boundaryEntry(LOCAL_TARGET, "findLocalByNumber",
                "Returns invoices with this number.", "\"number\":\"id\"", "Invoice", "s.owner", true,
                fingerprint) + "]");

        IntakeRefusal refusal = onlyRefusal(root);
        assertThat(refusal.cause()).isEqualTo(IntakeRefusal.Cause.CATEGORY_MISMATCH);
        assertThat(refusal.detail())
                .contains("HAS an in-tree persistence access")
                .contains("category shape");
    }

    // ---------- read-safety is not softened, and C1 is refused by name ----------

    @Test
    void readSafetyStillRefusesAVisibleWriteBeforeAnyAffirmationIsConsidered(@TempDir Path root)
            throws IOException {
        copyFixture(root);
        String fingerprint = fingerprint(root, UNSAFE_TARGET);
        writeAttestations(root, "[" + boundaryEntry(UNSAFE_TARGET, "getRemoteInvoiceUnsafe",
                "Returns the invoice.", "\"id\":\"id\"", "Invoice", "s.owner", true, fingerprint) + "]");

        assertThat(onlyRefusal(root).cause()).isEqualTo(IntakeRefusal.Cause.READ_SAFETY);
    }

    @Test
    void theSecurityContractMemberC1IsRefusedByNameWithItsReason(@TempDir Path root) throws IOException {
        // AT-3's scope split (section 4.15.2.3): C1's target is an ALREADY-EMITTED capability, and that
        // targeting model is its own decision with a security consequence. It refuses rather than guessing.
        copyFixture(root);
        String fingerprint = fingerprint(root, DELEGATION_TARGET);
        String entry = boundaryEntry(DELEGATION_TARGET, "getRemoteInvoice", "Returns the invoice.",
                "\"id\":\"id\"", "Invoice", "s.owner", true, fingerprint)
                .replace("\"provenance\"", "\"assertedAuthorities\":[\"INVOICE_READ\"],\"provenance\"");
        writeAttestations(root, "[" + entry + "]");

        IntakeRefusal refusal = onlyRefusal(root);
        assertThat(refusal.cause()).isEqualTo(IntakeRefusal.Cause.CATEGORY_NOT_BUILT);
        assertThat(refusal.detail()).contains("SECURITY CONTRACT").contains("binding uniqueness");
    }

    // ---------- ATT-19, the counting guard (guard 3) ----------

    @Test
    void att19_aBoundaryAttestationIsNeverSummedIntoDiscovered(@TempDir Path root) throws IOException {
        copyFixture(root);
        String fingerprint = fingerprint(root, DELEGATION_TARGET);
        writeAttestations(root, "[" + boundaryEntry(DELEGATION_TARGET, "getRemoteInvoice",
                "Returns the invoice with this id from the billing service.", "\"id\":\"id\"", "Invoice",
                "s.owner", true, fingerprint) + "]");

        ScanReport report = provider.scan(new ProviderInput(root));

        assertThat(report.discoveredCount()).isZero();
        assertThat(report.attestedCount()).isEqualTo(1);
        assertThat(report.model().capabilities()).filteredOn(c -> !c.hasAttestation()).isEmpty();
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
        Path destination = root.resolve("AttestBoundary.java");
        if (!Files.exists(destination)) {
            Files.copy(FIXTURE, destination);
        }
    }

    private static void writeAttestations(Path root, String json) throws IOException {
        Path dir = root.resolve("tibyan");
        Files.createDirectories(dir);
        Files.write(dir.resolve("attestations.json"), json.getBytes(StandardCharsets.UTF_8));
    }

    private static String boundaryEntry(String target, String name, String resultSemantic,
                                        String parameters, String subjectEntity, String securityOwner,
                                        Boolean readOnlyAffirmed, String fingerprint) {
        StringBuilder entry = new StringBuilder("{")
                .append("\"target\":\"").append(target).append("\",")
                .append("\"category\":\"boundary\",")
                .append("\"name\":\"").append(name).append("\",")
                .append("\"resultSemantic\":\"").append(resultSemantic).append("\",")
                .append("\"parameters\":{").append(parameters).append("},")
                .append("\"subjectEntity\":\"").append(subjectEntity).append("\",");
        if (securityOwner != null) {
            entry.append("\"securityOwner\":\"").append(securityOwner).append("\",");
        }
        if (readOnlyAffirmed != null) {
            entry.append("\"readOnlyAffirmed\":").append(readOnlyAffirmed).append(",");
        }
        entry.append("\"provenance\":{\"assertedBy\":\"a.elseginy\",\"origin\":\"human-authored\",")
                .append("\"date\":\"2026-09-01\",\"fingerprint\":\"").append(fingerprint).append("\"}}");
        return entry.toString();
    }
}
