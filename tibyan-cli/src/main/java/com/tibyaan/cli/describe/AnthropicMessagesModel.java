package com.tibyaan.cli.describe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

/**
 * The Anthropic Messages API over the JDK's HTTP client (Volume 16 section 7, ADR-0016): one request shape, one
 * response shape, no SDK. This class and its package are the only place in the tool that opens a network
 * connection; a test asserts it.
 *
 * <p>Readiness corrections before the first real run (Volume 5 section 4.15.5.4), checked against the current API
 * reference: the request carries no sampling parameter, because {@code temperature} is rejected with a 400 on the
 * current models, including the default {@code claude-sonnet-5}; it sends no {@code thinking} configuration, so
 * each model runs its own default (adaptive where thinking is on by default), and {@link #MAX_TOKENS} leaves that
 * thinking room, because thinking tokens count against the cap; and a response that did not end its turn
 * ({@code max_tokens}, {@code refusal} or anything else) is refused rather than taken, because a truncated
 * sentence under 600 characters would otherwise pass DS-3 and be written as a draft.
 */
public final class AnthropicMessagesModel implements DescriptionModel {

    static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    static final String PROVIDER = "anthropic";
    static final String API_VERSION = "2023-06-01";
    /** Headroom for a model whose thinking is on by default: its thinking tokens count against this cap. */
    static final int MAX_TOKENS = 4096;
    /** Transient statuses retried, at most {@link #RETRIES} times: rate limit, server errors, overloaded. */
    static final Set<Integer> RETRYABLE = Set.of(429, 500, 502, 503, 504, 529);
    static final int RETRIES = 2;

    /** How the client waits between retries; replaced in tests so they do not sleep. */
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final String apiKey;
    private final String model;
    private final HttpClient client;
    private final URI endpoint;
    private final Sleeper sleeper;
    private final ObjectMapper mapper = new ObjectMapper();

    public AnthropicMessagesModel(String apiKey, String model) {
        this(apiKey, model, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(),
                URI.create(ENDPOINT), duration -> Thread.sleep(duration.toMillis()));
    }

    AnthropicMessagesModel(String apiKey, String model, HttpClient client) {
        this(apiKey, model, client, URI.create(ENDPOINT), duration -> Thread.sleep(duration.toMillis()));
    }

    AnthropicMessagesModel(String apiKey, String model, HttpClient client, URI endpoint, Sleeper sleeper) {
        this.apiKey = apiKey;
        this.model = model;
        this.client = client;
        this.endpoint = endpoint;
        this.sleeper = sleeper;
    }

    /** DS-P7 (Volume 5 section 4.15.5.5): the provider and the model, recorded as {@code describedBy}. */
    @Override
    public String id() {
        return PROVIDER + ":" + model;
    }

    @Override
    public String describe(String prompt) throws ModelException {
        HttpRequest request = request(prompt);
        HttpResponse<String> response = send(request);
        for (int attempt = 1; RETRYABLE.contains(response.statusCode()) && attempt <= RETRIES; attempt++) {
            try {
                sleeper.sleep(backoff(response, attempt));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ModelException("interrupted while waiting to retry " + endpoint, e);
            }
            response = send(request);
        }
        if (response.statusCode() / 100 != 2) {
            String head = response.body() == null ? "" : response.body();
            throw new ModelException("HTTP " + response.statusCode() + " from " + endpoint + ": "
                    + head.substring(0, Math.min(300, head.length())));
        }
        return text(response.body());
    }

    private HttpRequest request(String prompt) throws ModelException {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", MAX_TOKENS);
        ArrayNode messages = body.putArray("messages");
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", prompt);
        try {
            return HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(120))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
        } catch (IOException e) {
            throw new ModelException("could not build the request: " + e.getMessage(), e);
        }
    }

    private HttpResponse<String> send(HttpRequest request) throws ModelException {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ModelException("the request to " + endpoint + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelException("interrupted while waiting for " + endpoint, e);
        }
    }

    // The server's retry-after when it gives one (capped at 30 seconds), else 2 then 4 seconds.
    private static Duration backoff(HttpResponse<String> response, int attempt) {
        long seconds = response.headers().firstValue("retry-after")
                .flatMap(value -> {
                    try {
                        return java.util.Optional.of(Long.parseLong(value.trim()));
                    } catch (NumberFormatException e) {
                        return java.util.Optional.empty();
                    }
                })
                .orElse(2L << (attempt - 1));
        return Duration.ofSeconds(Math.max(0, Math.min(30, seconds)));
    }

    // The draft is the text the model wrote in a turn it ended itself: every text block, verbatim, in order. A
    // thinking block is skipped (its text is empty unless display is requested, and it is not the answer).
    private String text(String responseBody) throws ModelException {
        JsonNode root;
        try {
            root = mapper.readTree(responseBody);
        } catch (IOException e) {
            throw new ModelException("unreadable response: " + e.getMessage(), e);
        }
        String stopReason = root.path("stop_reason").asText("");
        if (!"end_turn".equals(stopReason)) {
            JsonNode details = root.path("stop_details");
            String category = details.path("category").isMissingNode() || details.path("category").isNull()
                    ? "" : " (category " + details.path("category").asText() + ")";
            throw new ModelException("the model did not end its turn: stop_reason '" + stopReason + "'" + category
                    + "; nothing is taken from a " + ("max_tokens".equals(stopReason) ? "truncated" : "stopped")
                    + " response");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode block : root.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText());
            }
        }
        if (text.length() == 0) {
            throw new ModelException("the response carried no text block");
        }
        return text.toString();
    }
}
