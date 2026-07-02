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
}
