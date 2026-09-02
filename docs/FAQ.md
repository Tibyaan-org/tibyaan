# First-run questions

Short answers to the things that surprise people the first time they run Tibyaan. Most of them are the tool working as designed, not failing. Each answer links to the fuller reference.

## I ran `generate` and got only one tool. Where are create, update, delete, and my other reads?

By design. v1 exposes only capabilities it can prove, structurally, from your source, and today that means read-only ones: read-by-id and unfiltered list-all. Everything else is refused on purpose, not missed:

- **Writes (create, update, delete) are refused at the discovery level.** They are never even considered for the manifest. Exposing a write to an AI agent safely needs a policy gate that v1 does not have, so v1 does not generate one. See [SECURITY.md](SECURITY.md) and the "What it does not do yet" section of the [README](../README.md#what-it-does-not-do-yet-and-why).
- **Filtered reads** (`findByStatus`, `search`, and the like) are refused, because a parameter's meaning is a claim Tibyaan cannot verify from structure alone. "Filter by X" in a tool description is a claim an agent would act on, so the tool refuses rather than assert it.
- **Custom paged result-set wrappers** (view objects wrapped in a `...ResultSet` type) are not yet seen through.

You are almost certainly not seeing everything the tool decided about your module. `generate` prints only the proven subset (see the next question). To see the full picture, including every refusal with its located reason, run `scan`:

```bash
java -jar tibyan-cli-0.2.0-jar-with-dependencies.jar scan /path/to/your-app --report survey.md
```

The survey lists what was discovered, what was refused and why (`NO_BACKING_METHOD`, `AMBIGUOUS_BINDING`, `NO_IDENTIFIER`, and so on), and the totals ending in `mis-bound 0`. A large real application turning into a small verified subset plus a long named ledger of refusals is the expected, honest result.

## Why does `scan` show more than `generate` (or the build plugin) does?

They serve two different jobs:

- **`scan` is the survey.** It prints the whole picture: every discovered capability, every refusal with its cause, every skipped file. It is for you, to decide whether to adopt.
- **`generate` and the Maven plugin produce the artifact.** They emit only the proven capabilities, into `tibyan/capabilities.json`, because that manifest is what an agent will act on. A refusal is a deliberate absence from the artifact, so it is not written there.

If you only ran `generate`, run `scan` (ideally with `--report`) to see the reasons behind what did and did not make it into the manifest.

## The output folder has `.java` files like `GetVendorTool.java`. What do I do with them?

For the Spring adoption path, nothing. You do not compile them, copy them, or wire them in.

The MCP backend produces two things: the manifest (`tibyan/capabilities.json`) and one generated tool class per capability. These are two materializations of the same contract for two different hosting paths:

- **The manifest** is the in-context path. Your running app plus the `tibyan-runtime-spring` dependency reads the manifest at startup and registers one MCP tool per entry, each calling your real service method. This is the path the Quickstart uses, and the generated `.java` files play no part in it.
- **The generated tool classes** are the standalone path, the materialization for a separate, hand-hosted generated project that is compiled on its own. Adopting Tibyaan into an existing Spring app does not use them.

So when you adopt with the runtime dependency and the build plugin, ignore the `.java` files entirely and rely on the manifest. This split is recorded in [Volume 7, section 8, item 6](volume-07-runtime.md).

## The manifest `module` field says `vendor-mgt`. Is that a bug? How do I cover the whole app?

Not a bug. The `module` field is just the name of the directory you scanned. If you pointed the tool at the `vendor-mgt` module, you get `vendor-mgt` and only that module's capabilities, because discovery resolves against exactly the source you scan.

To cover a multi-module application, scan the reactor root instead of one module, and configure the build plugin's `sourceRoot` accordingly. The full recipe, including why single-module and whole-application scans can differ, is in [MULTI-MODULE.md](MULTI-MODULE.md).

## How do I connect this to Claude Code, Claude Desktop, or another agent?

Once your adopted app is running, the MCP server is at `http://localhost:8080/sse`. For Claude Code:

```bash
claude mcp add --transport sse my-legacy-app http://localhost:8080/sse
```

Full instructions, including a curl smoke test, a scripted handshake check, the Claude Desktop bridge, and how to ask the agent to make its first call, are in [CONNECTING-AN-MCP-CLIENT.md](CONNECTING-AN-MCP-CLIENT.md).

## Do I need the CLI at all if I use the Maven plugin?

No. The build plugin runs the same pipeline the CLI runs, during your build, and writes the manifest onto the classpath automatically. The CLI is for previewing and inspecting read-only, before and independent of any build change. Use the CLI to decide, use the plugin to adopt. They generate identical output for the same source.

## It felt hard to get started. What is the intended path?

Four steps, in order:

1. **Preview.** `tibyan scan /path/to/your-app --report survey.md`. Read-only, changes nothing.
2. **Read the survey.** Decide from the evidence. Refusals are expected; the one number that must stay 0 is `mis-bound`.
3. **Adopt**, only if the preview convinced you: add the `tibyan-runtime-spring` dependency and the `tibyan-maven-plugin` to your `pom.xml`, then `mvn package`. Two build-file entries, no rewrite.
4. **Connect** an agent to `http://localhost:8080/sse`.

The [README Quickstart](../README.md#quickstart) walks through steps 1 to 4 with copy-paste commands.

## See also

- [README](../README.md): what the tool is, the evidence, and the Quickstart.
- [SECURITY.md](SECURITY.md): read this before exposing the endpoint. It runs real methods against real data.
- [MULTI-MODULE.md](MULTI-MODULE.md): surveying and adopting a multi-module application.
- [CONNECTING-AN-MCP-CLIENT.md](CONNECTING-AN-MCP-CLIENT.md): pointing an agent at the running server.
- [COMMANDS.md](COMMANDS.md): the full command reference for the CLI, plugin, and runtime.
