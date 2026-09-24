package com.tibyaan.core.semantic;

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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Core rule 14 (Volume 4 section 5; phase DL-2a, section 3.11): input multiplicity is well formed. Every name
 * in {@code multipleInputs} names an existing input, and that input is primitive-typed.
 */
class InputMultiplicityValidationTest {

    private final SemanticValidator validator = new SemanticValidator();

    @Test
    void aMultipleInputOverAnExistingPrimitiveInputIsValid() {
        Capability cap = capability(inputs("ids", Primitive.LONG), Set.of("ids"));

        assertThat(validator.validate(model(cap)).errors()).isEmpty();
        assertThat(cap.hasMultipleInputs()).isTrue();
        assertThat(cap.multipleInputs()).containsExactly("ids");
    }

    @Test
    void rule14_aMultipleNameThatIsNotAnInputIsReported() {
        Capability cap = capability(inputs("id", Primitive.LONG), Set.of("ids"));

        assertThat(validator.validate(model(cap)).errors())
                .anyMatch(e -> e.contains("marks input 'ids' multiple") && e.contains("not an input")
                        && e.contains("rule 14"));
    }

    @Test
    void rule14_aMultipleInputTypedAsAnEntityReferenceIsReported() {
        Capability cap = capability(inputs("product", Type.entityRef("Product")), Set.of("product"));

        assertThat(validator.validate(model(cap)).errors())
                .anyMatch(e -> e.contains("marks input 'product' multiple") && e.contains("ENTITY_REF(Product)")
                        && e.contains("rule 14"));
    }

    @Test
    void anAbsentSetIsNormalizedToEmptyAndNeverNull() {
        Capability cap = new Capability("getProduct", CapabilityKind.READ, inputs("id", Primitive.LONG),
                Type.entityRef("Product"), false, true, new BindingRef("a.S#m"), List.of(), null, Map.of(), null,
                null);

        assertThat(cap.multipleInputs()).isEmpty();
        assertThat(cap.hasMultipleInputs()).isFalse();
    }

    private static Capability capability(Map<String, Type> inputs, Set<String> multiple) {
        return new Capability("getProduct", CapabilityKind.READ, inputs, Type.entityRef("Product"), false, true,
                new BindingRef("a.S#m"), List.of(), null, Map.of(), null, multiple);
    }

    private static Map<String, Type> inputs(String name, Type type) {
        Map<String, Type> inputs = new LinkedHashMap<>();
        inputs.put(name, type);
        return inputs;
    }

    private static CapabilityModel model(Capability capability) {
        Entity product = new Entity("com.example.Product", "Product",
                List.of(Field.of("id", Primitive.LONG, false)), List.of(), Metadata.empty());
        return new CapabilityModel("m", "0.1", List.of(product), List.of(capability), List.of(), Metadata.empty());
    }
}
