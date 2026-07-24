package com.tibyaan.core.semantic;

import com.tibyaan.core.ir.Attestation;
import com.tibyaan.core.ir.AttestationCategory;
import com.tibyaan.core.ir.AttestationOrigin;
import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityKind;
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
 * Core validation rules 8 (attestation completeness, keyset equality) and 9 (the reserved {@code
 * attested_} prefix, both directions), Volume 4 section 5 and Volume 5 section 4.15. These are the
 * model-level guards of ZERO ATTESTATION DRIFT: an incomplete or mis-prefixed attestation is caught by
 * {@code tibyan-core} alone, with no provider or backend knowledge. Fixtures ATT-5, ATT-10, ATT-12 and
 * the completeness-as-keyset guard.
 */
class AttestationValidationTest {

    private final SemanticValidator validator = new SemanticValidator();

    private static Attestation predicate(Map<String, String> parameterMeanings) {
        return new Attestation(AttestationCategory.PREDICATE, "Returns the tenants matching the filters.",
                parameterMeanings, null, "a.elseginy", AttestationOrigin.HUMAN_AUTHORED, "2026-07-24",
                "sha256:abc");
    }

    private static CapabilityModel modelWith(Capability capability) {
        Entity tenant = new Entity("Tenant", "Tenant",
                List.of(Field.of("id", Primitive.LONG, false)), List.of(), Metadata.empty());
        return new CapabilityModel("pms", "0.1", List.of(tenant), List.of(capability), List.of(),
                Metadata.empty());
    }

    private static Map<String, String> meanings(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    void aCompleteAttestedCapabilityValidates() {
        Map<String, Type> inputs = new LinkedHashMap<>();
        inputs.put("fullName", Primitive.STRING);
        inputs.put("pageNum", Primitive.INTEGER);
        Capability cap = Capability.attested("attested_searchTenants", inputs, Type.entityRef("Tenant"),
                true, new BindingRef("TenantServiceImpl#getAllByFilter"), List.of(),
                predicate(meanings("fullName", "Matches the full name, case-insensitive.",
                        "pageNum", Attestation.PAGING_MARK)));

        assertThat(validator.validate(modelWith(cap)).isValid()).isTrue();
    }

    @Test
    void rule9_anAttestedCapabilityWithoutThePrefixFails() {
        Map<String, Type> inputs = Map.of("fullName", Primitive.STRING);
        Capability cap = Capability.attested("searchTenants", inputs, Type.entityRef("Tenant"), true,
                new BindingRef("TenantServiceImpl#getAllByFilter"), List.of(),
                predicate(meanings("fullName", "Matches the full name.")));

        assertThat(validator.validate(modelWith(cap)).errors())
                .anyMatch(e -> e.contains("searchTenants") && e.contains("attested_") && e.contains("rule 9"));
    }

    @Test
    void rule9_aVerifiedCapabilityWearingThePrefixFails() {
        Capability cap = Capability.read("attested_getTenant", Map.of("id", Primitive.LONG),
                Type.entityRef("Tenant"), new BindingRef("TenantServiceImpl#findById"));

        assertThat(validator.validate(modelWith(cap)).errors())
                .anyMatch(e -> e.contains("attested_getTenant") && e.contains("no attestation")
                        && e.contains("rule 9"));
    }

    @Test
    void rule8_anUncoveredInputFails() {
        Map<String, Type> inputs = new LinkedHashMap<>();
        inputs.put("fullName", Primitive.STRING);
        inputs.put("email", Primitive.STRING);
        Capability cap = Capability.attested("attested_searchTenants", inputs, Type.entityRef("Tenant"),
                true, new BindingRef("TenantServiceImpl#getAllByFilter"), List.of(),
                predicate(meanings("fullName", "Matches the full name.")));

        assertThat(validator.validate(modelWith(cap)).errors())
                .anyMatch(e -> e.contains("email") && e.contains("uncovered") && e.contains("rule 8"));
    }

    @Test
    void rule8_aMeaningKeyThatIsNotAnInputFails() {
        Map<String, Type> inputs = Map.of("fullName", Primitive.STRING);
        Capability cap = Capability.attested("attested_searchTenants", inputs, Type.entityRef("Tenant"),
                true, new BindingRef("TenantServiceImpl#getAllByFilter"), List.of(),
                predicate(meanings("fullName", "Matches the full name.", "ghost", "Not an input.")));

        assertThat(validator.validate(modelWith(cap)).errors())
                .anyMatch(e -> e.contains("ghost") && e.contains("not one of its") && e.contains("rule 8"));
    }

    @Test
    void rule8_aShapeAttestationWithoutSubjectEntityFails() {
        Map<String, Type> inputs = Map.of("id", Primitive.LONG);
        Attestation shape = new Attestation(AttestationCategory.SHAPE, "Returns the building, enriched.",
                meanings("id", Attestation.ID_MARK), null, "a.elseginy", AttestationOrigin.HUMAN_AUTHORED,
                "2026-07-24", "sha256:abc");
        Capability cap = Capability.attested("attested_getBuilding", inputs, Type.entityRef("Tenant"),
                false, new BindingRef("BuildingServiceImpl#findById"), List.of(), shape);

        assertThat(validator.validate(modelWith(cap)).errors())
                .anyMatch(e -> e.contains("shape") && e.contains("no subjectEntity") && e.contains("rule 8"));
    }

    @Test
    void rule8_anAttestationOnAWriteCapabilityFails() {
        Map<String, Type> inputs = Map.of("fullName", Primitive.STRING);
        Capability cap = new Capability("attested_doThing", CapabilityKind.WRITE, inputs,
                Type.entityRef("Tenant"), false, false, new BindingRef("X#y"), List.of(),
                predicate(meanings("fullName", "Matches the full name.")));

        assertThat(validator.validate(modelWith(cap)).errors())
                .anyMatch(e -> e.contains("attested_doThing") && e.contains("write or action")
                        && e.contains("rule 8"));
    }
}
