# Volume 11: CLI Specification

Status: Stable for the MVP commands; future commands are listed as direction. The CLI is a thin caller of the pipeline (Volume 9). It parses arguments and invokes provide, validate, and generate. It contains no compiler logic of its own. It is built with picocli (Volume 16).

---

## 1. Overview

The CLI binary is `tibyan`. It exposes a small set of commands that map directly onto pipeline stages. Hosting the generated layer is the runtime's job inside the target application, so the CLI stops at generation.

## 2. Commands (MVP)

| Command | Purpose | Key options |
|---|---|---|
| tibyan scan | Survey a system into IR, resiliently (Volume 5 section 4.3) | path (positional), --out (write the discovered IR JSON), --report (write a Markdown survey report, section 3.4) |
| tibyan generate | Generate a backend from IR | --backend (for example mcp), --ir (IR file) or path, --out (output dir) |
| tibyan validate | Validate an IR without generating | --ir (IR file) |
| tibyan version | Print the tool and IR schema versions | none |

## 3. Examples and expected output

```text
$ tibyan scan ./legacy-spring-app --out inventory.ir.json
Scanning ./legacy-spring-app
Provider: spring (entity and service extractors)
Entities found (in parseable files): 1
Discovered (1):
  + Product: getProduct -> ProductService#findById
Totals: discovered 1, failed 0, skipped-by-parse 0, mis-bound 0
Wrote IR to inventory.ir.json
```

On a large, messy app the survey does not abort; it classifies every entity it can parse and names every file it cannot (Volume 5 section 4.3):

```text
$ tibyan scan ./pms-backend
Scanning ./pms-backend
Provider: spring (entity and service extractors)
Entities found (in parseable files): 43
Failed (43):
  - Building [UNMAPPED_FIELD_TYPE]: unmapped Java type 'Property' on field 'Building.property'.
  - Admin [NO_BACKING_METHOD]: no service method backs read capability 'getAdmin' for entity 'Admin'.
  ...
Skipped, unparseable (27):
  ~ .../VendorServiceImpl.java: (line 1,col 36) Parse error. Found  "nimport" <IDENTIFIER>, ...
  ...
Totals: discovered 0, failed 43 (by cause: UNMAPPED_FIELD_TYPE 24, NO_BACKING_METHOD 19), skipped-by-parse 27, mis-bound 0
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

### 3.2 Partial entities in the survey (partial-model integrity, pending ratification)

When an app extracts only in part, the provider skips fields it cannot map rather than vetoing the whole entity, and it closes the model by dropping references to entities that are absent (Volume 5, sections 4.7 to 4.9). An entity can therefore be discovered and still carry skipped fields; the survey discloses this. A discovered partial entity gains a qualifier, and the totals gain a partial dimension, distinct from discovered and failed so historical rows stay comparable:

```text
$ tibyan scan ./erp-backend
Scanning ./erp-backend
Provider: spring (entity and service extractors)
Entities found (in parseable files): 25
Discovered (16):
  + getProduct -> ProductService#findById (partial: 3 fields skipped)
  + getContactType -> ContactTypeServiceImpl#findById
  ...
Failed (9):
  - EmailTemplate [NO_IDENTIFIER]: @Id 'type' is an enum, which maps to no bindable primitive.
  - User [AMBIGUOUS_BINDING]: backed by more than one candidate ...
  ...
Totals: discovered 16, failed 9 (by cause: NO_BACKING_METHOD 7, NO_IDENTIFIER 1, AMBIGUOUS_BINDING 1), skipped-by-parse 0, partial 13 (23 fields skipped), mis-bound 0
```

The `discovered`, `failed`, `skipped-by-parse`, and `mis-bound` figures keep their historical meaning; `partial` is a new dimension that never inflates discovered and never masks a mis-binding. Absence of a qualifier means the entity's shape is complete.

### 3.3 List capabilities in the survey (phase E4, list reads, RATIFIED and BUILT 2026-07-02)

Until E4, an entity yielded at most one capability (a read-by-id), so `discovered` counted both capabilities and discovered entities at once. E4 breaks that one-to-one link: an entity can now yield a read-by-id and a list, a list and no read-by-id (for example when its identifier does not map, so `getEmailTemplate` fails but `listEmailTemplate` is clean), or several list candidates that refuse. The survey extends additively, and `discovered` stays a capability count, now split by kind so historical comparisons still hold:

```text
$ tibyan scan ./erp-backend
...
Discovered (25):
  by-id (16):
    + Contact: getContact -> ContactServiceImpl#findById (partial: 3 fields skipped)
    ...
  list (9):
    + listContact -> ContactServiceImpl#findAll (paged)
    + listEmailTemplate -> EmailTemplateServiceImpl#findAll (paged)
    ...
