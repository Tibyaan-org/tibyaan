# Tibyaan commands manual

A single operational reference for every way you invoke Tibyaan: the `tibyan` CLI, the Maven build
plugin, the Spring runtime, and the build commands. For the CLI alone in more depth, see
[docs/CLI.md](CLI.md); for the adoption story see the root [README](../README.md); for connecting an
agent see [docs/CONNECTING-AN-MCP-CLIENT.md](CONNECTING-AN-MCP-CLIENT.md).

Everything here is read-only with respect to the application Tibyaan reads: it never modifies the
system it scans, and it never emits a wrong binding. Anything it cannot prove safe is refused with a
located reason. `mis-bound 0` is the absolute bar.

A note on versions: the latest released artifacts are `0.2.0` (on Maven Central under groupId
`io.github.tibyaan-org`, with `0.1.0` still available). A local build of this tree reports
`0.4.0-SNAPSHOT`.
Substitute the version you actually have wherever a version appears below.

---

## 1. The four surfaces at a glance

| Surface | You use it to | Entry point |
|---|---|---|
| CLI (`tibyan`) | Survey an app, and produce artifacts by hand | `java -jar tibyan-cli-<ver>-jar-with-dependencies.jar <command>` |
| Maven plugin | Generate the manifest automatically at build time | `tibyan-maven-plugin` goal `generate` |
| Runtime | Host the generated MCP server inside your app | `tibyan-runtime-spring` dependency, then run your app |
| Build | Build, test, and release Tibyaan itself | `mvn ...` |

Most adopters never touch the CLI in production: the Maven plugin runs the same pipeline at build
time and writes the manifest onto the classpath, and the runtime serves it. The CLI is for surveys,
one-off artifacts, and inspection.

---

## 2. The `tibyan` CLI

### 2.1 Getting the CLI

Download the self-contained jar from Maven Central (released line):

```bash
curl -O https://repo1.maven.org/maven2/io/github/tibyaan-org/tibyan-cli/0.2.0/tibyan-cli-0.2.0-jar-with-dependencies.jar
```

Or build it from a checkout of this repository (current tree, `0.4.0-SNAPSHOT`):

```bash
mvn -q -pl tibyan-cli -am -DskipTests package
# produces tibyan-cli/target/tibyan-cli-0.4.0-SNAPSHOT-jar-with-dependencies.jar
```

Invoke it as `java -jar <jar> <command> [options]`. To keep examples short, define `$TIBYAN` as the
jar path, and optionally a wrapper so you can type `tibyan ...`:

```bash
# bash / zsh
TIBYAN="/path/to/tibyan-cli-0.2.0-jar-with-dependencies.jar"
tibyan() { java -jar "$TIBYAN" "$@"; }
```

```powershell
# PowerShell
$TIBYAN = "C:\path\to\tibyan-cli-0.2.0-jar-with-dependencies.jar"
function tibyan { java -jar $TIBYAN @args }
```

### 2.2 Commands at a glance

| Command | Purpose |
|---|---|
| `scan` | Resiliently survey a system's source into a Capability IR. Never aborts on a bad file. |
| `generate` | Generate a backend (MCP or OpenAPI) from an IR file, or by scanning a path (strict). |
| `validate` | Validate an IR JSON file without generating. |
| `version` | Print the tool and IR schema versions. |

Global options, on any command:

| Option | Effect |
|---|---|
| `-h`, `--help` | Print usage for the tool or a subcommand (`tibyan scan --help`). |
| `-V`, `--version` | Print the tool version. |

Running `tibyan` with no command prints the usage summary.

### 2.3 Exit codes (one convention everywhere)

| Code | Meaning |
|---|---|
| 0 | Success. |
| 1 | Usage error: bad or missing arguments, or an unknown backend. |
| 2 | Validation failure: the IR did not pass semantic validation, so no artifact was written. |
| 3 | Provider or backend error: the source is missing or unwalkable, or generation failed. |

