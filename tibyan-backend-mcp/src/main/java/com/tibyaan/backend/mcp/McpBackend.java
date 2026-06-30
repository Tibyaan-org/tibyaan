package com.tibyaan.backend.mcp;

import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.plugin.Backend;
import com.tibyaan.plugin.GenerationContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The MCP backend (Volume 6 section 3). It reads only the {@link CapabilityModel} and emits one
 * MCP tool per capability, plus a capabilities manifest the runtime reads to host them.
 *
 * <p>The generated tool is framework-neutral: it imports only the {@code ExecutionAdapter} and
 * {@code CapabilityRef} contracts and, on invocation, calls
 * {@code executionAdapter.invoke(new CapabilityRef(name), args)}. It never imports or calls a
 * service class or any framework (ADR-0004). Generation is deterministic and golden-file tested.
 */
public final class McpBackend implements Backend {

    private static final String GENERATED_PACKAGE = "com.tibyaan.generated.mcp";
    private static final String TOOL_TEMPLATE = "/templates/mcp/tool.java.template";
    private static final String MANIFEST_PATH = "tibyan/capabilities.json";

    @Override
    public void generate(CapabilityModel ir, GenerationContext ctx) {
        Path outputRoot = ctx.outputRoot();
        for (Capability capability : ir.capabilities()) {
            writeToolClass(outputRoot, capability);
        }
        write(outputRoot.resolve(MANIFEST_PATH), ManifestWriter.toJson(ir));
    }

    private void writeToolClass(Path outputRoot, Capability capability) {
        String toolClass = toolClassName(capability.name());

        Map<String, String> tokens = new LinkedHashMap<>();
        tokens.put("PACKAGE", GENERATED_PACKAGE);
        tokens.put("TOOL_CLASS", toolClass);
        tokens.put("CAPABILITY_NAME", capability.name());

        String source = TemplateRenderer.render(TOOL_TEMPLATE, tokens);
        Path file = outputRoot
                .resolve(GENERATED_PACKAGE.replace('.', '/'))
                .resolve(toolClass + ".java");
        write(file, source);
    }

    /** The generated class name for a capability, for example getProduct yields GetProductTool. */
    static String toolClassName(String capabilityName) {
        return Character.toUpperCase(capabilityName.charAt(0))
                + capabilityName.substring(1)
                + "Tool";
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write " + file, e);
        }
    }
}
