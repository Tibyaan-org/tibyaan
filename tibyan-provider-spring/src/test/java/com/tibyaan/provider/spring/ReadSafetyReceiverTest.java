package com.tibyaan.provider.spring;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.tibyaan.provider.spring.attest.ReadSafety;
import com.tibyaan.provider.spring.extractors.RepositoryCatalog;
import com.tibyaan.provider.spring.extractors.TypeReferenceResolver;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fixtures RS-1 to RS-13 for the ratified read-safety receiver-awareness refinement (Volume 5 section
 * 4.15.2.1), exercised directly against {@link ReadSafety} so each of the ratified conditions L1 to L6 and
 * refusal cases R1 to R9 gets its own assertion.
 *
 * <p>Why directly rather than only through the attestation intake: several refusal cases are unreachable
 * end to end. A collection-typed parameter fails intake condition 3 (parameter type mapping) BEFORE
 * read-safety runs, so R4 in both its direct and aliased forms can only be reached here. The end-to-end
 * behaviour is covered separately in {@code AttestationIntakeProviderTest} (RS-1, RS-2, RS-3).
 *
 * <p>THE INVARIANT UNDER TEST IS MIS-SAFE: a call that writes to application state must never be
 * classified read-safe. Every refusal assertion below is a guard on that invariant, and RS-3, RS-7 and the
 * RS-8 family are the ones that would break first if the carve-out were ever widened.
 */
class ReadSafetyReceiverTest {

    private static final Path FIXTURE = Path.of("src", "test", "resources", "fixtures",
            "read-safety-receiver", "ReadSafetyReceiver.java");

    // ---------- the carve-out: provably local receivers are not writes ----------

    @Test
    void rs1_aggregationIntoAFreshlyConstructedLocalMapIsReadSafe() throws IOException {
        assertThat(check("totalsByCategory")).isEmpty();
    }

    @Test
    void rs2_aProvableLocalAggregationIsReadSafeEvenWhenTheReadIsOtherwiseUnattestable()
            throws IOException {
        assertThat(check("ledgerTotals")).isEmpty();
    }

    // ---------- R1, the primary safety guard ----------

    @Test
    void rs3_aWriteSetCallOnARepositoryFieldStillRefuses() throws IOException {
        assertThat(check("totalsAndPurge"))
                .hasValueSatisfying(reason -> assertThat(reason)
                        .contains("write-set call 'delete'")
                        .contains("repository field 'costRepository'")
                        .contains("R1"));
    }

    // ---------- R5 and R8, the alias and escape guards ----------

    @Test
    void rs4a_mutatingACollectionObtainedFromALoadedEntityRefuses() throws IOException {
        assertThat(check("dropTag"))
                .hasValueSatisfying(reason -> assertThat(reason)
                        .contains("obtained from a persistence access")
                        .contains("R5"));
    }

    @Test
    void rs4b_mutatingAFreshLocalThatEscapedToARepositoryRefuses() throws IOException {
        assertThat(check("attachThenTrim"))
                .hasValueSatisfying(reason -> assertThat(reason)
                        .contains("passed to a persistence receiver")
                        .contains("L6")
                        .contains("R8"));
    }

    // ---------- R6, the type guards ----------

    @Test
    void rs5a_aConstructedTypeOutsideTheAllowlistRefuses() throws IOException {
        assertThat(check("customAccumulator"))
                .hasValueSatisfying(reason -> assertThat(reason)
                        .contains("'Accumulator' is not an allowlisted local container")
                        .contains("R6"));
    }

    @Test
    void rs5b_anAllowlistedNameShadowedByAnInTreeDeclarationRefuses() throws IOException {
        assertThat(check("shadowedContainer"))
                .hasValueSatisfying(reason -> assertThat(reason)
                        .contains("shadowed by an in-tree declaration")
                        .contains("R6"));
    }

    // ---------- R7, the reassignment guard ----------

