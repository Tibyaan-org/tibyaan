package com.tibyaan.backend.openapi;

import com.tibyaan.core.ir.Annotation;
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
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Carriage of a reviewed capability on the OpenAPI surface (phase DL-1, Volume 6 section 4.6): the path
 * derives from the minted name, a READ is a tagged GET with query parameters, a WRITE or ACTION is a
 * tagged POST whose body carries the inputs, a void method responds 204 with no body, an opaque output
 * is the permissive object of section 4.1, and a verified or attested document gains none of it (fixture
 * RV-12). The golden case in {@link OpenApiBackendGoldenTest} pins the whole document; this test pins the
 * rules one by one through the parsed model, so a drift names the rule it broke.
 */
class ReviewedOpenApiCarriageTest {

    private static Review review(ReviewLabel label) {
        return new Review(label, "a.elseginy", "2026-09-23", Review.ORIGIN_HUMAN, "sha256:r1");
    }

    private static CapabilityModel model(Capability... capabilities) {
        Entity product = new Entity("Product", "Product",
                List.of(Field.of("id", Primitive.LONG, false)), List.of(), Metadata.empty());
        return new CapabilityModel("shop", "0.1", List.of(product), List.of(capabilities), List.of(),
                Metadata.empty());
    }

    private static Map<String, Type> inputs(String name, Type type) {
        Map<String, Type> inputs = new LinkedHashMap<>();
        inputs.put(name, type);
        return inputs;
    }

    private static OpenAPI parse(String json) {
        SwaggerParseResult parsed = new OpenAPIV3Parser().readContents(json, null, null);
        assertThat(parsed.getOpenAPI()).as("document parses as OpenAPI 3.x").isNotNull();
        assertThat(parsed.getMessages()).as("no OpenAPI validation messages").isEmpty();
        return parsed.getOpenAPI();
    }

    @Test
    void aReviewedWriteIsATaggedPostWhoseBodyCarriesTheInputs() {
        Capability write = Capability.reviewed("reviewed_createProduct", inputs("name", Primitive.STRING),
                Primitive.LONG, false, new BindingRef("com.example.ProductService#create"),
                List.of(new Annotation("description",
                        List.of("Creates a product with the given name and returns its id."))),
                Map.of(), review(ReviewLabel.WRITE));

        String json = OpenApiDocument.toJson(model(write));
        OpenAPI document = parse(json);

        // The path derives from the minted name; the operation is a POST, never a GET.
        Operation post = document.getPaths().get("/reviewed/createProduct").getPost();
        assertThat(post).as("POST at /reviewed/createProduct").isNotNull();
        assertThat(document.getPaths().get("/reviewed/createProduct").getGet()).isNull();
        assertThat(post.getOperationId()).isEqualTo("reviewed_createProduct");
        assertThat(post.getTags()).containsExactly("reviewed");
        assertThat(post.getSummary()).isEqualTo("Reviewed by a.elseginy (human), not tool-verified; the tool "
                + "labeled it WRITE. Creates a product with the given name and returns its id.");
        // The body is one required JSON object with every input a required, typed property.
        assertThat(post.getRequestBody().getRequired()).isTrue();
        var schema = post.getRequestBody().getContent().get("application/json").getSchema();
        assertThat(schema.getType()).isEqualTo("object");
        assertThat(schema.getProperties()).containsOnlyKeys("name");
        assertThat(schema.getRequired()).containsExactly("name");
        assertThat(post.getParameters()).isNull();
        // A primitive output maps per the table.
        var ok = post.getResponses().get("200").getContent().get("application/json").getSchema();
        assertThat(ok.getType()).isEqualTo("integer");
        assertThat(ok.getFormat()).isEqualTo("int64");
        // The machine half: the class marker and the review object, character-exact.
        assertThat(json).contains("\"x-tibyan-reviewed\": true");
        assertThat(json).contains("\"x-tibyan-review\": {\n"
                + "          \"label\": \"WRITE\",\n"
                + "          \"reviewedBy\": \"a.elseginy\",\n"
                + "          \"reviewedOn\": \"2026-09-23\",\n"
                + "          \"descriptionOrigin\": \"human\"\n"
                + "        }");
    }

