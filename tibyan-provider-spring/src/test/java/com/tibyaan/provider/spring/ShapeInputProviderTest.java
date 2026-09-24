package com.tibyaan.provider.spring;

import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityKind;
import com.tibyaan.core.ir.Field;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Shape;
import com.tibyaan.core.ir.Type;
import com.tibyaan.core.semantic.SemanticValidator;
import com.tibyaan.core.semantic.ValidationResult;
import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.provider.spring.draft.DraftEntry;
import com.tibyaan.provider.spring.draft.DraftInput;
import com.tibyaan.provider.spring.draft.DraftRefusal;
import com.tibyaan.provider.spring.draft.DraftRefusal.Cause;
import com.tibyaan.provider.spring.draft.DraftReport;
import com.tibyaan.provider.spring.draft.DraftsFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase DL-2b, structured inputs (Volume 5 section 4.19.3, fixtures SI-1 to SI-10 and SI-12 on the provider
 * side; SI-11's runtime half is in tibyan-runtime-spring). A DTO the runtime can construct binds as a shape,
 * with its fields, its closed sets, its nested shapes and its skipped fields disclosed.
 */
class ShapeInputProviderTest {

    private static final Path FIXTURES = Path.of("src", "test", "resources", "fixtures");
    private static final Path FIXTURE = FIXTURES.resolve("shape-inputs").resolve("ShapeInputs.java");
    private static final String REVIEWER = "a.reviewer";
    private static final String DATE = "2026-09-24";

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();
    private final SemanticValidator validator = new SemanticValidator();

    @Test
    void theDraftsFileShowsTheWholeObjectOfAShapeInput(@TempDir Path root) throws IOException {
        copyFixture(root);
        DraftReport report = provider.draft(new ProviderInput(root));

        DraftInput draft = entry(report, "OrderServiceImpl_create").inputs().get(0);
        assertThat(draft.type()).isEqualTo("SHAPE_REF(OrderDraft)");
        assertThat(draft.shape().key()).isEqualTo("com.example.shape.OrderDraft");
        assertThat(draft.shape().fields()).extracting(f -> f.name() + ":" + f.type())
                .containsExactly("customer:STRING", "quantity:INTEGER", "priority:STRING", "address:SHAPE_REF(Address)",
                        "tags:STRING");
        assertThat(draft.shape().fields().get(2).values()).containsExactly("LOW", "HIGH");
        assertThat(draft.shape().fields().get(3).shape().fields()).extracting(f -> f.name()).containsExactly("street", "city");
        assertThat(draft.shape().fields().get(4).multiple()).isTrue();
        assertThat(draft.shape().skipped()).containsExactly("order: Order (entity, never an input: decision D8)",
                "extra: Map<String,Object> (not in the scanned tree)");
        // A class the tool cannot construct names why, so the reviewer sees the refusal before approving.
        assertThat(entry(report, "OrderServiceImpl_immutable").inputs().get(0).unmapped())
                .contains("SHAPE_NOT_CONSTRUCTIBLE");
        assertThat(entry(report, "OrderServiceImpl_attach").inputs().get(0).unmapped()).contains("decision D8");
        // The file round-trips with the nested shapes.
        Path file = DraftsFile.defaultLocation(root);
        DraftsFile.write(file, report);
        assertThat(DraftsFile.read(file)).isEqualTo(report.drafts());
    }

    @Test
    void si1_si3_si4_si5_aLombokDtoBindsWithItsFieldsClosedSetsNestedShapeAndPartialDisclosure(@TempDir Path root)
            throws IOException {
        ScanReport report = approveAndScan(root, "OrderServiceImpl_create", "Creates an order from the draft.");

        Capability create = capability(report, "reviewed_OrderServiceImpl_create");
        assertThat(create.kind()).isEqualTo(CapabilityKind.WRITE);
        assertThat(create.inputs()).containsExactly(Map.entry("draft", Type.shapeRef("OrderDraft")));
        assertThat(report.model().shapes()).extracting(Shape::name).containsExactlyInAnyOrder("OrderDraft", "Address");

        Shape orderDraft = shape(report, "OrderDraft");
        assertThat(orderDraft.key()).isEqualTo("com.example.shape.OrderDraft");
        assertThat(orderDraft.fields()).containsExactly(
                Field.of("customer", Primitive.STRING, true),
                Field.of("quantity", Primitive.INTEGER, true),
                Field.of("priority", Primitive.STRING, true),
                Field.of("address", Type.shapeRef("Address"), true),
                Field.of("tags", Primitive.STRING, true));
        assertThat(orderDraft.fieldValues()).containsExactly(Map.entry("priority", List.of("LOW", "HIGH")));
        assertThat(orderDraft.multipleFields()).containsExactly("tags");
        assertThat(orderDraft.partialFields()).containsExactly("order: Order (entity, never an input: decision D8)",
                "extra: Map<String,Object> (not in the scanned tree)");
        assertThat(shape(report, "Address").fields()).extracting(Field::name).containsExactly("street", "city");
        assertValid(report);
    }

    @Test
    void si2_aRecordBindsThroughItsCanonicalConstructor(@TempDir Path root) throws IOException {
        ScanReport report = approveAndScan(root, "OrderServiceImpl_locate", "Formats the coordinates.");

        Capability locate = capability(report, "reviewed_OrderServiceImpl_locate");
        assertThat(locate.inputs()).containsExactly(Map.entry("coordinates", Type.shapeRef("Coordinates")));
        assertThat(shape(report, "Coordinates").fields()).containsExactly(
                Field.of("lat", Primitive.FLOAT, true), Field.of("lng", Primitive.FLOAT, true));
        assertThat(shape(report, "Coordinates").partialFields()).isEmpty();
        assertValid(report);
    }

    @Test
    void aListOfADtoBindsAsTheShapeMultiple(@TempDir Path root) throws IOException {
        ScanReport report = approveAndScan(root, "OrderServiceImpl_bulk", "Creates several orders.");

        Capability bulk = capability(report, "reviewed_OrderServiceImpl_bulk");
        assertThat(bulk.inputs()).containsExactly(Map.entry("drafts", Type.shapeRef("OrderDraft")));
        assertThat(bulk.multipleInputs()).containsExactly("drafts");
        assertValid(report);
    }

    @Test
    void si6_aDtoWithNoMappableFieldRefusesShapeEmpty(@TempDir Path root) throws IOException {
        assertThat(approveAndRefuse(root, "OrderServiceImpl_empty").detail())
                .contains("parameter 'empty'").contains("SHAPE_EMPTY");
    }

    @Test
    void si7_anAllArgumentsOnlyConstructorRefusesShapeNotConstructible(@TempDir Path root) throws IOException {
        assertThat(approveAndRefuse(root, "OrderServiceImpl_immutable").detail())
                .contains("parameter 'immutable'").contains("SHAPE_NOT_CONSTRUCTIBLE");
    }

    @Test
    void si8_anEntityParameterRefuses(@TempDir Path root) throws IOException {
        assertThat(approveAndRefuse(root, "OrderServiceImpl_attach").detail())
                .contains("parameter 'order'").contains("persistence object").contains("D8");
    }

    @Test
    void si9_aSelfReferentialDtoRefusesShapeCycle(@TempDir Path root) throws IOException {
        assertThat(approveAndRefuse(root, "OrderServiceImpl_tree").detail())
                .contains("parameter 'node'").contains("SHAPE_CYCLE");
    }

    @Test
    void si10_twoSameSimpleNameDtosGetDistinctDisplayNamesAndKeys(@TempDir Path root) throws IOException {
        Path source = FIXTURES.resolve("shape-inputs-collision");
        Files.createDirectories(root.resolve("crm"));
        Files.createDirectories(root.resolve("support"));
        Files.copy(source.resolve("crm").resolve("Filter.java"), root.resolve("crm").resolve("Filter.java"));
        Files.copy(source.resolve("support").resolve("Filter.java"), root.resolve("support").resolve("Filter.java"));
        Files.copy(source.resolve("support").resolve("SearchServiceImpl.java"),
                root.resolve("support").resolve("SearchServiceImpl.java"));
        DraftReport drafts = provider.draft(new ProviderInput(root));
        List<DraftEntry> approved = new ArrayList<>();
        approved.add(approved(entry(drafts, "SearchServiceImpl_crm"), "Searches crm."));
        approved.add(approved(entry(drafts, "SearchServiceImpl_support"), "Searches support."));
        DraftsFile.write(DraftsFile.defaultLocation(root), approved);

        ScanReport report = provider.scan(new ProviderInput(root));

        assertThat(report.draftRefusals()).isEmpty();
        assertThat(report.model().shapes()).extracting(Shape::key)
                .containsExactlyInAnyOrder("example.crm.shape.Filter", "example.support.shape.Filter");
        assertThat(report.model().shapes()).extracting(Shape::name).containsExactlyInAnyOrder("CrmFilter", "SupportFilter");
        assertThat(capability(report, "reviewed_SearchServiceImpl_crm").inputs().get("filter"))
                .isEqualTo(Type.shapeRef("CrmFilter"));
        assertThat(capability(report, "reviewed_SearchServiceImpl_support").inputs().get("filter"))
                .isEqualTo(Type.shapeRef("SupportFilter"));
        assertValid(report);
    }

    @Test
    void si12_aDtoFieldChangeFlipsTheApprovedEntryToStale(@TempDir Path root) throws IOException {
        copyFixture(root);
        DraftEntry create = entry(provider.draft(new ProviderInput(root)), "OrderServiceImpl_create");
        DraftsFile.write(DraftsFile.defaultLocation(root), List.of(approved(create, "Creates an order from the draft.")));
        Path copy = root.resolve("ShapeInputs.java");
        String source = Files.readString(copy, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(source).contains("    private Integer quantity;\n");
        Files.writeString(copy, source.replace("    private Integer quantity;\n",
                "    private Integer quantity;\n    private String note;\n"), StandardCharsets.UTF_8);

        ScanReport report = provider.scan(new ProviderInput(root));

        assertThat(report.draftRefusals()).hasSize(1);
        DraftRefusal refusal = report.draftRefusals().get(0);
        assertThat(refusal.cause()).as(refusal.detail()).isEqualTo(Cause.STALE_DRAFT);
        assertThat(report.reviewedCount()).isZero();
    }

    private ScanReport approveAndScan(Path root, String name, String description) throws IOException {
        copyFixture(root);
        DraftEntry draft = entry(provider.draft(new ProviderInput(root)), name);
        DraftsFile.write(DraftsFile.defaultLocation(root), List.of(approved(draft, description)));
        ScanReport report = provider.scan(new ProviderInput(root));
        assertThat(report.draftRefusals()).as("refusals").isEmpty();
        return report;
    }

    private DraftRefusal approveAndRefuse(Path root, String name) throws IOException {
        copyFixture(root);
        DraftEntry draft = entry(provider.draft(new ProviderInput(root)), name);
        DraftsFile.write(DraftsFile.defaultLocation(root), List.of(approved(draft, "A description.")));
        ScanReport report = provider.scan(new ProviderInput(root));
        assertThat(report.draftRefusals()).as("refusals").hasSize(1);
        DraftRefusal refusal = report.draftRefusals().get(0);
        assertThat(refusal.cause()).as(refusal.detail()).isEqualTo(Cause.INPUT_UNMAPPED);
        assertThat(report.reviewedCount()).isZero();
        assertThat(report.model().shapes()).isEmpty();
        return refusal;
    }

    private void assertValid(ScanReport report) {
        ValidationResult result = validator.validate(report.model());
        assertThat(result.isValid()).as("validation errors: %s", result.errors()).isTrue();
    }

    private static Shape shape(ScanReport report, String name) {
        return report.model().shapes().stream().filter(s -> s.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no shape named " + name + " among "
                        + report.model().shapes().stream().map(Shape::name).toList()));
    }

    private static Capability capability(ScanReport report, String name) {
        return report.model().capabilities().stream().filter(c -> c.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no capability " + name + " among "
                        + report.model().capabilities().stream().map(Capability::name).toList()
                        + "; refusals " + report.draftRefusals()));
    }

    private static DraftEntry approved(DraftEntry d, String description) {
        return new DraftEntry(d.name(), d.target(), d.binding(), d.label(), d.reason(), d.capability(),
                d.returnType(), new ArrayList<>(d.inputs()), description, DraftEntry.APPROVED, REVIEWER, DATE,
                DraftEntry.ORIGIN_HUMAN, d.fingerprint(), false, null);
    }

    private static DraftEntry entry(DraftReport report, String name) {
        return report.drafts().stream().filter(d -> d.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no draft named " + name + " among "
                        + report.drafts().stream().map(DraftEntry::name).toList()));
    }

    private static void copyFixture(Path root) throws IOException {
        Files.write(root.resolve("ShapeInputs.java"), Files.readAllBytes(FIXTURE));
    }
}
