package com.tibyaan.runtime.spring;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spike SEC-0 (Volume 7 section 15.3), run as a test against a real servlet host with Spring Security, HTTP
 * Basic, a context path and the MCP Java SDK client: Q1 the runtime's transport provider replaces Spring AI's
 * and the base URL still works; Q2 the caller the host authenticated reaches exactly its own tool call; Q3 a
 * SecurityContext around the invocation makes the host's own method security fire.
 */
@SpringBootTest(classes = CallerBoundarySpikeTest.Host.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "server.servlet.context-path=/ctx",
                "spring.ai.mcp.server.base-url=/ctx",
                "tibyan.capabilities=classpath:no-such-manifest.json"
        })
class CallerBoundarySpikeTest {

    /** The host: Spring Security with two users, method security on one service method, no permitted path. */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableMethodSecurity
    static class Host {

        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults())
                    .build();
        }

        @Bean
        InMemoryUserDetailsManager users() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("alice").password("{noop}pw").authorities("480012").build(),
                    User.withUsername("bob").password("{noop}pw").authorities("OTHER").build());
        }

        @Bean
        WhoService whoService() {
            return new WhoService();
        }

        /** The manifest, supplied directly so the test needs no resource file. */
        @Bean
        CapabilitiesManifest tibyanCapabilitiesManifest() {
            String service = WhoService.class.getCanonicalName();
            return new CapabilitiesManifest("spike", "0.1", List.of(
                    new CapabilityDescriptor("whoAmI", "Who is calling.", service + "#whoAmI", "STRING", List.of()),
                    new CapabilityDescriptor("secured", "Needs 480012.", service + "#secured", "STRING", List.of()),
                    new CapabilityDescriptor("contracted", "Compiled contract.", service + "#contracted", "STRING", List.of())
                            .withRequiredAuthorities(List.of("480012", "480099"))));
        }
    }

    public static class WhoService {
        static final List<String> seen = new CopyOnWriteArrayList<>();

        public String whoAmI() {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            String name = authentication == null ? "(none)" : authentication.getName();
            seen.add(name);
            return name;
        }

        @PreAuthorize("hasAuthority('480012')")
        public String secured() {
            return "ok for " + SecurityContextHolder.getContext().getAuthentication().getName();
        }

        /** No method security of its own: the manifest carries the compiled contract the runtime enforces (S3). */
        public String contracted() {
            return "contracted for " + SecurityContextHolder.getContext().getAuthentication().getName();
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    WebMvcSseServerTransportProvider transportProvider;

    @Autowired
    org.springframework.context.ApplicationContext context;

    private McpSyncClient client(String user) {
        String basic = "Basic " + Base64.getEncoder().encodeToString((user + ":pw").getBytes(StandardCharsets.UTF_8));
        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder("http://localhost:" + port)
                .sseEndpoint("/ctx/sse")
                .customizeRequest(builder -> builder.header("Authorization", basic))
                .build();
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(30))
                .initializationTimeout(Duration.ofSeconds(30))
                .build();
        client.initialize();
        return client;
    }

    private static String text(McpSchema.CallToolResult result) {
        return result.content().isEmpty() ? "" : ((McpSchema.TextContent) result.content().get(0)).text();
    }

    @Test
    void q1_theRuntimesTransportProviderIsTheOneRegisteredAndTheBaseUrlWorksUnderAContextPath() {
        // The bean exists and is the one Tibyaan built: the only way a caller can be captured is our extractor.
        assertThat(transportProvider).isNotNull();
        assertThat(context.containsBean("tibyanTransportProvider")).isTrue();
        assertThat(context.containsBean("webMvcSseServerTransportProvider")).isFalse();
        assertThat(context.containsBean("mvcMcpRouterFunction")).isFalse();
        assertThat(context.containsBean("tibyanMcpRouterFunction")).isTrue();
        McpSyncClient alice = client("alice");
        try {
            assertThat(alice.listTools().tools()).extracting(McpSchema.Tool::name).contains("whoAmI", "secured");
        } finally {
            alice.closeGracefully();
        }
    }

    @Test
    void q2_theCallerTheHostAuthenticatedReachesExactlyItsOwnToolCall() throws Exception {
        McpSyncClient alice = client("alice");
        McpSyncClient bob = client("bob");
        try {
            assertThat(text(alice.callTool(new McpSchema.CallToolRequest("whoAmI", Map.of())))).isEqualTo("\"alice\"");
            assertThat(text(bob.callTool(new McpSchema.CallToolRequest("whoAmI", Map.of())))).isEqualTo("\"bob\"");

            // Interleaved from two threads: no call ever sees the other session's caller.
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                futures.add(pool.submit(() -> text(alice.callTool(new McpSchema.CallToolRequest("whoAmI", Map.of())))));
                futures.add(pool.submit(() -> text(bob.callTool(new McpSchema.CallToolRequest("whoAmI", Map.of())))));
            }
            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get());
            }
            pool.shutdown();
            assertThat(results).hasSize(20).containsOnly("\"alice\"", "\"bob\"");
            assertThat(results.stream().filter("\"alice\""::equals).count()).isEqualTo(10);
        } finally {
            alice.closeGracefully();
            bob.closeGracefully();
        }
    }

    @Test
    void q3_theHostsOwnMethodSecurityFiresOnTheInvocationWithTheCapturedCaller() {
        McpSyncClient alice = client("alice");
        McpSyncClient bob = client("bob");
        try {
            McpSchema.CallToolResult ok = alice.callTool(new McpSchema.CallToolRequest("secured", Map.of()));
            assertThat(ok.isError()).isFalse();
            assertThat(text(ok)).isEqualTo("\"ok for alice\"");

            McpSchema.CallToolResult denied = bob.callTool(new McpSchema.CallToolRequest("secured", Map.of()));
            assertThat(denied.isError()).isTrue();
            assertThat(text(denied)).contains("AuthorizationDeniedException: Access Denied");
        } finally {
            alice.closeGracefully();
            bob.closeGracefully();
        }
    }

    @Test
    void sc1_sc2_theCompiledContractIsEnforcedAtTheBoundaryNamingTheContractNeverTheCallersAuthorities() {
        McpSyncClient alice = client("alice");
        McpSyncClient bob = client("bob");
        try {
            McpSchema.CallToolResult ok = alice.callTool(new McpSchema.CallToolRequest("contracted", Map.of()));
            assertThat(ok.isError()).isFalse();
            assertThat(text(ok)).isEqualTo("\"contracted for alice\"");

            McpSchema.CallToolResult denied = bob.callTool(new McpSchema.CallToolRequest("contracted", Map.of()));
            assertThat(denied.isError()).isTrue();
            assertThat(text(denied)).contains("'contracted' refused").contains("[480012, 480099]")
                    .doesNotContain("OTHER").doesNotContain("bob");
        } finally {
            alice.closeGracefully();
            bob.closeGracefully();
        }
    }

    @Test
    void q3_theCapturedCallerIsClearedFromTheInvokingThreadAfterwards() {
        var token = new org.springframework.security.authentication.TestingAuthenticationToken("carol", "pw", "X");
        String seen = SecuredInvocation.withCaller(token,
                () -> SecurityContextHolder.getContext().getAuthentication().getName());
        assertThat(seen).isEqualTo("carol");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void anAnonymousClientNeverReachesTheServer() {
        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder("http://localhost:" + port)
                .sseEndpoint("/ctx/sse").build();
        McpSyncClient anonymous = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(5)).initializationTimeout(Duration.ofSeconds(5)).build();
        try {
            assertThatThrownBy(anonymous::initialize).isInstanceOf(RuntimeException.class);
        } finally {
            anonymous.closeGracefully();
        }
    }
}
