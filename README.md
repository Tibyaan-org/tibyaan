# Tibyaan

Workspace for building **Tibyan**, an application modernization platform that turns an existing legacy business system into an AI-operable platform without rewriting it.

This repository is set up for agent-assisted development with Claude Code.

## Start here
1. Read `CLAUDE.md`. It is the build brief: the order to build in, the rules that must not be broken, and the first task. `AGENTS.md` is the same brief for other agents.
2. The full design is in `docs/`. The single-file edition is `docs/Tibyan_Documentation.md` (and `docs/Tibyan_Documentation.pdf`). Individual volumes are `docs/volume-01-...` through `docs/volume-17-...`.

## Prime directive
Build the Technical Spike first (Phase -1), then stop. See `CLAUDE.md` and `docs/volume-08-technical-spike.md`. Do not build the platform until the spike proves the runtime can call a real Spring bean over MCP, on an unmodified app.

## Build order
Phase -1 Spike  ->  P0 Core + IR  ->  P1 Spring provider  ->  P2 MCP backend + runtime  ->  P3 CLI

## Hard rules (summary; full list in CLAUDE.md)
- `tibyan-core` has zero external dependencies
- Backends never call frameworks; they call the ExecutionAdapter
- Keep the IR tiny: entities and read-only capabilities only
- One plugin per module; package root `com.tibyaan`
- Build only on standard public libraries (no private foundation libs)

## Layout
```
tibyaan/
|-- pom.xml              # Maven aggregator (parent POM)
|-- CLAUDE.md            # agent build brief (read first)
|-- AGENTS.md            # same brief, for non-Claude agents
|-- README.md            # this file
|-- .gitignore
|-- .gitattributes
|-- tibyan-runtime-spring/   # Spring Boot starter: MCP server inside the app context
|-- examples/
|   `-- legacy-spring-app/   # example Spring app used by the spike and tests
`-- docs/                    # the full documentation, Volumes 1 to 17
    |-- README.md
    |-- Tibyan_Documentation.md
    |-- Tibyan_Documentation.pdf
    |-- volume-01-...  ... volume-17-...
    `-- diagrams/
```
Additional modules (tibyan-core, tibyan-plugin-api, and so on) are created as the build progresses past the spike, following CLAUDE.md.

## Running the spike

```bash
mvn clean install -DskipTests
cd examples/legacy-spring-app
mvn spring-boot:run
```

The app starts on port 8080. The MCP server is available at `http://localhost:8080/sse`. See CLAUDE.md for what to test and Volume 16 for the spike results.
