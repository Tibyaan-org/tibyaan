package com.tibyaan.provider.spring;

import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.provider.spring.draft.DraftEntry;
import com.tibyaan.provider.spring.draft.DraftInput;
import com.tibyaan.provider.spring.draft.DraftLabel;
import com.tibyaan.provider.spring.draft.DraftReport;
import com.tibyaan.provider.spring.draft.DraftsFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spike DL-0, draft and label (Volume 13 section 3, "v2 direction"). The labeler adds no proof: VERIFIED and
 * ATTESTED come from the scan, the rest from read-safety. These tests pin the label of every shape the fixture
 * covers, the two spike-failure conditions (a discovered binding not VERIFIED, a confirmed attestation target
 * not ATTESTED), and the determinism of the file.
 */
class DraftLabelerTest {

    private static final Path FIXTURE = Path.of("src", "test", "resources", "fixtures", "draft-labels");
    private static final Path ATTEST_FIXTURE =
            Path.of("src", "test", "resources", "fixtures", "attest-predicate", "AttestPredicate.java");
    private static final String FILTER_TARGET =
            "com.example.attest.TenantServiceImpl#getAllByFilter(String, Integer)";

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();

    @Test
    void everyPublicMethodOfAConcreteServiceIsDraftedWithItsLabel() {
        DraftReport report = provider.draft(new ProviderInput(FIXTURE));
        Map<String, DraftLabel> labels = new LinkedHashMap<>();
        report.drafts().forEach(d -> labels.put(d.name(), d.label()));

        assertThat(labels).containsExactly(
                Map.entry("OrderServiceImpl_archive", DraftLabel.WRITE),
                Map.entry("OrderServiceImpl_closeAll", DraftLabel.WRITE),
                Map.entry("OrderServiceImpl_count", DraftLabel.READ),
                Map.entry("OrderServiceImpl_create", DraftLabel.WRITE),
                Map.entry("OrderServiceImpl_describe", DraftLabel.READ),
                Map.entry("OrderServiceImpl_flushAll", DraftLabel.WRITE),
                Map.entry("OrderServiceImpl_getById", DraftLabel.VERIFIED),
                Map.entry("OrderServiceImpl_notify", DraftLabel.UNKNOWN),
                Map.entry("OrderServiceImpl_purge", DraftLabel.WRITE),
                Map.entry("OrderServiceImpl_raw", DraftLabel.UNKNOWN),
                Map.entry("OrderServiceImpl_search", DraftLabel.READ),
                Map.entry("OrderServiceImpl_search_2", DraftLabel.READ),
                Map.entry("OrderServiceImpl_sync", DraftLabel.WRITE));

        // The abstract service and the interface contribute nothing; the static factory is not a method.
        assertThat(report.drafts()).noneMatch(d -> d.binding().startsWith("AbstractAuditService"));
        assertThat(report.counts()).containsExactly(
                Map.entry("VERIFIED", 1), Map.entry("ATTESTED", 0), Map.entry("READ", 4),
                Map.entry("WRITE", 6), Map.entry("UNKNOWN", 2));
    }

    @Test
    void aVerifiedDraftCarriesTheCapabilityAndTheAttestationTargetFormat() {
        DraftReport report = provider.draft(new ProviderInput(FIXTURE));
        DraftEntry getById = find(report, "OrderServiceImpl_getById");

        assertThat(getById.capability()).isEqualTo("getOrder");
        assertThat(getById.target()).isEqualTo("com.example.draft.OrderServiceImpl#getById(Long)");
        assertThat(getById.binding()).isEqualTo("OrderServiceImpl#getById");
        assertThat(getById.returnType()).isEqualTo("Order");
        assertThat(getById.inputs()).containsExactly(new DraftInput("id", "Long", "LONG"));
        assertThat(getById.description()).isEmpty();
        assertThat(getById.review()).isEqualTo(DraftEntry.PENDING);
    }

