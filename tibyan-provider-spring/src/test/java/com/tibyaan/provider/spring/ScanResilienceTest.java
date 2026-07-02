package com.tibyaan.provider.spring;

import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.provider.spring.EntityOutcome.Cause;
import com.tibyaan.provider.spring.EntityOutcome.Status;
import com.tibyaan.plugin.ProviderInput;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/**
 * Phase E1: the resilient scan survives real-world source and classifies it (Volume 5 section 4).
 * The fixtures live in test resources only and are never compiled; the provider reads them as
 * source, exactly as it reads a real app.
 */
class ScanResilienceTest {

    private static final Path FIXTURES = Path.of("src", "test", "resources", "fixtures");

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();

    @Test
    void java17SyntaxParsesAndDiscovers() {
        // The service uses a switch expression, an instanceof pattern, and a text block, so it only
        // parses at Java 14 or later. If the language level were too low, GadgetService would be a
        // parse skip and Gadget would fail with NO_BACKING_METHOD instead of discovering.
        ScanReport report = provider.scan(new ProviderInput(FIXTURES.resolve("java17")));

        assertThat(report.parseSkips()).isEmpty();
        assertThat(report.discoveredCount()).isEqualTo(1);
        assertThat(report.model().capabilities()).extracting(Capability::name).containsExactly("getGadget");
        assertThat(report.entityOutcomes())
                .filteredOn(EntityOutcome::isDiscovered)
                .singleElement()
                .satisfies(outcome -> {
                    assertThat(outcome.entityName()).isEqualTo("Gadget");
                    assertThat(outcome.detail()).contains("GadgetService#findById");
                });
    }

    @Test
    void collectAndContinueSurvivesABrokenFile() {
        // One unparseable file in the tree must not abort the scan. Widget still discovers, and the
        // bad file is a named, located skip.
        ScanReport report = provider.scan(new ProviderInput(FIXTURES.resolve("resilient")));

        assertThat(report.discoveredCount()).isEqualTo(1);
        assertThat(report.model().entities()).extracting(Entity::name).containsExactly("Widget");
        assertThat(report.model().capabilities()).extracting(Capability::name).containsExactly("getWidget");

        assertThat(report.parseSkips()).singleElement().satisfies(skip -> {
            assertThat(skip.path()).contains("Broken.java");
            assertThat(skip.reason()).isNotBlank();
        });
    }

    @Test
    void provideStillAbortsLoudlyOnABrokenFile() {
        // The strict contract is unchanged: producing an artifact refuses a scan with any
        // unparseable file, naming it, rather than shipping a model that silently missed source.
        assertThatThrownBy(() -> provider.provide(new ProviderInput(FIXTURES.resolve("resilient"))))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("failed to parse")
                .hasMessageContaining("Broken.java");
    }

    @Test
    void scanClassifiesEveryEntityByCause() {
        // Every entity the scan parses gets exactly one outcome. Under E5 tolerance (Volume 5
        // section 4.8), Beta's undeclared-relationship field is skipped and Beta now extracts
        // partial, so it is in the model and its outcome is NO_BACKING_METHOD, not UNMAPPED.
        ScanReport report = provider.scan(new ProviderInput(FIXTURES.resolve("mixed")));

        assertThat(report.discoveredCount()).isEqualTo(1);
        assertThat(report.failedCount()).isEqualTo(3);
        assertThat(report.skippedByParseCount()).isZero();
        assertThat(report.failuresByCause()).containsOnly(
                entry(Cause.NO_BACKING_METHOD, 2L),
                entry(Cause.NO_IDENTIFIER, 1L));

        // All four entities extract now (Beta partial, its 'owner' field skipped); only Alpha yields
        // a capability.
        assertThat(report.model().entities()).extracting(Entity::name)
                .containsExactlyInAnyOrder("Alpha", "Beta", "Gamma", "Delta");
        assertThat(report.model().capabilities()).extracting(Capability::name).containsExactly("getAlpha");
        assertThat(outcome(report, "Beta").partialFields()).containsExactly("owner");
    }

    @Test
    void scanClassifiesAmbiguousBindingWithoutThrowing() {
        // The ambiguous fixture makes the strict provide throw (BindingDiscoveryTest); the survey
        // records it as a located failure and carries on.
        ScanReport report = provider.scan(new ProviderInput(FIXTURES.resolve("ambiguous")));

        assertThat(report.discoveredCount()).isZero();
        assertThat(report.entityOutcomes()).singleElement().satisfies(outcome -> {
            assertThat(outcome.status()).isEqualTo(Status.FAILED);
            assertThat(outcome.cause()).isEqualTo(Cause.AMBIGUOUS_BINDING);
        });
    }

    @Test
    void walkScopeExcludesBuildOutputAndToolDirectories() {
        // The walk rule (Volume 5 section 4): build output and tool metadata directories are never
        // walked, so their files are neither modeled nor reported as skips. Everything else is
        // walked, and relevance is decided by the @Entity and @Service annotations, not by paths.
        assertThat(SpringCapabilityProvider.isExcludedFromWalk(Path.of("proj", "target", "classes", "X.java"))).isTrue();
        assertThat(SpringCapabilityProvider.isExcludedFromWalk(Path.of("app", "build", "libs", "Y.java"))).isTrue();
        assertThat(SpringCapabilityProvider.isExcludedFromWalk(Path.of("m", ".git", "Z.java"))).isTrue();
        assertThat(SpringCapabilityProvider.isExcludedFromWalk(Path.of("m", ".idea", "W.java"))).isTrue();

        assertThat(SpringCapabilityProvider.isExcludedFromWalk(Path.of("src", "main", "java", "com", "A.java"))).isFalse();
    }

    private static EntityOutcome outcome(ScanReport report, String entityName) {
        return report.entityOutcomes().stream()
                .filter(o -> o.entityName().equals(entityName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no outcome for " + entityName));
    }
}
