package com.tibyaan.runtime.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase SEC-1, rules S3 and S4 at the gate (Volume 7 section 15.4), fixtures SC-1 to SC-4 and SC-8 of section
 * 15.6, plus decision D2 (exact literal comparison) and the manifest field the loader now reads.
 */
class CallerGateTest {

    private static final String SVC = "com.example.Svc";

    private static final CapabilityDescriptor PLAIN_READ =
            new CapabilityDescriptor("getThing", "Read Thing.", SVC + "#get", "Thing", List.of());

    private static final CapabilityDescriptor CONTRACTED =
            new CapabilityDescriptor("getSecured", "Read Secured.", SVC + "#secured", "Secured", List.of())
                    .withRequiredAuthorities(List.of("480012", "480099"));

    private static final CapabilityDescriptor WRITE = CapabilityDescriptor.reviewed("reviewed_record",
            "Reviewed by r (human), not tool-verified; the tool labeled it WRITE. X.", SVC + "#record", "Receipt",
            List.of(), CapabilityDescriptor.KIND_WRITE, false, new ReviewDescriptor("WRITE", "r", "2026-09-24", "human"));

    private static final CapabilityDescriptor ACTION = CapabilityDescriptor.reviewed("reviewed_act",
            "Reviewed by r (human), not tool-verified; the tool labeled it UNKNOWN. X.", SVC + "#act", "Void",
            List.of(), CapabilityDescriptor.KIND_ACTION, false, new ReviewDescriptor("UNKNOWN", "r", "2026-09-24", "human"));

    private static final CapabilityDescriptor UNVERIFIABLE_READ = CapabilityDescriptor.reviewed("reviewed_open",
            "Reviewed by r (human), not tool-verified; the tool labeled it READ. X.", SVC + "#open", "Thing",
            List.of(), CapabilityDescriptor.KIND_READ, true, new ReviewDescriptor("READ", "r", "2026-09-24", "human"));

    private static Optional<Principal> caller(String name, String... authorities) {
        return Optional.of(new TestingAuthenticationToken(name, "pw", authorities));
    }

    @Test
    void sc1_anAuthenticatedCallerHoldingOneCompiledAuthorityInvokes() {
        assertThat(CallerGate.refusal(CONTRACTED, caller("alice", "480099", "OTHER"), true)).isEmpty();
        assertThat(CallerGate.refusal(CONTRACTED, caller("alice", "480012"), false)).isEmpty();
    }

    @Test
    void sc2_aCallerHoldingNoneIsRefusedNamingTheToolAndTheContractNeverTheCallersAuthorities() {
        Optional<String> refusal = CallerGate.refusal(CONTRACTED, caller("bob", "OTHER", "SECRET_ROLE"), true);

        assertThat(refusal).isPresent();
        assertThat(refusal.get())
                .contains("'getSecured'")
                .contains("[480012, 480099]")
                .contains("holds none of the compiled authorities")
                .doesNotContain("OTHER")
                .doesNotContain("SECRET_ROLE")
                .doesNotContain("bob");
    }

    @Test
    void d2_theComparisonIsExactLiteralForLiteral() {
        assertThat(CallerGate.refusal(CONTRACTED, caller("x", "ROLE_480012"), true)).isPresent();
        assertThat(CallerGate.refusal(CONTRACTED, caller("x", "480012 "), true)).isPresent();
        assertThat(CallerGate.refusal(CONTRACTED, caller("x", "4800120"), true)).isPresent();
        assertThat(CallerGate.refusal(CONTRACTED, caller("x", "480012"), true)).isEmpty();
    }

