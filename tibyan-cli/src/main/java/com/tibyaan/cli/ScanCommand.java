package com.tibyaan.cli;

import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.semantic.ValidationException;
import com.tibyaan.pipeline.IrJson;
import com.tibyaan.pipeline.TibyanCompiler;
import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.provider.spring.ProviderException;
import com.tibyaan.provider.spring.SpringCapabilityProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "scan", description = "Read a system from source and produce a validated Capability IR.")
final class ScanCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "<path>", description = "Path to the system to scan.")
    Path path;

    @Option(names = "--out", paramLabel = "<ir.json>", description = "Write the IR JSON to this file (otherwise to stdout).")
    Path out;

    private final TibyanCompiler compiler = new TibyanCompiler();

    @Override
    public Integer call() {
        System.out.println("Scanning " + path);
        try {
            CapabilityModel ir = compiler.scan(new SpringCapabilityProvider(), new ProviderInput(path));
            System.out.println("Provider: spring (entity and service extractors)");
            System.out.println("Found " + CliOutput.summary(ir));
            System.out.println("Validation: ok");
            if (out != null) {
                IrJson.write(ir, out);
                System.out.println("Wrote IR to " + out);
            } else {
                System.out.println(IrJson.writeString(ir));
            }
            return 0;
        } catch (ValidationException e) {
            CliOutput.printValidationFailure(e.errors());
            return 2;
        } catch (ProviderException e) {
            System.err.println("Provider error: " + e.getMessage());
            return 3;
        } catch (RuntimeException e) {
            System.err.println("Error: " + e.getMessage());
            return 3;
        }
    }
}
