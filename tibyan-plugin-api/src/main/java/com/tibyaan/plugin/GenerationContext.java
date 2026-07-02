package com.tibyaan.plugin;

import java.nio.file.Path;

/**
 * What a {@link Backend} receives alongside the IR: where to write generated output.
 *
 * <p>Kept minimal for the MVP: an output root. A backend writes its files under this root and
 * returns nothing to the caller. The context never carries the IR (the backend is handed that
 * separately) and never carries framework handles.
 *
 * @param outputRoot the directory under which the backend writes generated files
 */
public record GenerationContext(Path outputRoot) {

    public GenerationContext {
        if (outputRoot == null) {
            throw new IllegalArgumentException("outputRoot must not be null");
        }
    }
}
