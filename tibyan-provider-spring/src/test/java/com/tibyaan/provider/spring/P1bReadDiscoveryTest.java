package com.tibyaan.provider.spring;

import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.Type;
import com.tibyaan.core.semantic.SemanticValidator;
import com.tibyaan.plugin.ProviderInput;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase AT-0, the end-to-end proof that the P1b strict by-primary-key custom-query matcher (Volume 5,
 * section 4.2.10) is threaded into the section 4.2 read discovery. A verified custom {@code @Query}
 * discovers exactly as a framework load would; an inner JOIN FETCH or a filtered WHERE stays refused,
 * with the P1b reason located on the outcome. The token-level condition coverage lives in
 * P1bQueryMatcherTest; these fixtures prove the wiring, not the tokens.
 */
class P1bReadDiscoveryTest {

    private static final Path FIXTURES = Path.of("src", "test", "resources", "fixtures");

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();
    private final SemanticValidator validator = new SemanticValidator();

    @Test
    void aVerifiedByPkCustomQueryDiscoversTheReadThroughTheViewRule() {
        ScanReport report = provider.scan(input("p1b-verified"));

        Capability alpha = named(report, "getAlpha");
        assertThat(alpha.binding().value()).isEqualTo("AlphaService#getById");
        assertThat(alpha.output()).isEqualTo(Type.entityRef("Alpha"));
        assertThat(alpha.annotations()).anySatisfy(annotation -> {
            assertThat(annotation.key()).isEqualTo("view");
            assertThat(annotation.values()).containsExactly("AlphaVto");
        });
        assertThat(validator.validate(report.model()).isValid()).isTrue();
    }

    @Test
    void anInnerFetchAndAFilteredWhereBothStayRefusedWithTheP1bReason() {
        ScanReport report = provider.scan(input("p1b-refused"));

        // No capability is emitted for either refused shape.
        assertThat(report.model().capabilities()).extracting(Capability::name)
                .doesNotContain("getBeta", "getGamma");

        // The inner JOIN FETCH is the pms Tenant shape: an existence filter, refused (P1b, P4).
        assertThat(cause(report, "Beta")).isEqualTo(EntityOutcome.Cause.NO_BACKING_METHOD);
        assertThat(detail(report, "Beta")).contains("inner JOIN FETCH").contains("P1b, P4");

        // The filtered WHERE is the defining refusal (P1b, P5).
        assertThat(cause(report, "Gamma")).isEqualTo(EntityOutcome.Cause.NO_BACKING_METHOD);
        assertThat(detail(report, "Gamma")).contains("filters beyond the primary key").contains("P1b, P5");
    }

    // ---------- helpers ----------

    private ProviderInput input(String fixture) {
        Path source = FIXTURES.resolve(fixture);
        assertThat(Files.isDirectory(source)).as("fixture at " + source.toAbsolutePath()).isTrue();
        return new ProviderInput(source);
    }

    private static Capability named(ScanReport report, String name) {
        return report.model().capabilities().stream()
                .filter(capability -> capability.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no capability named " + name
                        + "; capabilities were "
                        + report.model().capabilities().stream().map(Capability::name).toList()));
    }

    private static EntityOutcome outcome(ScanReport report, String entityName) {
        return report.entityOutcomes().stream()
                .filter(o -> o.entityName().equals(entityName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no outcome for entity " + entityName));
    }

    private static String detail(ScanReport report, String entityName) {
        return outcome(report, entityName).detail();
    }

    private static EntityOutcome.Cause cause(ScanReport report, String entityName) {
        return outcome(report, entityName).cause();
    }
}
