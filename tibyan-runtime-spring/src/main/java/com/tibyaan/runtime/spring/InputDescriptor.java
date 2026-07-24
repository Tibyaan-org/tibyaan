package com.tibyaan.runtime.spring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One named, typed input of a tool, as read from the generated capabilities manifest. The type
 * is the IR canonical string, for example {@code LONG}.
 *
 * <p>Unknown fields are tolerated explicitly (the additive-field discipline), so the backend can add
 * per-input fields the runtime does not act on, for example the attested {@code description} an attested
 * tool carries on each input (Volume 6 section 3.6), without a runtime change.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InputDescriptor(String name, String type) {
}
