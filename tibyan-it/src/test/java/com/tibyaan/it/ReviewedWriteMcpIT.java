package com.tibyaan.it;

import com.tibyaan.cli.TibyanCli;
import com.tibyaan.provider.spring.draft.DraftEntry;
import com.tibyaan.provider.spring.draft.DraftLabel;
import com.tibyaan.provider.spring.draft.DraftsFile;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase DL-1, fixture RV-2 end to end (Volume 5 section 4.17.10): a WRITE the discovery rules never emit is
 * drafted by {@code tibyan draft}, approved by a named human in {@code tibyan/drafts.json}, admitted by
 * {@code tibyan scan} as {@code reviewed_rename}, generated into the manifest, and served by the runtime ONLY
 * when its exact name is in {@code tibyan.writes.allow}. Without the list the tool is not advertised; with it
 * the write runs against the real H2 store and the verified read observes the change.
 *
 * <p>The example app's own classpath manifest stays verified-only: the approved drafts file lives in a scratch
 * copy of the source, and the app is started with {@code -Dtibyan.capabilities} pointing at the manifest that
 * copy produced.
 */
class ReviewedWriteMcpIT {

    private static final String RENAME_TARGET = "com.tibyaan.examples.legacy.ProductService#rename(Long, String)";

    private static Path manifest;
    private static Path bootJar;
    private static String javaBin;
    private static Path workDir;

