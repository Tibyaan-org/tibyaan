package com.tibyaan.runtime.spring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tibyaan.plugin.CapabilityRef;
import com.tibyaan.plugin.ExecutionAdapter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;

/**
 * Bridges one generated tool to the MCP server. It carries the tool's definition (name,
 * description, JSON Schema) and, on invocation, routes the call through the
 * {@link ExecutionAdapter}, never to a service directly. This is the in-context materialization
 * of the generated tool: identical contract, hosted dynamically rather than compiled.
 */
final class CapabilityToolCallback implements ToolCallback {

    private static final TypeReference<Map<String, Object>> ARGS_TYPE = new TypeReference<>() {
    };

    private final CapabilityDescriptor descriptor;
    private final ExecutionAdapter executionAdapter;
    private final ObjectMapper objectMapper;
    private final String inputSchema;

    CapabilityToolCallback(CapabilityDescriptor descriptor, ExecutionAdapter executionAdapter,
                           ObjectMapper objectMapper) {
        this.descriptor = descriptor;
        this.executionAdapter = executionAdapter;
        this.objectMapper = objectMapper;
        this.inputSchema = buildInputSchema(descriptor);
    }

    /**
     * The tool's definition: name, description and input schema, and NOTHING that says whether the tool
     * mutates. That is a recorded gap, not an omission (Volume 7 section 11; Volume 5 section 4.17.6).
     *
     * <p>THE HINTS FINDING, verified against the pinned jars on 2026-09-23. The MCP schema CAN carry the
     * hints: mcp-core 0.18.3 (resolved through spring-ai-mcp 1.0.9) has
     * {@code io.modelcontextprotocol.spec.McpSchema$ToolAnnotations} with {@code readOnlyHint},
     * {@code destructiveHint}, {@code idempotentHint} and {@code openWorldHint}, and
     * {@code McpSchema$Tool$Builder.annotations(ToolAnnotations)}. The Spring AI callback path this runtime
     * uses CANNOT: Spring AI 1.0.9's {@code ToolDefinition} has only {@code name()}, {@code description()}
     * and {@code inputSchema()}, its {@code ToolMetadata} only {@code returnDirect()}, and
     * {@code McpToolUtils.toSyncToolSpecification(ToolCallback, MimeType)} builds the MCP tool with
     * {@code name}, {@code description} and {@code inputSchema} and never calls {@code annotations}. So
     * whatever this callback said about read-only-ness, the bridge would drop it before the client saw it.
     *
     * <p>Until the Volume 16 stack bump brings a callback surface that carries annotations, the channel a
     * client sees is the one E6b already chose: the reserved name prefix ({@code attested_},
     * {@code reviewed_}) and the description's first sentence ({@code Reviewed by <name> (<origin>), not
     * tool-verified; the tool labeled it WRITE.}), both carried here verbatim and never rewritten. The
     * runtime does not route around the bridge by registering raw {@code SyncToolSpecification} beans:
     * that is a second registration path, and this project does not fork a path to smuggle a field.
     */
    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name(descriptor.name())
                .description(descriptor.description())
                .inputSchema(inputSchema)
                .build();
    }

    @Override
    public String call(String toolInput) {
        try {
            Map<String, Object> args = (toolInput == null || toolInput.isBlank())
                    ? Map.of()
                    : objectMapper.readValue(toolInput, ARGS_TYPE);
            Object result = executionAdapter.invoke(new CapabilityRef(descriptor.name()), args);
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to process tool '" + descriptor.name() + "'", e);
        }
    }

    // A minimal JSON Schema for the tool's inputs, derived from the IR types.
    private static String buildInputSchema(CapabilityDescriptor descriptor) {
        StringBuilder properties = new StringBuilder();
        StringBuilder required = new StringBuilder();
        for (int i = 0; i < descriptor.inputs().size(); i++) {
            InputDescriptor input = descriptor.inputs().get(i);
            if (i > 0) {
                properties.append(",");
                required.append(",");
            }
            properties.append("\"").append(input.name()).append("\":{\"type\":\"")
                    .append(IrTypes.jsonSchemaType(input.type())).append("\"}");
            required.append("\"").append(input.name()).append("\"");
        }
        return "{\"type\":\"object\",\"properties\":{" + properties + "},\"required\":[" + required + "]}";
    }
}
