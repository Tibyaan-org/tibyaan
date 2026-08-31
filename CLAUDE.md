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

---

## Current status (v1 published; post-v1 phases in progress)

`docs/STATUS.md` is the authoritative resume point; read it first. This section is a summary and must not drift from it.

v1 implementation is complete and PUBLISHED. The MVP (Spike, P0 to P3), three hardening steps (binding discovery, one-dependency adoption, build-time manifest generation), and every Tier 2 semantic phase (E1 parse resilience, E3 associations and enums and FLOAT, E2 DTO-aware read discovery, E5 partial-model integrity, E4 the unfiltered list-all read) are built and benchmarked. A second backend, OpenAPI, is built alongside MCP, proving one IR feeds more than one output. Version 0.1.0 was published to Maven Central on 2026-07-08 under groupId `io.github.tibyaan-org` (tag v0.1.0 at commit f01d390); the repository is back on 0.2.0-SNAPSHOT.

The external signal arrived on 2026-07-12 and reranked the post-v1 order: a real enterprise-scale adopter application, the private enterprise benchmark, permanently redacted and recorded by structural counts only (169 modules, 111 entities, 158 services; docs/experiments/enterprise-benchmark.md). Built since publish, in order:

- Entity Identity, rank 1 (2026-07-13; Volume 5, section 4.12). Qualified FQCN keying. This was the only total, output-blocking failure found: simple-name keying collapsed legitimately reused names across bounded contexts, so a real multi-module app emitted nothing. The private benchmark moved BLOCKED to EMITTED; both public benchmarks byte-identical.
- Discovery-gap rules, rank 2 (2026-07-13; section 4.13), general scope only. Inherited `@Id` and the private-helper hop got rules; the third shape got none because it is already handled. Measured: zero new capabilities discovered, by design. The build changed honesty, not coverage.
- Controller-Authority (2026-07-19; section 4.14), the project's first real-user-requested phase: controller discovery plus extraction of the declared `@PreAuthorize` authorities.
- Attestation AT-0 (2026-07-24) and AT-1 (2026-07-25), section 4.15 and following: the structural floor and envelope unwrap, then the attestation core and the PREDICATE category.
- First real measurement on pms (2026-08-01), docs only, no tool code changed. It produced the project's first defect found by its own measurement.
- Read-safety receiver awareness (2026-08-04; section 4.15.2.1), which fixed that defect: a mutating-named call is read-safe only if its receiver is provably a local, freshly constructed, allowlisted JDK container that never escapes to persistence.
- Enum inputs, step EI-R (2026-08-05; Volume 7, sections 9 and 9.1). The runtime binds arguments to the DECLARED parameter types, so an enum or date parameter no longer makes a capability uninvokable, and an illegal enum value is refused before invoking, with the legal values named.
- Read-Identity (2026-08-31; Volume 5, section 4.1.1), UNPLANNED, and it preempted EI-1 because it closed a MIS-BIND. Section 4.1, the entity-returning path, matched on signature alone, so the by-primary-key fact section 4.2 calls the safety-critical discriminator was never established there. On a fourth real application it had bound a read-by-id capability to a method that filters by a foreign key and returns the first hit. Both paths now share one by-primary-key proof; the rule can only remove candidates, never add one.

Health line, re-measured 2026-08-31 on the current trees: discovered 5 (pms) / 35 (erp) / 5 (as-backend), mis-bound 0, attestation-drift 0, mis-safe 0, attested 0. The pms and erp figures differ from the historical 5 / 25 because those applications evolved, not because the tool moved. The private enterprise benchmark was NOT re-measured by the Read-Identity build (no path to it in that session) and 2 of its 4 discovered reads are entity-returning, so its "4" is stale until someone re-runs it. `mvn clean verify` is green across 12 modules and both backends generate. Those four zeros are absolute invariants, not metrics: any of them above zero outranks every other priority, including the phase in hand. EI-1 adds a fifth, mis-constrained 0.

