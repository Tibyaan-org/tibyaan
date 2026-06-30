package com.tibyaan.runtime.spring;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The starter supplies sensible MCP server defaults so the app needs no {@code spring.ai.mcp.server}
 * block, but never overrides what the app states (Volume 7 section 4).
 */
class TibyanDefaultPropertiesEnvironmentPostProcessorTest {

    private final TibyanDefaultPropertiesEnvironmentPostProcessor postProcessor =
            new TibyanDefaultPropertiesEnvironmentPostProcessor();

    @Test
    void suppliesMcpServerNameAndVersionDefaults() {
        StandardEnvironment environment = new StandardEnvironment();

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.ai.mcp.server.name")).isEqualTo("tibyaan");
        assertThat(environment.getProperty("spring.ai.mcp.server.version")).isEqualTo("0.1.0");
    }

    @Test
    void doesNotOverrideWhatTheAppAlreadyConfigured() {
        StandardEnvironment environment = new StandardEnvironment();
        // A higher-precedence source stands in for the app's own application.yml.
        environment.getPropertySources().addFirst(new MapPropertySource(
                "appConfig", Map.of("spring.ai.mcp.server.name", "my-own-server")));

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        // The app's value wins; the default still fills in what the app left unset.
        assertThat(environment.getProperty("spring.ai.mcp.server.name")).isEqualTo("my-own-server");
        assertThat(environment.getProperty("spring.ai.mcp.server.version")).isEqualTo("0.1.0");
    }
}