    @BeforeAll
    static void draftApproveScanAndGenerate() throws Exception {
        Path exampleApp = Path.of("..", "examples", "legacy-spring-app");
        workDir = Files.createTempDirectory("tibyan-it-reviewed");
        Path sourceCopy = workDir.resolve("app");
        copyTree(exampleApp.resolve("src").resolve("main").resolve("java"), sourceCopy.resolve("src"));

        // 1. draft: every public service method becomes a labeled candidate, the write among them
        int draftCode = new CommandLine(new TibyanCli()).execute("draft", sourceCopy.toString());
        assertThat(draftCode).as("tibyan draft exit code").isEqualTo(0);
        Path draftsFile = DraftsFile.defaultLocation(sourceCopy);
        List<DraftEntry> drafts = DraftsFile.read(draftsFile);
        DraftEntry rename = drafts.stream().filter(d -> d.target().equals(RENAME_TARGET)).findFirst()
                .orElseThrow(() -> new AssertionError("rename was not drafted; targets: "
                        + drafts.stream().map(DraftEntry::target).toList()));
        assertThat(rename.label()).as("the write is labeled by read-safety").isEqualTo(DraftLabel.WRITE);
        assertThat(rename.fingerprint()).as("the tool stamps the fingerprint").isNotBlank();

        // 2. approve: the named human's act, an edit of the file, nothing else
        List<DraftEntry> approved = new ArrayList<>();
        for (DraftEntry d : drafts) {
            approved.add(d == rename
                    ? new DraftEntry(d.name(), d.target(), d.binding(), d.label(), d.reason(), d.capability(),
                            d.returnType(), d.inputs(), "Renames the product with the given id and returns it.",
                            DraftEntry.APPROVED, "it.reviewer", "2026-09-23", DraftEntry.ORIGIN_HUMAN,
                            d.fingerprint(), false, null)
                    : d);
        }
        DraftsFile.write(draftsFile, approved);

        // 3. scan admits the approved write as reviewed_<name>; 4. generate the manifest from that IR
        Path irFile = workDir.resolve("reviewed.ir.json");
        int scanCode = new CommandLine(new TibyanCli())
                .execute("scan", sourceCopy.toString(), "--out", irFile.toString());
        assertThat(scanCode).as("tibyan scan exit code").isEqualTo(0);
        Path out = workDir.resolve("generated");
        int generateCode = new CommandLine(new TibyanCli())
                .execute("generate", "--backend", "mcp", "--ir", irFile.toString(), "--out", out.toString());
        assertThat(generateCode).as("tibyan generate exit code").isEqualTo(0);
        manifest = out.resolve("tibyan/capabilities.json");
        String manifestText = Files.readString(manifest);
        assertThat(manifestText).contains("\"name\": \"reviewed_" + rename.name() + "\"")
                .contains("\"kind\": \"WRITE\"").contains("\"tier\": \"reviewed\"");

        bootJar = locateBootJar(exampleApp.resolve("target"));
        javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    @Test
    void theWriteIsNotAdvertisedWithoutTheAllowList() throws Exception {
        AppProcess app = startApp(manifest.toUri().toString(), null);
        try (McpSyncClient client = client(app.baseUrl)) {
            client.initialize();
            List<String> names = client.listTools().tools().stream().map(McpSchema.Tool::name).toList();
            assertThat(names).contains("getProduct");
            assertThat(names).as("a WRITE never registers unless its exact name is allowed")
                    .noneMatch(n -> n.startsWith("reviewed_"));
        } finally {
            stopApp(app);
        }
    }

    /**
     * SEC-1, rule S4 (Volume 7 section 15.4) on the example host, which has no Spring Security: under the default
     * the allowed write refuses the anonymous caller before anything runs, and the startup log warns that no
     * caller can ever be captured here. The test below runs the write as the host the design names for that
     * case, one that sets {@code tibyan.security.requireCaller=false} knowingly.
     */
    @Test
    void theAllowedWriteRefusesTheAnonymousCallerByDefaultOnAHostWithNoSecurity() throws Exception {
        String toolName = "reviewed_ProductService_rename";
        AppProcess app = startApp(manifest.toUri().toString(), toolName);
        try (McpSyncClient client = client(app.baseUrl)) {
            client.initialize();
            McpSchema.CallToolResult refused = client.callTool(new McpSchema.CallToolRequest(toolName,
                    Map.of("id", 5, "name", "Renamed Mouse")));
            assertThat(refused.isError()).as("an anonymous reviewed write is refused by default").isTrue();
            String text = ((McpSchema.TextContent) refused.content().get(0)).text();
            assertThat(text).contains("'" + toolName + "' refused").contains("no authenticated caller")
                    .contains("reviewed WRITE").contains("tibyan.security.requireCaller=true");

            McpSchema.CallToolResult read = client.callTool(new McpSchema.CallToolRequest("getProduct", Map.of("id", 5)));
            String product = ((McpSchema.TextContent) read.content().get(0)).text();
            assertThat(product).as("the refused write changed nothing").doesNotContain("Renamed Mouse");
            assertThat(Files.readString(app.log))
                    .contains("Tibyaan: caller gate: tibyan.security.requireCaller=true, Spring Security absent;")
                    .contains("Spring Security is not on the class path");
        } finally {
            stopApp(app);
        }
    }

    @Test
    void theAllowedWriteRunsAndTheVerifiedReadObservesIt() throws Exception {
        String toolName = "reviewed_ProductService_rename";
        // The example host has no Spring Security and so no caller to capture: it sets requireCaller=false
        // knowingly (SEC-1, S4), which the test above shows is the only way the anonymous write runs.
        AppProcess app = startApp(manifest.toUri().toString(), toolName, false);
        try (McpSyncClient client = client(app.baseUrl)) {
            client.initialize();
            List<McpSchema.Tool> tools = client.listTools().tools();
            McpSchema.Tool write = tools.stream().filter(t -> t.name().equals(toolName)).findFirst()
                    .orElseThrow(() -> new AssertionError("allowed write not advertised; tools: "
                            + tools.stream().map(McpSchema.Tool::name).toList()));
            assertThat(write.description()).startsWith("Reviewed by it.reviewer (human), not tool-verified;"
                    + " the tool labeled it WRITE.");

            McpSchema.CallToolResult renamed = client.callTool(new McpSchema.CallToolRequest(toolName,
                    Map.of("id", 5, "name", "Renamed Mouse")));
            assertThat(renamed.isError()).as("the write must invoke").isNotEqualTo(Boolean.TRUE);

            McpSchema.CallToolResult read = client.callTool(new McpSchema.CallToolRequest("getProduct", Map.of("id", 5)));
            String text = ((McpSchema.TextContent) read.content().get(0)).text();
            assertThat(text).as("the verified read sees the reviewed write's effect").contains("Renamed Mouse");
        } finally {
            stopApp(app);
        }
    }

    // ---------- harness ----------

    private static McpSyncClient client(String baseUrl) {
        return McpClient.sync(HttpClientSseClientTransport.builder(baseUrl).build())
                .requestTimeout(Duration.ofSeconds(30))
                .initializationTimeout(Duration.ofSeconds(30))
                .build();
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> paths = Files.walk(from)) {
            for (Path p : paths.toList()) {
                Path target = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(p, target);
                }
            }
        }
    }

    private static AppProcess startApp(String manifestUri, String writesAllow) throws Exception {
        return startApp(manifestUri, writesAllow, null);
    }

    /** @param requireCaller SEC-1 rule S4 (Volume 7 section 15.4); null keeps the runtime's default, true */
    private static AppProcess startApp(String manifestUri, String writesAllow, Boolean requireCaller) throws Exception {
        int port = freePort();
        String baseUrl = "http://localhost:" + port;
        Path log = Files.createTempFile(workDir, "example-app-", ".log");
        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-Dtibyan.capabilities=" + manifestUri);
        if (writesAllow != null) {
            command.add("-Dtibyan.writes.allow=" + writesAllow);
        }
        if (requireCaller != null) {
            command.add("-Dtibyan.security.requireCaller=" + requireCaller);
        }
        command.add("-jar");
        command.add(bootJar.toString());
        command.add("--server.port=" + port);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
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
        try (Stream<Path> files = Files.list(targetDir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".jar")
                            && !p.getFileName().toString().endsWith(".jar.original"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("no runnable jar in " + targetDir.toAbsolutePath()));
        }
    }

    private static void waitUntilReady(AppProcess app, Duration timeout) throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        HttpRequest probe = HttpRequest.newBuilder(URI.create(app.baseUrl + "/api/products/5"))
                .timeout(Duration.ofSeconds(2)).GET().build();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (!app.process.isAlive()) {
                throw new IllegalStateException("example app exited early; see " + app.log.toAbsolutePath()
                        + "\n" + Files.readString(app.log));
            }
            try {
                if (http.send(probe, HttpResponse.BodyHandlers.ofString()).statusCode() == 200) {
                    return;
                }
            } catch (IOException notReadyYet) {
                // not accepting connections yet
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("example app did not become ready within " + timeout);
    }

    private record AppProcess(Process process, String baseUrl, Path log) {
    }
}
