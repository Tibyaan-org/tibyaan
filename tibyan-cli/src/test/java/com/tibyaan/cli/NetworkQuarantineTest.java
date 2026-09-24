package com.tibyaan.cli;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Volume 16 section 7, the quarantine: the only code in the tool that opens a network connection is the describe
 * package of the CLI. Every other main source of every module is free of the JDK and Java HTTP client types, so
 * scan, draft, generate, attest, validate and the Maven plugin stay offline by construction.
 */
class NetworkQuarantineTest {

    private static final List<String> NETWORK_MARKERS = List.of("java.net.http", "HttpURLConnection", "URL.openConnection");

    @Test
    void onlyTheDescribePackageReferencesANetworkClient() throws IOException {
        Path reactor = Path.of("..").toAbsolutePath().normalize();
        List<String> offenders;
        try (Stream<Path> files = Files.walk(reactor)) {
            offenders = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/tibyan-cli/src/main/java/com/tibyaan/cli/describe/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/examples/"))
                    .filter(p -> {
                        try {
                            String source = Files.readString(p);
                            return NETWORK_MARKERS.stream().anyMatch(source::contains);
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .map(p -> reactor.relativize(p).toString())
                    .toList();
        }
        assertThat(offenders).as("main sources referencing a network client outside the describe package").isEmpty();
    }
}
