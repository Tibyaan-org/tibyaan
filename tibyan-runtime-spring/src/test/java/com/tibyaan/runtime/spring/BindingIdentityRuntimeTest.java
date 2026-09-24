package com.tibyaan.runtime.spring;

import com.tibyaan.plugin.CapabilityRef;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase Binding Identity, the runtime fixtures (Volume 7 section 12.6, BI-5 to BI-9). The adapter resolves
 * the binding's class part to exactly one bean by the bean's USER class canonical name, and never by bean
 * name or simple class name: on a real host a reviewed target resolved by simple name to a class the
 * reviewer never read (docs/experiments/as-backend-trial.md section 10.7, F7).
 */
class BindingIdentityRuntimeTest {

    /** The crm context's LookupService: same simple name as the stock one, a different class. */
    public static class Crm {
        public static class LookupService {
            public String getAll() {
                return "crm";
            }
        }
    }

    /** The stock context's LookupService, the one the reviewer read. */
    public static class Stock {
        public static class LookupService {
            public String getAll() {
                return "stock";
            }
        }
    }

    private static final String STOCK = Stock.LookupService.class.getCanonicalName();
    private static final String CRM = Crm.LookupService.class.getCanonicalName();

    private static CapabilitiesManifest manifest(String binding) {
        return new CapabilitiesManifest("app", "0.1", List.of(
                new CapabilityDescriptor("cap", "A capability.", binding, "STRING", List.of())));
    }

    @Test
    void bi5_theF7Case_twoBeansSharingASimpleNameResolveByTheirQualifiedClass() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            // The crm bean carries the default bean name the old adapter tried first, lookupService.
            context.registerBean("lookupService", Crm.LookupService.class);
            context.registerBean("stockLookup", Stock.LookupService.class);
            context.refresh();

            SpringExecutionAdapter adapter = new SpringExecutionAdapter(context, manifest(STOCK + "#getAll"));
            assertThat(adapter.invoke(new CapabilityRef("cap"), Map.of())).isEqualTo("stock");

            SpringExecutionAdapter crm = new SpringExecutionAdapter(context, manifest(CRM + "#getAll"));
            assertThat(crm.invoke(new CapabilityRef("cap"), Map.of())).isEqualTo("crm");
        }
    }

    @Test
    void bi6_noBeanOfTheClassRefusesNamingTheClassAndTheCapability() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("lookupService", Crm.LookupService.class);
            context.refresh();

            SpringExecutionAdapter adapter = new SpringExecutionAdapter(context, manifest(STOCK + "#getAll"));
            assertThatThrownBy(() -> adapter.invoke(new CapabilityRef("cap"), Map.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no bean of class '" + STOCK + "'")
                    .hasMessageContaining("capability 'cap'");
        }
    }

    @Test
    void bi7_twoBeansOfOneClassRefuseAsAmbiguousNamingBothBeans() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("primaryLookup", Stock.LookupService.class);
            context.registerBean("secondaryLookup", Stock.LookupService.class);
            context.refresh();

            SpringExecutionAdapter adapter = new SpringExecutionAdapter(context, manifest(STOCK + "#getAll"));
            assertThatThrownBy(() -> adapter.invoke(new CapabilityRef("cap"), Map.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("is ambiguous")
                    .hasMessageContaining("primaryLookup")
                    .hasMessageContaining("secondaryLookup")
                    .hasMessageContaining("never guesses");
        }
    }

    @Test
    void bi8_anUnqualifiedBindingIsRefusedByTheAdapterToo() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("lookupService", Stock.LookupService.class);
            context.refresh();

            SpringExecutionAdapter adapter = new SpringExecutionAdapter(context, manifest("LookupService#getAll"));
            assertThatThrownBy(() -> adapter.invoke(new CapabilityRef("cap"), Map.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("is not qualified")
                    .hasMessageContaining("regenerated");
        }
    }

    @Test
    void bi9_aCglibProxiedBeanResolvesByItsUserClass() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
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
            context.refresh();
            assertThat(context.getBean("stockLookup").getClass().getName())
                    .as("the bean is the CGLIB subclass, as under Spring Boot's default proxying")
                    .contains("$$SpringCGLIB$$");

            SpringExecutionAdapter adapter = new SpringExecutionAdapter(context, manifest(STOCK + "#getAll"));
            assertThat(adapter.invoke(new CapabilityRef("cap"), Map.of())).isEqualTo("stock");
        }
    }
}
