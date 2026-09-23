package com.tibyaan.runtime.spring;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RX-10 (Volume 7 section 10.7): the comma-separated property form and the YAML list form bind to the same
 * list. This is a fixture because {@code @Value} does not read a YAML list, so the lists are bound through
 * the Spring {@code Binder}. The three attested booleans keep their defaults and meaning (section 10 D4),
 * and {@code tibyan.reviewed.expose} defaults true (DL-1 D2).
 */
class ExposurePolicyBindingTest {

    private static StandardEnvironment environmentWith(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        return environment;
    }

    private static StandardEnvironment environmentWithYaml(String yaml) throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("test-yaml",
                new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));
        for (PropertySource<?> source : sources) {
            environment.getPropertySources().addFirst(source);
        }
        return environment;
    }

    @Test
    void theDefaultsAreAttestedOpenUnverifiableClosedBoundaryClosedReviewedOpenAndBothListsEmpty() {
        ExposurePolicy policy = ExposurePolicy.bind(new StandardEnvironment());

        assertThat(policy).isEqualTo(ExposurePolicy.defaults());
        assertThat(policy.exposeAttested()).isTrue();
        assertThat(policy.exposeSecurityUnverifiable()).isFalse();
        assertThat(policy.exposeBoundary()).isFalse();
        assertThat(policy.exposeReviewed()).isTrue();
        assertThat(policy.writesAllow()).isEmpty();
        assertThat(policy.exposeUnverifiable()).isEmpty();
    }

    @Test
    void rx10_theCommaSeparatedPropertyFormBindsBothLists() {
        ExposurePolicy policy = ExposurePolicy.bind(environmentWith(Map.of(
                ExposurePolicy.SECURITY_EXPOSE_UNVERIFIABLE, "attested_citiesByFilters, attested_searchTenants",
                ExposurePolicy.WRITES_ALLOW, "reviewed_recordPayment,reviewed_closeTicket")));

        assertThat(policy.exposeUnverifiable())
                .containsExactly("attested_citiesByFilters", "attested_searchTenants");
        assertThat(policy.writesAllow()).containsExactly("reviewed_recordPayment", "reviewed_closeTicket");
    }

    @Test
    void rx10_theYamlListFormBindsToTheSameLists() throws Exception {
        ExposurePolicy policy = ExposurePolicy.bind(environmentWithYaml("""
                tibyan:
                  security:
                    exposeUnverifiable:
                      - attested_citiesByFilters
                      - attested_searchTenants
                  writes:
                    allow:
                      - reviewed_recordPayment
                      - reviewed_closeTicket
                """));

        assertThat(policy.exposeUnverifiable())
                .containsExactly("attested_citiesByFilters", "attested_searchTenants");
        assertThat(policy.writesAllow()).containsExactly("reviewed_recordPayment", "reviewed_closeTicket");
    }

    @Test
    void rx10_theTwoFormsAreEqualAsPolicies() throws Exception {
        ExposurePolicy fromProperties = ExposurePolicy.bind(environmentWith(Map.of(
                ExposurePolicy.SECURITY_EXPOSE_UNVERIFIABLE, "a_one,a_two",
                ExposurePolicy.WRITES_ALLOW, "r_one")));
        ExposurePolicy fromYaml = ExposurePolicy.bind(environmentWithYaml("""
                tibyan:
                  security:
                    exposeUnverifiable: [a_one, a_two]
                  writes:
                    allow:
                      - r_one
                """));

        assertThat(fromProperties).isEqualTo(fromYaml);
    }

    @Test
    void theSixPropertiesBindUnderTheirDocumentedCamelCaseNames() {
        ExposurePolicy policy = ExposurePolicy.bind(environmentWith(Map.of(
                ExposurePolicy.ATTESTED_EXPOSE, "false",
                ExposurePolicy.ATTESTED_EXPOSE_SECURITY_UNVERIFIABLE, "true",
                ExposurePolicy.ATTESTED_EXPOSE_BOUNDARY, "true",
                ExposurePolicy.REVIEWED_EXPOSE, "false",
                ExposurePolicy.WRITES_ALLOW, "reviewed_recordPayment",
                ExposurePolicy.SECURITY_EXPOSE_UNVERIFIABLE, "attested_citiesByFilters")));

        assertThat(policy).isEqualTo(new ExposurePolicy(false, true, true, false,
                List.of("reviewed_recordPayment"), List.of("attested_citiesByFilters")));
    }

    @Test
    void anEmptyPropertyValueBindsAnEmptyListNotABlankEntry() {
        ExposurePolicy policy = ExposurePolicy.bind(environmentWith(Map.of(
                ExposurePolicy.WRITES_ALLOW, "",
                ExposurePolicy.SECURITY_EXPOSE_UNVERIFIABLE, "")));

        assertThat(policy.writesAllow()).isEmpty();
        assertThat(policy.exposeUnverifiable()).isEmpty();
    }
}
