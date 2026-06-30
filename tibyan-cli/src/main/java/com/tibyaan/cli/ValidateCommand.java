package com.tibyaan.cli;

import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.semantic.ValidationResult;
import com.tibyaan.pipeline.IrJson;
import com.tibyaan.pipeline.TibyanCompiler;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "validate", description = "Validate an IR without generating.")
final class ValidateCommand implements Callable<Integer> {

    @Option(names = "--ir", required = true, paramLabel = "<ir.json>", description = "IR JSON file to validate.")
    Path ir;

    private final TibyanCompiler compiler = new TibyanCompiler();

    @Override
    public Integer call() {
        try {
            CapabilityModel model = IrJson.read(ir);
            ValidationResult result = compiler.validate(model);
            if (result.isValid()) {
                System.out.println("Validation: ok (" + CliOutput.summary(model) + ")");
                return 0;
            }
            CliOutput.printValidationFailure(result.errors());
            return 2;
        } catch (RuntimeException e) {
            System.err.println("Error: " + e.getMessage());
            return 3;
        }
    }
}
