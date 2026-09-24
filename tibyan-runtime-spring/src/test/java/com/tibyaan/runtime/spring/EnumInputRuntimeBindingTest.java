package com.tibyaan.runtime.spring;

import com.fasterxml.jackson.annotation.JsonValue;
import com.tibyaan.plugin.CapabilityRef;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase Enum Inputs, step EI-R: runtime argument binding and closed-set enforcement (Volume 7 section 9).
 *
 * <p>The defect these fixtures close: the adapter used to resolve its target method by RECONSTRUCTING
 * parameter types from the IR, so a STRING input yielded {@code String.class} against a real enum or
 * {@code LocalDate} parameter and the capability was UNINVOKABLE. It now resolves by name and arity and
 * converts into the method's DECLARED types.
 *
 * <p>THE INVARIANT UNDER TEST IS MIS-CONSTRAINED: the runtime accepts exactly the values the application
 * accepts on the wire, and when it refuses it names those values. It never invents a value set, and it
 * never accepts a value outside the one it would advertise.
 */
class EnumInputRuntimeBindingTest {

    // A BARE enum, shape (a): the wire form is the constant name. This is the erp idiom.
    public enum TicketStatus {
        OPEN, ASSIGNED, DOING, DONE
    }

    // A VALUE-MAPPED enum, shape (b): a @JsonValue accessor makes the wire form the literal, NOT the
    // constant name. This is the pms generated idiom, where the application accepts "maintenance" and
    // would reject "MAINTENANCE".
    public enum CostCategory {
        MAINTENANCE("maintenance"),
        UTILITIES("utilities"),
        OTHER("other");

        private final String value;

        CostCategory(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }
    }

    /**
     * A CHARACTER-EXACT replica of the real pms declaration
     * {@code com/pms/billing/model/generated/CostCategory.java}, all eight constants in declaration order,
     * so the rule is verified against the real benchmark shape and not only against a convenient sample.
     */
    public enum RealCostCategory {
        MAINTENANCE("maintenance"),
        UTILITIES("utilities"),
        INSURANCE("insurance"),
        TAXES("taxes"),
        SUPPLIES("supplies"),
        RENOVATION("renovation"),
        SECURITY("security"),
        OTHER("other");

        private final String value;

        RealCostCategory(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }
    }

    public record Row(String label) {
    }

    /** Stands in for a filtered read whose parameters are the shapes the old adapter could not bind. */
    public static class ReportService {
        public Row byStatus(TicketStatus status) {
            return new Row("status=" + status.name());
        }

        public Row byCategory(CostCategory category) {
            return new Row("category=" + category.getValue());
        }

        public Row byDate(LocalDate from) {
            return new Row("from=" + from);
        }

        public Row byBuilding(Long buildingId, TicketStatus status, Integer pageSize) {
            return new Row(buildingId + "/" + status.name() + "/" + pageSize);
        }
    }

    /** Two genuine overloads of the same name and arity: the adapter must refuse, never guess. */
    public static class AmbiguousService {
        public Row lookup(String key) {
            return new Row("string");
        }

        public Row lookup(Long key) {
            return new Row("long");
        }
    }

    /** A generic interface implementation, which javac gives a same-name same-arity BRIDGE method. */
    public interface Finder<T> {
        Row find(T key);
    }

    public static class StringFinder implements Finder<String> {
        @Override
        public Row find(String key) {
            return new Row("found=" + key);
        }
    }

    // ---------- the enum half of the defect ----------

    @Test
    void eiR1_aBareEnumParameterIsBoundByConstantNameAndInvoked() {
        assertThat(invoke("byStatus", input("status", "STRING"), Map.of("status", "DOING")))
                .isEqualTo(new Row("status=DOING"));
    }

    @Test
    void eiR2_aValueMappedEnumParameterIsBoundByItsWireLiteralNotItsConstantName() {
        // The application accepts "maintenance". This is the wire-value rule enforced at the runtime.
        assertThat(invoke("byCategory", input("category", "STRING"), Map.of("category", "maintenance")))
                .isEqualTo(new Row("category=maintenance"));
    }

