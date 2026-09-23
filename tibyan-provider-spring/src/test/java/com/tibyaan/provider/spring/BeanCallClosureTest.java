package com.tibyaan.provider.spring;

import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.provider.spring.draft.DraftEntry;
import com.tibyaan.provider.spring.draft.DraftLabel;
import com.tibyaan.provider.spring.draft.DraftReport;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DL-1.1, the bean-call closure of the READ label (Volume 5 section 4.17.14), fixtures LC-1 to LC-7. Each case is
 * the shape one of the as-backend false READs took (as-backend-trial.md section 10.2), reduced to a fixture.
 */
class BeanCallClosureTest {

    private static final Path FIXTURE = Path.of("src", "test", "resources", "fixtures", "bean-closure");

    private final DraftReport report = new SpringCapabilityProvider().draft(new ProviderInput(FIXTURE));

    private DraftEntry entry(String method) {
        return report.drafts().stream().filter(d -> d.name().equals("TicketServiceImpl_" + method)).findFirst()
                .orElseThrow(() -> new AssertionError("no draft for " + method));
    }

    @Test
    void lc1_aReadOnlyBeanBehindAnInterfaceStaysRead() {
        assertThat(entry("search").label()).isEqualTo(DraftLabel.READ);
    }

    @Test
    void lc2_aBeanThatSavesThroughAHelperIsWriteWithTheChain() {
        DraftEntry allocate = entry("allocate");
        assertThat(allocate.label()).isEqualTo(DraftLabel.WRITE);
        assertThat(allocate.reason()).contains("TicketServiceImpl#allocate -> Counter#next").contains("'save'");
    }

    @Test
    void lc3_aJmsPublisherBehindABeanIsWrite() {
        DraftEntry download = entry("download");
        assertThat(download.label()).isEqualTo(DraftLabel.WRITE);
        assertThat(download.reason()).contains("JmsTemplate").contains("C3");
    }

    @Test
    void lc4_aFeignGetIsReadWithTheCallNamedAndAPostIsWrite() {
        assertThat(entry("remoteGet").label()).isEqualTo(DraftLabel.READ);
        assertThat(entry("remoteGet").reason()).contains("RemoteTickets.get (Feign GET)");
        assertThat(entry("remoteCreate").label()).isEqualTo(DraftLabel.WRITE);
    }

    @Test
    void lc5_reflectionIsUnknown() {
        DraftEntry dispatch = entry("dispatch");
        assertThat(dispatch.label()).isEqualTo(DraftLabel.UNKNOWN);
        assertThat(dispatch.reason()).contains("reflective").contains("C5");
    }

    @Test
    void lc6_aChainPastTheBoundIsUnknown() {
        DraftEntry deep = entry("deep");
        assertThat(deep.label()).isEqualTo(DraftLabel.UNKNOWN);
        assertThat(deep.reason()).contains("bound of 4");
    }

    @Test
    void lc7_aMapStructMapperStaysRead() {
        assertThat(entry("title").label()).isEqualTo(DraftLabel.READ);
    }
}
