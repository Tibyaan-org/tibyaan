package com.tibyaan.provider.spring;

import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.semantic.SemanticValidator;
import com.tibyaan.core.semantic.ValidationResult;
import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.provider.spring.draft.DraftEntry;
import com.tibyaan.provider.spring.draft.DraftInput;
import com.tibyaan.provider.spring.draft.DraftRefusal;
import com.tibyaan.provider.spring.draft.DraftRefusal.Cause;
import com.tibyaan.provider.spring.draft.DraftReport;
import com.tibyaan.provider.spring.draft.DraftsFile;
import com.tibyaan.provider.spring.extractors.CollectionParameter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase DL-2a, collection inputs (Volume 5 section 4.19.2, fixtures CI-1 to CI-6 on the provider side; CI-3's
 * and CI-7's runtime halves are in tibyan-runtime-spring). A List, Set, Collection or Iterable of a modeled
 * primitive or an in-tree enum binds as its element type carried as an array; a raw, wildcard or nested
 * collection refuses by name.
 */
class CollectionInputProviderTest {

    private static final Path FIXTURE =
            Path.of("src", "test", "resources", "fixtures", "collection-inputs", "CollectionInputs.java");
    private static final String REVIEWER = "a.reviewer";
    private static final String DATE = "2026-09-24";

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();
    private final SemanticValidator validator = new SemanticValidator();

    @Test
    void theDraftsFileShowsTheElementTypeAndMultipleOnEveryCollectionInput(@TempDir Path root) throws IOException {
        copyFixture(root);
        DraftReport report = provider.draft(new ProviderInput(root));

        assertThat(entry(report, "TicketServiceImpl_byIds").inputs())
                .containsExactly(new DraftInput("ids", "List<Long>", "LONG", Boolean.TRUE));
        assertThat(entry(report, "TicketServiceImpl_byCodes").inputs())
                .containsExactly(new DraftInput("codes", "Set<String>", "STRING", Boolean.TRUE));
        // An enum element maps at intake, exactly as a plain enum parameter does, so its type is null here.
        assertThat(entry(report, "TicketServiceImpl_byStatuses").inputs())
                .containsExactly(new DraftInput("statuses", "Collection<TicketStatus>", null, Boolean.TRUE));
        // A parameter that is not a collection carries no multiple field at all (byte-identity of old entries).
        assertThat(entry(report, "TicketServiceImpl_rawIds").inputs().get(0).multiple()).isNull();
    }

    @Test
    void ci1_aListOfLongBindsAsLongMultiple(@TempDir Path root) throws IOException {
        Capability reviewed = approveAndScan(root, "TicketServiceImpl_byIds", "Reads the tickets with these ids.");

        assertThat(reviewed.inputs()).containsExactly(Map.entry("ids", Primitive.LONG));
        assertThat(reviewed.multipleInputs()).containsExactly("ids");
        assertThat(reviewed.inputValues()).isEmpty();
    }

    @Test
    void ci2_aSetOfStringBindsAsStringMultiple(@TempDir Path root) throws IOException {
        Capability reviewed = approveAndScan(root, "TicketServiceImpl_byCodes", "Reads the tickets with these codes.");

        assertThat(reviewed.inputs()).containsExactly(Map.entry("codes", Primitive.STRING));
        assertThat(reviewed.multipleInputs()).containsExactly("codes");
    }

    @Test
    void ci3_aCollectionOfAnInTreeEnumCarriesItsClosedSetOnTheElement(@TempDir Path root) throws IOException {
        Capability reviewed = approveAndScan(root, "TicketServiceImpl_byStatuses",
                "Reads the tickets in any of these statuses.");

        assertThat(reviewed.inputs()).containsExactly(Map.entry("statuses", Primitive.STRING));
        assertThat(reviewed.multipleInputs()).containsExactly("statuses");
        assertThat(reviewed.inputValues()).containsExactly(Map.entry("statuses", List.of("OPEN", "CLOSED")));
    }

    @Test
    void ci4_aRawListRefusesNamingTheParameter(@TempDir Path root) throws IOException {
        DraftRefusal refusal = approveAndRefuse(root, "TicketServiceImpl_rawIds");

        assertThat(refusal.detail()).contains("parameter 'ids'").contains("raw List").contains("R1");
    }

    @Test
    void ci5_aWildcardElementRefusesNamingTheParameter(@TempDir Path root) throws IOException {
        DraftRefusal refusal = approveAndRefuse(root, "TicketServiceImpl_wildIds");

        assertThat(refusal.detail()).contains("parameter 'ids'").contains("wildcard");
    }

    @Test
    void ci6_aNestedCollectionRefusesNamingTheParameter(@TempDir Path root) throws IOException {
        DraftRefusal refusal = approveAndRefuse(root, "TicketServiceImpl_nestedIds");

        assertThat(refusal.detail()).contains("parameter 'ids'").contains("nested collection");
    }

    @Test
    void theClassifierNamesEveryShapeItRefuses() {
        assertThat(CollectionParameter.classify("Long").isCollection()).isFalse();
        assertThat(CollectionParameter.classify("java.util.List<Long>").element()).isEqualTo("Long");
        assertThat(CollectionParameter.classify("Iterable<String>").element()).isEqualTo("String");
        assertThat(CollectionParameter.classify("Long[]").refusal()).contains("array");
        assertThat(CollectionParameter.classify("Map<String, Long>").isCollection()).isFalse();
        assertThat(CollectionParameter.classify("List<Map<String, Long>>").refusal()).contains("nested");
    }

    private Capability approveAndScan(Path root, String name, String description) throws IOException {
        copyFixture(root);
        DraftEntry draft = entry(provider.draft(new ProviderInput(root)), name);
        DraftsFile.write(DraftsFile.defaultLocation(root), List.of(approved(draft, description)));

        ScanReport report = provider.scan(new ProviderInput(root));

        assertThat(report.draftRefusals()).as("refusals").isEmpty();
        ValidationResult result = validator.validate(report.model());
        assertThat(result.isValid()).as("validation errors: %s", result.errors()).isTrue();
        return report.model().capabilities().stream()
                .filter(c -> c.name().equals("reviewed_" + name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no capability reviewed_" + name + " among "
                        + report.model().capabilities().stream().map(Capability::name).toList()));
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
        return refusal;
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
        Files.write(root.resolve("CollectionInputs.java"), Files.readAllBytes(FIXTURE));
    }
}
