# Security posture (v1)

Read this before you expose anything. Tibyan generates a layer that executes real code in your
application. This document states plainly what that means, what v1 protects, and what it deliberately
leaves to you.

## What exposing tools actually does

The generated MCP server runs inside your application's own process and calls your real service methods
against your real data. A tool named `getProduct` invokes the actual `ProductService#findById` bean and
returns the actual product. There is no sandbox and no copy: an agent that can reach the MCP endpoint can
invoke every exposed capability with the same access your service methods already have.

## The read-only boundary, and why it exists

v1 serves read-only capabilities only. Write and action capabilities are not merely turned off in the
runtime; they are refused at the discovery level, so a write is never generated into the manifest in the
first place. This boundary is deliberate: exposing a method that changes data to an autonomous agent is a
different risk class from exposing a read, and it needs an explicit policy gate (which capabilities may be
exposed, to whom, under what conditions) that v1 does not have. Until that gate exists, the honest default
is to expose nothing that mutates state. If a future version adds writes, they will be off by default and
gated, never silently enabled.

## What the generated layer inherits, and what you must gate

The layer inherits your application's process, its database connections, and its data access. It does not
add an authentication or authorization layer of its own. There is no auth in v1.

That makes network exposure of the MCP endpoint your decision, and the most important one:

- Do not expose the MCP endpoint (`/sse` on the app's HTTP port) to an untrusted network. Treat it as you
  would an unauthenticated admin console.
- Bind it to localhost, or keep it behind your existing network controls, VPN, or an authenticating
  reverse proxy that you operate. Put your organization's existing authentication in front of it.
- The capability your app already protects with its own security is not automatically protected here,
  because the tool calls the service method directly through the execution adapter. Assume every exposed
  read is reachable by anyone who can reach the endpoint, and gate the endpoint accordingly.

## What fail-loud does and does not protect against

Tibyan's fail-loud, never-guess discipline protects the integrity of the mapping: a generated tool is
bound only to a method Tibyan structurally proved backs it, so a tool does not silently call the wrong
method or the wrong entity's data. Across two real benchmark apps this held with zero wrong bindings. That
is a correctness guarantee about what each tool does.

It is not an access-control guarantee. Fail-loud does not authenticate callers, does not authorize
actions, and does not rate-limit or audit. It ensures the tools are honest about what they do; it does not
decide who may call them. That decision is yours, and it is the network and process boundary described
above.

## Reporting a vulnerability

Please report suspected security issues privately to the maintainer at ahmed0elseginy@gmail.com rather
than opening a public issue, and allow reasonable time for a fix before any public disclosure.
