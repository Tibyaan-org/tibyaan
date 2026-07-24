package com.tibyaan.provider.spring;

import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.Type;
import com.tibyaan.core.semantic.SemanticValidator;
import com.tibyaan.plugin.ProviderInput;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase AT-0, E6a: the envelope transparency rule (Volume 5, section 4.11.7), pre-registered fixtures
 * E6a-1..E6a-5. E6a makes a read hidden behind a wrapper VISIBLE and classified; it grants no discovery.
 * The only discovery is a clean unfiltered findAll behind a generic wrapper of the entity (E6a-1); every
 * other wrapper read is visible-but-deferred as filtered (E6a-2, E6a-4), or refused OPAQUE_WRAPPER when
 * its tie to an entity is not structurally provable (E6a-3). The tie is never read from the wrapper's
 * fields, and never trusted from its name (E6a-4, the name trap).
 */
class EnvelopeTransparencyTest {

    private static final Path E6A = Path.of("src", "test", "resources", "fixtures", "e6a");

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();
    private final SemanticValidator validator = new SemanticValidator();

    @Test
    void e6a1_aCleanFindAllBehindAGenericWrapperOfTheEntityDiscoversAListRead() {
        ScanReport report = scan();

        Capability listBoxed = report.model().capabilities().stream()
                .filter(capability -> capability.name().equals("listBoxed"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("E6a-1 must discover listBoxed; capabilities were "
                        + report.model().capabilities().stream().map(Capability::name).toList()));
        assertThat(listBoxed.binding().value()).isEqualTo("BoxedService#getAllBoxed");
        assertThat(listBoxed.output()).isEqualTo(Type.entityRef("Boxed"));
        assertThat(listBoxed.outputMultiple()).isTrue();
        assertThat(validator.validate(report.model()).isValid()).isTrue();
    }

    @Test
    void e6a2_aFilteredWrapperReadIsVisibleButDeferred_neverDiscovered() {
        ScanReport report = scan();

        // Visible: it now appears as a named deferral, no longer silently unseen.
        assertThat(deferralMentioning(report, "getLeases"))
                .isPresent()
                .get()
                .satisfies(deferred -> assertThat(deferred.reason())
                        .contains("read of Lease")
                        .contains("E6a"));
        // Not discovered: no list capability for Lease is emitted.
        assertThat(listCapabilityNames(report)).doesNotContain("listLease");
    }

    @Test
    void e6a3_aWrapperWithNoProvableEntityTieIsRefusedOpaque() {
        ScanReport report = scan();

        assertThat(deferralMentioning(report, "getGhosts"))
                .isPresent()
                .get()
                .satisfies(deferred -> assertThat(deferred.reason()).contains("OPAQUE_WRAPPER"));
        assertThat(listCapabilityNames(report)).doesNotContain("listGhost");
    }

    @Test
    void e6a4_theNameTrap_theTieIsToTheRepositorysEntityNeverTheWrappersName() {
        ScanReport report = scan();

        // The wrapper is named TenantResultSet, but the read is classified as a read of Building, the
        // entity its repository access proves. Never Tenant, never discovered.
        assertThat(deferralMentioning(report, "getBuildings"))
                .isPresent()
                .get()
                .satisfies(deferred -> assertThat(deferred.reason()).contains("read of Building"));
        assertThat(listCapabilityNames(report)).doesNotContain("listTenant");
    }

    @Test
    void e6a5_misClassificationGuard_onlyTheCleanReadDiscovers_everyEntityIsItsOwn() {
        ScanReport report = scan();

        // Across the whole fixture set, exactly one list read discovers (E6a-1), and it outputs its own
        // entity. No filtered or opaque wrapper read leaks into a discovery, and no read is bound to a
        // sibling entity's name.
        assertThat(listCapabilityNames(report)).containsExactly("listBoxed");
    }

    // ---------- helpers ----------

    private ScanReport scan() {
        assertThat(Files.isDirectory(E6A)).as("fixture at " + E6A.toAbsolutePath()).isTrue();
        return provider.scan(new ProviderInput(E6A));
    }

    private static List<String> listCapabilityNames(ScanReport report) {
        return report.model().capabilities().stream()
                .map(Capability::name)
                .filter(name -> name.startsWith("list"))
                .toList();
    }

    private static Optional<DeferredListRead> deferralMentioning(ScanReport report, String needle) {
        return report.deferredListReads().stream()
                .filter(deferred -> deferred.method().contains(needle))
                .findFirst();
    }
}
