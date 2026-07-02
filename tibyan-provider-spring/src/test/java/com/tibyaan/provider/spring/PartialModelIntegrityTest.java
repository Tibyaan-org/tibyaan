package com.tibyaan.provider.spring;

import com.tibyaan.core.ir.Annotation;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Field;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Relation;
import com.tibyaan.core.ir.RelationKind;
import com.tibyaan.core.ir.Type;
import com.tibyaan.provider.spring.EntityOutcome.Cause;
import com.tibyaan.provider.spring.EntityOutcome.Status;
import com.tibyaan.plugin.ProviderInput;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase E5, partial-model integrity (Volume 5 sections 4.7, 4.8, 4.9). Field tolerance and the
 * survey contract are exercised through the scan (fixtures T and S); model closure is exercised in
 * isolation (the G-logic), because given tolerance the scan path never produces an absent
 * ENTITY_REF target (a recorded finding, section 4.7). Fixtures are read as source, never compiled.
 */
class PartialModelIntegrityTest {

    private static final Path TOLERATE = Path.of("src", "test", "resources", "fixtures", "tolerate");

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();

    // Field tolerance (section 4.8), fixtures T1 to T7.

    @Test
    void t1_collectionFieldSkippedAndEntityDiscoversViaRule41() {
        ScanReport report = scan();
        // The collection is skipped, but the entity extracts and discovers via 4.1.
        assertThat(fieldNames(report, "Gizmo")).doesNotContain("parts");
        Capability getGizmo = capability(report, "getGizmo");
        assertThat(getGizmo.output()).isEqualTo(Type.entityRef("Gizmo"));
        assertThat(outcome(report, "Gizmo").status()).isEqualTo(Status.DISCOVERED);
        assertThat(outcome(report, "Gizmo").partialFields()).contains("parts");
    }

    @Test
    void t7_discoveredBindingIsExactlyRightUnderTolerance() {
        // The mis-bound guard: tolerance changed only whether Gizmo reached discovery, not how.
        assertThat(capability(scan(), "getGizmo").binding().value()).isEqualTo("GizmoService#findById");
    }

    @Test
    void t2_embeddableValueTypeSkipped() {
        ScanReport report = scan();
        assertThat(entityNames(report)).contains("Boxed");
        assertThat(fieldNames(report, "Boxed")).doesNotContain("addr");
        assertThat(skippedNote(report, "Boxed", "addr")).contains("@Embeddable");
    }

    @Test
    void t3_byteArraySkippedWithTheBinaryNoteNotToMany() {
        ScanReport report = scan();
        assertThat(fieldNames(report, "Blob")).doesNotContain("data");
        String note = skippedNote(report, "Blob", "data");
        assertThat(note).contains("binary");
        assertThat(note).doesNotContain("to-many");
    }

    @Test
    void t4_unresolvableLibraryTypeSkipped() {
        ScanReport report = scan();
        assertThat(fieldNames(report, "Libby")).doesNotContain("price");
        assertThat(skippedNote(report, "Libby", "price")).contains("not resolvable");
    }

    @Test
    void t5_latentEnumIdSurfacesAsNoIdentifierOnceCollectionTolerated() {
        ScanReport report = scan();
        // The collection no longer vetoes; the enum @Id now surfaces its identifier case.
        assertThat(entityNames(report)).contains("Templated");
        assertThat(outcome(report, "Templated").partialFields()).contains("items");
        assertThat(outcome(report, "Templated").cause()).isEqualTo(Cause.NO_IDENTIFIER);
    }

    @Test
    void t6_toleranceDidNotSoftenDiscovery() {
        ScanReport report = scan();
        // Nofind extracts partial but has no service: still NO_BACKING_METHOD.
        assertThat(entityNames(report)).contains("Nofind");
        assertThat(outcome(report, "Nofind").cause()).isEqualTo(Cause.NO_BACKING_METHOD);
        // Twinned extracts partial but has a composite key: still COMPOSITE_KEY.
        assertThat(entityNames(report)).contains("Twinned");
        assertThat(outcome(report, "Twinned").cause()).isEqualTo(Cause.COMPOSITE_KEY);
    }

    // The survey contract (section 4.9), fixtures S1 to S3.

    @Test
    void s1_partialEntityCarriesTheAnnotationAndTheOutcomeQualifier() {
        ScanReport report = scan();
        assertThat(partialAnnotation(report, "Gizmo")).contains("parts");
        assertThat(outcome(report, "Gizmo").isPartial()).isTrue();
    }

    @Test
    void s2_completeEntityCarriesNoPartialMarker() {
        ScanReport report = scan();
        // Part has only mapped fields; absence of the marker means the shape is complete.
        assertThat(entity(report, "Part").annotations()).noneMatch(a -> a.key().equals("partial"));
        assertThat(outcome(report, "Part").isPartial()).isFalse();
    }

