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

/**
 * The Anthropic Messages API over the JDK's HTTP client (Volume 16 section 7, ADR-0016): one request shape, one
 * response shape, no SDK. This class and its package are the only place in the tool that opens a network
 * connection; a test asserts it.
 */
public final class AnthropicMessagesModel implements DescriptionModel {

    static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    static final String API_VERSION = "2023-06-01";
    static final int MAX_TOKENS = 400;

    private final String apiKey;
    private final String model;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public AnthropicMessagesModel(String apiKey, String model) {
        this(apiKey, model, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build());
    }

    AnthropicMessagesModel(String apiKey, String model, HttpClient client) {
        this.apiKey = apiKey;
        this.model = model;
        this.client = client;
    }

    @Override
    public String id() {
        return model;
    }

    @Override
    public String describe(String prompt) throws ModelException {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", MAX_TOKENS);
        body.put("temperature", 0);
        ArrayNode messages = body.putArray("messages");
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", prompt);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                    .timeout(Duration.ofSeconds(90))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
        } catch (IOException e) {
            throw new ModelException("could not build the request: " + e.getMessage(), e);
        }
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ModelException("the request to " + ENDPOINT + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelException("interrupted while waiting for " + ENDPOINT, e);
        }
        if (response.statusCode() / 100 != 2) {
            String head = response.body() == null ? "" : response.body();
            throw new ModelException("HTTP " + response.statusCode() + " from " + ENDPOINT + ": "
                    + head.substring(0, Math.min(300, head.length())));
        }
        try {
            JsonNode content = mapper.readTree(response.body()).path("content");
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText())) {
                    return block.path("text").asText();
                }
            }
            throw new ModelException("the response carried no text block");
        } catch (IOException e) {
            throw new ModelException("unreadable response: " + e.getMessage(), e);
        }
    }
}
