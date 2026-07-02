# CLAUDE.md

Agent brief for the Tibyan repository. Read this before writing any code. The full design is in `docs/` (Volumes 1 to 17). This file tells you how to execute that design: the order, the rules, and the boundaries. When this file and a volume disagree, this file wins for process; the volumes win for design detail.

---

## What this project is

Tibyan is an application modernization platform. It reads an existing legacy business system, builds a neutral model of what it can do (the Capability IR), and generates an AI-operable layer on top of it, starting with an MCP server, without rewriting or editing the original system.

It is NOT a code generator, a Spring generator, a CRUD generator, or an ERP framework. Do not build those.

The five abstractions: CapabilityProvider (input), Capability IR (model), Backend (output), ExecutionAdapter (how a capability runs), Runtime (where it is hosted). See Volume 3 and the README.

---

## Prime directive: evidence before elaboration

The Technical Spike (Phase -1) was the original prime directive; it ran on 2026-06-30 and succeeded (Volume 8; answers in Volume 16, section 3). The directive that replaces it: build only what evidence from a real application justifies, in the order the evidence ranks it. The current plan lives in Volume 13, in three tiers: Tier 1 is the completed record, Tier 2 is the evidence-based next phases, Tier 3 is direction only. Do not start Tier 3 work, and do not start a Tier 2 phase before the previous one meets its exit criterion.

The spike precedent stands: when a future step rests on an unknown that big, spike it before building on it.

---

## Current status (MVP complete, hardened, and benchmarked)

All MVP phases are complete on the main branch: -1 (Spike), P0 (Core and IR), P1 (Spring provider), P2 (MCP backend and runtime), and P3 (CLI and pipeline), plus three hardening steps: the binding is discovered from the real service method (Volume 5, section 4.1); a truly untouched app needs only the one dependency, with the MCP server auto-configured and the manifest discovered from the classpath (Volume 7, sections 4 and 8); and the manifest is generated at build time by the `tibyan-maven-plugin`, which runs the same pipeline as the CLI, so nothing is copied by hand (Volume 11). The MVP works end to end: `mvn clean verify` is green, and the flow (build generates the manifest onto the classpath, launch the app with no launch property, live MCP getProduct) is verified.

After the MVP, the unchanged tool was run against a second, large real app (pms-backend, 43 entities). It discovered 0 capabilities, mis-bound 0, and failed loudly on all 43, and the gaps were ranked by counted evidence (docs/experiments/second-app-trial.md). That app is now the standing benchmark (Volume 13, section 5): after each Tier 2 phase it is re-run, and discovered rising while mis-bound stays zero is the health metric. Mis-bound greater than zero becomes the top priority over everything.

The full resume point, with the exact commands that reproduce the MVP, is in `docs/STATUS.md`. Known gaps and deferred work are in Volume 7, section 8; plugin discovery status is in Volume 10, section 2.

The one-phase-per-session rule remains in force for any future phase.

---

## Hard rules (never violate these)

1. `tibyan-core` has zero external dependencies. Not Spring, not JavaParser, not an MCP library, not a template engine. If core needs one of those, a concept has leaked; move it out.
2. A Backend never knows the source and never calls a framework directly. It reads only the IR and invokes logic through `ExecutionAdapter`.
3. A Provider only produces IR. It never generates output and never depends on a backend or a runtime.
4. The Runtime hosts and binds. It contains no IR and no generation logic. It owns the `ExecutionAdapter` for its host. Known MVP runtime gaps and deferred work are listed in Volume 7, section 8.
5. Keep the IR tiny. MVP IR is entities plus read-only capabilities only. Do NOT add workflows, events, policies, permissions, dependencies, or domain knowledge. Those are future (Volume 4, section 7); they are off limits for the MVP.
6. One plugin per module. A provider, a backend, or a runtime is its own module that depends only on `tibyan-plugin-api` and `tibyan-core`. Never bundle two plugins in one module. Never let one plugin depend on another.
7. Dependencies point inward, toward the core, never sideways between plugins. See Volume 12, section 5 for the allowed and forbidden edges.
8. Package root is `com.tibyaan`.
9. No em dashes anywhere: code comments, docs, commit messages, output. Use commas or colons.
10. Generation is deterministic and covered by golden-file tests. Same IR in, same files out.
11. Branching: trunk-based. All phase work commits to the main branch. No per-phase branches.
12. Ratification before build. For any design-then-build phase, the ratification records (the frozen design in the volumes) are committed before implementation begins, as E3 and E5 did. Committing the frozen design first guarantees the build implements the ratified rule, rather than the rule drifting to fit the build.
13. Flag mid-build diagnostic amendments. A diagnostic-only improvement made during a build session, one that changes no matching outcome and only the wording or precision of a message, is permitted, but it must be flagged as an amendment in that session's summary, so ratified text and built behavior never silently diverge.

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

The build order lives in Volume 13, the roadmap, in three tiers. Do not re-derive it from this file.

- Tier 1 is complete and is a record, not a plan: the spike, P0 to P3, the three hardening steps, the second-app trial, and phase E1 (parse resilience).
- Tier 2 is the active plan: evidence-based phases E1 (parse resilience and scan scope, done), E2 (DTO-aware read discovery, design in Volume 5 first, then build), E3 (associations), E4 (list, filter, and multi-argument reads, the first phase that grows the IR). Each has a goal, evidence, and an exit criterion in Volume 13, section 3. Do them in order; re-run the pms-backend benchmark after each and append the dated entry to docs/experiments/second-app-trial.md.
- Tier 3 is direction only. Do not build Tier 3 items, and do not write detailed plans for them; they are conditional on Tier 2 proving the semantic core on real apps.

The next phase is E2, and its rule must be designed into Volume 5, section 4.2 before any code is written, exactly as the binding rule was. One phase per session; a phase is not started until the previous phase meets its exit criterion.

Two disciplines from the spike era survive verbatim into every phase:

- Fail loud, never guess. If a rule does not decide a case unambiguously, the tool refuses with a located error; it never falls back to a convention or emits a plausible binding. Collect-and-continue (phase E1) relocates loudness, it does not soften it: every skipped file is named, and a partial result never masquerades as a complete one.
- If a phase does not work cleanly: stop. Do not work around it by faking a result or quietly changing the model. Report the problem and the options. Changing the design is a decision for the human, not a silent workaround.

---

## Scope discipline (do not over-build)

The biggest failure mode is building the elegant full platform instead of the smallest working slice. Resist it.

Do not, unless the current phase's exit criterion requires it:
- add IR elements beyond entities and read-only capabilities (phase E4 is the first sanctioned growth, and only through the Volume 4, section 7 discipline: designed, validated, golden-tested),
- implement extractors beyond the enabled Entity and Service extractors (repository, controller, and annotation stay empty placeholders),
- add backends other than MCP,
- add runtimes other than Spring,
- build the plugin marketplace, governance, workflows, or events,
- generalize an abstraction before there is a second case that needs it.

Tier 2's detail is not license to build ahead: a phase's exit criterion bounds its scope, and the benchmark, not ambition, decides what is next. When unsure whether something is in scope, it is not. Ask the human.

---

## Where to read in `docs/`

| You are working on | Open |
|---|---|
| What to build next, and why | Volume 13 (the tiers), docs/STATUS.md |
| The benchmark and its evidence | docs/experiments/second-app-trial.md |
| The IR | Volume 4 |
| A provider | Volume 5 |
| A backend | Volume 6 |
| The runtime | Volume 7 |
| The pipeline | Volume 9 |
| Module layout and dependency rules | Volume 12 |
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
