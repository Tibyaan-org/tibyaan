package com.tibyaan.provider.spring.draft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One parameter of a drafted method as the reviewer sees it (Volume 5 section 4.17.2): the name, the type as
 * written in source, and the IR type the tool maps it to, or null when it does not map today.
 *
 * <p>Phase DL-2a (section 4.19.2): {@code multiple} is true when the parameter is a collection of the mapped
 * type, so the approval covers the array. Phase DL-2b (section 4.19.3): {@code shape} lists the fields of a
 * structured input and {@code unmapped} names why a class could not be one. Each is omitted from the file
 * when absent, which keeps every entry written before the phase byte-identical.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DraftInput(String name, String javaType, String type,
                         @JsonInclude(JsonInclude.Include.NON_NULL) Boolean multiple,
                         @JsonInclude(JsonInclude.Include.NON_NULL) DraftShape shape,
                         @JsonInclude(JsonInclude.Include.NON_NULL) String unmapped) {

    public DraftInput(String name, String javaType, String type) {
        this(name, javaType, type, null, null, null);
    }

    public DraftInput(String name, String javaType, String type, Boolean multiple) {
        this(name, javaType, type, multiple, null, null);
    }

    // Not is-prefixed: an is-prefixed accessor would be auto-detected as a second serialized property and
    // written as false on every entry, breaking the byte-identity of files written before the phase.
    public boolean carriedAsArray() {
        return Boolean.TRUE.equals(multiple);
    }
}
