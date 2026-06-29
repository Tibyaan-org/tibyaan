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
Provider: spring (entity extractor)
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
