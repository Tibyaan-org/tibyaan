# Tibyan

**Tibyan reads an existing Spring application, builds a verified model of what it can safely expose, and serves that model to AI agents over MCP and as an OpenAPI document, without changing a line of the original system.**

Its defining property is what it refuses to do. Tibyan only exposes a capability when it can prove, structurally from the source, exactly which real method backs it. When it cannot prove that, it refuses and tells you why, in a located message. It never guesses a binding. That guarantee is measured, not asserted: across two real Spring applications it has never once produced a wrong binding.

- **27 capabilities discovered across two real, unmodified benchmark apps. 0 wrong bindings.**
- Evidence, with the full per-entity ledger of what was discovered and what was refused and why: [ERP backend trial](docs/experiments/third-app-trial.md) (25 capabilities) and [PMS backend trial](docs/experiments/second-app-trial.md) (2 capabilities).

Security matters before you expose anything: read [docs/SECURITY.md](docs/SECURITY.md) first. The generated MCP server executes real methods against real data.

---

## What you will actually see

Tibyan is honest at the door, so set your expectations before the first run.

A large, real application does not turn into a hundred tools. It turns into a precise, verified subset plus a named ledger of everything Tibyan declined to expose. On the ERP benchmark (25 entities) Tibyan discovered 25 capabilities (16 read-by-id, 9 list-all) and named every refusal. On the PMS benchmark (43 entities, view-heavy, with unrendered source files) it discovered 2 and named all the rest. Neither run produced a single wrong binding.

**Refusals are the product working, not failing.** A refusal means Tibyan found something it could not verify and chose to say so rather than expose a plausible-but-wrong tool an agent would then call against your data. A real survey looks like this (from the ERP trial):

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

`User` had two methods that both fit the shape of a read-by-id, so Tibyan refused rather than pick one. That refusal is the whole point: `mis-bound 0` is the number to watch, and it stays 0.

---

## Quickstart

Requirements: Java 17 and Maven, a Spring Boot 3.x application whose services you want to expose.

Availability note: the artifacts are prepared at version `0.1.0` but are not yet on Maven Central (the remaining publish steps are in [docs/RELEASING.md](docs/RELEASING.md)). Until they are, build them into your local Maven repository from a checkout of this repository:

```bash
mvn -DskipTests install
```

**1. Add the runtime dependency** to your application's `pom.xml`. This one dependency hosts the MCP server inside your app's own context:

```xml
<dependency>
  <groupId>com.tibyaan</groupId>
  <artifactId>tibyan-runtime-spring</artifactId>
  <version>0.1.0</version>
</dependency>
```

**2. Add the build plugin** so the capability manifest is generated from your source at build time and placed on the classpath. Nothing is committed by hand:

```xml
<plugin>
  <groupId>com.tibyaan</groupId>
  <artifactId>tibyan-maven-plugin</artifactId>
  <version>0.1.0</version>
  <executions>
    <execution>
      <goals><goal>generate</goal></goals>
    </execution>
  </executions>
</plugin>
```

**3. Build.** The plugin scans your source and writes `tibyan/capabilities.json` onto the classpath (`target/classes/tibyan/capabilities.json`):

```bash
mvn package
```

**4. Run your app as usual.** The runtime finds the manifest on the classpath automatically. No launch property, no MCP block in `application.yml`. If no manifest is found, it fails loudly rather than starting empty:

```bash
java -jar target/your-app.jar
```

The MCP server is served at `http://localhost:8080/sse`. Point an MCP client (for example Claude Desktop) at it, and your discovered capabilities appear as tools that call your real service methods.

**Preview before you adopt.** To see exactly what Tibyan would discover and refuse, without changing your build, run the survey. Build the CLI once (`mvn -pl tibyan-cli -am -DskipTests package`), then:

```bash
java -jar tibyan-cli/target/tibyan-cli-0.1.0-jar-with-dependencies.jar scan /path/to/your-app
```

This prints the discovered capabilities, every refusal with its located reason, and the totals including `mis-bound 0`. It reads source only and writes nothing.

You can also generate an OpenAPI 3.0 document from the same model: `tibyan generate --backend openapi --ir <ir.json> --out <dir>`.

---

## What it does not do yet, and why

Every one of these is a deliberate refusal-over-guessing boundary, not an oversight. The boundary itself is evidence-proven and recorded as a first-class fact, "The structural ceiling", in [Volume 13](docs/volume-13-roadmap.md): what is structurally provable is discovered and has never been wrong; what would require reading business semantics is refused, and is reachable in the future only through explicitly human-attested knowledge (the designed-and-waiting Attestation phase), never through guessing.

- **Write and action capabilities.** Only reads are discovered. Writes are refused at the discovery level because exposing them safely needs a policy gate that does not exist yet; see [docs/SECURITY.md](docs/SECURITY.md).
- **Filtered reads** (`findByCategory`, `search`, and similar). Refused. A parameter's meaning is a claim Tibyan cannot verify from structure alone, and "filter by X" in a tool description is a claim an agent would act on. Carrying filters honestly (as human-attested, never as verified) is the Attestation phase, post-v1.
- **List envelopes** (custom paged result-set wrapper types). Not yet seen through; the unfiltered `findAll` list is modeled, the wrapped variants are consolidated into the same Attestation phase.
- **Non-Spring stacks.** The Capability model is framework-neutral by design, but only the Spring source provider exists today. Other providers (database, OpenAPI import, .NET) are direction, not built.

---

## Where the deep documentation lives

The full design is 17 volumes under [docs/](docs/README.md): the neutral Capability model, the provider rules, the backends, the runtime, and the architecture decisions behind every one. The current build state and resume point is [docs/STATUS.md](docs/STATUS.md).

Contributing, and the design-before-code discipline this project follows, is in [CONTRIBUTING.md](CONTRIBUTING.md). Licensed under Apache-2.0 ([LICENSE](LICENSE)).
