package com.tibyaan.provider.spring.draft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes {@code tibyan/drafts.json} (spike DL-0; the record of phase DL-1, Volume 5 section 4.17.2):
 * the reviewable file of draft candidates, beside the source it describes, committed and reviewed through the
 * team's own version control exactly as {@code tibyan/attestations.json} is. The file is deterministic for a
 * given source tree: entries are ordered by the extractor (stale ones after, in their existing order), the
 * counts are derived from them, and the line separator is fixed to {@code \n} so the same tree yields the same
 * bytes on every platform.
 *
 * <p>The writer omits what a fresh entry does not have: {@code capability}, {@code reviewedBy},
 * {@code reviewedOn} and {@code previousLabel} when null, and {@code stale} when false, so a fresh file is
 * DL-0's shape plus {@code descriptionOrigin} and {@code fingerprint}. The reader tolerates unknown fields, so
 * a reviewer's typo surfaces as a located intake refusal rather than a parse abort of the whole file.
 */
public final class DraftsFile {

    /** The path of the drafts file relative to the scanned source root. */
    public static final String RELATIVE_PATH = "tibyan/drafts.json";

    /** The file format version, bumped only when the entry shape changes. */
    public static final int VERSION = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.ALWAYS);

    private static final ObjectWriter WRITER = MAPPER
            .writer(new DefaultPrettyPrinter()
                    .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                    .withArrayIndenter(new DefaultIndenter("  ", "\n")));

    private DraftsFile() {
    }

    /** The file's JSON, as the bytes {@link #write(Path, DraftReport)} writes. */
    public static String render(DraftReport report) {
        return render(report.drafts(), report.counts());
    }

    /** The file's JSON for a bare entry list, the counts derived from it. */
    public static String render(List<DraftEntry> entries) {
        return render(entries, DraftReport.count(entries));
    }

    private static String render(List<DraftEntry> entries, Map<String, Integer> counts) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", VERSION);
        root.put("counts", counts);
        root.put("drafts", entries);
        try {
            return WRITER.writeValueAsString(root) + "\n";
        } catch (IOException e) {
            throw new UncheckedIOException("could not render " + RELATIVE_PATH, e);
        }
    }

    /** Writes the drafts file to the given path, creating parent directories as needed. */
    public static void write(Path file, DraftReport report) {
        writeText(file, render(report));
    }

    /** Writes a bare entry list as a drafts file, the counts derived from it. */
    public static void write(Path file, List<DraftEntry> entries) {
        writeText(file, render(entries));
    }

    private static void writeText(Path file, String text) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, text);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + file, e);
        }
    }

    /**
     * Reads the entries of a drafts file. An absent file is no entries, never an error; a present file that
     * cannot be read or parsed is a located failure surfaced to the caller, never a silent skip. Unknown fields
     * are tolerated.
     *
     * @throws DraftsFileException if the file exists but cannot be read or parsed
     */
    public static List<DraftEntry> read(Path file) {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            FileShape shape = MAPPER.readValue(Files.readAllBytes(file), FileShape.class);
            return shape.drafts() == null ? List.of() : List.copyOf(shape.drafts());
        } catch (IOException | IllegalArgumentException e) {
            throw new DraftsFileException("could not read or parse " + file + ": " + e.getMessage(), e);
        }
    }

    /** Whether a drafts file exists at its default location under this source root. */
    public static boolean exists(Path sourceRoot) {
        return Files.isRegularFile(defaultLocation(sourceRoot));
    }

    /** The default location of the drafts file under a source root. */
    public static Path defaultLocation(Path sourceRoot) {
        return sourceRoot.resolve(RELATIVE_PATH);
    }

    static List<DraftEntry> copy(List<DraftEntry> entries) {
        return List.copyOf(entries);
    }

    // The file's root object as read: the version and counts are informational (counts are derived on every
    // write), only the entries carry state.
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FileShape(Integer version, Map<String, Integer> counts, List<DraftEntry> drafts) {
    }

    /** A located failure reading the drafts file (a syntax error, an unreadable file, a malformed entry). */
    public static final class DraftsFileException extends RuntimeException {
        DraftsFileException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
