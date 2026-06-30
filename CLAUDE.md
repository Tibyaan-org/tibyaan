# CLAUDE.md

Agent brief for the Tibyan repository. Read this before writing any code. The full design is in `docs/` (Volumes 1 to 17). This file tells you how to execute that design: the order, the rules, and the boundaries. When this file and a volume disagree, this file wins for process; the volumes win for design detail.

---

## What this project is

Tibyan is an application modernization platform. It reads an existing legacy business system, builds a neutral model of what it can do (the Capability IR), and generates an AI-operable layer on top of it, starting with an MCP server, without rewriting or editing the original system.

It is NOT a code generator, a Spring generator, a CRUD generator, or an ERP framework. Do not build those.

The five abstractions: CapabilityProvider (input), Capability IR (model), Backend (output), ExecutionAdapter (how a capability runs), Runtime (where it is hosted). See Volume 3 and the README.

---

## Prime directive: build the spike first

Do NOT build the platform yet. Build the Technical Spike (Phase -1) first and stop.

The spike exists to prove the only real unknown: can a generated MCP layer run inside a real Spring application and call a real bean, by adding one dependency, so an AI agent gets a real result? Everything in the architecture depends on this being true. If it is not true, the design changes. So prove it before building anything on top of it.

Read Volume 8 (Technical Spike) and Volume 16 (Technology Stack) before starting.

---

## Current status (MVP complete)

All MVP phases are complete on the main branch: -1 (Spike), P0 (Core and IR), P1 (Spring provider), P2 (MCP backend and runtime), and P3 (CLI and pipeline), plus two hardening steps: the binding is discovered from the real service method (Volume 5, section 4.1), and a truly untouched app needs only the one dependency, with the manifest shipped on the classpath and the MCP server auto-configured (Volume 7, sections 4 and 8). The MVP works end to end: `mvn clean verify` is green, and the terminal flow (tibyan scan, tibyan generate, launch the app carrying the classpath manifest with no launch property, live MCP getProduct) is verified.

The full resume point, with the exact commands that reproduce the MVP, is in `docs/STATUS.md`. Known gaps and deferred work are in Volume 7, section 8; plugin discovery status is in Volume 10, section 2.

The spike-first directive above and the one-phase-per-session rule remain in force for any future phase.

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

---

## Tech stack (fixed for the MVP)

- Java, LTS version (17 or 21).
- Spring Boot 3.x (for the runtime and for generated and host apps).
- Maven multi-module. Package root `com.tibyaan`.
- Source analysis: JavaParser (AST). No reflection-based or bytecode analysis in the MVP.
- CLI: picocli.
- Tests: JUnit 5, AssertJ, golden-file tests, one end-to-end test.
- MCP server library: NOT yet decided. The spike chooses between Spring AI MCP server support (preferred, because it does much of the starter and auto-configuration work) and the official MCP Java SDK. Pin the exact version during the spike and record it in Volume 16.

Do not add a dependency without a reason tied to the current phase. Never add a dependency to `tibyan-core`.

---

## Dependencies and the author's private libraries

The author has a set of private Spring foundation libraries (for example: common, cache, logger, json, security, session, service-context, rest, mq, sql-db-adapter, swagger, workflow-engine, product-adapter, user-adapter, id-counter, unit-test). Do NOT depend on any of them.

Two reasons. First, Tibyan must be buildable by any contributor using only standard public libraries; a repo that only its author can compile is not really open source. Second, those libraries are for building business systems, and Tibyan is a tool that reads business systems, not a business system itself. They belong in the systems Tibyan modernizes, which are its input, not in Tibyan.

Build only on standard, public libraries: Spring Boot, JavaParser, Jackson (in non-core modules only), the SLF4J logging facade, picocli, JUnit 5, AssertJ. The core and the plugin API stay zero-dependency regardless.

The author's own Spring systems are still useful here, just on the other side of the line: they are an ideal first spike target and a realistic first user. Point the spike at one of them. Do not import them.

## Build order (one phase per session)

Do these in order. Do not start a phase until the previous phase meets its definition of done. Volume 17 has the step detail and code sketches.

