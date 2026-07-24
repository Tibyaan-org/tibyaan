package com.tibyaan.core.semantic;

import com.tibyaan.core.SampleModels;
import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityKind;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Field;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Relation;
import com.tibyaan.core.ir.RelationKind;
import com.tibyaan.core.ir.Type;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticValidatorTest {

    private final SemanticValidator validator = new SemanticValidator();

    @Test
    void theVolume4ExampleIsValid() {
        assertThat(validator.validate(SampleModels.inventory()).isValid()).isTrue();
    }

    @Test
    void duplicateEntityKeyIsReported() {
        // Entity Identity (Volume 4 rule 1): two entities with the same qualified key are a genuine
        // duplicate. Entity.of(name, fields) sets key = name, so two "Product" entities collide on key.
        CapabilityModel model = new CapabilityModel("m", "0.1",
                List.of(Entity.of("Product", List.of()), Entity.of("Product", List.of())),
                List.of(), List.of(), Metadata.empty());

        assertThat(validator.validate(model).errors())
                .anyMatch(e -> e.contains("duplicate entity key") && e.contains("Product"));
    }

    @Test
    void duplicateEntityDisplayNameIsReported() {
        // Entity Identity (Volume 4 rule 1): two DISTINCT keys resolving to the same display name must
        // fail loud in core, independent of any provider (the surface-safety check).
        CapabilityModel model = new CapabilityModel("m", "0.1",
                List.of(Entity.of("a.Product", "Product", List.of()),
                        Entity.of("b.Product", "Product", List.of())),
                List.of(), List.of(), Metadata.empty());

        assertThat(validator.validate(model).errors())
                .anyMatch(e -> e.contains("duplicate entity display name") && e.contains("Product"));
    }

    @Test
    void duplicateFieldNameWithinAnEntityIsReported() {
        Entity entity = new Entity("Product", "Product", List.of(
                Field.of("id", Primitive.LONG, false),
                Field.of("id", Primitive.STRING, false)), List.of(), Metadata.empty());
        CapabilityModel model = new CapabilityModel("m", "0.1",
                List.of(entity), List.of(), List.of(), Metadata.empty());

        assertThat(validator.validate(model).errors())
                .anyMatch(e -> e.contains("Product") && e.contains("duplicate field name")
                        && e.contains("id"));
    }

    @Test
    void duplicateCapabilityNameIsReported() {
        Capability cap = Capability.read("getProduct", Map.of(), Primitive.LONG,
                new BindingRef("a#b"));
        CapabilityModel model = new CapabilityModel("m", "0.1",
                List.of(), List.of(cap, cap), List.of(), Metadata.empty());

        assertThat(validator.validate(model).errors())
                .anyMatch(e -> e.contains("duplicate capability name") && e.contains("getProduct"));
    }

    @Test
    void duplicateBindingStringIsReported() {
        // Entity Identity (Volume 4 rule 7): two capabilities carrying the same binding string fail
        // loud, so an unprovable binding is refused rather than shipped as a possible runtime mis-bind.
        Capability a = Capability.read("getCrmNote", Map.of(), Primitive.LONG,
                new BindingRef("NoteService#findById"));
        Capability b = Capability.read("getSupportNote", Map.of(), Primitive.LONG,
                new BindingRef("NoteService#findById"));
        CapabilityModel model = new CapabilityModel("m", "0.1",
                List.of(), List.of(a, b), List.of(), Metadata.empty());

        assertThat(validator.validate(model).errors())
                .anyMatch(e -> e.contains("duplicate binding") && e.contains("NoteService#findById"));
    }

    @Test
    void unresolvedEntityReferenceInAFieldIsReported() {
        Entity entity = new Entity("Order", "Order", List.of(
                Field.of("product", Type.entityRef("Ghost"), false)), List.of(), Metadata.empty());
        CapabilityModel model = new CapabilityModel("m", "0.1",
                List.of(entity), List.of(), List.of(), Metadata.empty());

        assertThat(validator.validate(model).errors())
                .anyMatch(e -> e.contains("Order.product") && e.contains("unknown entity")
                        && e.contains("Ghost"));
    }

    @Test
    void unresolvedEntityReferenceInACapabilityOutputIsReported() {
        Capability cap = Capability.read("getGhost", Map.of(), Type.entityRef("Ghost"),
                new BindingRef("a#b"));
        CapabilityModel model = new CapabilityModel("m", "0.1",
                List.of(), List.of(cap), List.of(), Metadata.empty());

        assertThat(validator.validate(model).errors())
                .anyMatch(e -> e.contains("getGhost") && e.contains("output")
                        && e.contains("Ghost"));
    }

    @Test
    void unresolvedRelationEndpointsAreReported() {
        CapabilityModel model = new CapabilityModel("m", "0.1",
                List.of(Entity.of("Product", List.of())),
                List.of(),
                List.of(new Relation("Product", "Ghost", RelationKind.ONE_TO_MANY)),
                Metadata.empty());

        assertThat(validator.validate(model).errors())
                .anyMatch(e -> e.contains("unknown entity") && e.contains("Ghost") && e.contains("(to)"));
    }

    @Test
    void readOnlyCapabilityWithWriteKindIsReported() {
        Capability cap = new Capability("createProduct", CapabilityKind.WRITE, Map.of(),
                Primitive.LONG, false, true, new BindingRef("a#b"), List.of(), null);
        CapabilityModel model = new CapabilityModel("m", "0.1",
                List.of(), List.of(cap), List.of(), Metadata.empty());

        assertThat(validator.validate(model).errors())
                .anyMatch(e -> e.contains("createProduct") && e.contains("readOnly")
                        && e.contains("WRITE"));
    }

    @Test
    void validateOrThrowThrowsWithAllErrors() {
        CapabilityModel model = new CapabilityModel("m", "0.1",
                List.of(Entity.of("Product", List.of()), Entity.of("Product", List.of())),
                List.of(), List.of(), Metadata.empty());

        assertThatThrownBy(() -> validator.validateOrThrow(model))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).errors()).isNotEmpty());
    }
}
