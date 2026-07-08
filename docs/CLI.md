# Tibyaan CLI reference

The `tibyan` command-line tool reads a Spring application from source, surveys it into a Capability
IR, and generates an AI-operable layer from that IR (an MCP server manifest or an OpenAPI document).
It is the manual and inspection counterpart to the build-time Maven plugin: most adopters let the
plugin do this automatically at build time, and reach for the CLI to survey an app or produce
artifacts by hand.

Everything the CLI does is read-only with respect to your application: it never modifies the system
it scans, and it never emits a wrong binding. An entity or read it cannot prove safe is refused with
a located reason (`mis-bound 0` is the absolute bar).

## Running the CLI

The CLI ships as a self-contained "jar-with-dependencies". Build it from the repository:

```
mvn -q -pl tibyan-cli -am -DskipTests package
```

That produces `tibyan-cli/target/tibyan-cli-<version>-jar-with-dependencies.jar`. Invoke it as:

```
java -jar <path-to-jar> <command> [options]
```

Throughout this document `$TIBYAN` stands for that jar path. Define it once so the examples stay
readable:

```powershell
# PowerShell
$TIBYAN = "C:\Users\HazeX01\Documents\GitHub\me-workspace\tibyaan\tibyan-cli\target\tibyan-cli-0.2.0-SNAPSHOT-jar-with-dependencies.jar"
```
```bash
# bash / zsh
TIBYAN="$HOME/.../tibyaan/tibyan-cli/target/tibyan-cli-0.2.0-SNAPSHOT-jar-with-dependencies.jar"
```

So the fully written out form of a command looks exactly like this:

```
java -jar "C:\Users\HazeX01\Documents\GitHub\me-workspace\tibyaan\tibyan-cli\target\tibyan-cli-0.2.0-SNAPSHOT-jar-with-dependencies.jar" scan "C:\path\to\app" --report report.md
```

Optional convenience wrapper (then you can type `tibyan ...`):

```powershell
function tibyan { java -jar $TIBYAN @args }   # PowerShell
```
```bash
tibyan() { java -jar "$TIBYAN" "$@"; }        # bash / zsh
```

Note on the version: the released artifact is `0.1.0`; a local build of the current tree is
`0.2.0-SNAPSHOT`. Substitute the version you actually built into the jar name.

## Commands at a glance

| Command | Purpose |
|---|---|
| `scan` | Resiliently survey a system's source into a Capability IR (never aborts on a bad file). |
| `generate` | Generate a backend (MCP or OpenAPI) from an IR file, or by scanning a path (strict). |
| `validate` | Validate an IR JSON file without generating. |
| `version` | Print the tool and IR schema versions. |

Global options, available on any command:

| Option | Effect |
|---|---|
| `-h`, `--help` | Print usage for the tool or a subcommand (`tibyan scan --help`). |
| `-V`, `--version` | Print the tool version. |

Running `tibyan` with no command prints the usage summary.

## Exit codes

Every command uses one convention (Volume 11, section 4):

| Code | Meaning |
|---|---|
| 0 | Success. |
| 1 | Usage error: bad or missing arguments, or an unknown backend. |
| 2 | Validation failure: the IR did not pass semantic validation, so no artifact was written. |
| 3 | Provider or backend error: the source is missing or unwalkable, or generation failed. |

---

## `tibyan scan`

Read a system from source and survey it into a Capability IR. This is the **resilient** path: it
walks the entire source tree, parses collect-and-continue, classifies every entity it can read, and
names every file it cannot parse. It never aborts on a single unparseable file, and it never emits a
wrong binding.

### Synopsis

```
tibyan scan <path> [--out <ir.json>] [--report <report.md>]
```

### Arguments and options

| Name | Required | Description |
|---|---|---|
| `<path>` | yes | Path to the system (its source root) to scan. |
| `--out <ir.json>` | no | Write the discovered IR as JSON to this file. Written **only if** the discovered model validates; otherwise nothing is written and the command exits 2. The survey is still printed. |
| `--report <report.md>` | no | Write a human-readable Markdown survey to this file: a summary table, the operable subset with its bindings, the refusals grouped with their located reasons, and the single most common blocker. Read-only and independent of `--out`. |

### Output

Prints a per-entity outcome (discovered, or refused with a located reason such as `NO_IDENTIFIER`,
`NO_BACKING_METHOD`, `AMBIGUOUS_BINDING`, `COMPOSITE_KEY`, `LIST_AMBIGUOUS`), the list of files it had
to skip because they would not parse, and a totals line:
`discovered N (by-id A, list B), failed, list-refused, skipped-by-parse, partial, mis-bound`.

