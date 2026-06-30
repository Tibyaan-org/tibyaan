package com.tibyaan.pipeline;

import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.semantic.SemanticValidator;
import com.tibyaan.core.semantic.ValidationResult;
import com.tibyaan.plugin.Backend;
import com.tibyaan.plugin.CapabilityProvider;
import com.tibyaan.plugin.GenerationContext;
import com.tibyaan.plugin.ProviderInput;

/**
 * The pipeline orchestrator (Volume 9): it runs provide, validate, generate. It holds no provider,
 * backend, or runtime logic; it composes them through the plugin interfaces. The concrete provider
 * and backend are passed in by the caller, so the pipeline depends only on the plugin API and core.
 *
 * <p>Validation is the gate. {@link #scan} and {@link #generate} both validate and refuse to
 * continue on an invalid IR, throwing {@code ValidationException}, so no caller can generate from
 * an unvalidated model.
 */
public final class TibyanCompiler {

    private final SemanticValidator validator = new SemanticValidator();

    /** Provide then validate. Returns the validated IR, or throws on a validation failure. */
    public CapabilityModel scan(CapabilityProvider provider, ProviderInput input) {
        CapabilityModel ir = provider.provide(input);
        validator.validateOrThrow(ir);
        return ir;
    }

    /** Validate then generate. Refuses to generate from an invalid IR. */
    public void generate(Backend backend, CapabilityModel ir, GenerationContext context) {
        validator.validateOrThrow(ir);
        backend.generate(ir, context);
    }

    /** Validate without generating, returning all findings. */
    public ValidationResult validate(CapabilityModel ir) {
        return validator.validate(ir);
    }
}
