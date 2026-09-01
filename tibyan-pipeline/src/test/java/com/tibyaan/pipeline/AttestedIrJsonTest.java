package com.tibyaan.pipeline;

import com.tibyaan.core.ir.Attestation;
import com.tibyaan.core.ir.AttestationCategory;
import com.tibyaan.core.ir.AttestationOrigin;
import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Field;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The serialized IR carriage surface for an attested capability (Volume 4 section 3.8; the carriage-
 * exactness fixture ATT-4 on the IR). The Attestation component round-trips through the intermediate IR
 * JSON character-exact, so {@code generate} carries the human's claim from {@code scan} to the backends;
 * and an attestation-free model serializes byte-identically (ATT-11), because the component is omitted
 * when absent.
 */
class AttestedIrJsonTest {

    private static final String RESULT_SEMANTIC = "Returns the tenants matching the given filters.";

    private static CapabilityModel attestedModel() {
        Map<String, Type> inputs = new LinkedHashMap<>();
        inputs.put("fullName", Primitive.STRING);
        inputs.put("pageNum", Primitive.INTEGER);
        Map<String, String> meanings = new LinkedHashMap<>();
        meanings.put("fullName", "Matches the full name, case-insensitive.");
        meanings.put("pageNum", Attestation.PAGING_MARK);
        Attestation attestation = Attestation.data(AttestationCategory.PREDICATE, RESULT_SEMANTIC, meanings,
                null, "a.elseginy", AttestationOrigin.HUMAN_AUTHORED, "2026-07-24", "sha256:abc123");
        Capability attested = Capability.attested("attested_searchTenants", inputs, Type.entityRef("Tenant"),
                true, new BindingRef("TenantServiceImpl#getAllByFilter"), List.of(), attestation);
        Entity tenant = new Entity("Tenant", "Tenant",
                List.of(Field.of("id", Primitive.LONG, false)), List.of(), Metadata.empty());
        return new CapabilityModel("pms", "0.1", List.of(tenant), List.of(attested), List.of(),
                Metadata.empty());
    }

    @Test
    void theAttestationRoundTripsThroughTheIrJsonCharacterExact(@TempDir Path dir) {
        CapabilityModel original = attestedModel();
        Path file = dir.resolve("attested.ir.json");
        IrJson.write(original, file);

        CapabilityModel back = IrJson.read(file);
        assertThat(back).isEqualTo(original);

        Capability attested = back.capabilities().get(0);
        assertThat(attested.hasAttestation()).isTrue();
        assertThat(attested.attestation().resultSemantic()).isEqualTo(RESULT_SEMANTIC);
        assertThat(attested.attestation().fingerprint()).isEqualTo("sha256:abc123");

        String json = IrJson.writeString(original);
        assertThat(json).contains(RESULT_SEMANTIC).contains("sha256:abc123").contains("attested_searchTenants");
    }

    @Test
    void anAttestationFreeModelOmitsTheComponentEntirely() {
        Entity product = new Entity("Product", "Product",
                List.of(Field.of("id", Primitive.LONG, false)), List.of(), Metadata.empty());
        Capability verified = Capability.read("getProduct", Map.of("id", Primitive.LONG),
                Type.entityRef("Product"), new BindingRef("ProductService#findById"));
        CapabilityModel model = new CapabilityModel("shop", "0.1", List.of(product), List.of(verified),
                List.of(), Metadata.empty());

        assertThat(IrJson.writeString(model)).doesNotContain("attestation");
    }
}
