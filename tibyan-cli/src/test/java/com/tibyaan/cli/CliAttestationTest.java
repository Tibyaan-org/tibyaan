package com.tibyaan.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The attestation flow through the CLI (Volume 5 section 4.15): {@code tibyan attest} stamps the
 * fingerprint into a hand-authored record, then {@code tibyan scan} intakes it and the survey reports the
 * attested capability as its own outcome class, counted SEPARATELY and never summed into discovered. This
 * is the carriage-exactness fixture for the SURVEY surface (ATT-4) plus the attest CLI surface.
 */
class CliAttestationTest {

    private static final String FIXTURE = """
            package com.example.attest;

            import jakarta.persistence.Entity;
            import jakarta.persistence.Id;
            import org.springframework.data.jpa.repository.JpaRepository;
            import org.springframework.stereotype.Service;

            import java.util.List;

            @Entity
            class Tenant {
                @Id
                Long id;
                String fullName;
            }

            interface TenantRepository extends JpaRepository<Tenant, Long> {
                List<Tenant> findByFilters(String fullName, Integer pageNum);
            }

            class TenantResultSet {
                TenantResultSet(List<Tenant> data) {
                }
            }

            @Service
            class TenantServiceImpl {
                private final TenantRepository tenantRepository;

                TenantServiceImpl(TenantRepository tenantRepository) {
                    this.tenantRepository = tenantRepository;
                }

                public TenantResultSet getAllByFilter(String fullName, Integer pageNum) {
                    return new TenantResultSet(tenantRepository.findByFilters(fullName, pageNum));
                }
            }
            """;

    // A hand-authored entry, WITHOUT a fingerprint: it refuses at intake until tibyan attest stamps it.
    private static final String ATTESTATIONS = """
            [{"target":"com.example.attest.TenantServiceImpl#getAllByFilter(String,Integer)",\
            "category":"predicate","name":"searchTenants",\
            "resultSemantic":"Returns the tenants matching the given filters.",\
            "parameters":{"fullName":"Matches the full name.","pageNum":"paging"},\
            "provenance":{"assertedBy":"a.elseginy","origin":"human-authored","date":"2026-07-24"}}]
            """;

    private int run(String... args) {
        return new CommandLine(new TibyanCli()).execute(args);
    }

    private String runCapturingStdout(String... args) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            run(args);
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void attestStampsThenScanIntakesAndTheSurveyReportsTheAttestedClassSeparately(@TempDir Path root)
            throws IOException {
        Files.writeString(root.resolve("AttestPredicate.java"), FIXTURE);
        Path attestations = root.resolve("tibyan").resolve("attestations.json");
        Files.createDirectories(attestations.getParent());
        Files.writeString(attestations, ATTESTATIONS);

        // Before stamping, the entry has no fingerprint, so the survey refuses it (never carries it).
        String beforeStamp = runCapturingStdout("scan", root.toString());
        assertThat(beforeStamp).contains("Attestation refusals").contains("MISSING_FINGERPRINT");
        assertThat(beforeStamp).doesNotContain("attested_searchTenants");

        // tibyan attest stamps the fingerprint over the current code.
        assertThat(run("attest", root.toString())).isEqualTo(0);

        // Now the scan intakes it: the survey shows the attested class, counted separately, drift 0.
        String afterStamp = runCapturingStdout("scan", root.toString());
        assertThat(afterStamp).contains("Attested, human-attested not tool-verified (1):");
        assertThat(afterStamp).contains("@ attested_searchTenants -> com.example.attest.TenantServiceImpl#getAllByFilter "
                + "[predicate]: Returns the tenants matching the given filters. "
                + "(asserted by a.elseginy, human-authored, 2026-07-24)");
        // Counted separately: discovered is 0 (the filtered read mints no verified capability), attested 1.
        assertThat(afterStamp).contains("discovered 0 (by-id 0, list 0), attested 1");
        assertThat(afterStamp).contains("attestation-drift 0");
    }
}
