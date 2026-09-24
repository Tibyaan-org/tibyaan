package com.tibyaan.pipeline;

import com.tibyaan.core.ir.Annotation;
import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Field;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Review;
import com.tibyaan.core.ir.ReviewLabel;
import com.tibyaan.core.ir.Shape;
import com.tibyaan.core.ir.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase DL-2b (Volume 4 section 3.12): shapes round-trip through the IR JSON and are absent from a model without one. */
class ShapesIrJsonTest {

    @Test
    void shapesRoundTripThroughJson(@TempDir Path dir) {
        CapabilityModel original = model(true);
        Path file = dir.resolve("ir.json");

        IrJson.write(original, file);
        CapabilityModel back = IrJson.read(file);

        assertThat(back).isEqualTo(original);
        assertThat(back.shapes()).hasSize(1);
        assertThat(back.shapes().get(0).fieldValues()).containsEntry("priority", List.of("LOW", "HIGH"));
        assertThat(back.capabilities().get(0).inputs().get("draft")).isEqualTo(Type.shapeRef("OrderDraft"));
        assertThat(IrJson.writeString(original)).contains("\"shapes\"").contains("\"SHAPE_REF(OrderDraft)\"");
    }

    @Test
    void aModelWithoutShapesSerializesWithoutTheKey() {
        assertThat(IrJson.writeString(model(false))).doesNotContain("\"shapes\"");
    }

    private static CapabilityModel model(boolean withShape) {
        Map<String, Type> inputs = new LinkedHashMap<>();
        inputs.put("draft", withShape ? Type.shapeRef("OrderDraft") : Primitive.STRING);
        Capability create = Capability.reviewed("reviewed_create", inputs, Primitive.LONG, false,
                new BindingRef("com.example.OrderService#create"), List.of(), Map.of(),
                new Review(ReviewLabel.WRITE, "a.reviewer", "2026-09-24", Review.ORIGIN_HUMAN, "sha256:x"), Set.of());
        List<Shape> shapes = withShape
                ? List.of(new Shape("com.example.OrderDraft", "OrderDraft",
                        List.of(Field.of("customer", Primitive.STRING, true), Field.of("priority", Primitive.STRING, true),
                                Field.of("tags", Primitive.STRING, true)),
                        Set.of("tags"), Map.of("priority", List.of("LOW", "HIGH")),
                        List.of(new Annotation(Shape.PARTIAL_ANNOTATION, List.of("extra: Map<String, Object>")))))
                : List.of();
        return new CapabilityModel("shop", "0.1", List.of(), List.of(create), List.of(), Metadata.empty(), shapes);
    }
}
