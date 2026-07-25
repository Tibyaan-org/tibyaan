package com.tibyaan.provider.spring.attest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads the adopter's committed {@code tibyan/attestations.json} (Volume 5 section 4.15.3), the reviewable
 * file that is the record. The file lives at {@code <sourceRoot>/tibyan/attestations.json}, beside the
 * source it describes, so it survives re-scans and rides the team's version control. An absent file means
 * no attestations, never an error (the phase is additive: fixture ATT-11). A present-but-malformed file is
 * a located failure surfaced to the caller, never a silent skip.
 */
public final class AttestationFile {

    /** The path of the record relative to the scanned source root. */
    public static final String RELATIVE_PATH = "tibyan/attestations.json";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private AttestationFile() {
    }

    /** Whether a record file exists under this source root. */
    public static boolean exists(Path sourceRoot) {
        return Files.isRegularFile(sourceRoot.resolve(RELATIVE_PATH));
    }

    /**
     * Reads the record entries under this source root, or an empty list when no file exists.
     *
     * @throws AttestationFileException if the file exists but cannot be read or parsed as a JSON array of
     *                                  attestation entries
     */
    public static List<AttestationRecord> read(Path sourceRoot) {
        Path file = sourceRoot.resolve(RELATIVE_PATH);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            AttestationRecord[] entries = MAPPER.readValue(Files.readAllBytes(file), AttestationRecord[].class);
            return List.of(entries);
        } catch (IOException e) {
            throw new AttestationFileException("could not read or parse " + RELATIVE_PATH + ": "
                    + e.getMessage(), e);
        }
    }

    /** Writes the record entries back to the file (the {@code tibyan attest} stamp action). */
    public static void write(Path sourceRoot, List<AttestationRecord> records) {
        Path file = sourceRoot.resolve(RELATIVE_PATH);
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.write(file, MAPPER.writeValueAsBytes(records));
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + RELATIVE_PATH, e);
        }
    }

    /** A located failure reading the record file (a syntax error, an unreadable file). */
    public static final class AttestationFileException extends RuntimeException {
        AttestationFileException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
