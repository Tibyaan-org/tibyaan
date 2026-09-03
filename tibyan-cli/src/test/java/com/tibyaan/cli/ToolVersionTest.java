package com.tibyaan.cli;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CLI version is derived from the build, so these assertions deliberately name no version: a
 * literal here would need editing on every release, which is the drift being fixed. The constant
 * had sat at one value across releases before this.
 */
class ToolVersionTest {

    @Test
    void resolvesTheVersionFromTheFilteredResource() {
        String version = ToolVersion.read();

        // "unknown" means the resource was missing; a leftover delimiter means Maven copied it
        // without substituting, which would print a raw token as if it were a version.
        assertThat(version).isNotEqualTo(ToolVersion.UNKNOWN_VERSION);
        assertThat(version).doesNotContain("${").doesNotContain("@");
        assertThat(version).matches("[0-9]+[.][0-9]+[.][0-9]+(-SNAPSHOT)?");
    }

    @Test
    void theCommandConstantUsesThatSameResolvedValue() {
        assertThat(TibyanCli.TOOL_VERSION).isEqualTo(ToolVersion.read());
    }

    @Test
    void versionProviderReportsTheResolvedVersion() {
        String[] lines = new TibyanVersionProvider().getVersion();

        assertThat(lines).hasSize(1);
        assertThat(lines[0]).isEqualTo("tibyan " + ToolVersion.read());
        assertThat(lines[0]).doesNotContain(ToolVersion.UNKNOWN_VERSION);
    }
}
