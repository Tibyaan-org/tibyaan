# Tibyaan status and resume point

Status: MVP complete, plus three hardening steps, the second-app trial, planning reconciled, Tier 2 phase E1 (parse resilience), and the E2 design half (rule written, pending ratification). Work paused here. Last updated 2026-07-02.

Recent changes (most recent first):

- Tier 2 phase E2, design half, is done: the DTO-aware read discovery rule is written into Volume 5, section 4.2, from a read-only evidence study of pms-backend, before any code. The rule anchors on structural evidence only (a by-primary-key load proven by the framework contract, direct or one delegation hop; one mapping step with the loaded entity as sole argument; in-tree resolution; names carry no weight), refuses every trap case found live in the benchmark, tightens eligibility to close the composite-key half-read hazard, keeps the capability output as ENTITY_REF(E) with the view recorded as an annotation, and pre-registers fixtures F1 to F12 plus a benchmark forecast (experiment doc, section 8.2). Design finding, stated plainly: E2's build alone cannot raise discovered above 0 on this benchmark, because none of the 19 blocked entities has a read in a parseable service; the first rise comes from E2 plus E3 together (forecast: up to 2, Unit and Lease). The rule and the sequencing choice await ratification; no code was written.
- Tier 2 phase E1, parse resilience and scan scope, is done. The Spring provider now has two entry points (Volume 5, section 4.3): a resilient `scan` (what `tibyan scan` runs) that walks the whole source, parses collect-and-continue at the Java 17 language level, and classifies every entity it can parse while naming every file it cannot; and the unchanged strict `provide` (what the build and `tibyan generate` run) that still refuses any unparseable file or hard failure. `tibyan scan examples/pms-backend` now completes end to end at exit 0 with a per-entity outcome for all 43 entities (discovered 0, failed 43, 27 files skipped by parse) instead of aborting on the first bad file; mis-bound stayed 0. `mvn clean verify` is green. Benchmark row and interpretation: docs/experiments/second-app-trial.md, section 8; exit-code semantics: Volume 11, section 4.1.
- Planning reconciled with reality. Every volume was audited against what was actually built, and stale statements were corrected (the audit table is docs/PLAN-REVIEW.md). Volume 13 is rewritten as the single plan in three tiers: Tier 1 the completed record, Tier 2 the evidence-based next phases (E1 to E4, derived from the trial's ranked gaps), Tier 3 direction only. No code changed.
- Direction A, the second-app trial, is done (2026-07-02). The unchanged CLI was run against a large real app, pms-backend (43 entities, 14 domain modules). Result: discovered 0, mis-bound 0, failed loudly 43, every failure a located error. The one forbidden outcome, a silent wrong binding, did not occur. Gaps were ranked by capabilities blocked: (1) parse resilience, language level, and scan scope; (2) DTO/VTO-returning reads unmatched by the discovery rule; (3) association fields unmapped; (4) list/filter/multi-argument reads unmodeled; (5) no-backing-method and composite keys. Full evidence: docs/experiments/second-app-trial.md, which is now the standing benchmark (Volume 13, section 5).
- Direction B step three: the manifest is generated at build time, not copied by hand. A new `tibyan-maven-plugin` runs the existing pipeline (same `TibyanCompiler` and `McpBackend` the CLI uses) during the app's build, bound to `generate-resources`, and writes `tibyan/capabilities.json` onto the classpath output. The example app no longer commits a manifest; its build generates one from its own source, byte-identical to `tibyan generate` (asserted by EndToEndMcpIT). Adopting Tibyan is now: add the dependency, add the build plugin once, build. Details in Volume 11 and Volume 7, section 8 (gap 2).
- Direction B step two: a truly untouched app needs only the `tibyan-runtime-spring` dependency. The starter now supplies the MCP server name and version as overridable defaults (an `EnvironmentPostProcessor`), and the runtime discovers the manifest by a fixed order (explicit `tibyan.capabilities`, then `classpath:/tibyan/capabilities.json`, then `file:tibyan/capabilities.json`), failing loudly if none is found. The example app proves it: no `spring.ai.mcp.server` block in its `application.yml`, the manifest shipped as a classpath resource at `tibyan/capabilities.json`, no `-Dtibyan.capabilities`, and getProduct still returns the real product over genuine MCP. Details in Volume 7, sections 4 and 8 (gap 2, now closed).
- Direction B step one: the capability binding is discovered from the real `@Service` method, not assumed by the `get<Entity>` to `<Entity>Service#findById` convention. The matching rule and what is still assumed are in Volume 5, section 4.1. For the example app the discovered binding is still `ProductService#findById`; the difference is that it is now read from the source, so a second app with a differently named service or finder is discovered correctly instead of silently mis-bound.

To resume: read this file, then CLAUDE.md, then Volume 13 (the tiered plan). E1 is done and E2's rule is designed (Volume 5, section 4.2) but not ratified. The next step is a human decision, not a build: ratify or challenge the 4.2 rule, and choose the sequencing (build E2 now against the section 8.2 forecast, or design and build E3 first and measure the two jointly). No E2 or E3 code before that choice. Volume 7 section 8 holds the runtime-side gaps; the provider-side gaps live in the experiment doc and Volume 5, sections 4.1 and 4.2.

---

## Where things stand

All MVP phases are complete on the main branch.

| Phase | What it delivered |
|---|---|
| -1 Spike | In-context MCP server on a real Spring app, a real bean call proven |
| P0 | tibyan-core (Capability IR plus validation, zero dependencies) and tibyan-plugin-api |
| P1 | tibyan-provider-spring (scans source to IR, EntityExtractor only) |
| P2 | tibyan-backend-mcp (IR to MCP tools) and the graduated tibyan-runtime-spring |
| P3 | tibyan-pipeline and tibyan-cli (scan, generate, validate, version) |

The MVP works end to end. `mvn clean verify` is green: unit and golden tests across all modules, plus the failsafe end-to-end test (tibyan-it, EndToEndMcpIT) that generates through the CLI and then drives the genuine MCP protocol (initialize, tools/list, tools/call) against the real example app, returning the real product. The terminal flow has also been verified live, including a genuine MCP call against the running app.

---

## Reproduce the MVP

All paths are relative to the repository root. Build once. The example app's build runs the `tibyan-maven-plugin`, which scans the app's source and generates `tibyan/capabilities.json` onto its classpath (`target/classes/tibyan/capabilities.json`), so packaging is all that is needed:

```
mvn -q -DskipTests package
```

Then launch the app. It needs no manifest property, and its `application.yml` has no MCP block; the manifest is on the classpath because the build put it there:

```
java -jar examples/legacy-spring-app/target/legacy-spring-app-0.1.0-SNAPSHOT.jar
```

The runtime finds the manifest on the classpath (verified: it logs `loaded 1 capability(ies) from class path resource [tibyan/capabilities.json]` then `registered 1 MCP tool(s): [getProduct]`, and Spring AI logs `Registered tools: 1`). The app starts on port 8080 with the MCP server at `http://localhost:8080/sse`. An MCP client (for example Claude Desktop) that calls getProduct with id 5 gets the real product, `{"id":5,"name":"Wireless Mouse","price":29.99}`, read from the real H2 store through ProductService.findById.

The CLI remains for manual and inspection use, and produces the same manifest:

```
java -jar tibyan-cli/target/tibyan-cli-0.1.0-SNAPSHOT-jar-with-dependencies.jar scan examples/legacy-spring-app --out ./generated/legacy.ir.json

java -jar tibyan-cli/target/tibyan-cli-0.1.0-SNAPSHOT-jar-with-dependencies.jar generate --backend mcp --ir ./generated/legacy.ir.json --out ./generated/legacy-mcp
```

To point the runtime at a manifest file for an ad hoc run, pass `-Dtibyan.capabilities`; on Windows PowerShell quote it: `java "-Dtibyan.capabilities=file:generated/legacy-mcp/tibyan/capabilities.json" -jar examples/legacy-spring-app/target/legacy-spring-app-0.1.0-SNAPSHOT.jar`.

---

## Open notes

Known gaps and deferred work are recorded in `docs/volume-07-runtime.md` section 8. Plugin discovery status (not real SPI yet) is in `docs/volume-10-plugin-system.md` section 2. The binding is no longer assumed: it is discovered from the real service method (Volume 5, section 4.1), so the convention is no longer the first thing that breaks on a second app. What is still assumed there: only single-id-parameter reads are modeled, the capability name is still the `get<Entity>` convention, and the identifier is the first `@Id` field.

---

## Next directions (the plan now lives in Volume 13)

The old Direction A (trial a second app) and Direction B (harden, three steps) are both done, Tier 2 phase E1 (parse resilience) is done, and E2's design half is done; their results are in the recent-changes list above. What drives the work is the tiered roadmap in Volume 13, and the next step is:

- Ratify the E2 rule (Volume 5, section 4.2) and choose the sequencing. The design finding (Volume 13, phase E2; experiment doc section 8.2): E2's build alone cannot raise discovered above 0 on the benchmark, so either build E2 now measured against the forecast (diagnostics and cause shift, discovered still 0), with discovered greater than zero becoming the joint E2 plus E3 exit, or design and build E3 first and measure the two jointly. Either way the E2 build must pass the pre-registered fixtures F1 to F12 before the benchmark re-run.

After E2, in order and each gated by its exit criterion: E3 (associations, where E1 showed 24 entities blocked by unmapped field types, including enums and Double alongside true associations; E2's forecast expects Unit and Lease to flip once E3 clears their fields), E4 (list, filter, and multi-argument reads, the first deliberate IR growth). After each phase, re-run the benchmark and append the dated entry to the experiment doc. Mis-bound greater than zero at any point becomes the top priority over everything.

Runtime-side deferred work is unchanged and stays listed until closed: health, observability, and lifecycle (Volume 7, section 8, gap 7); real SPI plugin discovery (Volume 10, section 2). These sit in Volume 13, Tier 3, behind the Tier 2 semantic phases.