    @Test
    void eiR3_aValueMappedEnumRefusesTheConstantNameBecauseTheApplicationWouldReject() {
        // MIS-CONSTRAINED guard: accepting "MAINTENANCE" here would mean the runtime accepts a value
        // outside the set the provider advertises, which is the failure this phase forbids.
        assertThatThrownBy(() -> invoke("byCategory", input("category", "STRING"),
                Map.of("category", "MAINTENANCE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("input 'category' has value 'MAINTENANCE'")
                .hasMessageContaining("maintenance, utilities, other");
    }

    @Test
    void eiR4_anIllegalEnumValueIsRefusedWithTheLegalValuesNamed() {
        // This is the enforcement a schema closed set promises (Volume 7 section 9 item 4).
        assertThatThrownBy(() -> invoke("byStatus", input("status", "STRING"),
                Map.of("status", "CLOSED")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not one of the values 'TicketStatus' accepts")
                .hasMessageContaining("OPEN, ASSIGNED, DOING, DONE");
    }

    // ---------- the non-enum half of the same defect ----------

    @Test
    void eiR5_aLocalDateParameterIsBoundFromItsIsoText() {
        assertThat(invoke("byDate", input("from", "TIMESTAMP"), Map.of("from", "2026-08-05")))
                .isEqualTo(new Row("from=2026-08-05"));
    }

    @Test
    void eiR6_aMixedArityReadBindsEveryParameterToItsDeclaredType() {
        List<InputDescriptor> inputs = List.of(
                new InputDescriptor("buildingId", "LONG"),
                new InputDescriptor("status", "STRING"),
                new InputDescriptor("pageSize", "INTEGER"));
        assertThat(invoke("byBuilding", inputs,
                Map.of("buildingId", 7, "status", "OPEN", "pageSize", 25)))
                .isEqualTo(new Row("7/OPEN/25"));
    }

    // ---------- the never-guess guards on method resolution ----------

    @Test
    void eiR7_twoGenuineOverloadsOfTheSameArityRefuseLoudly() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("ambiguousService", AmbiguousService.class);
            context.refresh();
            SpringExecutionAdapter adapter = new SpringExecutionAdapter(context, manifest(
                    "com.tibyaan.runtime.spring.EnumInputRuntimeBindingTest.AmbiguousService#lookup", List.of(new InputDescriptor("key", "STRING"))));

            assertThatThrownBy(() -> adapter.invoke(new CapabilityRef("cap"), Map.of("key", "x")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("is ambiguous")
                    .hasMessageContaining("2 overloads of 'lookup'")
                    .hasMessageContaining("never guesses");
        }
    }

    @Test
    void eiR8_aBridgeMethodDoesNotCreateAFalseAmbiguity() {
        // StringFinder has both find(String) and a synthetic bridge find(Object). Skipping bridges is what
        // keeps a perfectly ordinary generic implementation from refusing as ambiguous.
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("stringFinder", StringFinder.class);
            context.refresh();
            SpringExecutionAdapter adapter = new SpringExecutionAdapter(context, manifest(
                    "com.tibyaan.runtime.spring.EnumInputRuntimeBindingTest.StringFinder#find", List.of(new InputDescriptor("key", "STRING"))));

            assertThat(adapter.invoke(new CapabilityRef("cap"), Map.of("key", "abc")))
                    .isEqualTo(new Row("found=abc"));
        }
    }

    @Test
    void eiR9_anUnknownMethodNameRefusesNamingTheArity() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("reportService", ReportService.class);
            context.refresh();
            SpringExecutionAdapter adapter = new SpringExecutionAdapter(context, manifest(
                    "com.tibyaan.runtime.spring.EnumInputRuntimeBindingTest.ReportService#nope", List.of(new InputDescriptor("x", "STRING"))));

            assertThatThrownBy(() -> adapter.invoke(new CapabilityRef("cap"), Map.of("x", "y")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no method 'nope' taking 1 argument(s)");
        }
    }

    // ---------- the MIS-CONSTRAINED guard on the value set itself ----------

    @Test
    void eiR10_theLegalValueSetIsReadFromTheEnumAndNeverSynthesized() {
        // Character-exact against the declarations above, in declaration order, for both shapes.
        assertThat(IrTypes.legalValues(TicketStatus.class))
                .containsExactly("OPEN", "ASSIGNED", "DOING", "DONE");
        assertThat(IrTypes.legalValues(CostCategory.class))
                .containsExactly("maintenance", "utilities", "other");
        // A non-enum carries no set at all: the runtime never invents one.
        assertThat(IrTypes.legalValues(String.class)).isEmpty();
        assertThat(IrTypes.legalValues(Long.class)).isEmpty();
    }

    @Test
    void eiR11_theRealBenchmarkEnumYieldsItsWireLiteralsCharacterExact() {
        // Verified against com/pms/billing/model/generated/CostCategory.java: all eight constants, in
        // declaration order, as the LITERALS the application accepts, never the constant names.
        assertThat(IrTypes.legalValues(RealCostCategory.class))
                .containsExactly("maintenance", "utilities", "insurance", "taxes",
                        "supplies", "renovation", "security", "other");
        assertThat(EnumWireValues.match(RealCostCategory.class, "renovation"))
                .contains(RealCostCategory.RENOVATION);
        assertThat(EnumWireValues.match(RealCostCategory.class, "RENOVATION")).isEmpty();
    }

    // ---------- harness ----------

    private static List<InputDescriptor> input(String name, String type) {
        return List.of(new InputDescriptor(name, type));
    }

    private static CapabilitiesManifest manifest(String binding, List<InputDescriptor> inputs) {
        return new CapabilitiesManifest("test-app", "0.1", List.of(
                new CapabilityDescriptor("cap", "A test capability.", binding, "ENTITY_REF(Row)", inputs)));
    }

    private static Object invoke(String method, List<InputDescriptor> inputs, Map<String, Object> args) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("reportService", ReportService.class);
            context.refresh();
            SpringExecutionAdapter adapter =
                    new SpringExecutionAdapter(context, manifest("com.tibyaan.runtime.spring.EnumInputRuntimeBindingTest.ReportService#" + method, inputs));
            return adapter.invoke(new CapabilityRef("cap"), args);
        }
    }
}
