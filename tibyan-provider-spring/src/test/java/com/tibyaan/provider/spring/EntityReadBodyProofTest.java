package com.tibyaan.provider.spring;

import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.provider.spring.EntityOutcome.Cause;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase Read-Identity, the fixtures for the by-primary-key body proof on the entity-returning path
 * (Volume 5, section 4.1.1). These fixtures live in test resources only and are never compiled; the
 * provider reads them as source, exactly as it reads a real app.
 *
 * <p>The phase exists because section 4.1 matched on SIGNATURE alone, so the by-primary-key fact
 * section 4.2 calls the safety-critical discriminator was never established on this path. RID-4 is
 * the mis-bind that proved it, reduced to its structure from a real application. The rest of the
 * refusals are the surrounding conditions, and the three positives are the shapes that had to keep
 * discovering, because this rule may only remove candidates, never add one.
 */
class EntityReadBodyProofTest {

    private static final Path FIXTURES = Path.of("src", "test", "resources", "fixtures");

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();

    // ---------- The positives: what the proof must keep (section 4.1.1, conditions 8 and 9) ----------

    @Test
    void positives_theThreeProvableEntityReturningShapesStillDiscover() {
        ScanReport report = provider.scan(input("rid-discovered"));
        CapabilityModel model = report.model();

        // RID-1 direct 4a fused, RID-2 one-hop 4b two-statement, RID-3 the private-helper hop.
        assertThat(model.capabilities()).extracting(Capability::name)
                .containsExactlyInAnyOrder("getDepot", "getPallet", "getCrate");
        assertThat(report.discoveredCount()).isEqualTo(3);
        assertThat(report.failedCount()).isZero();

        assertThat(named(model, "getDepot").binding().value()).isEqualTo("rid.discovered.r1.DepotService#getById");
        assertThat(named(model, "getPallet").binding().value()).isEqualTo("rid.discovered.r2.PalletService#getById");
        assertThat(named(model, "getCrate").binding().value()).isEqualTo("rid.discovered.r3.CrateService#getById");
    }

    @Test
    void positives_theExplanationCarriesTheLoadEvidenceNotJustTheReturnType() {
        // Before section 4.1.1 the entity-returning explanation was "returns the entity X directly"
        // and nothing more, because nothing more had been established. It now carries the proof.
        ScanReport report = provider.scan(input("rid-discovered"));

        assertThat(detail(report, "Depot"))
                .contains("returns the entity Depot directly (section 4.1)")
                .contains("by primary key")
                .contains("DepotJpaRepo");
        assertThat(detail(report, "Pallet"))
                .contains("PalletRepositoryImpl")
                .contains("PalletJpaRepo#findById");
    }

    // ---------- The refusals, one per condition (section 4.1.1, conditions 5 to 9) ----------

    @Test
    void rid4_theMisBindRefuses_aFilterByForeignKeyIsNotAByPrimaryKeyRead() {
        // The reason the phase exists. The signature fits section 4.1 exactly; the body does not read
        // the entity by its primary key, so there is no read-by-id here to discover.
        ScanReport report = provider.scan(input("rid-refused"));

        assertThat(report.model().capabilities()).extracting(Capability::name)
                .doesNotContain("getCustomerProduct");
        assertThat(outcome(report, "CustomerProduct").cause()).isEqualTo(Cause.NO_BACKING_METHOD);
        assertThat(detail(report, "CustomerProduct"))
                .contains("CustomerProductService#validateExistence")
                .contains("condition 4");
    }

    @Test
    void rid5to9_everyOtherConditionRefusesWithItsOwnLocatedReason() {
        ScanReport report = provider.scan(input("rid-refused"));

        // Condition 6, the write set: a save on a repository field is not a read.
        assertThat(detail(report, "Ledger")).contains("mutation 'save'").contains("condition 6");

        // Condition 5, exactly one persistence access: aggregate enrichment is an AT-2 shape.
        assertThat(detail(report, "Invoice"))
                .contains("more than one repository access")
                .contains("condition 5");

        // Condition 7, the id verbatim.
        assertThat(detail(report, "Ticket")).contains("verbatim").contains("condition 7");

        // Condition 8, and by KEY: a by-primary-key read of the wrong entity is still wrong.
        assertThat(detail(report, "Car")).contains("'Wheel'").contains("condition 4");

        // Condition 9, the identity shape: a navigation returns a different instance, and a post-load
        // guard is a semantic the structure cannot carry.
        assertThat(detail(report, "Node")).contains("condition 9a");
        assertThat(detail(report, "Badge")).contains("condition 9");
    }

    @Test
    void refusals_nothingInTheRefusedFixtureEverBinds() {
        // The governing bias, asserted as a whole: every one of these is a genuine near miss, and the
        // provider emits nothing rather than emit a plausible binding for any of them.
        ScanReport report = provider.scan(input("rid-refused"));

        assertThat(report.model().capabilities()).isEmpty();
        assertThat(report.discoveredCount()).isZero();
        assertThat(report.failuresByCause()).containsOnlyKeys(Cause.NO_BACKING_METHOD);
    }

    private ProviderInput input(String fixture) {
        Path source = FIXTURES.resolve(fixture);
        assertThat(Files.isDirectory(source)).as("fixture at " + source.toAbsolutePath()).isTrue();
        return new ProviderInput(source);
    }

    private static Capability named(CapabilityModel model, String name) {
        return model.capabilities().stream()
                .filter(capability -> capability.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no capability named " + name
                        + "; capabilities were " + model.capabilities().stream().map(Capability::name).toList()));
    }

    private static EntityOutcome outcome(ScanReport report, String entityName) {
        return report.entityOutcomes().stream()
                .filter(o -> o.entityName().equals(entityName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no outcome for entity " + entityName));
    }

    private static String detail(ScanReport report, String entityName) {
        return outcome(report, entityName).detail();
    }
}
