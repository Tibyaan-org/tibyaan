package com.tibyaan.maven;

import com.tibyaan.backend.mcp.McpBackend;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.semantic.ValidationException;
import com.tibyaan.pipeline.TibyanCompiler;
import com.tibyaan.plugin.GenerationContext;
import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.provider.spring.ParseSkip;
import com.tibyaan.provider.spring.ProviderException;
import com.tibyaan.provider.spring.ScanReport;
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
import java.util.List;

/**
 * Generates the capabilities manifest onto the module's classpath at build time, so an app no
 * longer runs {@code tibyan generate} by hand and copies the result into its resources (Volume 11,
 * Volume 7 section 8 gap 2).
 *
 * <p>This Mojo is Maven wiring only. It invokes the same pipeline the CLI invokes, then
 * {@link TibyanCompiler#generate} with {@link McpBackend}. It contains no generation logic of its own.
 *
 * <p>By default the Mojo is resilient, so adopting the plugin can never break a build on an
 * unparseable file (Volume 11 section 3.1.1). It runs the same resilient
 * {@link SpringCapabilityProvider#scan} the CLI's {@code tibyan scan} runs: an unparseable file is
 * skipped and named at WARN, never fatal, and the manifest is generated from what was discovered.
 * A partial manifest is disclosed loudly (every skip is logged), never silent. The output is
 * byte-identical to {@code tibyan scan} for the same source, and identical to {@code tibyan generate}
 * whenever the source is fully parseable. Setting {@code strict=true} (or {@code -Dtibyan.strict=true})
 * opts back into the strict {@link TibyanCompiler#scan} path, which fails the build on any unparseable
 * file, for adopters who want their build to gate on a fully-parseable tree.
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

    /**
     * When true, run the strict provider path: any unparseable source file or hard failure fails the
     * build. Off by default so adopting the plugin cannot break a build on an unparseable file
     * (Volume 11 section 3.1.1); the default resilient path skips and names such files instead.
     */
    @Parameter(defaultValue = "false", property = "tibyan.strict")
    private boolean strict;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Path source = sourceRoot.toPath();
        Path work = workDirectory.toPath();
        Path classpathManifest = outputDirectory.toPath().resolve(MANIFEST_PATH);

        try {
            TibyanCompiler compiler = new TibyanCompiler();
            SpringCapabilityProvider provider = new SpringCapabilityProvider();

            CapabilityModel ir;
            if (strict) {
                // Strict opt-in: the provider's strict path refuses any unparseable file, so the
                // build fails rather than shipping a manifest from a partial read.
                ir = compiler.scan(provider, new ProviderInput(source));
            } else {
                // Default: resilient. Adopting the plugin must never break the build on an
                // unparseable file. Skip and name them, then generate from what was discovered.
                ScanReport report = provider.scan(new ProviderInput(source));
                reportSkips(report);
                ir = report.model();
            }

            compiler.generate(new McpBackend(), ir, new GenerationContext(work));

            Files.createDirectories(classpathManifest.getParent());
            Files.copy(work.resolve(MANIFEST_PATH), classpathManifest, StandardCopyOption.REPLACE_EXISTING);

            if (ir.capabilities().isEmpty()) {
                getLog().warn("Tibyaan: generated 0 capabilities; the MCP server will expose no tools. "
                        + "Run 'tibyan scan " + source + "' to see why.");
            }
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

    /**
     * Logs every file the resilient scan could not parse, so a partial manifest is disclosed loudly
     * and never masquerades as complete (the collect-and-continue discipline, Volume 5 section 4.3).
     */
    private void reportSkips(ScanReport report) {
        List<ParseSkip> skips = report.parseSkips();
        if (skips.isEmpty()) {
            return;
        }
        getLog().warn("Tibyaan: " + skips.size() + " source file(s) could not be parsed and were "
                + "skipped, so the generated manifest is partial. Set -Dtibyan.strict=true to fail "
                + "the build on any unparseable file instead.");
        for (ParseSkip skip : skips) {
            getLog().warn("  skipped " + skip.describe());
        }
    }
}
