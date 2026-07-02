package com.tibyaan.core.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tibyaan.core.SampleModels;
import com.tibyaan.core.ir.Annotation;
import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Field;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Type;
import com.tibyaan.core.semantic.SemanticValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The P0 definition of done: the hand built Volume 4 example validates, serializes to JSON,
 * and deserializes back into an equal model that still validates.
 */
class IrJsonRoundTripTest {

    private final SemanticValidator validator = new SemanticValidator();
    private final ObjectMapper mapper = IrTestJson.mapper();

    @Test
    void volume4ExampleValidatesAndRoundTripsThroughJson() throws Exception {
        CapabilityModel original = SampleModels.inventory();

        assertThat(validator.validate(original).isValid())
                .as("the hand built model must be valid before serializing")
                .isTrue();

        String json = mapper.writeValueAsString(original);

        // The serialized form is the one documented in Volume 4 section 6: types and the
        // binding are canonical strings, and the capability kind is its enum name.
        assertThat(json).contains(
                "\"module\":\"inventory\"",
                "\"LONG\"",
                "\"DECIMAL\"",
                "\"ENTITY_REF(Product)\"",
                "\"getProduct\"",
                "\"READ\"",
                "\"inventory.ProductService#findById\"");

        CapabilityModel roundTripped = mapper.readValue(json, CapabilityModel.class);

        assertThat(roundTripped)
                .as("deserializing the JSON must yield an equal model")
                .isEqualTo(original);

        assertThat(validator.validate(roundTripped).isValid())
                .as("the round tripped model must still be valid")
                .isTrue();
    }

    @Test
    void listCapabilityWithOutputMultipleValidatesAndRoundTrips() throws Exception {
        Entity contact = new Entity("Contact",
                List.of(Field.of("id", Primitive.UUID, false)), List.of(), Metadata.empty());
        Capability listContact = Capability.readList("listContact", Map.of(), Type.entityRef("Contact"),
                new BindingRef("ContactService#findAll"), List.of(Annotation.flag("paged")));
        CapabilityModel original = new CapabilityModel("erp", "0.1",
                List.of(contact), List.of(listContact), List.of(), Metadata.empty());

        assertThat(validator.validate(original).isValid()).isTrue();

        String json = mapper.writeValueAsString(original);
        assertThat(json).contains("\"outputMultiple\":true");

        CapabilityModel roundTripped = mapper.readValue(json, CapabilityModel.class);
        assertThat(roundTripped).isEqualTo(original);
        assertThat(roundTripped.capabilities().get(0).outputMultiple()).isTrue();
        assertThat(validator.validate(roundTripped).isValid()).isTrue();
    }
}
