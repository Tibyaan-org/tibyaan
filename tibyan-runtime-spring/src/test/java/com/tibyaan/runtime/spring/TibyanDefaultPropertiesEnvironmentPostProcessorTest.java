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
 *
 * <p>The version assertions deliberately do not name a version. The advertised version is derived
 * from the module's own Maven version at build time, so a literal here would have to be edited on
 * every release, which is exactly the drift that made this derivation necessary: the value sat at
 * {@code 0.1.0} through the 0.2.0 and 0.3.0 releases.
 */
class TibyanDefaultPropertiesEnvironmentPostProcessorTest {

    private final TibyanDefaultPropertiesEnvironmentPostProcessor postProcessor =
            new TibyanDefaultPropertiesEnvironmentPostProcessor();

    @Test
    void suppliesMcpServerNameAndVersionDefaults() {
        StandardEnvironment environment = new StandardEnvironment();

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.ai.mcp.server.name")).isEqualTo("tibyaan");
        assertThat(environment.getProperty("spring.ai.mcp.server.version"))
                .isNotBlank()
                .matches("[0-9]+[.][0-9]+[.][0-9]+(-SNAPSHOT)?");
    }

    @Test
    void advertisedVersionIsResolvedFromTheBuild() {
        StandardEnvironment environment = new StandardEnvironment();

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        String version = environment.getProperty("spring.ai.mcp.server.version");
        // The resource must be present and filtered. "unknown" means it was missing; a "${" means
        // Maven copied it without substituting, which would advertise a literal token to clients.
        assertThat(version).isNotEqualTo(TibyanDefaultPropertiesEnvironmentPostProcessor.UNKNOWN_VERSION);
        assertThat(version).doesNotContain("${");
    }

    @Test
    void doesNotOverrideWhatTheAppAlreadyConfigured() {
        StandardEnvironment environment = new StandardEnvironment();
        // A higher-precedence source stands in for the app's own application.yml.
        environment.getPropertySources().addFirst(new MapPropertySource(
                "appConfig", Map.of(
                        "spring.ai.mcp.server.name", "my-own-server",
                        "spring.ai.mcp.server.version", "9.9.9")));

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        // The app's values win, including the version: the starter states a default, never a policy.
        assertThat(environment.getProperty("spring.ai.mcp.server.name")).isEqualTo("my-own-server");
        assertThat(environment.getProperty("spring.ai.mcp.server.version")).isEqualTo("9.9.9");
    }

    @Test
    void stillFillsInWhatTheAppLeftUnset() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "appConfig", Map.of("spring.ai.mcp.server.name", "my-own-server")));

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.ai.mcp.server.name")).isEqualTo("my-own-server");
        assertThat(environment.getProperty("spring.ai.mcp.server.version"))
                .isNotBlank()
                .matches("[0-9]+[.][0-9]+[.][0-9]+(-SNAPSHOT)?");
    }
}
