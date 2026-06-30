package com.tibyaan.plugin;

/**
 * Names the capability to invoke when calling an {@link ExecutionAdapter}.
 *
 * <p>It carries only the capability name from the IR. How that name resolves to real logic is
 * the runtime's concern, through the capability's binding, not the caller's.
 *
 * @param name the capability name, matching {@code Capability.name()} in the IR
 */
public record CapabilityRef(String name) {

    public CapabilityRef {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("capability name must not be null or blank");
        }
    }
}
