package com.tibyaan.provider.spring.draft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One parameter of a drafted method as the reviewer sees it (Volume 5 section 4.17.2): the name, the type as
 * written in source, and the IR type the tool maps it to, or null when it does not map today.
 *
 * <p>Phase DL-2a (section 4.19.2): {@code multiple} is true when the parameter is a collection of the mapped
 * type, so the approval covers the array. It is omitted from the file when absent, which keeps every entry
 * written before the phase byte-identical.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DraftInput(String name, String javaType, String type,
                         @JsonInclude(JsonInclude.Include.NON_NULL) Boolean multiple) {

    public DraftInput(String name, String javaType, String type) {
        this(name, javaType, type, null);
    }

    // Not is-prefixed: an is-prefixed accessor would be auto-detected as a second serialized property and
    // written as false on every entry, breaking the byte-identity of files written before the phase.
    public boolean carriedAsArray() {
        return Boolean.TRUE.equals(multiple);
    }
}
