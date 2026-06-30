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

`tibyan generate --backend mcp` writes the generated tool files and a capabilities manifest, `tibyan/capabilities.json`, into the chosen `--out` directory, for example `./generated/<module>-mcp/`. That generated manifest is the artifact the runtime hosts.

The default and simplest way to host it is to ship it as a normal classpath resource. Copy the generated `tibyan/capabilities.json` into the app's `src/main/resources/tibyan/capabilities.json`. With the `tibyan-runtime-spring` dependency on the classpath, the runtime finds it there automatically, with no launch property and no MCP config in `application.yml` (Volume 7, section 4).

For ad hoc runs you can instead point the runtime at a manifest file without copying it, using `-Dtibyan.capabilities=file:./generated/<module>-mcp/tibyan/capabilities.json`, a relative path, never a hardcoded absolute or machine-specific path. The explicit property overrides the classpath default and still reproduces the end-to-end MCP path.

The manifest that ships under `tibyan-backend-mcp/src/test/resources/...` is only a golden fixture for the backend's tests. It is not a runtime artifact. Nothing should point the runtime at it, or at any fixed location such as a hand-placed file in a system directory; the only manifest the runtime should host is the one a `generate` run produces into its output directory.

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
