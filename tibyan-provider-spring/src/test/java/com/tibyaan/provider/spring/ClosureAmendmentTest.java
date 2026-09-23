package com.tibyaan.provider.spring;

import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.provider.spring.draft.DraftEntry;
import com.tibyaan.provider.spring.draft.DraftLabel;
import com.tibyaan.provider.spring.draft.DraftReport;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** DL-1.2, the closed-set amendment of the bean-call closure (Volume 5 section 4.17.16), fixtures LA-1 to LA-6. */
class ClosureAmendmentTest {

    private static final Path FIXTURE = Path.of("src", "test", "resources", "fixtures", "closure-amendment");

    private final DraftReport report = new SpringCapabilityProvider().draft(new ProviderInput(FIXTURE));

    private DraftEntry entry(String method) {
        return report.drafts().stream().filter(d -> d.name().equals("NoteServiceImpl_" + method)).findFirst()
                .orElseThrow(() -> new AssertionError("no draft for " + method));
    }

    @Test
    void la1_aConfiguredSubclassOfAnAbstractConfigIsABeanAndItsGetterIsRead() {
        assertThat(entry("prefix").label()).isEqualTo(DraftLabel.READ);
    }

    @Test
    void la2_aProviderLookupIsReadAndASaveOnTheProvidedBeanIsWrite() {
        assertThat(entry("repo").label()).isEqualTo(DraftLabel.READ);
        DraftEntry save = entry("saveViaProvider");
        assertThat(save.label()).isEqualTo(DraftLabel.WRITE);
        assertThat(save.reason()).contains("'save'");
    }

    @Test
    void la3_gsonAndPasswordEncoderArePure() {
        assertThat(entry("json").label()).isEqualTo(DraftLabel.READ);
        assertThat(entry("matches").label()).isEqualTo(DraftLabel.READ);
    }

    @Test
    void la4_aFluentGetIsReadWithTheCallNamedAndAPostIsWrite() {
        DraftEntry fetch = entry("fetch");
        assertThat(fetch.label()).isEqualTo(DraftLabel.READ);
        assertThat(fetch.reason()).contains("RestClient.get");
        DraftEntry post = entry("post");
        assertThat(post.label()).isEqualTo(DraftLabel.WRITE);
        assertThat(post.reason()).contains("POST");
    }

    @Test
    void la5_ambiguousImplementationsTakeTheWorstLabel() {
        assertThat(entry("find").label()).isEqualTo(DraftLabel.READ);
        DraftEntry write = entry("write");
        assertThat(write.label()).isEqualTo(DraftLabel.WRITE);
        assertThat(write.reason()).contains("LocalNoteWriter WRITE").contains("LoggingNoteWriter READ");
    }

    @Test
    void la6_sameArityOverloadsAreResolvedByTheArgumentTypeSinceDl13() {
        // Under DL-1.2 this stayed UNKNOWN; DL-1.3 B2 reads the Long parameter and picks the Long overload.
        DraftEntry describe = entry("describe");
        assertThat(describe.label()).isEqualTo(DraftLabel.READ);
    }
}
