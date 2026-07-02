# Volume 13: Development Roadmap

Status: Living. Rewritten on 2026-07-02, after the MVP was completed, hardened, and pointed at a second real application. This volume is now the single plan from the spike to production readiness, in three tiers: what was actually built (Tier 1, a record), what the evidence says to build next (Tier 2, detailed), and where the project is heading (Tier 3, direction only). The original roadmap's hypothetical sequence, P4 OpenAPI, P5 SDK, P6 database provider, P7 .NET, is superseded as a schedule: those ambitions survive in Tier 3, explicitly conditional on the semantic core proving itself on real applications first.

---

## 1. Sequencing principles

Three rules order everything:

1. Prove the hardest assumption first. The runtime binding was the largest unknown, so the Technical Spike came before any other work (Volume 8). This rule is now satisfied and stands as precedent: when a new unknown of that size appears, spike it before building on it.
2. Build in thin vertical slices. Each phase keeps something that runs end to end and grows it, rather than building a layer in isolation.
3. Evidence before elaboration. After the MVP, phases are chosen by what a real application actually blocked, measured by the standing benchmark (section 5), not by the architecture's symmetry or by what would be elegant to build. A capability gap earns a phase by blocking real capabilities in a real app.

The disciplines that accompany these rules do not relax as the plan grows: one phase per session; a phase is not started until the previous phase meets its exit criterion; the tool fails loudly and never guesses; the IR stays tiny and grows only through deliberate, designed steps (Volume 4, section 7). Nothing in this roadmap is license to build ahead of evidence.

## 2. Tier 1: completed (this is a record, not a plan)

The real sequence, as built and verified on the main branch. Each line states what the step proved.

| Step | What it proved |
|---|---|
| Phase -1, Technical Spike | An MCP server can run inside a real Spring app's own context and call a real bean, activated by one dependency; the stack was pinned (Spring Boot 3.4.1, Spring AI 1.0.9, SSE transport; Volume 16, section 3) |
| P0, Core and IR | The IR stands alone: zero-dependency records that validate and round-trip to JSON (Volume 4) |
| P1, Spring provider | A provider can build a valid IR from real source with JavaParser (EntityExtractor; Volume 5) |
| P2, MCP backend and runtime | A backend plus the ExecutionAdapter execute against the real, unmodified app: the MVP acceptance criteria (Volume 2, section 5) passed with a genuine MCP call returning the real product |
| P3, Pipeline and CLI | The whole flow is reproducible from the terminal: scan, validate, generate (Volumes 9 and 11) |
| Hardening 1, binding discovery | The binding is read from the real `@Service` method by shape, not assumed by convention; absence or ambiguity fails loudly instead of mis-binding (Volume 5, section 4.1) |
| Hardening 2, one dependency | A truly untouched app needs only the starter: MCP server defaults and manifest discovery are auto-configured, no `application.yml` block, no launch property (Volume 7, section 4) |
| Hardening 3, build-time generation | The manifest is generated onto the classpath during the app's own build by `tibyan-maven-plugin`, running the same pipeline as the CLI, byte-identical output, nothing copied by hand (Volume 11, section 3.1) |
| Second-app trial (evidence gathering, not a build) | Against a large real app (pms-backend, 43 entities) the unchanged tool discovered 0 capabilities, mis-bound 0, and failed loudly on all 43, each failure located and explained; the gaps were ranked by how many real capabilities each one blocked (docs/experiments/second-app-trial.md) |
| Phase E1, parse resilience and scan scope | The scan survives a large messy app: `tibyan scan` on pms-backend completes end to end at exit 0, classifies all 43 entities with located per-entity outcomes, and names every unparseable file, instead of aborting on the first. Discovered stayed 0 (E1 adds no discovery power) and mis-bound stayed 0 (Volume 5, section 4.3; the phase spec, now with its outcome, is in section 3 below) |
| Phase E3, type-system decisions | Associations map to ENTITY_REF plus a deduplicated Relation, enums to STRING plus a value-set annotation, and Java floating types to the new FLOAT primitive (the one deliberate IR growth). All 24 formerly extraction-blocked pms-backend entities now extract; discovered held at 0, mis-bound 0 (Volume 5, sections 4.4 to 4.6; the phase detail is in section 3 below) |
| Phase E2, DTO-aware read discovery | A view-returning service method backs a read when a structural by-primary-key load of the entity (direct or one-hop) is followed by one traceable mapping step; names carry no weight. Measured jointly with E3: discovered 2 (getUnit, getLease), mis-bound 0, meeting the ratified joint exit (Volume 5, section 4.2; the phase detail is in section 3 below) |

