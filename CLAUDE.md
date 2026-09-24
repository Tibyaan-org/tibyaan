# CLAUDE.md

Agent brief for the Tibyan repository. Read this before writing any code. The full design is in `docs/` (Volumes 1 to 17). This file tells you how to execute that design: the order, the rules, and the boundaries. When this file and a volume disagree, this file wins for process; the volumes win for design detail.

---

## What this project is

Tibyan is an application modernization platform. It reads an existing legacy business system, builds a neutral model of what it can do (the Capability IR), and generates an AI-operable layer on top of it, starting with an MCP server, without rewriting or editing the original system.

It is NOT a code generator, a Spring generator, a CRUD generator, or an ERP framework. Do not build those.

The five abstractions: CapabilityProvider (input), Capability IR (model), Backend (output), ExecutionAdapter (how a capability runs), Runtime (where it is hosted). See Volume 3 and the README.

---

## Prime directive: evidence before elaboration

The Technical Spike (Phase -1) was the original prime directive; it ran on 2026-06-30 and succeeded (Volume 8; answers in Volume 16, section 3). The directive that replaces it: build only what evidence from a real application justifies, in the order the evidence ranks it. The plan lives in Volume 13. Tiers 1 and 2 are both completed records now; the live plan is the post-v1, evidence-ranked sequence in Volume 13, section 3, whose order was set by a real adopter application rather than by internal consensus. Tier 3 is direction only: do not start Tier 3 work, and do not start a phase before the previous one meets its exit criterion.

The spike precedent stands: when a future step rests on an unknown that big, spike it before building on it.

v2 DIRECTION (maintainer decision 2026-09-23; Volume 13, section 3, "v2 direction"). The product philosophy changes, the engine does not: draft first (every public service method is a candidate tool, reads and writes), prove what can be proved (VERIFIED and ATTESTED tiers keep the zero invariants), label the rest (READ, WRITE, UNKNOWN by read-safety), gate writes (per-name allowlist, default off), batch review in a file. Spike DL-0 (draft and label) and phase DL-1 (the reviewed tier and the write gate) are BUILT, 2026-09-23. The hard rules below were amended by the DL-1 ratification (Volume 5 section 4.17.9): rule 5 names three tiers, the fail-loud discipline governs proofs and labels, and the refusal bias is stated for proofs.

---

## Current status (v1 published; post-v1 phases in progress)

`docs/STATUS.md` is the authoritative resume point; read it first. This section is a summary and must not drift from it.

v1 implementation is complete and PUBLISHED. The MVP (Spike, P0 to P3), three hardening steps (binding discovery, one-dependency adoption, build-time manifest generation), and every Tier 2 semantic phase (E1 parse resilience, E3 associations and enums and FLOAT, E2 DTO-aware read discovery, E5 partial-model integrity, E4 the unfiltered list-all read) are built and benchmarked. A second backend, OpenAPI, is built alongside MCP, proving one IR feeds more than one output. Three versions are published to Maven Central under groupId `io.github.tibyaan-org`: 0.1.0 on 2026-07-08 (tag v0.1.0 at commit f01d390), 0.2.0 on 2026-07-17 (tag v0.2.0 at commit ff22554, carrying Entity Identity and the discovery-gap rules), and 0.3.0 on 2026-09-03 (tag v0.3.0 at commit 00cd69b), which SHIPPED the whole attestation phase, enum inputs, Controller-Authority, read-safety receiver awareness and Read-Identity. The tree is now on 0.4.0-SNAPSHOT. Per-release contents are in CHANGELOG.md.

The external signal arrived on 2026-07-12 and reranked the post-v1 order: a real enterprise-scale adopter application, the private enterprise benchmark, permanently redacted and recorded by structural counts only (169 modules, 111 entities, 158 services; docs/experiments/enterprise-benchmark.md). Built since publish, in order:

