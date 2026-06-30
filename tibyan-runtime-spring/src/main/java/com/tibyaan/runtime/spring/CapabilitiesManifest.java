package com.tibyaan.runtime.spring;

import java.util.List;

/**
 * The generated capabilities manifest the runtime hosts: the module, the IR version, and the
 * tools to register. The backend writes it from the IR; the runtime reads it and never sees the
 * source or the generator.
 */
public record CapabilitiesManifest(String module, String version, List<CapabilityDescriptor> tools) {

    public CapabilitiesManifest {
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    /** An empty manifest, used when no manifest is found so the app still starts cleanly. */
    public static CapabilitiesManifest empty() {
        return new CapabilitiesManifest(null, null, List.of());
    }
}
