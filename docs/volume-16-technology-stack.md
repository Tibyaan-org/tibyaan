# Volume 16: Technology Stack

Status: Stable for the MVP. Exact library versions are pinned during the Technical Spike (Volume 8), because the MCP libraries on the JVM are moving quickly. This volume records what is chosen, what is deferred, and the reasoning, so the choices are not re-litigated later without cause.

---

## 1. Headline decision

Build the MVP in Java with Spring Boot. This is recorded as ADR-0009 and expanded here. The reasoning is not only that Java is the maintainer's strongest skill. It is that the two highest-value and highest-risk parts of the MVP are JVM-bound by their nature:

1. Reading Java and Spring source is native to the JVM. JavaParser already understands Java and can be grown toward Spring and JPA meaning.
2. The runtime must run inside the legacy Spring context and call real beans. Reaching a real `ProductService` bean is a JVM task. No other language can autowire a Spring bean.

So roughly two thirds of the MVP's value sits where Java is the natural fit and another language would add cost without benefit.

## 2. The stack, module by module

| Concern | Choice | Why |
|---|---|---|
| Language and platform | Java, on an LTS version (17 or 21) | Maintainer expertise; the target systems are Java; the runtime must be JVM |
| Application framework (runtime and generated code) | Spring Boot 3.x | The first target stack and the host for the in-context runtime |
| Build | Maven multi-module (Gradle equivalent) | Native to the Spring and Java world; matches generated projects |
| Source analysis | JavaParser (AST) | Reads `.java` files without compiling or running them; understands Java natively (ADR-0010) |
| IR representation | Plain Java records, serialized to JSON | Zero framework dependency in core; JSON for inspection and golden-file tests |
| Code generation | A template engine such as Freemarker or Handlebars | Templates per backend, easy to diff in golden-file tests |
| MCP server library | Official MCP Java SDK, or Spring AI MCP server support | One of these; the spike decides which (see section 4) |
| CLI | picocli | Small, idiomatic Java CLI framework |
| Runtime distribution | Spring Boot starter with auto-configuration | One dependency activates the in-context runtime (ADR-0007) |
| Testing | JUnit 5, AssertJ, golden-file tests, one end-to-end test | Determinism and real-binding verification |

## 3. Versions and how they are pinned

Java and Spring Boot versions should track current LTS and stable releases. The exact MCP library coordinates change often enough that this document does not hard-code them. Instead:

> Rule: the Technical Spike (Volume 8) pins the precise versions of the JVM, Spring Boot, and the chosen MCP library, and records them here as a versions table once proven. Until the spike runs, treat versions as "current LTS and current stable," not as fixed numbers.

### 3.1 Versions pinned by the Technical Spike (Phase -1)

The spike ran on 2026-06-30 and pinned these versions:

| Component | Version | Coordinates |
|---|---|---|
| Java | 17 (Azul Zulu 17.0.18) | LTS |
| Spring Boot | 3.4.1 | `org.springframework.boot:spring-boot-starter-parent:3.4.1` |
| Spring AI BOM | 1.0.9 | `org.springframework.ai:spring-ai-bom:1.0.9` |
| MCP server starter | (managed by Spring AI BOM) | `org.springframework.ai:spring-ai-starter-mcp-server-webmvc` |
| H2 (example app only) | (managed by Spring Boot BOM) | `com.h2database:h2` |

### 3.2 Spike answers

| Question | Answer |
|---|---|
| Which MCP library? | Spring AI MCP server support (`spring-ai-starter-mcp-server-webmvc`), via the Spring AI 1.0.9 BOM. The official MCP Java SDK is used underneath, but Spring AI's auto-configuration handles all wiring. |
| Was the starter alone enough? | Yes. Adding `tibyan-runtime-spring` as a single dependency to the legacy app activated auto-configuration, discovered the `ProductService` bean, registered the tool, and started the MCP server. No other changes to the legacy app were needed beyond minimal `application.yml` config for the MCP server name. |
| Was bean discovery clean? | Yes. `ApplicationContext.getBean("productService")` found the real bean. Reflection via `getClass().getMethod("findById", Long.class)` located the method. No classloader issues, no context conflicts. |
| Was tool registration clean? | Yes. Spring AI's `MethodToolCallbackProvider` scanned for `@Tool`-annotated methods and registered them. The MCP server auto-configuration logged `Registered tools: 1`. |
| Transport? | SSE (Server-Sent Events) over HTTP. The MCP server serves at `/sse` on the same port as the web application (8080). Messages are exchanged via `/mcp/message?sessionId=...`. Claude Desktop connects using the SSE URL `http://localhost:8080/sse`. |
| Were there context or classloader problems? | None. The MCP server runs inside the same Spring ApplicationContext as the legacy app. No separate process, no classloader isolation needed. |
| Is reflection enough, or is ByteBuddy needed? | Reflection is enough. `Method.invoke` on the discovered bean works cleanly. No bytecode manipulation needed. |
| Is auto-configuration enough to activate everything? | Yes. The `@AutoConfiguration` class registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` was picked up automatically by Spring Boot's auto-configuration mechanism. |

## 4. The one open library question

There are two credible ways to run an MCP server on the JVM:

| Option | Nature | Trade-off |
|---|---|---|
| Official MCP Java SDK | The protocol library directly | More control, more wiring to do by hand |
| Spring AI MCP server support | MCP integrated into Spring auto-configuration | Less wiring, closer to the starter model, tied to the Spring AI release cadence |

Recommendation: prefer the Spring AI MCP server support for the MVP, because it does much of the starter and auto-configuration work that `tibyan-runtime-spring` needs anyway, which is the fastest path to the in-context goal. The spike exists partly to confirm this. If Spring AI's MCP support is not mature enough at spike time, fall back to the official SDK and do the wiring in the runtime module. Either way, the MCP library lives behind the runtime and the backend, so swapping it does not touch the IR or the core.

## 5. Why not Rust or Go for the MVP

To be fair to the alternatives, here is the comparison, which is also why they are deferred.

| Factor | Java | Rust or Go |
|---|---|---|
| Reading Java and Spring source | Native fit (JavaParser) | Would parse Java with tree-sitter and re-derive Spring meaning, against the grain |
| In-context runtime and real bean access | Natural, this is the JVM | Not possible in pure Rust or Go; needs a separate process or JVM interop |
| IR, validation, codegen, CLI | Comfortable | Comfortable, even pleasant, plus a learning and ecosystem tax |
| Raw performance, single binary, embedded | JVM is fine for a tool | Rust or Go win, but this is irrelevant to MVP value |

The honest summary: the parts where Rust or Go would shine (a single-binary CLI, a high-performance standalone core, out-of-process analyzers for non-JVM stacks, embedded targets) are all later, out-of-process, or not on the critical path. The parts that carry the MVP (reading Java source and binding to live Spring beans) are JVM problems Rust and Go cannot solve cleanly. Do not split the MVP across two languages; a foreign-function-interface seam on the riskiest piece would cost more than it saves.

Where the comparison flips, later: a `.NET` or PHP provider that runs out of process, a single-binary CLI for distribution, or a WebAssembly or embedded target. Those are P4 and beyond, and Rust or Go become reasonable, sometimes better, choices there. None of them change the core, because the core is framework and language neutral by design.

## 6. Dependencies that must never reach the core

A reminder that this volume must not contradict: `tibyan-core` depends on none of the above. Not Spring, not JavaParser, not the MCP library, not the template engine. Those belong to providers, backends, and runtimes. If any of them appears in core's dependency list, a concept has leaked and must move out (ADR-0001, ADR-0013, Volume 12 section 4).
