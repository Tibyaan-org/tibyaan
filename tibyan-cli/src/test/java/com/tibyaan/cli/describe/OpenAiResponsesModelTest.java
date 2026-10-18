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
 * The Responses API client against a local fake endpoint (amendment DS-P, Volume 5 section 4.15.5.5, fixtures DS-7
 * to DS-12): the exact request it sends and how it reads each response shape, proven before the first real call.
 */
class OpenAiResponsesModelTest {

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
        server.createContext("/v1/responses", exchange -> {
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

    private OpenAiResponsesModel model() {
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/responses");
        return new OpenAiResponsesModel("test-key", "test-model", HttpClient.newHttpClient(), endpoint, slept::add);
    }

    private void reply(int status, String body) {
        statuses.add(new int[] {status});
        replies.add(body);
    }

    private static String completed(String... parts) {
        StringBuilder content = new StringBuilder();
        for (String text : parts) {
            content.append(content.length() == 0 ? "" : ",")
                    .append("{\"type\":\"output_text\",\"text\":\"").append(text).append("\",\"annotations\":[]}");
        }
        return "{\"object\":\"response\",\"status\":\"completed\",\"output\":["
                + "{\"type\":\"reasoning\",\"id\":\"rs_1\",\"summary\":[]},"
                + "{\"type\":\"message\",\"id\":\"msg_1\",\"role\":\"assistant\",\"status\":\"completed\",\"content\":["
                + content + "]}]}";
    }

    private static final String RATE_LIMITED =
            "{\"error\":{\"message\":\"slow down\",\"type\":\"requests\",\"param\":null,\"code\":\"rate_limit_exceeded\"}}";

    @Test
    void ds7_theRequestCarriesExactlyModelInputCapAndStoreFalse() throws Exception {
        reply(200, completed("Creates a governorate."));

        model().describe("the prompt");

        JsonNode body = bodies.get(0);
        List<String> fields = new ArrayList<>();
        body.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("model", "input", "max_output_tokens", "store");
        assertThat(body.path("model").asText()).isEqualTo("test-model");
        assertThat(body.path("input").asText()).isEqualTo("the prompt");
        assertThat(body.path("max_output_tokens").asInt()).isEqualTo(OpenAiResponsesModel.MAX_OUTPUT_TOKENS);
        assertThat(body.path("store").isBoolean()).isTrue();
        assertThat(body.path("store").asBoolean()).isFalse();
        assertThat(body.has("temperature")).isFalse();
        assertThat(body.has("top_p")).isFalse();
        assertThat(body.has("reasoning")).isFalse();
        Map<String, List<String>> sent = headers.get(0);
        assertThat(sent.get("Authorization")).containsExactly("Bearer test-key");
        assertThat(sent.get("Content-type")).containsExactly("application/json");
    }

    @Test
    void ds7_theIdNamesTheProviderAndTheModel() {
        assertThat(model().id()).isEqualTo("openai:test-model");
    }

    @Test
    void ds8_theReasoningItemIsSkippedAndEveryOutputTextPartIsTakenVerbatimInOrder() throws Exception {
        reply(200, completed("Creates a governorate ", "from its two titles."));

        assertThat(model().describe("p")).isEqualTo("Creates a governorate from its two titles.");
    }

    @Test
    void ds9_anIncompleteResponseIsRefusedNamingItsReason() {
        reply(200, "{\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"},\"output\":["
                + "{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"Creates a gov\"}]}]}");

        assertThatThrownBy(() -> model().describe("p"))
                .isInstanceOf(DescriptionModel.ModelException.class)
                .hasMessageContaining("status 'incomplete'")
                .hasMessageContaining("reason max_output_tokens")
                .hasMessageContaining("truncated");
    }

    @Test
    void ds10_aRefusalPartRefusesTheEntryNamingIt() {
        reply(200, "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"content\":["
                + "{\"type\":\"refusal\",\"refusal\":\"I cannot help with that.\"}]}]}");

        assertThatThrownBy(() -> model().describe("p"))
                .isInstanceOf(DescriptionModel.ModelException.class)
                .hasMessageContaining("refused")
                .hasMessageContaining("I cannot help with that.");
    }

    @Test
    void ds11_aRateLimitIsRetriedHonoringRetryAfterAndRetriesStopAfterTwo() throws Exception {
        statuses.add(new int[] {503});
        replies.add("{\"error\":{\"message\":\"busy\",\"type\":\"server_error\",\"param\":null,\"code\":null}}");
        statuses.add(new int[] {429, 7});
        replies.add(RATE_LIMITED);
        reply(200, completed("Lists cities."));

        assertThat(model().describe("p")).isEqualTo("Lists cities.");
        assertThat(slept).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(7));

        bodies.clear();
        for (int i = 0; i < 3; i++) {
            statuses.add(new int[] {503});
            replies.add("{\"error\":{\"message\":\"busy\",\"type\":\"server_error\",\"param\":null,\"code\":null}}");
        }
        assertThatThrownBy(() -> model().describe("p"))
                .isInstanceOf(DescriptionModel.ModelException.class)
                .hasMessageContaining("HTTP 503");
        assertThat(bodies).hasSize(1 + OpenAiResponsesModel.RETRIES);
    }

    @Test
    void ds12_anExhaustedQuotaIsNotRetriedAndNeitherIsAClientError() {
        reply(429, "{\"error\":{\"message\":\"You exceeded your current quota\",\"type\":\"insufficient_quota\","
                + "\"param\":null,\"code\":\"insufficient_quota\"}}");

        assertThatThrownBy(() -> model().describe("p"))
                .isInstanceOf(DescriptionModel.ModelException.class)
                .hasMessageContaining("HTTP 429")
                .hasMessageContaining("insufficient_quota");
        assertThat(bodies).hasSize(1);

        bodies.clear();
        reply(400, "{\"error\":{\"message\":\"bad\",\"type\":\"invalid_request_error\",\"param\":\"x\",\"code\":null}}");
        assertThatThrownBy(() -> model().describe("p"))
                .isInstanceOf(DescriptionModel.ModelException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageContaining("invalid_request_error");
        assertThat(bodies).hasSize(1);
        assertThat(slept).isEmpty();
    }
}
