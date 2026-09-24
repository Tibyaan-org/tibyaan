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
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase F9, hosted bindings (Volume 7 section 14, fixtures HB-1 to HB-5). Only a binding this application
 * hosts registers; an unhosted or ambiguous one is named on one startup line with its reason; a name in an
 * exposure list that the service does not host stays a valid entry.
 */
class HostedBindingsTest {

    public static class Crm {
        public static class LookupService {
            public String getAll() {
                return "crm";
            }
        }
    }

    public static class Stock {
        public static class LookupService {
            public String getAll() {
                return "stock";
            }
        }
    }

    public static class LedgerService {
        public Long record(String note) {
            return 1L;
        }
    }

    private static final String CRM = Crm.LookupService.class.getCanonicalName();
    private static final String STOCK = Stock.LookupService.class.getCanonicalName();
    private static final String LEDGER = LedgerService.class.getCanonicalName();

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
                new CapabilityDescriptor("getCrm", "Read crm.", CRM + "#getAll", "STRING", List.of()),
                new CapabilityDescriptor("getStock", "Read stock.", STOCK + "#getAll", "STRING", List.of()),
                CapabilityDescriptor.reviewed("reviewed_record",
                        "Reviewed by r (human), not tool-verified; the tool labeled it WRITE. Y.",
                        LEDGER + "#record", "LONG", List.of(new InputDescriptor("note", "STRING")),
                        CapabilityDescriptor.KIND_WRITE, false, new ReviewDescriptor("WRITE", "r", "2026-09-24", "human"))));
    }

    private ToolRegistrar.Registration register(Consumer<AnnotationConfigApplicationContext> beans, ExposurePolicy policy) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            beans.accept(context);
            context.refresh();
            SpringExecutionAdapter adapter = new SpringExecutionAdapter(context, manifest());
            ToolRegistrar.Registration registration = new ToolRegistrar(mapper, policy).registration(manifest(), adapter);
            TibyanAutoConfiguration.logStartup(registration);
            return registration;
        }
    }

    private List<String> infoLines() {
        return events.list.stream().filter(e -> e.getLevel() == Level.INFO).map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void hb1_aHostedBindingRegistersAndNoHostingLineIsLogged() {
        ToolRegistrar.Registration registration = register(context -> {
            context.registerBean("crmLookup", Crm.LookupService.class);
            context.registerBean("stockLookup", Stock.LookupService.class);
        }, ExposurePolicy.defaults());

        assertThat(registration.registeredNames()).containsExactly("getCrm", "getStock");
        assertThat(registration.unhosted()).isEmpty();
        assertThat(infoLines()).noneMatch(line -> line.contains("not hosted"));
    }

    @Test
    void hb2_anUnhostedBindingIsNotRegisteredAndIsNamedWithItsReason() {
        ToolRegistrar.Registration registration = register(context ->
                context.registerBean("crmLookup", Crm.LookupService.class), ExposurePolicy.defaults());

        assertThat(registration.registeredNames()).containsExactly("getCrm");
        assertThat(registration.unhosted()).containsExactly(Map.entry("getStock", "no bean of class " + STOCK));
        assertThat(infoLines()).anyMatch(line -> line.startsWith("Tibyaan: 1 tool(s) not hosted by this application and not registered: ")
                && line.contains("getStock (no bean of class " + STOCK + ")"));
        assertThat(infoLines()).anyMatch(line -> line.startsWith("Tibyaan: registered 1 MCP tool(s): [getCrm]"));
    }

    @Test
    void hb3_twoBeansOfTheClassIsNotHostedAndBothBeanNamesAreNamed() {
        ToolRegistrar.Registration registration = register(context -> {
            context.registerBean("crmLookup", Crm.LookupService.class);
            context.registerBean("primaryStock", Stock.LookupService.class);
            context.registerBean("secondaryStock", Stock.LookupService.class);
        }, ExposurePolicy.defaults());

        assertThat(registration.registeredNames()).containsExactly("getCrm");
        assertThat(registration.unhosted().get("getStock"))
                .isEqualTo("2 beans of class " + STOCK + ": primaryStock, secondaryStock");
    }

    @Test
    void hb4_aCglibProxiedBeanCountsAsHostingItsUserClass() {
        ToolRegistrar.Registration registration = register(context -> {
            context.registerBean("crmLookup", Crm.LookupService.class);
            context.registerBean("stockLookup", Stock.LookupService.class);
            context.registerBean("advisor", BeanPostProcessor.class, () -> new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof Stock.LookupService)) {
                        return bean;
                    }
                    ProxyFactory factory = new ProxyFactory(bean);
                    factory.setProxyTargetClass(true);
                    return factory.getProxy();
                }
            });
        }, ExposurePolicy.defaults());

        assertThat(registration.registeredNames()).containsExactly("getCrm", "getStock");
        assertThat(registration.unhosted()).isEmpty();
    }

    @Test
    void hb5_anUnhostedNameInTheWriteAllowListDoesNotFailStartupAndIsReported() {
        ExposurePolicy policy = new ExposurePolicy(true, false, false, true, List.of("reviewed_record"), List.of());
        ToolRegistrar.Registration registration = register(context -> {
            context.registerBean("crmLookup", Crm.LookupService.class);
            context.registerBean("stockLookup", Stock.LookupService.class);
        }, policy);

        assertThat(registration.registeredNames()).containsExactly("getCrm", "getStock");
        assertThat(registration.excluded()).isEmpty();
        assertThat(registration.unhosted()).containsExactly(Map.entry("reviewed_record", "no bean of class " + LEDGER));
    }

    @Test
    void aPlainExecutionAdapterIsNotAskedAndEverythingThePolicyAllowsRegisters() {
        ToolRegistrar.Registration registration = new ToolRegistrar(mapper, ExposurePolicy.defaults())
                .registration(manifest(), (ref, arguments) -> null);

        assertThat(registration.registeredNames()).containsExactly("getCrm", "getStock");
        assertThat(registration.unhosted()).isEmpty();
    }
}
