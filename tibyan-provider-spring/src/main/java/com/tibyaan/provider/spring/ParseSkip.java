package com.tibyaan.provider.spring;

/**
 * One source file the provider could not parse, recorded so a scan can continue past it without
 * silently dropping it (Volume 5 section 4, phase E1).
 *
 * <p>A skip is loud, not silent: the file is named and the reason is located (the JavaParser
 * problems, or an I/O failure). Collect-and-continue relocates the loudness from "abort the whole
 * scan on the first bad file" to "name every bad file and keep going". A partial scan can never
 * masquerade as a complete one, because every file it could not read is listed here.
 *
 * @param path   the file that could not be parsed, as walked (relative or absolute as given)
 * @param reason why it could not be parsed, for example the parser problems or an I/O message
 */
public record ParseSkip(String path, String reason) {

    public ParseSkip {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("a parse skip must name its file");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("a parse skip must state its reason");
        }
    }

    /** A one-line rendering for diagnostics, {@code <path>: <reason>}. */
    public String describe() {
        return path + ": " + reason;
    }
}
