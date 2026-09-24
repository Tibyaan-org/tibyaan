package com.tibyaan.cli.describe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Messages API client against a local fake endpoint (Volume 5 section 4.15.5.4): the exact request it sends and
 * how it reads each response shape the API returns, proven before the first real call rather than by it.
 */
class AnthropicMessagesModelTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<JsonNode> bodies = new CopyOnWriteArrayList<>();
    private final List<Map<String, List<String>>> headers = new CopyOnWriteArrayList<>();
    private final Deque<int[]> statuses = new ArrayDeque<>();
    private final Deque<String> replies = new ArrayDeque<>();
    private final List<Duration> slept = new ArrayList<>();
    private HttpServer server;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            bodies.add(mapper.readTree(exchange.getRequestBody()));
            headers.add(Map.copyOf(exchange.getRequestHeaders()));
            int[] status = statuses.isEmpty() ? new int[] {200} : statuses.poll();
            String reply = replies.isEmpty() ? "{}" : replies.poll();
            if (status.length > 1) {
                exchange.getResponseHeaders().add("retry-after", String.valueOf(status[1]));
            }
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status[0], bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private AnthropicMessagesModel model() {
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/messages");
        return new AnthropicMessagesModel("test-key", "claude-sonnet-5", HttpClient.newHttpClient(), endpoint,
                slept::add);
    }

    private void reply(int status, String body) {
        statuses.add(new int[] {status});
        replies.add(body);
    }

    private static String ended(String... textBlocks) {
        StringBuilder content = new StringBuilder("{\"type\":\"thinking\",\"thinking\":\"\",\"signature\":\"s\"}");
        for (String text : textBlocks) {
            content.append(",{\"type\":\"text\",\"text\":\"").append(text).append("\"}");
        }
        return "{\"type\":\"message\",\"role\":\"assistant\",\"stop_reason\":\"end_turn\",\"content\":[" + content + "]}";
    }

    @Test
    void theRequestCarriesTheModelTheCapAndTheMessageAndNoSamplingOrThinkingParameter() throws Exception {
        reply(200, ended("Creates a governorate."));

        model().describe("the prompt");

        JsonNode body = bodies.get(0);
        assertThat(body.path("model").asText()).isEqualTo("claude-sonnet-5");
        assertThat(body.path("max_tokens").asInt()).isEqualTo(AnthropicMessagesModel.MAX_TOKENS);
        assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("user");
        assertThat(body.path("messages").get(0).path("content").asText()).isEqualTo("the prompt");
        // Rejected with a 400 on the current models: the request must not carry them.
        assertThat(body.has("temperature")).isFalse();
        assertThat(body.has("top_p")).isFalse();
        assertThat(body.has("top_k")).isFalse();
        assertThat(body.has("thinking")).isFalse();
        Map<String, List<String>> sent = headers.get(0);
        assertThat(sent.get("X-api-key")).containsExactly("test-key");
        assertThat(sent.get("Anthropic-version")).containsExactly("2023-06-01");
        assertThat(sent.get("Content-type")).containsExactly("application/json");
    }

    @Test
    void theThinkingBlockIsSkippedAndEveryTextBlockIsTakenVerbatimInOrder() throws Exception {
        reply(200, ended("Creates a governorate ", "from its two titles."));

        assertThat(model().describe("p")).isEqualTo("Creates a governorate from its two titles.");
    }

    @Test
    void aTruncatedResponseIsRefusedNotTaken() {
        reply(200, "{\"stop_reason\":\"max_tokens\",\"content\":[{\"type\":\"text\",\"text\":\"Creates a gov\"}]}");

        assertThatThrownBy(() -> model().describe("p"))
                .isInstanceOf(DescriptionModel.ModelException.class)
                .hasMessageContaining("stop_reason 'max_tokens'")
                .hasMessageContaining("truncated");
    }

    @Test
    void aRefusalIsRefusedWithItsCategory() {
        reply(200, "{\"stop_reason\":\"refusal\",\"stop_details\":{\"type\":\"refusal\",\"category\":\"cyber\"},"
                + "\"content\":[]}");

        assertThatThrownBy(() -> model().describe("p"))
                .isInstanceOf(DescriptionModel.ModelException.class)
                .hasMessageContaining("stop_reason 'refusal'")
                .hasMessageContaining("category cyber");
    }

    @Test
    void anOverloadedOrRateLimitedResponseIsRetriedHonoringRetryAfter() throws Exception {
        statuses.add(new int[] {529});
        replies.add("{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\"}}");
        statuses.add(new int[] {429, 7});
        replies.add("{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\"}}");
        reply(200, ended("Lists cities."));

        assertThat(model().describe("p")).isEqualTo("Lists cities.");
        assertThat(bodies).hasSize(3);
        assertThat(slept).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(7));
    }

    @Test
    void retriesStopAfterTwoAndTheStatusIsNamed() {
        for (int i = 0; i < 3; i++) {
            statuses.add(new int[] {529});
            replies.add("{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\"}}");
        }

        assertThatThrownBy(() -> model().describe("p"))
                .isInstanceOf(DescriptionModel.ModelException.class)
                .hasMessageContaining("HTTP 529");
        assertThat(bodies).hasSize(1 + AnthropicMessagesModel.RETRIES);
    }

    @Test
    void aClientErrorIsNotRetriedAndItsBodyIsNamed() {
        reply(400, "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"bad\"}}");

        assertThatThrownBy(() -> model().describe("p"))
                .isInstanceOf(DescriptionModel.ModelException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageContaining("invalid_request_error");
        assertThat(bodies).hasSize(1);
    }
}
