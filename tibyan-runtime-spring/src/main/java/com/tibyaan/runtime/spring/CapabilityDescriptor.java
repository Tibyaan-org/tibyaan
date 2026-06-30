package com.tibyaan.runtime.spring;

import java.util.List;

/**
 * One tool, as read from the generated capabilities manifest: its name, a description, the
 * opaque binding the runtime resolves, the output type, and the ordered inputs. This is the
 * runtime's view of a capability; it never sees the IR's sealed types, only their string forms.
 */
public record CapabilityDescriptor(
        String name,
        String description,
        String binding,
        String output,
        List<InputDescriptor> inputs) {

    public CapabilityDescriptor {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
    }
}
