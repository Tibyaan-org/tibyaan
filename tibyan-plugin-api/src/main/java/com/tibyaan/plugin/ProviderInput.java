package com.tibyaan.plugin;

import java.nio.file.Path;

/**
 * The input handed to a {@link CapabilityProvider}: where to read the source system from.
 *
 * <p>Kept deliberately small for the MVP: a single source root. Providers that need more
 * (multiple roots, options, credentials) will extend this through their own phase, not here.
 *
 * @param sourceRoot the root location the provider reads, for example a project directory
 */
public record ProviderInput(Path sourceRoot) {

    public ProviderInput {
        if (sourceRoot == null) {
            throw new IllegalArgumentException("sourceRoot must not be null");
        }
    }
}
