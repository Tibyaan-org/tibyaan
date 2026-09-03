package com.tibyaan.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads the CLI's own version from a resource filtered at build time, so no release step has to
 * remember to edit a literal. See {@code tibyan/cli-version.properties}.
 */
final class ToolVersion {

    /** Classpath location of the build-filtered file carrying this module's Maven version. */
    static final String VERSION_RESOURCE = "/tibyan/cli-version.properties";

    /**
     * Reported when the version resource is missing or unfiltered, which happens only in a broken or
     * hand-assembled classpath. Deliberately not a plausible-looking version: a wrong number that
     * looks real is worse than one that is obviously a fallback.
     */
    static final String UNKNOWN_VERSION = "unknown";

    private ToolVersion() {
    }

    static String read() {
        try (InputStream in = ToolVersion.class.getResourceAsStream(VERSION_RESOURCE)) {
            if (in == null) {
                return UNKNOWN_VERSION;
            }
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version");
            if (version == null || version.isBlank() || version.contains("${") || version.contains("@")) {
                // An unsubstituted delimiter means filtering did not run; report it as unknown rather
                // than printing a raw token as if it were a version.
                return UNKNOWN_VERSION;
            }
            return version.trim();
        } catch (IOException e) {
            return UNKNOWN_VERSION;
        }
    }
}
