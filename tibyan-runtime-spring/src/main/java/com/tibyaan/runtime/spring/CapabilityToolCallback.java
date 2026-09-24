package com.tibyaan.runtime.spring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tibyaan.plugin.CapabilityRef;
import com.tibyaan.plugin.ExecutionAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

/**
 * Bridges one generated tool to the MCP server. It carries the tool's definition (name,
 * description, JSON Schema) and, on invocation, routes the call through the
 * {@link ExecutionAdapter}, never to a service directly. This is the in-context materialization
 * of the generated tool: identical contract, hosted dynamically rather than compiled.
 */
final class CapabilityToolCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(CapabilityToolCallback.class);

    private static final TypeReference<Map<String, Object>> ARGS_TYPE = new TypeReference<>() {
    };

    private final CapabilityDescriptor descriptor;
    private final ExecutionAdapter executionAdapter;
    private final ObjectMapper objectMapper;
    private final String inputSchema;

    CapabilityToolCallback(CapabilityDescriptor descriptor, ExecutionAdapter executionAdapter,
                           ObjectMapper objectMapper) {
        this(descriptor, executionAdapter, objectMapper, Map.of());
    }

    CapabilityToolCallback(CapabilityDescriptor descriptor, ExecutionAdapter executionAdapter,
                           ObjectMapper objectMapper, Map<String, ShapeDescriptor> shapesByName) {
        this.descriptor = descriptor;
        this.executionAdapter = executionAdapter;
        this.objectMapper = objectMapper;
        this.inputSchema = buildInputSchema(descriptor, shapesByName);
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
        return call(toolInput, null);
    }

    /**
     * Spike SEC-0 and phase SEC-1 (Volume 7 section 15): the call the MCP server actually makes, with the tool
     * context that carries the exchange. The caller the host authenticated is read from the exchange's transport
     * context (S1) and, when Spring Security is present, put in scope around the invocation and cleared after (S2).
     */
    @Override
    public String call(String toolInput, ToolContext toolContext) {
        try {
            Map<String, Object> args = (toolInput == null || toolInput.isBlank())
                    ? Map.of()
                    : objectMapper.readValue(toolInput, ARGS_TYPE);
            Optional<Principal> caller = CallerContext.callerOf(toolContext);
            log.debug("Tibyaan: tool {} called by {}", descriptor.name(),
                    caller.map(Principal::getName).orElse("(no authenticated caller)"));
            Object result = caller.isPresent() && SecuredInvocation.available()
                    ? SecuredInvocation.withCaller(caller.get(),
                            () -> executionAdapter.invoke(new CapabilityRef(descriptor.name()), args))
                    : executionAdapter.invoke(new CapabilityRef(descriptor.name()), args);
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to process tool '" + descriptor.name() + "'", e);
        }
    }

    // A minimal JSON Schema for the tool's inputs, derived from the IR types.
    private static String buildInputSchema(CapabilityDescriptor descriptor, Map<String, ShapeDescriptor> shapesByName) {
        StringBuilder properties = new StringBuilder();
        StringBuilder required = new StringBuilder();
        for (int i = 0; i < descriptor.inputs().size(); i++) {
            InputDescriptor input = descriptor.inputs().get(i);
            if (i > 0) {
                properties.append(",");
                required.append(",");
            }
            // DL-2a (Volume 7 section 13 item 3): a multiple input is an array of its element type. DL-2b: a
            // SHAPE_REF input is the nested object schema of its shape.
            String element = input.type() != null && input.type().startsWith("SHAPE_REF(")
                    ? shapeSchema(input.type().substring("SHAPE_REF(".length(), input.type().length() - 1),
                            shapesByName, 0)
                    : "{\"type\":\"" + IrTypes.jsonSchemaType(input.type()) + "\"}";
            properties.append("\"").append(input.name()).append("\":")
                    .append(input.multiple() ? "{\"type\":\"array\",\"items\":" + element + "}" : element);
            required.append("\"").append(input.name()).append("\"");
        }
        return "{\"type\":\"object\",\"properties\":{" + properties + "},\"required\":[" + required + "]}";
    }

    // DL-2b (Volume 7 section 13 item 3): a shape as a nested object schema with no required list (decision
    // D4), each closed set as enum, a multiple field as an array, a nested shape recursed, and the partial
    // disclosure in the description. A shape the manifest does not carry renders as an open object with a
    // description saying so; the adapter refuses the call at invocation.
    private static String shapeSchema(String name, Map<String, ShapeDescriptor> shapesByName, int depth) {
        ShapeDescriptor shape = shapesByName.get(name);
        if (shape == null || depth > 8) {
            return "{\"type\":\"object\",\"description\":\"" + escape("Shape " + name + " is not carried by the manifest.")
                    + "\"}";
        }
        StringBuilder properties = new StringBuilder();
        for (int i = 0; i < shape.fields().size(); i++) {
            ShapeDescriptor.ShapeFieldDescriptor field = shape.fields().get(i);
            if (i > 0) {
                properties.append(",");
            }
            String element;
            if (field.type() != null && field.type().startsWith("SHAPE_REF(")) {
                element = shapeSchema(field.type().substring("SHAPE_REF(".length(), field.type().length() - 1),
                        shapesByName, depth + 1);
            } else {
                StringBuilder scalar = new StringBuilder("{\"type\":\"")
                        .append(IrTypes.jsonSchemaType(field.type())).append("\"");
                if (!field.values().isEmpty()) {
                    scalar.append(",\"enum\":[");
                    for (int v = 0; v < field.values().size(); v++) {
                        scalar.append(v > 0 ? "," : "").append("\"").append(escape(field.values().get(v))).append("\"");
                    }
                    scalar.append("]");
                }
                element = scalar.append("}").toString();
            }
            properties.append("\"").append(escape(field.name())).append("\":")
                    .append(field.multiple() ? "{\"type\":\"array\",\"items\":" + element + "}" : element);
        }
        String description = "Input object " + shape.name() + "; every field optional."
                + (shape.partial().isEmpty() ? "" : " Not modeled: " + String.join(", ", shape.partial()) + ".");
        return "{\"type\":\"object\",\"description\":\"" + escape(description) + "\",\"properties\":{" + properties
                + "},\"additionalProperties\":false}";
    }

    private static String escape(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
