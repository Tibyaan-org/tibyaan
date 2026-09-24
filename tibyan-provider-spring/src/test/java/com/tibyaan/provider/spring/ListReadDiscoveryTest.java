package com.tibyaan.provider.spring;

import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.Type;
import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.provider.spring.EntityOutcome.Cause;
import com.tibyaan.provider.spring.EntityOutcome.Status;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase E4: the ratified list-read rule (Volume 5 section 4.10), exercised by the pre-registered
 * fixtures L1 to L12. Fixtures are read as source, never compiled.
 */
class ListReadDiscoveryTest {

    private static final Path LIST = Path.of("src", "test", "resources", "fixtures", "list");

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();

    @Test
    void l1_bareFindAllDiscoversAndIsNotPaged() {
        ScanReport report = scan("l1");
        Capability listAlpha = capability(report, "listAlpha");
        assertThat(listAlpha.binding().value()).isEqualTo("com.example.list.l1.AlphaService#findAll");
        assertThat(listAlpha.output()).isEqualTo(Type.entityRef("Alpha"));
        assertThat(listAlpha.outputMultiple()).isTrue();
        assertThat(listAlpha.inputs()).isEmpty();
        assertThat(paged(listAlpha)).isFalse();
        assertThat(listOutcome(report, "Alpha").status()).isEqualTo(ListOutcome.Status.DISCOVERED);
    }

    @Test
    void l2_pagedFindAllDiscoversWithThePagedDisclosure() {
        ScanReport report = scan("l2");
        Capability listBeta = capability(report, "listBeta");
        assertThat(listBeta.binding().value()).isEqualTo("com.example.list.l2.BetaService#findAll");
        assertThat(listBeta.outputMultiple()).isTrue();
        assertThat(listBeta.inputs()).isEmpty();
        assertThat(paged(listBeta)).isTrue();
    }

    @Test
    void l3_containerVarietyDoesNotChangeTheOutput() {
        ScanReport report = scan("l3");
        Capability listGamma = capability(report, "listGamma");
        assertThat(listGamma.outputMultiple()).isTrue();
        assertThat(paged(listGamma)).as("a Set is not a page envelope").isFalse();
        Capability listDelta = capability(report, "listDelta");
        assertThat(listDelta.outputMultiple()).isTrue();
        assertThat(paged(listDelta)).as("a Slice is a page envelope").isTrue();
    }

    @Test
    void l4_listIsIdentifierIndependentAndCoexistsWithAFailedGet() {
        ScanReport report = scan("l4");
        // The list-all discovers even though the by-id read cannot: an enum @Id maps to no bindable id.
        Capability listTemplate = capability(report, "listTemplate");
        assertThat(listTemplate.outputMultiple()).isTrue();
        assertThat(paged(listTemplate)).isTrue();
        assertThat(names(report)).doesNotContain("getTemplate");
        EntityOutcome template = entityOutcome(report, "Template");
        assertThat(template.status()).isEqualTo(Status.FAILED);
        assertThat(template.cause()).isEqualTo(Cause.NO_IDENTIFIER);
    }

    @Test
    void l5_filteredReadRefusesAndNeverClaimsAFilterInput() {
        ScanReport report = scan("l5");
        assertThat(names(report)).doesNotContain("listCat");
        assertThat(deferredMentioning(report, "categoryId")).isPresent();
        // The proof the tool never claims filter semantics: no capability carries the parameter as input.
        assertThat(report.model().capabilities())
                .allSatisfy(c -> assertThat(c.inputs()).doesNotContainKey("categoryId"));
    }

    @Test
    void l6_bodyFilteredReadRefuses() {
        ScanReport report = scan("l6");
        assertThat(names(report)).doesNotContain("listOrder");
        assertThat(deferredMentioning(report, "findCompleted")).isPresent();
    }

    @Test
    void l7_viewReturningListRefuses() {
        ScanReport report = scan("l7");
        assertThat(names(report)).doesNotContain("listItem", "listItemDto");
        assertThat(deferredMentioning(report, "ItemDto")).isPresent();
    }

    @Test
    void l8_nonFrameworkTerminalRefuses() {
        ScanReport report = scan("l8");
        assertThat(names(report)).doesNotContain("listNote");
        Optional<DeferredListRead> deferred = deferredMentioning(report, "findAllActive");
        assertThat(deferred).isPresent();
        assertThat(deferred.get().reason()).contains("findByActiveTrue").contains("findAll");
    }

    @Test
    void l9_mutationInBodyRefuses() {
        ScanReport report = scan("l9");
        assertThat(names(report)).doesNotContain("listTask");
        Optional<DeferredListRead> deferred = deferredMentioning(report, "findAll");
        assertThat(deferred).isPresent();
        assertThat(deferred.get().reason()).contains("mutation").contains("deleteAll");
    }

    @Test
    void l10_indistinguishablePairIsAmbiguous() {
        ScanReport report = scan("l10");
        assertThat(names(report)).doesNotContain("listPair");
        ListOutcome pair = listOutcome(report, "Pair");
        assertThat(pair.status()).isEqualTo(ListOutcome.Status.LIST_AMBIGUOUS);
        assertThat(pair.detail()).contains("PairService#findAll").contains("more than one");
    }

    @Test
    void l11_listBindsExactlyToTheRightEntitysCollection() {
        ScanReport report = scan("l11");
        Capability listBook = capability(report, "listBook");
        assertThat(listBook.binding().value()).isEqualTo("com.example.list.l11.BookService#findAll");
        assertThat(listBook.output()).isEqualTo(Type.entityRef("Book"));
        Capability listAuthor = capability(report, "listAuthor");
        assertThat(listAuthor.binding().value()).isEqualTo("com.example.list.l11.AuthorService#findAll");
        assertThat(listAuthor.output()).isEqualTo(Type.entityRef("Author"));
    }

    @Test
    void l12_getAndListCoexist() {
        ScanReport report = scan("l12");
        Capability getCar = capability(report, "getCar");
        assertThat(getCar.outputMultiple()).isFalse();
        assertThat(getCar.output()).isEqualTo(Type.entityRef("Car"));
        Capability listCar = capability(report, "listCar");
        assertThat(listCar.outputMultiple()).isTrue();
        assertThat(listCar.binding().value()).isEqualTo("com.example.list.l12.CarService#findAll");
        assertThat(entityOutcome(report, "Car").status()).isEqualTo(Status.DISCOVERED);
        assertThat(listOutcome(report, "Car").isDiscovered()).isTrue();
    }

    private ScanReport scan(String fixture) {
        return provider.scan(new ProviderInput(LIST.resolve(fixture)));
    }

    private static boolean paged(Capability capability) {
        return capability.annotations().stream().anyMatch(a -> a.key().equals("paged"));
    }

    private static java.util.List<String> names(ScanReport report) {
        return report.model().capabilities().stream().map(Capability::name).toList();
    }

    private static Capability capability(ScanReport report, String name) {
        return report.model().capabilities().stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("capability " + name + " not discovered"));
    }

    private static EntityOutcome entityOutcome(ScanReport report, String entityName) {
        return report.entityOutcomes().stream()
                .filter(o -> o.entityName().equals(entityName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no by-id outcome for " + entityName));
    }

    private static ListOutcome listOutcome(ScanReport report, String entityName) {
        return report.listOutcomes().stream()
                .filter(o -> o.entityName().equals(entityName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no list outcome for " + entityName));
    }

    private static Optional<DeferredListRead> deferredMentioning(ScanReport report, String needle) {
        return report.deferredListReads().stream()
                .filter(d -> d.method().contains(needle) || d.reason().contains(needle))
                .findFirst();
    }
}
