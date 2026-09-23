package com.tibyaan.provider.spring;

import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.provider.spring.draft.DraftEntry;
import com.tibyaan.provider.spring.draft.DraftLabel;
import com.tibyaan.provider.spring.draft.DraftReport;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** DL-1.3, bodiless accessors and overload disambiguation (Volume 5 section 4.17.18), fixtures LB-1 to LB-5. */
class OverloadClosureTest {

    private static final Path FIXTURE = Path.of("src", "test", "resources", "fixtures", "overload-closure");

    private final DraftReport report = new SpringCapabilityProvider().draft(new ProviderInput(FIXTURE));

    private DraftEntry entry(String method) {
        return report.drafts().stream().filter(d -> d.name().equals("TaskServiceImpl_" + method)).findFirst()
                .orElseThrow(() -> new AssertionError("no draft for " + method));
    }

    @Test
    void lb1_bodilessAccessorsOfDeclaredFieldsAreRead() {
        assertThat(entry("defaultOwner").label()).isEqualTo(DraftLabel.READ);
        assertThat(entry("strict").label()).isEqualTo(DraftLabel.READ);
    }

    @Test
    void lb2_theParametersDeclaredTypePicksTheOverload() {
        assertThat(entry("load").label()).isEqualTo(DraftLabel.READ);
        DraftEntry store = entry("store");
        assertThat(store.label()).isEqualTo(DraftLabel.WRITE);
        assertThat(store.reason()).contains("TaskEngine#process").contains("'save'");
    }

    @Test
    void lb3_anEnumConstantPicksTheEnumOverload() {
        assertThat(entry("onCreated").label()).isEqualTo(DraftLabel.READ);
    }

    @Test
    void lb4_aNullAgainstTwoReferenceOverloadsStaysUnknown() {
        DraftEntry describe = entry("describeNothing");
        assertThat(describe.label()).isEqualTo(DraftLabel.UNKNOWN);
        assertThat(describe.reason()).contains("2 match the argument types [null]");
    }

    @Test
    void lb5_aMethodCallArgumentStaysUnknownNamingIt() {
        DraftEntry describe = entry("describeLoaded");
        assertThat(describe.label()).isEqualTo(DraftLabel.UNKNOWN);
        assertThat(describe.reason()).contains("argument 1 ('engine.process(id)') has no readable static type");
    }
}
