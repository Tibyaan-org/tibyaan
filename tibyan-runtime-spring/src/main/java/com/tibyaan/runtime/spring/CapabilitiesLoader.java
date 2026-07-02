package com.tibyaan.runtime.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Reads the generated capabilities manifest from a {@link Resource}. When the manifest is
 * absent, the runtime starts with no tools rather than failing, so adding the dependency is
 * always safe.
 */
public final class CapabilitiesLoader {

    private static final Logger log = LoggerFactory.getLogger(CapabilitiesLoader.class);

    private final ObjectMapper objectMapper;

    public CapabilitiesLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CapabilitiesManifest load(Resource resource) {
        if (resource == null || !resource.exists()) {
            log.warn("Tibyaan: no capabilities manifest found at {}; no tools will be registered", resource);
            return CapabilitiesManifest.empty();
        }
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readValue(in, CapabilitiesManifest.class);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read capabilities manifest from " + resource, e);
        }
    }
}
