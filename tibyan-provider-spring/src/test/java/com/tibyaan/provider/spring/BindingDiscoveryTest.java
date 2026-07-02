package com.tibyaan.provider.spring;

import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Type;
import com.tibyaan.core.semantic.SemanticValidator;
import com.tibyaan.plugin.ProviderInput;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/**
 * Proves the capability binding is discovered from the real service method, not assumed from the
 * entity name (Volume 5 section 4.1). These fixtures live in test resources only and are never
 * compiled; the provider reads them as source, exactly as it reads a real app.
 */
class BindingDiscoveryTest {

    private static final Path FIXTURES = Path.of("src", "test", "resources", "fixtures");

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();
    private final SemanticValidator validator = new SemanticValidator();

    @Test
    void discoversTheBindingFromADifferentlyNamedMethodOnADifferentlyNamedService() {
        Path source = FIXTURES.resolve("discovered");
        assertThat(Files.isDirectory(source)).as("fixture at " + source.toAbsolutePath()).isTrue();

        CapabilityModel model = provider.provide(new ProviderInput(source));

        assertThat(model.entities()).extracting(Entity::name).containsExactly("Account");
        assertThat(model.capabilities()).hasSize(1);

        Capability getAccount = model.capabilities().get(0);
        assertThat(getAccount.name()).isEqualTo("getAccount");
        assertThat(getAccount.inputs()).containsExactly(entry("id", Primitive.LONG));
        assertThat(getAccount.output()).isEqualTo(Type.entityRef("Account"));

        // The proof of discovery: the binding is the real method AccountStore#loadById. The old
        // get<Entity> -> <Entity>Service#findById convention would have produced
        // AccountService#findById, which does not exist in the fixture.
        assertThat(getAccount.binding().value()).isEqualTo("AccountStore#loadById");
        assertThat(getAccount.binding().value()).isNotEqualTo("AccountService#findById");

        assertThat(validator.validate(model).isValid()).isTrue();
    }

    @Test
    void failsLoudlyWhenNoServiceMethodBacksTheRead() {
        Path source = FIXTURES.resolve("unbound");

        assertThatThrownBy(() -> provider.provide(new ProviderInput(source)))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("getOrder")
                .hasMessageContaining("Order")
                .hasMessageContaining("LONG")
                // It reports the @Service methods it did see, so the failure is diagnosable.
                .hasMessageContaining("findByReference")
                // And it never falls back to a convention.
                .hasMessageContaining("discovered, not assumed");
    }

    @Test
    void failsLoudlyWhenMoreThanOneServiceMethodBacksTheRead() {
        Path source = FIXTURES.resolve("ambiguous");

        assertThatThrownBy(() -> provider.provide(new ProviderInput(source)))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("more than one")
                .hasMessageContaining("ItemService#findById")
                .hasMessageContaining("ItemService#loadById")
                .hasMessageContaining("will not guess");
    }
}