    @Test
    void aVoidActionIsAPostWithNoBodyThatResponds204() {
        Capability action = Capability.reviewed("reviewed_reindexProducts", new LinkedHashMap<>(),
                Primitive.STRING, false, new BindingRef("com.example.ProductService#reindex"),
                List.of(Annotation.flag("void"),
                        new Annotation("description", List.of("Rebuilds the product search index."))),
                Map.of(), new Review(ReviewLabel.UNKNOWN, "a.elseginy", "2026-09-23",
                        Review.ORIGIN_AI_DRAFT, "sha256:r3"));

        OpenAPI document = parse(OpenApiDocument.toJson(model(action)));

        Operation post = document.getPaths().get("/reviewed/reindexProducts").getPost();
        assertThat(post).isNotNull();
        assertThat(post.getRequestBody()).as("no inputs, no body").isNull();
        assertThat(post.getResponses()).containsOnlyKeys("204");
        assertThat(post.getResponses().get("204").getContent()).isNull();
        assertThat(post.getSummary()).startsWith("Reviewed by a.elseginy (ai-draft), not tool-verified; the "
                + "tool labeled it UNKNOWN.");
    }

    @Test
    void aReviewedReadIsATaggedGetWithQueryParametersAndTheEnumRendersAsToday() {
        Capability read = Capability.reviewed("reviewed_findProductsByStatus",
                inputs("status", Primitive.STRING), Type.entityRef("Product"), true,
                new BindingRef("com.example.ProductService#findByStatus"),
                List.of(new Annotation("description", List.of("Lists the products in the given status."))),
                Map.of("status", List.of("ACTIVE", "DISCONTINUED")), review(ReviewLabel.READ));

        OpenAPI document = parse(OpenApiDocument.toJson(model(read)));

        Operation get = document.getPaths().get("/reviewed/findProductsByStatus").getGet();
        assertThat(get).isNotNull();
        assertThat(get.getTags()).containsExactly("reviewed");
        assertThat(get.getParameters()).hasSize(1);
        assertThat(get.getParameters().get(0).getName()).isEqualTo("status");
        assertThat(get.getParameters().get(0).getIn()).isEqualTo("query");
        assertThat(get.getParameters().get(0).getRequired()).isTrue();
        assertThat(get.getParameters().get(0).getSchema().getEnum()).containsExactly("ACTIVE", "DISCONTINUED");
        // A list of the entity is an array of its component, as a list read renders (section 4.2).
        var ok = get.getResponses().get("200").getContent().get("application/json").getSchema();
        assertThat(ok.getType()).isEqualTo("array");
        assertThat(ok.getItems().get$ref()).isEqualTo("#/components/schemas/Product");
        assertThat(document.getComponents().getSchemas()).containsKey("Product");
    }

    @Test
    void anOpaqueOutputIsThePermissiveObjectThatDeclinesToEnumerateFields() {
        Capability read = Capability.reviewed("reviewed_getProductStats", inputs("id", Primitive.LONG),
                Primitive.STRING, false, new BindingRef("com.example.ProductService#statsFor"),
                List.of(new Annotation("description", List.of("Returns sales statistics for the product.")),
                        new Annotation("opaqueOutput", List.of("ProductStatsDto"))),
                Map.of(), review(ReviewLabel.READ));

        String json = OpenApiDocument.toJson(model(read));
        OpenAPI document = parse(json);

        Operation get = document.getPaths().get("/reviewed/getProductStats").getGet();
        var ok = get.getResponses().get("200").getContent().get("application/json").getSchema();
        assertThat(ok.getType()).isEqualTo("object");
        assertThat(ok.getProperties()).isNull();
        assertThat(ok.getAdditionalProperties()).isEqualTo(Boolean.TRUE);
        assertThat(ok.getDescription()).isEqualTo("Returns ProductStatsDto; the concrete field shape of "
                + "ProductStatsDto is not modeled, so this schema does not enumerate its fields.");
        assertThat(json).contains("\"x-tibyan-opaque-output\": \"ProductStatsDto\"");
        // The placeholder output never becomes a component or a $ref.
        assertThat(json).doesNotContain("#/components/schemas/STRING");
        assertThat(document.getComponents()).isNull();
    }

    @Test
    void rv12_aVerifiedDocumentGainsNoReviewedTagPathOrExtension() {
        Capability verified = Capability.read("getProduct", inputs("id", Primitive.LONG),
                Type.entityRef("Product"), new BindingRef("com.example.ProductService#findById"));

        String json = OpenApiDocument.toJson(model(verified));

        assertThat(json).doesNotContain("\"tags\"");
        assertThat(json).doesNotContain("/reviewed/");
        assertThat(json).doesNotContain("x-tibyan-reviewed");
        assertThat(json).doesNotContain("x-tibyan-review");
        assertThat(json).doesNotContain("\"post\"");
        assertThat(json).doesNotContain("Reviewed by");
    }
}
