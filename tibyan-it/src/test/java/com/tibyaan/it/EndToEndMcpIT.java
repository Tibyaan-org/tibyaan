package com.tibyaan.it;

import com.tibyaan.cli.TibyanCli;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The MVP acceptance test (Volume 2 section 5), now also proving Direction B step two: a truly
 * untouched app needs only the one dependency. It exercises the whole path against the real
 * example app:
 *
 * <ol>
 *   <li>the tibyan CLI scans the example app source into a validated IR (tibyan scan),</li>
 *   <li>the tibyan CLI generates the getProduct tool and capabilities manifest (tibyan generate),</li>
 *   <li>the unmodified example app is started as a process,</li>
 *   <li>a genuine MCP client runs initialize, tools/list, and tools/call getProduct(5),</li>
 *   <li>the real ProductService.findById returns the real product from the real H2 store.</li>
 * </ol>
 *
 * <p>Two start modes are proven. The default mode passes NO {@code -Dtibyan.capabilities} property
 * and NO {@code spring.ai.mcp.server} config: the app ships the generated manifest on its classpath
 * at {@code tibyan/capabilities.json}, and the starter auto-configures the MCP server. The explicit
 * mode still supports pointing the runtime at a manifest with {@code -Dtibyan.capabilities}.
 */
class EndToEndMcpIT {

    private static Path generatedManifest;
    private static Path bootJar;
    private static String javaBin;
    private static Path workDir;

    @BeforeAll
    static void scanAndGenerateThroughTheCli() throws Exception {
        Path exampleApp = Path.of("..", "examples", "legacy-spring-app");
        workDir = Files.createTempDirectory("tibyan-it-generated");
        Path irFile = workDir.resolve("legacy.ir.json");

        // 1. scan the unmodified source into a validated IR, through the CLI
        int scanCode = new CommandLine(new TibyanCli())
                .execute("scan", exampleApp.toString(), "--out", irFile.toString());
        assertThat(scanCode).as("tibyan scan exit code").isEqualTo(0);

        // 2. generate the MCP tools and manifest from the IR, through the CLI
        int generateCode = new CommandLine(new TibyanCli())
                .execute("generate", "--backend", "mcp", "--ir", irFile.toString(), "--out", workDir.toString());
        assertThat(generateCode).as("tibyan generate exit code").isEqualTo(0);
        generatedManifest = workDir.resolve("tibyan/capabilities.json");
        assertThat(Files.exists(generatedManifest)).as("generate must write the capabilities manifest").isTrue();

        bootJar = locateBootJar(exampleApp.resolve("target"));
        javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    @Test
    void getProductOverGenuineMcpUsingOnlyTheDependencyAndClasspathManifest() throws Exception {
        // The defining test for "one dependency, nothing else": no -Dtibyan.capabilities, no MCP
        // config. The app finds tibyan/capabilities.json on its own classpath and the starter
        // auto-configures the MCP server.
        AppProcess app = startApp(null);
        try {
            assertGetProductReturnsTheRealProduct(app.baseUrl);
        } finally {
            stopApp(app);
        }
    }

    @Test
    void getProductStillWorksWhenTheCapabilitiesPropertyIsGivenExplicitly() throws Exception {
        // The explicit property is still honored when a host chooses to point the runtime at a
        // specific generated manifest. Here it is the one tibyan generate just produced.
        AppProcess app = startApp(generatedManifest.toUri().toString());
        try {
            assertGetProductReturnsTheRealProduct(app.baseUrl);
        } finally {
            stopApp(app);
        }
    }

    private void assertGetProductReturnsTheRealProduct(String baseUrl) {
        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(baseUrl).build();
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(30))
                .initializationTimeout(Duration.ofSeconds(30))
                .build();
        try {
            McpSchema.InitializeResult initialize = client.initialize();
            assertThat(initialize).as("initialize handshake").isNotNull();

            McpSchema.ListToolsResult tools = client.listTools();
            assertThat(tools.tools())
                    .as("tools/list must advertise the generated getProduct tool")
                    .extracting(McpSchema.Tool::name)
                    .contains("getProduct");

            McpSchema.CallToolResult result =
                    client.callTool(new McpSchema.CallToolRequest("getProduct", Map.of("id", 5)));

            assertThat(result.isError()).as("tools/call must not be an error").isNotEqualTo(Boolean.TRUE);
            String text = ((McpSchema.TextContent) result.content().get(0)).text();
            assertThat(text)
                    .as("the real product, read from the real H2 store via ProductService.findById")
                    .contains("Wireless Mouse")
                    .contains("29.99");
        } finally {
            client.closeGracefully();
        }
    }

    // Starts the packaged example app on a free port. When explicitManifestUri is null, no
    // -Dtibyan.capabilities is passed, so the default classpath discovery path is exercised.
    private static AppProcess startApp(String explicitManifestUri) throws Exception {
        int port = freePort();
        String baseUrl = "http://localhost:" + port;
        Path log = Files.createTempFile(workDir, "example-app-", ".log");

        List<String> command = new ArrayList<>();
        command.add(javaBin);
        if (explicitManifestUri != null) {
            command.add("-Dtibyan.capabilities=" + explicitManifestUri);
        }
        command.add("-jar");
        command.add(bootJar.toString());
        command.add("--server.port=" + port);

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();

        AppProcess app = new AppProcess(process, baseUrl, log);
        waitUntilReady(app, Duration.ofSeconds(90));
        return app;
    }

    private static void stopApp(AppProcess app) throws Exception {
        if (app.process.isAlive()) {
            app.process.destroy();
            if (!app.process.waitFor(15, TimeUnit.SECONDS)) {
                app.process.destroyForcibly();
            }
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static Path locateBootJar(Path targetDir) throws IOException {
        if (!Files.isDirectory(targetDir)) {
            throw new IllegalStateException("example app is not packaged; run with the verify phase. "
                    + "Missing: " + targetDir.toAbsolutePath());
        }
        try (Stream<Path> files = Files.list(targetDir)) {
            return files
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".jar") && !name.endsWith(".jar.original");
                    })
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "no runnable jar found in " + targetDir.toAbsolutePath()));
        }
    }

    private static void waitUntilReady(AppProcess app, Duration timeout) throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        HttpRequest probe = HttpRequest.newBuilder(URI.create(app.baseUrl + "/api/products/5"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (!app.process.isAlive()) {
                throw new IllegalStateException("example app exited early; see " + app.log.toAbsolutePath());
            }
            try {
                HttpResponse<String> response = http.send(probe, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (IOException notReadyYet) {
                // server not accepting connections yet
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("example app did not become ready within " + timeout
                + "; see " + app.log.toAbsolutePath());
    }

    private static final class AppProcess {
        final Process process;
        final String baseUrl;
        final Path log;

        AppProcess(Process process, String baseUrl, Path log) {
            this.process = process;
            this.baseUrl = baseUrl;
            this.log = log;
        }
    }
}
