package com.tibyaan.provider.spring.draft;

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
 * Writes {@code tibyan/drafts.json} (spike DL-0): the reviewable file of draft candidates, beside the source
 * it describes. The file is deterministic for a given source tree: entries are sorted by the extractor, the
 * counts are derived from them, and the line separator is fixed to {@code \n} so the same tree yields the
 * same bytes on every platform. Nothing in this file is served by anything yet; serving an approved draft is
 * the spike's question Q3 and, if it passes, the next phase's work.
 */
public final class DraftsFile {

    /** The path of the drafts file relative to the scanned source root. */
    public static final String RELATIVE_PATH = "tibyan/drafts.json";

    /** The file format version, bumped only when the entry shape changes. */
    public static final int VERSION = 1;

    private static final ObjectWriter WRITER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.ALWAYS)
            .writer(new DefaultPrettyPrinter()
                    .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                    .withArrayIndenter(new DefaultIndenter("  ", "\n")));

    private DraftsFile() {
    }

    /** The file's JSON, as the bytes {@link #write} writes. */
    public static String render(DraftReport report) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", VERSION);
        root.put("counts", report.counts());
        root.put("drafts", report.drafts());
        try {
            return WRITER.writeValueAsString(root) + "\n";
        } catch (IOException e) {
            throw new UncheckedIOException("could not render " + RELATIVE_PATH, e);
        }
    }

    /** Writes the drafts file to the given path, creating parent directories as needed. */
    public static void write(Path file, DraftReport report) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, render(report));
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + file, e);
        }
    }

    /** The default location of the drafts file under a source root. */
    public static Path defaultLocation(Path sourceRoot) {
        return sourceRoot.resolve(RELATIVE_PATH);
    }

    static List<DraftEntry> copy(List<DraftEntry> entries) {
        return List.copyOf(entries);
    }
}
