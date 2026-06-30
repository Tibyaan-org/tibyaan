package com.tibyaan.plugin;

import com.tibyaan.core.ir.CapabilityModel;

/**
 * The input abstraction (Volume 3, Volume 12): something that produces a {@link CapabilityModel}.
 *
 * <p>A provider only produces IR. It never generates output and never depends on a backend or
 * a runtime. This is the broadest input contract; {@link SourceAnalyzer} narrows it to
 * providers that read code.
 */
public interface CapabilityProvider {

    /** Reads the given input and produces a neutral Capability IR. */
    CapabilityModel provide(ProviderInput input);
}
