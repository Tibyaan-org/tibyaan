package com.tibyaan.cli.describe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * The OpenAI Responses API over the JDK's HTTP client (amendment DS-P, Volume 5 section 4.15.5.5; Volume 16
 * section 7, ADR-0016): one request shape, one response shape, no SDK, in the one package a test allows to open a
 * network connection.
 *
 * <p>The request carries {@code model}, {@code input}, {@link #MAX_OUTPUT_TOKENS} and {@code store: false} only
 * (DS-P3): no sampling parameter and no reasoning configuration, and {@code store} is sent because the Responses
 * API retains a response unless told not to and the prompt is the adopter's source. A response is taken only when
 * its {@code status} is {@code completed}; reasoning items are skipped, every {@code output_text} part of every
 * message item is taken verbatim, in order, and a {@code refusal} part refuses the entry (DS-P4). A 429 whose
 * error is {@code insufficient_quota} is a billing state and is never retried (DS-P5).
 */
public final class OpenAiResponsesModel implements DescriptionModel {

    static final String ENDPOINT = "https://api.openai.com/v1/responses";
    static final String PROVIDER = "openai";
    /** Headroom for a reasoning model: its reasoning tokens count against this cap. */
    static final int MAX_OUTPUT_TOKENS = 4096;
    /** Transient statuses retried, at most {@link #RETRIES} times: rate limit and server errors. */
    static final Set<Integer> RETRYABLE = Set.of(429, 500, 502, 503, 504);
    static final int RETRIES = 2;
    static final String INSUFFICIENT_QUOTA = "insufficient_quota";

    private final String apiKey;
    private final String model;
    private final HttpClient client;
    private final URI endpoint;
    private final AnthropicMessagesModel.Sleeper sleeper;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiResponsesModel(String apiKey, String model) {
        this(apiKey, model, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(),
                URI.create(ENDPOINT), duration -> Thread.sleep(duration.toMillis()));
    }

    OpenAiResponsesModel(String apiKey, String model, HttpClient client, URI endpoint,
                         AnthropicMessagesModel.Sleeper sleeper) {
        this.apiKey = apiKey;
        this.model = model;
        this.client = client;
        this.endpoint = endpoint;
        this.sleeper = sleeper;
    }

    /** DS-P7: the provider and the model, so the record names the vendor without relying on its naming. */
    @Override
    public String id() {
        return PROVIDER + ":" + model;
    }

    @Override
    public String describe(String prompt) throws ModelException {
        HttpRequest request = request(prompt);
        HttpResponse<String> response = send(request);
        for (int attempt = 1; retryable(response) && attempt <= RETRIES; attempt++) {
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
            String quota = quotaExhausted(response) ? " (insufficient_quota: a billing state, not retried)" : "";
            throw new ModelException("HTTP " + response.statusCode() + quota + " from " + endpoint + ": "
                    + head.substring(0, Math.min(300, head.length())));
        }
        return text(response.body());
    }

    private HttpRequest request(String prompt) throws ModelException {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("input", prompt);
        body.put("max_output_tokens", MAX_OUTPUT_TOKENS);
        body.put("store", false);
        try {
            return HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(120))
                    .header("authorization", "Bearer " + apiKey)
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

    private boolean retryable(HttpResponse<String> response) {
        return RETRYABLE.contains(response.statusCode()) && !quotaExhausted(response);
    }

    // The error body names the quota state in its code, and in its type on some responses; either one is enough.
    private boolean quotaExhausted(HttpResponse<String> response) {
        if (response.statusCode() != 429 || response.body() == null) {
            return false;
        }
        try {
            JsonNode error = mapper.readTree(response.body()).path("error");
            return INSUFFICIENT_QUOTA.equals(error.path("code").asText())
                    || INSUFFICIENT_QUOTA.equals(error.path("type").asText());
        } catch (IOException e) {
            return false;
        }
    }

    // The server's retry-after when it gives one (capped at 30 seconds), else 2 then 4 seconds.
    private static Duration backoff(HttpResponse<String> response, int attempt) {
        long seconds = response.headers().firstValue("retry-after")
                .flatMap(value -> {
                    try {
                        return Optional.of(Long.parseLong(value.trim()));
                    } catch (NumberFormatException e) {
                        return Optional.<Long>empty();
                    }
                })
                .orElse(2L << (attempt - 1));
        return Duration.ofSeconds(Math.max(0, Math.min(30, seconds)));
    }

    // The draft is the text of a response the model completed: every output_text part, verbatim, in order.
    private String text(String responseBody) throws ModelException {
        JsonNode root;
        try {
            root = mapper.readTree(responseBody);
        } catch (IOException e) {
            throw new ModelException("unreadable response: " + e.getMessage(), e);
        }
        String status = root.path("status").asText("");
        if (!"completed".equals(status)) {
            String reason = root.path("incomplete_details").path("reason").asText("");
            String error = root.path("error").path("message").asText("");
            throw new ModelException("the response did not complete: status '" + status + "'"
                    + (reason.isEmpty() ? "" : " (reason " + reason + ")")
                    + (error.isEmpty() ? "" : " (" + error + ")")
                    + "; nothing is taken from a " + ("max_output_tokens".equals(reason) ? "truncated" : "stopped")
                    + " response");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode item : root.path("output")) {
            if (!"message".equals(item.path("type").asText())) {
                continue;
            }
            for (JsonNode part : item.path("content")) {
                String type = part.path("type").asText();
                if ("refusal".equals(type)) {
                    throw new ModelException("the model refused: " + part.path("refusal").asText());
                }
                if ("output_text".equals(type)) {
                    text.append(part.path("text").asText());
                }
            }
        }
        if (text.length() == 0) {
            throw new ModelException("the response carried no output_text part");
        }
        return text.toString();
    }
}
