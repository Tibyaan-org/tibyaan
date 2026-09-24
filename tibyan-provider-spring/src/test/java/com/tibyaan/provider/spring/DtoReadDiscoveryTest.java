package com.tibyaan.provider.spring;

import com.tibyaan.core.ir.Annotation;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.Type;
import com.tibyaan.provider.spring.EntityOutcome.Cause;
import com.tibyaan.provider.spring.EntityOutcome.Status;
import com.tibyaan.plugin.ProviderInput;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase E2: the ratified DTO-aware read discovery rule (Volume 5 section 4.2), exercised by the
 * pre-registered fixtures F1 to F12. Fixtures are read as source, never compiled.
 */
class DtoReadDiscoveryTest {

    private static final Path DTO = Path.of("src", "test", "resources", "fixtures", "dto");

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();

    @Test
    void f1_directSpringDataFusedFormDiscovers() {
        ScanReport report = scan("f1");
        Capability capability = capability(report, "getWidget");
        assertThat(capability.binding().value()).isEqualTo("com.example.dto.f1.WidgetService#findById");
        assertThat(capability.output()).isEqualTo(Type.entityRef("Widget"));
        assertThat(viewAnnotation(capability)).containsExactly("WidgetVto");
        assertThat(outcome(report, "Widget").status()).isEqualTo(Status.DISCOVERED);
    }

    @Test
    void f2_oneHopDelegationTwoStatementDiscovers() {
        ScanReport report = scan("f2");
        Capability capability = capability(report, "getGadget");
        assertThat(capability.binding().value()).isEqualTo("com.example.dto.f2.GadgetService#findById");
        assertThat(capability.output()).isEqualTo(Type.entityRef("Gadget"));
        assertThat(viewAnnotation(capability)).containsExactly("GadgetVto");
    }

    @Test
    void f3_optionalMapMethodReferenceDiscovers() {
        ScanReport report = scan("f3");
        Capability capability = capability(report, "getThing");
        assertThat(capability.binding().value()).isEqualTo("com.example.dto.f3.ThingService#findById");
        assertThat(viewAnnotation(capability)).containsExactly("ThingVto");
    }

    @Test
    void f4_nameSimilarButStructurallyUnrelatedDoesNotMatch() {
        ScanReport report = scan("f4");
        assertThat(report.model().capabilities()).extracting(Capability::name).doesNotContain("getTenant");
        EntityOutcome outcome = outcome(report, "Tenant");
        assertThat(outcome.status()).isEqualTo(Status.FAILED);
        assertThat(outcome.cause()).isEqualTo(Cause.NO_BACKING_METHOD);
    }

    @Test
    void f5_nonPrimaryKeyLoadRefuses() {
        EntityOutcome outcome = outcome(scan("f5"), "Account");
        assertThat(outcome.cause()).isEqualTo(Cause.NO_BACKING_METHOD);
        assertThat(outcome.detail()).contains("findByUserId").contains("by-primary-key");
    }

    @Test
    void f6_compositeAssemblyRefuses() {
        EntityOutcome outcome = outcome(scan("f6"), "Report");
        assertThat(outcome.cause()).isEqualTo(Cause.NO_BACKING_METHOD);
        assertThat(outcome.detail()).contains("more than one repository access").contains("P2");
    }

    @Test
    void f7_writeInDisguiseRefuses() {
        EntityOutcome outcome = outcome(scan("f7"), "Token");
        assertThat(outcome.cause()).isEqualTo(Cause.NO_BACKING_METHOD);
        assertThat(outcome.detail()).contains("mutation").contains("save");
    }

    @Test
    void f8_customQueryTerminalRefusesWithP1bNearMiss() {
        EntityOutcome outcome = outcome(scan("f8"), "Doc");
        assertThat(outcome.cause()).isEqualTo(Cause.NO_BACKING_METHOD);
        assertThat(outcome.detail()).contains("P1b").contains("findByIdWithRelations");
    }

    @Test
    void f9_navigationArgumentRefuses() {
        EntityOutcome outcome = outcome(scan("f9"), "Holder");
        assertThat(outcome.cause()).isEqualTo(Cause.NO_BACKING_METHOD);
        assertThat(outcome.detail()).contains("verbatim");
    }

    @Test
    void f10_delegationOnlyFacadeRefuses() {
        EntityOutcome outcome = outcome(scan("f10"), "Prod");
        assertThat(outcome.cause()).isEqualTo(Cause.NO_BACKING_METHOD);
        assertThat(outcome.detail()).contains("no persistence access");
    }

    @Test
    void f11_ambiguityAcrossKindsListsBoth() {
        EntityOutcome outcome = outcome(scan("f11"), "Job");
        assertThat(outcome.cause()).isEqualTo(Cause.AMBIGUOUS_BINDING);
        assertThat(outcome.detail())
                .contains("more than one")
                .contains("JobService#getEntity")
                .contains("JobService#findById")
                .contains("entity-returning")
                .contains("view-returning");
    }

    @Test
    void f12_compositeKeyEntitySurfacesTheIdentifierCause() {
        ScanReport report = scan("f12");
        assertThat(report.model().capabilities()).isEmpty();
        EntityOutcome outcome = outcome(report, "Membership");
        assertThat(outcome.status()).isEqualTo(Status.FAILED);
        assertThat(outcome.cause()).isEqualTo(Cause.COMPOSITE_KEY);
        assertThat(outcome.detail()).contains("groupId").contains("userId");
    }

    private ScanReport scan(String fixture) {
        return provider.scan(new ProviderInput(DTO.resolve(fixture)));
    }

    private static java.util.List<String> viewAnnotation(Capability capability) {
        return capability.annotations().stream()
                .filter(a -> a.key().equals("view"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no view annotation on " + capability.name()))
                .values();
    }

    private static Capability capability(ScanReport report, String name) {
        return report.model().capabilities().stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("capability " + name + " not discovered"));
    }

    private static EntityOutcome outcome(ScanReport report, String entityName) {
        return report.entityOutcomes().stream()
                .filter(o -> o.entityName().equals(entityName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no outcome for " + entityName));
    }
}
