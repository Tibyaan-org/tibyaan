package com.tibyaan.runtime.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

import java.util.List;

/**
 * The Tibyaan Spring runtime (Volume 7 section 4), distributed as a Spring Boot starter. Adding
 * the one dependency activates this auto-configuration, which:
 *
 * <ol>
 *   <li>loads the generated capabilities manifest,</li>
 *   <li>provides the {@link SpringExecutionAdapter} that binds capabilities to real beans,</li>
 *   <li>registers one MCP tool per capability through a {@link ToolCallbackProvider}, which the
 *       Spring AI MCP server exposes inside this application's own context.</li>
 * </ol>
 *
 * <p>This graduates the spike: the single hard-coded getProduct tool is gone, replaced by tools
 * derived from the IR. Health, observability, and lifecycle hardening are deliberately out of
 * scope for the MVP (Volume 7 lists them as later).
 */
@AutoConfiguration
public class TibyanAutoConfiguration {

    /** The manifest location, overridable so a host can point at a generated file without edits. */
    static final String DEFAULT_MANIFEST_LOCATION = "classpath:/tibyan/capabilities.json";

    private static final Logger log = LoggerFactory.getLogger(TibyanAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public CapabilitiesManifest tibyanCapabilitiesManifest(
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper,
            @Value("${tibyan.capabilities:" + DEFAULT_MANIFEST_LOCATION + "}") String location) {
        CapabilitiesManifest manifest =
                new CapabilitiesLoader(objectMapper).load(resourceLoader.getResource(location));
        log.info("Tibyaan: loaded {} capability(ies) from {}", manifest.tools().size(), location);
        return manifest;
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringExecutionAdapter tibyanExecutionAdapter(
            ApplicationContext applicationContext, CapabilitiesManifest manifest) {
        return new SpringExecutionAdapter(applicationContext, manifest);
    }

    @Bean
    public ToolCallbackProvider tibyanToolProvider(
            CapabilitiesManifest manifest,
            SpringExecutionAdapter executionAdapter,
            ObjectMapper objectMapper) {
        List<ToolCallback> callbacks = new ToolRegistrar(objectMapper).register(manifest, executionAdapter);
        log.info("Tibyaan: registered {} MCP tool(s): {}",
                callbacks.size(), manifest.tools().stream().map(CapabilityDescriptor::name).toList());
        return ToolCallbackProvider.from(callbacks);
    }
}
