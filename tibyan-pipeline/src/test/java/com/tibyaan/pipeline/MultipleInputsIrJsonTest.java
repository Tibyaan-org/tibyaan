package com.tibyaan.pipeline;

import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Field;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Review;
import com.tibyaan.core.ir.ReviewLabel;
import com.tibyaan.core.ir.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase DL-2a (Volume 4 section 3.11): multipleInputs round-trips, and is absent from a model that has none. */
class MultipleInputsIrJsonTest {

    @Test
    void multipleInputsRoundTripsThroughJson(@TempDir Path dir) {
        CapabilityModel original = model(Set.of("ids"));
        Path file = dir.resolve("ir.json");

        IrJson.write(original, file);
        CapabilityModel back = IrJson.read(file);

        assertThat(back).isEqualTo(original);
        assertThat(back.capabilities().get(0).multipleInputs()).containsExactly("ids");
        assertThat(IrJson.writeString(original)).contains("\"multipleInputs\"");
    }

    @Test
    void aModelWithoutMultipleInputsSerializesWithoutTheKey() {
        assertThat(IrJson.writeString(model(Set.of()))).doesNotContain("multipleInputs");
    }

    private static CapabilityModel model(Set<String> multiple) {
        Entity product = new Entity("com.example.Product", "Product",
                List.of(Field.of("id", Primitive.LONG, false)), List.of(), Metadata.empty());
        Map<String, Type> inputs = new LinkedHashMap<>();
        inputs.put("ids", Primitive.LONG);
        Capability byIds = Capability.reviewed("reviewed_byIds", inputs, Type.entityRef("Product"), true,
                new BindingRef("com.example.ProductService#byIds"), List.of(), Map.of(),
                new Review(ReviewLabel.READ, "a.reviewer", "2026-09-24", Review.ORIGIN_HUMAN, "sha256:x"),
                multiple);
        return new CapabilityModel("shop", "0.1", List.of(product), List.of(byIds), List.of(), Metadata.empty());
    }
}
