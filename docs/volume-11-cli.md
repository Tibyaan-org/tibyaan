# Volume 11: CLI Specification

Status: Stable for the MVP commands; future commands are listed as direction. The CLI is a thin caller of the pipeline (Volume 9). It parses arguments and invokes provide, validate, and generate. It contains no compiler logic of its own. It is built with picocli (Volume 16).

---

## 1. Overview

The CLI binary is `tibyan`. It exposes a small set of commands that map directly onto pipeline stages. Hosting the generated layer is the runtime's job inside the target application, so the CLI stops at generation.

## 2. Commands (MVP)

| Command | Purpose | Key options |
|---|---|---|
| tibyan scan | Read a system and produce IR | path (positional), --out (IR JSON file) |
| tibyan generate | Generate a backend from IR | --backend (for example mcp), --ir (IR file) or path, --out (output dir) |
| tibyan validate | Validate an IR without generating | --ir (IR file) |
| tibyan version | Print the tool and IR schema versions | none |

## 3. Examples and expected output

```text
$ tibyan scan ./legacy-spring-app --out inventory.ir.json
Scanning ./legacy-spring-app
Provider: spring (entity and service extractors)
Found 1 entity (Product), 1 capability (getProduct)
Validation: ok
Wrote IR to inventory.ir.json
```

```text
$ tibyan generate --backend mcp --ir inventory.ir.json --out ./generated/inventory-mcp
Loading IR inventory.ir.json
Validation: ok
Backend: mcp
Generated 1 tool (getProduct)
Wrote 3 files to ./generated/inventory-mcp
```

```text
$ tibyan validate --ir inventory.ir.json
Validation: ok (1 entity, 1 capability, 0 relations)
```

```text
$ tibyan scan ./broken-app --out out.ir.json
Scanning ./broken-app
Validation: FAILED
  - capability 'getOrder' returns ENTITY_REF(Order) but no entity 'Order' is defined
Exit code 2
```

### 3.1 The generated manifest is what the runtime hosts

Generation, whether from the CLI or at build time, writes a capabilities manifest, `tibyan/capabilities.json`. That manifest is the artifact the runtime hosts. The CLI's `tibyan generate --backend mcp` writes it (plus the generated tool files) into the chosen `--out` directory, for example `./generated/<module>-mcp/`.

Recommended adoption: generate at build time. The `tibyan-maven-plugin` runs the same pipeline during the app's own build and writes the manifest onto the classpath (`target/classes/tibyan/capabilities.json`). Add it to the app's build once:

```xml
<plugin>
  <groupId>com.tibyaan</groupId>
  <artifactId>tibyan-maven-plugin</artifactId>
  <version>...</version>
  <executions>
    <execution><goals><goal>generate</goal></goals></execution>
  </executions>
</plugin>
```

The goal binds to `generate-resources`, scans the module (by default `${project.basedir}`, matching `tibyan scan <module>`), and produces a manifest byte-identical to `tibyan generate`, because it invokes the same `TibyanCompiler` and `McpBackend`, not a second generator. With the `tibyan-runtime-spring` dependency on the classpath, the runtime finds the manifest there automatically, with no launch property and no MCP config in `application.yml` (Volume 7, section 4). Nothing is committed by hand.

The CLI remains for manual and inspection use: run `tibyan scan` and `tibyan generate` to see the IR and the generated output, or to produce a manifest for a host that is not built with Maven. You can also point the runtime at a manifest file for an ad hoc run with `-Dtibyan.capabilities=file:./generated/<module>-mcp/tibyan/capabilities.json`, a relative path, never a hardcoded absolute or machine-specific path; the explicit property overrides the classpath default.

The manifest that ships under `tibyan-backend-mcp/src/test/resources/...` is only a golden fixture for the backend's tests. It is not a runtime artifact. Nothing should point the runtime at it, or at any fixed location such as a hand-placed file in a system directory; the only manifest the runtime should host is one a generation run produces, from the CLI or the build plugin.

## 4. Exit codes

| Code | Meaning |
|---|---|
| 0 | Success |
| 1 | Usage error (bad arguments) |
| 2 | Validation failure |
| 3 | Provider or backend error |

## 5. Future commands

| Command | Purpose |
|---|---|
| tibyan diff | Show what changed in the IR or the generated output between two runs |
| tibyan init | Scaffold a Tibyan configuration in a project |
| tibyan serve | Run a generated layer locally for testing, outside a host application |
| tibyan list | List available providers and backends discovered as plugins |

Future commands follow the same rule: they call the pipeline or the plugin registry, and they hold no compiler logic themselves.
