package com.tibyaan.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the build plugin's resilient-by-default behavior (Volume 11 section 3.1.1). The point of
 * the phase: adopting the plugin must never break a build on an unparseable file. Each test scans a
 * source tree that mixes one discoverable entity with one genuinely unparseable file.
 */
class GenerateManifestMojoTest {

    @Test
    void resilientDefaultGeneratesFromDiscoverableSourceAndSkipsUnparseableFile(@TempDir Path dir) throws Exception {
        writeMixedSourceTree(dir);
        Path classes = dir.resolve("target/classes");
        Path work = dir.resolve("target/tibyan-generate");

        GenerateManifestMojo mojo = newMojo(dir, classes, work, false);

        // The malformed file must not abort the build; discovery still finds the clean entity.
        mojo.execute();

        Path manifest = classes.resolve("tibyan/capabilities.json");
        assertThat(manifest).exists();
        assertThat(Files.readString(manifest)).contains("getProduct");
    }

    @Test
    void strictOptInFailsTheBuildOnAnUnparseableFile(@TempDir Path dir) throws Exception {
        writeMixedSourceTree(dir);
        Path classes = dir.resolve("target/classes");
        Path work = dir.resolve("target/tibyan-generate");

        GenerateManifestMojo mojo = newMojo(dir, classes, work, true);

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("provider error");
        assertThat(classes.resolve("tibyan/capabilities.json")).doesNotExist();
    }

    /**
     * Writes a minimal rule 4.1 discovery target (an {@code @Entity} with an {@code @Id}, a Spring
     * Data repository of it, and an {@code @Service} that returns it by id) alongside one file that
     * is not valid Java. The strict path aborts on the bad file; the resilient path skips it.
     */
    private static void writeMixedSourceTree(Path dir) throws Exception {
        Path pkg = dir.resolve("src/main/java/com/example");
        Files.createDirectories(pkg);

        Files.writeString(pkg.resolve("Product.java"), """
                package com.example;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;
                @Entity
                public class Product {
                    @Id private Long id;
                    private String name;
                    public Long getId() { return id; }
                    public String getName() { return name; }
                }
                """);

        Files.writeString(pkg.resolve("ProductRepository.java"), """
                package com.example;
                import org.springframework.data.jpa.repository.JpaRepository;
                public interface ProductRepository extends JpaRepository<Product, Long> {
                }
                """);

        Files.writeString(pkg.resolve("ProductService.java"), """
                package com.example;
                import org.springframework.stereotype.Service;
                @Service
                public class ProductService {
                    private final ProductRepository repository;
                    public ProductService(ProductRepository repository) { this.repository = repository; }
                    public Product findById(Long id) { return repository.findById(id).orElse(null); }
                }
                """);

        Files.writeString(pkg.resolve("Broken.java"), """
                package com.example;
                public class Broken {
                    public void m() {
                        this is definitely not valid java;
                    }
                }
                """);
    }

    private static GenerateManifestMojo newMojo(Path source, Path outputDir, Path workDir, boolean strict)
            throws Exception {
        GenerateManifestMojo mojo = new GenerateManifestMojo();
        setField(mojo, "sourceRoot", source.toFile());
        setField(mojo, "outputDirectory", outputDir.toFile());
        setField(mojo, "workDirectory", workDir.toFile());
        setField(mojo, "strict", strict);
        return mojo;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
