package com.tibyaan.core.semantic;

import java.util.List;

/**
 * Thrown when an invalid {@code CapabilityModel} is validated with fail fast semantics.
 * Carries every collected error so the caller can report them all.
 */
public class ValidationException extends RuntimeException {

    private final List<String> errors;

    public ValidationException(List<String> errors) {
        super(buildMessage(errors));
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /** The located, human readable validation errors. */
    public List<String> errors() {
        return errors;
    }

    private static String buildMessage(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return "model is invalid";
        }
        return "model is invalid:" + System.lineSeparator()
                + "  - " + String.join(System.lineSeparator() + "  - ", errors);
    }
}
