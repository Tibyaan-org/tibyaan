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
        return Attestation.data(AttestationCategory.PREDICATE, "Returns the tenants matching the filters.",
                parameterMeanings, null, "a.elseginy", AttestationOrigin.HUMAN_AUTHORED, "2026-07-24",
                "sha256:abc");
    }

    // ---------- rule 11, the AT-3 boundary rules (Volume 5 section 4.15.2.3) ----------

    @Test
    void rule11_aBoundaryAttestationWithoutASecurityOwnerFails() {
        Capability cap = boundaryCapability(null, true, List.of());

        assertThat(validator.validate(modelWith(cap)).errors())
                .anyMatch(e -> e.contains("securityOwner") && e.contains("rule 11"));
    }

    @Test
    void rule11_aBoundaryAttestationThatAssertsNothingFails() {
        // A security owner who signs for nothing: neither read-only-ness nor any authority.
        Capability cap = boundaryCapability("s.owner", false, List.of());

        assertThat(validator.validate(modelWith(cap)).errors())
                .anyMatch(e -> e.contains("asserts nothing") && e.contains("rule 11"));
    }

    @Test
    void rule11_aDataAttestationCarryingSecurityFieldsFails() {
        // The other direction, which is what keeps the two classes from blurring: a predicate attestation
        // wearing a security owner would read as a security claim to any consumer.
        Attestation predicateWithOwner = new Attestation(AttestationCategory.PREDICATE,
                "Returns the tenants matching the filters.", meanings("fullName", "Matches the name."),
                null, "a.elseginy", AttestationOrigin.HUMAN_AUTHORED, "2026-09-01", "sha256:abc",
                "s.owner", false, List.of());
        Capability cap = Capability.attested("attested_searchTenants",
                Map.of("fullName", Primitive.STRING), Type.entityRef("Tenant"), true,
                new BindingRef("TenantServiceImpl#getAllByFilter"), List.of(), predicateWithOwner);

        assertThat(validator.validate(modelWith(cap)).errors())
                .anyMatch(e -> e.contains("securityOwner") && e.contains("rule 11"));
    }

    @Test
    void rule11_theContradictionGuardRefusesACompiledAndAnAssertedContractTogether() {
        // AT-3 member C1. Section 4.14's composition runs AFTER attestation intake, so this is the only
        // place the two axes can be compared. If the tool PROVED a contract for this read, a human
        // assertion over it is not carried alongside it: the model fails loud rather than shipping two
        // answers to one question.
        Attestation boundary = new Attestation(AttestationCategory.BOUNDARY, "Returns the tenant.",
                meanings("id", Attestation.ID_MARK), "Tenant", "a.elseginy",
                AttestationOrigin.HUMAN_AUTHORED, "2026-09-01", "sha256:abc",
                "s.owner", true, List.of("TENANT_READ"));
        Capability cap = new Capability("attested_boundary_getTenant", CapabilityKind.READ,
                Map.of("id", Primitive.LONG), Type.entityRef("Tenant"), false, true,
                new BindingRef("TenantServiceImpl#getRemote"),
                List.of(new com.tibyaan.core.ir.Annotation("requiredAuthorities", List.of("TENANT_READ"))),
                boundary, Map.of(), null, java.util.Set.of());

        assertThat(validator.validate(modelWith(cap)).errors())
                .anyMatch(e -> e.contains("contradiction guard") && e.contains("rule 11"));
    }

    @Test
    void rule11_aBoundaryAttestationAssertingOnlyAuthoritiesIsValid() {
        // C1 without C2's affirmation: a security assertion is an assertion, so the entry stands.
        Capability cap = boundaryCapability("s.owner", false, List.of("TENANT_READ", "TENANT_ADMIN"));

        assertThat(validator.validate(modelWith(cap)).isValid()).isTrue();
    }

    private static Capability boundaryCapability(String securityOwner, boolean readOnlyAffirmed,
                                                 List<String> assertedAuthorities) {
        Attestation boundary = new Attestation(AttestationCategory.BOUNDARY, "Returns the tenant.",
                meanings("id", Attestation.ID_MARK), "Tenant", "a.elseginy",
                AttestationOrigin.HUMAN_AUTHORED, "2026-09-01", "sha256:abc",
                securityOwner, readOnlyAffirmed, assertedAuthorities);
        return Capability.attested("attested_boundary_getTenant", Map.of("id", Primitive.LONG),
                Type.entityRef("Tenant"), false, new BindingRef("TenantServiceImpl#getRemote"),
                List.of(), boundary);
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
        Attestation shape = Attestation.data(AttestationCategory.SHAPE, "Returns the building, enriched.",
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
        Capability cap = Capability.of("attested_doThing", CapabilityKind.WRITE, inputs,
                Type.entityRef("Tenant"), false, false, new BindingRef("X#y"), List.of(),
                predicate(meanings("fullName", "Matches the full name.")));

        assertThat(validator.validate(modelWith(cap)).errors())
                .anyMatch(e -> e.contains("attested_doThing") && e.contains("write or action")
                        && e.contains("rule 8"));
    }
}
