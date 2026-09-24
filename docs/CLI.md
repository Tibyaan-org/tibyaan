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
$TIBYAN = "C:\Users\HazeX01\Documents\GitHub\me-workspace\tibyaan\tibyan-cli\target\tibyan-cli-0.4.0-SNAPSHOT-jar-with-dependencies.jar"
```
```bash
# bash / zsh
TIBYAN="$HOME/.../tibyaan/tibyan-cli/target/tibyan-cli-0.4.0-SNAPSHOT-jar-with-dependencies.jar"
```

So the fully written out form of a command looks exactly like this:

```
java -jar "C:\Users\HazeX01\Documents\GitHub\me-workspace\tibyaan\tibyan-cli\target\tibyan-cli-0.4.0-SNAPSHOT-jar-with-dependencies.jar" scan "C:\path\to\app" --report report.md
```

Optional convenience wrapper (then you can type `tibyan ...`):

```powershell
function tibyan { java -jar $TIBYAN @args }   # PowerShell
```
```bash
tibyan() { java -jar "$TIBYAN" "$@"; }        # bash / zsh
```

Note on the version: the latest released artifact is `0.3.0`, and a local build of this tree is
`0.4.0-SNAPSHOT`. Substitute the version you actually built into the jar name.

## Commands at a glance

| Command | Purpose |
|---|---|
| `scan` | Resiliently survey a system's source into a Capability IR (never aborts on a bad file). |
| `generate` | Generate a backend (MCP or OpenAPI) from an IR file, or by scanning a path (strict). |
| `attest` | Stamp fingerprints into the attestations file, and confirm one drafted entry. |
| `draft` | Draft a labeled candidate tool for every public service method, reads and writes, into `tibyan/drafts.json` (DL-0 and DL-1). |
| `describe` | Draft a description for every pending entry of `tibyan/drafts.json` from the fingerprinted code; the only command that calls a model (phase Describe). |
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

## `tibyan attest`

Two actions on the adopter's committed `tibyan/attestations.json`, the reviewable record of every human
assertion (Volume 5, section 4.15.3). The tool never writes a claim's WORDS in either action: the meaning
is always the human's.

### Synopsis

```
tibyan attest <path> [--date <yyyy-mm-dd>]
tibyan attest <path> --confirm <target> --by <name>
```

### Stamping (the default action)

Computes the structural fingerprint of each entry's slice over the CURRENT code and writes it back, with
`--date` optionally stamping the assertion date at the same time. This is the only path that stamps a
fingerprint, so a fingerprint always co-arrives with the code it pins; a hand-authored entry without one
is refused at intake rather than silently completed.

```
java -jar $TIBYAN attest "C:\path\to\app" --date 2026-09-01
```

### Confirming a draft (`--confirm`)

An entry whose `origin` is `ai-draft` is a PROPOSAL, not a claim. It is queued in the scan survey, counted
apart from attested and from discovered, and it ships nothing: no capability, no manifest entry, no
OpenAPI operation. Confirming it is the human act that turns it into a claim.

```
java -jar $TIBYAN attest "C:\path\to\app" --confirm "com.example.TenantServiceImpl#getAllByFilter(String,Integer)" --by a.elseginy
```

That rewrites exactly that entry's origin to `ai-drafted-human-confirmed` and records you in
`assertedBy`. Then stamp it and re-scan to intake it.

Four properties are deliberate, and each is enforced by construction rather than by a check that could be
relaxed later (Volume 5, section 4.15.5):

- ONE ENTRY AT A TIME. There is no `--confirm-all` and no multi-target form, here or in the API beneath.
  Confirmation is a per-entry human act; a team with ten drafts reviews ten drafts.
- YOU ARE NAMED. `--by` is required and may not be blank. The whole value of the rewritten origin is that
  it records who took the claim on.
- THE WORDS ARE NOT REWRITTEN. Confirming means you read the proposal and take it on as your own claim. If
  you want different words, edit the file; it is the same reviewable record either way.
- ONLY A DRAFT IS CONFIRMED. Confirming a `human-authored` entry refuses, because relabelling your own
  claim as AI-drafted would be untrue in the one field whose job is to be true about how the words were
  produced.

### Exit codes

| Code | When |
|---|---|
| 0 | The file was stamped, or the named draft was confirmed. |
| 1 | `--confirm` did not apply: no such target, not a draft, or no `--by` given. |
| 3 | The scan could not run: the path is missing or cannot be walked. |

---

## `tibyan draft`

Drafts a candidate tool for EVERY public method of every concrete `@Service` class, reads and writes alike,
labeled by what the tool could establish, and writes the reviewable `tibyan/drafts.json` beside the source
(spike DL-0; the record of phase DL-1, Volume 5 section 4.17). It serves nothing by itself: an entry ships only
after a named human approves it in the file and `tibyan scan` admits it.

### Synopsis

```
tibyan draft <path> [--out <drafts.json>]
```

### What each entry carries

The tool writes `name` (a minted default you may rename), `target`, `binding`, `label`, `reason`, `capability`
(when the label is VERIFIED or ATTESTED), `returnType`, `inputs` (each with its Java type and the mapped
primitive, or `null` when the mapper cannot map it), and `fingerprint`, the pin of the code you approve.
You write `description` (the sentence the tool never authors), `descriptionOrigin` (`human`, or `ai-draft` when
an agent wrote it), `review` (`approved` or `rejected`; the tool writes `pending`), `reviewedBy` and
`reviewedOn`.

The five labels: `VERIFIED` (a discovered binding), `ATTESTED` (a confirmed attestation target), `READ` (no
mutation visible in the analyzed slice), `WRITE` (a mutation is visible), `UNKNOWN` (the check could not
decide, or the method returns nothing). A label is what the tool could establish, never a proof of meaning.

### Re-running

Re-running merges and never overwrites: your review fields are kept, new methods are appended as pending, a
target that no longer exists is marked `stale` in place, and an entry whose recomputed label differs from the
one you approved gets `previousLabel` and its review RESET to `pending`, because you approved something else.

### What happens at `tibyan scan`

Every `approved` entry is checked (Volume 5 section 4.17.3) and either admitted as `reviewed_<name>` or refused
with a located cause: `TARGET_UNRESOLVED`, `STALE_DRAFT`, `LABEL_DRIFT`, `SUPERSEDED` (the target is already a
verified or attested capability), `INPUT_UNMAPPED` (a collection or DTO parameter; collection inputs are
deferred to DL-2), `REVIEW_INCOMPLETE`. The survey prints `reviewed N` beside `discovered` and `attested`,
never summed, plus the pending and refused draft counts. A WRITE or ACTION capability then registers at the
runtime only when its exact name is in `tibyan.writes.allow` (docs/SECURITY.md, the write gate).

### Exit codes

| Code | Meaning |
|---|---|
| 0 | The drafts file was written. |
| 3 | The source root could not be read at all. |

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
tibyan 0.4.0-SNAPSHOT
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


## `tibyan describe`

Drafts a description for every pending entry of `tibyan/drafts.json` from the code the tool fingerprints, and
writes it back under `descriptionOrigin: ai-draft` with the model id (`describedBy`) and the fingerprint it
was drafted from (`describedFrom`). Nothing it writes reaches a tool surface: the entry stays `pending`
until a named human approves it (phase Describe, Volume 5 section 4.15.5.2; Volume 11 section 3.6).

This is the ONLY command that makes a network call. `scan`, `draft`, `generate`, `attest`, `validate` and
the Maven plugin never do.

### Synopsis

```
tibyan describe <path> [--model <id>] [--limit <n>] [--only <name>,...] [--label READ|WRITE|UNKNOWN]
                [--drafts <drafts.json>] [--dry-run]
