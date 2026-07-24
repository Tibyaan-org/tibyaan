package com.tibyaan.runtime.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tibyaan.plugin.ExecutionAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The runtime's attested-exposure controls (Volume 5 section 4.15.7) and manifest loader tolerance
 * (Volume 6 section 3.6), fixture the G8 default. A verified tool always registers; an attested tool
 * registers under the default policy; an attested-and-security-unverifiable tool defaults CLOSED. The
 * loader tolerates the new attested/attestation/per-input-description fields.
 */
class AttestedRegistrationTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutionAdapter noop = (ref, arguments) -> null;

    private static CapabilityDescriptor verified() {
        return new CapabilityDescriptor("getProduct", "Read Product.", "ProductService#findById", "Product",
                List.of(new InputDescriptor("id", "LONG")));
    }

    private static CapabilityDescriptor attested(boolean securityUnverifiable) {
        return new CapabilityDescriptor("attested_searchTenants", "Human-attested, not tool-verified.",
                "TenantServiceImpl#getAllByFilter", "Tenant",
                List.of(new InputDescriptor("fullName", "STRING")), true, securityUnverifiable);
    }

    private List<String> registeredNames(CapabilitiesManifest manifest, boolean exposeAttested,
                                         boolean exposeSecurityUnverifiable) {
        List<ToolCallback> callbacks = new ToolRegistrar(mapper, exposeAttested, exposeSecurityUnverifiable)
                .register(manifest, noop);
        return callbacks.stream().map(cb -> cb.getToolDefinition().name()).toList();
    }

    @Test
    void underTheDefaultPolicyAVerifiedAndAPlainAttestedToolBothRegister() {
        CapabilitiesManifest manifest = new CapabilitiesManifest("m", "0.1",
                List.of(verified(), attested(false)));
        assertThat(registeredNames(manifest, true, false))
                .containsExactly("getProduct", "attested_searchTenants");
    }

    @Test
    void g8_anAttestedSecurityUnverifiableToolDefaultsClosed() {
        CapabilitiesManifest manifest = new CapabilitiesManifest("m", "0.1",
                List.of(verified(), attested(true)));
        // Default: the security-unverifiable attested tool is excluded; the verified tool still registers.
        assertThat(registeredNames(manifest, true, false)).containsExactly("getProduct");
        // Explicit opt-in exposes it.
        assertThat(registeredNames(manifest, true, true))
                .containsExactly("getProduct", "attested_searchTenants");
    }

    @Test
    void exposeAttestedFalseExcludesAllAttestedToolsButKeepsVerified() {
        CapabilitiesManifest manifest = new CapabilitiesManifest("m", "0.1",
                List.of(verified(), attested(false), attested(true)));
        assertThat(registeredNames(manifest, false, false)).containsExactly("getProduct");
    }

    @Test
    void theLoaderToleratesTheNewAttestedFields() throws Exception {
        String json = "{\"module\":\"pms\",\"version\":\"0.1\",\"tools\":[{"
                + "\"name\":\"attested_searchTenants\",\"description\":\"Human-attested, not tool-verified.\","
                + "\"binding\":\"TenantServiceImpl#getAllByFilter\",\"output\":\"ENTITY_REF(Tenant)\","
                + "\"outputMultiple\":true,\"attested\":true,"
                + "\"attestation\":{\"category\":\"predicate\",\"resultSemantic\":\"x\","
                + "\"assertedBy\":\"a\",\"origin\":\"human-authored\",\"date\":\"2026-07-24\","
                + "\"fingerprint\":\"sha256:abc\"},"
                + "\"inputs\":[{\"name\":\"fullName\",\"type\":\"STRING\","
                + "\"description\":\"Matches the full name.\"}]}]}";

        CapabilitiesManifest manifest = mapper.readValue(json, CapabilitiesManifest.class);
        assertThat(manifest.tools()).hasSize(1);
        CapabilityDescriptor descriptor = manifest.tools().get(0);
        assertThat(descriptor.name()).isEqualTo("attested_searchTenants");
        assertThat(descriptor.attested()).isTrue();
        assertThat(descriptor.inputs()).hasSize(1);
        assertThat(descriptor.inputs().get(0).name()).isEqualTo("fullName");
    }
}
