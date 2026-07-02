package com.tibyaan.runtime.spring;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The documented manifest resolution order (Volume 7 section 4): explicit property, then the
 * classpath convention, then the file convention, then a clear failure. A stub
 * {@link ResourceLoader} controls exactly which locations exist, so the order is tested in
 * isolation, without touching the real classpath or filesystem.
 */
class ManifestResolverTest {

    @Test
    void anExplicitPropertyWins() {
        String explicit = "classpath:/some/where/explicit.json";
        ManifestResolver resolver = new ManifestResolver(
                stubLoader(Set.of(explicit, ManifestResolver.CLASSPATH_LOCATION)));

        Resource resolved = resolver.resolve(explicit);

        assertThat(resolved.getDescription()).contains(explicit);
    }

    @Test
    void anExplicitPropertyThatResolvesToNothingFailsLoudlyInsteadOfFallingThrough() {
        // The classpath default exists, but the explicit property must not silently fall through to
        // it: the host named a location and it was wrong.
        ManifestResolver resolver = new ManifestResolver(
                stubLoader(Set.of(ManifestResolver.CLASSPATH_LOCATION)));

        assertThatThrownBy(() -> resolver.resolve("classpath:/typo.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("classpath:/typo.json")
                .hasMessageContaining("no capabilities manifest exists there");
    }

    @Test
    void withNoExplicitPropertyTheClasspathConventionIsUsed() {
        ManifestResolver resolver = new ManifestResolver(
                stubLoader(Set.of(ManifestResolver.CLASSPATH_LOCATION)));

        Resource resolved = resolver.resolve("");

        assertThat(resolved.getDescription()).contains(ManifestResolver.CLASSPATH_LOCATION);
    }

    @Test
    void withNoClasspathManifestTheFileConventionIsUsed() {
        ManifestResolver resolver = new ManifestResolver(
                stubLoader(Set.of(ManifestResolver.FILE_LOCATION)));

        Resource resolved = resolver.resolve(null);

        assertThat(resolved.getDescription()).contains(ManifestResolver.FILE_LOCATION);
    }

    @Test
    void whenNoManifestExistsAnywhereItFailsWithEverywhereItLooked() {
        ManifestResolver resolver = new ManifestResolver(stubLoader(Set.of()));

        assertThatThrownBy(() -> resolver.resolve("   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no capabilities manifest found")
                .hasMessageContaining(ManifestResolver.CLASSPATH_LOCATION)
                .hasMessageContaining(ManifestResolver.FILE_LOCATION);
    }

    // A ResourceLoader whose resources exist only for the given locations. Present locations get a
    // real (empty) resource; absent ones get a resource that reports exists() == false.
    private static ResourceLoader stubLoader(Set<String> existing) {
        return new ResourceLoader() {
            @Override
            public Resource getResource(String location) {
                if (existing.contains(location)) {
                    return new ByteArrayResource(new byte[0], location);
                }
                return new AbstractResource() {
                    @Override
                    public boolean exists() {
                        return false;
                    }

                    @Override
                    public String getDescription() {
                        return "missing[" + location + "]";
                    }

                    @Override
                    public InputStream getInputStream() throws FileNotFoundException {
                        throw new FileNotFoundException(location);
                    }
                };
            }

            @Override
            public ClassLoader getClassLoader() {
                return getClass().getClassLoader();
            }
        };
    }
}