| Phase | Goal | Definition of done |
|---|---|---|
| -1 Spike | Prove the runtime binding | An agent calls `getProduct(5)` on an unmodified Spring app and gets a real result from the real database |
| P0 Core + IR | The IR stands alone | A hand-built IR validates and round-trips to JSON; `tibyan-core` has zero forbidden deps |
| P1 Spring provider | Build IR from real source | Scanning the example app yields a valid IR with `Product` and `getProduct` |
| P2 MCP backend + runtime | Execute against the real system | The MVP acceptance criteria in Volume 2, section 5 pass against a real, unmodified Spring app |
| P3 CLI | Make the flow usable | A developer reproduces scan, validate, and generate from the terminal, and launching the app with the generated manifest reproduces the end-to-end MCP path with no hand-copied file |

---

## Task 1 (do this now): the spike

Build ONLY this. Nothing else.

Deliverable:
- `examples/legacy-spring-app/` with `Product` (id, name, price), `ProductService.findById(Long)` returning a real product, a normal controller, and H2 seeded with one product. Treat this app as if you cannot edit it, except to add one dependency.
- `tibyan-runtime-spring/` as a hand-written Spring Boot starter (NOT generated) that: auto-configures on startup, discovers the real `ProductService` bean, registers ONE hard-coded `getProduct` tool, and starts an MCP server inside the application's own context.

Do NOT build in the spike: the IR, any provider, JavaParser, any backend, any generator, the CLI, or the plugin system. All hard-coded.

Success test (this is the only thing that counts):
1. Add the starter as a dependency to the legacy app. Start the app.
2. A human connects an MCP client (Claude Desktop) and calls `getProduct(5)`.
3. The real product comes back, from the real `ProductService`, from H2.
4. No file of the legacy app was edited except adding the one dependency.

Environment note: you can write and run the Spring app and the starter. You cannot connect Claude Desktop yourself. When the app is running and the MCP server is up, stop and tell the human exactly how to connect Claude Desktop and what to call, then wait for them to confirm the real result. The spike is not done until the human confirms a real product came back.

Also record, in Volume 16: which MCP library you used and its version, whether the starter alone was enough, whether bean discovery and tool registration were clean, and the transport (stdio or HTTP). These answers are the real output of the spike.

If the spike does not work cleanly: stop. Do not work around it by faking the result or by quietly switching to a separate process without flagging it. Report the problem and the options (for example, a separate-process runtime calling over REST). Changing the runtime model is a decision for the human, not a silent workaround.

---

## Scope discipline (do not over-build)

The biggest failure mode is building the elegant full platform instead of the smallest working slice. Resist it.

Do not, unless the current phase's definition of done requires it:
- add IR elements beyond entities and read-only capabilities,
- implement extractors other than the Entity extractor (leave the rest as empty placeholders),
- add backends other than MCP,
- add runtimes other than Spring,
- build the plugin marketplace, governance, workflows, or events,
- generalize an abstraction before there is a second case that needs it.

When unsure whether something is in scope, it is not. Ask the human.

---

## Where to read in `docs/`

| You are working on | Open |
|---|---|
| The spike | Volume 8, Volume 16, Volume 17 section 2 |
| The IR | Volume 4 |
| A provider | Volume 5 |
| A backend | Volume 6 |
| The runtime | Volume 7 |
| The pipeline | Volume 9 |
| Module layout and dependency rules | Volume 12 |
| Why a decision is the way it is | Volume 14 (ADRs) |
| Step-by-step build with code sketches | Volume 17 |

---

## How to work

- One phase per session. Keep changes scoped to the current phase.
- After changes, run `mvn test`. Generation changes must keep golden-file tests passing or update them deliberately with a visible diff.
- Never add a dependency to `tibyan-core`.
- Prefer plain Java records and small interfaces over frameworks in the core and plugin-api.
- Code sketches in the volumes are intent, not final code. Make them concrete, but do not change their shape or the rules above.
- If a volume's design turns out to be wrong once code runs (for example, the MCP library does not behave as assumed), surface it and propose the fix; do not silently diverge.
