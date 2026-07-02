package com.tibyaan.runtime.spring;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the generated capabilities manifest without requiring a launch property (Volume 7
 * section 4). The resolution order is small, documented, and fixed:
 *
 * <ol>
 *   <li>an explicit {@code tibyan.capabilities} location, if the host set one. If it is set but
 *       resolves to nothing, that is a loud failure, not a silent fall-through to the defaults:
 *       the host named a location and it was wrong.</li>
 *   <li>the conventional classpath location {@value #CLASSPATH_LOCATION}, where an app that ships
 *       the manifest as a normal resource carries it.</li>
 *   <li>the conventional file location {@value #FILE_LOCATION}, relative to the working
 *       directory, for running against a manifest sitting next to the app.</li>
 * </ol>
 *
 * <p>If none of these holds a manifest, the resolver fails with a message that lists everywhere it
 * looked. The runtime is meant to host generated tools, so finding no manifest anywhere is a real
 * misconfiguration worth surfacing, not a reason to start silently serving nothing.
 */
final class ManifestResolver {

    /** The conventional classpath location an app ships its generated manifest at. */
    static final String CLASSPATH_LOCATION = "classpath:/tibyan/capabilities.json";

    /** The conventional file location, relative to the working directory. */
    static final String FILE_LOCATION = "file:tibyan/capabilities.json";

    private final ResourceLoader resourceLoader;

    ManifestResolver(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Resolves the manifest resource, or throws with a clear message if none is found.
     *
     * @param explicitLocation the {@code tibyan.capabilities} value, or null/blank if unset
     */
    Resource resolve(String explicitLocation) {
        List<String> tried = new ArrayList<>();

        if (explicitLocation != null && !explicitLocation.isBlank()) {
            String location = explicitLocation.trim();
            Resource resource = resourceLoader.getResource(location);
            if (resource.exists()) {
                return resource;
            }
            throw new IllegalStateException(
                    "Tibyaan: tibyan.capabilities was set to '" + location + "' but no capabilities "
                    + "manifest exists there. Point it at the manifest produced by "
                    + "'tibyan generate', or remove the property to use the default locations ("
                    + CLASSPATH_LOCATION + ", then " + FILE_LOCATION + ").");
        }

        for (String location : List.of(CLASSPATH_LOCATION, FILE_LOCATION)) {
            Resource resource = resourceLoader.getResource(location);
            if (resource.exists()) {
                return resource;
            }
            tried.add(location);
        }

        throw new IllegalStateException(
                "Tibyaan: no capabilities manifest found. Looked at the tibyan.capabilities property "
                + "(unset), then " + tried + ". Generate one with 'tibyan generate --backend mcp' and "
                + "ship it on the classpath at tibyan/capabilities.json, or set tibyan.capabilities to "
                + "its location.");
    }
}
