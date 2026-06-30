package com.tibyaan.cli;

import com.tibyaan.backend.mcp.McpBackend;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.semantic.ValidationException;
import com.tibyaan.pipeline.IrJson;
import com.tibyaan.pipeline.TibyanCompiler;
import com.tibyaan.plugin.GenerationContext;
import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.provider.spring.ProviderException;
import com.tibyaan.provider.spring.SpringCapabilityProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(name = "generate", description = "Generate a backend from an IR file, or by scanning a path.")
final class GenerateCommand implements Callable<Integer> {

    @Option(names = "--backend", required = true, paramLabel = "<backend>", description = "Backend to generate (mcp).")
    String backend;

    @Option(names = "--ir", paramLabel = "<ir.json>", description = "IR JSON file to read.")
    Path ir;

    @Parameters(index = "0", arity = "0..1", paramLabel = "<path>", description = "Path to scan when --ir is not given.")
    Path path;

    @Option(names = "--out", required = true, paramLabel = "<dir>", description = "Output directory for the generated files.")
    Path out;

    private final TibyanCompiler compiler = new TibyanCompiler();

    @Override
    public Integer call() {
        if (!"mcp".equals(backend)) {
            System.err.println("Unknown backend: " + backend + " (supported: mcp)");
            return 1;
        }
        try {
            CapabilityModel model = loadIr();
            if (model == null) {
                System.err.println("Provide --ir <file> or a <path> to scan");
                return 1;
            }
            compiler.generate(new McpBackend(), model, new GenerationContext(out));
            System.out.println("Validation: ok");
            System.out.println("Backend: mcp");
            int tools = model.capabilities().size();
            System.out.println("Generated " + tools + " " + (tools == 1 ? "tool" : "tools")
                    + " (" + toolNames(model) + ")");
            System.out.println("Wrote files to " + out
                    + " (manifest: " + out.resolve("tibyan/capabilities.json") + ")");
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

    private CapabilityModel loadIr() {
        if (ir != null) {
            System.out.println("Loading IR " + ir);
            return IrJson.read(ir);
        }
        if (path != null) {
            System.out.println("Scanning " + path);
            return new SpringCapabilityProvider().provide(new ProviderInput(path));
        }
        return null;
    }

    private static String toolNames(CapabilityModel model) {
        return model.capabilities().isEmpty()
                ? "none"
                : model.capabilities().stream().map(Capability::name).collect(Collectors.joining(", "));
    }
}
