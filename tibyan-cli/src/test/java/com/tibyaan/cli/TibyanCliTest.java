package com.tibyaan.cli;

import com.tibyaan.backend.mcp.McpBackend;
import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Type;
import com.tibyaan.pipeline.IrJson;
import com.tibyaan.plugin.GenerationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the CLI in process against the unmodified example app and proves the two-command flow:
 * scan produces a valid IR, generate produces the manifest, and that manifest is byte-identical to
 * what the MCP backend produces directly (the CLI reuses the backend, it does not reimplement it).
 */
class TibyanCliTest {

    private static final Path EXAMPLE_APP = Path.of("..", "examples", "legacy-spring-app");

    private int run(String... args) {
        return new CommandLine(new TibyanCli()).execute(args);
    }

    @Test
    void scanWritesAValidIrWithProductAndGetProduct(@TempDir Path dir) {
        Path irFile = dir.resolve("legacy.ir.json");

        int code = run("scan", EXAMPLE_APP.toString(), "--out", irFile.toString());

        assertThat(code).isEqualTo(0);
        assertThat(Files.exists(irFile)).isTrue();
        CapabilityModel ir = IrJson.read(irFile);
        assertThat(ir.entities()).extracting(Entity::name).containsExactly("Product");
        assertThat(ir.capabilities()).extracting(Capability::name).containsExactly("getProduct");
    }

    @Test
    void scanWritesAMarkdownReport(@TempDir Path dir) throws IOException {
        Path reportFile = dir.resolve("survey.md");

        int code = run("scan", EXAMPLE_APP.toString(), "--report", reportFile.toString());

        assertThat(code).isEqualTo(0);
        assertThat(Files.exists(reportFile)).isTrue();
        String md = Files.readString(reportFile);
        assertThat(md).contains("# Tibyaan capability survey: legacy-spring-app");
        assertThat(md).contains("getProduct -> ProductService#findById");
        assertThat(md).contains("| Wrong bindings (mis-bound) | 0 |");
    }

    @Test
    void generateProducesAManifestByteIdenticalToTheBackend(@TempDir Path dir) throws IOException {
        Path irFile = dir.resolve("legacy.ir.json");
        Path cliOut = dir.resolve("cli-mcp");
        Path backendOut = dir.resolve("backend-mcp");

        assertThat(run("scan", EXAMPLE_APP.toString(), "--out", irFile.toString())).isEqualTo(0);
        assertThat(run("generate", "--backend", "mcp", "--ir", irFile.toString(), "--out", cliOut.toString()))
                .isEqualTo(0);

        // The reference: the backend run directly on the same IR the CLI loaded.
        new McpBackend().generate(IrJson.read(irFile), new GenerationContext(backendOut));

        Path cliManifest = cliOut.resolve("tibyan/capabilities.json");
        Path backendManifest = backendOut.resolve("tibyan/capabilities.json");
        assertThat(Files.exists(cliManifest)).isTrue();
        assertThat(normalize(Files.readString(cliManifest)))
                .isEqualTo(normalize(Files.readString(backendManifest)));
    }

    @Test
    void generateCanScanAPathDirectlyWithoutAnIrFile(@TempDir Path dir) {
        Path out = dir.resolve("mcp");

        int code = run("generate", "--backend", "mcp", EXAMPLE_APP.toString(), "--out", out.toString());

        assertThat(code).isEqualTo(0);
        assertThat(Files.exists(out.resolve("tibyan/capabilities.json"))).isTrue();
    }

    @Test
    void unknownBackendIsAUsageError(@TempDir Path dir) {
        int code = run("generate", "--backend", "graphql", EXAMPLE_APP.toString(), "--out", dir.resolve("x").toString());
        assertThat(code).isEqualTo(1);
    }

    @Test
    void validateFailsWithExitTwoOnInvalidIr(@TempDir Path dir) {
        Path irFile = dir.resolve("broken.ir.json");
        // A capability whose output references an entity that does not exist.
        CapabilityModel broken = new CapabilityModel("broken", "0.1",
                List.of(),
                List.of(Capability.read("getGhost", Map.<String, Type>of(), Type.entityRef("Ghost"),
                        new BindingRef("GhostService#findById"))),
                List.of(), Metadata.empty());
        IrJson.write(broken, irFile);

        int code = run("validate", "--ir", irFile.toString());

        assertThat(code).isEqualTo(2);
    }

    @Test
    void versionReturnsZero() {
        assertThat(run("version")).isEqualTo(0);
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }
}
