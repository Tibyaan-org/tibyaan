package com.tibyaan.provider.spring;

import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.semantic.SemanticValidator;
import com.tibyaan.plugin.ProviderInput;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase Binding Identity, the provider-side fixtures (Volume 7 section 12.6; Volume 5 section 4.18). The
 * binding is {@code <canonical qualified class name>#<method>} on every tier, so two services sharing a
 * simple name across bounded contexts no longer collide on the binding string.
 */
class BindingIdentityProviderTest {

    private static final Path FIXTURES = Path.of("src", "test", "resources", "fixtures");

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();
    private final SemanticValidator validator = new SemanticValidator();

    @Test
    void bi1_aDiscoveredBindingCarriesTheServicesQualifiedName() {
        CapabilityModel model = provider.provide(input("discovered"));

        assertThat(model.capabilities()).extracting(c -> c.binding().value())
                .containsExactly("com.example.banking.AccountStore#loadById");
    }

    @Test
    void bi2_twoSameSimpleNameServicesInTwoContextsBothEmitWithDistinctQualifiedBindings() {
        // Before this phase both reads bound to OfferService#getById and core rule 7 refused the model as a
        // possible runtime mis-bind. The qualified binding makes the two distinct, and the entity display
        // names (section 4.12.3) keep the capability names distinct, so both ship.
        CapabilityModel model = provider.provide(input("binding-identity"));

        assertThat(model.entities()).extracting(Entity::name)
                .containsExactlyInAnyOrder("CrmOffer", "SupportOffer");
        assertThat(model.capabilities()).extracting(Capability::name)
                .containsExactlyInAnyOrder("getCrmOffer", "getSupportOffer");
        assertThat(model.capabilities()).extracting(c -> c.binding().value())
                .containsExactlyInAnyOrder("example.crm.model.OfferService#getById",
                        "example.support.model.OfferService#getById");
        assertThat(validator.validate(model).isValid())
                .as("distinct qualified bindings satisfy rule 7; distinct names satisfy rule 2")
                .isTrue();
    }

    private ProviderInput input(String fixture) {
        Path source = FIXTURES.resolve(fixture);
        assertThat(Files.isDirectory(source)).as("fixture at " + source.toAbsolutePath()).isTrue();
        return new ProviderInput(source);
    }
}
