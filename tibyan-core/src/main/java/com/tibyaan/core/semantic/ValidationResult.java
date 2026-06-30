package com.tibyaan.core.semantic;

import java.util.List;

/**
 * The outcome of validating a {@code CapabilityModel}: either valid, or a list of located,
 * human readable error messages.
 *
 * <p>The validator collects every error in one pass rather than stopping at the first, so a
 * caller sees all problems at once. Use {@link #throwIfInvalid()} when fail fast behavior is
 * preferred (Volume 4 section 5: a validation failure stops the pipeline before generation).
 *
 * @param errors the validation errors, never null and empty when the model is valid
 */
public record ValidationResult(List<String> errors) {

    public ValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /** A successful result with no errors. */
    public static ValidationResult valid() {
        return new ValidationResult(List.of());
    }

    /** True when there are no errors. */
    public boolean isValid() {
        return errors.isEmpty();
    }

    /**
     * Throws {@link ValidationException} carrying all errors when the result is invalid,
     * otherwise does nothing.
     */
    public void throwIfInvalid() {
        if (!isValid()) {
            throw new ValidationException(errors);
        }
    }
}