### 2.4 `tibyan scan`

Read a system from source and survey it into a Capability IR. This is the resilient path: it walks
the whole tree, parses collect-and-continue, classifies every entity it can read, and names every
file it cannot parse. It never aborts on one unparseable file.

```
tibyan scan <path> [--out <ir.json>] [--report <report.md>]
```

| Name | Required | Description |
|---|---|---|
| `<path>` | yes | Path to the system (its source root) to scan. |
| `--out <ir.json>` | no | Write the discovered IR as JSON. Written only if the model validates; otherwise nothing is written and the command exits 2. The survey still prints. |
| `--report <report.md>` | no | Write a human-readable Markdown survey: a summary table, the operable subset with its bindings, the grouped refusals with located reasons, and the single most common blocker. Read-only and independent of `--out`. |

Output: a per-entity outcome (discovered, or refused with a cause such as `NO_IDENTIFIER`,
`NO_BACKING_METHOD`, `AMBIGUOUS_BINDING`, `COMPOSITE_KEY`, `LIST_AMBIGUOUS`), the list of
skipped-by-parse files, and a totals line:
`discovered N (by-id A, list B), failed, list-refused, skipped-by-parse, partial, mis-bound`.

Exit codes: `0` the survey completed (including zero discovered); `2` `--out` given but the IR did
not validate; `3` the path is missing, is not a directory, or cannot be walked.

```bash
tibyan scan /path/to/app                                   # survey to the console
tibyan scan /path/to/app --report report.md                # shareable Markdown survey
tibyan scan /path/to/app --out app.ir.json                 # write the IR (only if it validates)
tibyan scan /path/to/app --out app.ir.json --report report.md
```

### 2.5 `tibyan generate`

Generate a backend from an IR file, or by scanning a path directly. Generating from a `<path>` uses
the strict provider, not the resilient scan: it refuses any unparseable file or unresolvable binding
rather than producing a partial artifact. Generation is deterministic: the same IR in produces the
same files out.

```
tibyan generate --backend <mcp|openapi> --out <dir> (--ir <ir.json> | <path>)
```

| Name | Required | Description |
|---|---|---|
| `--backend <backend>` | yes | `mcp` or `openapi`. |
| `--out <dir>` | yes | Output directory for the generated files. |
| `--ir <ir.json>` | either this | IR JSON file to read. |
| `<path>` | or this | Path to scan (strict) when `--ir` is not given. |

Provide either `--ir <file>` or a `<path>`, not neither (neither exits 1).

| Backend | Files written |
|---|---|
| `mcp` | `<out>/tibyan/capabilities.json` (the MCP server manifest). |
| `openapi` | `<out>/openapi.json` (an OpenAPI 3.0 document). |

Exit codes: `1` unknown backend, or neither `--ir` nor `<path>` given; `2` the IR did not validate;
`3` provider or backend error.

```bash
tibyan generate --backend mcp     --ir app.ir.json --out ./generated
tibyan generate --backend openapi --ir app.ir.json --out ./generated
tibyan generate --backend openapi --out ./generated /path/to/app   # scan directly (strict)
```

### 2.6 `tibyan validate`

Validate an IR JSON file against the Capability IR's semantic rules, without generating anything.
Useful before feeding a hand-edited or externally produced IR to `generate`.

```
tibyan validate --ir <ir.json>
```

| Name | Required | Description |
|---|---|---|
| `--ir <ir.json>` | yes | IR JSON file to validate. |

Exit codes: `0` valid (a one-line summary prints); `2` invalid (the located errors print); `3` the
file cannot be read or parsed.

```bash
tibyan validate --ir app.ir.json
```

### 2.7 `tibyan version`

```
tibyan version
```

Prints the tool version and the IR schema version, for example:

```
tibyan 0.4.0-SNAPSHOT
IR schema 0.1
```

