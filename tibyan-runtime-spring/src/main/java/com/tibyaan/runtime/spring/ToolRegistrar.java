package com.tibyaan.runtime.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tibyaan.plugin.ExecutionAdapter;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers the generated tools with the running MCP server (Volume 7, tool registration). It
 * turns each capability in the manifest into a {@link ToolCallback} that routes through the
 * {@link ExecutionAdapter}. This replaces the spike's single hard-coded tool with tools derived
 * from the IR.
 *
 * <p>Which tools register is the {@link ExposurePolicy}'s decision, applied here per tool with one reason
 * token per exclusion (Volume 7 sections 10.6 and 11), so the startup log can name every tool it did not
 * serve and say why.
 */
public final class ToolRegistrar {

    /** Attested tool, and {@code tibyan.attested.expose} is false. */
    public static final String ATTESTED_CLOSED = "attested-closed";
    /** Boundary tool, and {@code tibyan.attested.exposeBoundary} is false. */
    public static final String BOUNDARY_CLOSED = "boundary-closed";
    /**
     * Security-unverifiable tool, attested or reviewed, and neither the global
     * {@code tibyan.attested.exposeSecurityUnverifiable} is true nor its name is in
     * {@code tibyan.security.exposeUnverifiable}.
     */
    public static final String SECURITY_UNVERIFIABLE_NOT_REVIEWED = "security-unverifiable-not-reviewed";
    /** Reviewed tool, and {@code tibyan.reviewed.expose} is false. */
    public static final String REVIEWED_CLOSED = "reviewed-closed";
    /** WRITE or ACTION tool whose name is not in {@code tibyan.writes.allow}: the write gate. */
    public static final String WRITE_NOT_ALLOWED = "write-not-allowed";

    /**
     * What one registration decided: the callbacks to serve, every excluded tool with its reason token in
     * manifest order, and the security-unverifiable tools that registered ONLY because the global switch
     * is on, which the startup log warns about because nobody named them.
     */
    public record Registration(
            List<ToolCallback> callbacks,
            Map<String, String> excluded,
            List<String> openedByGlobalSwitchOnly,
            Map<String, String> unhosted,
            List<String> contractCarrying,
            List<String> unverifiableRegistered) {

        public Registration {
            callbacks = List.copyOf(callbacks);
            excluded = Collections.unmodifiableMap(new LinkedHashMap<>(excluded));
            openedByGlobalSwitchOnly = List.copyOf(openedByGlobalSwitchOnly);
            // F9 (Volume 7 section 14): the tools the policy would have registered but this application
            // cannot execute, each with the reason, in manifest order.
            unhosted = Collections.unmodifiableMap(new LinkedHashMap<>(unhosted == null ? Map.of() : unhosted));
            // SEC-1, S5 (Volume 7 section 15.4): the registered tools that carry a compiled authority contract, and
            // the registered security-unverifiable tools, in manifest order, for the startup line.
            contractCarrying = contractCarrying == null ? List.of() : List.copyOf(contractCarrying);
            unverifiableRegistered = unverifiableRegistered == null ? List.of() : List.copyOf(unverifiableRegistered);
        }

        public Registration(List<ToolCallback> callbacks, Map<String, String> excluded,
                            List<String> openedByGlobalSwitchOnly) {
            this(callbacks, excluded, openedByGlobalSwitchOnly, Map.of());
        }

        public Registration(List<ToolCallback> callbacks, Map<String, String> excluded,
                            List<String> openedByGlobalSwitchOnly, Map<String, String> unhosted) {
            this(callbacks, excluded, openedByGlobalSwitchOnly, unhosted, List.of(), List.of());
        }

        /** The names of the tools actually registered, in manifest order. */
        public List<String> registeredNames() {
            return callbacks.stream().map(callback -> callback.getToolDefinition().name()).toList();
        }
    }

    private final ObjectMapper objectMapper;
    private final ExposurePolicy policy;

    /** The default policy (Volume 5 section 4.15.7): attested tools expose, security-unverifiable do not. */
    public ToolRegistrar(ObjectMapper objectMapper) {
        this(objectMapper, ExposurePolicy.defaults());
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
        this(objectMapper, new ExposurePolicy(exposeAttested, exposeSecurityUnverifiable, exposeBoundary,
                true, List.of(), List.of()));
    }

    /** The full policy, the six controls of Volume 7 sections 10 and 11. */
    public ToolRegistrar(ObjectMapper objectMapper, ExposurePolicy policy) {
        this.objectMapper = objectMapper;
        this.policy = policy;
    }

    /** The callbacks to serve; see {@link #registration} for the exclusions and their reasons. */
    public List<ToolCallback> register(CapabilitiesManifest manifest, ExecutionAdapter executionAdapter) {
        return registration(manifest, executionAdapter).callbacks();
    }

