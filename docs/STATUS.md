# Tibyaan status and resume point

Status: MVP complete, plus the first two hardening steps. Work paused here. Last updated 2026-07-01.

Recent changes (most recent first):

- Direction B step two: a truly untouched app needs only the `tibyan-runtime-spring` dependency. The starter now supplies the MCP server name and version as overridable defaults (an `EnvironmentPostProcessor`), and the runtime discovers the manifest by a fixed order (explicit `tibyan.capabilities`, then `classpath:/tibyan/capabilities.json`, then `file:tibyan/capabilities.json`), failing loudly if none is found. The example app proves it: no `spring.ai.mcp.server` block in its `application.yml`, the manifest shipped as a classpath resource at `tibyan/capabilities.json`, no `-Dtibyan.capabilities`, and getProduct still returns the real product over genuine MCP. Details in Volume 7, sections 4 and 8 (gap 2, now closed).
- Direction B step one: the capability binding is discovered from the real `@Service` method, not assumed by the `get<Entity>` to `<Entity>Service#findById` convention. The matching rule and what is still assumed are in Volume 5, section 4.1. For the example app the discovered binding is still `ProductService#findById`; the difference is that it is now read from the source, so a second app with a differently named service or finder is discovered correctly instead of silently mis-bound.

To resume: read this file, then CLAUDE.md and Volume 7 section 8 (Known gaps and deferred work), then pick a next step from "Next directions" below.

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

All paths are relative to the repository root. Build once so the CLI jar and the example app jar exist:

```
mvn -q -DskipTests package
```

The CLI flow that produces the manifest (each command is a single line):

```
java -jar tibyan-cli/target/tibyan-cli-0.1.0-SNAPSHOT-jar-with-dependencies.jar scan examples/legacy-spring-app --out ./generated/legacy.ir.json

java -jar tibyan-cli/target/tibyan-cli-0.1.0-SNAPSHOT-jar-with-dependencies.jar generate --backend mcp --ir ./generated/legacy.ir.json --out ./generated/legacy-mcp
```

The example app already ships that generated manifest on its classpath at `examples/legacy-spring-app/src/main/resources/tibyan/capabilities.json`, so launching needs no manifest property and the app's `application.yml` has no MCP block:

```
java -jar examples/legacy-spring-app/target/legacy-spring-app-0.1.0-SNAPSHOT.jar
```

The runtime finds the manifest on the classpath (verified: it logs `loaded 1 capability(ies) from class path resource [tibyan/capabilities.json]` then `registered 1 MCP tool(s): [getProduct]`, and Spring AI logs `Registered tools: 1`). The app starts on port 8080 with the MCP server at `http://localhost:8080/sse`. An MCP client (for example Claude Desktop) that calls getProduct with id 5 gets the real product, `{"id":5,"name":"Wireless Mouse","price":29.99}`, read from the real H2 store through ProductService.findById.

To point the runtime at a manifest without shipping it as a resource (an ad hoc override), pass `-Dtibyan.capabilities` instead; on Windows PowerShell quote it: `java "-Dtibyan.capabilities=file:generated/legacy-mcp/tibyan/capabilities.json" -jar examples/legacy-spring-app/target/legacy-spring-app-0.1.0-SNAPSHOT.jar`.

---

## Open notes

Known gaps and deferred work are recorded in `docs/volume-07-runtime.md` section 8. Plugin discovery status (not real SPI yet) is in `docs/volume-10-plugin-system.md` section 2. The binding is no longer assumed: it is discovered from the real service method (Volume 5, section 4.1), so the convention is no longer the first thing that breaks on a second app. What is still assumed there: only single-id-parameter reads are modeled, the capability name is still the `get<Entity>` convention, and the identifier is the first `@Id` field.

---

## Next directions (decide when work resumes, not yet acted on)

- Direction A: point the existing tool at a second, real Spring app and observe what breaks. With the binding now discovered, the next likely breaks are shape gaps, not the binding convention: an entity whose finder takes more than the id, a read that returns a list, a service split across classes, or a non-`@Id` identifier. This is still the highest information value for the least code, and it tells us what to build next from evidence rather than guesswork.
- Direction B: harden what exists, in order. Step one, done: the ServiceExtractor is enabled and the binding is discovered from the real service method instead of assumed (Volume 5, section 4.1). Step two, done: a truly untouched app needs only the one dependency; the starter auto-configures the MCP server and discovers the manifest from the classpath, no `application.yml` block and no launch property (Volume 7, sections 4 and 8 gap 2). Candidate step three: remove the remaining manual step by auto-generating or refreshing the classpath manifest at build time (today `tibyan generate` is run separately and the manifest is placed by hand as a resource). Beyond that, health, observability, and lifecycle hardening (Volume 7, section 8, gap 7).