Read the "proven" numbers honestly. The two public benchmarks were written by the same author on the same private foundation libraries, so their coverage is idiom-bound and does not fully compound (docs/experiments/third-app-trial.md, section 7). The private enterprise benchmark is the one genuinely independent-scale target, and its redaction is permanent. Treat "proven on the benchmarks" as "proven on these apps," not "proven in general."

NEXT ACTION, and it is a build: EI-1, the second step of the enum-input phase (the core value constraint and rule 10, the provider's structural-provability rule, and the backend surfaces; Volume 5, section 4.16; Volume 4, section 3.9; Volume 6, sections 3.7 and 4.5). It is unblocked because the ratified sequencing rule, no surface renders a closed set until EI-R is green, is satisfied in the direction it was written. After it, AT-2 (SHAPE), the third and last of the three measured blockers and the one that actually moves pms. Nothing of AT-2, AT-3, or AT-4 is built; AT-3 needs its own ratification with stronger guards, and AT-4 is optional and separable. Known gaps and deferred work are in Volume 7, section 8; plugin discovery status is in Volume 10, section 2.

The one-phase-per-session rule remains in force.

---

## Hard rules (never violate these)

1. `tibyan-core` has zero external dependencies. Not Spring, not JavaParser, not an MCP library, not a template engine. If core needs one of those, a concept has leaked; move it out.
2. A Backend never knows the source and never calls a framework directly. It reads only the IR and invokes logic through `ExecutionAdapter`.
3. A Provider only produces IR. It never generates output and never depends on a backend or a runtime.
4. The Runtime hosts and binds. It contains no IR and no generation logic. It owns the `ExecutionAdapter` for its host. Known MVP runtime gaps and deferred work are listed in Volume 7, section 8.
5. Keep the IR tiny. It is entities plus read-only capabilities: read-by-id and the unfiltered list-all. Every addition so far was earned by a ratified, validated, golden-tested phase, and the ledger is deliberately short: FLOAT in the closed primitive set (E3), the `outputMultiple` boolean (E4), the qualified entity `key` (Entity Identity), and the `Attestation` component with core rules 8 and 9 and the reserved `attested_` prefix (AT-1). Declared authorities ride the existing `Annotation` hook (`requiredAuthorities`, `securityUnverifiable`) rather than a new IR type: the IR carries a compiled DECLARED contract, it does not model a permission system. One addition is designed and pending its build, the per-input value constraint and rule 10 (EI-1). Do NOT add workflows, events, policies, permissions, dependencies, or domain knowledge. Those are future (Volume 4, section 7); they stay off limits until a designed, ratified, golden-tested phase earns them on real evidence.
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
- The post-v1 sequence is the live plan and lives in Volume 13, section 3, ranked by the external signal. Built: Entity Identity, Discovery-gap, Controller-Authority, Attestation AT-0 and AT-1, read-safety receiver awareness, and enum inputs step EI-R. Pending: EI-1, then AT-2 (SHAPE). The Current status section above has the dates and the outcomes.
- Tier 3 is direction only. Do not build Tier 3 items, and do not write detailed plans for them; they are conditional on external evidence, not internal consensus.

The next build phase is EI-1, and after it AT-2. A phase is not started until its design is ratified (rule 12), and one phase is built per session. After each build, re-run the benchmarks and append the dated entry to the experiment docs; a phase forecasts its measurements before the build and checks them after it, and an unforecast movement is a regression finding.

Two disciplines from the spike era survive verbatim into every phase:

- Fail loud, never guess. If a rule does not decide a case unambiguously, the tool refuses with a located error; it never falls back to a convention or emits a plausible binding. Collect-and-continue (phase E1) relocates loudness, it does not soften it: every skipped file is named, and a partial result never masquerades as a complete one.
- If a phase does not work cleanly: stop. Do not work around it by faking a result or quietly changing the model. Report the problem and the options. Changing the design is a decision for the human, not a silent workaround.

The bias behind every refusal rule, stated once here so no phase re-litigates it: a false refusal is a coverage miss, a false pass is a safety breach, and the design prefers the miss. A genuinely safe case whose safety the tool cannot prove stays refused, and that is correct.

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
| The enum-input phase (EI-R built, EI-1 next) | Volume 5, section 4.16; Volume 7, section 9 |
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
