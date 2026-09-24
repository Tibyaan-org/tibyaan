package com.tibyaan.runtime.spring;

import com.tibyaan.plugin.CapabilityRef;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase DL-2b at the runtime (Volume 7 section 13 item 2; the runtime halves of SI-3 and SI-11). A structured
 * input is walked against its shape, then bound to the declared class; an unknown property, an illegal enum
 * value and a non-object refuse by name.
 */
class ShapeInputRuntimeTest {

    public enum Priority { LOW, HIGH }

    public static class Address {
        private String street;
        private String city;

        public String getStreet() { return street; }
        public void setStreet(String street) { this.street = street; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
    }

    public static class OrderDraft {
        private String customer;
        private Integer quantity;
        private Priority priority;
        private Address address;
        private List<String> tags;
        private Object extra;

        public String getCustomer() { return customer; }
        public void setCustomer(String customer) { this.customer = customer; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public Priority getPriority() { return priority; }
        public void setPriority(Priority priority) { this.priority = priority; }
        public Address getAddress() { return address; }
        public void setAddress(Address address) { this.address = address; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
        public Object getExtra() { return extra; }
        public void setExtra(Object extra) { this.extra = extra; }
    }

    public static class OrderService {
        public String create(OrderDraft draft) {
            return draft.customer + "/" + draft.quantity + "/" + draft.priority + "/"
                    + (draft.address == null ? "-" : draft.address.city) + "/" + draft.tags + "/" + draft.extra;
        }

        public String bulk(List<OrderDraft> drafts) {
            return "n=" + drafts.size() + ":" + drafts.get(0).customer;
        }
    }

    private static final String SERVICE = OrderService.class.getCanonicalName();

    private static CapabilitiesManifest manifest(String method, boolean multiple) {
        ShapeDescriptor address = new ShapeDescriptor("x.Address", "Address", List.of(
                new ShapeDescriptor.ShapeFieldDescriptor("street", "STRING", false, List.of()),
                new ShapeDescriptor.ShapeFieldDescriptor("city", "STRING", false, List.of())), List.of());
        ShapeDescriptor orderDraft = new ShapeDescriptor("x.OrderDraft", "OrderDraft", List.of(
                new ShapeDescriptor.ShapeFieldDescriptor("customer", "STRING", false, List.of()),
                new ShapeDescriptor.ShapeFieldDescriptor("quantity", "INTEGER", false, List.of()),
                new ShapeDescriptor.ShapeFieldDescriptor("priority", "STRING", false, List.of("LOW", "HIGH")),
                new ShapeDescriptor.ShapeFieldDescriptor("address", "SHAPE_REF(Address)", false, List.of()),
                new ShapeDescriptor.ShapeFieldDescriptor("tags", "STRING", true, List.of())),
                List.of("extra: Object"));
        return new CapabilitiesManifest("app", "0.1", List.of(new CapabilityDescriptor("cap", "A capability.",
                SERVICE + "#" + method, "STRING", List.of(new InputDescriptor("draft", "SHAPE_REF(OrderDraft)", multiple)))),
                List.of(orderDraft, address));
    }

    private static Object invoke(String method, boolean multiple, Object value) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("orderService", OrderService.class);
            context.refresh();
            SpringExecutionAdapter adapter = new SpringExecutionAdapter(context, manifest(method, multiple));
            Map<String, Object> args = new HashMap<>();
            args.put("draft", value);
            return adapter.invoke(new CapabilityRef("cap"), args);
        }
    }

    private static Map<String, Object> draft() {
        Map<String, Object> draft = new HashMap<>();
        draft.put("customer", "acme");
        draft.put("quantity", 3);
        draft.put("priority", "HIGH");
        draft.put("address", Map.of("street", "1 Main", "city", "Cairo"));
        draft.put("tags", List.of("a", "b"));
        return draft;
    }

    @Test
    void aJsonObjectIsWalkedAgainstItsShapeAndBoundToTheDeclaredClass() {
        assertThat(invoke("create", false, draft())).isEqualTo("acme/3/HIGH/Cairo/[a, b]/null");
    }

    @Test
    void si11_anUnknownPropertyIsRefusedNamingTheFieldsAndTheSkippedOnes() {
        Map<String, Object> draft = draft();
        draft.put("extra", "x");
        assertThatThrownBy(() -> invoke("create", false, draft))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'extra', which is not a field of OrderDraft")
                .hasMessageContaining("not modeled: extra: Object")
                .hasMessageContaining("never drops");
    }

    @Test
    void si3_anIllegalEnumValueInAShapeFieldIsRefusedNamingTheLegalValues() {
        Map<String, Object> draft = draft();
        draft.put("priority", "URGENT");
        assertThatThrownBy(() -> invoke("create", false, draft))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("draft.priority")
                .hasMessageContaining("URGENT")
                .hasMessageContaining("LOW, HIGH");
    }

    @Test
    void aNestedUnknownPropertyIsRefusedWithItsPath() {
        Map<String, Object> draft = draft();
        draft.put("address", Map.of("city", "Cairo", "zip", "123"));
        assertThatThrownBy(() -> invoke("create", false, draft))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("draft.address")
                .hasMessageContaining("'zip', which is not a field of Address");
    }

    @Test
    void aNonObjectValueForAShapeInputRefuses() {
        assertThatThrownBy(() -> invoke("create", false, "acme"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be a JSON object");
    }

    @Test
    void aListOfShapesBindsEachElement() {
        assertThat(invoke("bulk", true, List.of(draft(), Map.of("customer", "zeta")))).isEqualTo("n=2:acme");
    }

    @Test
    void theToolSchemaRendersTheShapeAsANestedObjectWithNoRequiredList() {
        CapabilitiesManifest manifest = manifest("create", false);
        CapabilityToolCallback callback = new CapabilityToolCallback(manifest.tools().get(0), null,
                new com.fasterxml.jackson.databind.ObjectMapper(), manifest.shapesByName());
        String schema = callback.getToolDefinition().inputSchema();
        assertThat(schema).contains("\"draft\":{\"type\":\"object\"")
                .contains("\"priority\":{\"type\":\"string\",\"enum\":[\"LOW\",\"HIGH\"]}")
                .contains("\"address\":{\"type\":\"object\"")
                .contains("\"tags\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}")
                .contains("Not modeled: extra: Object")
                .contains("\"additionalProperties\":false");
        assertThat(schema.indexOf("\"required\":[\"draft\"]")).isGreaterThan(0);
        assertThat(schema).doesNotContain("\"required\":[\"customer\"");
    }
}
