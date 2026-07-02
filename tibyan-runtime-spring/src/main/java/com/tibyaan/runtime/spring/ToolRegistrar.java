package com.tibyaan.runtime.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tibyaan.plugin.ExecutionAdapter;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers the generated tools with the running MCP server (Volume 7, tool registration). It
 * turns each capability in the manifest into a {@link ToolCallback} that routes through the
 * {@link ExecutionAdapter}. This replaces the spike's single hard-coded tool with tools derived
 * from the IR.
 */
public final class ToolRegistrar {

    private final ObjectMapper objectMapper;

    public ToolRegistrar(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ToolCallback> register(CapabilitiesManifest manifest, ExecutionAdapter executionAdapter) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (CapabilityDescriptor descriptor : manifest.tools()) {
            callbacks.add(new CapabilityToolCallback(descriptor, executionAdapter, objectMapper));
        }
        return callbacks;
    }
}
