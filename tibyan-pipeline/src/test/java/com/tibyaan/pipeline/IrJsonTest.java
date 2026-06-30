package com.tibyaan.pipeline;

import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Field;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IrJsonTest {

    @Test
    void irRoundTripsThroughJsonOnDisk(@TempDir Path dir) {
        CapabilityModel original = productModel();
        Path file = dir.resolve("ir.json");

        IrJson.write(original, file);
        CapabilityModel back = IrJson.read(file);

        assertThat(back).isEqualTo(original);
    }

    @Test
    void irJsonUsesCanonicalTypeAndBindingStrings() {
        String json = IrJson.writeString(productModel());

        assertThat(json)
                .contains("\"LONG\"")
                .contains("\"DECIMAL\"")
                .contains("\"ENTITY_REF(Product)\"")
                .contains("\"ProductService#findById\"")
                .contains("\"READ\"");
    }

    private static CapabilityModel productModel() {
        Entity product = new Entity("Product",
                List.of(
                        Field.of("id", Primitive.LONG, false),
                        Field.of("name", Primitive.STRING, false),
                        Field.of("price", Primitive.DECIMAL, false)),
                List.of(), Metadata.empty());
        Map<String, Type> inputs = new LinkedHashMap<>();
        inputs.put("id", Primitive.LONG);
        Capability getProduct = Capability.read("getProduct", inputs, Type.entityRef("Product"),
                new BindingRef("ProductService#findById"));
        return new CapabilityModel("legacy-spring-app", "0.1",
                List.of(product), List.of(getProduct), List.of(), Metadata.empty());
    }
}
