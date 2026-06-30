package com.tibyaan.plugin;

/**
 * A {@link CapabilityProvider} that produces its IR by reading source code (Volume 12).
 *
 * <p>This is a marker that documents intent: the first provider, the Spring source analyzer in
 * P1, is a {@code SourceAnalyzer}. Other providers (for example a database provider) may
 * implement {@link CapabilityProvider} without reading code.
 */
public interface SourceAnalyzer extends CapabilityProvider {
}
