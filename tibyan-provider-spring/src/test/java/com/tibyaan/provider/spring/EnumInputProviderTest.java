package com.tibyaan.provider.spring;

import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.Primitive;
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
 * Phase Enum Inputs, step EI-1: the input value constraint (Volume 5 section 4.16.4, fixtures EI-1 to
 * EI-9). The tool carries a value set it READ from a resolved in-tree enum declaration, or it carries
 * none. It never infers values from a parameter's name, from usage, from call sites, or from a naming
 * pattern.
 *
 * <p>These reads are all filtered, so all of them are structurally refused and become observable only
 * through a PREDICATE attestation. That is the point of the split this phase rests on: the human attests
 * MEANING and the tool proves VALUES, and an attestation can neither supply, extend, nor override a set.
 *
 * <p>The governing bias, as everywhere else: advertising a SUBSET of the accepted values is safe,
 * advertising a SUPERSET is the MIS-CONSTRAINED breach, so an unreadable declaration produces no set at
 * all rather than a guessed one, and the capability refuses rather than shipping unconstrained.
 */
class EnumInputProviderTest {

    private static final Path FIXTURE =
            Path.of("src", "test", "resources", "fixtures", "enum-inputs", "EnumInputs.java");
    private static final Path COLLISION =
            Path.of("src", "test", "resources", "fixtures", "enum-inputs-collision");
    private static final String SVC = "com.example.enuminputs.TicketServiceImpl#";

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();
    private final SemanticValidator validator = new SemanticValidator();

    // ---------- EI-1, EI-2, EI-3: what the tool reads, and what it refuses to read ----------

    @Test
    void ei1_aBareEnumCarriesItsConstantNamesInDeclarationOrder(@TempDir Path root) throws IOException {
        Capability capability = attest(root, SVC + "byStatus(TicketStatus,Integer)", "searchTickets",
                "\"status\":\"Matches tickets in this status.\",\"pageNum\":\"paging\"");

        assertThat(capability.inputs().get("status")).isEqualTo(Primitive.STRING);
        assertThat(capability.inputValues().get("status"))
                .containsExactly("OPEN", "ASSIGNED", "DOING", "DONE");
    }

    @Test
    void ei2_aValueMappedEnumCarriesItsLiteralsNotItsConstantNames(@TempDir Path root) throws IOException {
        // The @JsonValue accessor proves the field IS the wire form, so the carried set is what the
        // application actually accepts: lowercase literals, not MAINTENANCE.
        Capability capability = attest(root, SVC + "byCategory(CostCategory)", "searchByCategory",
                "\"category\":\"Matches tickets in this cost category.\"");

        assertThat(capability.inputValues().get("category"))
                .containsExactly("maintenance", "utilities", "other");
    }

    @Test
    void ei3_theConstructorLiteralTrap_labelsAreNeverMistakenForWireValues(@TempDir Path root)
            throws IOException {
        // The most important negative in the phase. LeaseStatus declares DRAFT("Draft") with no
        // @JsonValue, so Jackson serializes the CONSTANT NAME and the labels are decorative. Carrying
        // "Draft" would advertise a value the application rejects, which is the mis-constrained breach.
        Capability capability = attest(root, SVC + "byLease(LeaseStatus)", "searchByLease",
                "\"leaseStatus\":\"Matches tickets whose lease is in this state.\"");

        assertThat(capability.inputValues().get("leaseStatus"))
                .containsExactly("DRAFT", "ACTIVE", "ENDED");
        assertThat(capability.inputValues().get("leaseStatus"))
                .doesNotContain("Draft", "Active", "Ended");
    }

    // ---------- EI-4: the attestation split ----------

    @Test
    void ei4_theToolProvesValuesAndTheHumanAttestsMeaning(@TempDir Path root) throws IOException {
        Capability capability = attest(root, SVC + "byStatus(TicketStatus,Integer)", "searchTickets",
                "\"status\":\"Matches tickets in this status.\",\"pageNum\":\"paging\"");

        // Both halves present, each from its own source, on one capability.
        assertThat(capability.attestation().parameterMeanings().get("status"))
                .isEqualTo("Matches tickets in this status.");
        assertThat(capability.inputValues().get("status")).isNotEmpty();
        // The set is on the capability, never inside the attested text: an attestation cannot supply,
        // extend, or override it.
        assertThat(capability.attestation().resultSemantic()).doesNotContain("OPEN");
        assertThat(validator.validate(modelOf(root)).isValid()).isTrue();
    }

    // ---------- EI-5, EI-7: refusals, never an unconstrained fallback ----------

    @Test
    void ei5_anEnumThatDoesNotResolveInTreeRefusesRatherThanShippingUnconstrained(@TempDir Path root)
            throws IOException {
        IntakeRefusal refusal = refuse(root, SVC + "byExternal(DayOfWeek)", "searchByDay",
                "\"day\":\"Matches tickets raised on this weekday.\"");

        assertThat(refusal.cause()).isEqualTo(IntakeRefusal.Cause.STRUCTURAL_FRAME);
        assertThat(refusal.detail()).contains("EI-1").contains("does not map to a modeled type");
    }

    @Test
    void ei7_shapeCDeclarationsRefuseToConstrainAndThereforeRefuseTheCapability(@TempDir Path root)
            throws IOException {
        // A @JsonValue on a method with logic: the wire form is not readable.
        assertThat(refuse(root, SVC + "byLogic(LogicMapped)", "searchByLogic",
                "\"value\":\"Matches this value.\"").detail())
                .contains("not a single 'return <field>;'");

        // Two @JsonValue members: which one is the wire form is not readable.
        assertThat(refuse(root, SVC + "byTwo(TwoValues)", "searchByTwo",
                "\"value\":\"Matches this value.\"").detail())
                .contains("2 @JsonValue members");

        // A constant passing a non-literal: the tool will not evaluate it.
        assertThat(refuse(root, SVC + "byNonLiteral(NonLiteral)", "searchByNonLiteral",
                "\"value\":\"Matches this value.\"").detail())
                .contains("non-literal");
    }