### Exit codes

| Code | When |
|---|---|
| 0 | The survey completed, whatever it discovered (including zero). |
| 2 | `--out` was given but the discovered IR did not validate, so no file was written. |
| 3 | The scan could not run: the path is missing, is not a directory, or cannot be walked. |

### Examples

```
# Survey an app and print the report to the console
java -jar $TIBYAN scan "C:\path\to\EduNest"

# Survey and write a shareable Markdown report
java -jar $TIBYAN scan "C:\path\to\EduNest" --report report.md

# Survey and write the IR JSON (only written if it validates)
java -jar $TIBYAN scan "C:\path\to\EduNest" --out edunest.ir.json

# Do both at once
java -jar $TIBYAN scan "C:\path\to\EduNest" --out edunest.ir.json --report report.md
```

---

## `tibyan generate`

Generate a backend from an IR file, or by scanning a path directly. When generating from a `<path>`
this uses the **strict** provider, not the resilient scan: it refuses any unparseable file or
unresolvable binding rather than producing a partial artifact. Generation is deterministic: the same
IR in produces the same files out.

### Synopsis

```
tibyan generate --backend <mcp|openapi> --out <dir> (--ir <ir.json> | <path>)
```

### Arguments and options

| Name | Required | Description |
|---|---|---|
| `--backend <backend>` | yes | The backend to generate: `mcp` or `openapi`. |
| `--out <dir>` | yes | Output directory for the generated files. |
| `--ir <ir.json>` | see note | IR JSON file to read. |
| `<path>` | see note | Path to scan (strict) when `--ir` is not given. |

Note: provide **either** `--ir <file>` **or** a `<path>`, not neither. If neither is given, the
command exits 1.

### Output

| Backend | Files written |
|---|---|
| `mcp` | `<out>/tibyan/capabilities.json` (the MCP server manifest). |
| `openapi` | `<out>/openapi.json` (an OpenAPI 3.0 document). |

### Exit codes

| Code | When |
|---|---|
| 1 | Unknown backend, or neither `--ir` nor `<path>` was given. |
| 2 | The IR did not validate. |
| 3 | Provider or backend error. |

### Examples

```
# Generate an MCP manifest from an IR file
java -jar $TIBYAN generate --backend mcp --ir edunest.ir.json --out ./generated

# Generate an OpenAPI document by scanning an app directly (strict path)
java -jar $TIBYAN generate --backend openapi --out ./generated "C:\path\to\app"
```

---

## `tibyan validate`

Validate an IR JSON file against the Capability IR's semantic rules, without generating anything.
Useful to check a hand-edited or externally produced IR before feeding it to `generate`.

### Synopsis

```
tibyan validate --ir <ir.json>
```

### Arguments and options

| Name | Required | Description |
|---|---|---|
| `--ir <ir.json>` | yes | IR JSON file to validate. |

### Exit codes

| Code | When |
|---|---|
| 0 | The IR is valid; a one-line summary is printed. |
| 2 | The IR is invalid; the located errors are printed. |
| 3 | Error, for example the file cannot be read or parsed. |

### Example

```
java -jar $TIBYAN validate --ir edunest.ir.json
```

---

## `tibyan version`

Print the tool version and the IR schema version.

### Synopsis

```
tibyan version
```

### Example output

```
tibyan 0.2.0-SNAPSHOT
IR schema 0.1
```

`tibyan --version` prints just the tool line; `tibyan version` also prints the IR schema version.

---

## A typical end-to-end session

```
# 1. Survey the app to see what is operable and what is refused, and why
java -jar $TIBYAN scan "C:\path\to\app" --report report.md

# 2. When the survey looks right, write the IR
java -jar $TIBYAN scan "C:\path\to\app" --out app.ir.json

# 3. Validate it (optional; generate validates too)
java -jar $TIBYAN validate --ir app.ir.json

# 4. Generate the MCP manifest, and/or an OpenAPI document
java -jar $TIBYAN generate --backend mcp     --ir app.ir.json --out ./generated
java -jar $TIBYAN generate --backend openapi --ir app.ir.json --out ./generated
```

For most adopters the Maven plugin performs steps 1 to 4 automatically during the application's
build, writing the manifest onto the classpath, so the running app serves the MCP tools with no
manual step. The CLI is for manual runs, surveys, and inspection. See the root `README.md` for the
adoption path and `docs/CONNECTING-AN-MCP-CLIENT.md` for connecting a client to the running server.
