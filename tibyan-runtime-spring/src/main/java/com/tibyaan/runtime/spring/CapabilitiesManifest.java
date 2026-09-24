package com.tibyaan.runtime.spring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The generated capabilities manifest as the runtime reads it (Volume 6 section 3): the module, the IR
 * version, the tools to register and, since phase DL-2b (Volume 6 section 3.11), the input shapes the tools
 * reference. The backend writes it from the IR; the runtime reads it and never sees the source. Unknown
 * fields are tolerated, the additive-field discipline of section 3.1.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CapabilitiesManifest(String module, String version, List<CapabilityDescriptor> tools,
                                   List<ShapeDescriptor> shapes) {

    public CapabilitiesManifest {
        tools = tools == null ? List.of() : List.copyOf(tools);
        shapes = shapes == null ? List.of() : List.copyOf(shapes);
    }

    public CapabilitiesManifest(String module, String version, List<CapabilityDescriptor> tools) {
        this(module, version, tools, List.of());
    }

    /** An empty manifest, used when no manifest is found so the app still starts cleanly. */
    public static CapabilitiesManifest empty() {
        return new CapabilitiesManifest(null, null, List.of(), List.of());
    }

    /** The shapes by display name, the key a {@code SHAPE_REF(name)} input type carries. */
    public Map<String, ShapeDescriptor> shapesByName() {
        Map<String, ShapeDescriptor> byName = new LinkedHashMap<>();
        for (ShapeDescriptor shape : shapes) {
            byName.put(shape.name(), shape);
        }
        return byName;
    }
}
