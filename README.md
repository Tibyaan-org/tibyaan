# Tibyaan

[![Maven Central](https://img.shields.io/maven-central/v/io.github.tibyaan-org/tibyan-runtime-spring?label=Maven%20Central)](https://central.sonatype.com/namespace/io.github.tibyaan-org)
[![CI](https://github.com/Tibyaan-org/tibyaan/actions/workflows/ci.yml/badge.svg)](https://github.com/Tibyaan-org/tibyaan/actions/workflows/ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

**The software that runs your business cannot talk to AI. Tibyaan changes that.**

Make the systems you already run understandable and usable by AI agents, without rewriting a line of them. Application modernization, in place.

---

## The problem

Every established business runs on software built years ago. It works, and the business depends on it, but it was never designed for an age where AI agents are expected to read a system, act on it, and get real work done. The obvious fix, rebuilding these systems from scratch, is slow, expensive, and risky. So the systems that matter most stay locked away from the very tools that are reshaping every industry.

## The idea

Tibyaan opens your existing systems to AI without rewriting them. It reads a Spring application you already run, works out what it can safely expose, and serves those abilities to AI agents over MCP and as an OpenAPI document, in place. Its defining principle is trust: it surfaces only what it can prove from your own source, and it tells you plainly what it cannot, so nothing it puts in front of an agent is a guess. Today that means verified, read-only access, with write and action capabilities gated behind a policy layer that comes later. The trust guarantee is the foundation everything else is built on.

## Why it matters

- **Modernize without rewriting.** Keep the systems that already work, and add what the AI era needs on top of them, without touching a line of the original.
- **AI-ready, in place.** Your existing software becomes something an AI agent can understand and put to work, through a server that runs inside your own application and calls your real methods.
- **Trustworthy by design.** It never guesses. Across two real applications it has never once produced a wrong binding. What it shows you, you can rely on.

A clear path from the software you already have to the AI-ready business you want, without the cost and risk of starting over.

---

## The evidence

Tibyaan only exposes a capability when it can prove, structurally from the source, exactly which real method backs it. When it cannot prove that, it refuses and tells you why, in a located message. That guarantee is measured, not asserted:

- **30 capabilities discovered across two real benchmark apps. 0 wrong bindings.**
- Evidence, with the full per-entity ledger of what was discovered and what was refused and why: [ERP backend trial](docs/experiments/third-app-trial.md) (25 capabilities) and [PMS backend trial](docs/experiments/second-app-trial.md) (5 capabilities).
- **Read those numbers honestly.** They are measured on exactly two applications, both written by the same author, and coverage is idiom-bound: the two apps discover through different rules, and the count does not compound (see [the transfer verdict](docs/experiments/third-app-trial.md#7-the-transfer-verdict)). The ERP app returns entities from its services and discovers well (16 of 25 by-id); the PMS app returns DTOs from its services, which is common in mature layered systems, and discovers little (5 of 43). Your app's number depends on your idioms, and could land anywhere in that range. The one guarantee that does transfer is `mis-bound 0`: no wrong binding on either app, on any run. Preview your own app (Quickstart) to see your real number before you decide. (The PMS figure was re-baselined from 2 to 5 on 2026-07-20 after the app's owner repaired 27 copy-corrupted source files; Tibyaan itself still rewrites no business code. See [second-app-trial.md, section 9](docs/experiments/second-app-trial.md).)

Security matters before you expose anything: read [docs/SECURITY.md](docs/SECURITY.md) first. The generated MCP server executes real methods against real data.

---

## What you will actually see

Tibyaan is honest at the door, so set your expectations before the first run.

A large, real application does not turn into a hundred tools. It turns into a precise, verified subset plus a named ledger of everything Tibyaan declined to expose. On the ERP benchmark (25 entities) Tibyaan discovered 25 capabilities (16 read-by-id, 9 list-all) and named every refusal. On the PMS benchmark (43 entities, view-heavy, with unrendered source files) it discovered 2 and named all the rest. Neither run produced a single wrong binding.

**Refusals are the product working, not failing.** A refusal means Tibyaan found something it could not verify and chose to say so rather than expose a plausible-but-wrong tool an agent would then call against your data. A real survey looks like this (from the ERP trial):

```text
Discovered (25):
  by-id (16):
    + Contact: getContact -> ContactServiceImpl#findById
    ...
  list (9):
    + listContact -> ContactServiceImpl#findAll (paged)
    ...
Failed (9):
  - EmailTemplate [NO_IDENTIFIER]: @Id 'type' is an enum, which maps to no bindable primitive.
  - User [AMBIGUOUS_BINDING]: backed by more than one candidate: [UserService#findById, UserService#findByUsername];
    the backing method must be unambiguous; the provider will not guess.
  ...
Totals: discovered 25 (by-id 16, list 9), failed 9, list-refused 1, skipped-by-parse 0, partial 13, mis-bound 0
```

`User` had two methods that both fit the shape of a read-by-id, so Tibyaan refused rather than pick one. That refusal is the whole point: `mis-bound 0` is the number to watch, and it stays 0.

---

## Requirements and compatibility

Read this before you plan an adoption, because v1 does not fit every "aging Spring backend".

- **Host application: Spring Boot 3.x on Java 17 or later.** The runtime hosts the MCP server through Spring AI (`spring-ai-starter-mcp-server-webmvc`, Spring AI 1.0.9), which requires Boot 3.x, which requires Java 17. A Spring Boot 2.x or Java 8/11 application cannot host this runtime as-is: it would first need a Boot 2 to 3 migration, which is a modernization project in its own right. If your backend is pre-Boot-3, v1 is not yet for you, and we would rather tell you that now than after you have edited your `pom.xml`.
- **Build tool: Maven.** The build-time manifest generation ships as a Maven plugin. A Gradle equivalent is not built.
- **The CLI preview needs only Java 17.** It reads source and never runs your app, so you can preview any Java source tree with it regardless of the host's runtime requirements above.
- **Transport: HTTP with Server-Sent Events (SSE).** The MCP endpoint is served at `/sse`. Your MCP client must speak the HTTP+SSE transport (many clients that speak only stdio need a bridge). See [docs/SECURITY.md](docs/SECURITY.md) before exposing the endpoint.

## Quickstart

Availability note: the artifacts are on Maven Central under groupId `io.github.tibyaan-org`. The current release is `0.3.0`; `0.2.0` and `0.1.0` remain available for anyone pinned to them. Pre-1.0, treat a minor bump as potentially breaking: read [docs/RELEASING.md](docs/RELEASING.md) section 2 before upgrading.

### Step 1: Preview first. This reads your source and changes nothing.

Always start here. The preview is read-only: it never writes to your project, never touches your build, and cannot break anything. It shows you exactly what Tibyaan would expose and what it would refuse, so you decide with the evidence in front of you before you change a single file.

Download the self-contained CLI from Maven Central, then point it at your application:

```bash
# Download the tibyan CLI (a self-contained jar):
curl -O https://repo1.maven.org/maven2/io/github/tibyaan-org/tibyan-cli/0.3.0/tibyan-cli-0.3.0-jar-with-dependencies.jar

# Survey your application. This only reads source; it writes nothing:
java -jar tibyan-cli-0.3.0-jar-with-dependencies.jar scan /path/to/your-app
```

Prefer to build from source? From a checkout of this repository, run `mvn -pl tibyan-cli -am -DskipTests package`, then use `tibyan-cli/target/tibyan-cli-0.4.0-SNAPSHOT-jar-with-dependencies.jar`.

This prints the discovered capabilities, every refusal with its located reason, and the totals including `mis-bound 0`. Expect most of a real, layered application to be refused (see "What you will actually see" above); that is the tool working, not failing. If the preview discovers nothing useful for your app, stop here: you have spent no build changes to learn it.

**Multi-module application?** Point the scan at the whole reactor root, not one module at a time; you get one manifest covering every module. Discovery resolves against exactly the source you scan, so this matters. See [Surveying and adopting a multi-module application](docs/MULTI-MODULE.md).

You can also generate an OpenAPI 3.0 document from the same read-only model: `tibyan generate --backend openapi --ir <ir.json> --out <dir>`.

### Step 2: Adopt. This wires the layer into your build.

Once the preview convinces you, add two things to your application's `pom.xml`. First the runtime dependency, which hosts the MCP server inside your app's own context:

```xml
<dependency>
  <groupId>io.github.tibyaan-org</groupId>
  <artifactId>tibyan-runtime-spring</artifactId>
  <version>0.3.0</version>
</dependency>
```

Then the build plugin, which generates the capability manifest from your source at build time and places it on the classpath, so nothing is committed by hand:

```xml
<plugin>
  <groupId>io.github.tibyaan-org</groupId>
  <artifactId>tibyan-maven-plugin</artifactId>
  <version>0.3.0</version>
  <executions>
    <execution>
      <goals><goal>generate</goal></goals>
    </execution>
  </executions>
</plugin>
```

> **Build-safety note.** The build plugin is resilient by default: if an in-scope source file cannot be parsed at the Java 17 language level (for example newer-than-17 syntax, or a genuinely malformed file), the plugin skips and names it at WARN and generates from what it could read, rather than failing your build. It never modifies your source. If you want your build to fail on any unparseable file instead (for example to gate CI), set `<strict>true</strict>` on the plugin, or pass `-Dtibyan.strict=true`. Either way, running the Step 1 preview first shows you exactly what would be discovered and skipped before you wire anything in.

Build, and the plugin writes `tibyan/capabilities.json` onto the classpath (`target/classes/tibyan/capabilities.json`):

```bash
mvn package
```

Then run your app as usual. The runtime finds the manifest on the classpath automatically: no launch property, no MCP block in `application.yml`. If the manifest is genuinely missing, it fails loudly rather than starting empty:

```bash
java -jar target/your-app.jar
```

The MCP server is served at `http://localhost:8080/sse`. Point an MCP client that speaks the HTTP+SSE transport at it, and your discovered capabilities appear as tools that call your real service methods. This is verified end to end: an out-of-process client run against the example app completes `initialize`, `tools/list`, and `tools/call getProduct(5)`, and gets the real product back over the network. For the exact steps, a one-line curl smoke test, a scripted check, and how to connect a stdio-only client like Claude Desktop through a bridge, see [Connecting an MCP client](docs/CONNECTING-AN-MCP-CLIENT.md).

---

## What it does not do yet, and why

Every one of these is a deliberate refusal-over-guessing boundary, not an oversight. The boundary itself is evidence-proven and recorded as a first-class fact, "The structural ceiling", in [Volume 13](docs/volume-13-roadmap.md): what is structurally provable is discovered and has never been wrong; what would require reading business semantics is refused, and is reachable in the future only through explicitly human-attested knowledge (the designed-and-waiting Attestation phase), never through guessing.

- **Write and action capabilities.** Only reads are discovered. Writes are refused at the discovery level because exposing them safely needs a policy gate that does not exist yet; see [docs/SECURITY.md](docs/SECURITY.md).
- **Filtered reads** (`findByCategory`, `search`, and similar). Refused. A parameter's meaning is a claim Tibyaan cannot verify from structure alone, and "filter by X" in a tool description is a claim an agent would act on. Carrying filters honestly (as human-attested, never as verified) is the Attestation phase, post-v1.
- **List envelopes** (custom paged result-set wrapper types). Not yet seen through; the unfiltered `findAll` list is modeled, the wrapped variants are consolidated into the same Attestation phase.
- **Non-Spring stacks.** The Capability model is framework-neutral by design, but only the Spring source provider exists today. Other providers (database, OpenAPI import, .NET) are direction, not built.

---

## A note on the name

The project is **Tibyaan** (the organization, the Maven groupId `io.github.tibyaan-org`, and the Java package root `com.tibyaan`). For historical reasons the Maven artifact ids and the CLI command keep the shorter spelling `tibyan` (for example `tibyan-runtime-spring`, `tibyan-maven-plugin`, and the `tibyan` command). They are the same project. A single canonical spelling across artifacts and the CLI is a candidate cleanup for the 1.0 line, where changing coordinates is acceptable.

## Adopting: practical guides

Surprised by your first run? Start with the [first-run FAQ](docs/FAQ.md): why you may see only one tool, what the generated `.java` files are for, why `scan` shows more than `generate`, and how to cover a multi-module app. The full command reference is [docs/COMMANDS.md](docs/COMMANDS.md); connecting an agent (including Claude Code) is [docs/CONNECTING-AN-MCP-CLIENT.md](docs/CONNECTING-AN-MCP-CLIENT.md).

## Where the deep documentation lives

The full design is 17 volumes under [docs/](docs/README.md): the neutral Capability model, the provider rules, the backends, the runtime, and the architecture decisions behind every one. The current build state and resume point is [docs/STATUS.md](docs/STATUS.md).

Competitive landscape: how Tibyaan compares to external systems (vendor claims and positioning analysis, kept separate from the technical spec) is in [docs/competitive-landscape.md](docs/competitive-landscape.md).

Contributing, and the design-before-code discipline this project follows, is in [CONTRIBUTING.md](CONTRIBUTING.md). Licensed under Apache-2.0 ([LICENSE](LICENSE)).
