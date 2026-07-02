# Tibyaan status and resume point

Status: MVP complete, plus three hardening steps, the second-app trial, planning reconciled, Tier 2 phases E1, E3, and E2 all built with the E2 view finding closed, and now a third-app generalization trial. Two standing benchmarks (pms-backend, erp-backend); mis-bound 0 on both. Work paused here. Last updated 2026-07-02.

Recent changes (most recent first):

- Third-app trial (erp-backend), a read-only generalization experiment, no tool change (docs/experiments/third-app-trial.md). A third real Spring app (25 entities, single module, UUID and String ids, entity-returning by-id reads, direct Spring Data repositories, dense collection associations, an `@Embeddable` value type) was scanned. Result: exit 0, discovered 7 (all via rule 4.1, entity-returning), failed 18 (UNMAPPED_FIELD_TYPE 13 = collections 10 + embeddable `Address` 3; NO_BACKING_METHOD 4; AMBIGUOUS_BINDING 1), skipped-by-parse 0, mis-bound 0 (all 7 hand-verified). Transfer verdict: the oldest, most general rule (4.1) carried all discoveries; the pms-derived rule 4.2 fired zero times (erp returns entities, not views); E1 parse resilience held cleanly; E3 revealed pms-shaped assumptions, with collections (deferred, cheap on pms) and embeddables (never seen) now the top blockers, and FLOAT inert (erp uses BigDecimal). Discovery does not fully compound: coverage is idiom-bound (28 percent here), and the two benchmarks discover through disjoint rules. One IR-consistency finding: E3 emits dangling `ENTITY_REF`/`Relation` to entities that fail extraction, so `--out` fails validation and generation is blocked on this app; a fix is described and deferred. erp-backend is now a second standing benchmark. `.gitignore` gained `/examples/erp-backend` (replacing an over-broad `/examples` line).
- The E2 view finding is closed (2026-07-02), backend only. The MCP backend now surfaces a capability's `view` annotation in the generated manifest as a `view` field after `output`, plus a one-sentence description clause; capabilities without a view are unchanged, so the example-app golden stays byte-identical. Regenerating pms-backend confirms `getUnit` carries `view: UnitVTO` and `getLease` carries `view: LeaseVTO`. One-off for the view annotation, not a generic passthrough (Volume 6, section 3.1). The runtime tolerates the additive field explicitly (`@JsonIgnoreProperties` on `CapabilityDescriptor`, proven by a strict-mapper loader test); IR, provider, and runtime behavior are unchanged. `mvn clean verify` green; discovery numbers unchanged (experiment doc, section 8.5, finding-closed note).
- Phase E2, DTO-aware read discovery, is built (2026-07-02), conditions 0 to 10 exactly as ratified (Volume 5, section 4.2). The Spring provider now discovers a read backed by a view-returning service method when the structural evidence holds: a by-primary-key load of the entity (direct, or one hop through a hand-written repository to a Spring Data repository of it), a verbatim id argument, no second repository access and no mutation, one of the two body shapes, and a single mapping step whose sole argument is the loaded entity and whose callee resolves in-tree to (entity) to view. Names carry no weight. A composite key (eligibility condition 0) surfaces the new COMPOSITE_KEY cause. Measured jointly with E3, the pms-backend benchmark met the ratified exit exactly: discovered 2 (getUnit to UnitServiceImpl#findById view UnitVTO, getLease to LeaseServiceImpl#findById view LeaseVTO, both P1a one-hop), NO_BACKING_METHOD 38 with named near-misses, COMPOSITE_KEY 3, skipped 27, mis-bound 0. Fixtures F1 to F12 green; `mvn clean verify` green; golden manifest byte-identical; generation produces a valid manifest for the two capabilities. One finding: the `view` annotation is in the IR (section 4.2.5) but not yet in the MCP manifest, a deferred backend enhancement. Full detail: experiment doc, section 8.5.
- Phase E3 is built (2026-07-02), all three rules exactly as ratified. FLOAT joined the closed primitive set in `tibyan-core` (still zero-dependency); the Spring provider now maps `@ManyToOne`/`@OneToOne` fields to an `ENTITY_REF` field plus a deduplicated, ordered `Relation`, maps enum fields to STRING carrying an `enum` value-set annotation, and maps the four Java floating types to FLOAT, all resolved structurally through a new `TypeRegistry` (never by name). A FLOAT `@Id` forms no read intent. Fixtures A1 to A9, B1 to B5, C1 to C5 are green, `mvn clean verify` is green with the golden manifest byte-identical, and the pms-backend benchmark matched the E3-alone forecast exactly: discovered 0, mis-bound 0, skipped 27, failed 43 all NO_BACKING_METHOD, with the IR gaining 19 relations, 21 enum value sets, and 8 FLOAT fields (experiment doc, section 8.4). Discovered holds at 0 as forecast; the first rise comes from the E2 build.
- The three E3 type rules are ratified (2026-07-02): associations (Volume 5, section 4.4), enums (section 4.5), and inexact numerics (section 4.6, adding FLOAT to the closed set). Volume 4 now also records a growth bar for the closed type set: a new primitive enters only on counted benchmark evidence, only when it names the source truthfully, and never as a coercion target from an exact type. The E3 build follows in this session.
- Tier 2 phase E3, design half, is done as three separate type-system decisions (Volume 5, sections 4.4 to 4.6), from a full field census of the 24 extraction-blocked entities that corrected E1's first-cause split (notably: 8 `Double` fields on 7 entities, not 3, including both joint-exit targets). (a) Associations: pure provider mapping onto the existing ENTITY_REF and Relation, no IR growth, to-one only, in-tree resolution, deduplicated relations. (b) Enums: STRING plus an `enum` annotation with the constant names, no IR growth, enum-ness proven by finding the declaration. (c) Inexact numerics: the census flipped this from refusal to deliberate IR growth, FLOAT enters the closed set (Volume 4, section 4, flagged), because refusal would gate 7 of 24 entities and the ratified joint exit itself. Fixtures A1 to A9, B1 to B5, C1 to C5 pre-registered; forecasts in the experiment doc, section 8.3 (after E3 alone: discovered still 0, all 43 to NO_BACKING_METHOD, the IR gains full shapes and 19 relations; after E3 plus E2: discovered 2, getUnit and getLease, COMPOSITE_KEY 3, mis-bound 0). All three rules pending ratification; no code.
- Three decisions ratified (2026-07-02): the Volume 5, section 4.2 DTO rule stands as written; E3 is designed and built before the E2 build, with the two measured jointly (discovered greater than zero and mis-bound exactly zero); and E3 is split into three separately-argued type decisions.
- Tier 2 phase E2, design half, is done: the DTO-aware read discovery rule is written into Volume 5, section 4.2, from a read-only evidence study of pms-backend, before any code. The rule anchors on structural evidence only (a by-primary-key load proven by the framework contract, direct or one delegation hop; one mapping step with the loaded entity as sole argument; in-tree resolution; names carry no weight), refuses every trap case found live in the benchmark, tightens eligibility to close the composite-key half-read hazard, keeps the capability output as ENTITY_REF(E) with the view recorded as an annotation, and pre-registers fixtures F1 to F12 plus a benchmark forecast (experiment doc, section 8.2). Design finding, stated plainly: E2's build alone cannot raise discovered above 0 on this benchmark, because none of the 19 blocked entities has a read in a parseable service; the first rise comes from E2 plus E3 together (forecast: up to 2, Unit and Lease). The rule and the sequencing choice await ratification; no code was written.
- Tier 2 phase E1, parse resilience and scan scope, is done. The Spring provider now has two entry points (Volume 5, section 4.3): a resilient `scan` (what `tibyan scan` runs) that walks the whole source, parses collect-and-continue at the Java 17 language level, and classifies every entity it can parse while naming every file it cannot; and the unchanged strict `provide` (what the build and `tibyan generate` run) that still refuses any unparseable file or hard failure. `tibyan scan examples/pms-backend` now completes end to end at exit 0 with a per-entity outcome for all 43 entities (discovered 0, failed 43, 27 files skipped by parse) instead of aborting on the first bad file; mis-bound stayed 0. `mvn clean verify` is green. Benchmark row and interpretation: docs/experiments/second-app-trial.md, section 8; exit-code semantics: Volume 11, section 4.1.
- Planning reconciled with reality. Every volume was audited against what was actually built, and stale statements were corrected (the audit table is docs/PLAN-REVIEW.md). Volume 13 is rewritten as the single plan in three tiers: Tier 1 the completed record, Tier 2 the evidence-based next phases (E1 to E4, derived from the trial's ranked gaps), Tier 3 direction only. No code changed.
- Direction A, the second-app trial, is done (2026-07-02). The unchanged CLI was run against a large real app, pms-backend (43 entities, 14 domain modules). Result: discovered 0, mis-bound 0, failed loudly 43, every failure a located error. The one forbidden outcome, a silent wrong binding, did not occur. Gaps were ranked by capabilities blocked: (1) parse resilience, language level, and scan scope; (2) DTO/VTO-returning reads unmatched by the discovery rule; (3) association fields unmapped; (4) list/filter/multi-argument reads unmodeled; (5) no-backing-method and composite keys. Full evidence: docs/experiments/second-app-trial.md, which is now the standing benchmark (Volume 13, section 5).
- Direction B step three: the manifest is generated at build time, not copied by hand. A new `tibyan-maven-plugin` runs the existing pipeline (same `TibyanCompiler` and `McpBackend` the CLI uses) during the app's build, bound to `generate-resources`, and writes `tibyan/capabilities.json` onto the classpath output. The example app no longer commits a manifest; its build generates one from its own source, byte-identical to `tibyan generate` (asserted by EndToEndMcpIT). Adopting Tibyan is now: add the dependency, add the build plugin once, build. Details in Volume 11 and Volume 7, section 8 (gap 2).
- Direction B step two: a truly untouched app needs only the `tibyan-runtime-spring` dependency. The starter now supplies the MCP server name and version as overridable defaults (an `EnvironmentPostProcessor`), and the runtime discovers the manifest by a fixed order (explicit `tibyan.capabilities`, then `classpath:/tibyan/capabilities.json`, then `file:tibyan/capabilities.json`), failing loudly if none is found. The example app proves it: no `spring.ai.mcp.server` block in its `application.yml`, the manifest shipped as a classpath resource at `tibyan/capabilities.json`, no `-Dtibyan.capabilities`, and getProduct still returns the real product over genuine MCP. Details in Volume 7, sections 4 and 8 (gap 2, now closed).
- Direction B step one: the capability binding is discovered from the real `@Service` method, not assumed by the `get<Entity>` to `<Entity>Service#findById` convention. The matching rule and what is still assumed are in Volume 5, section 4.1. For the example app the discovered binding is still `ProductService#findById`; the difference is that it is now read from the source, so a second app with a differently named service or finder is discovered correctly instead of silently mis-bound.

To resume: read this file, then CLAUDE.md, then Volume 13 (the tiered plan). Tier 2 phases E1, E3, and E2 are all built; the benchmark discovers getUnit and getLease with mis-bound 0. The next step is a human decision on what comes next, from the roadmap:

- Tier 2, phase E4 (list, filter, and multi-argument reads): the next evidence-ranked phase, and the first that grows the IR again (a collection output shape) under Volume 4, section 7 discipline. Design-before-code as with E2/E3.
- Deferred provider patterns, if evidence justifies: P1b (custom `@Query` by-pk terminals, would add Tenant) and P2 (composite view assembly, would add Building, Property, ChatRoom).

The E2 view finding is closed (the manifest now carries the view), so it is no longer an open item.

Whichever is chosen: design first where a rule or IR growth is involved, keep mis-bound at 0 as the absolute bar, and re-run the benchmark after each build. Volume 7 section 8 holds the runtime-side gaps; the provider-side gaps live in the experiment doc and Volume 5, sections 4.1 to 4.6.

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

Direction A, Direction B, and Tier 2 phases E1, E3, and E2 are all done, and a third-app generalization trial has run; their results are in the recent-changes list above. What drives the work is the tiered roadmap in Volume 13, and the candidates for next, now informed by two benchmarks, are:

- Collections during extraction (erp-backend's top blocker, 10 of 25 entities; pms-backend had zero). Two distinct pieces: modeling collection outputs (phase E4, list reads), and a smaller change so a to-many field does not abort a whole entity's extraction, which alone would let several erp entities discover via rule 4.1. Highest-impact unlock on the second benchmark. Design-before-code.
- Embeddable value types (`@Embeddable`, erp-backend, 3 entities; a new pattern pms-backend never showed). A modeling decision, like the E3 rules.
- The IR-consistency finding: E3 emits dangling `ENTITY_REF`/`Relation` to entities that fail extraction, so `--out` fails validation and generation is blocked on erp-backend. A provider fix (emit references only to in-model entities, or drop dangling ones with a note), described in the third-app trial, deferred.
- Phase E4 (list, filter, and multi-argument reads) and the deferred pms patterns P1b/P2, as before. Note P1b/P2 had zero occurrences on erp-backend, evidence they are pms-idiom-specific.

The E2 view finding is closed: the MCP manifest now carries the view field (Volume 6, section 3.1).

After each build, re-run the benchmark and append the dated entry to the experiment doc. Mis-bound greater than zero at any point becomes the top priority over everything.

Runtime-side deferred work is unchanged and stays listed until closed: health, observability, and lifecycle (Volume 7, section 8, gap 7); real SPI plugin discovery (Volume 10, section 2). These sit in Volume 13, Tier 3, behind the Tier 2 semantic phases.
