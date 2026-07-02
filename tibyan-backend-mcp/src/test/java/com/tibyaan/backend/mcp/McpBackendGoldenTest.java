package com.tibyaan.backend.mcp;

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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden-file test for the MCP backend (Volume 6 section 5): a fixed IR produces a fixed set of
 * files, byte for byte, so any change to generated output is a visible diff.
 */
class McpBackendGoldenTest {

    @Test
    void generatesTheGetProductToolAndManifest(@TempDir Path outputRoot) throws IOException {
        new McpBackend().generate(productIr(), new GenerationContext(outputRoot));

        assertMatchesGolden(outputRoot, "com/tibyaan/generated/mcp/GetProductTool.java");
        assertMatchesGolden(outputRoot, "tibyan/capabilities.json");

        assertThat(generatedFiles(outputRoot))
                .as("exactly two files are generated for one capability")
                .isEqualTo(2);
    }

    @Test
    void generatedToolNeverImportsAServiceOrFramework() throws IOException {
        Path outputRoot = Files.createTempDirectory("mcp-gen");
        new McpBackend().generate(productIr(), new GenerationContext(outputRoot));

        String tool = Files.readString(outputRoot.resolve("com/tibyaan/generated/mcp/GetProductTool.java"));

        assertThat(tool)
                .contains("import com.tibyaan.plugin.ExecutionAdapter;")
                .contains("import com.tibyaan.plugin.CapabilityRef;")
                .doesNotContain("springframework")
                .doesNotContain("ProductService")
                .doesNotContain("jakarta");
    }

    @Test
    void surfacesTheViewOfAViewReturningCapability(@TempDir Path outputRoot) throws IOException {
        new McpBackend().generate(viewIr(), new GenerationContext(outputRoot));

        // The manifest carries the view field and a description clause; a second golden case, kept
        // separate so the example-app golden above stays byte-identical (Volume 6 section 3.1).
        String actual = normalize(Files.readString(outputRoot.resolve("tibyan/capabilities.json")));
        String expected = normalize(readGolden("/golden/mcp-view/tibyan/capabilities.json"));
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void surfacesTheListShapeOfAListCapability(@TempDir Path outputRoot) throws IOException {
        new McpBackend().generate(listIr(), new GenerationContext(outputRoot));

        // A list tool carries the additive outputMultiple and paged fields and the honest "List all"
        // description; a third golden case, so the example-app and view goldens stay byte-identical
        // (Volume 6 section 3.3).
        String actual = normalize(Files.readString(outputRoot.resolve("tibyan/capabilities.json")));
        String expected = normalize(readGolden("/golden/mcp-list/tibyan/capabilities.json"));
        assertThat(actual).isEqualTo(expected);
    }

    private static void assertMatchesGolden(Path outputRoot, String relativePath) throws IOException {
        String actual = normalize(Files.readString(outputRoot.resolve(relativePath)));
        String expected = normalize(readGolden("/golden/mcp/" + relativePath));
        assertThat(actual).as(relativePath).isEqualTo(expected);
    }

    private static long generatedFiles(Path outputRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(outputRoot)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private static String readGolden(String resource) throws IOException {
        try (InputStream in = McpBackendGoldenTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("golden resource not found: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    // The Volume 4 example, as the provider produces it from the example app.
    private static CapabilityModel productIr() {
        Entity product = new Entity(
                "Product",
                List.of(
                        Field.of("id", Primitive.LONG, false),
                        Field.of("name", Primitive.STRING, false),
                        Field.of("price", Primitive.DECIMAL, false)),
                List.of(),
                Metadata.empty());

        Map<String, Type> inputs = new LinkedHashMap<>();
        inputs.put("id", Primitive.LONG);

        Capability getProduct = Capability.read(
                "getProduct",
                inputs,
                Type.entityRef("Product"),
                new BindingRef("ProductService#findById"));

        return new CapabilityModel(
                "legacy-spring-app", "0.1",
                List.of(product), List.of(getProduct), List.of(), Metadata.empty());
    }

    // A read discovered from a view-returning service method: output stays ENTITY_REF(Unit), and the
    // capability carries the view annotation the provider attaches (Volume 5 section 4.2.5).
    private static CapabilityModel viewIr() {
        Entity unit = new Entity(
                "Unit",
                List.of(Field.of("id", Primitive.LONG, false)),
                List.of(),
                Metadata.empty());

        Map<String, Type> inputs = new LinkedHashMap<>();
        inputs.put("id", Primitive.LONG);

        Capability getUnit = new Capability(
                "getUnit",
                CapabilityKind.READ,
                inputs,
                Type.entityRef("Unit"),
                false,
                true,
                new BindingRef("UnitServiceImpl#findById"),
                List.of(new Annotation("view", List.of("UnitVTO"))));

        return new CapabilityModel(
                "pms-backend", "0.1",
                List.of(unit), List.of(getUnit), List.of(), Metadata.empty());
    }

    // A list read discovered from a paged findAll: output stays ENTITY_REF(Contact) with outputMultiple
    // true and the paged annotation (Volume 5 section 4.10; Volume 6 section 3.3).
    private static CapabilityModel listIr() {
        Entity contact = new Entity(
                "Contact",
                List.of(Field.of("id", Primitive.UUID, false)),
                List.of(),
                Metadata.empty());

        Capability listContact = Capability.readList(
                "listContact",
                new LinkedHashMap<>(),
                Type.entityRef("Contact"),
                new BindingRef("ContactService#findAll"),
                List.of(Annotation.flag("paged")));

        return new CapabilityModel(
                "erp-backend", "0.1",
                List.of(contact), List.of(listContact), List.of(), Metadata.empty());
    }
}
