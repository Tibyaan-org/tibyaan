package com.tibyaan.runtime.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tibyaan.plugin.ExecutionAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The write gate and the reviewed tier at the runtime (Volume 7 section 11; Volume 5 section 4.17.6). A
 * reviewed READ registers by default; a WRITE or ACTION registers ONLY when its exact name is in
 * {@code tibyan.writes.allow}, a list under section 10's five conditions; {@code tibyan.reviewed.expose=false}
 * closes the whole tier whatever the lists say (X4); and a served write is the same reflective invoke,
 * end to end through the registered callback.
 */
class WriteGateTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutionAdapter noop = (ref, arguments) -> null;

    private static final String VERIFIED = "getProduct";
    private static final String READ = "reviewed_findOverdue";
    private static final String WRITE = "reviewed_recordPayment";
    private static final String ACTION = "reviewed_runNightly";

    private static final ReviewDescriptor REVIEW_READ = new ReviewDescriptor("READ", "a.reviewer", "2026-09-23", "human");
    private static final ReviewDescriptor REVIEW_WRITE = new ReviewDescriptor("WRITE", "a.reviewer", "2026-09-23", "human");
    private static final ReviewDescriptor REVIEW_UNKNOWN = new ReviewDescriptor("UNKNOWN", "a.reviewer", "2026-09-23", "ai-draft");

    /** F9: bound to a class the two real-adapter tests host, so the verified tool still registers there. */
    public static class ProductService {
        public String findById(Long id) {
            return "product";
        }
    }

    private static CapabilityDescriptor verified() {
        return new CapabilityDescriptor(VERIFIED, "Read Product.", ProductService.class.getCanonicalName() + "#findById", "Product",
                List.of(new InputDescriptor("id", "LONG")));
    }

    private static CapabilityDescriptor reviewedRead() {
        return CapabilityDescriptor.reviewed(READ,
                "Reviewed by a.reviewer (human), not tool-verified; the tool labeled it READ. Lists overdue invoices.",
                "com.example.InvoiceService#findOverdue", "Invoice", List.of(), CapabilityDescriptor.KIND_READ, false, REVIEW_READ);
    }

    private static CapabilityDescriptor reviewedWrite(boolean securityUnverifiable) {
        return CapabilityDescriptor.reviewed(WRITE,
                "Reviewed by a.reviewer (human), not tool-verified; the tool labeled it WRITE. Records a payment.",
                "com.tibyaan.runtime.spring.WriteGateTest.LedgerService#record", "ENTITY_REF(Receipt)",
                List.of(new InputDescriptor("note", "STRING"), new InputDescriptor("amount", "LONG")),
                CapabilityDescriptor.KIND_WRITE, securityUnverifiable, REVIEW_WRITE);
    }

    private static CapabilityDescriptor reviewedAction() {
        return CapabilityDescriptor.reviewed(ACTION,
                "Reviewed by a.reviewer (ai-draft), not tool-verified; the tool labeled it UNKNOWN. Runs the nightly job.",
                "com.example.JobService#runNightly", "VOID", List.of(), CapabilityDescriptor.KIND_ACTION, false, REVIEW_UNKNOWN);
    }

    private static CapabilitiesManifest manifest() {
        return new CapabilitiesManifest("m", "0.1", List.of(verified(), reviewedRead(), reviewedWrite(false), reviewedAction()));
    }

    private static ExposurePolicy policy(boolean exposeReviewed, List<String> writesAllow) {
        return new ExposurePolicy(true, false, false, exposeReviewed, writesAllow, List.of());
    }

    private ToolRegistrar.Registration register(CapabilitiesManifest manifest, ExposurePolicy policy) {
        return new ToolRegistrar(mapper, policy).registration(manifest, noop);
    }

    // ---------- the gate ----------

    @Test
    void aReviewedReadRegistersByDefaultAndAnApprovedWriteDoesNotWithoutTheList() {
        ToolRegistrar.Registration registration = register(manifest(), ExposurePolicy.defaults());

        assertThat(registration.registeredNames()).containsExactly(VERIFIED, READ);
        assertThat(registration.excluded()).containsExactly(
                Map.entry(WRITE, ToolRegistrar.WRITE_NOT_ALLOWED),
                Map.entry(ACTION, ToolRegistrar.WRITE_NOT_ALLOWED));
    }

    @Test
    void anApprovedWriteRegistersWithItsExactNameAndOnlyThatOne() {
        ToolRegistrar.Registration registration = register(manifest(), policy(true, List.of(WRITE)));

        assertThat(registration.registeredNames()).containsExactly(VERIFIED, READ, WRITE);
        assertThat(registration.excluded()).containsExactly(Map.entry(ACTION, ToolRegistrar.WRITE_NOT_ALLOWED));
    }

    @Test
    void anApprovedActionIsBehindTheSameGate() {
        ToolRegistrar.Registration registration = register(manifest(), policy(true, List.of(ACTION)));

        assertThat(registration.registeredNames()).containsExactly(VERIFIED, READ, ACTION);
        assertThat(registration.excluded()).containsExactly(Map.entry(WRITE, ToolRegistrar.WRITE_NOT_ALLOWED));
    }

    @Test
    void x4_reviewedExposeFalseClosesTheWholeTierWhateverTheListSays() {
        ToolRegistrar.Registration registration = register(manifest(), policy(false, List.of(WRITE, ACTION)));

        assertThat(registration.registeredNames()).containsExactly(VERIFIED);
        assertThat(registration.excluded()).containsExactly(
                Map.entry(READ, ToolRegistrar.REVIEWED_CLOSED),
                Map.entry(WRITE, ToolRegistrar.REVIEWED_CLOSED),
                Map.entry(ACTION, ToolRegistrar.REVIEWED_CLOSED));
    }

    @Test
    void aSecurityUnverifiableWriteNeedsBothLists() {
        CapabilitiesManifest manifest = new CapabilitiesManifest("m", "0.1", List.of(verified(), reviewedWrite(true)));

        // The write gate alone is not enough: the declared gate is bypassed and nobody reviewed that.
        ToolRegistrar.Registration writeOnly = register(manifest,
                new ExposurePolicy(true, false, false, true, List.of(WRITE), List.of()));
        assertThat(writeOnly.registeredNames()).containsExactly(VERIFIED);
        assertThat(writeOnly.excluded()).containsEntry(WRITE, ToolRegistrar.SECURITY_UNVERIFIABLE_NOT_REVIEWED);

        // The review list alone is not enough either: a write is a write.
        ToolRegistrar.Registration securityOnly = register(manifest,
                new ExposurePolicy(true, false, false, true, List.of(), List.of(WRITE)));
        assertThat(securityOnly.registeredNames()).containsExactly(VERIFIED);
        assertThat(securityOnly.excluded()).containsEntry(WRITE, ToolRegistrar.WRITE_NOT_ALLOWED);

        // Both named: it registers.
        ToolRegistrar.Registration both = register(manifest,
                new ExposurePolicy(true, false, false, true, List.of(WRITE), List.of(WRITE)));
        assertThat(both.registeredNames()).containsExactly(VERIFIED, WRITE);
    }

    // ---------- X1 to X5 on tibyan.writes.allow ----------

    @Test
    void x1_aWildcardAPrefixAndAWrongCaseNameEachFailStartupAsUnknown() {
        for (String entry : List.of("reviewed_*", "reviewed_", "REVIEWED_RECORDPAYMENT", "reviewed_recordpayment")) {
            assertThatThrownBy(() -> register(manifest(), policy(true, List.of(entry))))
                    .as("entry %s", entry)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(ExposurePolicy.WRITES_ALLOW)
                    .hasMessageContaining("'" + entry + "'")
                    .hasMessageContaining("names no capability");
        }
    }

    @Test
    void x2_anUnknownNameFailsStartupNamingThePropertyAndTheEntry() {
        assertThatThrownBy(() -> register(manifest(), policy(true, List.of("reviewed_ghost"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ExposurePolicy.WRITES_ALLOW)
                .hasMessageContaining("'reviewed_ghost'")
                .hasMessageContaining("names no capability in the loaded manifest");
    }

    @Test
    void x3_aListedReadOrVerifiedToolFailsStartupBecauseTheEntryIsInert() {
        assertThatThrownBy(() -> register(manifest(), policy(true, List.of(READ))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ExposurePolicy.WRITES_ALLOW)
                .hasMessageContaining("'" + READ + "'")
                .hasMessageContaining("is kind READ, not WRITE or ACTION")
                .hasMessageContaining("X3");
        assertThatThrownBy(() -> register(manifest(), policy(true, List.of(VERIFIED))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'" + VERIFIED + "'")
                .hasMessageContaining("is kind READ, not WRITE or ACTION");
    }

    @Test
    void x5_aBlankAndADuplicateEntryEachFailStartup() {
        assertThatThrownBy(() -> register(manifest(), policy(true, List.of("", WRITE))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ExposurePolicy.WRITES_ALLOW)
                .hasMessageContaining("entry 1 is blank")
                .hasMessageContaining("X5");
        assertThatThrownBy(() -> register(manifest(), policy(true, List.of(WRITE, ACTION, WRITE))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ExposurePolicy.WRITES_ALLOW)
                .hasMessageContaining("'" + WRITE + "' is listed more than once")
                .hasMessageContaining("X5");
    }

    // ---------- the manifest contract, refused loudly ----------

    @Test
    void aWriteWithoutTheReviewedTierIsRefusedAtStartupNotServedAsVerified() {
        // No backend emits this (core rule 12); a hand-edited manifest might. The runtime never guesses.
        CapabilityDescriptor unsignedWrite = new CapabilityDescriptor("recordPayment", "Records a payment.",
                "com.tibyaan.runtime.spring.WriteGateTest.LedgerService#record", "ENTITY_REF(Receipt)", List.of(), false, false, false,
                CapabilityDescriptor.KIND_WRITE, null, null);
        CapabilitiesManifest manifest = new CapabilitiesManifest("m", "0.1", List.of(unsignedWrite));

        assertThatThrownBy(() -> register(manifest, policy(true, List.of("recordPayment"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'recordPayment'")
                .hasMessageContaining("carries no reviewed tier");
    }

    @Test
    void anUnknownKindIsRefusedAtStartup() {
        CapabilityDescriptor odd = new CapabilityDescriptor("odd", "Odd.", "S#m", "X", List.of(),
                false, false, false, "write", null, null);
        CapabilitiesManifest manifest = new CapabilitiesManifest("m", "0.1", List.of(odd));

        assertThatThrownBy(() -> register(manifest, ExposurePolicy.defaults()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'odd'")
                .hasMessageContaining("kind 'write'")
                .hasMessageContaining("not READ, WRITE or ACTION");
    }

    // ---------- end to end: a served write is the same reflective invoke ----------

    public record Receipt(String note, Long amount) {
    }

    /** Stands in for the legacy service whose write a named human approved. */
    public static class LedgerService {
        final List<Receipt> recorded = new ArrayList<>();

        public Receipt record(String note, Long amount) {
            Receipt receipt = new Receipt(note, amount);
            recorded.add(receipt);
            return receipt;
        }
    }

    @Test
    void rv2_anAllowedWriteInvokesTheRealBeanThroughTheRegisteredCallback() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("ledgerService", LedgerService.class);
            context.registerBean("productService", ProductService.class);
            context.refresh();
            CapabilitiesManifest manifest = new CapabilitiesManifest("m", "0.1", List.of(verified(), reviewedWrite(false)));
            SpringExecutionAdapter adapter = new SpringExecutionAdapter(context, manifest);

            List<ToolCallback> callbacks = new ToolRegistrar(mapper, policy(true, List.of(WRITE)))
                    .register(manifest, adapter);
            ToolCallback write = callbacks.stream()
                    .filter(callback -> callback.getToolDefinition().name().equals(WRITE))
                    .findFirst()
                    .orElseThrow();

            // The MCP client sends amount as a JSON number; the adapter binds it to the declared Long.
            String result = write.call("{\"note\":\"rent\",\"amount\":5}");

            assertThat(mapper.readValue(result, Receipt.class)).isEqualTo(new Receipt("rent", 5L));
            assertThat(context.getBean(LedgerService.class).recorded).containsExactly(new Receipt("rent", 5L));
        }
    }

    @Test
    void rv2_theSameWriteIsNotReachableWhenTheGateIsClosed() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("ledgerService", LedgerService.class);
            context.registerBean("productService", ProductService.class);
            context.refresh();
            CapabilitiesManifest manifest = new CapabilitiesManifest("m", "0.1", List.of(verified(), reviewedWrite(false)));
            SpringExecutionAdapter adapter = new SpringExecutionAdapter(context, manifest);

            List<ToolCallback> callbacks = new ToolRegistrar(mapper, ExposurePolicy.defaults()).register(manifest, adapter);

            assertThat(callbacks).extracting(callback -> callback.getToolDefinition().name()).containsExactly(VERIFIED);
            assertThat(context.getBean(LedgerService.class).recorded).isEmpty();
        }
    }
}
