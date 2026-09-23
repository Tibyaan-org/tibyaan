package com.tibyaan.runtime.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tibyaan.plugin.ExecutionAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Per-capability exposure of security-unverifiable tools (Volume 7 section 10, RATIFIED 2026-09-23; the
 * property amended to {@code tibyan.security.exposeUnverifiable} for any tier by DL-1 decision D1). The
 * pre-registered fixtures RX-1 to RX-9 of section 10.7; RX-10, the two binding forms, is in
 * {@link ExposurePolicyBindingTest}.
 *
 * <p>The defect this closes: the only control over an unverifiable attested capability was capability-wide,
 * so an adopter who reviewed fifteen by name had to open every one attested next month by anyone. The list
 * is exact names, nothing else, and every entry must resolve and be needed, or the host does not start.
 */
class SecurityUnverifiableListTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutionAdapter noop = (ref, arguments) -> null;

    private static final String VERIFIED = "getProduct";
    private static final String CITIES = "attested_citiesByFilters";
    private static final String TENANTS = "attested_searchTenants";
    private static final String PLAIN = "attested_plainRead";
    private static final String REMOTE = "attested_remoteLookup";
    private static final String REVIEWED_READ = "reviewed_findOverdue";

    private static CapabilityDescriptor verified() {
        return new CapabilityDescriptor(VERIFIED, "Read Product.", "ProductService#findById", "Product",
                List.of(new InputDescriptor("id", "LONG")));
    }

    private static CapabilityDescriptor unverifiable(String name) {
        return new CapabilityDescriptor(name, "Human-attested, not tool-verified.", "Svc#m", "Entity",
                List.of(), true, true, false);
    }

    private static CapabilityDescriptor attestedCompiled() {
        return new CapabilityDescriptor(PLAIN, "Human-attested, not tool-verified.", "Svc#plain", "Entity",
                List.of(), true, false, false);
    }

    private static CapabilityDescriptor boundaryUnverifiable() {
        return new CapabilityDescriptor(REMOTE, "Human-attested remote delegation.", "Svc#remote", "Entity",
                List.of(), true, true, true);
    }

    private static CapabilityDescriptor reviewedUnverifiableRead() {
        return CapabilityDescriptor.reviewed(REVIEWED_READ,
                "Reviewed by a.reviewer (human), not tool-verified; the tool labeled it READ. Lists overdue.",
                "InvoiceService#findOverdue", "Invoice", List.of(), CapabilityDescriptor.KIND_READ, true,
                new ReviewDescriptor("READ", "a.reviewer", "2026-09-23", "human"));
    }

    private static CapabilitiesManifest twoUnverifiable() {
        return new CapabilitiesManifest("m", "0.1", List.of(verified(), unverifiable(CITIES), unverifiable(TENANTS)));
    }

    private static ExposurePolicy policy(boolean globalSwitch, List<String> list) {
        return new ExposurePolicy(true, globalSwitch, false, true, List.of(), list);
    }

    private static ExposurePolicy policy(boolean exposeAttested, boolean globalSwitch, boolean exposeBoundary,
            List<String> list) {
        return new ExposurePolicy(exposeAttested, globalSwitch, exposeBoundary, true, List.of(), list);
    }

    private ToolRegistrar.Registration register(CapabilitiesManifest manifest, ExposurePolicy policy) {
        return new ToolRegistrar(mapper, policy).registration(manifest, noop);
    }

    @Test
    void rx1_theListedUnverifiableToolRegistersAndTheUnlistedOneDoesNot() {
        ToolRegistrar.Registration registration = register(twoUnverifiable(), policy(false, List.of(CITIES)));

        assertThat(registration.registeredNames()).containsExactly(VERIFIED, CITIES);
        assertThat(registration.excluded())
                .containsExactly(java.util.Map.entry(TENANTS, ToolRegistrar.SECURITY_UNVERIFIABLE_NOT_REVIEWED));
        // Nothing the global switch alone opened, so nothing to warn about.
        assertThat(registration.openedByGlobalSwitchOnly()).isEmpty();
    }

    @Test
    void rx2_theG8DefaultHolds_noListAndGlobalFalseRegistersNeither() {
        ToolRegistrar.Registration registration = register(twoUnverifiable(), policy(false, List.of()));

        assertThat(registration.registeredNames()).containsExactly(VERIFIED);
        assertThat(registration.excluded()).containsOnlyKeys(CITIES, TENANTS);
        assertThat(registration.excluded().values())
                .containsOnly(ToolRegistrar.SECURITY_UNVERIFIABLE_NOT_REVIEWED);
    }

    @Test
    void rx3_theGlobalSwitchStillOpensBothAndTheWarnNamesBoth() {
        ToolRegistrar.Registration registration = register(twoUnverifiable(), policy(true, List.of()));

        assertThat(registration.registeredNames()).containsExactly(VERIFIED, CITIES, TENANTS);
        assertThat(registration.excluded()).isEmpty();
        // Exactly the ones nobody named (Volume 7 section 10.6).
        assertThat(registration.openedByGlobalSwitchOnly()).containsExactly(CITIES, TENANTS);
    }

    @Test
    void rx3b_withTheGlobalSwitchOnTheWarnOmitsAListedName() {
        ToolRegistrar.Registration registration = register(twoUnverifiable(), policy(true, List.of(CITIES)));

        assertThat(registration.registeredNames()).containsExactly(VERIFIED, CITIES, TENANTS);
        assertThat(registration.openedByGlobalSwitchOnly()).containsExactly(TENANTS);
    }

    @Test
    void rx4_aListedNameNeverOpensASecondUnlistedUnverifiableTool() {
        ToolRegistrar.Registration registration = register(twoUnverifiable(), policy(false, List.of(TENANTS)));

        assertThat(registration.registeredNames()).containsExactly(VERIFIED, TENANTS);
        assertThat(registration.excluded()).containsKey(CITIES);
    }

    @Test
    void rx5_noPatterns_aWildcardAPrefixAndAWrongCaseNameEachFailStartupAsUnknown() {
        for (String entry : List.of("attested_*", "attested_", "ATTESTED_CITIESBYFILTERS", "attested_citiesbyfilters")) {
            assertThatThrownBy(() -> register(twoUnverifiable(), policy(false, List.of(entry))))
                    .as("entry %s", entry)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(ExposurePolicy.SECURITY_EXPOSE_UNVERIFIABLE)
                    .hasMessageContaining("'" + entry + "'")
                    .hasMessageContaining("names no capability");
        }
    }

    @Test
    void rx6_x2_anUnknownNameFailsStartupNamingTheEntry() {
        assertThatThrownBy(() -> register(twoUnverifiable(), policy(false, List.of("attested_ghost"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ExposurePolicy.SECURITY_EXPOSE_UNVERIFIABLE)
                .hasMessageContaining("'attested_ghost'")
                .hasMessageContaining("names no capability in the loaded manifest");
    }

    @Test
    void rx7_x3_aListedVerifiedOrCompiledContractToolFailsStartupBecauseTheEntryIsInert() {
        CapabilitiesManifest manifest = new CapabilitiesManifest("m", "0.1",
                List.of(verified(), unverifiable(CITIES), attestedCompiled()));

        assertThatThrownBy(() -> register(manifest, policy(false, List.of(VERIFIED))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'" + VERIFIED + "'")
                .hasMessageContaining("is a verified capability")
                .hasMessageContaining("X3");
        assertThatThrownBy(() -> register(manifest, policy(false, List.of(PLAIN))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'" + PLAIN + "'")
                .hasMessageContaining("attested but not security-unverifiable")
                .hasMessageContaining("X3");
    }

    @Test
    void rx8_x4_theListNeverWidensTheWholesaleOrTheBoundaryControl() {
        // tibyan.attested.expose=false with a populated list: nothing attested registers, listed or not.
        ToolRegistrar.Registration closed =
                register(twoUnverifiable(), policy(false, false, false, List.of(CITIES, TENANTS)));
        assertThat(closed.registeredNames()).containsExactly(VERIFIED);
        assertThat(closed.excluded().values()).containsOnly(ToolRegistrar.ATTESTED_CLOSED);

        // A listed boundary tool still needs exposeBoundary; the list is not a substitute for it.
        CapabilitiesManifest withBoundary = new CapabilitiesManifest("m", "0.1",
                List.of(verified(), boundaryUnverifiable()));
        ToolRegistrar.Registration boundaryClosed =
                register(withBoundary, policy(true, false, false, List.of(REMOTE)));
        assertThat(boundaryClosed.registeredNames()).containsExactly(VERIFIED);
        assertThat(boundaryClosed.excluded()).containsEntry(REMOTE, ToolRegistrar.BOUNDARY_CLOSED);

        // With the boundary control on as well, the listed boundary tool registers.
        ToolRegistrar.Registration boundaryOpen =
                register(withBoundary, policy(true, false, true, List.of(REMOTE)));
        assertThat(boundaryOpen.registeredNames()).containsExactly(VERIFIED, REMOTE);
    }

    @Test
    void rx9_x5_aBlankAndADuplicateEntryEachFailStartup() {
        assertThatThrownBy(() -> register(twoUnverifiable(), policy(false, List.of(CITIES, ""))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ExposurePolicy.SECURITY_EXPOSE_UNVERIFIABLE)
                .hasMessageContaining("entry 2 is blank")
                .hasMessageContaining("X5");
        assertThatThrownBy(() -> register(twoUnverifiable(), policy(false, List.of(CITIES, "  "))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is blank");
        assertThatThrownBy(() -> register(twoUnverifiable(), policy(false, List.of(CITIES, TENANTS, CITIES))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ExposurePolicy.SECURITY_EXPOSE_UNVERIFIABLE)
                .hasMessageContaining("'" + CITIES + "' is listed more than once")
                .hasMessageContaining("X5");
    }

    // ---------- the D1 amendment: one list, any tier ----------

    @Test
    void d1_aReviewedSecurityUnverifiableReadIsClosedByDefaultAndOpenedByTheSameList() {
        CapabilitiesManifest manifest = new CapabilitiesManifest("m", "0.1",
                List.of(verified(), reviewedUnverifiableRead()));

        ToolRegistrar.Registration closed = register(manifest, policy(false, List.of()));
        assertThat(closed.registeredNames()).containsExactly(VERIFIED);
        assertThat(closed.excluded())
                .containsEntry(REVIEWED_READ, ToolRegistrar.SECURITY_UNVERIFIABLE_NOT_REVIEWED);

        ToolRegistrar.Registration listed = register(manifest, policy(false, List.of(REVIEWED_READ)));
        assertThat(listed.registeredNames()).containsExactly(VERIFIED, REVIEWED_READ);
        assertThat(listed.openedByGlobalSwitchOnly()).isEmpty();

        // The attested-named global switch never opens a reviewed tool: the list is the only door (build
        // decision, Volume 5 section 4.17.13), so nothing was opened by the switch alone.
        ToolRegistrar.Registration global = register(manifest, policy(true, List.of()));
        assertThat(global.registeredNames()).containsExactly(VERIFIED);
        assertThat(global.excluded())
                .containsEntry(REVIEWED_READ, ToolRegistrar.SECURITY_UNVERIFIABLE_NOT_REVIEWED);
        assertThat(global.openedByGlobalSwitchOnly()).isEmpty();
    }

    @Test
    void d1_x3_aListedReviewedToolThatIsNotUnverifiableFailsStartup() {
        CapabilityDescriptor reviewedVerifiableRead = CapabilityDescriptor.reviewed("reviewed_findPaid",
                "Reviewed by a.reviewer (human), not tool-verified; the tool labeled it READ. Lists paid.",
                "InvoiceService#findPaid", "Invoice", List.of(), CapabilityDescriptor.KIND_READ, false,
                new ReviewDescriptor("READ", "a.reviewer", "2026-09-23", "human"));
        CapabilitiesManifest manifest = new CapabilitiesManifest("m", "0.1", List.of(reviewedVerifiableRead));

        assertThatThrownBy(() -> register(manifest, policy(false, List.of("reviewed_findPaid"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'reviewed_findPaid'")
                .hasMessageContaining("reviewed but not security-unverifiable")
                .hasMessageContaining("X3");
    }

    // ---------- the loader reads the additive fields and tolerates their absence ----------

    @Test
    void theLoaderReadsKindTierAndReviewAndDefaultsThemWhenAbsent() throws Exception {
        String json = "{\"module\":\"m\",\"version\":\"0.1\",\"tools\":["
                + "{\"name\":\"getProduct\",\"description\":\"Read Product.\",\"binding\":\"P#f\","
                + "\"output\":\"ENTITY_REF(Product)\",\"inputs\":[]},"
                + "{\"name\":\"reviewed_recordPayment\",\"description\":\"Reviewed by r (human), not "
                + "tool-verified; the tool labeled it WRITE. Records a payment.\",\"binding\":\"L#record\","
                + "\"output\":\"ENTITY_REF(Receipt)\",\"securityUnverifiable\":true,\"kind\":\"WRITE\","
                + "\"tier\":\"reviewed\",\"review\":{\"label\":\"WRITE\",\"reviewedBy\":\"r\","
                + "\"reviewedOn\":\"2026-09-23\",\"descriptionOrigin\":\"ai-draft\",\"extra\":1},"
                + "\"inputs\":[{\"name\":\"note\",\"type\":\"STRING\"}]}]}";

        CapabilitiesManifest manifest = mapper.readValue(json, CapabilitiesManifest.class);

        CapabilityDescriptor verified = manifest.tools().get(0);
        assertThat(verified.kind()).isEqualTo(CapabilityDescriptor.KIND_READ);
        assertThat(verified.tier()).isNull();
        assertThat(verified.review()).isNull();
        assertThat(verified.verified()).isTrue();
        assertThat(verified.reviewed()).isFalse();
        assertThat(verified.mutating()).isFalse();

        CapabilityDescriptor write = manifest.tools().get(1);
        assertThat(write.kind()).isEqualTo(CapabilityDescriptor.KIND_WRITE);
        assertThat(write.tier()).isEqualTo(CapabilityDescriptor.TIER_REVIEWED);
        assertThat(write.reviewed()).isTrue();
        assertThat(write.mutating()).isTrue();
        assertThat(write.securityUnverifiable()).isTrue();
        assertThat(write.review())
                .isEqualTo(new ReviewDescriptor("WRITE", "r", "2026-09-23", "ai-draft"));
    }
}