Failed (9):
  - EmailTemplate [NO_IDENTIFIER]: @Id 'type' is an enum, which maps to no bindable primitive.
  - User [AMBIGUOUS_BINDING]: backed by more than one candidate ...
  ...
List-refused (1):
  - ProductCategory [LIST_AMBIGUOUS]: list capability 'listProductCategory' has more than one list-all candidate: [ProductCategoryServiceImpl#findAll, ProductCategoryServiceImpl#findAll].
Deferred list reads (56):
  ~ ProductServiceImpl#findAll(Pageable): body is not a single return of the framework findAll; a transformed, filtered, or multi-statement body is deferred (condition 6).
  ...
Totals: discovered 25 (by-id 16, list 9), failed 9 (by cause: NO_IDENTIFIER 1, NO_BACKING_METHOD 6, AMBIGUOUS_BINDING 2), list-refused 1, skipped-by-parse 0, partial 13 (22 fields skipped), mis-bound 0
```

Note that `EmailTemplate` appears in both `list (9)` and `Failed (9)`: its list-all read is clean, so `listEmailTemplate` is discovered, while its by-id read fails NO_IDENTIFIER. This is exactly the decoupling E4 introduces, and it is why the two ledgers are kept on separate axes.

The contract, stated so it is not a silent redefinition:

- `discovered` remains the count of emitted capabilities. From E4 on it is annotated by kind, `discovered N (by-id A, list B)`, and `A` is directly comparable to the pre-E4 `discovered` figure, so every historical row stays readable. `A` plus `B` equals `N`. The `failed` figure keeps its historical meaning too: it counts failed by-id outcomes at the entity level, so `by-id A` plus `failed` still equals the entity count, and the by-id ledger stays a clean partition of the entities exactly as before E4.
- A refused list candidate is either its own dimension or a named deferral, and neither disturbs the by-id `failed` count. A `LIST_AMBIGUOUS` (two or more list-all methods for one entity) is reported on a separate `list-refused` line and totalled separately, precisely so an entity that discovers by-id but has an ambiguous list (ProductCategory) is not double-counted against the entity partition. A filtered, view-returning, or transformed list read is out of E4 scope by design, not a failure of it, so it is reported per method under "Deferred list reads", never counted in `failed` or `list-refused` and never counted as a missed capability, exactly as 4.2's non-primary-key reads are named rather than failed.
- The health metric is unchanged: discovered rising while mis-bound stays zero. `mis-bound` keeps its meaning, extended to lists: a list capability is mis-bound if it is bound to a method returning a collection of the wrong entity, or to a method that presents a filtered subset as the whole collection (Volume 5, section 4.10.9). Both are counted; either one stops all phase work.
- A list capability may itself be partial, when its element entity is partial (its schema is incomplete). The `partial` dimension counts the entity once, as today; it is orthogonal to whether the entity yielded a by-id capability, a list capability, or both.

Writing an artifact is where strictness returns. With `--out`, the survey prints as above and then the discovered IR is validated before it is written; if it does not validate, nothing is written and the command exits 2, with the survey already shown:

```text
$ tibyan scan ./some-app --out out.ir.json
Scanning ./some-app
... survey ...
Validation: FAILED
  - <located validation error on the discovered IR>
Exit code 2
```

With model closure (Volume 5, section 4.7) a partial model is always valid, so `--out` writes it and generation succeeds even when the app extracted only in part; the exit-2 validation failure above is reserved for a genuinely malformed discovered IR, which closure makes far rarer.

### 3.4 The survey report (`--report`), a shareable map of AI-operability (RATIFIED 2026-07-07)

The console survey (sections 3.1 to 3.3) is written for a terminal: it scrolls past, and it is not something an adopter can hand to a colleague or keep as a record. A first-time user's honest result is often mostly refusals, and a wall of console text reads as failure unless the reader already holds the refusals-are-the-product frame. So `tibyan scan` gains an optional `--report <file.md>` that writes the same survey as a human-readable Markdown document: a shareable map of what an AI agent can operate in the application today and, with the located reason, what it cannot yet.

Design decision, ratified before the build (rule 12), recorded here first. The report is read-only and purely additive: it renders the exact `ScanReport` the console already prints, so it discovers nothing new, changes no count, and makes no semantic claim. It writes only the file named, never touches the source or the build, and is orthogonal to `--out` (which writes the IR JSON); either, both, or neither may be given.

The report has a fixed, deterministic structure so it is golden-stable and diffable across runs (no timestamps, no absolute paths in the body):

- A one-paragraph framing that states the guarantee plainly: every listed capability is structurally proven and bound to a named real method, and every refusal is named rather than guessed.
- A summary table: operable capabilities (by-id and list), refused, list-refused, files skipped, partial entities, and `mis-bound` (always stated, always 0 by construction).
- A "what an agent can operate today" section listing each discovered capability with its binding, split into read-by-id and list-all exactly as the console splits them.
- A "what was refused, and why" section grouping the failures with their located first-sentence reason, plus the files skipped and any deferred (filtered, view-returning, transformed) reads when present, each named.
- A single "most common blocker" line naming the cause that blocked the most entities (or the parse-skip wall when that is larger), so the reader sees the one highest-leverage change without the tool claiming anything it cannot prove.

Honesty (the never-claim gate). The report renders proven facts and nothing else. It adds no capability, and it never presents a refused or deferred read as available. The "most common blocker" line is a descriptive tally of the failure causes already in the survey, a count, not a recommendation the tool cannot substantiate and not a promise that the blocked reads would be safe to expose once unblocked. `mis-bound` is unaffected, because nothing about discovery changes; the report is a second rendering of the same model, alongside the console and the manifest.

Forecast (from the existing benchmark rows, not new runs): on the example app the report lists `getProduct` as the one operable capability with no refusals and no blocker; on erp-backend it lists 16 by-id and 9 list operable with 9 refused, `NO_BACKING_METHOD` the most common blocker; on pms-backend it lists `getUnit` and `getLease` operable with 41 refused, `NO_BACKING_METHOD` (38) the most common blocker. `mis-bound 0` on all three.

### 3.1 The generated manifest is what the runtime hosts

Generation, whether from the CLI or at build time, writes a capabilities manifest, `tibyan/capabilities.json`. That manifest is the artifact the runtime hosts. The CLI's `tibyan generate --backend mcp` writes it (plus the generated tool files) into the chosen `--out` directory, for example `./generated/<module>-mcp/`.

Recommended adoption: generate at build time. The `tibyan-maven-plugin` runs the same pipeline during the app's own build and writes the manifest onto the classpath (`target/classes/tibyan/capabilities.json`). Add it to the app's build once:

```xml
<plugin>
  <groupId>io.github.tibyaan-org</groupId>
  <artifactId>tibyan-maven-plugin</artifactId>
  <version>...</version>
  <executions>
    <execution><goals><goal>generate</goal></goals></execution>
  </executions>
</plugin>
```

The goal binds to `generate-resources`, scans the module (by default `${project.basedir}`, matching `tibyan scan <module>`), and produces a manifest through the same `TibyanCompiler` and `McpBackend` the CLI uses, not a second generator. With the `tibyan-runtime-spring` dependency on the classpath, the runtime finds the manifest there automatically, with no launch property and no MCP config in `application.yml` (Volume 7, section 4). Nothing is committed by hand.

#### 3.1.1 The plugin is resilient by default, so adopting it cannot break a build (RATIFIED 2026-07-07)

Design decision, ratified before the build (rule 12), recorded here first. The problem it fixes: the build plugin ran the provider's strict `provide` path, so a single source file the parser could not read at the Java 17 language level (newer-than-17 syntax, or a genuinely malformed file) aborted the Mojo and failed `mvn package`. For a tool whose defining promise is that it changes nothing in the host system, breaking the host's build on adoption is the wrong default, and it punishes exactly the messy, real, aging applications the tool exists to serve (the pms benchmark has 27 such files, so its build would have failed on `mvn package`).

The ratified behavior:

- **Default (resilient).** The plugin runs the same resilient `scan` the CLI's `tibyan scan` runs (`SpringCapabilityProvider#scan`, returning a `ScanReport`), then generates the manifest from the discovered model. An unparseable file is skipped and named, never fatal. Adopting the plugin therefore cannot break a build on an unparseable file. This makes the plugin's output byte-identical to `tibyan scan` for the same source, and identical to `tibyan generate` whenever the source is fully parseable (the two paths share one discovery rule set; resilience changes only whether an unparseable file aborts, never what is discovered, so `mis-bound` stays 0 either way).
- **Loudness relocated, not softened (the E1 discipline, Volume 5 section 4.3).** A resilient build is not a silent one. The plugin logs, at WARN, every file it skipped with its located reason, and it logs a clear line when it generated zero capabilities (so an empty manifest never passes unnoticed). A partial manifest can never masquerade as a complete one, because the build log names every file that was not read. This is the same standard the CLI survey already meets.
- **Strict opt-in.** Setting `<strict>true</strict>` on the plugin (or `-Dtibyan.strict=true`) restores the old behavior: the plugin runs the strict `provide` path and fails the build on any unparseable file or hard failure. This is for adopters who want their build to gate on a fully-parseable source tree (for example in CI). It is off by default.
- **What still fails the build in the default path.** A genuinely invalid discovered IR (validation failure, which model closure in E5 makes rare) fails the build, because validation is the pipeline's gate and shipping a malformed manifest would be worse than failing. A scan that cannot run at all (the source root is missing, is not a directory, or cannot be walked) also fails the build. Only per-file unparseability is downgraded from fatal to a named skip.

Honesty analysis (the never-claim constraint, the absolute gate): resilience adds no capability and weakens no proof. Every capability in a partial manifest is discovered by the unchanged structural rules and bound to a method the tool proved backs it; nothing is guessed, and no filtered or unproven read is emitted. The only thing that changes is that an unparseable file no longer aborts the whole build, and its absence is disclosed loudly. `mis-bound 0` is not at risk, because the discovery and matching rules are untouched.

Pre-registered forecast (rule 12, measured against a stated prediction):

- Example app (`examples/legacy-spring-app`, fully parseable): byte-identical manifest to today (`getProduct` only), the `EndToEndMcpIT` end-to-end test green, and the MCP golden unchanged. Resilient and strict produce the same output on a clean tree.
- pms-backend (27 unparseable files): the strict path fails `mvn package` today; the resilient default succeeds, generating the 2 discovered capabilities (`getUnit`, `getLease`) and logging 27 named skips. This is inferred from the identical `scan` code path the CLI already runs on pms (second-app-trial.md section 8), not a new plugin run against the private app.
- erp-backend (0 unparseable files, valid model post-E5): resilient and strict both generate all 25 capabilities, byte-identical.

The CLI remains for manual and inspection use: run `tibyan scan` and `tibyan generate` to see the IR and the generated output, or to produce a manifest for a host that is not built with Maven. You can also point the runtime at a manifest file for an ad hoc run with `-Dtibyan.capabilities=file:./generated/<module>-mcp/tibyan/capabilities.json`, a relative path, never a hardcoded absolute or machine-specific path; the explicit property overrides the classpath default.

The manifest that ships under `tibyan-backend-mcp/src/test/resources/...` is only a golden fixture for the backend's tests. It is not a runtime artifact. Nothing should point the runtime at it, or at any fixed location such as a hand-placed file in a system directory; the only manifest the runtime should host is one a generation run produces, from the CLI or the build plugin.

## 4. Exit codes

| Code | Meaning |
|---|---|
| 0 | Success |
| 1 | Usage error (bad arguments) |
| 2 | Validation failure |
| 3 | Provider or backend error |

### 4.1 What each code means for `tibyan scan` (Volume 5 section 4.3)

`scan` is a resilient survey, so its exit codes distinguish "the scan could not run" from "the scan ran and found problems in the source":

- 0: the survey completed. This is the code whatever it discovered, including the honest "discovered 0, many failed" case: per-entity failures and unparseable files are reported, not fatal. If `--out` was given, the discovered IR validated and was written.
- 2: only when `--out` was given and the discovered IR did not validate, so no file was written. The survey still printed in full.
- 3: the scan could not run at all: the source root is missing, is not a directory, or cannot be walked.

Surveying is not the same as producing an artifact. `tibyan generate` stays strict: it runs the provider's strict path, which refuses a scan with any unparseable file or hard failure and exits 3, so a hand-run generation never produces a manifest from a partial read. A per-entity failure being exit 0 under `scan` is a survey result; the same source under `generate` is a loud refusal.

The build plugin is the one exception, and deliberately so (section 3.1.1, ratified 2026-07-07): it is resilient by default, because adopting it must never break the host's build on an unparseable file. It runs the same resilient `scan` the CLI runs, skips and names unparseable files rather than aborting, and generates from what it discovered, logging every skip loudly so the manifest is never a silent partial. Setting `strict=true` on the plugin opts back into the strict `provide` path (fail the build on any unparseable file) for adopters who want their build to gate on a fully-parseable tree. The strict CLI `generate` is unchanged.

## 5. Future commands

| Command | Purpose |
|---|---|
| tibyan diff | Show what changed in the IR or the generated output between two runs |
| tibyan init | Scaffold a Tibyan configuration in a project |
| tibyan serve | Run a generated layer locally for testing, outside a host application |
| tibyan list | List available providers and backends discovered as plugins |

Future commands follow the same rule: they call the pipeline or the plugin registry, and they hold no compiler logic themselves.

## 3.5 `tibyan draft` and the reviewed tier in the survey (spike DL-0 built 2026-09-23; phase DL-1 designed 2026-09-23, PENDING RATIFICATION)

`tibyan draft <path> [--out <drafts.json>]` writes `tibyan/drafts.json`: one entry per public method of every
concrete `@Service` class, labeled VERIFIED, ATTESTED, READ, WRITE or UNKNOWN with the located reason (built in
spike DL-0, Volume 13 section 3). DL-1 (Volume 5, section 4.17.7) adds the merge on re-run and the fingerprint
stamp, and makes the file the record: review is editing it, there is no review command, and `tibyan scan` reports
`reviewed N` beside `discovered` and `attested`, never summed, plus the pending and refused draft counts with each
refusal's cause and target. Exit codes unchanged: 0 written, 3 the source could not be read.
