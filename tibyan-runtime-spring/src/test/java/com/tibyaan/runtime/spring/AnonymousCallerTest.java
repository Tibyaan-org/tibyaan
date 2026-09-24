package com.tibyaan.runtime.spring;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase SEC-1, rule S4 end to end (Volume 7 section 15.4), fixtures SC-3 and SC-8 of section 15.6 through a real
 * MCP client: a host whose MCP endpoints are public, so the anonymous caller reaches the runtime, which must refuse
 * a reviewed write and a contract-carrying tool and admit a plain read.
 */
@SpringBootTest(classes = AnonymousCallerTest.Host.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "tibyan.capabilities=classpath:no-such-manifest.json",
                "tibyan.writes.allow=reviewed_record",
                "tibyan.security.exposeUnverifiable=reviewed_open"
        })
class AnonymousCallerTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Host {

        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(requests -> requests
                            .requestMatchers("/sse", "/mcp/message").permitAll()
                            .anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults())
                    .build();
        }

        @Bean
        InMemoryUserDetailsManager users() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("alice").password("{noop}pw").authorities("480012").build());
        }

        @Bean
        Svc svc() {
            return new Svc();
        }

        @Bean
        CapabilitiesManifest tibyanCapabilitiesManifest() {
            String svc = Svc.class.getCanonicalName();
            return new CapabilitiesManifest("anon", "0.1", List.of(
                    new CapabilityDescriptor("ping", "Read Ping.", svc + "#ping", "STRING", List.of()),
                    new CapabilityDescriptor("contracted", "Needs 480012.", svc + "#contracted", "STRING", List.of())
                            .withRequiredAuthorities(List.of("480012")),
                    CapabilityDescriptor.reviewed("reviewed_record",
                            "Reviewed by r (human), not tool-verified; the tool labeled it WRITE. Records.",
                            svc + "#record", "STRING", List.of(), CapabilityDescriptor.KIND_WRITE, false,
                            new ReviewDescriptor("WRITE", "r", "2026-09-24", "human")),
                    CapabilityDescriptor.reviewed("reviewed_open",
                            "Reviewed by r (human), not tool-verified; the tool labeled it READ. Open.",
                            svc + "#open", "STRING", List.of(), CapabilityDescriptor.KIND_READ, true,
                            new ReviewDescriptor("READ", "r", "2026-09-24", "human"))));
        }
    }

    public static class Svc {
        static final List<String> recorded = new CopyOnWriteArrayList<>();

        public String ping() {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication == null ? "pong for nobody" : "pong for " + authentication.getName();
        }

        public String contracted() {
            return "contracted ok";
        }

        public String record() {
            recorded.add("recorded");
            return "recorded";
        }

        public String open() {
            return "open";
        }
    }

    @LocalServerPort
    int port;

    private McpSyncClient anonymous;

    @BeforeEach
    void connect() {
        anonymous = client(null);
        Svc.recorded.clear();
    }

    @AfterEach
    void disconnect() {
        anonymous.closeGracefully();
    }

    private McpSyncClient client(String user) {
        HttpClientSseClientTransport.Builder transport = HttpClientSseClientTransport.builder("http://localhost:" + port)
                .sseEndpoint("/sse");
        if (user != null) {
            String basic = "Basic " + Base64.getEncoder().encodeToString((user + ":pw").getBytes(StandardCharsets.UTF_8));
            transport.customizeRequest(builder -> builder.header("Authorization", basic));
        }
        McpSyncClient client = McpClient.sync(transport.build())
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
    void theRegisteredSurfaceIsUnchangedByTheGate() {
        assertThat(anonymous.listTools().tools()).extracting(McpSchema.Tool::name)
                .containsExactlyInAnyOrder("ping", "contracted", "reviewed_record", "reviewed_open");
    }

    @Test
    void sc3_theAnonymousCallerIsRefusedOnTheReviewedWriteBeforeAnythingRuns() {
        McpSchema.CallToolResult denied = anonymous.callTool(new McpSchema.CallToolRequest("reviewed_record", Map.of()));
        assertThat(denied.isError()).isTrue();
        assertThat(text(denied)).contains("'reviewed_record' refused").contains("no authenticated caller")
                .contains("reviewed WRITE");
        assertThat(Svc.recorded).isEmpty();
    }

    @Test
    void sc3_theAnonymousCallerIsRefusedOnAContractCarryingTool() {
        McpSchema.CallToolResult denied = anonymous.callTool(new McpSchema.CallToolRequest("contracted", Map.of()));
        assertThat(denied.isError()).isTrue();
        assertThat(text(denied)).contains("'contracted' refused").contains("[480012]");
    }

    @Test
    void aPlainReadKeepsTodaysBehaviorForTheAnonymousCaller() {
        McpSchema.CallToolResult ok = anonymous.callTool(new McpSchema.CallToolRequest("ping", Map.of()));
        assertThat(ok.isError()).isFalse();
        assertThat(text(ok)).isEqualTo("\"pong for nobody\"");
    }

    @Test
    void sc8_aListedSecurityUnverifiableReadIsGatedByTheListAloneAndRuns() {
        McpSchema.CallToolResult ok = anonymous.callTool(new McpSchema.CallToolRequest("reviewed_open", Map.of()));
        assertThat(ok.isError()).isFalse();
        assertThat(text(ok)).isEqualTo("\"open\"");
    }

    @Test
    void theSameWriteAndContractToolAdmitTheAuthenticatedCallerOnThePublicEndpoint() {
        McpSyncClient alice = client("alice");
        try {
            assertThat(text(alice.callTool(new McpSchema.CallToolRequest("reviewed_record", Map.of())))).isEqualTo("\"recorded\"");
            assertThat(text(alice.callTool(new McpSchema.CallToolRequest("contracted", Map.of())))).isEqualTo("\"contracted ok\"");
            assertThat(text(alice.callTool(new McpSchema.CallToolRequest("ping", Map.of())))).isEqualTo("\"pong for alice\"");
        } finally {
            alice.closeGracefully();
        }
    }
}
