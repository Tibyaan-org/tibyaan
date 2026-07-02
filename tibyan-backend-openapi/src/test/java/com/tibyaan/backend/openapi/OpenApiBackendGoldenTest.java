package com.tibyaan.backend.openapi;

import com.tibyaan.core.ir.Annotation;
import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityKind;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Field;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Type;
import com.tibyaan.plugin.GenerationContext;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden-file test for the OpenAPI backend (Volume 6 sections 4 and 4.1): a fixed IR produces a
 * fixed document byte for byte, and every generated document is validated against the OpenAPI 3.x
 * schema by swagger-parser. Four IRs cover the shapes: a complete entity-returning capability, a
 * view capability (the honesty refusal), a partial entity (the disclosure), and the full type set
 * with an enum, a nullable field, and an entity reference.
 */
class OpenApiBackendGoldenTest {

    // Set to true once to (re)write the golden resources, then back to false. When true the test
    // writes each generated document to the golden path and fails, so a golden is never silently
    // trusted; when false it asserts byte equality.
    private static final boolean WRITE_GOLDENS = false;

    @Test
    void completeEntityReturningCapability(@TempDir Path out) throws IOException {
        assertGoldenAndValid(completeIr(), out, "complete.json");
    }

    @Test
    void viewCapabilityDeclinesToEnumerateFields(@TempDir Path out) throws IOException {
        assertGoldenAndValid(viewIr(), out, "view.json");
    }

    @Test
    void partialEntityDisclosesItsOmittedFields(@TempDir Path out) throws IOException {
        assertGoldenAndValid(partialIr(), out, "partial.json");
    }

    @Test
    void fullTypeSetWithEnumNullableAndReference(@TempDir Path out) throws IOException {
        assertGoldenAndValid(typesIr(), out, "types.json");
    }

    private static void assertGoldenAndValid(CapabilityModel ir, Path out, String golden) throws IOException {
        new OpenApiBackend().generate(ir, new GenerationContext(out));
        String actual = normalize(Files.readString(out.resolve("openapi.json")));

        // Every generated document must be a valid OpenAPI 3.x document, with no parse messages.
        SwaggerParseResult parsed = new OpenAPIV3Parser().readContents(actual, null, null);
        assertThat(parsed.getOpenAPI()).as("document parses as OpenAPI 3.x").isNotNull();
        assertThat(parsed.getMessages()).as("no OpenAPI validation messages").isEmpty();

        if (WRITE_GOLDENS) {
            Path goldenFile = Path.of("src", "test", "resources", "golden", "openapi", golden);
            Files.createDirectories(goldenFile.getParent());
            Files.writeString(goldenFile, actual, StandardCharsets.UTF_8);
            throw new AssertionError("golden written: " + goldenFile + " (set WRITE_GOLDENS=false and re-run)");
        }
        assertThat(actual).as(golden).isEqualTo(normalize(readGolden(golden)));
    }

    // The legacy example app, an entity-returning read of a complete entity.
    private static CapabilityModel completeIr() {
        Entity product = new Entity("Product", List.of(
                Field.of("id", Primitive.LONG, false),
                Field.of("name", Primitive.STRING, false),
                Field.of("price", Primitive.DECIMAL, false)),
                List.of(), Metadata.empty());
        Capability getProduct = Capability.read("getProduct", inputs("id", Primitive.LONG),
                Type.entityRef("Product"), new BindingRef("ProductService#findById"));
        return new CapabilityModel("legacy-spring-app", "0.1",
                List.of(product), List.of(getProduct), List.of(), Metadata.empty());
    }

    // A view-returning read: the schema must decline to enumerate the view's fields.
    private static CapabilityModel viewIr() {
        Entity unit = new Entity("Unit", List.of(Field.of("id", Primitive.LONG, false)),
                List.of(), Metadata.empty());
        Capability getUnit = new Capability("getUnit", CapabilityKind.READ, inputs("id", Primitive.LONG),
                Type.entityRef("Unit"), true, new BindingRef("UnitServiceImpl#findById"),
                List.of(new Annotation("view", List.of("UnitVTO"))));
        return new CapabilityModel("pms-backend", "0.1",
                List.of(unit), List.of(getUnit), List.of(), Metadata.empty());
    }

    // A partial entity: two modeled fields, two skipped, disclosed via the partial annotation.
    private static CapabilityModel partialIr() {
        Entity gizmo = new Entity("Gizmo", List.of(
                Field.of("id", Primitive.UUID, false),
                Field.of("name", Primitive.STRING, false)),
                List.of(new Annotation("partial", List.of("parts", "tags"))), Metadata.empty());
        Capability getGizmo = Capability.read("getGizmo", inputs("id", Primitive.UUID),
                Type.entityRef("Gizmo"), new BindingRef("GizmoService#findById"));
        return new CapabilityModel("widgets", "0.1",
                List.of(gizmo), List.of(getGizmo), List.of(), Metadata.empty());
    }

    // Every primitive, an enum field, a nullable field, and an entity reference to a second entity.
    private static CapabilityModel typesIr() {
        Map<String, Type> fields = new LinkedHashMap<>();
        Entity kind = new Entity("Kind", List.of(Field.of("id", Primitive.UUID, false)),
                List.of(), Metadata.empty());
        Entity sample = new Entity("Sample", List.of(
                Field.of("id", Primitive.UUID, false),
                Field.of("name", Primitive.STRING, false),
                Field.of("count", Primitive.INTEGER, false),
                Field.of("size", Primitive.LONG, false),
                Field.of("price", Primitive.DECIMAL, false),
                Field.of("ratio", Primitive.FLOAT, false),
                Field.of("active", Primitive.BOOLEAN, false),
                Field.of("createdOn", Primitive.TIMESTAMP, false),
                Field.of("note", Primitive.STRING, true),
                new Field("status", Primitive.STRING, false,
                        List.of(new Annotation("enum", List.of("NEW", "DONE")))),
                Field.of("kind", Type.entityRef("Kind"), false)),
                List.of(), Metadata.empty());
        Capability getSample = Capability.read("getSample", inputs("id", Primitive.UUID),
                Type.entityRef("Sample"), new BindingRef("SampleService#findById"));
        return new CapabilityModel("types", "0.1",
                List.of(sample, kind), List.of(getSample), List.of(), Metadata.empty());
    }

    private static Map<String, Type> inputs(String name, Type type) {
        Map<String, Type> inputs = new LinkedHashMap<>();
        inputs.put(name, type);
        return inputs;
    }

    private static String readGolden(String name) throws IOException {
        try (InputStream in = OpenApiBackendGoldenTest.class.getResourceAsStream("/golden/openapi/" + name)) {
            if (in == null) {
                throw new IllegalStateException("golden resource not found: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }
}
