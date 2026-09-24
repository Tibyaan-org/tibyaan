package com.tibyaan.runtime.spring;

import com.tibyaan.plugin.CapabilityRef;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase DL-2a at the runtime (Volume 7 section 13 item 1; the runtime halves of fixtures CI-1, CI-3 and CI-7). A
 * multiple input arrives as a JSON array and is built into the declared collection, each element coerced by
 * the EI-R rule to the generic element type; anything the adapter cannot build refuses loudly.
 */
class CollectionInputRuntimeTest {

    public enum TicketStatus { OPEN, CLOSED }

    public static class TicketService {
        public String byIds(List<Long> ids) {
            return "ids=" + ids + ":" + (ids.isEmpty() ? "none" : ids.get(0).getClass().getSimpleName());
        }

        public String byCodes(Set<String> codes) {
            return "codes=" + codes + ":" + codes.getClass().getSimpleName();
        }

        public String byStatuses(Collection<TicketStatus> statuses) {
            return "statuses=" + statuses;
        }

        @SuppressWarnings("rawtypes")
        public String rawIds(List ids) {
            return "raw";
        }
    }

    private static final String SERVICE = TicketService.class.getCanonicalName();

    private static CapabilitiesManifest manifest(String method, String input, String type) {
        return new CapabilitiesManifest("app", "0.1", List.of(new CapabilityDescriptor("cap", "A capability.",
                SERVICE + "#" + method, "STRING", List.of(new InputDescriptor(input, type, true)))));
    }

    private static Object invoke(String method, String input, String type, Object value) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("ticketService", TicketService.class);
            context.refresh();
            SpringExecutionAdapter adapter = new SpringExecutionAdapter(context, manifest(method, input, type));
            Map<String, Object> args = new HashMap<>();
            args.put(input, value);
            return adapter.invoke(new CapabilityRef("cap"), args);
        }
    }

    @Test
    void ci1_aJsonArrayOfNumbersIsBuiltIntoAListOfTheDeclaredElementType() {
        // Jackson hands integers as Integer; the element rule coerces each to the declared Long.
        assertThat(invoke("byIds", "ids", "LONG", List.of(1, 2, 3))).isEqualTo("ids=[1, 2, 3]:Long");
    }

    @Test
    void ci2_aSetParameterKeepsInsertionOrderAndDropsDuplicates() {
        assertThat(invoke("byCodes", "codes", "STRING", List.of("b", "a", "b")))
                .isEqualTo("codes=[b, a]:LinkedHashSet");
    }

    @Test
    void ci3_anIllegalEnumElementIsRefusedNamingTheIndexAndTheLegalValues() {
        assertThatThrownBy(() -> invoke("byStatuses", "statuses", "STRING", List.of("OPEN", "PENDING")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("statuses[1]")
                .hasMessageContaining("PENDING")
                .hasMessageContaining("OPEN, CLOSED");
        assertThat(invoke("byStatuses", "statuses", "STRING", List.of("CLOSED"))).isEqualTo("statuses=[CLOSED]");
    }

    @Test
    void ci7_anEmptyArrayIsAnEmptyCollectionAndNullIsNull() {
        assertThat(invoke("byIds", "ids", "LONG", List.of())).isEqualTo("ids=[]:none");
        assertThatThrownBy(() -> invoke("byIds", "ids", "LONG", null))
                .as("the method dereferences null; the adapter passed null through, as EI-R does")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NullPointerException");
    }

    @Test
    void aNonArrayArgumentForAMultipleInputRefuses() {
        assertThatThrownBy(() -> invoke("byIds", "ids", "LONG", 7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be a JSON array");
    }

    @Test
    void aRawCollectionParameterRefusesRatherThanGuessingTheElementType() {
        assertThatThrownBy(() -> invoke("rawIds", "ids", "LONG", List.of(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names no single element class");
    }

    @Test
    void theToolSchemaRendersAMultipleInputAsAnArray() {
        CapabilityDescriptor descriptor = manifest("byIds", "ids", "LONG").tools().get(0);
        CapabilityToolCallback callback = new CapabilityToolCallback(descriptor, null,
                new com.fasterxml.jackson.databind.ObjectMapper());
        assertThat(callback.getToolDefinition().inputSchema())
                .contains("\"ids\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"}}");
    }
}
