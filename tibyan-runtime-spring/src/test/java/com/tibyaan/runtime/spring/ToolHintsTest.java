package com.tibyaan.runtime.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tibyaan.plugin.ExecutionAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tool hints gap (Volume 7 section 11; Volume 5 section 4.17.6), recorded as a test so the day it closes
 * is visible.
 *
 * <p>FINDING (2026-09-23, the pinned jars): mcp-core 0.18.3 has {@code McpSchema$ToolAnnotations} with
 * {@code readOnlyHint} and {@code destructiveHint}, but Spring AI 1.0.9's {@code ToolDefinition} carries
 * only name, description and input schema, {@code ToolMetadata} only {@code returnDirect}, and
 * {@code McpToolUtils.toSyncToolSpecification} never sets annotations on the MCP tool it builds. The callback
 * path this runtime uses therefore cannot carry a hint, so the channel a client sees stays the E6b one: the
 * reserved name prefix and the description's first sentence, carried verbatim.
 */
class ToolHintsTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutionAdapter noop = (ref, arguments) -> null;

    private static final String WRITE_DESCRIPTION =
            "Reviewed by a.reviewer (human), not tool-verified; the tool labeled it WRITE. Records a payment.";
    private static final String READ_DESCRIPTION =
            "Reviewed by a.reviewer (ai-draft), not tool-verified; the tool labeled it READ. Lists overdue invoices.";

    @Test
    void aReviewedToolsDescriptionBeginsWithReviewedByAndIsCarriedVerbatim() {
        CapabilitiesManifest manifest = new CapabilitiesManifest("m", "0.1", List.of(
                CapabilityDescriptor.reviewed("reviewed_findOverdue", READ_DESCRIPTION, "com.example.InvoiceService#findOverdue",
                        "Invoice", List.of(), CapabilityDescriptor.KIND_READ, false,
                        new ReviewDescriptor("READ", "a.reviewer", "2026-09-23", "ai-draft")),
                CapabilityDescriptor.reviewed("reviewed_recordPayment", WRITE_DESCRIPTION, "com.example.LedgerService#record",
                        "Receipt", List.of(), CapabilityDescriptor.KIND_WRITE, false,
                        new ReviewDescriptor("WRITE", "a.reviewer", "2026-09-23", "human"))));
        ExposurePolicy policy = new ExposurePolicy(true, false, false, true, List.of("reviewed_recordPayment"), List.of());

        List<ToolCallback> callbacks = new ToolRegistrar(mapper, policy).register(manifest, noop);

        assertThat(callbacks).hasSize(2);
        for (ToolCallback callback : callbacks) {
            ToolDefinition definition = callback.getToolDefinition();
            assertThat(definition.name()).startsWith("reviewed_");
            assertThat(definition.description()).startsWith("Reviewed by ");
        }
        assertThat(callbacks.get(0).getToolDefinition().description()).isEqualTo(READ_DESCRIPTION);
        assertThat(callbacks.get(1).getToolDefinition().description()).isEqualTo(WRITE_DESCRIPTION);
    }

    @Test
    void theGapIsReal_thePinnedCallbackSurfaceHasNoAnnotationsToCarry() {
        // A tripwire, not a wish: when the Volume 16 stack bump gives ToolDefinition or ToolMetadata an
        // annotations surface, this fails, and that is the signal to carry readOnlyHint and destructiveHint
        // here instead of relying on the name prefix and the first sentence.
        assertThat(ToolDefinition.class.getMethods()).extracting(Method::getName)
                .containsExactlyInAnyOrder("name", "description", "inputSchema", "builder");
        assertThat(ToolMetadata.class.getMethods()).extracting(Method::getName)
                .containsExactlyInAnyOrder("returnDirect", "builder", "from");
    }
}
