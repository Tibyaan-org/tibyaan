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
