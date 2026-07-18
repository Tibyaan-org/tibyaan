# Connecting an MCP client

Once the `tibyan-runtime-spring` dependency is in your application and the manifest is on the classpath, the runtime hosts an MCP server inside your app's own process. This page shows what it serves, how to confirm it works, and how to point a client at it. It is written against the example app; substitute your app's host and port.

## What the runtime serves

- An MCP server on the same HTTP port as your application, over the Server-Sent Events (SSE) transport.
- The SSE endpoint is `http://<host>:<port>/sse` (the example app: `http://localhost:8080/sse`). The session's message endpoint is issued to the client as the first SSE event, at `/mcp/message?sessionId=...`.
- One MCP tool per discovered capability. Each tool calls the real service method the capability is bound to.

## Verified end to end (2026-07-07)

This path is verified two ways, so the promise is not just asserted:

- In process, on every build: the `EndToEndMcpIT` failsafe test boots a real Spring context and a genuine MCP server and drives `initialize`, `tools/list`, and `tools/call` against the real bean.
- Out of process, over the network: the example app was launched as a normal jar and an external client connected to `http://localhost:8080/sse` and ran the full handshake. The result, recorded verbatim:

  ```
  SSE endpoint event: /mcp/message?sessionId=e843ec10-...
  initialize: ok   serverInfo: {"name":"tibyaan","version":"0.1.0"}
  tools/list: ['getProduct']
  tools/call getProduct(5): {"content":[{"type":"text","text":"{\"id\":5,\"name\":\"Wireless Mouse\",\"price\":29.99}"}],"isError":false}
  ```

  The returned product is the real row read by `ProductService.findById` from the app's store, not a fixture.

What is not yet mechanically verified: the configuration of any one specific desktop client (for example the Claude Desktop UI), because that is a GUI step on the adopter's machine. The protocol and the network path are verified; the per-client wiring below is the standard approach, to confirm against your client's current version.

## Confirm it yourself

### 1. A one-line smoke test (curl)

With the app running, open the SSE stream. The server should immediately send an `endpoint` event naming the session's message URL, which proves the MCP server is live and speaking the protocol:

```bash
curl -N http://localhost:8080/sse
# event: endpoint
# data: /mcp/message?sessionId=...
```

Press Ctrl-C to stop; the stream stays open by design.

### 2. A full handshake (Python, standard library only)

`scripts/verify-mcp-sse.py` drives the whole flow (open the stream, read the endpoint, `initialize`, `tools/list`, `tools/call getProduct` with id 5) and prints the result. With the example app running:

```bash
python scripts/verify-mcp-sse.py
```

It uses only the Python standard library. Point it at your own app by editing the `BASE` URL, and change the final `tools/call` to one of your discovered tools.

## Pointing a client at it

Any MCP client that speaks the HTTP + SSE transport can connect to `http://<host>:<port>/sse` directly. Two common clients are shown below: Claude Code, which speaks SSE directly, and Claude Desktop, which speaks only stdio and needs a bridge.

### Claude Code (direct SSE)

Claude Code connects to an SSE server directly, no bridge needed. With your app running, register it once:

```bash
claude mcp add --transport sse my-legacy-app http://localhost:8080/sse
```

Then confirm it is connected:

```bash
claude mcp list
```

Inside a session, `/mcp` shows the connected servers, and each discovered capability appears as a tool named `mcp__my-legacy-app__<capability>` (for the example app, `mcp__my-legacy-app__getProduct`). You do not call that name by hand: ask the agent in plain language, and it calls the tool. For the example app, "get product 5" makes the agent call `getProduct` with `id` 5 and read back the real row, `{"id":5,"name":"Wireless Mouse","price":29.99}`, from `ProductService.findById`. For your own app, ask for one of the capabilities your survey discovered.

To scope the server to one project instead of your user config, add it to a project `.mcp.json` (committed with the repo) rather than the user scope; see Claude Code's own documentation for the current file format.

### Claude Desktop and other stdio-only clients

Clients that speak only the stdio transport (Claude Desktop configures stdio servers) reach a remote SSE server through a bridge. The common one is `mcp-remote`, launched over stdio:

```json
{
  "mcpServers": {
    "my-legacy-app": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "http://localhost:8080/sse"]
    }
  }
}
```

Confirm the exact command names and config format against your client's current documentation; client configuration formats change faster than this repository does.

### Transport caveat

The runtime serves the HTTP + SSE transport (pinned by the spike, Volume 16 section 3.2). The MCP ecosystem has been moving toward the newer Streamable HTTP transport. If your client supports only Streamable HTTP, you will need a bridge like the one above, or a future runtime that offers the newer transport. Adding Streamable HTTP to the runtime is a candidate follow-up, not built in v1.

## Before you expose it

Read [SECURITY.md](SECURITY.md) first. The MCP endpoint has no authentication of its own and calls your real methods against your real data. Bind it to localhost or keep it behind your own network controls; treat `/sse` like an unauthenticated admin console.