The trial is the pivot of this roadmap: it converted "what should we build next" from guesswork into a ranked, counted list. The single most important fact in it is the zero in the mis-bound column. The tool's one forbidden failure mode, a plausible but wrong binding, did not occur even once against a messy real system.

## 3. Tier 2: evidence-based next phases

These phases are detailed because the pms-backend trial justifies detail: each one is traceable to a ranked gap with counted evidence. Each phase states its goal, its exit criterion, and the evidence that earns it a place. No dates, no estimates. The order is the trial's ranking: each phase unblocks the measurement of the ones after it.

After every phase in this tier, the benchmark is re-run and the experiment doc gains a dated entry (section 5). That re-run is part of the phase, not an optional follow-up.

### Phase E1: Parse resilience and scan scope (complete, 2026-07-02)

| Field | Detail |
|---|---|
| Goal | A scan survives a large, messy, real codebase and reports per-file problems instead of dying on the first one. Three parts: raise the JavaParser language level to the app's Java version (Java 17); collect-and-continue per file, meaning an unparseable file is recorded and skipped and the scan continues, with every skipped file named in the output along with why; scope the walk to relevant source, excluding build output and tool-metadata directories |
| Evidence | Trial gap 1, the widest margin: the parse gate blocked 38 of 43 entities across 12 of 14 entity-bearing modules before extraction began, and the whole-app scan died inside `library/mq-adapter`, code that is not even part of the domain. Two triggers share the gate: legitimate Java 17 syntax rejected by a parser configured below Java 14 (f1), and files that are genuinely invalid as text (f2) |
| Exit criterion | The pms-backend whole-app scan completes end to end: it reaches every module, reports each unparseable file with its reason instead of aborting, and the benchmark entry records per-entity outcomes rather than a module-level abort |
| Outcome | Met. `tibyan scan examples/pms-backend` now exits 0 and classifies all 43 entities with a located per-entity outcome (discovered 0, failed 43: UNMAPPED_FIELD_TYPE 24, NO_BACKING_METHOD 19), and names all 27 unparseable files with their parse location; mis-bound stayed 0. The f1 language-level failures are eliminated. Delivered in `tibyan-provider-spring` as a resilient `scan` alongside the strict `provide` (Volume 5, section 4.3), with `mvn clean verify` green. Full run: docs/experiments/second-app-trial.md, section 8.1 |

A discipline note, because this phase is the one most easily misread as a license to soften: collect-and-continue does not weaken fail-loud. It relocates the loudness from "abort everything on the first bad file" to "name every bad file and keep going". A silent skip is still forbidden. The scan's result must state exactly which files were not read and why, so an empty or partial result can never masquerade as a complete one. Two evidence notes for later phases surfaced only because E1 now reaches every entity: enum-typed and `Double`-typed fields are unmapped alongside true associations (weigh in E3 or a type-mapping step), and the composite-key join entities fail earlier on an unmapped embedded-id type, so cause c stays latent behind UNMAPPED_FIELD_TYPE.

### Phase E2: DTO-aware read discovery (rule RATIFIED and BUILT 2026-07-02, after E3)