    @Test
    void s3_totalsExposeADistinctPartialCount() {
        ScanReport report = scan();
        // At least Gizmo, Boxed, Blob, Libby, Templated, Nofind, Twinned are partial; Part is not.
        assertThat(report.partialEntityCount()).isGreaterThanOrEqualTo(7);
        assertThat(report.skippedFieldCount()).isGreaterThanOrEqualTo(report.partialEntityCount());
        assertThat(report.partialEntityCount()).isLessThan(entityNames(report).size());
    }

    // Model closure (section 4.7), the G-logic, exercised directly on a synthetic model.

    @Test
    void g1_closureDropsAReferenceToAnAbsentEntityAndDiscloses() {
        Entity a = new Entity("A", List.of(
                new Field("toAbsent", Type.entityRef("Missing"), false, List.of()),
                new Field("toPresent", Type.entityRef("Present"), false, List.of()),
                new Field("name", Primitive.STRING, false, List.of())),
                List.of(), Metadata.empty());
        Entity present = new Entity("Present",
                List.of(new Field("id", Primitive.LONG, false, List.of())), List.of(), Metadata.empty());
        List<Relation> relations = List.of(
                new Relation("A", "Missing", RelationKind.MANY_TO_ONE),
                new Relation("A", "Present", RelationKind.MANY_TO_ONE));
        List<EntityOutcome> outcomes = List.of(EntityOutcome.discovered("A", "getA -> AService#findById"));

        SpringCapabilityProvider.Finalized result =
                SpringCapabilityProvider.closeAndFinalize(List.of(a, present), relations, outcomes, List.of());

        // G1: the reference to the absent entity is dropped; the present one is kept.
        Entity closedA = result.entities().stream().filter(e -> e.name().equals("A")).findFirst().orElseThrow();
        assertThat(closedA.fields()).extracting(Field::name).containsExactly("toPresent", "name");
        // G3: the relation to a present target is kept, the dangling one dropped.
        assertThat(result.relations()).containsExactly(new Relation("A", "Present", RelationKind.MANY_TO_ONE));
        // Disclosed: A is partial, the drop is in the skipped-field list and the outcome.
        assertThat(closedA.annotations()).anySatisfy(ann -> {
            assertThat(ann.key()).isEqualTo("partial");
            assertThat(ann.values()).containsExactly("toAbsent");
        });
        assertThat(result.skippedFields()).anySatisfy(s -> {
            assertThat(s.entity()).isEqualTo("A");
            assertThat(s.field()).isEqualTo("toAbsent");
            assertThat(s.note()).contains("not in the model");
        });
        assertThat(result.outcomes().get(0).partialFields()).containsExactly("toAbsent");
    }

    @Test
    void g2_capabilityOutputIsNeverTouchedByClosure() {
        // Condition 4: closure never drops or alters a capability's output or binding.
        Entity a = new Entity("A",
                List.of(new Field("toAbsent", Type.entityRef("Missing"), false, List.of())),
                List.of(), Metadata.empty());
        List<EntityOutcome> outcomes = List.of(EntityOutcome.discovered("A", "getA"));
        SpringCapabilityProvider.Finalized result = SpringCapabilityProvider.closeAndFinalize(
                List.of(a), List.of(new Relation("A", "Missing", RelationKind.MANY_TO_ONE)), outcomes, List.of());
        // The entity kept no dangling field, so the model is closed and A remains discoverable.
        Entity closedA = result.entities().get(0);
        assertThat(closedA.fields()).isEmpty();
        assertThat(result.relations()).isEmpty();
        assertThat(result.outcomes().get(0).isDiscovered()).isTrue();
    }

    private ScanReport scan() {
        return provider.scan(new ProviderInput(TOLERATE));
    }

    private static List<String> entityNames(ScanReport report) {
        return report.model().entities().stream().map(Entity::name).toList();
    }

    private static List<String> fieldNames(ScanReport report, String entityName) {
        return entity(report, entityName).fields().stream().map(Field::name).toList();
    }

    private static List<String> partialAnnotation(ScanReport report, String entityName) {
        return entity(report, entityName).annotations().stream()
                .filter(a -> a.key().equals("partial"))
                .findFirst()
                .map(Annotation::values)
                .orElseThrow(() -> new AssertionError("no partial annotation on " + entityName));
    }

    private static String skippedNote(ScanReport report, String entityName, String fieldName) {
        return report.skippedFields().stream()
                .filter(s -> s.entity().equals(entityName) && s.field().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no skipped field " + entityName + "." + fieldName))
                .note();
    }

    private static Capability capability(ScanReport report, String name) {
        return report.model().capabilities().stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("capability " + name + " not discovered"));
    }

    private static Entity entity(ScanReport report, String name) {
        return report.model().entities().stream()
                .filter(e -> e.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("entity " + name + " not in the model"));
    }

    private static EntityOutcome outcome(ScanReport report, String entityName) {
        return report.entityOutcomes().stream()
                .filter(o -> o.entityName().equals(entityName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no outcome for " + entityName));
    }
}
