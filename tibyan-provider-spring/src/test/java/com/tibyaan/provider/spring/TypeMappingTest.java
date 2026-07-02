package com.tibyaan.provider.spring;

import com.tibyaan.core.ir.Annotation;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.EntityRef;
import com.tibyaan.core.ir.Field;
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
 * Phase E3: the three ratified type rules, exercised by the pre-registered fixtures (Volume 5
 * sections 4.4, 4.5, 4.6). Fixtures are read as source, never compiled.
 */
class TypeMappingTest {

    private static final Path FIXTURES = Path.of("src", "test", "resources", "fixtures");

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();

    // Associations (section 4.4), fixtures A1 to A9.

    @Test
    void a1_manyToOneMapsToEntityRefAndManyToOneRelation() {
        ScanReport report = provider.scan(new ProviderInput(FIXTURES.resolve("assoc")));
        assertThat(fieldType(report, "Person", "employer")).isEqualTo(Type.entityRef("Company"));
        assertThat(report.model().relations())
                .contains(new Relation("Person", "Company", RelationKind.MANY_TO_ONE));
    }

    @Test
    void a2_oneToOneMapsWithOneToOneKind() {
        ScanReport report = provider.scan(new ProviderInput(FIXTURES.resolve("assoc")));
        assertThat(fieldType(report, "Passport", "holder")).isEqualTo(Type.entityRef("Person"));
        assertThat(report.model().relations())
                .contains(new Relation("Passport", "Person", RelationKind.ONE_TO_ONE));
    }

    @Test
    void a3_twoFieldsToTheSameTargetDedupeToOneRelation() {
        ScanReport report = provider.scan(new ProviderInput(FIXTURES.resolve("assoc")));
        assertThat(fieldType(report, "Friendship", "a")).isEqualTo(Type.entityRef("Person"));
        assertThat(fieldType(report, "Friendship", "b")).isEqualTo(Type.entityRef("Person"));
        assertThat(report.model().relations().stream()
                .filter(r -> r.from().equals("Friendship") && r.to().equals("Person")).toList())
                .as("two fields to Person produce exactly one Relation")
                .containsExactly(new Relation("Friendship", "Person", RelationKind.MANY_TO_ONE));
    }

    @Test
    void a4_entityTypedFieldWithNoRelationshipAnnotationRefuses() {
        ScanReport report = provider.scan(new ProviderInput(FIXTURES.resolve("assoc")));
        EntityOutcome outcome = outcome(report, "Sponsorship");
        assertThat(outcome.status()).isEqualTo(Status.FAILED);
        assertThat(outcome.cause()).isEqualTo(Cause.UNMAPPED_FIELD_TYPE);
        assertThat(outcome.detail()).contains("sponsor").contains("no @ManyToOne or @OneToOne");
        assertThat(entityNames(report)).doesNotContain("Sponsorship");
    }

    @Test
    void a5_collectionFieldRefusesAsDeferred() {
        ScanReport report = provider.scan(new ProviderInput(FIXTURES.resolve("assoc")));
        EntityOutcome outcome = outcome(report, "Team");
        assertThat(outcome.status()).isEqualTo(Status.FAILED);
        assertThat(outcome.detail()).contains("members").contains("to-many").contains("deferred");
    }

    @Test
    void a6_inTreeNonEntityClassFieldRefuses() {
        ScanReport report = provider.scan(new ProviderInput(FIXTURES.resolve("assoc")));
        EntityOutcome outcome = outcome(report, "Account");
        assertThat(outcome.status()).isEqualTo(Status.FAILED);
        assertThat(outcome.detail()).contains("balance").contains("neither an @Entity nor an enum");
    }

    @Test
    void a7_fullyQualifiedEntityTypeResolvesByLastSegment() {
        ScanReport report = provider.scan(new ProviderInput(FIXTURES.resolve("assoc")));
        assertThat(fieldType(report, "Registration", "org")).isEqualTo(Type.entityRef("Company"));
        assertThat(report.model().relations())
                .contains(new Relation("Registration", "Company", RelationKind.MANY_TO_ONE));
    }

    @Test
    void a8_nameTrapUnresolvedTypeRefuses() {
        ScanReport report = provider.scan(new ProviderInput(FIXTURES.resolve("assoc")));
        EntityOutcome outcome = outcome(report, "Ghost");
        assertThat(outcome.status()).isEqualTo(Status.FAILED);
        assertThat(outcome.detail()).contains("Phantom").contains("unresolved");
    }

    @Test
    void a9_idAssociationMapsAsFieldWhileReadDoesNotForm() {
        ScanReport report = provider.scan(new ProviderInput(FIXTURES.resolve("assoc")));
        // The @Id @ManyToOne field still maps to an ENTITY_REF and contributes a Relation.
        assertThat(fieldType(report, "Enrollment", "student")).isEqualTo(Type.entityRef("Person"));
        assertThat(report.model().relations())
                .contains(new Relation("Enrollment", "Person", RelationKind.MANY_TO_ONE));
        // But no read intent forms: the identifier is not a bindable primitive. Composite-key
        // eligibility itself (section 4.2 condition 0) is phase E2.
        EntityOutcome outcome = outcome(report, "Enrollment");
        assertThat(outcome.status()).isEqualTo(Status.FAILED);
        assertThat(outcome.cause()).isEqualTo(Cause.NO_IDENTIFIER);
    }

