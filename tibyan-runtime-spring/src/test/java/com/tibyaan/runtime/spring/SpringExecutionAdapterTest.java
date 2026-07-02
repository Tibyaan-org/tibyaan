package com.tibyaan.runtime.spring;

import com.tibyaan.plugin.CapabilityRef;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test for the adapter's binding resolution: given the binding {@code ProductService#findById}
 * and an argument, it resolves the real bean and invokes the real method reflectively. This is the
 * spike's binding mechanism, now driven by the manifest rather than hard-coded. The adapter treats
 * the binding as an opaque {@code <Bean>#<method>} string, so it is indifferent to whether the
 * provider discovered it or synthesized it (Volume 5 section 4.1).
 */
class SpringExecutionAdapterTest {

    /** Stands in for the legacy service. The adapter neither imports nor names it at compile time. */
    public static class ProductService {
        public Product findById(Long id) {
            return new Product(id, "Wireless Mouse");
        }

        public List<Product> findAll() {
            return List.of(new Product(1L, "Wireless Mouse"), new Product(2L, "Keyboard"));
        }
    }

    public record Product(Long id, String name) {
    }

    private static CapabilitiesManifest getProductManifest() {
        return new CapabilitiesManifest("legacy-spring-app", "0.1", List.of(
                new CapabilityDescriptor(
                        "getProduct", "Read Product by id.",
                        "ProductService#findById", "ENTITY_REF(Product)",
                        List.of(new InputDescriptor("id", "LONG")))));
    }

    @Test
    void resolvesTheBindingToARealBeanAndInvokesTheRealMethod() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("productService", ProductService.class);
            context.refresh();

            SpringExecutionAdapter adapter = new SpringExecutionAdapter(context, getProductManifest());

            // The MCP client sends id as a JSON number, which Jackson parses to Integer; the adapter
            // must coerce it to the Long the method expects.
            Object result = adapter.invoke(new CapabilityRef("getProduct"), Map.of("id", 5));

            assertThat(result).isInstanceOf(Product.class);
            assertThat((Product) result).isEqualTo(new Product(5L, "Wireless Mouse"));
        }
    }

    @Test
    void invokesANoArgumentListReadAndReturnsTheCollection() {
        // A list-all capability has no inputs (Volume 5 section 4.10.3), so the adapter invokes the
        // no-argument method and returns the collection result end to end (Volume 6 section 3.3). The
        // additive outputMultiple manifest field is tolerated by the loader and not acted on here.
        CapabilitiesManifest manifest = new CapabilitiesManifest("legacy-spring-app", "0.1", List.of(
                new CapabilityDescriptor("listProduct", "List all Product.",
                        "ProductService#findAll", "ENTITY_REF(Product)", List.of())));
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("productService", ProductService.class);
            context.refresh();

            SpringExecutionAdapter adapter = new SpringExecutionAdapter(context, manifest);
            Object result = adapter.invoke(new CapabilityRef("listProduct"), Map.of());

            assertThat(result).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<Product> products = (List<Product>) result;
            assertThat(products)
                    .containsExactly(new Product(1L, "Wireless Mouse"), new Product(2L, "Keyboard"));
        }
    }

    @Test
    void rejectsAnUnknownCapability() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            SpringExecutionAdapter adapter = new SpringExecutionAdapter(context, getProductManifest());

            assertThatThrownBy(() -> adapter.invoke(new CapabilityRef("getGhost"), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknown capability");
        }
    }
}
