package com.tibyaan.runtime.spring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One tool, as read from the generated capabilities manifest: its name, a description, the
 * opaque binding the runtime resolves, the output type, and the ordered inputs. This is the
 * runtime's view of a capability; it never sees the IR's sealed types, only their string forms.
 *
 * <p>Unknown fields are tolerated explicitly, so the backend can add fields the runtime does not
 * act on (for example {@code view}, the concrete return-shape name, Volume 6 section 3.1) without
 * a runtime change and regardless of the host's Jackson configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CapabilityDescriptor(
        String name,
        String description,
        String binding,
        String output,
        List<InputDescriptor> inputs,
        boolean attested,
        boolean securityUnverifiable,
        boolean attestedBoundary) {


    public CapabilityDescriptor {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
    }

    /**
     * The verified-tool arity (no attestation, no unverifiable marker), for the common case and for
     * existing callers. A verified manifest omits both flags, so they default false.
     */
    public CapabilityDescriptor(String name, String description, String binding, String output,
            List<InputDescriptor> inputs) {
        this(name, description, binding, output, inputs, false, false, false);
    }
}