    // Enums (section 4.5), fixtures B1 to B5.

    @Test
    void b1_enumeratedStringFieldMapsToStringWithConstantsInOrder() {
        ScanReport report = provider.scan(new ProviderInput(FIXTURES.resolve("enums")));
        assertThat(fieldType(report, "Widget", "color")).isEqualTo(Primitive.STRING);
        assertThat(enumValues(report, "Widget", "color")).containsExactly("RED", "GREEN", "BLUE");
    }

    @Test
    void b2_bareEnumFieldMapsIdentically() {
        ScanReport report = provider.scan(new ProviderInput(FIXTURES.resolve("enums")));
        assertThat(fieldType(report, "Gadget", "color")).isEqualTo(Primitive.STRING);
        assertThat(enumValues(report, "Gadget", "color")).containsExactly("RED", "GREEN", "BLUE");
    }

    @Test
    void b3_fullyQualifiedEnumResolvesByLastSegment() {
        ScanReport report = provider.scan(new ProviderInput(FIXTURES.resolve("enums")));
        assertThat(fieldType(report, "Gizmo", "shade")).isEqualTo(Primitive.STRING);
        assertThat(enumValues(report, "Gizmo", "shade")).containsExactly("RED", "GREEN", "BLUE");
    }

    @Test
    void b4_unresolvableTypeRefuses() {
        ScanReport report = provider.scan(new ProviderInput(FIXTURES.resolve("enums")));
        EntityOutcome outcome = outcome(report, "Thing");
        assertThat(outcome.status()).isEqualTo(Status.FAILED);
        assertThat(outcome.detail()).contains("Mystery").contains("unresolved");
    }

    @Test
    void b5_nameTrapEntityTypedStatusRoutesToAssociationNotEnum() {
        ScanReport report = provider.scan(new ProviderInput(FIXTURES.resolve("enums")));
        Field status = field(report, "Order", "status");
        assertThat(status.type()).isEqualTo(Type.entityRef("OrderStatus"));
        assertThat(status.annotations()).as("must not be treated as an enum").isEmpty();
        assertThat(report.model().relations())
                .contains(new Relation("Order", "OrderStatus", RelationKind.MANY_TO_ONE));
    }

    // Inexact numerics (section 4.6), fixtures C1 to C3 and C5. C4 (JSON round-trip) is in IrJsonTest.

    @Test
    void c1_doubleMapsToFloat() {
        ScanReport report = provider.scan(new ProviderInput(FIXTURES.resolve("floats")));
        assertThat(fieldType(report, "Measure", "amount")).isEqualTo(Primitive.FLOAT);
    }

    @Test
    void c2_allJavaFloatingTypesMapToFloat() {
        ScanReport report = provider.scan(new ProviderInput(FIXTURES.resolve("floats")));
        assertThat(fieldType(report, "Readings", "primitiveDouble")).isEqualTo(Primitive.FLOAT);
        assertThat(fieldType(report, "Readings", "primitiveFloat")).isEqualTo(Primitive.FLOAT);
        assertThat(fieldType(report, "Readings", "boxedFloat")).isEqualTo(Primitive.FLOAT);
    }

    @Test
    void c3_exactInexactLineStaysHardBothWays() {
        ScanReport report = provider.scan(new ProviderInput(FIXTURES.resolve("floats")));
        assertThat(fieldType(report, "Money2", "exact")).isEqualTo(Primitive.DECIMAL);
        assertThat(fieldType(report, "Money2", "inexact")).isEqualTo(Primitive.FLOAT);
    }

    @Test
    void c5_floatIdFormsNoReadIntentAndRefusesWithLocatedMessage() {
        ScanReport report = provider.scan(new ProviderInput(FIXTURES.resolve("floats")));
        // The entity extracts (the Double id maps to FLOAT) but forms no read intent.
        assertThat(fieldType(report, "Weird", "ratingId")).isEqualTo(Primitive.FLOAT);
        EntityOutcome outcome = outcome(report, "Weird");
        assertThat(outcome.status()).isEqualTo(Status.FAILED);
        assertThat(outcome.cause()).isEqualTo(Cause.NO_IDENTIFIER);
        assertThat(outcome.detail()).contains("ratingId").contains("inexact").contains("FLOAT");
    }

    private static Type fieldType(ScanReport report, String entity, String field) {
        return field(report, entity, field).type();
    }

    private static List<String> enumValues(ScanReport report, String entity, String field) {
        Annotation annotation = field(report, entity, field).annotations().stream()
                .filter(a -> a.key().equals("enum"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no enum annotation on " + entity + "." + field));
        return annotation.values();
    }

    private static Field field(ScanReport report, String entityName, String fieldName) {
        return entity(report, entityName).fields().stream()
                .filter(f -> f.name().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no field " + fieldName + " on " + entityName));
    }

    private static Entity entity(ScanReport report, String name) {
        return report.model().entities().stream()
                .filter(e -> e.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("entity " + name + " not in the model"));
    }

    private static List<String> entityNames(ScanReport report) {
        return report.model().entities().stream().map(Entity::name).toList();
    }

    private static EntityOutcome outcome(ScanReport report, String entityName) {
        return report.entityOutcomes().stream()
                .filter(o -> o.entityName().equals(entityName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no outcome for " + entityName));
    }
}
