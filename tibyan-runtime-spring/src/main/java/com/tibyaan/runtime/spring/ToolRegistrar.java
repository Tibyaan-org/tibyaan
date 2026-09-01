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
    private final boolean exposeAttested;
    private final boolean exposeSecurityUnverifiable;
    private final boolean exposeBoundary;

    /** The default policy (Volume 5 section 4.15.7): attested tools expose, security-unverifiable do not. */
    public ToolRegistrar(ObjectMapper objectMapper) {
        this(objectMapper, true, false, false);
    }

    /**
     * @param exposeAttested             whether attested tools register at all ({@code tibyan.attested.expose},
     *                                   default true: committing the reviewed attestations file is the opt-in)
     * @param exposeSecurityUnverifiable whether an ATTESTED tool whose declared security is unverifiable
     *                                   registers ({@code tibyan.attested.exposeSecurityUnverifiable}, default
     *                                   FALSE: the security-boundary stake defaults closed, the G8 default)
     */
    public ToolRegistrar(ObjectMapper objectMapper, boolean exposeAttested,
            boolean exposeSecurityUnverifiable) {
        this(objectMapper, exposeAttested, exposeSecurityUnverifiable, false);
    }

    /**
     * AT-3 guard 5 (Volume 5 section 4.15.2.3): a BOUNDARY tool does not register unless
     * {@code tibyan.attested.exposeBoundary} is set deliberately. The two older controls do not cover it:
     * the wholesale one defaults TRUE and would ship a boundary tool by default, and the
     * security-unverifiable one governs a declared-contract marker, not a read-only affirmation. For every
     * other attested tool the tool proved no write is visible in what it read; for a delegation it proved
     * nothing of the kind, so exposing one is a deliberate choice.
     */
    public ToolRegistrar(ObjectMapper objectMapper, boolean exposeAttested,
            boolean exposeSecurityUnverifiable, boolean exposeBoundary) {
        this.objectMapper = objectMapper;
        this.exposeAttested = exposeAttested;
        this.exposeSecurityUnverifiable = exposeSecurityUnverifiable;
        this.exposeBoundary = exposeBoundary;
    }

    public List<ToolCallback> register(CapabilitiesManifest manifest, ExecutionAdapter executionAdapter) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (CapabilityDescriptor descriptor : manifest.tools()) {
            if (excluded(descriptor)) {
                continue;
            }
            callbacks.add(new CapabilityToolCallback(descriptor, executionAdapter, objectMapper));
        }
        return callbacks;
    }

    // The two attested-exposure controls (Volume 5 section 4.15.7); a verified tool always registers, its
    // invocation and registration unchanged. An attested tool registers unless the wholesale control is
    // off, and an attested-and-security-unverifiable tool defaults CLOSED: its only declared gate is both
    // unverifiable and bypassed by the direct bean invocation (Volume 7 gap 9), so it does not register
    // without an explicit opt-in.
    private boolean excluded(CapabilityDescriptor descriptor) {
        if (!descriptor.attested()) {
            return false;
        }
        if (!exposeAttested) {
            return true;
        }
        if (descriptor.attestedBoundary() && !exposeBoundary) {
            return true;
        }
        return descriptor.securityUnverifiable() && !exposeSecurityUnverifiable;
    }
}
