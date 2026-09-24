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
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ClassUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase SEC-1, rule S5 (Volume 7 section 15.4): the caller-gate startup line, and fixture SC-7 of section 15.6,
 * a host with no Spring Security starting with {@code requireCaller=false} and the line saying so.
 */
class CallerGateStartupLineTest {

    public static class Svc {
        public String get() {
            return "thing";
        }

        public String secured() {
            return "secured";
        }

        public String open() {
            return "open";
        }

        public String record() {
            return "record";
        }
    }

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
                new CapabilityDescriptor("getThing", "Read Thing.", SVC + "#get", "Thing", List.of()),
                new CapabilityDescriptor("getSecured", "Read Secured.", SVC + "#secured", "Secured", List.of())
                        .withRequiredAuthorities(List.of("480012")),
                CapabilityDescriptor.reviewed("reviewed_open",
                        "Reviewed by r (human), not tool-verified; the tool labeled it READ. X.", SVC + "#open", "Thing",
                        List.of(), CapabilityDescriptor.KIND_READ, true, new ReviewDescriptor("READ", "r", "2026-09-24", "human")),
                CapabilityDescriptor.reviewed("reviewed_record",
                        "Reviewed by r (human), not tool-verified; the tool labeled it WRITE. X.", SVC + "#record", "Receipt",
                        List.of(), CapabilityDescriptor.KIND_WRITE, false, new ReviewDescriptor("WRITE", "r", "2026-09-24", "human"))));
    }

    private ToolRegistrar.Registration registration(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("svc", Svc.class);
            context.refresh();
            SpringExecutionAdapter adapter = new SpringExecutionAdapter(context, manifest());
            return new ToolRegistrar(mapper, ExposurePolicy.bind(environment)).registration(manifest(), adapter);
        }
    }

    private List<String> messages(Level level) {
        return events.list.stream()
                .filter(event -> event.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    @Test
    void s5_theCallerGateLineCountsContractsAndUnverifiableToolsAmongTheRegistered() {
        ToolRegistrar.Registration registration = registration(Map.of(
                ExposurePolicy.SECURITY_EXPOSE_UNVERIFIABLE, "reviewed_open",
                ExposurePolicy.WRITES_ALLOW, "reviewed_record"));
        assertThat(registration.contractCarrying()).containsExactly("getSecured");
        assertThat(registration.unverifiableRegistered()).containsExactly("reviewed_open");

        TibyanAutoConfiguration.logStartup(registration, ExposurePolicy.defaults(), true);

        assertThat(messages(Level.INFO)).anySatisfy(message -> assertThat(message)
                .startsWith("Tibyaan: caller gate: " + ExposurePolicy.SECURITY_REQUIRE_CALLER + "=true, Spring Security present; ")
                .contains("1 registered tool(s) carry a compiled authority contract")
                .contains("[getSecured]")
                .contains("1 registered security-unverifiable tool(s) open only by list or switch: [reviewed_open]"));
        assertThat(messages(Level.WARN)).noneMatch(message -> message.contains("no caller can be captured"));
    }

    @Test
    void anExcludedContractToolIsNotCounted() {
        // Nothing listed and no write allowed: the unverifiable read and the write are excluded and not counted.
        ToolRegistrar.Registration registration = registration(Map.of());
        assertThat(registration.contractCarrying()).containsExactly("getSecured");
        assertThat(registration.unverifiableRegistered()).isEmpty();
        assertThat(registration.excluded()).containsKeys("reviewed_open", "reviewed_record");
    }

    @Test
    void sc7_aHostWithNoSpringSecurityStartsWithRequireCallerFalseAndTheLineSaysSo() {
        // The class-path check is the runtime's own: a loader that hides Spring Security reports it absent.
        FilteredClassLoader hidden = new FilteredClassLoader(SecurityContextHolder.class);
        assertThat(ClassUtils.isPresent("org.springframework.security.core.context.SecurityContextHolder", hidden)).isFalse();

        ToolRegistrar.Registration registration = registration(Map.of(ExposurePolicy.SECURITY_REQUIRE_CALLER, "false"));
        ExposurePolicy policy = new ExposurePolicy(true, false, false, true, List.of(), List.of(), false);
        TibyanAutoConfiguration.logStartup(registration, policy, false);

        assertThat(messages(Level.INFO)).anySatisfy(message -> assertThat(message)
                .startsWith("Tibyaan: caller gate: " + ExposurePolicy.SECURITY_REQUIRE_CALLER + "=false, Spring Security absent; "));
        assertThat(messages(Level.INFO)).anySatisfy(message -> assertThat(message)
                .startsWith("Tibyaan: " + ExposurePolicy.SECURITY_REQUIRE_CALLER + "=false: an anonymous caller may invoke"));
        assertThat(messages(Level.WARN)).noneMatch(message -> message.contains("no caller can be captured"));
    }

    @Test
    void requireCallerTrueWithoutSpringSecurityWarnsThatEveryWriteAndContractToolWillRefuse() {
        ToolRegistrar.Registration registration = registration(Map.of());
        TibyanAutoConfiguration.logStartup(registration, ExposurePolicy.defaults(), false);

        assertThat(messages(Level.WARN)).anySatisfy(message -> assertThat(message)
                .startsWith("Tibyaan: " + ExposurePolicy.SECURITY_REQUIRE_CALLER + "=true but Spring Security is not on the class path")
                .contains("every reviewed WRITE or ACTION and every contract-carrying tool will refuse"));
    }

    @Test
    void thePropertyBindsInBothSpellingsAndDefaultsTrue() {
        StandardEnvironment environment = new StandardEnvironment();
        assertThat(ExposurePolicy.bind(environment).requireCaller()).isTrue();

        environment.getPropertySources().addFirst(new MapPropertySource("camel",
                Map.of(ExposurePolicy.SECURITY_REQUIRE_CALLER, "false")));
        assertThat(ExposurePolicy.bind(environment).requireCaller()).isFalse();

        StandardEnvironment kebab = new StandardEnvironment();
        kebab.getPropertySources().addFirst(new MapPropertySource("kebab",
                Map.of("tibyan.security.require-caller", "false")));
        assertThat(ExposurePolicy.bind(kebab).requireCaller()).isFalse();
    }
}
