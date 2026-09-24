package com.tibyaan.runtime.spring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One input shape as the runtime reads it from the manifest's {@code shapes} section (Volume 6 section 3.11;
 * Volume 7 section 13, phase DL-2b): the class, the display name the {@code SHAPE_REF(name)} token refers to,
 * the mapped fields, and the skipped fields the shape discloses. Unknown fields are tolerated.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShapeDescriptor(String key, String name, List<ShapeFieldDescriptor> fields, List<String> partial) {

    public ShapeDescriptor {
        fields = fields == null ? List.of() : List.copyOf(fields);
        partial = partial == null ? List.of() : List.copyOf(partial);
    }

    /** One mapped field: its element type, whether the wire carries an array, and its closed set if any. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShapeFieldDescriptor(String name, String type, boolean multiple,
                                       @JsonProperty("enum") List<String> values) {

        public ShapeFieldDescriptor {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }
}
