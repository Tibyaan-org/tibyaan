# Tibyaan status and resume point

Status: MVP complete, plus three hardening steps, plus the second-app trial; planning reconciled. Work paused here. Last updated 2026-07-02.

Recent changes (most recent first):

- Planning reconciled with reality. Every volume was audited against what was actually built, and stale statements were corrected (the audit table is docs/PLAN-REVIEW.md). Volume 13 is rewritten as the single plan in three tiers: Tier 1 the completed record, Tier 2 the evidence-based next phases (E1 to E4, derived from the trial's ranked gaps), Tier 3 direction only. No code changed.
- Direction A, the second-app trial, is done (2026-07-02). The unchanged CLI was run against a large real app, pms-backend (43 entities, 14 domain modules). Result: discovered 0, mis-bound 0, failed loudly 43, every failure a located error. The one forbidden outcome, a silent wrong binding, did not occur. Gaps were ranked by capabilities blocked: (1) parse resilience, language level, and scan scope; (2) DTO/VTO-returning reads unmatched by the discovery rule; (3) association fields unmapped; (4) list/filter/multi-argument reads unmodeled; (5) no-backing-method and composite keys. Full evidence: docs/experiments/second-app-trial.md, which is now the standing benchmark (Volume 13, section 5).
- Direction B step three: the manifest is generated at build time, not copied by hand. A new `tibyan-maven-plugin` runs the existing pipeline (same `TibyanCompiler` and `McpBackend` the CLI uses) during the app's build, bound to `generate-resources`, and writes `tibyan/capabilities.json` onto the classpath output. The example app no longer commits a manifest; its build generates one from its own source, byte-identical to `tibyan generate` (asserted by EndToEndMcpIT). Adopting Tibyan is now: add the dependency, add the build plugin once, build. Details in Volume 11 and Volume 7, section 8 (gap 2).
- Direction B step two: a truly untouched app needs only the `tibyan-runtime-spring` dependency. The starter now supplies the MCP server name and version as overridable defaults (an `EnvironmentPostProcessor`), and the runtime discovers the manifest by a fixed order (explicit `tibyan.capabilities`, then `classpath:/tibyan/capabilities.json`, then `file:tibyan/capabilities.json`), failing loudly if none is found. The example app proves it: no `spring.ai.mcp.server` block in its `application.yml`, the manifest shipped as a classpath resource at `tibyan/capabilities.json`, no `-Dtibyan.capabilities`, and getProduct still returns the real product over genuine MCP. Details in Volume 7, sections 4 and 8 (gap 2, now closed).
- Direction B step one: the capability binding is discovered from the real `@Service` method, not assumed by the `get<Entity>` to `<Entity>Service#findById` convention. The matching rule and what is still assumed are in Volume 5, section 4.1. For the example app the discovered binding is still `ProductService#findById`; the difference is that it is now read from the source, so a second app with a differently named service or finder is discovered correctly instead of silently mis-bound.

To resume: read this file, then CLAUDE.md, then Volume 13 (the tiered plan). The next phase is Tier 2, phase E1. Volume 7 section 8 holds the runtime-side gaps; the provider-side gaps live in the experiment doc and Volume 5, section 4.1.

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

The old Direction A (trial a second app) and Direction B (harden, three steps) are both done; their results are in the recent-changes list above. What replaces them is the tiered roadmap in Volume 13, and the next phase is:

- Tier 2, phase E1: parse resilience and scan scope. Raise the JavaParser language level to the app's Java version, collect-and-continue per file (every skipped file named, never silent), and scope the walk to relevant source. Exit criterion: the pms-backend whole-app scan completes end to end with per-entity outcomes instead of a module-level abort. Evidence: the parse gate blocked 38 of 43 entities before extraction began (docs/experiments/second-app-trial.md, gap 1).

After E1, in order and each gated by its exit criterion: E2 (DTO-aware read discovery, rule designed into Volume 5 section 4.2 before any code), E3 (associations), E4 (list, filter, and multi-argument reads, the first deliberate IR growth). After each phase, re-run the benchmark and append the dated entry to the experiment doc. Mis-bound greater than zero at any point becomes the top priority over everything.

Runtime-side deferred work is unchanged and stays listed until closed: health, observability, and lifecycle (Volume 7, section 8, gap 7); real SPI plugin discovery (Volume 10, section 2). These sit in Volume 13, Tier 3, behind the Tier 2 semantic phases.