    /**
     * Validates the manifest and both lists, then decides every tool. Validation runs first and FAILS
     * LOUDLY (Volume 7 section 10.9, D1): a list that names a stale, inert or unknown capability stops
     * the host rather than waiting silently to open whatever takes that name later.
     */
    public Registration registration(CapabilitiesManifest manifest, ExecutionAdapter executionAdapter) {
        checkManifest(manifest);
        policy.validate(manifest);

        List<ToolCallback> callbacks = new ArrayList<>();
        Map<String, String> excluded = new LinkedHashMap<>();
        List<String> openedByGlobalOnly = new ArrayList<>();
        Map<String, String> unhosted = new LinkedHashMap<>();
        List<String> contractCarrying = new ArrayList<>();
        List<String> unverifiableRegistered = new ArrayList<>();
        for (CapabilityDescriptor descriptor : manifest.tools()) {
            String reason = exclusionReason(descriptor);
            if (reason != null) {
                excluded.put(descriptor.name(), reason);
                continue;
            }
            // F9, rule H1 (Volume 7 section 14): after the policy has decided, only a binding this application
            // hosts registers; the rest are named on one startup line instead of failing at the first call.
            // H2: an exposure-list name the policy validated above stays valid whether or not it is hosted.
            if (executionAdapter instanceof BindingHost host) {
                java.util.Optional<String> problem = host.unhostedReason(descriptor.binding());
                if (problem.isPresent()) {
                    unhosted.put(descriptor.name(), problem.get());
                    continue;
                }
            }
            callbacks.add(new CapabilityToolCallback(descriptor, executionAdapter, objectMapper,
                    manifest.shapesByName(), policy.requireCaller()));
            if (descriptor.hasContract()) {
                contractCarrying.add(descriptor.name());
            }
            if (descriptor.securityUnverifiable() && !descriptor.verified()) {
                unverifiableRegistered.add(descriptor.name());
            }
            if (descriptor.securityUnverifiable() && !descriptor.verified()
                    && !policy.listsUnverifiable(descriptor.name())) {
                // It registered, it is unverifiable, and it is not on the list: the global switch alone opened it.
                openedByGlobalOnly.add(descriptor.name());
            }
        }
        return new Registration(callbacks, excluded, openedByGlobalOnly, unhosted, contractCarrying,
                unverifiableRegistered);
    }

    /**
     * The gate (Volume 7 section 11, composing section 10's rule and Volume 5 section 4.15.7). A verified
     * tool always registers. An attested tool registers unless the wholesale control is off, it is a
     * boundary tool without the boundary control, or it is security-unverifiable and neither the global
     * switch nor the review list opens it. A reviewed tool registers unless the reviewed control is off,
     * it is a WRITE or ACTION not named by the write gate, or it is security-unverifiable under the same
     * rule as attested.
     *
     * <p>X4 holds by construction: each tier's wholesale control is checked before either list, so a list
     * never widens it, and the boundary check precedes the review list, so a listed boundary tool still
     * needs {@code exposeBoundary}. The lists only ever narrow what a switch would open.
     *
     * @return the reason token, or null when the tool registers
     */
    String exclusionReason(CapabilityDescriptor descriptor) {
        if (descriptor.attested()) {
            if (!policy.exposeAttested()) {
                return ATTESTED_CLOSED;
            }
            if (descriptor.attestedBoundary() && !policy.exposeBoundary()) {
                return BOUNDARY_CLOSED;
            }
            if (descriptor.securityUnverifiable() && !policy.opensUnverifiable(descriptor.name())) {
                return SECURITY_UNVERIFIABLE_NOT_REVIEWED;
            }
            return null;
        }
        if (descriptor.reviewed()) {
            if (!policy.exposeReviewed()) {
                return REVIEWED_CLOSED;
            }
            if (descriptor.mutating() && !policy.allowsWrite(descriptor.name())) {
                return WRITE_NOT_ALLOWED;
            }
            // Build decision (Volume 5 section 4.17.13): the attested-named global switch never opens a reviewed
            // tool; a reviewed security-unverifiable capability opens by exact name in the list only.
            if (descriptor.securityUnverifiable() && !policy.listsUnverifiable(descriptor.name())) {
                return SECURITY_UNVERIFIABLE_NOT_REVIEWED;
            }
            return null;
        }
        return null;
    }

    // Fail loud, never guess: the runtime serves only what the manifest contract can mean. A WRITE or
    // ACTION without the reviewed tier is a write nobody signed for (core rule 12, Volume 5 section
    // 4.17.4), which no backend emits; an unknown kind is neither a read nor a write, and treating it as
    // either would be a guess.
    private static void checkManifest(CapabilitiesManifest manifest) {
        for (CapabilityDescriptor descriptor : manifest.tools()) {
            if (!descriptor.hasKnownKind()) {
                throw new IllegalStateException("manifest tool '" + descriptor.name() + "' has kind '"
                        + descriptor.kind() + "', which is not READ, WRITE or ACTION; the runtime never guesses "
                        + "whether a tool mutates");
            }
            if (descriptor.mutating() && !descriptor.reviewed()) {
                throw new IllegalStateException("manifest tool '" + descriptor.name() + "' is kind "
                        + descriptor.kind() + " but carries no reviewed tier; a write without a named reviewer "
                        + "is a write nobody signed for, and the runtime never serves one");
            }
        }
    }
}
