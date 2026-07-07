package com.tibyaan.cli;

import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.provider.spring.EntityOutcome;
import com.tibyaan.provider.spring.EntityOutcome.Cause;
import com.tibyaan.provider.spring.EntityOutcome.Status;
import com.tibyaan.provider.spring.ParseSkip;
import com.tibyaan.provider.spring.ScanReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Markdown survey report renderer (Volume 11 section 3.4). The renderer reads a
 * {@link ScanReport}, so these build one directly and assert the rendered facts, independent of a
 * live scan.
 */
class MarkdownReportTest {

    private static final CapabilityModel EMPTY_MODEL =
            new CapabilityModel("demo", "0.1", List.of(), List.of(), List.of(), Metadata.empty());

    @Test
    void rendersOperableRefusedAndTheMostCommonBlocker() {
        EntityOutcome discovered = new EntityOutcome("Product", Status.DISCOVERED, Cause.NONE,
                "getProduct -> ProductService#findById", List.of());
        EntityOutcome adminFailed = new EntityOutcome("Admin", Status.FAILED, Cause.NO_BACKING_METHOD,
                "no service method backs read capability 'getAdmin' for entity 'Admin'. Looked at services.",
                List.of());
        EntityOutcome orderFailed = new EntityOutcome("Order", Status.FAILED, Cause.NO_BACKING_METHOD,
                "no service method backs read capability 'getOrder' for entity 'Order'. Looked at services.",
                List.of());
        ScanReport report = new ScanReport(EMPTY_MODEL,
                List.of(discovered, adminFailed, orderFailed),
                List.of(new ParseSkip("Foo.java", "(line 1,col 1) Parse error")),
                List.of(), List.of(), List.of());

        String md = MarkdownReport.render(report, "demo-app");

        assertThat(md).startsWith("# Tibyaan capability survey: demo-app");
        assertThat(md).contains("| Operable, read by id | 1 |");
        assertThat(md).contains("| Refused (by id) | 2 |");
        assertThat(md).contains("| Files skipped (unparseable) | 1 |");
        assertThat(md).contains("| Wrong bindings (mis-bound) | 0 |");
        assertThat(md).contains("getProduct -> ProductService#findById");
        assertThat(md).contains("**Admin** [NO_BACKING_METHOD]");
        assertThat(md).contains("### Files it could not read (1)");
        assertThat(md).contains("Foo.java: (line 1,col 1) Parse error");
        // Two entities share NO_BACKING_METHOD, more than the single parse skip, so it is the blocker.
        assertThat(md).contains("2 entities were blocked by NO_BACKING_METHOD");
        // The report must never imply the blocked reads are safe to expose.
        assertThat(md).contains("separate question");
    }

    @Test
    void rendersTheZeroOperableCaseHonestly() {
        EntityOutcome failed = new EntityOutcome("Admin", Status.FAILED, Cause.NO_BACKING_METHOD,
                "no service method backs read capability 'getAdmin'. Looked at services.", List.of());
        ScanReport report = new ScanReport(EMPTY_MODEL,
                List.of(failed), List.of(), List.of(), List.of(), List.of());

        String md = MarkdownReport.render(report, "empty-app");

        assertThat(md).contains("## What an agent can operate today (0)");
        assertThat(md).contains("Nothing was structurally provable");
        assertThat(md).doesNotContain("### Read by id");
    }
}
