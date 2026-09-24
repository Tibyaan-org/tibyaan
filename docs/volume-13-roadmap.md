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

| Decision | Rule (ratified 2026-07-02) | Clears alone | Evidence highlight |
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

E6 design outcome (2026-07-02): DESIGNED, and DEFERRED WITH A DOCUMENTED CEILING (Volume 5, section 4.11). The census confirmed the filtered read is the majority of both apps' read APIs, and the design found that a filter's meaning is not structurally provable in the general case, so the structural-only tool must refuse it. The three honest shapes resolved as: REFUSE for v1 (the recommended path, claims nothing, meets the never-claim constraint trivially); MODELED-OPAQUE rejected permanently (the copied parameter name is a claim in the tool schema regardless of any disclaimer, and pms cannot even write a truthful disclaimer because its filter semantics live in a hand-written query builder, not in any query text); HUMAN-ATTESTED recorded as the one honest way to represent an unverified filter, but deferred to its own future phase because it is the project's first human-in-the-loop, first carried-but-unproven-claim mechanism, a real departure from structural-only, not an E6 matching rule. One narrow structural exception was recorded: Spring Data derived-query filters are a framework contract (not nominal inference), so a verified-derived-query rule is the honest structural avenue if filtered discovery is ever pursued, but it is erp-shaped, a minority, and does not help pms at all. The envelope sub-problem (seeing a wrapped read) is a structurally solvable prerequisite that pays off only once a representation exists, so it is deferred with the rest. For v1, filtered, projected, and envelope reads stay named deferrals in the survey, never emitted, never mis-bound: no IR growth, no new rule, no backend change. The ceiling and the reopening triggers (human-attested capabilities adopted as a product direction; or a narrow verified-derived-query rule; or envelope-unwrapping once there is something to represent) are in Volume 5, section 4.11. This is the honest ceiling of the structural-only tool: it tops out at unfiltered reads, get-by-id and list-all, and the filtered majority is future work, correctly refused for v1.

E6 split and honesty verdict, RATIFIED (2026-07-02), recorded before the E6a design work (rule 12):

- The E6 SPLIT is ratified: E6 is two phases, E6a (envelope and wrapper unwrapping, making hidden reads visible and classified) and E6b (the filter-semantics decision), E6a first, because an honesty decision about a filter cannot be made on a read that cannot be seen. E6a grants VISIBILITY, never discovery: a read seen through a wrapper still passes through the existing rules (4.1, 4.2, 4.10, P1b) and the E6b-pending filter refusal unchanged, so a filtered read seen through a wrapper is still refused, now visibly and countably instead of hidden. The expected E6a outcome is many newly visible reads, mostly refused as filtered, few or zero new discoveries, and a failure ledger that becomes complete.
- The E6 HONESTY VERDICT is ratified as standing. MODELED-OPAQUE is a STANDING REJECTION, never revisited casually: an MCP tool's name and signature are themselves the claim an agent acts on, and a "semantics not verified" disclaimer sits in a description that is invisible at the moment of action, so a copied filter-parameter name is an unverified claim the tool cannot honestly make. HUMAN-ATTESTED is recorded as the leading E6b candidate, deferred to post-v1, explicitly distinct from tool-verified: a human asserts the filter's meaning and the tool carries it labelled as attested, which is not a violation of "never guesses" (the tool did not guess, a person asserted), but is a real departure from structural-only that gets its own designed phase.

E6a is designed (Volume 5, section 4.11.7). The rule: an unrecognized wrapper return type is made visible as a read of E when transparency holds, either a generic tie (a type argument of E or a view of E) or a body tie (a single persistence access on a repository of E returning E or a collection of E), and is then handed to the existing rules unchanged; a wrapper with no provable tie refuses as `OPAQUE_WRAPPER`. It reads no wrapper internals, trusts no wrapper name, and grants no discovery. Forecast, framed as visibility not discovery: pms gains visibility of roughly 12 `*ResultSet` reads (property, lease, billing), all filtered, so all become visible-but-refused-filtered with zero new discoveries; the roughly 23 in the ten unparseable modules stay parse-skipped; erp is inert (no wrappers); existing discoveries byte-identical, mis-bound 0. The headline is visibility gained, not discoveries.

SUPERSEDED-NOT-ERASED (2026-07-02): the E6a BUILD and the E6b decision are consolidated into the Attestation phase (section 3, "Attestation: the first post-v1 phase"). The E6a transparency rule stands as attestation's unwrap precondition and the E6b analysis as its frozen foundation; the standing honesty verdict (modeled-opaque rejected; human-attested the mechanism) is the phase's charter. No standalone E6a or E6b session is scheduled.

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

### P1b: the custom-query by-primary-key terminal (DESIGNED and RATIFIED 2026-07-02 as the strict by-pk boundary; BUILD DEFERRED)

The deferred half of rule 4.2 condition 4: when a custom `@Query` method, rather than a framework `findById`, may serve as the by-primary-key load evidence for a view-returning read (Volume 5, section 4.2.10). The ratified rule verifies the JPQL structurally as a by-primary-key select of E: it selects the root entity (not a projection), its joins are all `LEFT JOIN FETCH` (a LEFT fetch never drops a root row; an inner `JOIN FETCH` is an existence filter and is refused), and its WHERE is exactly the id equality and nothing more. It refuses native queries, unlocatable queries, projections, and, the two semantic-lie guards, any filtered WHERE (P5) and any inner-fetch filter (P4). Names carry no weight; the query text is read as a bounded token template, never interpreted.

The design-closure finding, decisive and recorded honestly: on real-world evidence, refusal is not the primary path of this rule but the total path. Across both benchmarks there is no custom query that is both provably nothing-but-a-by-primary-key load and feeding a discoverable read. pms Tenant, the one case that looked like a clean unlock in the first draft, FAILS the strict rule: its query, `SELECT t FROM Tenant t JOIN FETCH t.user ... WHERE t.id = :id`, has a clean WHERE but an inner `JOIN FETCH t.user` on a nullable association, an existence filter that excludes userless Tenants, so it is not a by-primary-key load. erp Delivery's query passes the strict rule (all LEFT fetches) but its consumer is blocked by an unrelated implicit-this mapper; the other erp custom queries feed entity-returning `findById` methods that section 4.1 discovers without reading their bodies. So P1b discovers zero on both apps, and its ratified value is the strict refusal boundary, not discovery: the rule's job is to draw the honest line between a by-primary-key load and a filtered read.

Re-scope, ratified: P1b is NOT folded into E6; it is E6's LOWER BOUNDARY. E6 (the filter-semantics problem) inherits the majority case, custom queries that do more than a by-primary-key load (a WHERE predicate, an inner-fetch filter, a projection, a returned collection), and it designs how to represent them honestly, building up from P1b's floor. E6 may never present a query that does more than a by-primary-key load as an unfiltered by-id read; P1b's two refusals are that floor.