    @Test
    void rs6_aReassignedLocalRefuses() throws IOException {
        assertThat(check("reassignedAccumulator"))
                .hasValueSatisfying(reason -> assertThat(reason)
                        .contains("is reassigned in this body")
                        .contains("L5")
                        .contains("R7"));
    }

    // ---------- R3, the case that forbids copying the discovery-path scoping ----------

    @Test
    void rs7_aWriteSetCallOnANonRepositoryFieldStillRefuses() throws IOException {
        assertThat(check("archiveThenTotal"))
                .hasValueSatisfying(reason -> assertThat(reason)
                        .contains("write-set call 'delete'")
                        .contains("field 'costArchiver'")
                        .contains("a field receiver is never a provable local")
                        .contains("R3"));
    }

    // ---------- RS-8, the MIS-SAFE family: a local carve-out never rescues a writing body ----------

    @Test
    void rs8a_anEntityManagerWriteBesideALegitimateLocalAggregationRefuses() throws IOException {
        assertThat(check("persistThenTotal"))
                .hasValueSatisfying(reason -> assertThat(reason)
                        .contains("persistence-infrastructure field 'entityManager'")
                        .contains("R2"));
    }

    @Test
    void rs8b_aSetterOnALoadedEntityBesideALegitimateLocalAggregationRefuses() throws IOException {
        assertThat(check("retagThenTotal"))
                .hasValueSatisfying(reason -> assertThat(reason)
                        .contains("setter 'setCategory' on a loaded entity"));
    }

    @Test
    void rs8c_aModifyingRepositoryMethodBesideALegitimateLocalAggregationRefuses() throws IOException {
        assertThat(check("reclassifyThenTotal"))
                .hasValueSatisfying(reason -> assertThat(reason)
                        .contains("reclassifyAll")
                        .contains("@Modifying"));
    }

    // ---------- R9 and L1, the untraceable-receiver guards ----------

    @Test
    void rs9_anUnqualifiedWriteSetCallRefuses() throws IOException {
        assertThat(check("selfWrite"))
                .hasValueSatisfying(reason -> assertThat(reason)
                        .contains("has no receiver")
                        .contains("R9"));
    }

    @Test
    void rs10_aChainedReceiverRefuses() throws IOException {
        assertThat(check("chainedReceiver"))
                .hasValueSatisfying(reason -> assertThat(reason)
                        .contains("not a bare simple name")
                        .contains("L1"));
    }

    // ---------- L2 and R4, the declaration-origin guards ----------

    @Test
    void rs11_aForEachLoopVariableReceiverRefuses() throws IOException {
        assertThat(check("loopVariableReceiver"))
                .hasValueSatisfying(reason -> assertThat(reason)
                        .contains("does not resolve to exactly one plain local declaration")
                        .contains("L2"));
    }

    @Test
    void rs12_aLocalAliasedFromAParameterRefuses() throws IOException {
        assertThat(check("aliasedParameter"))
                .hasValueSatisfying(reason -> assertThat(reason)
                        .contains("is an alias of 'seed'")
                        .contains("R4"));
    }

    @Test
    void rs13_aMethodParameterReceiverRefuses() throws IOException {
        assertThat(check("parameterReceiver"))
                .hasValueSatisfying(reason -> assertThat(reason)
                        .contains("method parameter 'seed'")
                        .contains("R4"));
    }

    // ---------- harness ----------

    private Optional<String> check(String methodName) throws IOException {
        CompilationUnit unit = StaticJavaParser.parse(Files.readString(FIXTURE));
        List<CompilationUnit> units = List.of(unit);
        RepositoryCatalog catalog = RepositoryCatalog.from(units, TypeReferenceResolver.from(units));
        ClassOrInterfaceDeclaration service = unit.findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(type -> type.getNameAsString().equals("CostReportServiceImpl"))
                .findFirst()
                .orElseThrow();
        MethodDeclaration method = service.getMethods().stream()
                .filter(m -> m.getNameAsString().equals(methodName))
                .findFirst()
                .orElseThrow();
        return new ReadSafety(catalog).check(service, method, List.of());
    }
}
