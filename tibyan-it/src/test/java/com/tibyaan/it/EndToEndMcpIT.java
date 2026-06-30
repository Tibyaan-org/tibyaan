package com.tibyaan.it;

import com.tibyaan.cli.TibyanCli;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterAll;
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
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The MVP acceptance test (Volume 2 section 5). It exercises the whole path against the real,
 * unmodified example app:
 *
 * <ol>
 *   <li>the tibyan CLI scans the example app source into a validated IR (tibyan scan),</li>
 *   <li>the tibyan CLI generates the getProduct tool and capabilities manifest (tibyan generate),</li>
 *   <li>the unmodified example app is started as a process, adding only the generated manifest
 *       through a launch property (no file of the app is edited),</li>
 *   <li>a genuine MCP client runs initialize, tools/list, and tools/call getProduct(5),</li>
 *   <li>the real ProductService.findById returns the real product from the real H2 store.</li>
 * </ol>
 */
class EndToEndMcpIT {

    private static int port;
    private static String baseUrl;
    private static Process appProcess;
    private static Path appLog;

    @BeforeAll
    static void scanGenerateAndStartTheExampleApp() throws Exception {
        Path exampleApp = Path.of("..", "examples", "legacy-spring-app");
        Path generated = Files.createTempDirectory("tibyan-it-generated");
        Path irFile = generated.resolve("legacy.ir.json");

        // 1. scan the unmodified source into a validated IR, through the CLI
        int scanCode = new CommandLine(new TibyanCli())
                .execute("scan", exampleApp.toString(), "--out", irFile.toString());
        assertThat(scanCode).as("tibyan scan exit code").isEqualTo(0);

        // 2. generate the MCP tools and manifest from the IR, through the CLI
        int generateCode = new CommandLine(new TibyanCli())
                .execute("generate", "--backend", "mcp", "--ir", irFile.toString(), "--out", generated.toString());
        assertThat(generateCode).as("tibyan generate exit code").isEqualTo(0);
        Path manifest = generated.resolve("tibyan/capabilities.json");
        assertThat(Files.exists(manifest)).as("generate must write the capabilities manifest").isTrue();

        // 3. start the unmodified example app, passing only the generated manifest at launch
        port = freePort();
        baseUrl = "http://localhost:" + port;
        appLog = generated.resolve("example-app.log");
        Path bootJar = locateBootJar(exampleApp.resolve("target"));
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();

        appProcess = new ProcessBuilder(
                javaBin,
                "-Dtibyan.capabilities=" + manifest.toUri(),
                "-jar", bootJar.toString(),
                "--server.port=" + port)
                .redirectErrorStream(true)
                .redirectOutput(appLog.toFile())
                .start();

        waitUntilReady(Duration.ofSeconds(90));
    }

    @AfterAll
    static void stopTheExampleApp() throws Exception {
        if (appProcess != null && appProcess.isAlive()) {
            appProcess.destroy();
            if (!appProcess.waitFor(15, TimeUnit.SECONDS)) {
                appProcess.destroyForcibly();
            }
        }
    }

    @Test
    void getProductOverGenuineMcpProtocolReturnsTheRealProduct() {
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

    private static void waitUntilReady(Duration timeout) throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        HttpRequest probe = HttpRequest.newBuilder(URI.create(baseUrl + "/api/products/5"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (!appProcess.isAlive()) {
                throw new IllegalStateException("example app exited early; see " + appLog.toAbsolutePath());
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
                + "; see " + appLog.toAbsolutePath());
    }
}
