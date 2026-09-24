package com.tibyaan.backend.openapi;

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
 * Carriage-exactness of an attested capability on the OpenAPI surface (Volume 6 section 4.4), fixtures
 * ATT-4 and ATT-5. The path derives from the minted name (never the entity), the operationId and summary
 * carry the class, the machine extension carries the component character-exact, every parameter carries
 * its meaning, and a document with no attested capability is byte-identical (asserted by the goldens).
 */
class AttestedOpenApiCarriageTest {

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
                true, new BindingRef("com.example.TenantServiceImpl#getAllByFilter"), List.of(), attestation);
        Entity tenant = new Entity("Tenant", "Tenant",
                List.of(Field.of("id", Primitive.LONG, false)), List.of(), Metadata.empty());
        return new CapabilityModel("pms", "0.1", List.of(tenant), List.of(attested), List.of(),
                Metadata.empty());
    }

    @Test
    void theAttestedOperationCarriesThePathClassAndComponentCharacterExact() {
        String json = OpenApiDocument.toJson(attestedModel());

        // The path derives from the minted name, never the subject entity.
        assertThat(json).contains("\"/attested/searchTenants\"");
        assertThat(json).doesNotContain("\"/tenant\"");
        assertThat(json).contains("\"operationId\": \"attested_searchTenants\"");
        assertThat(json).contains("\"summary\": \"Human-attested, not tool-verified. " + RESULT_SEMANTIC
                + " Asserted by a.elseginy (human-authored), 2026-07-24.\"");
        assertThat(json).contains("\"x-tibyan-attested\": true");
        assertThat(json).contains("\"category\": \"predicate\"");
        assertThat(json).contains("\"fingerprint\": \"sha256:abc123\"");
        // Every parameter carries its meaning; the paging mark travels as its fixed description.
        assertThat(json).contains("\"description\": \"" + FULL_NAME_MEANING + "\"");
        assertThat(json).contains("\"description\": \"Paging or ordering parameter.\"");
        assertThat(json).contains("\"in\": \"query\"");
    }
}
