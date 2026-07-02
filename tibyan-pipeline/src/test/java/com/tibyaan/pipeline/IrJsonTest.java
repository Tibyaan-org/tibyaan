package com.tibyaan.pipeline;

import com.tibyaan.core.ir.Annotation;
import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Field;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Relation;
import com.tibyaan.core.ir.RelationKind;
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

    @Test
    void e3ContentRoundTripsThroughJson(@TempDir Path dir) {
        // C4 and the other new E3 model content: a FLOAT field, an enum annotation, and a Relation
        // must survive JSON unchanged (Volume 5 sections 4.4 to 4.6).
        CapabilityModel original = e3Model();
        Path file = dir.resolve("e3.ir.json");

        IrJson.write(original, file);
        CapabilityModel back = IrJson.read(file);

        assertThat(back).isEqualTo(original);
        assertThat(IrJson.writeString(original))
                .contains("\"FLOAT\"")
                .contains("\"ENTITY_REF(Company)\"")
                .contains("\"enum\"")
                .contains("\"MANY_TO_ONE\"");
    }

    private static CapabilityModel e3Model() {
        Entity person = new Entity("Person",
                List.of(
                        Field.of("id", Primitive.LONG, false),
                        new Field("rating", Primitive.FLOAT, false, List.of()),
                        new Field("status", Primitive.STRING, false,
                                List.of(new Annotation("enum", List.of("ACTIVE", "INACTIVE")))),
                        Field.of("employer", Type.entityRef("Company"), true)),
                List.of(), Metadata.empty());
        Entity company = new Entity("Company",
                List.of(Field.of("id", Primitive.LONG, false)), List.of(), Metadata.empty());
        return new CapabilityModel("assoc", "0.1",
                List.of(person, company), List.of(),
                List.of(new Relation("Person", "Company", RelationKind.MANY_TO_ONE)),
                Metadata.empty());
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
