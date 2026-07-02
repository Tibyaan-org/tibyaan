package com.tibyaan.plugin;

import com.tibyaan.core.ir.CapabilityModel;

/**
 * The output abstraction (Volume 3, Volume 12): something that turns IR into generated output.
 *
 * <p>A backend reads only the IR and the {@link GenerationContext}. It never knows the source
 * the IR came from, never calls a framework directly, and never depends on a runtime. When
 * generated code needs to run real logic, it goes through an {@link ExecutionAdapter}, never a
 * service class directly.
 */
public interface Backend {

    /** Generates output for the given model into the given context. */
    void generate(CapabilityModel ir, GenerationContext ctx);
}
