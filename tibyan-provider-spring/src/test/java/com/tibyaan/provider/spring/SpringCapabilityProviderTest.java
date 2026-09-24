package com.tibyaan.provider.spring;

import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Field;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Type;
import com.tibyaan.core.semantic.SemanticValidator;
import com.tibyaan.core.semantic.ValidationResult;
import com.tibyaan.plugin.ProviderInput;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The P1 definition of done: scanning the real example app produces a valid IR holding exactly
 * the Product entity and a getProduct read only capability.
 *
 * <p>The {@code ProductService#findById} binding is now discovered from the real service method,
 * not synthesized by convention (Volume 5 section 4.1). For this app the discovered value happens
 * to equal what the old convention produced; {@link BindingDiscoveryTest} proves the discovery is
 * genuine by binding a service and method that the convention could never have named.
 */
class SpringCapabilityProviderTest {

    // The example app lives in a sibling module; the test reads its source, it does not link
    // against the compiled module. Tests run with the module directory as the working directory.
    private static final Path EXAMPLE_APP = Path.of("..", "examples", "legacy-spring-app");

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();
    private final SemanticValidator validator = new SemanticValidator();

    @Test
    void scanningTheExampleAppYieldsTheProductGetProductModel() {
        assertThat(Files.isDirectory(EXAMPLE_APP))
                .as("example app must exist at " + EXAMPLE_APP.toAbsolutePath())
                .isTrue();

        CapabilityModel model = provider.provide(new ProviderInput(EXAMPLE_APP));

        assertThat(model).isEqualTo(expectedModel());
    }

    @Test
    void theScannedModelPassesSemanticValidation() {
        CapabilityModel model = provider.provide(new ProviderInput(EXAMPLE_APP));

        ValidationResult result = validator.validate(model);

        assertThat(result.isValid()).as("validation errors: %s", result.errors()).isTrue();
    }

    @Test
    void scanProducesExactlyTheProductEntityAndGetProductCapability() {
        CapabilityModel model = provider.provide(new ProviderInput(EXAMPLE_APP));

        assertThat(model.entities()).extracting(Entity::name).containsExactly("Product");
        assertThat(model.capabilities()).extracting(Capability::name).containsExactly("getProduct");
        assertThat(model.relations()).isEmpty();
    }

    // The expected IR is the Volume 4 section 6 example, with the module name and binding adapted
    // to the real source: module "legacy-spring-app" (Volume 4 uses "inventory") and binding
    // "com.tibyaan.examples.legacy.ProductService#findById" (Volume 4 uses "inventory.ProductService#findById"). The entity,
    // its fields, and the read only capability are identical.
    private static CapabilityModel expectedModel() {
        Entity product = new Entity(
                "com.tibyaan.examples.legacy.Product",
                "Product",
                List.of(
                        Field.of("id", Primitive.LONG, false),
                        Field.of("name", Primitive.STRING, false),
                        Field.of("price", Primitive.DECIMAL, false)),
                List.of(),
                Metadata.empty());

        Map<String, Type> inputs = new LinkedHashMap<>();
        inputs.put("id", Primitive.LONG);

        Capability getProduct = Capability.read(
                "getProduct",
                inputs,
                Type.entityRef("Product"),
                new BindingRef("com.tibyaan.examples.legacy.ProductService#findById"));

        return new CapabilityModel(
                "legacy-spring-app",
                "0.1",
                List.of(product),
                List.of(getProduct),
                List.of(),
                Metadata.empty());
    }
}