- Entity Identity, rank 1 (2026-07-13; Volume 5, section 4.12). Qualified FQCN keying. This was the only total, output-blocking failure found: simple-name keying collapsed legitimately reused names across bounded contexts, so a real multi-module app emitted nothing. The private benchmark moved BLOCKED to EMITTED; both public benchmarks byte-identical.
- Discovery-gap rules, rank 2 (2026-07-13; section 4.13), general scope only. Inherited `@Id` and the private-helper hop got rules; the third shape got none because it is already handled. Measured: zero new capabilities discovered, by design. The build changed honesty, not coverage.
- Controller-Authority (2026-07-19; section 4.14), the project's first real-user-requested phase: controller discovery plus extraction of the declared `@PreAuthorize` authorities.
- Attestation AT-0 (2026-07-24) and AT-1 (2026-07-25), section 4.15 and following: the structural floor and envelope unwrap, then the attestation core and the PREDICATE category.
- First real measurement on pms (2026-08-01), docs only, no tool code changed. It produced the project's first defect found by its own measurement.
- Read-safety receiver awareness (2026-08-04; section 4.15.2.1), which fixed that defect: a mutating-named call is read-safe only if its receiver is provably a local, freshly constructed, allowlisted JDK container that never escapes to persistence.
- Enum inputs, step EI-R (2026-08-05; Volume 7, sections 9 and 9.1). The runtime binds arguments to the DECLARED parameter types, so an enum or date parameter no longer makes a capability uninvokable, and an illegal enum value is refused before invoking, with the legal values named.
- Read-Identity (2026-08-31; Volume 5, section 4.1.1), UNPLANNED, and it preempted EI-1 because it closed a MIS-BIND. Section 4.1, the entity-returning path, matched on signature alone, so the by-primary-key fact section 4.2 calls the safety-critical discriminator was never established there. On a fourth real application it had bound a read-by-id capability to a method that filters by a foreign key and returns the first hit. Both paths now share one by-primary-key proof; the rule can only remove candidates, never add one.
- Attestation AT-2, the SHAPE category (2026-09-01; Volume 5, section 4.15.2 category B, build record at 4.15.2.2). Taken ahead of EI-1 at the maintainer's direction; its only hard dependency was AT-1, built since 2026-07-25. A human can now assert what a read RETURNS for the reads structure cannot tie, while the tool still proves the local-tie precondition, the subject entity's existence, non-contradiction, the by-primary-key load wherever an identifier is claimed, and read-safety. The compound category `both` is deliberately NOT built, so every compound target stays refused. All three benchmarks byte-identical: the phase is provably additive.
- Enum inputs, step EI-1 (2026-09-01; Volume 5, section 4.16). The per-input closed value set, proven from a resolved in-tree enum declaration, with core rule 10 and both backend surfaces.
- Attestation AT-3, the BOUNDARY category, member C2 (2026-09-01; Volume 5, section 4.15.2.3). DEFERRED BY NAME by the original design and given its own ratification session here, committed before the build. A human can now attest a remote delegation, including READ-ONLY-NESS itself, which is the one property the tool cannot check for that shape. Five guards: a securityOwner named separately from assertedBy, an explicit affirmation with no default, a separate count, the class in the name, and exposure opt-in default off. An attested authority is NEVER a compiled authority. Core gains rule 11. Member C1, the security contract, is refused by name: its targeting model is its own decision. All three benchmarks byte-identical.
- Attestation AT-3 member C1, the security contract (2026-09-01; section 4.15.2.3, "C1 RESOLVED"). A C1 assertion is carried BY a boundary entry, on the read it is already attesting, never as an entry of its own against an emitted capability: that dissolves the binding-uniqueness collision and keeps every human assertion off verified capabilities. C1 over a verified capability stays refused, because that case has a source fix. Core rule 11 gains the contradiction guard: never both a compiled and an asserted contract on one capability.
- The compound category `both` (2026-09-01; Volume 5, section 4.15.3). A two-hole target, a filtered read whose element type structure cannot tie, is closed by ONE entry with one minted name, because condition 1 forbids two entries against one declaration. Its semantics were ratified with the phase in 2026-07-21 and left unbuilt for scope only. The category must match the holes: enforced on the predicate half in both directions, and deliberately not on the shape half, because the E6a transparency tie makes "the element type is unresolvable" undecidable and non-contradiction guards the case instead.
- Attestation AT-4, the draft lifecycle (2026-09-01; Volume 5, section 4.15.5, build record at 4.15.5.1). A draft is a third thing, neither attested nor refused: queued for review, counted apart, and shipping nothing. Confirmation is a per-entry named human act, with no confirm-all anywhere in the CLI or the API beneath it. The assist that would produce drafts is NOT built and its open questions are named: it is a stack decision, not a phase decision. Any drafter works today, human or otherwise, so long as the entry carries origin ai-draft.
- Enum inputs, step EI-1 (2026-09-01; Volume 5, section 4.16.4, build record at 4.16.10). The fourth deliberate IR growth: a capability may carry a per-input closed value set, READ from a resolved in-tree enum declaration, rendered by both backends and enforced by the runtime EI-R built earlier. A bare enum carries its constant names, a value-mapped enum carries its `` literals, and anything the tool cannot read refuses the capability rather than shipping an unconstrained input. Core gains validation rule 10. Discovered is byte-identical BY CONSTRUCTION, since no discovered capability can have an enum input.