    // ---------- EI-6: the one place an unconstrained STRING is the truth ----------

    @Test
    void ei6_aStringParsedIntoAnEnumInTheBodyCarriesNoConstraintAndIsNotRefused(@TempDir Path root)
            throws IOException {
        Capability capability = attest(root, SVC + "byRawStatus(String)", "searchByRawStatus",
                "\"raw\":\"Matches tickets whose status name is this text.\"");

        assertThat(capability.inputs().get("raw")).isEqualTo(Primitive.STRING);
        assertThat(capability.inputValues()).doesNotContainKey("raw");

        // And the ledger names it, so the case is visible rather than silently unconstrained.
        assertThat(provider.scan(new ProviderInput(root)).enumParsedInBody())
                .anySatisfy(note -> assertThat(note)
                        .contains("byRawStatus")
                        .contains("enum-parsed-in-body"));
    }

    // ---------- EI-8, EI-9: the guards ----------

    @Test
    void ei8_theEnumRuleBypassesNothing_aProvableEnumWithNoAttestationStaysRefused(@TempDir Path root)
            throws IOException {
        copyFixture(root);

        ScanReport report = provider.scan(new ProviderInput(root));

        // Every read here is filtered, so with no attestations file nothing is emitted at all. Proving a
        // parameter's values is not a route around the structural rules.
        assertThat(report.model().capabilities()).isEmpty();
        assertThat(report.attestedCount()).isZero();
    }

    @Test
    void ei9_theMisConstrainedGuard_aSameNamedEnumInAnotherContextResolvesByKey(@TempDir Path root)
            throws IOException {
        // Two bounded contexts declare a different Priority. Carrying the wrong context's set would
        // advertise values that application rejects, which is exactly the breach the phase forbids.
        copyTree(COLLISION, root);
        String crm = "com.example.enumcollision.crm.CrmNoteServiceImpl#byPriority(Priority)";
        String support = "com.example.enumcollision.support.SupportNoteServiceImpl#byPriority(Priority)";
        writeAttestations(root, "["
                + entry(crm, "crmByPriority", "\"priority\":\"Matches crm notes at this priority.\"",
                        fingerprint(root, crm)) + ","
                + entry(support, "supportByPriority", "\"priority\":\"Matches support notes.\"",
                        fingerprint(root, support)) + "]");

        ScanReport report = provider.scan(new ProviderInput(root));

        assertThat(report.attestationRefusals()).isEmpty();
        assertThat(named(report, "attested_crmByPriority").inputValues().get("priority"))
                .containsExactly("LOW", "HIGH");
        assertThat(named(report, "attested_supportByPriority").inputValues().get("priority"))
                .containsExactly("URGENT", "ROUTINE");
    }

    @Test
    void rule10_aConstraintedModelValidatesAndTheConstraintIsCoreEnforced(@TempDir Path root)
            throws IOException {
        attest(root, SVC + "byStatus(TicketStatus,Integer)", "searchTickets",
                "\"status\":\"Matches tickets in this status.\",\"pageNum\":\"paging\"");

        assertThat(validator.validate(modelOf(root)).isValid()).isTrue();
    }

    // ---------- helpers ----------

    private Capability attest(Path root, String target, String name, String parameters) throws IOException {
        copyFixture(root);
        writeAttestations(root, "[" + entry(target, name, parameters, fingerprint(root, target)) + "]");
        ScanReport report = provider.scan(new ProviderInput(root));
        assertThat(report.attestationRefusals()).as("expected no intake refusal").isEmpty();
        return named(report, "attested_" + name);
    }

    private IntakeRefusal refuse(Path root, String target, String name, String parameters)
            throws IOException {
        Path scoped = Files.createDirectories(root.resolve(name));
        copyFixture(scoped);
        writeAttestations(scoped, "["
                + entry(target, name, parameters, fingerprint(scoped, target)) + "]");
        ScanReport report = provider.scan(new ProviderInput(scoped));
        assertThat(report.attestedCount()).as("a refused entry produces no capability").isZero();
        assertThat(report.attestationRefusals()).hasSize(1);
        return report.attestationRefusals().get(0);
    }

    private com.tibyaan.core.ir.CapabilityModel modelOf(Path root) {
        return provider.scan(new ProviderInput(root)).model();
    }

    private static Capability named(ScanReport report, String name) {
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
        Files.copy(FIXTURE, root.resolve("EnumInputs.java"));
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (var paths = Files.walk(from)) {
            for (Path path : paths.toList()) {
                Path destination = to.resolve(from.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination);
                }
            }
        }
    }

    private static void writeAttestations(Path root, String json) throws IOException {
        Path dir = root.resolve("tibyan");
        Files.createDirectories(dir);
        Files.write(dir.resolve("attestations.json"), json.getBytes(StandardCharsets.UTF_8));
    }

    private static String entry(String target, String name, String parameters, String fingerprint) {
        return "{\"target\":\"" + target + "\",\"category\":\"predicate\",\"name\":\"" + name + "\","
                + "\"resultSemantic\":\"Returns the matching tickets.\","
                + "\"parameters\":{" + parameters + "},"
                + "\"provenance\":{\"assertedBy\":\"a.elseginy\",\"origin\":\"human-authored\","
                + "\"date\":\"2026-09-01\",\"fingerprint\":\"" + fingerprint + "\"}}";
    }
}
