package com.tibyaan.backend.mcp;

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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Carriage-exactness of an attested capability on the MCP manifest surface (Volume 6 section 3.6),
 * fixtures ATT-4 and ATT-5. Every asserted string appears character-exact; the class label leads the
 * description; every input carries its meaning; and a verified capability gains no attested field. Drift
 * hides in the last surface nobody checks, so the manifest is checked directly.
 */
class AttestedManifestCarriageTest {

    private static final String RESULT_SEMANTIC = "Returns the tenants matching the given filters.";
    private static final String FULL_NAME_MEANING =
            "Matches tenants whose full name contains this text, case-insensitive.";

    private static CapabilityModel attestedModel() {
        Map<String, Type> inputs = new LinkedHashMap<>();
        inputs.put("fullName", Primitive.STRING);
        inputs.put("pageNum", Primitive.INTEGER);
        Map<String, String> meanings = new LinkedHashMap<>();
        meanings.put("fullName", FULL_NAME_MEANING);
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
    void theAttestedToolCarriesTheClassNameLabelClaimAndProvenanceCharacterExact() {
        String json = ManifestWriter.toJson(attestedModel());

        assertThat(json).contains("\"name\": \"attested_searchTenants\"");
        assertThat(json).contains("\"description\": \"Human-attested, not tool-verified. " + RESULT_SEMANTIC
                + " Asserted by a.elseginy (human-authored), 2026-07-24.\"");
        assertThat(json).contains("\"attested\": true");
        assertThat(json).contains("\"category\": \"predicate\"");
        assertThat(json).contains("\"resultSemantic\": \"" + RESULT_SEMANTIC + "\"");
        assertThat(json).contains("\"assertedBy\": \"a.elseginy\"");
        assertThat(json).contains("\"origin\": \"human-authored\"");
        assertThat(json).contains("\"fingerprint\": \"sha256:abc123\"");
        // Every input carries its meaning; the paging mark travels as its fixed description.
        assertThat(json).contains("\"description\": \"" + FULL_NAME_MEANING + "\"");
        assertThat(json).contains("\"description\": \"Paging or ordering parameter.\"");
    }

    @Test
    void att5_aVerifiedToolGainsNoAttestedField() {
        Entity product = new Entity("Product", "Product",
                List.of(Field.of("id", Primitive.LONG, false)), List.of(), Metadata.empty());
        Map<String, Type> inputs = new LinkedHashMap<>();
        inputs.put("id", Primitive.LONG);
        Capability verified = Capability.read("getProduct", inputs, Type.entityRef("Product"),
                new BindingRef("ProductService#findById"));
        CapabilityModel model = new CapabilityModel("shop", "0.1", List.of(product), List.of(verified),
                List.of(), Metadata.empty());

        String json = ManifestWriter.toJson(model);
        assertThat(json).doesNotContain("attested");
        assertThat(json).doesNotContain("attestation");
        assertThat(json).doesNotContain("Human-attested");
    }
}
