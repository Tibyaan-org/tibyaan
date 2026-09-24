package com.tibyaan.runtime.spring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One input of a generated tool as the runtime reads it from the manifest: the name, the IR type, and
 * (phase DL-2a, Volume 7 section 13) whether the wire carries an array of that type. Unknown fields are
 * tolerated, the additive-field discipline of Volume 6 section 3.1.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InputDescriptor(String name, String type, boolean multiple) {

    public InputDescriptor(String name, String type) {
        this(name, type, false);
    }
}