    @Test
    void sc3_anAnonymousCallerIsRefusedOnAContractCarryingToolAndOnAReviewedWriteUnderRequireCaller() {
        Optional<String> contract = CallerGate.refusal(CONTRACTED, Optional.empty(), true);
        assertThat(contract).isPresent();
        assertThat(contract.get()).contains("'getSecured'").contains("no authenticated caller").contains("[480012, 480099]");

        Optional<String> write = CallerGate.refusal(WRITE, Optional.empty(), true);
        assertThat(write).isPresent();
        assertThat(write.get()).contains("'reviewed_record'").contains("reviewed WRITE")
                .contains(ExposurePolicy.SECURITY_REQUIRE_CALLER + "=true");
        assertThat(CallerGate.refusal(ACTION, Optional.empty(), true).orElseThrow()).contains("reviewed ACTION");

        // A plain read keeps today's behavior: the anonymous caller is admitted (S4 names writes and contracts).
        assertThat(CallerGate.refusal(PLAIN_READ, Optional.empty(), true)).isEmpty();
        // And an authenticated caller invokes a write, whatever authorities it holds, when no contract is compiled.
        assertThat(CallerGate.refusal(WRITE, caller("alice"), true)).isEmpty();
    }

    @Test
    void sc4_requireCallerFalseAdmitsTheAnonymousCallerToAToolWithNoContractAndStillRefusesOneWithAContract() {
        assertThat(CallerGate.refusal(WRITE, Optional.empty(), false)).isEmpty();
        assertThat(CallerGate.refusal(ACTION, Optional.empty(), false)).isEmpty();
        assertThat(CallerGate.refusal(PLAIN_READ, Optional.empty(), false)).isEmpty();
        assertThat(CallerGate.refusal(CONTRACTED, Optional.empty(), false)).isPresent();
        assertThat(CallerGate.refusal(CONTRACTED, caller("bob", "OTHER"), false)).isPresent();
    }

    @Test
    void sc8_aSecurityUnverifiableToolKeepsTheListRuleAsItsOnlyGate() {
        // No contract was compiled for it, so the gate has nothing to enforce: anonymous or not, a READ passes.
        assertThat(UNVERIFIABLE_READ.hasContract()).isFalse();
        assertThat(CallerGate.refusal(UNVERIFIABLE_READ, Optional.empty(), true)).isEmpty();
        assertThat(CallerGate.refusal(UNVERIFIABLE_READ, caller("anyone"), true)).isEmpty();
        // The registrar's list rule is unchanged: unlisted, it does not register; listed, it does.
        ExposurePolicy unlisted = ExposurePolicy.defaults();
        ExposurePolicy listed = new ExposurePolicy(true, false, false, true, List.of(), List.of("reviewed_open"));
        assertThat(new ToolRegistrar(new ObjectMapper(), unlisted).exclusionReason(UNVERIFIABLE_READ))
                .isEqualTo(ToolRegistrar.SECURITY_UNVERIFIABLE_NOT_REVIEWED);
        assertThat(new ToolRegistrar(new ObjectMapper(), listed).exclusionReason(UNVERIFIABLE_READ)).isNull();
    }

    @Test
    void aPrincipalThatIsNotASpringSecurityAuthenticationCannotSatisfyAContract() {
        Principal foreign = () -> "container-user";
        Optional<String> refusal = CallerGate.refusal(CONTRACTED, Optional.of(foreign), true);
        assertThat(refusal).isPresent();
        assertThat(refusal.get()).contains("cannot be read").contains("[480012, 480099]");
        // The same foreign principal counts as a caller for S4: the write is admitted.
        assertThat(CallerGate.refusal(WRITE, Optional.of(foreign), true)).isEmpty();
    }

    @Test
    void theManifestsRequiredAuthoritiesReachTheDescriptorAndTheirAbsenceMeansNoContract() {
        String json = """
                {"module":"m","version":"0.1","tools":[
                  {"name":"getDepartment","description":"Read Department.","binding":"com.example.D#get",
                   "output":"Department","inputs":[],"requiredAuthorities":["200019"]},
                  {"name":"getThing","description":"Read Thing.","binding":"com.example.T#get","output":"Thing","inputs":[]}
                ]}""";
        CapabilitiesManifest manifest = new CapabilitiesLoader(new ObjectMapper())
                .load(new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8)));

        assertThat(manifest.tools().get(0).requiredAuthorities()).containsExactly("200019");
        assertThat(manifest.tools().get(0).hasContract()).isTrue();
        assertThat(manifest.tools().get(1).requiredAuthorities()).isEmpty();
        assertThat(manifest.tools().get(1).hasContract()).isFalse();
    }
}