| Field | Detail |
|---|---|
| Goal | Extend the discovery rule of Volume 5, section 4.1 so a service method that returns a DTO or VTO can back a read of an entity, when that relationship is establishable from the source. The rule must be designed and written into Volume 5 before it is coded, exactly as the binding rule was (the rule was committed to Volume 5 first, the code followed). Until the written rule exists, no matching code is written |
| Evidence | Trial gap 2, the widest latent reach: essentially all 40 single-key entities are blocked by shape rule 3 (return type simple name must equal the entity name) because this app's service boundary returns view types: `findById` returns `TenantVTO`, `getVendorById` returns `VendorVTO`. The audit module proved the current rule behaves correctly by refusing: it searched, found no entity-returning method, and failed with a located explanation rather than guessing |
| Exit criterion | Discovered capabilities greater than zero on pms-backend with zero mis-bindings. Every discovered capability names the real method it is bound to; any case the written rule does not decide unambiguously still fails loudly |
| Design status | The rule is written in Volume 5, section 4.2 (conditions, explanation format, refusals, IR representation, fixtures F1 to F12, benchmark forecast). It anchors on structural evidence only: a by-primary-key load of the entity proven by the framework contract, one traceable mapping step, in-tree resolution, names carrying no weight |
| Build outcome (2026-07-02) | Conditions 0 to 10 implemented exactly as ratified in `tibyan-provider-spring` (a `RepositoryCatalog` and `ViewReadMatcher`, no core or backend change); fixtures F1 to F12 green; `mvn clean verify` green with the golden manifest byte-identical. The joint E3-plus-E2 benchmark met the ratified exit exactly: discovered 2 (getUnit, getLease via P1a one-hop), mis-bound 0, with COMPOSITE_KEY 3 and NO_BACKING_METHOD 38 carrying named near-misses (experiment doc, section 8.5). The one finding, the `view` annotation living in the IR but not the MCP manifest, was closed in a follow-up backend-only phase: the manifest now carries a `view` field and a description clause (Volume 6 section 3.1), example-app golden byte-identical, discovery numbers unchanged |

