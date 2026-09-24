package com.tibyaan.runtime.spring;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase SEC-1, fixture SC-4 end to end (Volume 7 section 15.6): with {@code tibyan.security.requireCaller=false},
 * the same public host admits the anonymous caller to the reviewed write and still refuses the contract-carrying
 * tool, because a contract admits nobody it cannot check.
 */
@SpringBootTest(classes = AnonymousCallerTest.Host.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "tibyan.capabilities=classpath:no-such-manifest.json",
                "tibyan.writes.allow=reviewed_record",
                "tibyan.security.exposeUnverifiable=reviewed_open",
                "tibyan.security.requireCaller=false"
        })
class AnonymousCallerAdmittedTest {

    @LocalServerPort
    int port;

    private static String text(McpSchema.CallToolResult result) {
        return result.content().isEmpty() ? "" : ((McpSchema.TextContent) result.content().get(0)).text();
    }

    @Test
    void sc4_requireCallerFalseAdmitsTheAnonymousWriteAndStillRefusesTheContract() {
        McpSyncClient anonymous = McpClient.sync(HttpClientSseClientTransport.builder("http://localhost:" + port)
                        .sseEndpoint("/sse").build())
                .requestTimeout(Duration.ofSeconds(30)).initializationTimeout(Duration.ofSeconds(30)).build();
        try {
            anonymous.initialize();
            McpSchema.CallToolResult write = anonymous.callTool(new McpSchema.CallToolRequest("reviewed_record", Map.of()));
            assertThat(write.isError()).isFalse();
            assertThat(text(write)).isEqualTo("\"recorded\"");

            McpSchema.CallToolResult contract = anonymous.callTool(new McpSchema.CallToolRequest("contracted", Map.of()));
            assertThat(contract.isError()).isTrue();
            assertThat(text(contract)).contains("'contracted' refused").contains("[480012]");
        } finally {
            anonymous.closeGracefully();
        }
    }
}