`tibyan --version` prints just the tool line; `tibyan version` also prints the IR schema version.

### 2.8 A typical end-to-end CLI session

```bash
tibyan scan /path/to/app --report report.md               # 1. see what is operable and what is refused, and why
tibyan scan /path/to/app --out app.ir.json                # 2. write the IR when the survey looks right
tibyan validate --ir app.ir.json                          # 3. optional; generate validates too
tibyan generate --backend mcp     --ir app.ir.json --out ./generated
tibyan generate --backend openapi --ir app.ir.json --out ./generated
```

---

## 3. The Maven build plugin

The `tibyan-maven-plugin` runs the same pipeline the CLI runs, during your application's build, and
writes `tibyan/capabilities.json` onto the classpath output (`target/classes/tibyan/capabilities.json`).
Nothing is committed by hand.

### 3.1 Bound execution (recommended)

Add the plugin to your application's `pom.xml`. Its `generate` goal binds to the
`generate-resources` phase by default, so a normal `mvn package` produces the manifest:

```xml
<plugin>
  <groupId>io.github.tibyaan-org</groupId>
  <artifactId>tibyan-maven-plugin</artifactId>
  <version>0.2.0</version>
  <executions>
    <execution>
      <goals><goal>generate</goal></goals>
    </execution>
  </executions>
</plugin>
```

```bash
mvn package
```

### 3.2 One-off invocation (no pom change)

Run the goal directly with its fully qualified coordinates:

```bash
mvn io.github.tibyaan-org:tibyan-maven-plugin:0.2.0:generate
```

The short prefix form `mvn tibyan:generate` also works if the plugin group is on your Maven plugin
search path.

### 3.3 Goal: `generate`

| Behavior | Detail |
|---|---|
| Default phase | `generate-resources` |
| Output | `tibyan/capabilities.json` on the classpath output directory |
| Resilience | Resilient by default: an unparseable file is skipped and named at WARN, and the manifest is generated from what was discovered, so adopting the plugin never breaks a build on a bad file. A partial manifest is disclosed loudly. |
| Zero capabilities | If nothing is discovered, the build succeeds but logs a WARN telling you to run `tibyan scan` to see why. |
| Hard failures | A genuinely invalid IR, or an unwalkable source root, still fails the build. |

Configuration parameters (each has a `-D` property for command-line override):

| Parameter | Property | Default | Purpose |
|---|---|---|---|
| `sourceRoot` | `tibyan.sourceRoot` | `${project.basedir}` | The source root to scan. |
| `outputDirectory` | `tibyan.outputDirectory` | `${project.build.outputDirectory}` (`target/classes`) | Where the manifest is written. |
| `workDirectory` | `tibyan.workDirectory` | `${project.build.directory}/tibyan-generate` | Intermediate directory for the backend's full output. |
| `strict` | `tibyan.strict` | `false` | When true, run the strict provider: any unparseable file or hard failure fails the build. For CI gating. |

```bash
mvn package                        # resilient default
mvn package -Dtibyan.strict=true   # fail the build on any unparseable file (CI gate)
```

For a multi-module reactor, `sourceRoot` defaults to the single module the plugin runs in, which is usually not what you want. Point it at the reactor root so one manifest covers every module, on the classpath of the module that runs. See [MULTI-MODULE.md](MULTI-MODULE.md).

---

## 4. The Spring runtime

The `tibyan-runtime-spring` dependency hosts the generated MCP server inside your application's own
process. Add the dependency, and the manifest the build plugin produced is discovered and served
automatically.

```xml
<dependency>
  <groupId>io.github.tibyaan-org</groupId>
  <artifactId>tibyan-runtime-spring</artifactId>
  <version>0.2.0</version>
</dependency>
```

### 4.1 Launching

Run your application as usual. No launch property and no MCP block in `application.yml` are needed;
the runtime finds the manifest on the classpath and registers the tools.

```bash
java -jar target/your-app.jar
```