```

### Arguments and options

| Option | Effect |
|---|---|
| `<path>` | The system whose drafts file to describe. |
| `--model <id>` | The model id, recorded on every entry it touches (default `claude-sonnet-5`). |
| `--limit <n>` | At most this many entries per run (default 50). |
| `--only <name>,...` | Describe only these entry names. |
| `--label READ\|WRITE\|UNKNOWN` | Describe only entries with this label. |
| `--drafts <file>` | The drafts file (default `<path>/tibyan/drafts.json`). |
| `--dry-run` | Print every prompt and write nothing; needs no key. |

The key is read from the environment variable `ANTHROPIC_API_KEY` only, never from a flag or a file.

### What is described

A `pending` entry whose description is empty, or whose `ai-draft` description was written from a fingerprint
the entry no longer carries (`descriptionStale: true` after a `tibyan draft` re-run). Never an approved or
rejected entry, never words a person wrote, never a stale entry. The model sees the fingerprinted slice (the
method, its imports, the repository declarations it calls, its return type, its parameter shapes), the label
and its reason, and the house rules; the words come back verbatim, at most 600 characters on one line, or
the entry is refused with the reason. A drafted sentence is never printed by any command.

### Exit codes

`0` on success (refusals are listed, not fatal); `3` when the drafts file is missing or the key is absent,
with nothing written.

### Example

```bash
tibyan describe . --label WRITE --limit 50
tibyan describe . --only CityServiceImpl_createCity,CityServiceImpl_updateCity --dry-run
```
