# Tibyaan status and resume point

Status: MVP complete. Work paused here. Last updated 2026-06-30.

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

Then run the two CLI commands and launch the app (each command is a single line):

```
java -jar tibyan-cli/target/tibyan-cli-0.1.0-SNAPSHOT-jar-with-dependencies.jar scan examples/legacy-spring-app --out ./generated/legacy.ir.json

java -jar tibyan-cli/target/tibyan-cli-0.1.0-SNAPSHOT-jar-with-dependencies.jar generate --backend mcp --ir ./generated/legacy.ir.json --out ./generated/legacy-mcp

java -Dtibyan.capabilities=file:generated/legacy-mcp/tibyan/capabilities.json -jar examples/legacy-spring-app/target/legacy-spring-app-0.1.0-SNAPSHOT.jar
```

The launch uses a relative `file:` path to the manifest the generate step produced; it resolves from the repository root (verified: the runtime logs `loaded 1 capability(ies)` then `registered 1 MCP tool(s): [getProduct]`). On Windows PowerShell, quote the property: `java "-Dtibyan.capabilities=file:generated/legacy-mcp/tibyan/capabilities.json" -jar ...`.

The app starts on port 8080 with the MCP server at `http://localhost:8080/sse`. An MCP client (for example Claude Desktop) that calls getProduct with id 5 gets the real product, `{"id":5,"name":"Wireless Mouse","price":29.99}`, read from the real H2 store through ProductService.findById.

---

## Open notes

Known gaps and deferred work are recorded in `docs/volume-07-runtime.md` section 8. Plugin discovery status (not real SPI yet) is in `docs/volume-10-plugin-system.md` section 2. The highest-value next item is making the binding real (the ServiceExtractor), because the binding convention is the first thing that breaks on a second, different Spring app.

---

## Next directions (decide when work resumes, not yet acted on)

- Direction A: point the existing tool at a second, real Spring app and observe what breaks. The expected break is the binding convention (get<Entity> assumes <Entity>Service#findById). This is the highest information value for the least code, and it tells us what to build next from evidence rather than guesswork.
- Direction B: harden what exists, in order. First enable the ServiceExtractor so the binding is discovered from the real service method instead of assumed. Then fold the capabilities configuration into the starter auto-configuration so a truly untouched app needs only the one dependency.
