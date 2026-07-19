package com.tibyaan.runtime.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The runtime tolerates manifest fields it does not act on (Volume 6 section 3.1). The backend may
 * add a {@code view} field to a tool; the loader reads only the fields it knows and ignores the
 * rest. A deliberately strict {@link ObjectMapper} (unknown properties fail by default) proves the
 * tolerance is intrinsic to the manifest types, not a property of the host's Jackson configuration.
 */
class CapabilitiesLoaderTest {

    private static final String MANIFEST_WITH_VIEW = """
            {
              "module": "pms-backend",
              "version": "0.1",
              "tools": [
                {
                  "name": "getUnit",
                  "description": "Read Unit by id. Returns the UnitVTO view of Unit.",
                  "binding": "UnitServiceImpl#findById",
                  "output": "ENTITY_REF(Unit)",
                  "view": "UnitVTO",
                  "inputs": [
                    { "name": "id", "type": "LONG" }
                  ]
                }
              ]
            }
            """;

    // A tool carrying the phase Controller-Authority security fields (Volume 6 section 3.5): the runtime
    // does not act on them and does not validate anything (enforcement is the consumer's layer, Volume 7
    // section 8 gap 9), so the loader must read the fields it knows and ignore requiredAuthorities and
    // securityUnverifiable regardless of the host's Jackson configuration.
    private static final String MANIFEST_WITH_SECURITY = """
            {
              "module": "secured-app",
              "version": "0.1",
              "tools": [
                {
                  "name": "getWarehouse",
                  "description": "Read Warehouse by id. Requires any of the declared authorities: 480012, 480013.",
                  "binding": "WarehouseService#findById",
                  "output": "ENTITY_REF(Warehouse)",
                  "requiredAuthorities": ["480012", "480013"],
                  "inputs": [
                    { "name": "id", "type": "LONG" }
                  ]
                },
                {
                  "name": "getReport",
                  "description": "Read Report by id. Carries a declared security contract that could not be verified.",
                  "binding": "ReportService#findById",
                  "output": "ENTITY_REF(Report)",
                  "securityUnverifiable": true,
                  "inputs": [
                    { "name": "id", "type": "LONG" }
                  ]
                }
              ]
            }
            """;

    @Test
    void loadsAManifestWithAViewFieldAndIgnoresIt() {
        CapabilitiesLoader loader = new CapabilitiesLoader(new ObjectMapper());

        CapabilitiesManifest manifest = loader.load(
                new ByteArrayResource(MANIFEST_WITH_VIEW.getBytes(StandardCharsets.UTF_8)));

        assertThat(manifest.tools()).singleElement().satisfies(tool -> {
            assertThat(tool.name()).isEqualTo("getUnit");
            assertThat(tool.binding()).isEqualTo("UnitServiceImpl#findById");
            assertThat(tool.output()).isEqualTo("ENTITY_REF(Unit)");
            assertThat(tool.inputs()).singleElement().satisfies(input -> {
                assertThat(input.name()).isEqualTo("id");
                assertThat(input.type()).isEqualTo("LONG");
            });
        });
    }

    @Test
    void loadsAManifestWithSecurityFieldsAndIgnoresThem() {
        // A deliberately strict mapper (unknown properties fail by default) proves the tolerance is
        // intrinsic to the manifest types. The runtime loads the tools and enforces nothing this phase.
        CapabilitiesLoader loader = new CapabilitiesLoader(new ObjectMapper());

        CapabilitiesManifest manifest = loader.load(
                new ByteArrayResource(MANIFEST_WITH_SECURITY.getBytes(StandardCharsets.UTF_8)));

        assertThat(manifest.tools()).hasSize(2);
        assertThat(manifest.tools()).extracting(CapabilityDescriptor::name)
                .containsExactly("getWarehouse", "getReport");
        assertThat(manifest.tools().get(0).binding()).isEqualTo("WarehouseService#findById");
        assertThat(manifest.tools().get(1).binding()).isEqualTo("ReportService#findById");
    }
}
