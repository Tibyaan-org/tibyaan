package com.tibyaan.core.ir;

import java.util.Map;

/**
 * Free form, non semantic key and value data (Volume 4 section 3.6).
 *
 * <p>Metadata carries no semantic weight: it is for things like the source file a model
 * element came from. Backends may read it for provenance, but the IR's meaning never depends
 * on it.
 *
 * @param entries the metadata entries, never null
 */
public record Metadata(Map<String, String> entries) {

    public Metadata {
        entries = entries == null ? Map.of() : Map.copyOf(entries);
    }

    /** Empty metadata, the common default. */
    public static Metadata empty() {
        return new Metadata(Map.of());
    }
}