Design finding on the exit criterion, recorded plainly rather than silently edited: the evidence study (Volume 5, section 4.2.1) showed the criterion as written could not be met by the E2 build alone on this benchmark. None of the 19 blocked entities had any single-id read in a parseable service; the only reads the rule can discover belonged to entities still blocked at extraction (E3's causes). The first rise in discovered therefore came from E2 and E3 together, and the joint run confirmed it: exactly 2, Unit and Lease.

Ratified, 2026-07-02: (1) the Volume 5, section 4.2 rule stands as written, conditions 0 through 10, the ENTITY_REF-plus-view-annotation representation, fixtures F1 to F12, and the deferred patterns (P1b, P2, facades, non-pk reads). (2) Sequencing: E3 is designed and built before the E2 build; E2 and E3 are then measured jointly against the benchmark with the shared exit criterion, discovered greater than zero (forecast: Unit and Lease via P1a) and mis-bound exactly zero. (3) E3 is not one lump: it is three separate type-system decisions, associations, enums, and inexact numerics, each with its own design argument before any code (see phase E3 below).

### Phase E3: Type-system decisions (three rules RATIFIED and BUILT 2026-07-02)

Per the ratified decision above, E3 is not one lump: it is three separate type-system decisions, each with its own design argument, rule text, and refusal behavior, designed before any code and ratified 2026-07-02, including the FLOAT growth and the growth bar recorded in Volume 4. They shared one build session. The designs are in Volume 5, sections 4.4 to 4.6, from a full field census of the 24 extraction-blocked entities (the census corrected E1's first-cause split: 20 association fields on 15 entities, 21 enum fields on 13, 8 `Double` fields on 7).

Build outcome (2026-07-02): all three rules implemented exactly as ratified (FLOAT in `tibyan-core`, associations and enums via a structural `TypeRegistry` in `tibyan-provider-spring`), fixtures A1 to A9, B1 to B5, C1 to C5 green, `mvn clean verify` green across the reactor with the golden manifest byte-identical, and `tibyan-core` still zero-dependency. The benchmark re-run matched the section 8.3 E3-alone forecast on every number: discovered 0, mis-bound 0, skipped 27, failed 43 all NO_BACKING_METHOD, and the IR gained 19 unique MANY_TO_ONE relations, 21 enum value sets, and 8 FLOAT fields (experiment doc, section 8.4). Discovered holds at 0 as forecast; the first rise comes from the E2 build.

| Decision | Rule (designed, pending ratification) | Clears alone | Evidence highlight |
|---|---|---|---|
| (a) Associations | Pure provider mapping onto the existing IR, no growth: `@ManyToOne` and `@OneToOne` fields whose type resolves in-tree to an `@Entity` map to `ENTITY_REF` plus a deduplicated `Relation`; to-many and embeddables deferred (zero exist in the app); undeclared or unresolvable kinds refuse (Volume 5, section 4.4) | 8 of 24 | Every association in the app is `@ManyToOne` to an in-tree entity; `UserRole` has an `@Id @ManyToOne` field, so field mapping and read eligibility are kept separate |
| (b) Enums | STRING plus an `enum` annotation carrying the constant names, no IR growth; enum-ness proven by finding the in-tree declaration, never by name; unresolvable types keep refusing (Volume 5, section 4.5) | 5 of 24 | `User.status` is an entity, `Lease.status` is an enum: same naming pattern, different structure, so names carry nothing |
| (c) Inexact numerics | FLOAT enters the closed set as flagged, deliberate IR growth (Volume 4, section 4 growth note); the exact-inexact line stays hard, and a FLOAT id forms no read intent (Volume 5, section 4.6) | 2 of 24 | The census flipped this decision: 8 `Double` fields on 7 entities, gating both joint-exit targets; refusal would make the ratified joint exit unachievable |

| Field | Detail |
|---|---|
| Evidence | Trial gap 3 plus the E1 sub-findings, now fully counted (Volume 5, section 4.4.1). All three decisions together clear all 24 UNMAPPED_FIELD_TYPE entities; any subset clears fewer (the census gives the per-combination math) |
| Exit criterion (E3 build), MET 2026-07-02 | All 24 extraction-blocked entities extract on pms-backend with full shapes: 19 unique MANY_TO_ONE relations, enum value sets carried as annotations, FLOAT fields present; every remaining refusal is a located message naming the field and the deferred shape; zero mis-bindings. Discovered stays 0 until E2 lands. Achieved exactly (experiment doc, section 8.4) |
| Joint exit (E3 plus E2, ratified), pending the E2 build | Discovered greater than zero (forecast: exactly 2, getUnit and getLease via P1a) and mis-bound exactly 0 (forecast detail: experiment doc, section 8.3) |

### Phase E4: List reads (the unfiltered list-all), with filtered and view-returning lists split out (RATIFIED and BUILT 2026-07-02)

| Field | Detail |
|---|---|
| Goal | Model the unfiltered list-all read: a `@Service` method that returns a collection of an entity by delegating verbatim to the framework `findAll` on a Spring Data repository of that entity. This requires deliberate IR growth, and the design (Volume 5, section 4.10) settled it as the smallest honest surface: one boolean, `outputMultiple`, on Capability, with the closed type set unchanged (Volume 4, section 4 growth note), not a container type. It enters through Volume 4, section 7 discipline with its own validator (rule 6) and golden tests. This is the first Tier 2 phase that changes the IR, not only the provider |
| The split (the design's central finding) | The census of both benchmarks (docs/experiments/third-app-trial.md, section 12; second-app-trial.md, section 8.6) shows two disjoint list idioms. erp exposes 11 clean framework `findAll` pass-throughs across 10 entities (the modeled shape); pms exposes zero, its entire ~42-method list API being filtered and view-returning (`getAllByFilter` to `TenantResultSet`, `List<VendorVTO>` by foreign key). A filter parameter's meaning cannot be verified structurally (a `UUID categoryId` and a `Long tenantId` are the same shape; only names, which carry no weight, suggest intent), so "filter by X" is a claim the tool cannot substantiate. E4 therefore models list-all only and defers filtered, view-returning, paged-envelope, and multi-argument list reads by name to a successor phase (provisionally E6), which pms benchmarks. Splitting honestly beats modeling filters thinly |
| Evidence | Trial gap 4, now counted. erp: 9 list capabilities discover (including listEmailTemplate on an entity whose by-id read fails NO_IDENTIFIER, proving lists are identifier-independent), 1 refuses LIST_AMBIGUOUS (ProductCategory's two clean methods). pms: 0, all deferred. The modeled shape rests on the same repository resolution E2 built |
| IR growth | `outputMultiple` boolean on Capability (Volume 4, sections 3.4 and 4; validation rule 6). Ordering, uniqueness (List versus Set), and paging metadata are deliberately not modeled; a paged read discloses its envelope through the existing `paged` annotation hook, like a view. Backends: MCP gains additive `outputMultiple`/`paged` manifest fields (Volume 6, section 3.3); OpenAPI renders an array of the element schema with a paged disclosure (Volume 6, section 4.2) |
| Exit criterion (re-scoped from the original, with the reason recorded), MET 2026-07-02 | On erp-backend: list capabilities discovered greater than zero (forecast: exactly 9, met exactly) with zero mis-bindings, and the IR growth recorded in Volume 4 with its validator and golden-file tests. On pms-backend: unchanged (discovered 2, mis-bound 0), proving the phase additive, with every deferred list read named in the survey. The original criterion ("at least one list or filter read on pms-backend in each module") is not achievable by the list-all rule, because pms has no unfiltered entity-returning list read; it moves to the successor filtered/view-list phase, whose benchmark is pms. This re-scoping is a design finding, recorded not massaged (Volume 5, section 4.10.2) |
| Not this phase (deferred by name) | Filtered reads (any domain parameter), view-returning and projection lists, paged-envelope wrapper types (pms's `*ResultSet`), multi-argument reads, query-derivation from Spring Data method names (nominal inference, refused by principle), runtime paging behavior (what `Pageable` the adapter supplies; Volume 7, section 8), and write or action capabilities |
| Build outcome (2026-07-02) | Built exactly as ratified: `outputMultiple` on Capability in `tibyan-core` (still zero-dependency, validation rule 6), a `ListReadMatcher` in `tibyan-provider-spring` (list pool disjoint from by-id), additive `outputMultiple`/`paged` MCP manifest fields, OpenAPI array-of-element rendering, and the `discovered N (by-id A, list B)` survey split with a `list-refused` line and named deferrals. Fixtures L1 to L12 green; `mvn clean verify` green with existing MCP and OpenAPI goldens byte-identical and the example-app e2e unchanged. Benchmarks matched the firm forecasts exactly: erp discovered 25 (by-id 16 unchanged, list 9), list-refused 1 (ProductCategory LIST_AMBIGUOUS), mis-bound 0, generating 25 through both backends; pms discovered 2 (by-id 2, list 0), byte-identical to its prior row, mis-bound 0. Two deferred-count deviations recorded, not massaged (experiment docs): erp 56 named deferrals (the forecast's "~44" was the stated uncertainty) and pms 3 (the `*ResultSet` envelope reads are outside condition 2, and most `List<VTO>` reads sit in the 27 unparseable files). The hard bar held: mis-bound 0 on both, every `get<E>` row byte-identical. One ratified diagnostics amendment made mid-build and flagged in the session summary (Volume 5, section 4.10, the diagnostics amendment): condition 3's refusal names the domain parameter specifically, a diagnostics-only change with no matching-outcome effect |

Ratification rider (2026-07-02): the successor phase is recorded as **E6, the filter-semantics-plus-envelope problem**. The E4 benchmark run sharpened it into two halves, and E6's design session inherits both:

- The visibility half (surfaced by the E4 run, docs/experiments/second-app-trial.md, section 8.7). E4 named only 3 deferred list reads on pms, not the ~42 the census counted, because pms's roughly 35 list reads return opaque `*ResultSet` envelope types that the recognized-container rule (condition 2) cannot see into, and most of its `List<VTO>` reads sit in unparseable files. Before the tool can honestly refuse to claim a filter's meaning, it must first be able to see that an envelope-wrapped read exists at all. E6 must decide how to recognize an envelope-wrapped collection read structurally (unwrapping a custom wrapper type to find the collection it carries), which E4 deliberately deferred (Volume 5, section 4.10.6).
- The semantics half. Once seen, a filtered read must be represented without claiming the meaning of its filter, since a parameter's name is a claim an agent acts on and the tool cannot verify it structurally (Volume 5, section 4.10.2). E6 must decide how a filter surfaces (or does not) in the IR and in each backend's schema.

Both halves are honesty design, not a mere matching-rule extension, and both are decided before any matching code, exactly as E4's rule was designed before code. pms-backend is the benchmark, since pms's entire list API is filtered and envelope-shaped; this is why E6 sits after v1's release engineering rather than being the immediate next build.

### Phase E5: Partial-model integrity (RATIFIED and BUILT 2026-07-02)

Evidence from the third app (erp-backend), not pms-backend: this phase is idiom-driven, and its leverage was invisible on the first benchmark. It is three separate rules, each argued in Volume 5 (sections 4.7 to 4.9), and it needs no IR growth (the growth bar held; the Annotation hook and a closure invariant suffice, Volume 4, section 7.1).

Ratified 2026-07-02, all three rules as written: 4.7 model closure (drop-and-disclose, conditions 1 to 4, including condition 4's guarantee that a capability's output is never dropped or altered to force validity), 4.8 field tolerance (the five tolerated shapes, discovery untouched, the enum-@Id surfacing as NO_IDENTIFIER), and 4.9 the survey contract (the partial annotation via the existing hook, the survey qualifier, the additive benchmark column). Ratification rider: when any future backend emits entity field schemas (OpenAPI included), partiality must surface in that schema, a standing requirement inherited by that backend's design session (Volume 5, section 4.9).

| Decision | Rule (ratified) | Effect |
|---|---|---|
| (1) Model closure | After extraction, drop every `ENTITY_REF` field and `Relation` whose target is not in the model, and disclose it; a validated model is then always producible whatever fraction extracted (Volume 5, section 4.7) | Unblocks generation. Acceptance: the 7 current erp discoveries generate, where the run now exits 2 |
| (2) Field tolerance | A field the closed set and the association and enum rules cannot map is skipped with a note, and the entity extracts without it, rather than the field vetoing the entity; discovery is untouched (Volume 5, section 4.8) | The leverage: 9 erp entities with a good `findById` reach discovery via existing rule 4.1. Corrects the `byte[]` message and surfaces EmailTemplate's enum `@Id` as NO_IDENTIFIER |
| (3) Survey contract | A discovered entity may be partial; the survey qualifies it and the benchmark row gains a `partial` dimension distinct from discovered and failed, so historical rows stay comparable and the metric is not gamed (Volume 5, section 4.9) | Keeps the health metric honest: discovered rising, mis-bound zero, with partiality disclosed |

| Field | Detail |
|---|---|
| Evidence | erp-backend trial (docs/experiments/third-app-trial.md, sections 6 and 8): the dangling-reference finding blocked generation for all 7 valid discoveries; the field-veto finding blocked 13 entities on a single unmodeled field, 9 of which have a good `findById`. Verified from source this session |
| Exit criterion | On erp-backend: `tibyan scan --out` writes a valid IR and `generate` produces a valid manifest (closure); discovered rises to 16 with mis-bound 0 (tolerance); the survey and the benchmark row disclose partial entities (contract). On pms-backend: unchanged (no tolerated field shapes, no absent entities), proving the phase is additive, not a regression. Forecasts, number by number, in the experiment doc section 10 |
| Not this phase | This is not E4. No list-read modeling, no collection outputs, no new discovery rules; collections are skipped, not modeled |
| Build outcome (2026-07-02) | Built in `tibyan-provider-spring` and the CLI, no core change (no IR growth). erp: discovered 7 to 16 (all rule 4.1), partial 13, mis-bound 0, generation succeeds for all 16 (scan --out now exit 0). pms: byte-identical to its prior row (partial 0), proving the phase additive. Two forecast composition swaps, both verified with mis-bound 0 (Quote AMBIGUOUS via a competing getInvoice, VariationGroup discovers via getVariationGroup); the discovered count held at 16. Finding: model closure is unreachable via the scan path given tolerance, so it is retained as the invariant and unit-tested in isolation (Volume 5, section 4.7). `mvn clean verify` green, example-app golden byte-identical. Full result: experiment doc, section 11 |

### Deliberately not scheduled (from the trial's remaining causes)

- Cause d, read with no backing service method (1 entity, audit): the current behavior, a loud refusal naming what was sought, may be the permanently correct answer. A write-heavy module yielding no read capability is honest, not a gap. Revisit only if evidence shows real reads being missed.
- Cause c, composite `@EmbeddedId` identifiers (3 join entities): arguably out of scope for read-by-single-id. Smallest count in the trial. Stays out until evidence mounts.

## 4. Tier 3: direction only (production readiness and beyond)

This tier is deliberately coarse: one short paragraph per item, no designs, no phase plans, no acceptance criteria. Everything here is conditional on Tier 2 proving the semantic core on real applications, meaning the benchmark discovers a meaningful share of a real app's reads with zero mis-bindings. Writing detailed plans for this tier before that proof would repeat the original roadmap's mistake.

"Production ready" for this project means the following capabilities exist, as a checklist, not a schedule:

| Capability | What it means here |
|---|---|
| Works on a real app not designed for it | The standing benchmark discovers a meaningful share of the app's real reads, and that share is stated honestly in the experiment doc, including what remains undiscovered and why |
| Write and action capabilities gated behind policy | WRITE and ACTION capabilities are never silently enabled. The IR already distinguishes them from reads (Volume 2, NFR-S2); before any ships, an explicit policy gate controls exposure, off by default |
| Runtime hardening | Health checks, observability (logs, metrics, traces for tool calls), and lifecycle (clean startup and shutdown ordering), the items already named in Volume 7, sections 3 and 8 gap 7 |
| Security posture | Exposing tools from a legacy app respects the app's existing authentication and authorization (Volume 2, NFR-S1), with a stated stance on transport security and on which capabilities are exposed at all |
| Versioning and release | The starter and the Maven plugin are published to a public repository with a versioning discipline; the plugin API is the compatibility boundary (Volume 10, section 6) |
| Adoptable documentation | An outsider can adopt Tibyan from the docs alone: add the dependency and the build plugin, build, connect an agent |
| Deferred architecture closed | Real SPI plugin discovery replaces direct injection (Volume 10, section 2); a second backend proves the IR feeds more than one output (DONE, see below); a second provider proves source neutrality |

Record, moved from direction to done: the second backend proving IR reuse. The OpenAPI backend (`tibyan-backend-openapi`) was built 2026-07-02 (Volume 6, section 4). It turns the same Capability IR into a valid OpenAPI 3.0 document with zero change to the core, the plugin API, or any provider: the erp-backend IR renders 16 GET operations (with 13 partial entity schemas each disclosing its omitted fields) and the pms-backend IR renders 2 view operations (each declining to enumerate the view's fields), both validated against the OpenAPI 3.x schema. This is the one-IR-many-outputs claim, proven on real capabilities. It also discharged the E5 ratification rider (Volume 5, section 4.9): as the first backend to emit entity field schemas, it surfaces partiality in the schema. The OpenAPI backend for the Maven plugin, and the SDK, database-provider, and .NET items below, remain direction.

The former P4 to P7 ambitions, now direction, each conditional on the semantic core proving itself first:

- OpenAPI backend (was P4). Proves one IR feeds a second output with no provider changes. Cheap relative to its proof value once the IR is demonstrably rich enough to be worth re-targeting; pointless before then.
- SDK backend (was P5). Typed client libraries from the same IR. Same conditionality as OpenAPI, and behind it in line: it proves the same architectural claim.
- Database provider (was P6). Proves the IR accepts a non-code source. Also the fallback path for systems whose source is unreadable. Its open question, how much business meaning a schema alone can recover, is exactly the kind of question the benchmark discipline should answer before a build.
- .NET provider (was P7). Proves the platform crosses ecosystems. The largest and most conditional item: it multiplies every provider-side lesson from Tier 2 into a second language, so it comes last and only after the Java provider has stopped teaching new lessons.

### 4.1 Release engineering (v1): the active phase (decisions recorded 2026-07-02)

With all Tier 2 semantic phases built (E1 to E5) and the tool discovering 27 capabilities across two real benchmarks with zero mis-bindings, the semantic core is proven enough on real apps that the next phase is not another discovery rule but making the tool adoptable by a stranger. This pulls the "Versioning and release", "Adoptable documentation", and "Security posture" rows of the Tier 3 checklist to the front. It changes no discovery, backend, runtime, or CLI behavior; it is metadata, documentation, and mechanics. The full mechanics live in docs/RELEASING.md; the decisions are recorded here first, per rule 12.

- License: Apache-2.0 (`LICENSE`, `NOTICE`). Permissive so an adopter can embed the starter and the Maven plugin in a proprietary build without friction, with an explicit patent grant that protects adopters and contributors of a tool that generates and executes code paths into a host system. Rationale in docs/RELEASING.md section 1.
- Version: 0.1.0 (the reactor moves off `-SNAPSHOT` for the release commit). Pre-1.0 discipline: patch is docs and metadata and non-behavioral fixes; minor is a new phase or rule or IR growth or any behavior change, and pre-1.0 minor bumps may break; 1.0.0 is the first stability commitment, not yet claimed. The plugin API is the compatibility boundary (Volume 10 section 6).
- Publishing target: Maven Central under the `com.tibyaan` groupId, with GitHub Packages permitted only as an explicitly-labeled interim. The POM carries all Central-required metadata and a `release` profile (source and javadoc jars, GPG signing wired to external credentials, the central-publishing plugin), verifiable locally with `mvn -Prelease verify` and no keys. What cannot be done without a person (namespace verification, key ceremony, account creation) is enumerated as exact human steps in docs/RELEASING.md section 4, never faked.
- The adoption surface: the root README is rewritten for adopters (honest expectations, the refusals-are-the-product framing, a verified quickstart), a security posture is stated (docs/SECURITY.md), and CONTRIBUTING.md records the design-before-code and benchmark disciplines.

The two benchmark apps stay private and uncommitted (section 5); the ledgers are the published evidence, so a stranger can read the numbers without the source.

## 5. The standing measure: two benchmarks (pms-backend and erp-backend)

The trial targets become the project's standing benchmarks:

- The primary target is the local, read-only copy at `examples/pms-backend`: 43 entities across 14 domain modules, Long/Integer ids, view-returning reads (exercising rule 4.2). It is outside the Maven reactor and is not committed; docs/experiments/second-app-trial.md records its structure so results are interpretable without the source.
- A second target was added 2026-07-02, `examples/erp-backend`: 25 entities, single module, UUID/String ids, entity-returning reads (exercising rule 4.1), with collection associations and an embeddable value type pms-backend lacks. Same read-only, out-of-reactor, uncommitted status; docs/experiments/third-app-trial.md records it. The generalization finding: the two apps discover through disjoint rules (pms via 4.2, erp via 4.1), so together they keep any single phase from being tuned to one codebase. Coverage is idiom-bound (erp baseline: 7 of 25 discovered, all 4.1), and discovery does not fully compound across apps.
- After each Tier 2 phase, the tool is re-run against the benchmark(s), and the experiment doc gains a dated entry with the new totals: discovered, failed loudly by cause, and mis-bound.
- The health metric is one line: discovered rising while mis-bound stays zero, on both apps.
- Stated plainly: mis-bound greater than zero at any point becomes the top priority over everything. A silent wrong binding is the one failure the tool exists to never produce; if either benchmark ever shows one, all phase work stops until the cause is found and closed.

Baseline, 2026-07-02, tool unchanged since the MVP plus hardening: discovered 0, mis-bound 0, failed loudly 43 (by cause: f2 placeholder tokens 31, f1 language level 7, f3 association field 4, d no backing method 1). The scan aborted at exit 3 on the first unparseable file, so these are proximate module walls, not a per-entity classification.

After E1, 2026-07-02: discovered 0, mis-bound 0, failed 43 per entity (UNMAPPED_FIELD_TYPE 24, NO_BACKING_METHOD 19), skipped-by-parse 27, scan exit 0. Discovered held at 0 as expected (E1 adds no discovery power) and mis-bound held at 0; the gain is that the scan now completes and classifies every entity instead of aborting. The full log is docs/experiments/second-app-trial.md, section 8, which now also carries the joint E3-plus-E2 row (discovered 2) and the erp-backend baseline row (discovered 7, all rule 4.1, mis-bound 0).

## 6. What each step proves about the architecture

| Step | Architectural claim it validates |
|---|---|
| Spike (done) | The runtime can bind to a real legacy system in-context |
| P0 (done) | The IR can stand alone, framework-free |
| P1 (done) | A provider can feed the IR from real source |
| P2 (done) | A backend plus an adapter can execute against the real system |
| P3 (done) | The flow is reproducible without hand steps |
| Hardening 1 to 3 (done) | Adoption cost is one dependency plus one build-plugin entry, with the binding discovered, not assumed |
| E1 | The provider survives reality: scale, mess, and modern syntax |
| E2 | Discovery generalizes past the toy shape without guessing |
| E3, E4 | The IR grows deliberately, and only where real apps demand it |
| Tier 3, second backend and provider | One IR feeds many backends; one IR accepts many sources |

## 7. Guardrail

Workflows, permissions, events, policies, and the rest of the Semantic Business Capability Model (Volume 1, section 9.1; Volume 4, section 7) remain intentionally absent from Tier 2. They are real parts of the long-term IR, but they are not on the critical path to proving that the semantic core works on real applications. The one exception is narrow: Tier 3's policy gate for write capabilities will eventually pull the policy thread, and when it does, it enters through an RFC (Volume 15), not through this roadmap.

The standing disciplines, restated once more because this is the document most likely to be read as permission: one phase per session; exit criteria before the next phase; fail loudly, never guess; the IR stays tiny until evidence forces growth; and the benchmark, not ambition, decides what is next.
