package com.tibyaan.runtime.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Supplies sensible defaults for the Spring AI MCP server so a host app does not need a
 * {@code spring.ai.mcp.server} block of its own (Volume 7 section 4). The starter, not the app,
 * names the MCP server.
 *
 * <p>The defaults are added as the lowest-precedence property source, so anything the app sets in
 * its own {@code application.yml}, a profile, an environment variable, or a {@code -D} system
 * property still wins. The starter requires nothing of the app and overrides nothing it states.
 */
public final class TibyanDefaultPropertiesEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "tibyanRuntimeDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("spring.ai.mcp.server.name", "tibyaan");
        defaults.put("spring.ai.mcp.server.version", "0.1.0");
        // addLast: lowest precedence, so the app's own configuration always overrides these.
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }
}
