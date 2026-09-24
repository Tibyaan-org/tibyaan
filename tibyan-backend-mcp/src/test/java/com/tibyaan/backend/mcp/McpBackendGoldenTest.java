package com.tibyaan.backend.mcp;

import com.tibyaan.core.ir.Annotation;
import com.tibyaan.core.ir.Attestation;
import com.tibyaan.core.ir.AttestationCategory;
import com.tibyaan.core.ir.AttestationOrigin;
import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityKind;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Field;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Review;
import com.tibyaan.core.ir.Shape;
import com.tibyaan.core.ir.ReviewLabel;
import com.tibyaan.core.ir.Type;
import com.tibyaan.core.semantic.SemanticValidator;
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

    // Set to true once to (re)write the reviewed golden, then back to false (the OpenAPI golden test's
    // pattern). When true the test writes the generated manifest to the golden path and fails, so a golden
    // is never silently trusted; when false it asserts byte equality. The three older goldens are never
    // rewritten by this flag: they are the byte-identity guard (fixture RV-12) and must stay untouched.
    private static final boolean WRITE_GOLDENS = false;

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

    @Test
    void surfacesTheReviewedTierAndTheWriteKinds(@TempDir Path outputRoot) throws IOException {
        CapabilityModel ir = reviewedIr();
        // The fixture is a legal IR under core rules 12 and 13, so the golden renders what a provider can
        // actually emit and not a shape the core would refuse.
        assertThat(new SemanticValidator().validate(ir).errors()).as("fixture is a valid IR").isEmpty();

        new McpBackend().generate(ir, new GenerationContext(outputRoot));

        // Phase DL-1 (Volume 6 section 3.9): a fourth golden case carrying one capability of every tier and
        // every kind. The verified and attested tools in it render exactly as the goldens above render
        // them, and those goldens are unchanged on disk, which is the byte-identity guard (fixture RV-12).
        String actual = normalize(Files.readString(outputRoot.resolve("tibyan/capabilities.json")));
        if (WRITE_GOLDENS) {
            Path goldenFile = Path.of("src", "test", "resources", "golden", "mcp-reviewed", "tibyan",
                    "capabilities.json");
            Files.createDirectories(goldenFile.getParent());
            Files.writeString(goldenFile, actual, StandardCharsets.UTF_8);
            throw new AssertionError("golden written: " + goldenFile + " (set WRITE_GOLDENS=false and re-run)");
        }
        String expected = normalize(readGolden("/golden/mcp-reviewed/tibyan/capabilities.json"));
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void surfacesTheDeclaredSecurityContract(@TempDir Path outputRoot) throws IOException {
        new McpBackend().generate(securityIr(), new GenerationContext(outputRoot));

        String manifest = normalize(Files.readString(outputRoot.resolve("tibyan/capabilities.json")));

        // A compiled contract: the any-of authority array (verbatim, never transformed) after the output,
        // plus the description clause the surface most MCP clients show (Volume 6 section 3.5).
        assertThat(manifest)
                .contains("\"requiredAuthorities\": [\"480012\", \"480013\"]")
                .contains("Requires any of the declared authorities: 480012, 480013.");
        // An unverifiable contract: the binary marker and the warn-and-name-the-reason clause. Never a
        // partial authority list, never silently treated as unprotected.
        assertThat(manifest)
                .contains("\"securityUnverifiable\": true")
                .contains("could not be structurally verified (unparseable-expression); resolve it before "
                        + "exposing this tool.");
        // Absence adds nothing: a read with no declared contract carries neither field (byte-identical).
        assertThat(manifest).doesNotContain("\"requiredAuthorities\": []");
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
                new BindingRef("com.example.ProductService#findById"));

        return new CapabilityModel(
                "legacy-spring-app", "0.1",
                List.of(product), List.of(getProduct), List.of(), Metadata.empty());
    }

    // A read discovered from a view-returning service method: output stays ENTITY_REF(Unit), and the
    // capability carries the view annotation the provider attaches (Volume 5 section 4.2.5).
    private static CapabilityModel viewIr() {
        Entity unit = new Entity(
                "Unit",
                "Unit",
                List.of(Field.of("id", Primitive.LONG, false)),
                List.of(),
                Metadata.empty());

        Map<String, Type> inputs = new LinkedHashMap<>();
        inputs.put("id", Primitive.LONG);

        Capability getUnit = Capability.of(
                "getUnit",
                CapabilityKind.READ,
                inputs,
                Type.entityRef("Unit"),
                false,
                true,
                new BindingRef("com.example.UnitServiceImpl#findById"),
                List.of(new Annotation("view", List.of("UnitVTO"))),
                null);

        return new CapabilityModel(
                "pms-backend", "0.1",
                List.of(unit), List.of(getUnit), List.of(), Metadata.empty());
    }

    // A list read discovered from a paged findAll: output stays ENTITY_REF(Contact) with outputMultiple
    // true and the paged annotation (Volume 5 section 4.10; Volume 6 section 3.3).
    private static CapabilityModel listIr() {
        Entity contact = new Entity(
                "Contact",
                "Contact",
                List.of(Field.of("id", Primitive.UUID, false)),
                List.of(),
                Metadata.empty());

        Capability listContact = Capability.readList(
                "listContact",
                new LinkedHashMap<>(),
                Type.entityRef("Contact"),
                new BindingRef("com.example.ContactService#findAll"),
                List.of(Annotation.flag("paged")));

        return new CapabilityModel(
                "erp-backend", "0.1",
                List.of(contact), List.of(listContact), List.of(), Metadata.empty());
    }

    // One capability of every tier and every kind (phase DL-1, Volume 5 section 4.17): the verified
    // example-app read, the ATT-4 attested predicate read, and four reviewed capabilities: a READ with an
    // enum-constrained input, a WRITE with a String input, a void ACTION with no inputs (an approved
    // UNKNOWN draft, decision D4) whose words are an ai-draft a named human approved, and a READ whose
    // output is an opaque DTO (RV-F, decision D5). The reviewed capabilities carry the annotations the
    // provider's draft intake attaches: `description` (the reviewer's sentence), `void`, `opaqueOutput`.
    // The output of a void or opaque capability is the IR's placeholder, which the annotations override.
    static CapabilityModel reviewedIr() {
        Entity product = new Entity("Product", "Product", List.of(
                Field.of("id", Primitive.LONG, false),
                Field.of("name", Primitive.STRING, false),
                Field.of("price", Primitive.DECIMAL, false)),
                List.of(), Metadata.empty());
        Entity tenant = new Entity("Tenant", "Tenant",
                List.of(Field.of("id", Primitive.LONG, false)), List.of(), Metadata.empty());

        Capability getProduct = Capability.read("getProduct", inputs("id", Primitive.LONG),
                Type.entityRef("Product"), new BindingRef("com.example.ProductService#findById"));

        Map<String, Type> searchInputs = new LinkedHashMap<>();
        searchInputs.put("fullName", Primitive.STRING);
        searchInputs.put("pageNum", Primitive.INTEGER);
        Map<String, String> meanings = new LinkedHashMap<>();
        meanings.put("fullName", "Matches tenants whose full name contains this text, case-insensitive.");
        meanings.put("pageNum", Attestation.PAGING_MARK);
        Attestation attestation = Attestation.data(AttestationCategory.PREDICATE,
                "Returns the tenants matching the given filters.", meanings, null, "a.elseginy",
                AttestationOrigin.HUMAN_AUTHORED, "2026-07-24", "sha256:abc123");
        Capability searchTenants = Capability.attested("attested_searchTenants", searchInputs,
                Type.entityRef("Tenant"), true, new BindingRef("com.example.TenantServiceImpl#getAllByFilter"),
                List.of(), attestation);

        Capability findByStatus = Capability.reviewed("reviewed_findProductsByStatus",
                inputs("status", Primitive.STRING), Type.entityRef("Product"), true,
                new BindingRef("com.example.ProductService#findByStatus"),
                List.of(new Annotation("description", List.of("Lists the products in the given status."))),
                Map.of("status", List.of("ACTIVE", "DISCONTINUED")),
                new Review(ReviewLabel.READ, "a.elseginy", "2026-09-23", Review.ORIGIN_HUMAN, "sha256:r1"));

        // Phase DL-2a (Volume 6 sections 3.11 and 4.7): a reviewed read whose input is carried as an array.
        Capability findByIds = Capability.reviewed("reviewed_findProductsByIds",
                inputs("ids", Primitive.LONG), Type.entityRef("Product"), true,
                new BindingRef("com.example.ProductService#findByIds"),
                List.of(new Annotation("description", List.of("Lists the products with the given ids."))),
                Map.of(),
                new Review(ReviewLabel.READ, "a.elseginy", "2026-09-24", Review.ORIGIN_HUMAN, "sha256:r5"),
                java.util.Set.of("ids"));

        // Phase DL-2b (Volume 6 sections 3.11 and 4.7): a reviewed write whose input is a structured object.
        Shape orderDraft = new Shape("com.example.OrderDraft", "OrderDraft", List.of(
                Field.of("customer", Primitive.STRING, true),
                Field.of("priority", Primitive.STRING, true),
                Field.of("tags", Primitive.STRING, true)),
                java.util.Set.of("tags"), Map.of("priority", List.of("LOW", "HIGH")),
                List.of(new Annotation(Shape.PARTIAL_ANNOTATION, List.of("extra: Map<String, Object>"))));
        Capability createOrder = Capability.reviewed("reviewed_createOrder",
                inputs("draft", Type.shapeRef("OrderDraft")), Primitive.LONG, false,
                new BindingRef("com.example.OrderService#create"),
                List.of(new Annotation("description", List.of("Creates an order from the draft and returns its id."))),
                Map.of(),
                new Review(ReviewLabel.WRITE, "a.elseginy", "2026-09-24", Review.ORIGIN_HUMAN, "sha256:r6"),
                java.util.Set.of());

        Capability createProduct = Capability.reviewed("reviewed_createProduct",
                inputs("name", Primitive.STRING), Primitive.LONG, false,
                new BindingRef("com.example.ProductService#create"),
                List.of(new Annotation("description",
                        List.of("Creates a product with the given name and returns its id."))),
                Map.of(),
                new Review(ReviewLabel.WRITE, "a.elseginy", "2026-09-23", Review.ORIGIN_HUMAN, "sha256:r2"));

        Capability reindexProducts = Capability.reviewed("reviewed_reindexProducts",
                new LinkedHashMap<>(), Primitive.STRING, false,
                new BindingRef("com.example.ProductService#reindex"),
                List.of(Annotation.flag("void"),
                        new Annotation("description", List.of("Rebuilds the product search index."))),
                Map.of(),
                new Review(ReviewLabel.UNKNOWN, "a.elseginy", "2026-09-23", Review.ORIGIN_AI_DRAFT,
                        "sha256:r3"));

        Capability productStats = Capability.reviewed("reviewed_getProductStats",
                inputs("id", Primitive.LONG), Primitive.STRING, false,
                new BindingRef("com.example.ProductService#statsFor"),
                List.of(new Annotation("description", List.of("Returns sales statistics for the product.")),
                        new Annotation("opaqueOutput", List.of("ProductStatsDto"))),
                Map.of(),
                new Review(ReviewLabel.READ, "b.reviewer", "2026-09-22", Review.ORIGIN_HUMAN, "sha256:r4"));

        return new CapabilityModel("shop", "0.1", List.of(product, tenant),
                List.of(getProduct, searchTenants, findByStatus, findByIds, createProduct, reindexProducts, productStats,
                        createOrder),
                List.of(), Metadata.empty(), List.of(orderDraft));
    }

    private static Map<String, Type> inputs(String name, Type type) {
        Map<String, Type> inputs = new LinkedHashMap<>();
        inputs.put(name, type);
        return inputs;
    }

    // Two reads carrying declared-security annotations (Volume 6 section 3.5): one COMPILED with a
    // two-authority any-of sequence, one SECURITY-UNVERIFIABLE with a reason token. A third read with no
    // annotation would be ABSENT and carry neither field.
    private static CapabilityModel securityIr() {
        Entity warehouse = new Entity("Warehouse", "Warehouse",
                List.of(Field.of("id", Primitive.LONG, false)), List.of(), Metadata.empty());
        Entity report = new Entity("Report", "Report",
                List.of(Field.of("id", Primitive.LONG, false)), List.of(), Metadata.empty());

        Map<String, Type> warehouseInputs = new LinkedHashMap<>();
        warehouseInputs.put("id", Primitive.LONG);
        Capability getWarehouse = Capability.of("getWarehouse", CapabilityKind.READ, warehouseInputs,
                Type.entityRef("Warehouse"), false, true, new BindingRef("com.example.WarehouseService#findById"),
                List.of(new Annotation("requiredAuthorities", List.of("480012", "480013"))), null);

        Map<String, Type> reportInputs = new LinkedHashMap<>();
        reportInputs.put("id", Primitive.LONG);
        Capability getReport = Capability.of("getReport", CapabilityKind.READ, reportInputs,
                Type.entityRef("Report"), false, true, new BindingRef("com.example.ReportService#findById"),
                List.of(new Annotation("securityUnverifiable", List.of("unparseable-expression"))), null);

        return new CapabilityModel("secured-app", "0.1",
                List.of(warehouse, report), List.of(getWarehouse, getReport), List.of(), Metadata.empty());
    }
}
