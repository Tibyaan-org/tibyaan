package com.tibyaan.backend.openapi;

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
import com.tibyaan.core.ir.ReviewLabel;
import com.tibyaan.core.ir.Type;
import com.tibyaan.core.semantic.SemanticValidator;
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

    @Test
    void listCapabilityRendersAsAnArrayOfTheElement(@TempDir Path out) throws IOException {
        assertGoldenAndValid(listIr(), out, "list.json");
    }

    @Test
    void reviewedOperationsRenderAsTaggedGetAndPost(@TempDir Path out) throws IOException {
        CapabilityModel ir = reviewedIr();
        // The fixture is a legal IR under core rules 12 and 13, so the golden renders what a provider can
        // actually emit and not a shape the core would refuse.
        assertThat(new SemanticValidator().validate(ir).errors()).as("fixture is a valid IR").isEmpty();
        // Phase DL-1 (Volume 6 section 4.6): a sixth golden carrying one capability of every tier and every
        // kind. The five goldens above are unchanged on disk: the byte-identity guard (fixture RV-12).
        assertGoldenAndValid(ir, out, "reviewed.json");
    }

    @Test
    void declaredSecurityContractRendersAsVendorExtensionsAndStaysValid(@TempDir Path out) throws IOException {
        new OpenApiBackend().generate(securityIr(), new GenerationContext(out));
        String actual = normalize(Files.readString(out.resolve("openapi.json")));

        // The vendor extensions are valid OpenAPI 3.x (never the native securitySchemes vocabulary, which
        // would fabricate a credential type the IR does not carry, Volume 6 section 4.3).
        SwaggerParseResult parsed = new OpenAPIV3Parser().readContents(actual, null, null);
        assertThat(parsed.getOpenAPI()).as("document parses as OpenAPI 3.x").isNotNull();
        assertThat(parsed.getMessages()).as("no OpenAPI validation messages").isEmpty();

        // A compiled contract: the authorities extension and the summary clause; an unverifiable one: the
        // marker extension and its clause. Never securitySchemes, never a native security requirement.
        assertThat(actual)
                .contains("\"x-tibyan-required-authorities\"")
                .contains("480012")
                .contains("Requires any of the declared authorities: 480012, 480013.")
                .contains("\"x-tibyan-security-unverifiable\": true")
                .contains("could not be structurally verified (unparseable-expression)")
                .doesNotContain("securitySchemes")
                .doesNotContain("\"security\"");
    }

    // Two reads carrying declared-security annotations (Volume 6 section 4.3): one COMPILED, one
    // SECURITY-UNVERIFIABLE. Distinct entities so the two GET operations are independent.
    private static CapabilityModel securityIr() {
        Entity warehouse = new Entity("Warehouse", "Warehouse",
                List.of(Field.of("id", Primitive.LONG, false)), List.of(), Metadata.empty());
        Entity report = new Entity("Report", "Report",
                List.of(Field.of("id", Primitive.LONG, false)), List.of(), Metadata.empty());
        Capability getWarehouse = Capability.of("getWarehouse", CapabilityKind.READ,
                inputs("id", Primitive.LONG), Type.entityRef("Warehouse"), false, true,
                new BindingRef("com.example.WarehouseService#findById"),
                List.of(new Annotation("requiredAuthorities", List.of("480012", "480013"))), null);
        Capability getReport = Capability.of("getReport", CapabilityKind.READ,
                inputs("id", Primitive.LONG), Type.entityRef("Report"), false, true,
                new BindingRef("com.example.ReportService#findById"),
                List.of(new Annotation("securityUnverifiable", List.of("unparseable-expression"))), null);
        return new CapabilityModel("secured-app", "0.1",
                List.of(warehouse, report), List.of(getWarehouse, getReport), List.of(), Metadata.empty());
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
        Entity product = new Entity("Product", "Product", List.of(
                Field.of("id", Primitive.LONG, false),
                Field.of("name", Primitive.STRING, false),
                Field.of("price", Primitive.DECIMAL, false)),
                List.of(), Metadata.empty());
        Capability getProduct = Capability.read("getProduct", inputs("id", Primitive.LONG),
                Type.entityRef("Product"), new BindingRef("com.example.ProductService#findById"));
        return new CapabilityModel("legacy-spring-app", "0.1",
                List.of(product), List.of(getProduct), List.of(), Metadata.empty());
    }

    // A view-returning read: the schema must decline to enumerate the view's fields.
    private static CapabilityModel viewIr() {
        Entity unit = new Entity("Unit", "Unit", List.of(Field.of("id", Primitive.LONG, false)),
                List.of(), Metadata.empty());
        Capability getUnit = Capability.of("getUnit", CapabilityKind.READ, inputs("id", Primitive.LONG),
                Type.entityRef("Unit"), false, true, new BindingRef("com.example.UnitServiceImpl#findById"),
                List.of(new Annotation("view", List.of("UnitVTO"))), null);
        return new CapabilityModel("pms-backend", "0.1",
                List.of(unit), List.of(getUnit), List.of(), Metadata.empty());
    }

    // A partial entity: two modeled fields, two skipped, disclosed via the partial annotation.
    private static CapabilityModel partialIr() {
        Entity gizmo = new Entity("Gizmo", "Gizmo", List.of(
                Field.of("id", Primitive.UUID, false),
                Field.of("name", Primitive.STRING, false)),
                List.of(new Annotation("partial", List.of("parts", "tags"))), Metadata.empty());
        Capability getGizmo = Capability.read("getGizmo", inputs("id", Primitive.UUID),
                Type.entityRef("Gizmo"), new BindingRef("com.example.GizmoService#findById"));
        return new CapabilityModel("widgets", "0.1",
                List.of(gizmo), List.of(getGizmo), List.of(), Metadata.empty());
    }

    // Every primitive, an enum field, a nullable field, and an entity reference to a second entity.
    private static CapabilityModel typesIr() {
        Map<String, Type> fields = new LinkedHashMap<>();
        Entity kind = new Entity("Kind", "Kind", List.of(Field.of("id", Primitive.UUID, false)),
                List.of(), Metadata.empty());
        Entity sample = new Entity("Sample", "Sample", List.of(
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
                Type.entityRef("Sample"), new BindingRef("com.example.SampleService#findById"));
        return new CapabilityModel("types", "0.1",
                List.of(sample, kind), List.of(getSample), List.of(), Metadata.empty());
    }

    // A paged list read: the 200 response is an array whose items are the element entity's component,
    // with the x-tibyan-paged disclosure (Volume 6 section 4.2).
    private static CapabilityModel listIr() {
        Entity contact = new Entity("Contact", "Contact", List.of(
                Field.of("id", Primitive.UUID, false),
                Field.of("name", Primitive.STRING, false)),
                List.of(), Metadata.empty());
        Capability listContact = Capability.readList("listContact", new LinkedHashMap<>(),
                Type.entityRef("Contact"), new BindingRef("com.example.ContactService#findAll"),
                List.of(new Annotation("paged", List.of())));
        return new CapabilityModel("erp-backend", "0.1",
                List.of(contact), List.of(listContact), List.of(), Metadata.empty());
    }

    // One capability of every tier and every kind (phase DL-1, Volume 5 section 4.17): the verified
    // example-app read, the ATT-4 attested predicate read, and four reviewed capabilities: a READ with an
    // enum-constrained input, a WRITE with a String input, a void ACTION with no inputs (an approved
    // UNKNOWN draft, decision D4) whose words are an ai-draft a named human approved, and a READ whose
    // output is an opaque DTO (RV-F, decision D5). The reviewed capabilities carry the annotations the
    // provider's draft intake attaches: `description` (the reviewer's sentence), `void`, `opaqueOutput`.
    // The output of a void or opaque capability is the IR's placeholder, which the annotations override.
    // The same fixture as the MCP module's, so the two surfaces are checked over one IR.
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
                List.of(getProduct, searchTenants, findByStatus, findByIds, createProduct, reindexProducts, productStats),
                List.of(), Metadata.empty());
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