The MCP server is served over HTTP with Server-Sent Events at `http://<host>:<port>/sse`, on your
application's own HTTP port (8080 by default). Point any MCP client that speaks the HTTP and SSE
transport at it; a stdio-only client needs a bridge.

### 4.2 Manifest discovery order

The runtime looks for the manifest in a fixed, documented order, and fails loudly if it finds none
(it will not start silently serving nothing):

1. an explicit `tibyan.capabilities` location, if you set one. If set but nothing resolves there,
   that is a loud failure, not a fall-through to the defaults.
2. `classpath:/tibyan/capabilities.json` (where the build plugin puts it).
3. `file:tibyan/capabilities.json`, relative to the working directory.

### 4.3 Runtime properties

| Property | Effect |
|---|---|
| `tibyan.capabilities` | Point the runtime at a specific manifest, overriding the default locations. Accepts Spring resource locations (`classpath:`, `file:`). |

```bash
# run against a manifest produced by the CLI, sitting outside the classpath
java "-Dtibyan.capabilities=file:generated/tibyan/capabilities.json" -jar target/your-app.jar
```

On Windows PowerShell, quote the whole property: `java "-Dtibyan.capabilities=file:..." -jar ...`.

### 4.4 Security note

The generated server executes real methods against real data, and v1 adds no authentication of its
own. Gating the `/sse` endpoint is your responsibility: bind it to localhost, or put it behind your
existing network controls or an authenticating reverse proxy. Read
[docs/SECURITY.md](SECURITY.md) before you expose it.

---

## 5. Connecting an MCP client

With the app running, the endpoint is at `http://localhost:8080/sse`.

```bash
# quick smoke test that the SSE endpoint is live
curl -N http://localhost:8080/sse

# scripted, standard-library-only handshake check (initialize, tools/list, tools/call)
python scripts/verify-mcp-sse.py
```

For the full handshake, a curl walk-through, and the Claude Desktop stdio-bridge configuration, see
[docs/CONNECTING-AN-MCP-CLIENT.md](CONNECTING-AN-MCP-CLIENT.md).

---

## 6. Building Tibyaan from source

Run from the repository root.

| Command | What it does |
|---|---|
| `mvn clean verify` | Full reactor: unit and golden tests across all modules, plus the end-to-end MCP test. |
| `mvn -q -DskipTests package` | Build all artifacts without running tests. |
| `mvn -q -pl tibyan-cli -am -DskipTests package` | Build just the CLI (and its dependencies) into the self-contained jar. |
| `mvn -Prelease clean verify` | Build with the release profile (source and javadoc jars, signing wired but skippable). Green locally without keys. |

Reproduce the example app end to end:

```bash
mvn -q -DskipTests package
java -jar examples/legacy-spring-app/target/legacy-spring-app-0.4.0-SNAPSHOT.jar
# then, from another shell:
curl -N http://localhost:8080/sse
```

The example app's build runs the Maven plugin, so packaging alone puts the manifest on its
classpath and the running app serves `getProduct` over genuine MCP.

---

## 7. Cheat sheet

```bash
# survey (read-only, safe)
tibyan scan <path>
tibyan scan <path> --report report.md
tibyan scan <path> --out app.ir.json

# generate artifacts
tibyan generate --backend mcp     --ir app.ir.json --out ./out
tibyan generate --backend openapi --ir app.ir.json --out ./out
tibyan validate --ir app.ir.json
tibyan version

# adopt (in your app's build)
mvn package                        # plugin writes tibyan/capabilities.json onto the classpath
mvn package -Dtibyan.strict=true   # CI: fail on any unparseable file
java -jar target/your-app.jar      # runtime serves the tools at /sse

# build Tibyaan itself
mvn clean verify
mvn -q -pl tibyan-cli -am -DskipTests package
```

Exit codes, everywhere: `0` success, `1` usage error, `2` validation failure, `3` provider or
backend error.
