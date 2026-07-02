package com.tibyaan.backend.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * A tiny, dependency-free template renderer: load a template from the classpath and replace
 * {@code {{TOKEN}}} markers. Output line endings are normalized to {@code \n} so generation is
 * deterministic across platforms, which is what makes golden-file tests stable (Volume 6
 * section 5).
 */
final class TemplateRenderer {

    private TemplateRenderer() {
    }

    static String render(String templateResource, Map<String, String> tokens) {
        String result = load(templateResource);
        for (Map.Entry<String, String> token : tokens.entrySet()) {
            result = result.replace("{{" + token.getKey() + "}}", token.getValue());
        }
        return normalize(result);
    }

    static String normalize(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    private static String load(String templateResource) {
        try (InputStream in = TemplateRenderer.class.getResourceAsStream(templateResource)) {
            if (in == null) {
                throw new IllegalStateException("template not found on classpath: " + templateResource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read template " + templateResource, e);
        }
    }
}