Build: DEFERRED. The standalone P1b build would move no benchmark number (zero discoveries), so it is deferred until either E6 is built (which must implement the same by-primary-key floor and so absorbs P1b's matcher) or a future app presents a strict-passing by-pk custom query that feeds a resolvable view read. Forecast, build deferred: pms discovered 2 (unchanged, Tenant refused on the inner fetch), erp discovered 25 (unchanged), mis-bound 0 on both, byte-identical to the current rows. Fixtures P1b-1 to P1b-10 pre-registered (P1b-4 the filtered-WHERE guard, P1b-5 the inner-fetch guard). Rule 12 applies whenever the build lands: ratification records first, which this closure provides.

SUPERSEDED-NOT-ERASED (2026-07-02): the P1b BUILD is consolidated into the Attestation phase below. The ratified strict rule, the fixtures, and the deferred forecast above stand unchanged as the record and as attestation's lower boundary; only the standalone build trigger is retired in favor of the consolidated phase.

### The structural ceiling (evidence-proven, 2026-07-02)

This is an architectural statement, not a limitation report: Tibyan has a structural core that is never wrong, with a documented edge. Three phases mapped that edge from three independent directions, and this section records their convergence as a first-class fact.

What is STRUCTURALLY VERIFIABLE, and therefore discovered and never wrong: read-by-id of an entity (rule 4.1) or through a one-step view derivation (rule 4.2), the unfiltered list-all (rule 4.10), the clean by-primary-key custom query (the ratified P1b boundary: root select, LEFT fetches only, the id equality and nothing else), associations, enums, and the closed primitive set (E3), over partial models (E5). The proven totals across the two benchmark apps: 27 verified capabilities (erp-backend 25: 16 by-id and 9 list; pms-backend 2), every one hand-verified against source, with ZERO wrong bindings across every run of every phase. Both backends (MCP and OpenAPI) generate all 27. (RE-BASELINE 2026-07-20: the 27 is the v1-complete milestone measured against the pms copy that then carried 27 placeholder-corrupted files. The adopter repaired those trivially-corrupted files, so pms-backend now discovers 5, not 2, and the current two-app total is 30, erp 25 plus pms 5. The re-baseline is external, not a coverage rule, and the filtered majority stays refused; see docs/experiments/second-app-trial.md section 9. The structural ceiling itself is unchanged.)

What is BEYOND STRUCTURAL PROOF: filtered reads (any WHERE or parameter beyond the id equality), projected and view-returning custom queries, opaque hand-built wrapper assemblies, and multi-predicate custom queries. The reason, stated once and honestly: proving any of these requires reading business semantics, what a parameter means, what a predicate selects, what a hand-assembled wrapper carries, and the tool refuses to guess at semantics, because a copied name is a claim an agent acts on. A tool that guessed would trade its zero-wrong-bindings record for coverage; this tool refuses the trade.

The boundary is EVIDENCE-PROVEN, not assumed. Three phases mapped it from three angles: P1b from below (even the cleanest-looking custom queries carry hidden filters; the one candidate unlock failed on an inner-fetch existence filter, and the strict rule discovers zero); E6b from above (the three honest representations of a filter resolve to refuse now, attest later, with modeled-opaque permanently rejected); and E6a from the side (visibility without discovery; every wrapper read that transparency would reveal is filtered, so seeing them changes counts, not capabilities). Three directions, one edge. The convergence is the finding.

### Attestation: the first post-v1 phase (designed and waiting; consolidated 2026-07-02)

The deferred structural leftovers, the P1b build, the E6a transparent-unwrap precondition, and the E6b filter-semantics decision, are consolidated into ONE designed-and-waiting phase, Attestation, because three phases independently concluded that what remains beyond the structural ceiling can only be carried honestly by a human's assertion.

The mechanism: a person asserts what the tool cannot structurally prove (a filter's meaning, an opaque wrapper's returned entity, the intent of a custom query), and the tool records and carries it as HUMAN-ATTESTED, explicitly distinct from tool-verified, on every consumer surface (the IR, the MCP manifest, the OpenAPI document). Attested is NOT a guess and NOT a claim of verification; it is a third, honestly-labeled category of knowledge. It therefore does not violate the never-claim constraint: the tool never presents an attested fact as verified, and it never invents one. "Never guesses" survives intact; what changes is that the tool can carry a labelled human assertion where before it could only refuse.

What is already designed toward it, the frozen foundation the eventual design session inherits: the E6b analysis of the three honest shapes (Volume 5, section 4.11); the MODELED-OPAQUE standing rejection and its reasoning as the permanent guard (a tool name and signature are the claim; a disclaimer is invisible at the moment of action); the ratified P1b strict boundary as attestation's LOWER BOUND (anything provably nothing-but-a-by-pk-load needs no attestation, and nothing above that line may ever be presented as an unfiltered by-id read); and the E6a transparency rule as the unwrap PRECONDITION (T1 generic tie, T2 body tie, OPAQUE_WRAPPER refusal), since a read must be visible before a human can attest what it means, and where even existence is unprovable, attestation is exactly the mechanism that could carry it.

The build trigger, stated in writing: Attestation is designed and built ONLY when there is EVIDENCE that a real user needs it, a real adopter hitting the filter or wrapper ceiling on a real application, not before. Publishing v1 and getting external signal is the way that evidence arrives. When it does, the phase follows the standing discipline: design first, ratification records committed before any build (rule 12), pms-backend as the primary benchmark (its API is the ceiling made concrete), and mis-bound 0 as the absolute bar.

THE TRIGGER FIRED, and the design half is DONE (2026-07-20, pending ratification). The record: the earlier candidate firings were judged weak (the zs signal: 2 real query reads of 31 deferrals; the first real-user REQUEST was Controller-Authority, not the ceiling). On 2026-07-20 the maintainer, testing the tool as a real adopter on pms, hit exactly this wall (5 tools of an 89-GET read surface after the parse re-baseline, docs/experiments/second-app-trial.md section 9) and rejected the coverage as unadoptable on behalf of real users. A full adversarially-verified coverage census (the 2026-07-20 ladder) confirmed the remainder is majority-attestable and minority-excluded. That is the written trigger, fired for real, and the meta-finding is recorded with it: the external signals that actually arrived ranked identity, honesty, and declared-security carriage AHEAD of the ceiling, and the ceiling became binding only when the tool was good enough for its coverage to be the last complaint standing.

The design (Volume 5, section 4.15; Volume 4, sections 3.8 and rules 8 and 9; Volume 6, sections 3.6 and 4.4; Volume 7, gap 10; SECURITY.md) is frozen and split BY NAME into ordered, independently ratifiable sub-phases, none thinned to fit:

- AT-0, the structural floor and the envelope unwrap: BUILT 2026-07-24. Built the already-ratified E6a transparency rule (section 4.11.7) and P1b strict matcher (section 4.2.10), provider-only, no core change and no attestation machinery; product is the complete classified worklist. Measured: pms discovered 5 byte-identical and erp discovered 25 byte-identical, mis-bound 0 both, zero new discoveries as forecast; E6a made 56 previously-invisible pms wrapper reads countable (51 multi-read `*ResultSet` assemblies, 5 OPAQUE_WRAPPER aggregate/proxy reads), erp inert. A recorded refinement of the 2026-07-02 forecast: the repaired pms copy refuses its `*ResultSet` reads as multi-read assemblies (data plus count, 4.10 condition 7) and its hand-written-repo aggregates as OPAQUE_WRAPPER, more precise than the forecast's "visible-but-refused-filtered"; the invariants hold (docs/experiments/second-app-trial.md section 12, third-app-trial.md section 24).
- AT-1, the attestation core: BUILT 2026-07-25. The committed `tibyan/attestations.json` record, intake validation of every ratified refusal condition, the structural-fingerprint staleness lifecycle (invalidate loudly, re-attest deliberately, missing-fingerprint refuses), character-exact carriage to every surface, the `attested_` reserved name prefix and core rules 8 and 9, the separate ATTESTED survey class, and the PREDICATE category (filtered and guarded reads). Provider-side intake plus the first-class `Attestation` component in `tibyan-core` (zero-dependency preserved); Jackson added to the provider to read the record. Measured: pms discovered 5 and erp discovered 25 byte-identical, mis-bound 0, attested 0 (no human has attested yet); the mechanism proven end to end on fixture code with a carriage fixture per surface. `mvn clean verify` green (docs/experiments/second-app-trial.md section 13, third-app-trial.md section 25). THE PMS UNLOCK is the first real measurement, pms-with-the-maintainer, a later interactive session.
- AT-2, the SHAPE category: opaque wrappers, aggregate enrichment, unresolvable projections.
- AT-3, the BOUNDARY category, deferred by name with elevated stakes: attested security contracts and remote delegations, where a false assertion breaches a security boundary rather than mislabeling data; needs its own ratification with stronger guards.
- AT-4, the AI assist, optional and separable: AI may propose, only a human may confirm, only confirmed claims ship; provenance distinguishes ai-drafted-human-confirmed from human-authored; a draft can never reach any output.

RATIFICATION STATUS (2026-07-21, updated): RATIFIED. The Gate 1 block below was resolved and the maintainer, having reviewed the corrected forecast (docs/experiments/second-app-trial.md sections 10 and 11, Volume 5 section 4.15.11), RATIFIED Volume 5 section 4.15 as frozen: the three-category frame, the zero-attestation-drift invariant, the sub-phase order (AT-0, then AT-1, with AT-2 next and AT-3 requiring its own future ratification and AT-4 optional), the attestations.json mechanism with the SHA-256 fingerprint lifecycle and loud STALE_ATTESTATION, the first-class Attestation component and core rules 8 and 9, the attested_ reserved prefix, the read-safety and carriage guarantees as narrowed, the G8 default, and pms-with-the-maintainer as the designated first measurement. AT-0 (the E6a transparency rule and the P1b strict matcher, plus the reporting-only worklist) is BUILT (2026-07-24), and AT-1 (the attestation core plus PREDICATE) is BUILT (2026-07-25); see the AT-0 and AT-1 bullets above and the ledgers' `actual` sections. Per one-phase-per-session, the next step is the FIRST MEASUREMENT (pms with the maintainer, an interactive session), then AT-2 (SHAPE). The Gate 1 record is retained below, superseded-not-erased, because its process correction stands.

RATIFICATION STATUS (2026-07-21, original): NOT RATIFIED, BLOCKED BY GATE 1. A pre-ratification traceability check was run over every number in the forecast: each had to trace to a committed census row, a ledger row, or an explicit counted sample. The design volume and the erp and enterprise rows passed. The pms per-category counts did NOT: they were cited to a "2026-07-20 coverage census" that was real and adversarially verified but was NEVER RECORDED IN THE LEDGER, and re-verifying them against source found four of four wrong (AT-2 aggregates 7 to 8, with one member reclassified from SHAPE to PREDICATE because it performs no by-primary-key load; excluded context reads 9 to 11; binary and stub 10 to 9; coercion shapes 3 to 2). The verification, the corrections, and the root cause are recorded in docs/experiments/second-app-trial.md section 11, and the corrected figures are in section 10 and Volume 5, section 4.15.11.

Per the gate's own rule, ratification did not proceed and AT-0 was not built. The rule and the finding behind it are worth keeping: a forecast is the thing a build is measured against, so a number that cannot be traced to recorded evidence is not a forecast, it is a memory. The process correction, adopted here: a census that a forecast depends on is recorded as a ledger section IN THE SAME SESSION it is run, never cited from a session artifact. The design itself (the frame, the invariant, the sub-phase split, the mechanism, the surfaces) was not the thing that failed and stands unchanged awaiting ratification once the corrected forecast is reviewed.

The invariant that governs every sub-phase, this design's mis-bound-0: ZERO ATTESTATION DRIFT (character-exact carriage, exact attachment by structural fingerprint, the human-attested label on every surface, complete provenance, attested never rendered or counted as verified). The health metric evolves accordingly and the verified count stays pure forever: discovered N and attested K are reported separately, never summed; "discovered rising, attested labeled, mis-bound 0, attestation-drift 0" is the new health line. The attestable ceilings, per benchmark, honestly stated as "up to N given a knowledgeable human": pms up to roughly 44 predicate plus 7 shape over 5 verified (about 63 percent of its GET surface at the ceiling); erp up to roughly 44 over 25 verified; the private enterprise benchmark up to 20 now plus 15 behind AT-3, over 4 verified (ledgers, the Attestation forecast sections). The open verification is named in the design: the real attestation rate is unmeasurable without a real attesting human, and its first-use measurement is pre-registered.

### Entity Identity (FQCN keying): the first evidence-triggered post-v1 phase (designed and built 2026-07-13)

v1 0.1.0 was published to Maven Central, and the external signal arrived (2026-07-12): a real enterprise-scale adopter application was surveyed, the private enterprise benchmark (docs/experiments/enterprise-benchmark.md, recorded by structural counts only: 169 modules, 111 entities, parsed clean, mis-bound 0 confirmed adversarially). It reranked the post-v1 build order, and the top-ranked finding is NOT Attestation.

1. Entity Identity (RANK 1), BUILT 2026-07-13. The IR keyed entities by simple name, and a real bounded-context codebase legitimately reuses the same simple name across contexts (3 collisions across 6 entities on the benchmark). Simple-name keying failed validation and blocked ALL output for the whole application: no IR, no MCP, no OpenAPI. This was the only total, output-blocking failure, and it gated every other improvement. It is structural, not idiom-bound: name reuse across bounded contexts is the default reality of any multi-module DDD Java app. The fix (Volume 4, section 3.7; Volume 5, section 4.12; Volume 6, section 3.4): add one qualified `key` per entity and derive the display `name` from it, disambiguating only on collision. It relaxes no matching rule and removes a latent mis-bind (simple-name keying would have silently merged two distinct entities), so it strengthens the never-wrong guarantee rather than trading against it. Fixtures EI-1 to EI-15 are all green, and the three-benchmark result is measured and confirmed (the ledgers' Entity Identity `actual` sections): both public benchmarks BYTE-IDENTICAL (proven by `diff -r` against the pre-phase commit), the private one BLOCKED to EMITTED with discovered UNCHANGED at 4 (so not a coverage win) and mis-bound 0, no residual display collision. The build committed the ratified design first (rule 12), one phase in one session.

2. Discovery-gap rules (RANK 2), ranked-second and SEPARATE, DESIGNED and BUILT 2026-07-13 (Volume 5, section 4.13), GENERAL SCOPE ONLY. Build outcome, measured: the forecast held to the number. Both public benchmarks are byte-identical (neither exhibits any of the shapes; proven by a `diff -r` of generated output against the pre-build commit), and the private enterprise benchmark is discovered 4 to 4 with NO_IDENTIFIER 10 to 0 and NO_BACKING_METHOD 96 to 106, mis-bound 0, its generated output byte-identical so the 4 bindings are the same 4. Both general rules fired and both refused exactly where the design said: all 10 inherited identifiers resolved (and all 10 entities then failed NO_BACKING, having no by-id read), and all 3 private-helper hops were reached and refused on their soft-delete guard. The 11 foundation-specific instances stay refused; discovered above 4 would have meant a rule overreached, and it did not. What the build changed is honesty, not coverage. The design half (below) records why. The design's mandatory first step was an honest classification of the three recurring shapes against all three benchmarks, and it changed the phase's shape. Two shapes are GENERAL and got rules: inherited `@Id` via an in-tree `@MappedSuperclass` or `@Inheritance(JOINED)` parent (an identifier-eligibility widening), and the private same-class helper hop (stepping through one private delegation to a clean by-primary-key load, under section 4.2's disciplines, strict). One shape is NOT a general gap and got no rule: the "interface-to-implementation across module boundaries" is a mischaracterization, its in-tree form is already handled by section 4.2 condition 4b and by direct `@Service` scanning, and its only genuine form (a port whose implementation is in an unscanned binary dependency) is unreachable by a source-only tool and stays refused. The decisive, honest finding: on this benchmark the general rules discover ZERO new capabilities. A full census of the 39 by-id reads found the 10 NO_IDENTIFIER entities are internal subtype rows with no by-id read (inherited `@Id` relabels their cause, discovers nothing), and every private-helper hop carries a soft-delete existence guard the tool correctly refuses (indistinguishable from an authorization or business filter without interpreting it, the P1b lesson); the rest are aggregate-enrichment (Attestation), delegation (out of scope), or already handled. So the gap between the un-discovered reads and zero measures this app's dependence on its own foundation idioms and internal non-read entities, not on standard Spring: the general rules are correct but inert here. Both general rules were ratified for their boundary and then BUILT (the human elected to build rather than defer on the P1b precedent, for the correctness and honesty they deliver even at zero discovery). The foundation-flavored remainder is deferred by principle; the guarded and aggregate reads are Attestation territory, not more discovery rules. mis-bound 0 is preserved by construction (both traces re-anchor to the key resolver and pool under refuse-on-ambiguity) and is measured at 0 after the build.

3. Attestation (RANK 3), trigger fired but weak, and now the honest home of most of the remaining user-facing coverage. The ratified Attestation trigger (a real adopter hitting the filter or wrapper ceiling) did technically fire, but weakly on the narrow filter-count: of 31 deferred reads only 2 are real user-facing query capabilities. The discovery-gap classification (item 2) sharpened the picture and partly re-weighted this: the assumption that "much of the missing coverage is honest structural discovery, not attestation" did not survive the source census. The general discovery rules discover zero here; the un-discovered user-facing reads are dominated instead by soft-delete-guarded by-id reads (a general pattern the tool must refuse, because it cannot verify the guard is benign without interpreting it) and by aggregate-enrichment reads (a clean load plus a second access and a composite mapper). Both of those are exactly the carried-but-unproven and guarded shapes Attestation exists to carry honestly, not shapes a structural rule can prove. So Attestation is not out-prioritized by a discovery-gap phase that yields general coverage; the discovery-gap phase yields almost none here, which strengthens rather than weakens the case that Attestation is where the remaining reach lives. Its build trigger is unchanged (a real adopter hitting the filter or wrapper ceiling), and it stays designed-and-waiting behind the same discipline: no build ahead of the trigger.

The discipline is intact: the signal, not ambition, set this order, and the evidence was allowed to change the plan. Entity Identity was built only after its design was ratified and committed first (rule 12), one phase in one session. The discovery-gap phase was designed on its own source evidence, and that evidence changed it: what looked like a coverage phase turned out, on the census, to unlock zero general coverage on the benchmark. It was ratified for the boundary it draws and then built to that boundary, general scope only, and the build confirmed the forecast to the number (discovered 4 to 4, both public benchmarks byte-identical, mis-bound 0). It re-weighted rather than out-prioritized Attestation. Attestation waits behind both, on its unchanged trigger. mis-bound 0 stayed the absolute bar on all three benchmarks and is measured at 0 after both builds.

### Controller-Authority: the first real-user-REQUESTED phase (designed, ratified, and BUILT 2026-07-19)

Build outcome, measured (2026-07-19): the three-benchmark forecast held to the number. pms discovered 2 unchanged, with getUnit compiling `requiredAuthorities ["1", "2"]` and getLease `["1"]` through their strict `@Secured` controller anchors (additive output on exactly those two entries); erp discovered 25 unchanged and BYTE-IDENTICAL (zero declarations, every capability ABSENT, the negative control); the private enterprise benchmark discovered 4 unchanged, with exactly 1 read compiling a single-authority contract and 3 ABSENT, its AMBIGUOUS entity staying AMBIGUOUS (the live proof that a controller anchor is never a tiebreaker). mis-bound 0 on all three, now covering the security contract (each read carries exactly its own anchor's authorities, never a sibling's). No `tibyan-core` change and no IR growth: both the compiled and the unverifiable states ride the Annotation hook. `mvn clean verify` is green; the example-app e2e and every existing backend golden are byte-identical (getProduct is ABSENT). Fixtures CA-1 to CA-18 are green, including the security honesty boundary (hasPermission, custom-bean, and compound-SpEL refusals) and the cross-source mis-bound guard (no read ever carries another's authorities). The measured result confirms the ratified POOLING decision (a controller decorates the one pooled capability, it does not mint a second): had the phase minted a capability per endpoint, discovered would have jumped on all three benchmarks and the forecast would have failed; it held unchanged instead. Full record: Volume 5, section 4.14 and the three ledgers' Controller-Authority `actual` sections.

FORECAST-BLIND on the requesting user's system, recorded plainly and not softened: the adopter's inventory system was not provided, so the phase shipped measured on fixtures plus the three existing benchmarks, and the inventory-benchmark measurement is PENDING first contact, the phase's open verification. The `@PreAuthorize` half of the compilation set (the shape the adopter named) is exercised only by fixtures, because `@PreAuthorize` occurs zero times across all three existing benchmarks; whether the adopter's real usage lands inside the parseable set or degrades to honest unverifiable markers is unknown until first contact. If it degrades, that is itself the signal that authority carriage needs the Attestation mechanism (a human asserting what an expression means) sooner than expected.

The trigger, recorded as this phase's evidence because it is a new KIND of trigger: not a benchmark survey, not an internal ranking, but a direct feature request from a first adopter with a large inventory system. The adopter asked for (a) capability discovery from `@RestController` classes and (b) extraction of the required privileges declared in `@PreAuthorize` annotations (authorities, explicitly not roles), so that an external orchestration layer can enforce authorization before invoking capabilities. The orchestration itself (prompt planning, login, token validation) is the consumer's layer and out of scope; Tibyan compiles the DECLARED security contract out of the source and carries it honestly in the manifest, or marks loudly that it could not.

Worth recording about the Attestation trigger, because it is evidence about the roadmap itself: the first real user's need was NOT the filter or wrapper ceiling. The written Attestation trigger (an adopter hitting that ceiling) had NOT truly fired AS OF 2026-07-19; what a real adopter asked for first was the declared security contract, something none of the internal rankings predicted. The roadmap's own rule, that external signal outranks internal consensus, is what made Controller-Authority next. (SUPERSEDED 2026-07-20: the Attestation trigger HAS since fired for real, the maintainer-as-adopter hitting the pms filter ceiling; see the Attestation section above. This paragraph stands as the accurate record of the 2026-07-19 state.)

The design is frozen in Volume 5, section 4.14 (with Volume 4 section 7.1, Volume 6 sections 3.5 and 4.3, Volume 7 section 8 gap 9, and docs/SECURITY.md), from a full read-only census of all three benchmarks. Its decisive findings, briefly: `@PreAuthorize` occurs ZERO times across all three current benchmarks, so the requested SpEL shapes (`hasAuthority`/`hasAnyAuthority` literals) are designed from the request and exercised by fixtures until the inventory benchmark is provided (its measurement is PENDING and recorded as such); the real measurable idiom is `@Secured` with in-tree string constants (296 method-level occurrences across pms and the private enterprise benchmark, always on controllers, never on services), which the census pulled into the compiled set under strict conditions (the modern-enablement gate, the literal-initializer constant rule); and the pooling question has a live answer (controller endpoints delegate to exactly the service methods the tool already discovers, sometimes four routes to one method, so controller evidence POOLS as anchor-and-decorator, never minting a second capability, per ADR-0013). A controller grants NO discovery this phase: it confirms and decorates; a service read the rules refuse stays refused whatever its controller declares. The honesty invariant: a capability carries the COMPLETE compiled declared authority sequence, or a SECURITY-UNVERIFIABLE marker with a closed reason, or ABSENT (no declaration found, explicitly NOT a claim of unprotectedness, since URL-level security is invisible to this analysis). Mis-bound is extended to the security contract: one wrong authority attached to a capability counts against the absolute bar identically to a wrong binding.

Forecast (now CONFIRMED by the build, section top): pms discovered 2 unchanged, getUnit compiled `["1", "2"]` and getLease `["1"]` through their strict `@Secured` anchors, output additive on exactly those two entries; erp discovered 25 unchanged, zero security in the app, output byte-identical (the negative control); the private enterprise benchmark discovered 4 unchanged, 1 compiled a single-authority contract and 3 ABSENT (docs/experiments/enterprise-benchmark.md sections 7 and 7.4); the inventory benchmark PENDING. The build was recommended and taken (unlike P1b and Discovery-Gap this phase changes real output on two benchmarks and delivers the first real user's actual request), one phase, ratified design committed first (rule 12).

### Read-safety receiver awareness: a correctness refinement found by measurement (designed, ratified, and BUILT 2026-08-04)

The first phase in this project whose evidence is a defect the tool's own first real measurement produced,
rather than a gap in an application. The first real attestation measurement on pms
(docs/experiments/second-app-trial.md section 15) refused three of ten authored entries with READ_SAFETY on
bodies that are genuinely read-only: the write-set clause of the attestation read-safety check tests a
call's NAME before it looks at the receiver, so `someLocalMap.merge(...)` on a map the method constructed
itself is refused as "a mutating body is not a read".

Why it is a ratified design and not a rule-13 amendment: the change alters a matching outcome. Bodies that
refuse read-safety today would pass it. That is not discovery and not attestation, but it is a different
verdict at a gate, so rule 12 applies and the build is a separate later session.

What the design is, in one line: a mutating-named call is read-safe ONLY IF its receiver is PROVABLY a
local, freshly constructed, allowlisted JDK container that never escapes to persistence; everything else,
including everything the tool cannot decide, refuses exactly as today. The rule is Volume 5 section
4.15.2.1, with six provable-local conditions (L1 to L6), ten located refusal cases (R1 to R10), a closed
container allowlist, and fixtures RS-1 to RS-8.

The argument that makes it safe rather than a liberty: receiver scoping is already the ratified rule in the
three OTHER places the tool asks this question (section 4.2 condition 6, section 4.10 condition 7, section
4.13.3 condition 3), and none of them ever produced this false refusal. But the attestation check must NOT
simply copy their scoping, because they can afford to scope to repository fields only thanks to a shape
strictness that attestable bodies abandon by definition; copying it would silently admit
`someService.save(entity)`. Hence a carve-out on provable locality rather than a widening to
non-repository receivers.

This phase names its own absolute invariant, MIS-SAFE: a call that writes to application state classified
as read-safe. `mis-safe 0` joins `mis-bound 0` and `attestation-drift 0`. The governing bias is stated: a
false refusal is a coverage miss, a false pass is a safety breach, and the design prefers the miss every
time, so genuinely-safe bodies whose locality cannot be proven stay refused and that is correct.

Forecast, honestly near zero in coverage: three pms reads change read-safety verdict and all three land on
a DIFFERENT refusal (the AT-2 shape hole), attested stays 0 on every benchmark, erp is a measured negative
control with zero provable-local write-set calls anywhere in it, and the private enterprise benchmark is
byte-identical by construction because the check has one call site and that benchmark has no record file.
Discovered rows stay byte-identical on all three; any movement is a regression finding.

Its place in the sequence, and why it is first of the three pending decisions: it is the only one of them
that makes the tool stop saying something false, and it is a precondition for AT-2 being measurable at all,
because category B is the aggregate category and in-application aggregation is written with `merge` and
`remove` on local collections. So the order is: this refinement, then the E3 enum-input decision, then AT-2
(SHAPE). The three are independent of each other in mechanism; only this one is a correctness fix.

### Enum-typed capability inputs: the E3 reopening, second of the three measured blockers (DESIGNED 2026-08-04, RATIFIED; step EI-R BUILT 2026-08-05, step EI-1 BUILT 2026-09-01)

E3's ratified reopening condition ("revisit only if enums become capability inputs") has FIRED, from a
direction E3 could not see: attested PREDICATE filters take parameters, and real filters take domain enums.
Three of ten authored pms entries refused STRUCTURAL_FRAME on one, and erp's enum-parameter subset is its
only blocked remainder. The output decision is NOT reopened.

THE DECISION: keep STRING and add a first-class, per-input, optional value constraint (Volume 4 section
3.9, validation rule 10), rendered by both backends as a schema-level closed set and enforced by the runtime
before invocation. A first-class ENUM primitive was argued and REFUSED against the growth bar: the wire form
of an enum input genuinely is a string, `Type` is deliberately flat and an `ENUM(values...)` member would be
the first type carrying a payload, and the same enum would otherwise be STRING as an entity field and ENUM
as a parameter, an asymmetry with no truth behind it. The Annotation hook was also refused, on Volume 4
section 3.8's own argument: an output annotation is a hint, an input constraint is a contract, and a
contract whose completeness is an invariant needs a type the core can validate.

STRUCTURAL PROVABILITY, with a rule the benchmarks forced. The set carried is what the application ACCEPTS
ON THE WIRE, which is not always the constant names: a bare enum advertises constant names (shape a), and a
`@JsonValue`-mapped enum advertises the literals (shape b, the pms generated idiom, where `CostCategory`
accepts `maintenance` and would reject `MAINTENANCE`). Anything ambiguous refuses to constrain. The
CONSTRUCTOR-LITERAL TRAP is named and guarded: pms's `LeaseStatus` carries `DRAFT("Draft")` with no
`@JsonValue`, so its literals are decorative labels and a rule that read them would advertise values the
application rejects.

THE ATTESTATION SPLIT, the elegant case: the TOOL proves the closed VALUE SET structurally, the HUMAN
attests the parameter's MEANING, each from its own source of truth, and neither can substitute for the
other. An attestation can never supply, widen, narrow, or reorder a set, because a value set is a
structural fact and section 4.15.2 forbids an assertion from filling a structural hole.

A BLOCKING PRECONDITION FOUND WHILE DESIGNING, split by name rather than glossed. The runtime resolves its
target method by reconstructed parameter types, so a STRING input yields `String.class` against a real
`CostCategory` parameter and the capability is UNINVOKABLE; TIMESTAMP has the same problem against
`LocalDate`. It is latent only because every capability shipped to date has one identifier-typed input. The
phase is therefore two build steps: EI-R (runtime argument binding by declared parameter type, plus
closed-set validation before invoking, Volume 7 section 9) FIRST, then EI-1 (the constraint, the provider
rule, the surfaces). RATIFIED SEQUENCING: the backends do not render a closed set until EI-R is green,
because a shown set that is not enforced is the input-side modeled-opaque claim. If EI-R proves larger than
one session, enum inputs do not ship; they never ship unenforced.

The phase invariant is MIS-CONSTRAINED 0: never advertise a value the application would reject, never
advertise a set not read from a resolved enum, never render a set on a surface that does not enforce it.
Advertising a subset of the accepted values is safe; advertising a superset is a breach.

Forecast: pms three entries move STRUCTURAL_FRAME to CATEGORY_MISMATCH (refusals stay 10, attested stays 0,
coverage gain ZERO because pms is blocked on AT-2); erp is where the payoff lands, 10 read methods become
attestable, but the gain is gated on a human authoring entries rather than on a build, so the measured
result is unchanged; the private enterprise benchmark is untouched by construction. Discovered stays 5 / 25
/ 4 BYTE-IDENTICAL BY CONSTRUCTION, since no discovered capability can have an enum input. Fixtures EI-1 to
EI-9 pre-registered, EI-3 (the constructor-literal trap) and EI-9 (the mis-constrained guard) being the
safety ones.

Sequence after this: AT-2 (SHAPE) remains the third and last of the three measured blockers, and the one
that actually moves pms.

### Read-Identity: the by-primary-key body proof on the entity-returning path (UNPLANNED, designed, ratified, and BUILT 2026-08-31)

WHY IT IS ON THIS ROADMAP AT ALL. It was not ranked, not designed in advance, and not next. It preempted
EI-1 under the standing rule that a mis-bind outranks every other priority including the phase in hand.
Nothing about the post-v1 ranking changed; one phase was inserted ahead of it because the invariant broke.

WHAT WAS WRONG. Section 4.1, the entity-returning discovery path, matched on SIGNATURE alone: an `@Service`
class, a public non-static method, a return type resolving to E, one parameter mapping to E's id type, and
no examination of the body at all. So the by-primary-key fact that section 4.2.1 names "the safety-critical
discriminator", and that section 4.2 condition 4 anchors on the framework contract precisely because names
cannot carry it, was never established on that path. `mis-bound 0` was true of what the rules admitted and
the rule had no clause for the counter to enforce.

THE EVIDENCE. A fourth real application, as-backend (docs/experiments/as-backend-trial.md), where
`getCPESCustomerProduct(id)` had bound to a method that treats the id as a foreign key, runs a filter query,
returns element 0, calls a remote adapter, and can return null. Found while answering an adopter question,
not by a planned benchmark run.

THE RULE (Volume 5, section 4.1.1). Both paths now share one proof. Conditions 5 to 8 are section 4.2's
conditions 6, 5 and 4 run by the same code with the same refusals; condition 9 is the entity path's own
identity shape, the loaded entity returned untransformed in one of two forms, replacing the mapping proof
that path has no use for. The rule can only remove candidates, never add one, so any measured rise in
discovery would be a regression finding.

MEASURED, forecast held exactly on all three same-tree baselines: pms 5 to 5, erp 35 to 35, both with
serialized IR, MCP output and OpenAPI document BYTE-IDENTICAL to a worktree built at the pre-build commit;
as-backend 6 to 5, the single difference being the removal of the mis-bind and its artifacts.

ONE MID-BUILD DESIGN CORRECTION, recorded rather than left to diverge (section 4.1.1, THE BUILD-FORCED
CORRECTION). The ratified text first excluded the section 4.13.3 private-helper hop from this path,
justified as a coverage miss with no evidence behind it; fixture DG-3b presents exactly that shape, so
excluding it would have regressed a read the Discovery-Gap phase already covered. The hop is included under
section 4.13.3's own trace, ending in the identity shape. It changes a matching outcome, so it is not a
rule-13 diagnostic amendment; it is written up as a correction with its cost stated.

THE OPEN VERIFICATION, named: the private enterprise benchmark was NOT re-measured, because the build
session had no path to it. Its ledger records 2 of its 4 discovered reads as entity-returning, so those two
are exactly what the new proof judges and their outcome is unknown. Its "4" is stale until someone with the
application re-runs the scan. This is the one thing about this phase that is asserted rather than measured,
and it is stated here so no later session inherits it as settled.

Sequence after this: unchanged. EI-1, then AT-2 (SHAPE).


### Attestation AT-2, the SHAPE category (ratified 2026-07-21 with the phase; BUILT 2026-09-01)

WHAT IT ADDS. The third category of knowledge can now be asserted about what a read RETURNS, not only about what its predicate means. A human names the subject entity and the result shape; the tool proves everything around that claim and refuses the entry if any of it fails. It is the third and last of the three measured blockers, and the one both pms and as-backend rank first.

SEQUENCE NOTE, recorded because it departs from the written order: the roadmap put EI-1 before AT-2, and the maintainer reordered them. AT-2's only hard dependency is AT-1's machinery, which has been built since 2026-07-25, so the reorder is a ranking decision, not a dependency violation. EI-1 stays pending and unblocked.

WHAT THE TOOL STILL PROVES around a shape assertion: the local-tie precondition (a shape claim is honest only over a body whose persistence the read-safety check could actually inspect, so a delegation-built wrapper routes to AT-3 BY NAME and is never accepted here); the asserted subject entity exists in the model; structural non-contradiction (a body whose SINGLE repository access provably reads a different entity refuses); the by-primary-key load where the entry claims an identifier; and read-safety over the whole fingerprinted slice, unsoftened.

THREE RATIFIED-TEXT UNDERSPECIFICATIONS RESOLVED IN THE BUILD, all recorded in Volume 5 section 4.15.2.2 rather than quietly chosen: the aggregate discriminator is the reserved `id` mark, which now obliges a proven by-primary-key load instead of merely a typed parameter; a sentence-covered input means the target has a predicate hole too and belongs to `both`; and the fingerprinted slice is one function of the target, not of the category, which adds the return type declaration for every category and re-stamps every existing fingerprint.

SCOPE, DELIBERATE: `both` is NOT built. AT-2's ratified scope is "adds one category". Every compound target, a filtered read whose element type is also unresolvable, stays refused with a located message. This is the main limit on what the build unlocks, and it means the section 4.15.11 ceilings are NOT reachable by AT-2 alone: those counts include compound targets.

MEASURED (2026-09-01): all three benchmarks BYTE-IDENTICAL across serialized IR, generated MCP output, and generated OpenAPI document, verified by `diff -r` against a worktree at the pre-build commit. None carries an attestations file, so the phase is provably additive exactly as fixture ATT-11 requires. pms 5 / erp 35 / as-backend 5 discovered, mis-bound 0, attested 0.

THE OPEN VERIFICATION, unchanged and now the deciding one: no real attesting human has run this flow. The build did not manufacture one, because the tool authoring an assertion about code it does not know is the first thing section 4.15.9 forbids. The first real attestation is the adopting team's to write, and the first-use measurement is what decides whether AT-3 proceeds.

Sequence after this: EI-1 remains pending. AT-3 (BOUNDARY) needs its own ratification with stronger guards; AT-4 is optional and separable; `both` needs a decision of its own.


### Enum inputs, step EI-1: the input value constraint (ratified 2026-08-04 with the phase; BUILT 2026-09-01)

The second and last step of the enum-input phase, and the fourth deliberate IR growth after FLOAT,
`outputMultiple`, and `Attestation`. A capability may now carry a per-input closed value set, and both
backends render it as an enforced closed set because the runtime enforces it (EI-R, built 2026-08-05, which
is why the phase was split in this order).

RATIFICATION RECONCILIATION, done before any code. Volume 5 section 4.16, this roadmap, and CLAUDE.md all
recorded the phase as RATIFIED with only its build pending, and rule 10 was already written into Volume 4
section 5; but the headings of Volume 4 section 3.9 and Volume 6 sections 3.7 and 4.5 still read "pending
ratification", having been written in the same session and never updated. Four concurring records beat
three stale headings; the build corrected them and recorded the reconciliation (Volume 5, section 4.16.10).

WHAT THE TOOL PROVES. A bare enum carries its CONSTANT NAMES; a value-mapped enum whose single `@JsonValue`
no-argument accessor returns a field assigned from one constructor parameter, with every constant passing a
string literal there, carries THOSE LITERALS; anything else refuses to constrain and therefore refuses the
capability, because an unconstrained STRING on a parameter that only accepts a closed set invites an agent
to send a value the call will reject. The constructor-literal trap is the phase's most important negative
and fixture EI-3 pins it.

FOUR BUILD DECISIONS, recorded in Volume 5 section 4.16.10 rather than quietly chosen: the constraint is
carried at attestation intake, the only place an enum input can exist; the ratified `enum-parsed-in-body`
deferral needed a new survey channel; byte-identity is the serializer's job through a mixin scoped to one
property, because core carries no Jackson annotations; and `mis-constrained 0` prints only where a
constraint exists.

MEASURED (2026-09-01): pms 5 / erp 35 / as-backend 5 discovered, with serialized IR, generated MCP output
and generated OpenAPI document all BYTE-IDENTICAL to a worktree at the pre-build commit. Discovered is
byte-identical BY CONSTRUCTION, not merely by measurement. The one measured change anywhere is pms's survey
gaining two `enum-parsed-in-body` lines, both writes, correctly named as visible-and-unconstrained.

STILL GATED ON AN ATTESTER: erp's 10 enum-parameter reads become ATTESTABLE, not attested. This is the
first phase whose payoff waits on a human authoring entries rather than on a further build.

Sequence after this: AT-3 (BOUNDARY, both members), the compound category `both`, and AT-4's draft lifecycle
were all designed, ratified and built on 2026-09-01, and every ratified attestation category is now built.
The assist that would produce drafts is deferred as a stack decision (Volume 5, section 4.15.5.1). Nothing
was pending from the ratified sequence when the next entry's evidence arrived.

### The first real attestation run: five candidates, evidence-ranked (as-backend, 2026-09-04 to 2026-09-08; candidate 1 RATIFIED 2026-09-23, the other four NOT RATIFIED)

The external signal of 2026-07-12 reranked the post-v1 order. This is the second signal of that kind, and it
comes from the event every attestation phase named as its open verification: a named human authored, read and
confirmed attestations on a real application, generated the surface, and called the tools. The full record,
with each claim marked by where it was verified, is docs/experiments/as-backend-trial.md section 9.

WHAT IT VALIDATED, recorded first so the findings are not read as a failure. Thirteen of fifteen confirmed
entries are the compound category `both`, which is exactly what that ledger's section 7 predicted from the
deferral profile before any human wrote an entry. The per-entry confirmation gate held under real pressure.
And five tools that read ONE table, differing only by a hardcoded constant so that nothing structural
distinguishes them, were told apart correctly by an agent reading only the attested sentences. That is the
attestation thesis tested where it could actually fail.

FIVE CANDIDATES follow, ranked by the evidence rather than by size or by readiness. Candidate 1 was designed on
2026-09-20 and RATIFIED on 2026-09-23 (Volume 7, section 10, record at 10.10); its build is the next phase and
its own session. The other four are not ratified and none is a plan; rule 12 requires a design session and
frozen text before any of them is built, and rule 5 governs the one that touches the IR. They are recorded here
so the ranking is a written artifact rather than a memory.

1. PER-CAPABILITY ATTESTED EXPOSURE. Rank 1 because it is the only finding where the current design pushed a
   real adopter into a strictly LESS safe configuration than the one they wanted. All fifteen attested
   capabilities compiled to `securityUnverifiable(conflicting-declarations)`, so under the ratified G8 default
   none of them register; the adopter set `tibyan.attested.exposeSecurityUnverifiable=true` to use the reads
   they had just personally reviewed, and that flag is capability-wide, so it also opens every other
   unverifiable capability in the service, now and in future. The refusal itself is correct and must not be
   softened: the authority is declared on the controller and the tool binds to the service method beneath it.
   What is wrong is that the only available answer is a global switch. The shape to design is an allowlist,
   an adopter naming the capabilities they reviewed, so that opening fifteen reads opens fifteen reads. Read
   with the caveat that controller-declared authority may be a house style rather than a general idiom.
   DESIGNED 2026-09-20, RATIFIED 2026-09-23 AS WRITTEN: Volume 7, section 10, record at 10.10. Runtime only,
   one new property `tibyan.attested.exposeReviewed` holding exact capability names, five conditions, fixtures
   RX-1 to RX-10, a forecast gain of zero today, and the five decisions of 10.9 standing at their recommendations
   by ratification as written (fail startup, name only, a bare list plus one SECURITY.md sentence, the global
   flag kept with a WARN, the property name). NOT BUILT: the build is the next phase and its own session, and it
   opens with the maintainer's discussion, then a re-read of 10.9 and 10.10, before code.

2. THE DRAFT PROVENANCE PAIR. Two defects in one record shape, and they belong to one design session because
   they are the same question asked twice: who said this, and when did they say it. First, intake checks
   `assertedBy` for blankness BEFORE it recognises the `ai-draft` origin, so a draft, which by definition has
   no human asserter, cannot be queued without inventing one; twenty real drafts carried the placeholder
   string `UNCONFIRMED-DRAFT` in the field whose only job is to name a person. Second, confirmation writes the
   CONFIRMER into that same `assertedBy`, so once an entry is confirmed the record can no longer say who
   drafted the words and who signed them. AT-3 guard 1 treats exactly that separation as load bearing for a
   security decision; this is the documentation case losing it.

3. REFUSE AN UNATTESTABLE TARGET AT STAMP TIME. Ordering only, no rule change. A collection-typed parameter
   refuses the capability at intake, and four entries were confirmed BY NAME and then deleted: `tibyan attest`
   stamped them without complaint, a person read and signed four claims, and only the later scan reported the
   target was never attestable. The structural refusal is knowable when the fingerprint is stamped. Spending a
   human's review on a target the tool already knows it will refuse is the one cost this design should never
   impose, because human review is the scarce resource the whole mechanism is built around.

4. DERIVE THE MCP BASE URL FROM THE SERVLET CONTEXT PATH. Runtime, small, and the same shape as the version
   defect fixed on 2026-09-03, in the same class. The starter supplies only `spring.ai.mcp.server.name` and
   `.version`, so a host with a context path advertises a message endpoint without it and a client opens the
   stream then POSTs into a 404. Every adopted service needed the property written by hand. The `addLast`
   lowest-precedence contract that makes the existing defaults safe makes this one safe too.

5. COLLECTION-TYPED CAPABILITY INPUTS. Real evidence, four lost capabilities, and deliberately ranked LAST
   because it is the only candidate here that is an IR growth under rule 5 and therefore the only one that has
   to clear the Volume 4 section 7 bar rather than being a fix. A list-of-primitive input is a plausible
   growth and the enum-input phase is the precedent for how to argue one, including its refusal of a first
   class ENUM primitive. It is a phase, not a patch, and it does not start until the four above are settled.

DOCUMENTATION, not phases, from the same run: an adoption-hazard note that an app customising its HTTP message
converters can break the generated MCP endpoint with an error that points nowhere near the cause; and a note
in docs/CONNECTING-AN-MCP-CLIENT.md that a client's connection health check is not capability health, since
the honest liveness probe is calling a known tool.

THE OPEN VERIFICATION THAT REMAINS. The boundary category was never exercised: this application has no
boundary entry, so AT-3 guard 1, whether the security owner and the asserter are the same person in practice,
is still unanswered by any real use. It was the sharpest question AT-3 asked of itself and it stays open.

A NON-CANDIDATE, recorded so it is not proposed again without new evidence (2026-09-20;
docs/experiments/as-backend-trial.md section 9.9). 62 of this app's 86 multi-read deferrals are one idiom,
rows plus a same-filter count over one repository, which invites a list-plus-count discovery rule. A census of
the 86 bodies says no: 25 carry a hardcoded predicate, 15 read the caller from ambient context, and of the 6
that survive every lexical filter, 4 silently return at most 25 rows because an empty filter in this app
carries a default page size. The honest ceiling is 2 reads against 159 entities, and every other body fails
in the worst way available, provable structure with unprovable meaning. Section 4.11.7 stands, and the five
candidates above, which make a human sentence cheaper and safer to give, remain the evidence-ranked work.
The owed re-scan ran the same day (section 9.8): discovered 5, attested 15, mis-bound 0, attestation-drift 0,
mis-constrained 0.

### v2 direction: draft first, prove what can be proved, label the rest, gate writes (maintainer decision 2026-09-23; SPIKE DL-0 opened)

THE DECISION, and whose it is. After the first real adoption (section above, and docs/experiments/as-backend-trial.md
section 9), the maintainer judged the refusal-first, read-only v1 too narrow and too hard to use: 5 verified and 15
attested tools on a 159-entity application, reached through 32 manual actions, 24 of them per entry, by the tool's
own author. The maintainer considered abandoning the source-reading approach for a generic web-level tool. That
option was assessed on 2026-09-23 against a market scan (recorded in docs/competitive-landscape.md once that file
is refreshed; its Rindler section is stale) and refused on evidence: the generic web quadrant is the most crowded,
most capital-heavy and least reliable one, every vendor gates writes behind a human, and the one thing found
nowhere else in the market is what this project already has, a proof of read-only-ness from source at generation
time. The maintainer then chose the direction below. It is a change of product philosophy, not of engine.

THE SHAPE OF v2, in five clauses:

1. DRAFT FIRST. Every public method of every service class becomes a candidate tool, reads and writes alike.
   Nothing is refused for being unprovable; the structural drafting is deterministic and offline, and a
   description sentence is left for a person or an agent to fill through the same file contract AT-4 opened.
2. PROVE WHAT CAN BE PROVED. The discovery rules and the attestation intake are unchanged in what they prove.
   A candidate that is a discovered capability is VERIFIED; one carried by a confirmed attestation is ATTESTED.
   The four zero invariants (mis-bound, mis-safe, attestation-drift, mis-constrained) scope to those two tiers
   and stay absolute there.
3. LABEL THE REST. Read-safety (Volume 5, section 4.15.2 and 4.15.2.1) becomes the classifier of every other
   candidate: READ when it passes, WRITE when a mutation is visible, UNKNOWN when it cannot decide. A label is
   never a proof of meaning; it is an honest statement of what the tool could establish. The surfaces carry the
   label, and readOnlyHint is true only on VERIFIED, ATTESTED and READ.
4. GATE WRITES. A WRITE candidate is never served by default. It registers only behind an explicit per-name
   allowlist; the exact-name list ratified in Volume 7, section 10 is the first member of that gate and its build
   stands. Dry-run and confirmation are design questions for the phase that follows the spike, not for the spike.
5. BATCH REVIEW. Review happens in a file, in one reviewable act per file, with each entry's approval explicit.
   The per-entry-only confirmation rule of AT-4 is retired for drafts; the provenance record stays.

WHAT DOES NOT CHANGE: the five abstractions, the zero-dependency core, one plugin per module, determinism of
generation, golden tests, the fingerprint drift check, and the source-reading position itself.

WHAT THIS RETIRES, named so nothing is silent: "fail loud, never guess" as a rule about EMISSION (it remains the
rule about PROOF: a label is never upgraded to a proof by guessing); the read-only discovery boundary; the standing
gate against any write reaching a surface; and the sentence "zero wrong bindings", which is thereafter a claim
about the VERIFIED tier only. The constitution (CLAUDE.md rule 5's read-only clause and the refusal bias) is
amended AFTER the spike, from its evidence, not before it; during the spike, drafts live outside the IR and no hard
rule is touched.

SPIKE DL-0, DRAFT AND LABEL (opened 2026-09-23, per the spike precedent: an unknown this big is spiked before
anything is built on it). Scope: a `tibyan draft <source-root>` command that writes `tibyan/drafts.json`, one entry
per public non-static method of every concrete service class, each carrying the target in the attestation target
format, a minted name, the label (VERIFIED, ATTESTED, READ, WRITE, UNKNOWN) with its located reason, the inputs
with their mapped types where the mapper can map them, the return type, an empty description for the reviewer, and
review state `pending`. No IR change, no core change, no model call, deterministic, golden-tested on the example
app. Questions: Q1, the label census on as-backend (how many candidates, how many of each label); Q2, label
correctness, checked two ways, automatically (every discovered binding labels VERIFIED, every confirmed attestation
target labels ATTESTED and would label READ without the record, none of the fifteen labels WRITE) and by the
maintainer reading a sample of WRITE and READ entries; Q3, whether an approved draft, read or write, serves and
invokes end to end through the runtime behind the gate, on the example app first. Exit: the drafts file generated on
as-backend with the census recorded as docs/experiments/as-backend-trial.md section 10, the automatic checks of Q2
green, and Q3 answered on the example app or explicitly deferred to DL-1 with the reason. A wrong label on any
attested or discovered target is a spike FAILURE and stops the direction until explained.

DL-0 BUILT 2026-09-23 (commit fdd5ec3). Q1: 990 candidates on as-backend, VERIFIED 5, ATTESTED 15, READ 401,
WRITE 204, UNKNOWN 365. Q2 automatic half green, no failure condition fired; the maintainer's reading sample is
owed. Q3 deferred to DL-1 by name (input binding and the manifest shape). Two amendments recorded in the ledger:
the void rule and the target spelling. Record: as-backend-trial.md section 10.

DL-1, REVIEWED DRAFTS, DESIGNED 2026-09-23 AND RATIFIED 2026-09-23 AS WRITTEN (record at Volume 5 section 4.17.12; D1
to D6 at their recommendations; build pending, its own session): Volume 5, section 4.17, with the IR half in
Volume 4 section 3.10, the surfaces in Volume 6 sections 3.9 and 4.6, the runtime in Volume 7 section 11, and the
CLI in Volume 11 section 3.5. The drafts file becomes the record, review is a batch edit by a named human, intake
proves eight conditions (RV-A to RV-H) before an approved entry enters the IR as `reviewed_<name>` with a `Review`
component, WRITE and ACTION exist behind an exact-name write gate, and section 4.17.9 lists the six constitution
changes the ratification decides. Six open decisions (D1 to D6) carry recommendations; collection inputs are
deferred to DL-2 by name. Ratified 2026-09-23 and BUILT 2026-09-23 (build record Volume 5 section 4.17.13): the
reviewed tier, the write gate and Volume 7 section 10 are in the tree, the three benchmarks are byte-identical,
and the example app's write runs end to end behind the gate. The reading sample then ran (as-backend-trial.md
section 10.2): 3 of 30 READ labels hide a write through a call into another bean. DL-1.1, the bean-call closure of
the READ label, was ratified and BUILT 2026-09-23 (Volume 5 sections 4.17.14 and 4.17.15): on as-backend the three false READs
are caught, READ 401 to 271, WRITE 204 to 337, and the benchmarks are byte-identical. DL-1.2, the closed-set amendment of the
closure (Volume 5 section 4.17.16), was ratified and BUILT 2026-09-24 (Volume 5 sections 4.17.16 and 4.17.17): READ 271 to 346, every audited pure
read READ again, and 55 same-arity overloads recorded as the next candidate. DL-1.3 (Volume 5 section 4.17.18), bodiless accessors
and overload disambiguation, was ratified and BUILT 2026-09-24 (Volume 5 sections 4.17.18 and 4.17.19): 42 of the 55 decided, UNKNOWN 240 of 990.
The first real approvals happened 2026-09-24 (as-backend-trial.md section 10.6): reviewed 9, refused 0. The next
evidence is a reviewed tool called through a real MCP client on the adopter's running services.


### Binding Identity: the qualified binding, verified at resolution (UNPLANNED, found by measurement 2026-09-24; designed, RATIFIED and BUILT 2026-09-24)

Goal: close F7 of docs/experiments/as-backend-trial.md section 10.7, the runtime mis-bind risk the first
end-to-end reviewed call exposed on every tier: bindings are `<SimpleName>#<method>` and the runtime resolves
beans by simple name, so a reused simple name (four `LookupServiceImpl` classes in the adopter's reactor) can
invoke a class the reviewer never read. Evidence: one reviewed tool did resolve to the wrong class and failed
only because that class lacks the method. Rank: it preempts everything below the invariants, DL-2 and candidate
hygiene included, under the standing mis-bind rule; it is the follow-on Entity Identity named on 2026-07-13
(Volume 5 section 4.12.4) with its evidence finally in hand. Design: Volume 7 section 12 (R1 the provider
qualifies the binding on every tier, R2 the runtime resolves by user-class canonical name and refuses zero or
many, R3 an unqualified manifest fails startup), Volume 5 section 4.18, Volume 4 rule 7 amended reading,
Volume 6 section 3.10. Exit criterion: fixtures BI-1 to BI-11 green, goldens updated with a diff confined to
`binding` values, the three benchmarks' counts unchanged against a pre-build worktree (discovered 5 / 35 / 5,
attested 15, reviewed 9, every zero invariant), and the section 10.7 call repeated on as-backend with the
wrong-class case answering `no bean of class`. Not byte-identical by design; any movement outside the
`binding` field is a regression finding.


### DL-2: collection inputs and structured inputs (designed and RATIFIED 2026-09-24; DL-2a and DL-2b BUILT 2026-09-24)

Goal: lift the one refusal that now dominates the reviewed tier. On as-backend 473 of 990 drafted methods
have inputs the tool can bind; the rest are refused at RV-E for a collection or a DTO parameter, and that
includes 294 of the 372 writes. Evidence: the full-tree parameter census of Volume 5 section 4.19.1. Design:
DL-2a, a per-input `multiple` flag (Volume 4 section 3.11, the E4 precedent) with the runtime building the
declared collection; DL-2b, the Shape element and `SHAPE_REF` (Volume 4 section 3.12, the sixth IR growth,
rules 15 and 16), with disclosed partiality, shapes in the fingerprint, and Jackson binding that refuses
unknown properties (Volume 7 section 13; Volume 6 sections 3.11 and 4.7). Nine decisions with
recommendations in section 4.19.5. Forecast: byte-identical on the discovered and attested tiers of all three
benchmarks by construction; the as-backend drafts file goes from 473 to 533 (2a) to 853 (2b) entries typed on
every input, labels and census unchanged. Exit criterion per step: its fixtures green (CI-1 to CI-7; SI-1 to
SI-12), the forecast met against a pre-build worktree, and one real approval on as-backend exercising the new
input shape end to end through an MCP client. Rank: first coverage item after the invariants; nothing else
moves as many real methods.


### Hosted bindings, the startup check (candidate F9; designed, RATIFIED and BUILT 2026-09-24)

Goal: register only what a service can execute. Evidence: on as-backend's pharmacy service seven of eleven
reviewed tools and two of fifteen attested tools bind to beans that service does not host, and an agent finds
out at the first call (as-backend-trial.md sections 10.7 to 10.11). Design: Volume 7 section 14, rule H1 (the
section 12.2 resolution run once at startup; an unhosted or ambiguous tool is not registered and is named on
one line), H2 (an exposure-list name the service does not host stays valid and is reported), H3 (no property).
Fixtures HB-1 to HB-5. Forecast: byte-identical benchmarks by construction; the pharmacy registration line
drops by at least the nine named tools. Rank: after DL-2, the first honesty item; it changes what a registration
line claims, not coverage.


### Publication of 0.4.0 postponed (maintainer decision 2026-09-24)

The tree carries everything since 0.3.0 (the reviewed tier and the write gate, DL-1.1 to 1.3, Binding
Identity, DL-2a and DL-2b, F9), all measured, and it is not published: the maintainer decided that 0.4.0
waits for two things, the description drafter decision and the caller at the tool boundary. Both are
designed below, pending ratification. Nothing else is queued ahead of them.

### Describe: the AT-4 assist as one quarantined command (designed, RATIFIED and BUILT 2026-09-24)

Goal: end the hand-written description. Evidence: eleven approvals on as-backend, every sentence written by
a person from the slice the tool fingerprints. Design: Volume 5 section 4.15.5.2, Volume 11 section 3.6,
Volume 16 section 7, ADR-0016. Exit criterion: fixtures DS-1 to DS-6 green against a fake transport,
benchmarks byte-identical, and one real run on as-backend whose drafted sentences the maintainer reads and
approves or corrects by name.

### SEC-0 and SEC-1: the caller at the tool boundary (designed and RATIFIED 2026-09-24)

Goal: no reviewed write and no contract-carrying tool runs without a caller, and the caller the host
authenticated reaches the bound method. Evidence: sections 10.6 to 10.11 of the as-backend ledger. Design:
Volume 7 section 15 (a three-question spike, then S1 to S5), ADR-0017. Exit criterion: the spike's three
answers recorded; fixtures SC-1 to SC-8 green; benchmarks byte-identical; the section 10.7 run repeated on
as-backend with a real token, the anonymous calls refused and the authenticated ones narrowed as described.

SPIKE SEC-0 RUN 2026-09-24 (Volume 7 section 15.9; as-backend-trial.md section 10.14): three yes. Q1 with a
finding, Spring AI's whole WebMVC MCP configuration steps aside once any transport provider bean exists, so
the runtime registers its provider and the router for it; Q2 the caller reaches exactly its own call through
the per-request transport context; Q3 the host's own method security fires on the invocation. S1 and S2 are
in effect as built for the spike. The pharmacy leg ran anonymously (the caller captured as none beside the
adopter's own log line); the token leg waits for a token from the adopter's login. NEXT: build SEC-1.

SEC-1 BUILT 2026-09-24 (Volume 7 section 15.10; as-backend-trial.md section 10.15; docs/SECURITY.md): S3 the
compiled contract enforced exactly before invoking, S4 `tibyan.security.requireCaller` default true refusing
anonymous reviewed writes and actions, S5 the caller-gate startup line; SC-1 to SC-8 green at the gate and end
to end; benchmarks byte-identical by construction; on the pharmacy service the section 10.11 write is refused
anonymously before the service is entered and the reads run. EXIT CRITERION: three of four clauses met; the
authenticated as-backend leg waits for the maintainer's token. NEXT: that token run, then cut 0.4.0.

THE TOKEN LEG RAN 2026-09-24 (as-backend-trial.md section 10.16; Volume 7 section 15.11): the exit criterion is NOT met
in full. The calls run as the caller and a real contract refuses a caller lacking it, but the narrowing clause
fails on F11, the host's thread-bound request context not reaching the tool thread. Two candidates, not ratified:
F11 (spike running sync tools on the request thread) and F10 (refuse an absent or unknown top-level argument).

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


BUILT OUTCOME, measured (2026-08-04): the forecast held to the number. pms refusals stayed 10 and
redistributed exactly as forecast, read-safety 3 to 0, shape hole 4 to 7, enum input 3 unchanged, attested
0; the three `ReportingServiceImpl` aggregates each moved to their true next cause. THIS CAUSE-COUNT CHANGE
IS THE INTENDED EFFECT: the tool stopped calling three read-only aggregates mutating bodies. erp was inert
as forecast, and the private enterprise benchmark untouched by construction. The serialized IR and ALL
generated output (MCP and OpenAPI) are BYTE-IDENTICAL on both available benchmarks, verified by `diff`;
discovered stays 5 / 25 / 4. A MIS-SAFE probe on real code authored an attestation against a genuine
`costRepository.delete(cost)` body and intake refused it with the located R1 reason, so a real write is not
classified safe on real code. Fixtures RS-1 to RS-13 green, including the three refusal guards and the
MIS-SAFE family; `mvn clean verify` green across 12 modules; core untouched and still zero-dependency.

THE AT-2 AGGREGATE CATEGORY IS NO LONGER FALSELY BLOCKED BY READ-SAFETY, which was the point of doing this
first. Sequence unchanged for what remains: the E3 enum-input decision, then AT-2 (SHAPE). Nothing of
AT-2/3/4 is built.

A build-time note recorded per rule 13, changing no matching outcome: several ratified refusal cases are
unreachable through the attestation intake, because a collection-typed parameter fails intake condition 3
(parameter type mapping) before read-safety is ever evaluated. R4, the parameter-aliased receiver, is the
clearest example. Those cases are therefore proven by direct fixtures against the check rather than end to
end, and RS-1, RS-2 and RS-3 carry the end-to-end proof. The ratified rule is unchanged; only the fixture
construction adapts to where the check is reachable.


EI-R BUILT (2026-08-05), the ratified FIRST of the phase's two build steps. Runtime only: core, provider and
both backends untouched, so generated output could not move and did not. The adapter now resolves its target
method by name and ARITY (skipping bridge and synthetic methods, refusing loudly on two genuine same-arity
overloads rather than guessing) and converts each argument into that method's DECLARED parameter type,
including the java.time types and enums. A new `EnumWireValues` implements the same wire-value rule the
provider will use to advertise, so advertised and accepted agree BY CONSTRUCTION; an illegal value is refused
before invoking with the legal values named, which is the enforcement a schema closed set promises. Jackson
is matched by annotation type NAME and does not become a runtime dependency. Measured: all 27 runtime tests
green including the three pre-existing adapter cases that guard the ratified invariant that every existing
capability invokes identically; both benchmarks BYTE-IDENTICAL on IR, MCP and OpenAPI; pms refusal causes
UNCHANGED at STRUCTURAL_FRAME 3 and CATEGORY_MISMATCH 7, which is correct for a runtime-only step. Fixtures
EI-R1 to EI-R11, including a character-exact replica of the real pms `CostCategory` proving all eight wire
literals and the refusal of the constant name.

STEP EI-1 IS NEXT and is what moves the refusal ledger: the core component and rule 10, the provider's
structural-provability rule, and the backend surfaces. The ratified sequencing rule is now satisfied in the
direction it was written, since EI-R is green, so EI-1 may render the closed set when it lands.

### 4.1 Release engineering (v1): the active phase (decisions recorded 2026-07-02)

With all Tier 2 semantic phases built (E1 to E5) and the tool discovering 27 capabilities across two real benchmarks with zero mis-bindings, the semantic core is proven enough on real apps that the next phase is not another discovery rule but making the tool adoptable by a stranger. This pulls the "Versioning and release", "Adoptable documentation", and "Security posture" rows of the Tier 3 checklist to the front. It changes no discovery, backend, runtime, or CLI behavior; it is metadata, documentation, and mechanics. The full mechanics live in docs/RELEASING.md; the decisions are recorded here first, per rule 12.

- License: Apache-2.0 (`LICENSE`, `NOTICE`). Permissive so an adopter can embed the starter and the Maven plugin in a proprietary build without friction, with an explicit patent grant that protects adopters and contributors of a tool that generates and executes code paths into a host system. Rationale in docs/RELEASING.md section 1.
- Version: 0.1.0 (the reactor moves off `-SNAPSHOT` for the release commit). Pre-1.0 discipline: patch is docs and metadata and non-behavioral fixes; minor is a new phase or rule or IR growth or any behavior change, and pre-1.0 minor bumps may break; 1.0.0 is the first stability commitment, not yet claimed. The plugin API is the compatibility boundary (Volume 10 section 6).
- Publishing target: Maven Central under the `io.github.tibyaan-org` groupId (verified through the `tibyaan` GitHub user account; the Java package root stays `com.tibyaan`), with GitHub Packages permitted only as an explicitly-labeled interim. The POM carries all Central-required metadata and a `release` profile (source and javadoc jars, GPG signing wired to external credentials, the central-publishing plugin), verifiable locally with `mvn -Prelease verify` and no keys. What cannot be done without a person (namespace verification, key ceremony, account creation) is enumerated as exact human steps in docs/RELEASING.md section 4, never faked.
- The adoption surface: the root README is rewritten for adopters (honest expectations, the refusals-are-the-product framing, a verified quickstart), a security posture is stated (docs/SECURITY.md), and CONTRIBUTING.md records the design-before-code and benchmark disciplines.

The two benchmark apps stay private and uncommitted (section 5); the ledgers are the published evidence, so a stranger can read the numbers without the source.

### 4.2 v1 implementation is COMPLETE (declared 2026-07-02)

Stated plainly: all structurally reachable coverage is built. The Tier 2 semantic phases (E1 to E5, plus E4's IR growth) are ratified, built, and benchmarked; the structural ceiling above them is evidence-proven from three directions and recorded (section 3, "The structural ceiling"); the deferred remainder is consolidated into the designed-and-waiting Attestation phase with a written evidence trigger. The tool is verified across two hostile real codebases with ZERO wrong bindings (27 capabilities, every one hand-verified), both backends (MCP and OpenAPI) generate all of them, the adoption path is written and verified against the example app, and release engineering is done with CI green from a clean checkout. (The 27 is the v1-complete milestone; pms-backend was re-baselined 2 to 5 on 2026-07-20, so the current two-app total is 30, section 3 and docs/experiments/second-app-trial.md section 9.)

The only steps remaining to a public v1 are the human release-checklist items already enumerated in docs/RELEASING.md section 4: the namespace verification (`io.github.tibyaan-org`, through the `tibyaan` GitHub user account), the Central account and token, the GPG key, the deploy, and the tag. None of them is a build.

The next action is PUBLISH AND GET EXTERNAL SIGNAL, not further building. Real usage decides what comes next: if a real adopter hits the filter or wrapper ceiling, that is the written trigger for the Attestation phase; if adoption surfaces defects or gaps in the structural core, those are fixes under the standing disciplines. Building more coverage ahead of that signal would be building past the evidence, which is the failure mode this roadmap exists to prevent.

Update (2026-07-13): both steps happened, and the first evidence-triggered phase is built. v1 0.1.0 is published to Maven Central (docs/RELEASING.md), and the first external signal, the private enterprise benchmark, arrived and reranked the post-v1 order (section 3, "Entity Identity"). The signal's top finding was not the filter ceiling but a total output-blocking failure, entity-name collision on a multi-module app, so Entity Identity was designed, ratified, and BUILT (2026-07-13): the tool now emits output for a real multi-module bounded-context application (BLOCKED to EMITTED), both public benchmarks byte-identical, mis-bound 0. The discovery-gap phase (rank 2) was then DESIGNED (2026-07-13, section 3, item 2; Volume 5, section 4.13), and its source census changed it: two shapes are general and got rules (inherited `@Id`, private-helper hop), one is not a gap (already handled), and the general rules discover zero on the benchmark, so the phase is ratified for its boundary and its build deferred (P1b precedent). Attestation is ranked third and, on the same census, is the honest home of most of the remaining user-facing coverage. This is the roadmap working as intended: real evidence, not internal consensus, set the order, and it was allowed to reshape a phase (a coverage phase that the evidence showed unlocks little) rather than force it through.

## 5. The standing measure: three benchmarks (pms-backend, erp-backend, and the private enterprise benchmark)

The trial targets become the project's standing benchmarks:

- The primary target is the local, read-only copy at `examples/pms-backend`: 43 entities across 14 domain modules, Long/Integer ids, view-returning reads (exercising rule 4.2). It is outside the Maven reactor and is not committed; docs/experiments/second-app-trial.md records its structure so results are interpretable without the source. RE-BASELINED 2026-07-20 (second-app-trial.md section 9): the adopter repaired the 27 placeholder-corrupted files (a copy artifact, not real unparseable code), so the standing baseline is now discovered 5 (getUnit, getLease, getStaff, getUser, getVendor), skipped-by-parse 0, mis-bound 0; the 2026-07-02 baseline of discovered 2 with 27 skipped stands as the record of the corrupted copy every prior phase measured.
- A second target was added 2026-07-02, `examples/erp-backend`: 25 entities, single module, UUID/String ids, entity-returning reads (exercising rule 4.1), with collection associations and an embeddable value type pms-backend lacks. Same read-only, out-of-reactor, uncommitted status; docs/experiments/third-app-trial.md records it. The generalization finding: the two apps discover through disjoint rules (pms via 4.2, erp via 4.1), so together they keep any single phase from being tuned to one codebase. Coverage is idiom-bound (erp baseline: 7 of 25 discovered, all 4.1), and discovery does not fully compound across apps.
- A third target was added 2026-07-12, after v1 publish: a real enterprise-scale adopter application, the private enterprise benchmark. It is PRIVATE and recorded by structural counts and shape categories only (docs/experiments/enterprise-benchmark.md, never named, never quoted): 169 modules, 111 entities, 158 services, parsed clean, mis-bound 0 confirmed adversarially. It is the largest and most realistic benchmark, and it is the one that reranked the post-v1 order (section 3, "Entity Identity"): it exposed the entity-name-collision output-blocker that the two smaller apps, single-context per name, could not. Its redaction is permanent, for this and every future session.
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
