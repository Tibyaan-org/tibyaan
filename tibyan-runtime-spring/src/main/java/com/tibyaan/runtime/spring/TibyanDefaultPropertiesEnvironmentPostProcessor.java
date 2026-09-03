package com.tibyaan.runtime.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

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

    /**
     * Classpath location of the build-filtered file carrying this module's Maven version.
     */
    static final String VERSION_RESOURCE = "/tibyan/runtime-version.properties";

    /**
     * Reported when the version resource is missing or unreadable, which happens only in a broken
     * or hand-assembled classpath. It is deliberately not a plausible-looking version: a wrong
     * number that looks real is worse than one that is obviously a fallback.
     */
    static final String UNKNOWN_VERSION = "unknown";

    /**
     * The advertised MCP server version is the runtime module's own Maven version, read once from
     * a resource filtered at build time. It is derived rather than hardcoded on purpose: a literal
     * here silently outlives the release that set it, and this value is what MCP clients see in
     * {@code serverInfo}.
     */
    private static final String SERVER_VERSION = readVersion();

    private static String readVersion() {
        try (InputStream in = TibyanDefaultPropertiesEnvironmentPostProcessor.class
                .getResourceAsStream(VERSION_RESOURCE)) {
            if (in == null) {
                return UNKNOWN_VERSION;
            }
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version");
            if (version == null || version.isBlank() || version.contains("${") || version.contains("@")) {
                // An unsubstituted token means resource filtering did not run; report it as unknown
                // rather than advertising a literal "${project.version}" to MCP clients.
                return UNKNOWN_VERSION;
            }
            return version.trim();
        } catch (IOException e) {
            return UNKNOWN_VERSION;
        }
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("spring.ai.mcp.server.name", "tibyaan");
        defaults.put("spring.ai.mcp.server.version", SERVER_VERSION);
        // addLast: lowest precedence, so the app's own configuration always overrides these.
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }
}
