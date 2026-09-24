package com.tibyaan.runtime.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

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
@AutoConfiguration(beforeName = "org.springframework.ai.mcp.server.autoconfigure.McpWebMvcServerAutoConfiguration")
public class TibyanAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TibyanAutoConfiguration.class);

    /**
     * Spike SEC-0 and phase SEC-1, rule S1 (Volume 7 section 15): the WebMVC SSE transport provider, built as
     * Spring AI builds it (same endpoints, same base URL, same JSON mapper) plus the one thing Spring AI's does not
     * carry, an extractor that captures the caller the host authenticated into the per-request transport context.
     * Ordered before Spring AI's auto-configuration so its @ConditionalOnMissingBean yields to this one.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider")
    @ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "stdio", havingValue = "false", matchIfMissing = true)
    public io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider tibyanTransportProvider(
            ObjectMapper objectMapper, org.springframework.ai.mcp.server.autoconfigure.McpServerProperties properties) {
        return io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider.builder()
                .jsonMapper(new io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper(objectMapper))
                .baseUrl(properties.getBaseUrl())
                .messageEndpoint(properties.getSseMessageEndpoint())
                .sseEndpoint(properties.getSseEndpoint())
                .contextExtractor(CallerContext::capture)
                .build();
    }

    /**
     * The router that serves the SSE and message endpoints. Spike SEC-0 finding Q1: Spring AI's WHOLE WebMVC
     * configuration is conditional on no transport provider bean existing, so once the runtime supplies the
     * provider, the router that Spring AI would have built for it is gone too, and the runtime registers it.
     */
    @Bean
    @ConditionalOnClass(name = "io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider")
    @ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "stdio", havingValue = "false", matchIfMissing = true)
    public org.springframework.web.servlet.function.RouterFunction<org.springframework.web.servlet.function.ServerResponse> tibyanMcpRouterFunction(
            io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider provider) {
        return provider.getRouterFunction();
    }

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

    /**
     * Binds the exposure controls (Volume 7 sections 10 and 11) through the Spring {@link Environment}
     * rather than {@code @Value}, because the two lists must accept both the comma-separated property form
     * and the YAML list form (section 10.7, RX-10). A violated list condition throws here, so the host does
     * not start (section 10.9, D1).
     */
    @Bean
    public ToolCallbackProvider tibyanToolProvider(
            CapabilitiesManifest manifest,
            SpringExecutionAdapter executionAdapter,
            ObjectMapper objectMapper,
            Environment environment) {
        ExposurePolicy policy = ExposurePolicy.bind(environment);
        ToolRegistrar.Registration registration =
                new ToolRegistrar(objectMapper, policy).registration(manifest, executionAdapter);
        logStartup(registration, policy, SecuredInvocation.available());
        return ToolCallbackProvider.from(registration.callbacks());
    }

    /**
     * Startup honesty (Volume 7 section 10.6). The registered line names ONLY the tools actually
     * registered; before this phase it took its names from the whole manifest, so an operator was told a
     * closed capability was registered (the count was right and the names were wrong). Fixed here as a
     * rule-13 diagnostic amendment: no registration outcome changes. Then one line per excluded tool with
     * its reason token, and a WARN naming every security-unverifiable tool the global switch opened that
     * nobody listed by name.
     */
    /** The registration lines alone, the pre-SEC-1 arity kept for existing callers. */
    static void logStartup(ToolRegistrar.Registration registration) {
        logStartupRegistration(registration);
    }

    /**
     * SEC-1, rule S5 (Volume 7 section 15.4): after the registration lines, one caller-gate line saying whether
     * a caller is required, whether Spring Security is on the class path (the only way a caller can be captured),
     * how many registered tools carry a compiled contract, and how many are unverifiable and open only by list
     * or switch; a WARN when a caller is required but none can ever be captured.
     */
    static void logStartup(ToolRegistrar.Registration registration, ExposurePolicy policy, boolean securityPresent) {
        logStartupRegistration(registration);
        log.info("Tibyaan: caller gate: {}={}, Spring Security {}; {} registered tool(s) carry a compiled authority "
                        + "contract and refuse a caller holding none: {}; {} registered security-unverifiable tool(s) "
                        + "open only by list or switch: {}",
                ExposurePolicy.SECURITY_REQUIRE_CALLER, policy.requireCaller(),
                securityPresent ? "present" : "absent",
                registration.contractCarrying().size(), registration.contractCarrying(),
                registration.unverifiableRegistered().size(), registration.unverifiableRegistered());
        if (policy.requireCaller() && !securityPresent) {
            log.warn("Tibyaan: {}=true but Spring Security is not on the class path, so no caller can be captured: "
                            + "every reviewed WRITE or ACTION and every contract-carrying tool will refuse. Set it false "
                            + "only knowingly, for a host with no caller to capture.",
                    ExposurePolicy.SECURITY_REQUIRE_CALLER);
        }
        if (!policy.requireCaller()) {
            log.info("Tibyaan: {}=false: an anonymous caller may invoke a reviewed WRITE or ACTION; a tool carrying a "
                            + "compiled contract still refuses a caller it cannot check",
                    ExposurePolicy.SECURITY_REQUIRE_CALLER);
        }
    }

    private static void logStartupRegistration(ToolRegistrar.Registration registration) {
        log.info("Tibyaan: registered {} MCP tool(s): {}",
                registration.callbacks().size(), registration.registeredNames());
        registration.excluded().forEach((name, reason) ->
                log.info("Tibyaan: excluded MCP tool '{}' ({})", name, reason));
        // F9 (Volume 7 section 14, H1): one line naming every tool the policy would have registered but this
        // application cannot execute, with the reason, so the registered line above claims only what runs here.
        if (!registration.unhosted().isEmpty()) {
            log.info("Tibyaan: {} tool(s) not hosted by this application and not registered: {}",
                    registration.unhosted().size(),
                    registration.unhosted().entrySet().stream()
                            .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                            .toList());
        }
        if (!registration.openedByGlobalSwitchOnly().isEmpty()) {
            log.warn("Tibyaan: {} security-unverifiable tool(s) registered ONLY because {}=true; nobody named "
                            + "them in {}: {}",
                    registration.openedByGlobalSwitchOnly().size(),
                    ExposurePolicy.ATTESTED_EXPOSE_SECURITY_UNVERIFIABLE,
                    ExposurePolicy.SECURITY_EXPOSE_UNVERIFIABLE,
                    registration.openedByGlobalSwitchOnly());
        }
    }
}