Health line, re-measured 2026-08-31 on the current trees: discovered 5 (pms) / 35 (erp) / 5 (as-backend), mis-bound 0, attestation-drift 0, mis-safe 0, attested 0. The pms and erp figures differ from the historical 5 / 25 because those applications evolved, not because the tool moved. The private enterprise benchmark was NOT re-measured by the Read-Identity build (no path to it in that session) and 2 of its 4 discovered reads are entity-returning, so its "4" is stale until someone re-runs it. `mvn clean verify` is green across 12 modules and both backends generate. Those four zeros are absolute invariants, not metrics: any of them above zero outranks every other priority, including the phase in hand. EI-1 added the fifth, mis-constrained 0, reported wherever a value constraint exists.

Read the "proven" numbers honestly. The two public benchmarks were written by the same author on the same private foundation libraries, so their coverage is idiom-bound and does not fully compound (docs/experiments/third-app-trial.md, section 7). The private enterprise benchmark is the one genuinely independent-scale target, and its redaction is permanent. Treat "proven on the benchmarks" as "proven on these apps," not "proven in general."

NEXT ACTION (2026-09-24, superseding the rest of this paragraph): THE FIRST REVIEWED CAPABILITY RAN END TO END on as-backend through a real MCP client (as-backend-trial.md section 10.7) and found F7, a runtime mis-bind risk on every tier: the manifest binding is a simple class name and `SpringExecutionAdapter.resolveBean` resolves by simple name, so a reused simple name (four `LookupServiceImpl` classes in that reactor) can invoke a class the reviewer never read. NEXT: ratify, amend or refuse the F7 design, a qualified binding verified at resolution; under the mis-bind rule it outranks DL-1.1-era hygiene, DL-2 and everything below. Earlier that day: NEXT ACTION (2026-09-23, superseding the line below it the same day): SPIKE DL-0 IS BUILT (commit fdd5ec3; as-backend-trial.md section 10): `tibyan draft` exists, the as-backend census is 990 candidates (VERIFIED 5, ATTESTED 15, READ 401, WRITE 204, UNKNOWN 365), the automatic Q2 checks are green, Q3 is deferred to DL-1 by name. DL-1, REVIEWED DRAFTS AND THE WRITE GATE, IS BUILT 2026-09-23 (Volume 5, section 4.17, build record at 4.17.13), and with it Volume 7 section 10 under its D5 amendment. The reviewed tier, the kinds WRITE and ACTION behind `tibyan.writes.allow`, and the one security list `tibyan.security.exposeUnverifiable` exist; all three benchmarks are byte-identical. THE READING SAMPLE RAN (as-backend-trial.md section 10.2): 3 of 30 READ labels hide a write, all through calls into another bean. DL-1.1, the bean-call closure of the READ label, IS BUILT (Volume 5 section 4.17.14, record 4.17.15): the three false READs are caught, 22 of 27 audited pure reads keep READ. DL-1.2 IS BUILT 2026-09-24 (Volume 5 sections 4.17.16 and 4.17.17): all 27 audited pure reads are READ, C6 is 3, and 55 same-arity overloads are the next candidate by count. DL-1.3 IS BUILT 2026-09-24 (Volume 5 sections 4.17.18 and 4.17.19): 42 of 55 overload cases decided, UNKNOWN 240 of 990. No candidate from the census outranks real use. THE FIRST REAL APPROVALS HAPPENED 2026-09-24 (as-backend-trial.md section 10.6): reviewed 9, refused 0, review-drift 0, seven of the nine security-unverifiable. NEXT: run the adopter's services with the manifest and the seven names listed in `tibyan.security.exposeUnverifiable`, and call a reviewed tool through a real MCP client; the first real approvals on as-backend can proceed in parallel on the 11 audit-confirmed entries. DL-2 (collection inputs) and candidate hygiene wait. The maintainer's discussion happened on 2026-09-23 and produced the v2 direction. Before that: BUILD Volume 7, section 10, per-capability attested exposure, RATIFIED 2026-09-23 as written (record at section 10.10), in its own session; it stands as the first member of the v2 write gate and is built after DL-0. Runtime only: one property, `tibyan.attested.exposeReviewed`, a list of exact capability names, conditions X1 to X5, fixtures RX-1 to RX-10, a forecast gain of zero on as-backend today and byte-identical benchmarks by construction; the five decisions of 10.9 stand at their recommendations (fail startup on a bad entry, name only, a bare list plus one SECURITY.md sentence, the global flag kept with a WARN, the property name). It is candidate 1 of the five evidence-ranked candidates from the first real attestation run on as-backend (Volume 13, section 3; the other four are not ratified). Before that (2026-09-20): the design was committed pending ratification, the owed as-backend re-scan ran the same day (discovered 5, attested 15, mis-bound 0, attestation-drift 0, mis-constrained 0), and a census of that app's 86 multi-read deferrals recorded a list-plus-count discovery rule as a NON-CANDIDATE with an honest ceiling of 2 of 86 (docs/experiments/as-backend-trial.md sections 9.8 and 9.9). Before that: there is no pending build from the ratified sequence. EI-1 (2026-09-01) and AT-2 (2026-09-01) are both built, EI-1 second, and the ratified sequencing rule, that no surface renders a closed set until EI-R is green, was satisfied in the direction it was written. AT-2 (SHAPE), the third and last of the three measured blockers, is BUILT. EVERY RATIFIED ATTESTATION CATEGORY IS NOW BUILT: predicate (AT-1), shape (AT-2), the compound `both`, and boundary (AT-3, both members, C2 the remote delegation and C1 the security contract). AT-4 splits: its DRAFT LIFECYCLE is built (a draft is a third thing, queued and never shipped; confirmation is a per-entry named human act with no confirm-all), and THE ASSIST ITSELF is deferred with its open questions named in section 4.15.5.1, because adding a model call is a Volume 16 stack decision and the first network call in a tool whose contract is deterministic offline analysis. Two cases stay deliberately refused with their reasons recorded: C1 over a VERIFIED capability (that case has a source fix, not an attestation fix) and any attestation of a write or action (the standing policy gate). EI-1 adds the fifth invariant, mis-constrained 0. Known gaps and deferred work are in Volume 7, section 8; plugin discovery status is in Volume 10, section 2.

