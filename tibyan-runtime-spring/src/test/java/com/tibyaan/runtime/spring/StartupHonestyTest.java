package com.tibyaan.runtime.spring;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Startup honesty (Volume 7 section 10.6, and section 11 for the write gate), through the real
 * auto-configuration bean method and the real logger.
 *
 * <p>The incidental defect recorded in section 10.6 and fixed here as a rule-13 diagnostic amendment: the
 * "registered N MCP tool(s)" line took its NAMES from the whole manifest, so an operator was told a closed
 * capability was registered. It now names only what registered; every excluded tool gets its own line
 * with its reason token; and the global switch's WARN names exactly the unverifiable tools nobody listed.
 */
class StartupHonestyTest {

    private static final String VERIFIED = "getProduct";
    private static final String CITIES = "attested_citiesByFilters";
    private static final String TENANTS = "attested_searchTenants";
    private static final String READ = "reviewed_findOverdue";
    private static final String WRITE = "reviewed_recordPayment";

    /** F9 (Volume 7 section 14): the manifest binds to classes this test context hosts, so every tool the policy
     * opens registers; an unhosted binding would be named and skipped instead. */
    public static class ProductService {
        public String findById(Long id) {
            return "product";
        }
    }

    public static class Svc {
        public String cities() {
            return "cities";
        }

        public String tenants() {
            return "tenants";
        }

        public String overdue() {
            return "overdue";
        }

        public String record() {
            return "record";
        }
    }

    private static final String PRODUCT_SERVICE = ProductService.class.getCanonicalName();
    private static final String SVC = Svc.class.getCanonicalName();

    private final ObjectMapper mapper = new ObjectMapper();
    private final Logger logger = (Logger) LoggerFactory.getLogger(TibyanAutoConfiguration.class);
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();

    @BeforeEach
    void attach() {
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(events);
        events.stop();
    }

    private static CapabilitiesManifest manifest() {
        return new CapabilitiesManifest("m", "0.1", List.of(
                new CapabilityDescriptor(VERIFIED, "Read Product.", PRODUCT_SERVICE + "#findById", "Product", List.of()),
                new CapabilityDescriptor(CITIES, "Human-attested.", SVC + "#cities", "City", List.of(), true, true, false),
                new CapabilityDescriptor(TENANTS, "Human-attested.", SVC + "#tenants", "Tenant", List.of(), true, true, false),
                CapabilityDescriptor.reviewed(READ, "Reviewed by r (human), not tool-verified; the tool labeled it READ. X.",
                        SVC + "#overdue", "Invoice", List.of(), CapabilityDescriptor.KIND_READ, false,
                        new ReviewDescriptor("READ", "r", "2026-09-23", "human")),
                CapabilityDescriptor.reviewed(WRITE, "Reviewed by r (human), not tool-verified; the tool labeled it WRITE. Y.",
                        SVC + "#record", "Receipt", List.of(), CapabilityDescriptor.KIND_WRITE, false,
                        new ReviewDescriptor("WRITE", "r", "2026-09-23", "human"))));
    }

    private ToolCallbackProvider start(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("productService", ProductService.class);
            context.registerBean("svc", Svc.class);
            context.refresh();
            SpringExecutionAdapter adapter = new SpringExecutionAdapter(context, manifest());
            return new TibyanAutoConfiguration().tibyanToolProvider(manifest(), adapter, mapper, environment);
        }
    }

    private List<String> messages(Level level) {
        return events.list.stream()
                .filter(event -> event.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    @Test
    void theRegisteredLineNamesOnlyTheToolsActuallyRegistered() {
        ToolCallbackProvider provider = start(Map.of(
                ExposurePolicy.SECURITY_EXPOSE_UNVERIFIABLE, CITIES));

        assertThat(provider.getToolCallbacks()).hasSize(3);
        String registered = messages(Level.INFO).stream()
                .filter(message -> message.startsWith("Tibyaan: registered"))
                .findFirst()
                .orElseThrow();
        assertThat(registered)
                .startsWith("Tibyaan: registered 3 MCP tool(s): ")
                .contains(VERIFIED, CITIES, READ)
                .doesNotContain(TENANTS, WRITE);
    }

    @Test
    void everyExcludedToolGetsItsOwnLineWithItsReasonToken() {
        start(Map.of(ExposurePolicy.SECURITY_EXPOSE_UNVERIFIABLE, CITIES));

        assertThat(messages(Level.INFO))
                .contains("Tibyaan: excluded MCP tool '" + TENANTS + "' (" + ToolRegistrar.SECURITY_UNVERIFIABLE_NOT_REVIEWED + ")")
                .contains("Tibyaan: excluded MCP tool '" + WRITE + "' (" + ToolRegistrar.WRITE_NOT_ALLOWED + ")")
                .noneMatch(message -> message.contains("excluded MCP tool '" + CITIES + "'"))
                .noneMatch(message -> message.contains("excluded MCP tool '" + VERIFIED + "'"));
        assertThat(messages(Level.WARN)).isEmpty();
    }

    @Test
    void theGlobalSwitchWarnNamesExactlyTheUnverifiableToolsNobodyListed() {
        start(Map.of(
                ExposurePolicy.ATTESTED_EXPOSE_SECURITY_UNVERIFIABLE, "true",
                ExposurePolicy.SECURITY_EXPOSE_UNVERIFIABLE, CITIES));

        List<String> warnings = messages(Level.WARN);
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0))
                .startsWith("Tibyaan: 1 security-unverifiable tool(s) registered ONLY because "
                        + ExposurePolicy.ATTESTED_EXPOSE_SECURITY_UNVERIFIABLE + "=true")
                .contains(ExposurePolicy.SECURITY_EXPOSE_UNVERIFIABLE)
                .contains(TENANTS)
                .doesNotContain(CITIES);
    }

    @Test
    void withNothingOpenedByTheGlobalSwitchThereIsNoWarn() {
        start(Map.of());

        assertThat(messages(Level.WARN)).isEmpty();
        assertThat(messages(Level.INFO).get(0)).startsWith("Tibyaan: registered 2 MCP tool(s): ")
                .contains(VERIFIED, READ);
    }

    @Test
    void aBadListEntryStopsTheHostBeforeAnythingRegisters() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> start(Map.of(
                ExposurePolicy.WRITES_ALLOW, "reviewed_*")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ExposurePolicy.WRITES_ALLOW)
                .hasMessageContaining("'reviewed_*'");
        assertThat(messages(Level.INFO)).noneMatch(message -> message.startsWith("Tibyaan: registered"));
    }
}
