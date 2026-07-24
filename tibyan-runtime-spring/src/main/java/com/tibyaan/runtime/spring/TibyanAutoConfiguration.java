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
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.util.List;

/**
 * The Tibyaan Spring runtime (Volume 7 section 4), distributed as a Spring Boot starter. Adding
 * the one dependency activates this auto-configuration, which:
 *
 * <ol>
 *   <li>finds and loads the generated capabilities manifest,</li>
 *   <li>provides the {@link SpringExecutionAdapter} that binds capabilities to real beans,</li>
 *   <li>registers one MCP tool per capability through a {@link ToolCallbackProvider}, which the
 *       Spring AI MCP server exposes inside this application's own context.</li>
 * </ol>
 *
 * <p>An app needs only this one dependency. The manifest is found by {@link ManifestResolver}
 * without a launch property: ship it on the classpath at {@code tibyan/capabilities.json} and it
 * is picked up. The Spring AI MCP server's name and version are defaulted by
 * {@link TibyanDefaultPropertiesEnvironmentPostProcessor}, so no {@code spring.ai.mcp.server} block
 * is required either; the app may still override both through normal Spring properties.
 *
 * <p>This graduates the spike: the single hard-coded getProduct tool is gone, replaced by tools
 * derived from the IR. Health, observability, and lifecycle hardening are deliberately out of
 * scope for the MVP (Volume 7 lists them as later).
 */
@AutoConfiguration
public class TibyanAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TibyanAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public CapabilitiesManifest tibyanCapabilitiesManifest(
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper,
            @Value("${tibyan.capabilities:}") String explicitLocation) {
        Resource resource = new ManifestResolver(resourceLoader).resolve(explicitLocation);
        CapabilitiesManifest manifest = new CapabilitiesLoader(objectMapper).load(resource);
        log.info("Tibyaan: loaded {} capability(ies) from {}",
                manifest.tools().size(), resource.getDescription());
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
            ObjectMapper objectMapper,
            @Value("${tibyan.attested.expose:true}") boolean exposeAttested,
            @Value("${tibyan.attested.exposeSecurityUnverifiable:false}") boolean exposeSecurityUnverifiable) {
        List<ToolCallback> callbacks = new ToolRegistrar(objectMapper, exposeAttested,
                exposeSecurityUnverifiable).register(manifest, executionAdapter);
        log.info("Tibyaan: registered {} MCP tool(s): {}",
                callbacks.size(), manifest.tools().stream().map(CapabilityDescriptor::name).toList());
        return ToolCallbackProvider.from(callbacks);
    }
}
