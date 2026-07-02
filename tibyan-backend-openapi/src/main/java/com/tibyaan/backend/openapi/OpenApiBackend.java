package com.tibyaan.backend.openapi;

import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.plugin.Backend;
import com.tibyaan.plugin.GenerationContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The OpenAPI backend (Volume 6 section 4): it reads only the {@link CapabilityModel} and writes one
 * OpenAPI 3.0 document, {@code openapi.json}, describing the capabilities as read-by-id GET
 * operations. It is the second backend, and it proves the one-IR-many-outputs claim: the same IR
 * that feeds the MCP backend feeds this one, with no change to the core, the plugin API, or any
 * provider.
 *
 * <p>It never learns where the IR came from and never calls a framework (ADR-0004). The document is
 * written by a small deterministic JSON writer, so generation is deterministic and golden-file
 * tested, and no serialization library reaches the backend. Schemas honor the partiality-in-schema
 * rule (section 4.1): a reader is never more confident about the wire shape than the IR justifies.
 */
public final class OpenApiBackend implements Backend {

    private static final String DOCUMENT_PATH = "openapi.json";

    @Override
    public void generate(CapabilityModel ir, GenerationContext ctx) {
        Path file = ctx.outputRoot().resolve(DOCUMENT_PATH);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, OpenApiDocument.toJson(ir), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write " + file, e);
        }
    }
}
