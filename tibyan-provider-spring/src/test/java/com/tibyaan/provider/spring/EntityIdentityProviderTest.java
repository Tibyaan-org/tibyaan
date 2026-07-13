package com.tibyaan.provider.spring;

import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Type;
import com.tibyaan.core.semantic.SemanticValidator;
import com.tibyaan.plugin.ProviderInput;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase Entity Identity, the full-pipeline fixtures (Volume 5 section 4.12.6). These fixtures live in
 * test resources only and are never compiled; the provider reads them as source, exactly as it reads
 * a real app. The algorithm-level fixtures (EI-7 to EI-10, EI-13, EI-14) are in
 * {@link com.tibyaan.provider.spring.extractors.DisplayNamesTest}, and the reference-resolution
 * fixtures (EI-4, EI-5, EI-11, EI-12) in
 * {@link com.tibyaan.provider.spring.extractors.TypeReferenceResolverTest}. EI-3 (duplicate-key
 * validation) is in the core {@code SemanticValidatorTest}. All names are synthetic.
 */
class EntityIdentityProviderTest {

    private static final Path FIXTURES = Path.of("src", "test", "resources", "fixtures");

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();
    private final SemanticValidator validator = new SemanticValidator();

    @Test
    void ei1_twoSameNamedEntitiesEachDiscoverIntoDistinctDisambiguatedCapabilities() {
        CapabilityModel model = provider.provide(input("entity-identity-collision"));

        // Both entities are retained with distinct disambiguated display names, keyed by FQCN.
        assertThat(model.entities()).extracting(Entity::name)
                .containsExactlyInAnyOrder("CrmNote", "SupportNote");
        assertThat(model.entities()).extracting(Entity::key)
                .containsExactlyInAnyOrder("example.crm.model.Note", "example.support.model.Note");

        // Each read discovers into its own capability, bound to its own context's method by key.
        assertThat(model.capabilities()).extracting(Capability::name)
                .containsExactlyInAnyOrder("getCrmNote", "getSupportNote");
        Capability crm = capabilityNamed(model, "getCrmNote");
        assertThat(crm.output()).isEqualTo(Type.entityRef("CrmNote"));
        assertThat(crm.binding().value()).isEqualTo("CrmNoteService#getById");
        Capability support = capabilityNamed(model, "getSupportNote");
        assertThat(support.output()).isEqualTo(Type.entityRef("SupportNote"));
        assertThat(support.binding().value()).isEqualTo("SupportNoteService#getById");

        assertThat(validator.validate(model).isValid())
                .as("the disambiguated model is valid: distinct names, distinct bindings")
                .isTrue();
    }

    @Test
    void ei2_aMultiContextModelWithNoCollisionKeepsEverySimpleNameUnchanged() {
        CapabilityModel model = provider.provide(input("entity-identity-no-collision"));

        // No colliding simple names: every display name is exactly the simple name (no qualification),
        // even across two contexts. This is the no-regression clause (section 4.12.3, condition 1).
        assertThat(model.entities()).extracting(Entity::name)
                .containsExactlyInAnyOrder("Product", "Order");
        assertThat(model.entities()).extracting(Entity::key)
                .containsExactlyInAnyOrder("example.catalog.model.Product", "example.sales.model.Order");
        assertThat(model.capabilities()).extracting(Capability::name)
                .containsExactlyInAnyOrder("getProduct", "getOrder");
        assertThat(validator.validate(model).isValid()).isTrue();
    }

    @Test
    void ei6_aFinderOfOneContextsEntityNeverBindsTheOtherSameNamedEntity() {
        // The crm Note has a finder; the support Note has none. The mis-bound guard: the crm finder
        // backs only the crm Note, and the support Note fails loud as NO_BACKING_METHOD, it is never
        // mis-bound to the crm finder because identity is resolved by key.
        ScanReport report = provider.scan(input("entity-identity-misbound"));
        CapabilityModel model = report.model();

        assertThat(model.capabilities()).extracting(Capability::name).containsExactly("getCrmNote");
        Capability crm = model.capabilities().get(0);
        assertThat(crm.output()).isEqualTo(Type.entityRef("CrmNote"));
        assertThat(crm.binding().value()).isEqualTo("NoteService#getById");
        // No capability anywhere outputs the support Note: it was never bound.
        assertThat(model.capabilities()).noneMatch(c -> c.output().equals(Type.entityRef("SupportNote")));

        assertThat(outcomeNamed(report, "CrmNote").status()).isEqualTo(EntityOutcome.Status.DISCOVERED);
        EntityOutcome support = outcomeNamed(report, "SupportNote");
        assertThat(support.status()).isEqualTo(EntityOutcome.Status.FAILED);
        assertThat(support.cause()).isEqualTo(EntityOutcome.Cause.NO_BACKING_METHOD);
    }

    @Test
    void ei15_twoDistinctClassesSharingAKeyInDifferentFilesFailLoudAsASplitPackage() {
        assertThatThrownBy(() -> provider.provide(input("entity-identity-split")))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("split-package")
                .hasMessageContaining("example.shared.model.Note")
                // Worded distinctly from a genuine same-file duplicate, and it never merges them.
                .hasMessageContaining("not merged");
    }

    private ProviderInput input(String fixture) {
        Path source = FIXTURES.resolve(fixture);
        assertThat(Files.isDirectory(source)).as("fixture at " + source.toAbsolutePath()).isTrue();
        return new ProviderInput(source);
    }

    private static Capability capabilityNamed(CapabilityModel model, String name) {
        return model.capabilities().stream()
                .filter(capability -> capability.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no capability named " + name));
    }

    private static EntityOutcome outcomeNamed(ScanReport report, String entityName) {
        return report.entityOutcomes().stream()
                .filter(outcome -> outcome.entityName().equals(entityName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no outcome for entity " + entityName));
    }
}
