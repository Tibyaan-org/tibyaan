package com.tibyaan.cli;

import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.semantic.ValidationResult;
import com.tibyaan.pipeline.IrJson;
import com.tibyaan.pipeline.TibyanCompiler;
import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.provider.spring.ProviderException;
import com.tibyaan.provider.spring.ScanReport;
import com.tibyaan.provider.spring.SpringCapabilityProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * {@code tibyan scan} (Volume 11): a resilient survey of a system's source. It never aborts on a
 * single unparseable file; it walks the whole source, classifies every entity it can parse, and
 * prints a per-entity outcome report plus every file it had to skip (Volume 5 section 4, phase E1).
 *
 * <p>Exit codes (Volume 11 section 4): 0 when the survey completed, whatever it discovered; 2 when
 * {@code --out} was given but the discovered IR did not validate, so no artifact was written; 3 when
 * the scan could not run at all (the source root is missing, is not a directory, or cannot be
 * walked). Producing an artifact stays strict; {@code tibyan generate} still refuses a messy scan.
 */
@Command(name = "scan", description = "Read a system from source and survey it into a Capability IR.")
final class ScanCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "<path>", description = "Path to the system to scan.")
    Path path;

    @Option(names = "--out", paramLabel = "<ir.json>", description = "Write the discovered IR JSON to this file.")
    Path out;

    private final TibyanCompiler compiler = new TibyanCompiler();

    @Override
    public Integer call() {
        System.out.println("Scanning " + path);
        ScanReport report;
        try {
            report = new SpringCapabilityProvider().scan(new ProviderInput(path));
        } catch (ProviderException e) {
            System.err.println("Provider error: " + e.getMessage());
            return 3;
        }

        CliOutput.printScanReport(report);

        if (out == null) {
            return 0;
        }

        // Writing an artifact is where strictness returns: only a valid IR is written. The survey
        // above already printed, so an invalid discovered model still leaves a full, honest report.
        CapabilityModel model = report.model();
        ValidationResult validation = compiler.validate(model);
        if (!validation.isValid()) {
            CliOutput.printValidationFailure(validation.errors());
            return 2;
        }
        IrJson.write(model, out);
        System.out.println("Wrote IR to " + out);
        return 0;
    }
}