The one-phase-per-session rule remains in force.

---

## Hard rules (never violate these)

1. `tibyan-core` has zero external dependencies. Not Spring, not JavaParser, not an MCP library, not a template engine. If core needs one of those, a concept has leaked; move it out.
2. A Backend never knows the source and never calls a framework directly. It reads only the IR and invokes logic through `ExecutionAdapter`.
3. A Provider only produces IR. It never generates output and never depends on a backend or a runtime.
4. The Runtime hosts and binds. It contains no IR and no generation logic. It owns the `ExecutionAdapter` for its host. Known MVP runtime gaps and deferred work are listed in Volume 7, section 8.
5. Keep the IR tiny. It is entities plus capabilities in three tiers: VERIFIED (read-by-id and the unfiltered list-all, proven by the discovery rules), ATTESTED (a named human's claim carried faithfully), and, from the DL-1 build on (Volume 5 section 4.17, RATIFIED 2026-09-23), REVIEWED capabilities of kind READ, WRITE or ACTION carrying a `Review` component under core rules 12 and 13. WRITE and ACTION exist only in the reviewed tier and never register without an exact-name allowlist. Every addition so far was earned by a ratified, validated, golden-tested phase, and the ledger is deliberately short: FLOAT in the closed primitive set (E3), the `outputMultiple` boolean (E4), the qualified entity `key` (Entity Identity), and the `Attestation` component with core rules 8 and 9 and the reserved `attested_` prefix (AT-1). Declared authorities ride the existing `Annotation` hook (`requiredAuthorities`, `securityUnverifiable`) rather than a new IR type: the IR carries a compiled DECLARED contract, it does not model a permission system. The fourth and most recent is the per-input value constraint and rule 10 (EI-1, built 2026-09-01). Do NOT add workflows, events, policies, permissions, dependencies, or domain knowledge. Those are future (Volume 4, section 7); they stay off limits until a designed, ratified, golden-tested phase earns them on real evidence.
6. One plugin per module. A provider, a backend, or a runtime is its own module that depends only on `tibyan-plugin-api` and `tibyan-core`. Never bundle two plugins in one module. Never let one plugin depend on another.
7. Dependencies point inward, toward the core, never sideways between plugins. See Volume 12, section 5 for the allowed and forbidden edges.
8. Package root is `com.tibyaan`.
9. No em dashes anywhere: code comments, docs, commit messages, output. Use commas or colons.
10. Generation is deterministic and covered by golden-file tests. Same IR in, same files out.
11. Branching: trunk-based. All phase work commits to the main branch. No per-phase branches.
12. Ratification before build. For any design-then-build phase, the ratification records (the frozen design in the volumes) are committed before implementation begins, as E3 and E5 did. Committing the frozen design first guarantees the build implements the ratified rule, rather than the rule drifting to fit the build.
13. Flag mid-build diagnostic amendments. A diagnostic-only improvement made during a build session, one that changes no matching outcome and only the wording or precision of a message, is permitted, but it must be flagged as an amendment in that session's summary, so ratified text and built behavior never silently diverge.
14. Verify the build against the repository, never against the report. No session's build report is accepted as true until it is verified against the repository. Every build session's report must END with its spine: the session's commit hashes with one-line descriptions, and the `mvn clean verify` reactor result (the verbatim tail). Before any subsequent session builds on a reported build, the maintainer independently runs the reactor and confirms the reported commits exist. A build is done when the repository proves it, not when a turn says so. Rationale, earned by incident: a session reported the AT-1 phase complete when nothing of it existed in the repository (no component, no rules, no intake, no code, no fixtures); the false report was caught only by an evidence-only repository verification. The conversation is not the source of truth about the tree; the tree is.

---

## Tech stack (fixed for the MVP)

- Java, LTS version (17 or 21).
- Spring Boot 3.x (for the runtime and for generated and host apps).
- Maven multi-module. Package root `com.tibyaan`.
- Source analysis: JavaParser (AST). No reflection-based or bytecode analysis in the MVP.
- CLI: picocli.
- Tests: JUnit 5, AssertJ, golden-file tests, one end-to-end test.
- MCP server library: decided by the spike. Spring AI MCP server support (`spring-ai-starter-mcp-server-webmvc`, Spring AI BOM 1.0.9, SSE transport), with the official MCP Java SDK underneath. Pinned versions and the full answers are in Volume 16, sections 3.1 and 3.2.

Do not add a dependency without a reason tied to the current phase. Never add a dependency to `tibyan-core`.

---

## Dependencies and the author's private libraries

The author has a set of private Spring foundation libraries (for example: common, cache, logger, json, security, session, service-context, rest, mq, sql-db-adapter, swagger, workflow-engine, product-adapter, user-adapter, id-counter, unit-test). Do NOT depend on any of them.

Two reasons. First, Tibyan must be buildable by any contributor using only standard public libraries; a repo that only its author can compile is not really open source. Second, those libraries are for building business systems, and Tibyan is a tool that reads business systems, not a business system itself. They belong in the systems Tibyan modernizes, which are its input, not in Tibyan.

Build only on standard, public libraries: Spring Boot, JavaParser, Jackson (in non-core modules only), the SLF4J logging facade, picocli, JUnit 5, AssertJ. The core and the plugin API stay zero-dependency regardless.

The author's own Spring systems are still useful here, just on the other side of the line: they are an ideal first spike target and a realistic first user. Point the spike at one of them. Do not import them.

## Build order (one phase per session)

The build order lives in Volume 13, the roadmap. Do not re-derive it from this file.

- Tier 1 is complete and is a record, not a plan: the spike, P0 to P3, the three hardening steps, and the second-app trial.
- Tier 2 is complete and is a record: E1 (parse resilience), E3 (associations, enums, FLOAT), E2 (DTO-aware read discovery), E5 (partial-model integrity), and E4 (the unfiltered list-all read). Each phase's goal, evidence, exit criterion, and outcome is in Volume 13, section 3.
- The post-v1 sequence is the live plan and lives in Volume 13, section 3, ranked by the external signal. Built: Entity Identity, Discovery-gap, Controller-Authority, Attestation AT-0 to AT-4 (the AT-4 assist itself deferred), read-safety receiver awareness, Read-Identity, and enum inputs steps EI-R and EI-1. Built 2026-09-23: spike DL-0 and phase DL-1 (the reviewed tier and the write gate, which also built Volume 7 section 10). The Current status section above has the dates and the outcomes.
- Tier 3 is direction only. Do not build Tier 3 items, and do not write detailed plans for them; they are conditional on external evidence, not internal consensus.

Spike DL-0 and phase DL-1 are built (2026-09-23), DL-1 including Volume 7 section 10. There is no ratified build pending; the next step is evidence from real approvals on as-backend, and DL-2 (collection inputs) is designed only when that evidence ranks it. A phase is not started until its design is ratified (rule 12), a spike is started on its recorded questions, and one phase is built per session. After each build, re-run the benchmarks and append the dated entry to the experiment docs; a phase forecasts its measurements before the build and checks them after it, and an unforecast movement is a regression finding.

Two disciplines from the spike era survive verbatim into every phase:

- Fail loud, never guess. If a rule does not decide a case unambiguously, the tool refuses with a located error; it never falls back to a convention or emits a plausible binding. Collect-and-continue (phase E1) relocates loudness, it does not soften it: every skipped file is named, and a partial result never masquerades as a complete one. SCOPED BY DL-1 (ratified 2026-09-23): this governs PROOFS and LABELS. The tool never upgrades a label to a proof, never authors a description, and refuses every intake condition loudly. It no longer governs EMISSION: an unprovable method is drafted with its label, not refused, and ships only when a named human approves it.
- If a phase does not work cleanly: stop. Do not work around it by faking a result or quietly changing the model. Report the problem and the options. Changing the design is a decision for the human, not a silent workaround.

The bias behind every refusal rule, stated once here so no phase re-litigates it: for a PROOF, a false refusal is a coverage miss, a false pass is a safety breach, and the design prefers the miss. A genuinely safe case whose safety the tool cannot prove is never called proven, and that is correct. From DL-1 on (ratified 2026-09-23) such a case is drafted and labeled instead of refused: a reviewed capability is neither a proof nor a pass, it is a named human's decision carried faithfully, and the tool's guarantee for it is carriage, fingerprint, label drift and the write gate, never truth. "Zero wrong bindings" and the four zero invariants are claims about the verified and attested tiers; `review-drift 0` is the reviewed tier's.

---

## Scope discipline (do not over-build)

The biggest failure mode is building the elegant full platform instead of the smallest working slice. Resist it.

Do not, unless a ratified phase's exit criterion requires it:
- add IR elements beyond what rule 5 lists (the growth ledger is there, and it is short on purpose; anything new goes through the Volume 4, section 7 discipline: designed, validated, golden-tested),
- implement extractors beyond the enabled Entity, Service, and Controller extractors (repository and annotation stay empty placeholders),
- add backends beyond the built MCP and OpenAPI backends (OpenAPI was the sanctioned second-backend proof of one-IR-many-outputs; a third backend needs its own evidence),
- add runtimes other than Spring,
- build the plugin marketplace, governance, workflows, or events,
- generalize an abstraction before there is a second case that needs it.

Roadmap detail is not license to build ahead. The post-v1 order was set by real adopter evidence, and that is the only thing that promotes a phase: not internal ranking, not ambition, and not the fact that a design is already written and waiting. When unsure whether something is in scope, it is not. Ask the human.

---

## Where to read in `docs/`

| You are working on | Open |
|---|---|
| Current state and the next action | docs/STATUS.md, then Volume 13, section 3 |
| The benchmarks and their evidence | docs/experiments/second-app-trial.md (pms), docs/experiments/third-app-trial.md (erp), docs/experiments/enterprise-benchmark.md (the private enterprise benchmark, redacted) |
| The IR | Volume 4 |
| A provider | Volume 5 |
| A backend | Volume 6 |
| The runtime | Volume 7 |
| The pipeline | Volume 9 |
| Module layout and dependency rules | Volume 12 |
| Attestation and its sub-phases | Volume 5, section 4.15 and following |
| The enum-input phase (EI-R and EI-1 built) | Volume 5, section 4.16; Volume 7, section 9 |
| The reviewed tier, drafts and the write gate (DL-0 and DL-1, built) | Volume 5, section 4.17 (build record 4.17.13); Volume 7, sections 10 and 11 |
| Why a decision is the way it is | Volume 14 (ADRs) |
| The spike and the completed build, as records | Volume 8, Volume 16, Volume 17 |

---

## How to work

- One phase per session. Keep changes scoped to the current phase.
- After changes, run `mvn test`. Generation changes must keep golden-file tests passing or update them deliberately with a visible diff.
- Never add a dependency to `tibyan-core`.
- Prefer plain Java records and small interfaces over frameworks in the core and plugin-api.
- Code sketches in the volumes are intent, not final code. Make them concrete, but do not change their shape or the rules above.
- If a volume's design turns out to be wrong once code runs (for example, the MCP library does not behave as assumed), surface it and propose the fix; do not silently diverge.
