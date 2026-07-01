package com.tibyaan.maven;

import com.tibyaan.backend.mcp.McpBackend;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.semantic.ValidationException;
import com.tibyaan.pipeline.TibyanCompiler;
import com.tibyaan.plugin.GenerationContext;
import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.provider.spring.ProviderException;
import com.tibyaan.provider.spring.SpringCapabilityProvider;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Generates the capabilities manifest onto the module's classpath at build time, so an app no
 * longer runs {@code tibyan generate} by hand and copies the result into its resources (Volume 11,
 * Volume 7 section 8 gap 2).
 *
 * <p>This Mojo is Maven wiring only. It invokes the exact pipeline the CLI invokes:
 * {@link TibyanCompiler#scan} with {@link SpringCapabilityProvider}, then
 * {@link TibyanCompiler#generate} with {@link McpBackend}. The manifest it produces is therefore
 * byte-identical to {@code tibyan generate} output by construction, because it is the same code, not
 * a second generation path. It contains no generation logic of its own.
 *
 * <p>The backend writes the full generated output (the tool source plus the manifest) into an
 * intermediate directory under {@code target}; this Mojo then copies only the manifest onto the
 * classpath output. The intermediate tool source is a different materialization, not needed for the
 * in-context runtime, and is kept out of the app's classpath.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public final class GenerateManifestMojo extends AbstractMojo {

    private static final String MANIFEST_PATH = "tibyan/capabilities.json";

    /**
     * The source root to scan. Defaults to the module base directory, matching how the CLI is run
     * ({@code tibyan scan <module>}), so the module name in the manifest is the module directory
     * name, exactly as the CLI produces it.
     */
    @Parameter(defaultValue = "${project.basedir}", property = "tibyan.sourceRoot", required = true)
    private File sourceRoot;

    /** The classpath output directory the manifest is written into. Defaults to target/classes. */
    @Parameter(defaultValue = "${project.build.outputDirectory}", property = "tibyan.outputDirectory", required = true)
    private File outputDirectory;

    /** The intermediate directory the backend writes its full output into. Defaults to target/tibyan-generate. */
    @Parameter(defaultValue = "${project.build.directory}/tibyan-generate", property = "tibyan.workDirectory", required = true)
    private File workDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Path source = sourceRoot.toPath();
        Path work = workDirectory.toPath();
        Path classpathManifest = outputDirectory.toPath().resolve(MANIFEST_PATH);

        try {
            TibyanCompiler compiler = new TibyanCompiler();
            CapabilityModel ir = compiler.scan(new SpringCapabilityProvider(), new ProviderInput(source));
            compiler.generate(new McpBackend(), ir, new GenerationContext(work));

            Files.createDirectories(classpathManifest.getParent());
            Files.copy(work.resolve(MANIFEST_PATH), classpathManifest, StandardCopyOption.REPLACE_EXISTING);

            getLog().info("Tibyaan: generated " + ir.capabilities().size()
                    + " capability(ies) to " + classpathManifest);
        } catch (ValidationException e) {
            // Validation is the pipeline's gate; a build must fail loudly, not ship a bad manifest.
            throw new MojoFailureException("Tibyaan: IR validation failed: " + e.getMessage(), e);
        } catch (ProviderException e) {
            throw new MojoExecutionException("Tibyaan: provider error scanning " + source + ": "
                    + e.getMessage(), e);
        } catch (IOException e) {
            throw new MojoExecutionException("Tibyaan: failed to write manifest to " + classpathManifest, e);
        }
    }
}
