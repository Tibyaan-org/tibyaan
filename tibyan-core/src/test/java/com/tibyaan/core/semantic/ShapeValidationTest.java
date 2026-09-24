package com.tibyaan.core.semantic;

import com.tibyaan.core.ir.Annotation;
import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityKind;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Field;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Review;
import com.tibyaan.core.ir.ReviewLabel;
import com.tibyaan.core.ir.Shape;
import com.tibyaan.core.ir.Type;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Core rules 15 and 16 (Volume 4 section 5; phase DL-2b, section 3.12), and rule 14 as amended. */
class ShapeValidationTest {

    private final SemanticValidator validator = new SemanticValidator();

    @Test
    void aWellFormedShapeInputIsValid() {
        Shape address = shape("x.Address", "Address", List.of(Field.of("city", Primitive.STRING, true)), Set.of(), Map.of(),
                List.of());
        Shape draft = shape("x.OrderDraft", "OrderDraft", List.of(
                Field.of("customer", Primitive.STRING, true),
                Field.of("priority", Primitive.STRING, true),
                Field.of("address", Type.shapeRef("Address"), true),
                Field.of("tags", Primitive.STRING, true)),
                Set.of("tags"), Map.of("priority", List.of("LOW", "HIGH")),
                List.of(new Annotation(Shape.PARTIAL_ANNOTATION, List.of("extra: Map"))));

        assertThat(validator.validate(model(reviewed("draft", Type.shapeRef("OrderDraft"), Set.of()), draft, address))
                .errors()).isEmpty();
        // Rule 14 as amended: a List of a shape is a multiple SHAPE_REF input.
        assertThat(validator.validate(model(reviewed("drafts", Type.shapeRef("OrderDraft"), Set.of("drafts")), draft,
                address)).errors()).isEmpty();
    }

    @Test
    void rule15_anUnknownShapeReferenceAndAShapeOnAnOutputAreReported() {
        assertThat(validator.validate(model(reviewed("draft", Type.shapeRef("Ghost"), Set.of()))).errors())
                .anyMatch(e -> e.contains("unknown shape 'Ghost'") && e.contains("rule 15"));
        Capability output = new Capability("reviewed_x", CapabilityKind.READ, Map.of(), Type.shapeRef("OrderDraft"),
                false, true, new BindingRef("a.S#x"), List.of(), null, Map.of(), review(ReviewLabel.READ), Set.of());
        assertThat(validator.validate(model(output, shape("x.OrderDraft", "OrderDraft",
                List.of(Field.of("customer", Primitive.STRING, true)), Set.of(), Map.of(), List.of()))).errors())
                .anyMatch(e -> e.contains("output is the shape reference") && e.contains("rule 15"));
    }

    @Test
    void rule15_duplicateKeysNamesAndCyclesAreReported() {
        Shape a = shape("x.A", "A", List.of(Field.of("b", Type.shapeRef("B"), true)), Set.of(), Map.of(), List.of());
        Shape b = shape("x.B", "B", List.of(Field.of("a", Type.shapeRef("A"), true)), Set.of(), Map.of(), List.of());
        assertThat(validator.validate(model(reviewed("in", Type.shapeRef("A"), Set.of()), a, b)).errors())
                .anyMatch(e -> e.contains("cycle") && e.contains("rule 15"));
        Shape again = shape("x.A", "A", List.of(Field.of("n", Primitive.LONG, true)), Set.of(), Map.of(), List.of());
        assertThat(validator.validate(model(reviewed("in", Type.shapeRef("A"), Set.of()), again, again)).errors())
                .anyMatch(e -> e.contains("duplicate shape key")).anyMatch(e -> e.contains("duplicate shape name"));
    }

    @Test
    void rule16_emptyEntityTypedMisconstrainedAndContradictoryShapesAreReported() {
        Shape empty = shape("x.E", "E", List.of(), Set.of(), Map.of(), List.of());
        assertThat(validator.validate(model(reviewed("in", Type.shapeRef("E"), Set.of()), empty)).errors())
                .anyMatch(e -> e.contains("has no field") && e.contains("rule 16"));
        Shape entityTyped = shape("x.F", "F", List.of(Field.of("p", Type.entityRef("Product"), true)), Set.of(),
                Map.of(), List.of());
        assertThat(validator.validate(model(reviewed("in", Type.shapeRef("F"), Set.of()), entityTyped)).errors())
                .anyMatch(e -> e.contains("references entity 'Product'") && e.contains("rule 16"));
        Shape bad = shape("x.G", "G", List.of(Field.of("n", Primitive.LONG, true)), Set.of("ghost"),
                Map.of("n", List.of("1")), List.of(new Annotation(Shape.PARTIAL_ANNOTATION, List.of("n: Long"))));
        List<String> errors = validator.validate(model(reviewed("in", Type.shapeRef("G"), Set.of()), bad)).errors();
        assertThat(errors).anyMatch(e -> e.contains("marks field 'ghost' multiple"))
                .anyMatch(e -> e.contains("constrains field 'n'") && e.contains("not STRING"))
                .anyMatch(e -> e.contains("discloses 'n' as skipped"));
    }

    private static Shape shape(String key, String name, List<Field> fields, Set<String> multiple,
                               Map<String, List<String>> values, List<Annotation> annotations) {
        return new Shape(key, name, fields, multiple, values, annotations);
    }

    private static Capability reviewed(String input, Type type, Set<String> multiple) {
        Map<String, Type> inputs = new LinkedHashMap<>();
        inputs.put(input, type);
        return new Capability("reviewed_x", CapabilityKind.WRITE, inputs, Primitive.LONG, false, false,
                new BindingRef("a.S#x"), List.of(), null, Map.of(), review(ReviewLabel.WRITE), multiple);
    }

    private static Review review(ReviewLabel label) {
        return new Review(label, "a.reviewer", "2026-09-24", Review.ORIGIN_HUMAN, "sha256:x");
    }

    private static CapabilityModel model(Capability capability, Shape... shapes) {
        Entity product = new Entity("com.example.Product", "Product",
                List.of(Field.of("id", Primitive.LONG, false)), List.of(), Metadata.empty());
        return new CapabilityModel("m", "0.1", List.of(product), List.of(capability), List.of(), Metadata.empty(),
                List.of(shapes));
    }
}
