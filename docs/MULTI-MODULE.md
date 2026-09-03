# Surveying and adopting a multi-module application

You do not scan a multi-module application one module at a time. You point Tibyaan at the whole application once, and it produces one manifest that covers every module. This page explains how scan scope works, how to preview a whole reactor read-only, and how to wire the build plugin so the manifest covers the modules your running app actually serves.

If you scanned a single module and saw only that module's capabilities, this is why, and this is the fix.

## How scan scope works

`tibyan scan <path>` walks the entire directory tree under the path you give it, recursively, and reads every `.java` file it finds (it skips only build and tooling directories: `target`, `build`, `out`, `bin`, `.git`, `.idea`, `.mvn`). It does not stop at a module boundary. Point it at a reactor root and it reads every module under that root in a single pass.

Two consequences follow, and both matter:

1. **One scanned root produces one manifest.** The `module` field in the manifest is simply the name of the directory you scanned. Scan `.../pms-backend` and the module is `pms-backend`; scan `.../pms-backend/vendor-mgt` and the module is `vendor-mgt`. It is a label for the source root, nothing more.

2. **Discovery is resolved against exactly the files you scan.** When Tibyaan checks whether a service method is a read it can prove, it resolves the entity, its repository, and the method's return type against the types declared under the scanned root only. Types in modules you did not scan are treated as external library types, the same as `java.util.List`. So if `vendor-mgt`'s `VendorService` returns a `VendorVTO` view of a `Vendor` entity, that read is discoverable when `vendor-mgt` is in scope, and invisible when it is not.

This is why scanning module-by-module and scanning the whole application can give different results. It is not a bug in either run; it is the direct meaning of "resolved from your own source." Scan at the scope you intend to serve.

## Preview the whole application (read-only)

Point the CLI at the reactor root, exactly as you would a single module. This reads source and writes nothing:

```bash
java -jar tibyan-cli-0.3.0-jar-with-dependencies.jar scan /path/to/your-reactor-root --report survey.md
```

The console survey and the `survey.md` report list every discovered capability across all modules, and every refusal with its located reason, under the totals line ending in `mis-bound 0`. Read it before you change anything: it shows you the exact set of capabilities a whole-application adoption would expose.

Compare it against a single-module scan if you want to understand a specific module in isolation. Where the two disagree, the survey's refusal lines name the cause. The most common cross-module cause is an ambiguous simple type name: if two modules both declare a type with the same simple name and a service references it by that bare name, Tibyaan refuses rather than guess which one is meant (this is the same fail-loud discipline that keeps `mis-bound` at 0). A single module in isolation has no such ambiguity, so a read can be discovered there and refused at the wider scope, or the reverse. The survey tells you which, for the exact scope you ran.

## Adopt: point the build plugin at the reactor root

The `tibyan-maven-plugin` runs inside your build, once per module it is bound to, and by default scans only that module (`sourceRoot` defaults to `${project.basedir}`). For a multi-module application that default is usually not what you want: you want one manifest, covering the domain modules your app serves, on the classpath of the module that actually runs.

The recipe:

1. **Put the runtime dependency and the build plugin in the module that produces your runnable boot jar** (the web or application module, the one with `spring-boot-maven-plugin`). That module's classpath is where the runtime looks for the manifest at startup.

2. **Point the plugin's `sourceRoot` at the reactor root**, so it scans across module boundaries into one manifest. `${maven.multiModuleProjectDirectory}` is the Maven-provided path to the top of your reactor:

```xml
<plugin>
  <groupId>io.github.tibyaan-org</groupId>
  <artifactId>tibyan-maven-plugin</artifactId>
  <version>0.3.0</version>
  <configuration>
    <sourceRoot>${maven.multiModuleProjectDirectory}</sourceRoot>
  </configuration>
  <executions>
    <execution>
      <goals><goal>generate</goal></goals>
    </execution>
  </executions>
</plugin>
```

Confirm `sourceRoot` resolved to the root you expect by checking the `module` field of the generated `target/classes/tibyan/capabilities.json`: it will be your reactor root's directory name. If `${maven.multiModuleProjectDirectory}` does not resolve to your reactor root (it depends on where you invoke Maven and on your reactor layout), set an explicit path instead, either in `<sourceRoot>` or on the command line:

```bash
mvn package -Dtibyan.sourceRoot=/absolute/path/to/your-reactor-root
```

3. **Preview first, always.** Run the CLI scan at the same root you will configure, read the survey, and adopt only if it convinces you. The plugin runs the identical scan the CLI runs, so what the preview shows at a given root is what the plugin will generate at that root.

### A note on scope and cost

Scanning the whole reactor reads every module's source at build time. That is read-only and fast (it is AST parsing, not compilation), but it does mean the manifest reflects every module in scope. If you deliberately want to expose only one bounded context, scan only that context's source root instead, and the manifest will contain only what resolves there. Match the scan scope to what you intend to serve.

### If two modules declare the same entity

Tibyaan keys entities by fully qualified class name, so two modules that legitimately reuse a simple entity name (for example `billing.Invoice` and `sales.Invoice`) are distinct and both survive, with disambiguated display names. The one hard failure is a genuine split-package clash: the same fully qualified name declared by two source files under the scanned root. That fails loud with a located error rather than silently merging them, which is the correct outcome. It can only arise when a scan spans modules, so you will see it, if at all, on a reactor-root scan, and the message names the clashing key.

## See also

- [README Quickstart](../README.md#quickstart): the two-step preview-then-adopt flow, single-module by default.
- [COMMANDS.md](COMMANDS.md) section 3: the full Maven plugin parameter reference.
- [FAQ.md](FAQ.md): why you may see only one tool, what the generated `.java` files are for, and other first-run questions.
- [CONNECTING-AN-MCP-CLIENT.md](CONNECTING-AN-MCP-CLIENT.md): pointing an agent at the running server.