    @Test
    void inputsKeepTheJavaTypeAndMapToNullWhenTheMapperCannot() {
        DraftReport report = provider.draft(new ProviderInput(FIXTURE));

        assertThat(find(report, "OrderServiceImpl_count").inputs())
                .containsExactly(new DraftInput("ids", "List<Long>", null));
        assertThat(find(report, "OrderServiceImpl_count").target())
                .isEqualTo("com.example.draft.OrderServiceImpl#count(List)");
        assertThat(find(report, "OrderServiceImpl_search_2").inputs())
                .containsExactly(new DraftInput("status", "String", "STRING"), new DraftInput("limit", "int", "INTEGER"));
    }

    @Test
    void writeReasonsAreTheReadSafetyReasonsVerbatim() {
        DraftReport report = provider.draft(new ProviderInput(FIXTURE));

        assertThat(find(report, "OrderServiceImpl_create").reason()).contains("'save'").contains("R1)");
        assertThat(find(report, "OrderServiceImpl_flushAll").reason()).contains("'flush'").contains("R2)");
        assertThat(find(report, "OrderServiceImpl_sync").reason()).contains("'remove'").contains("R3)");
        assertThat(find(report, "OrderServiceImpl_archive").reason()).contains("setter 'setStatus'");
        assertThat(find(report, "OrderServiceImpl_closeAll").reason()).contains("@Modifying");
        assertThat(find(report, "OrderServiceImpl_purge").reason()).contains("'deleteById'");
        assertThat(find(report, "OrderServiceImpl_raw").reason()).contains("persistence-infrastructure field 'em'");
        assertThat(find(report, "OrderServiceImpl_notify").reason()).contains("returns void");
    }

    @Test
    void aConfirmedAttestationTargetLabelsAttestedAndReadWithoutTheRecord(@TempDir Path root) throws IOException {
        Files.copy(ATTEST_FIXTURE, root.resolve("AttestPredicate.java"));

        DraftReport before = provider.draft(new ProviderInput(root));
        DraftEntry unattested = byTarget(before, FILTER_TARGET);
        assertThat(unattested.label()).isEqualTo(DraftLabel.READ);
        assertThat(unattested.capability()).isNull();

        String fingerprint = provider.attestationFingerprint(new ProviderInput(root), FILTER_TARGET).orElseThrow();
        Files.createDirectories(root.resolve("tibyan"));
        Files.write(root.resolve("tibyan/attestations.json"), ("[{"
                + "\"target\":\"" + FILTER_TARGET + "\","
                + "\"category\":\"predicate\","
                + "\"name\":\"searchTenants\","
                + "\"resultSemantic\":\"Returns the tenants matching the filters.\","
                + "\"parameters\":{\"fullName\":\"Matches the full name.\",\"pageNum\":\"paging\"},"
                + "\"provenance\":{\"assertedBy\":\"a.person\",\"origin\":\"human-authored\","
                + "\"date\":\"2026-09-01\",\"fingerprint\":\"" + fingerprint + "\"}}]").getBytes(StandardCharsets.UTF_8));

        DraftReport after = provider.draft(new ProviderInput(root));
        DraftEntry attested = byTarget(after, FILTER_TARGET);
        assertThat(attested.label()).isEqualTo(DraftLabel.ATTESTED);
        assertThat(attested.capability()).isEqualTo("attested_searchTenants");
        assertThat(after.counts().get("ATTESTED")).isEqualTo(1);
    }

    @Test
    void theFileIsDeterministicAndUsesUnixLineSeparators() {
        DraftReport first = provider.draft(new ProviderInput(FIXTURE));
        DraftReport second = provider.draft(new ProviderInput(FIXTURE));

        String rendered = DraftsFile.render(first);
        assertThat(rendered).isEqualTo(DraftsFile.render(second));
        assertThat(rendered).doesNotContain("\r").startsWith("{\n  \"version\" : 1,").endsWith("}\n");
        assertThat(rendered).contains("\"counts\" : {").contains("\"drafts\" : [");
    }

    private static DraftEntry find(DraftReport report, String name) {
        Optional<DraftEntry> entry = report.drafts().stream().filter(d -> d.name().equals(name)).findFirst();
        assertThat(entry).as("draft named " + name).isPresent();
        return entry.get();
    }

    private static DraftEntry byTarget(DraftReport report, String target) {
        Optional<DraftEntry> entry = report.drafts().stream().filter(d -> d.target().equals(target)).findFirst();
        assertThat(entry).as("draft targeting " + target).isPresent();
        return entry.get();
    }
}
