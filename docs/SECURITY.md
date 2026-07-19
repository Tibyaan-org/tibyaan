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

## The declared authority contract in the manifest (phase Controller-Authority, designed, pending build)

Once the Controller-Authority phase is built (Volume 5, section 4.14; designed 2026-07-19), the manifest can
carry the security contract your application DECLARES on its own endpoints: a capability may list
`requiredAuthorities` (the literal authority strings your security annotations require, any-of semantics,
compiled only from expressions the tool can fully parse) or be marked `securityUnverifiable` (a declared
contract exists that the tool could not compile completely; treat it as gated until a human resolves it).

Read the three states precisely, because their honesty depends on it:

- `requiredAuthorities` present: your application declares that a caller must hold at least one of these
  authorities for this read. The tool carries the declaration; it does not enforce it.
- `securityUnverifiable` present: a security declaration exists on this read's surface that the tool could
  not compile (an expression beyond the parseable set, a conflict between routes, an unresolvable
  constant). Do not expose this capability through a layer that intends to honor declared security until
  you have resolved what the declaration means.
- Neither present: NO method-security declaration was found on the read's binding or its controller
  endpoints. This is NOT a statement that the read is unprotected: URL-level rules (your
  `SecurityFilterChain`, request matchers, filters) are invisible to this analysis, and the tool never
  reads them.

What does not change: the runtime still enforces nothing. The generated layer still bypasses your HTTP
security entirely (the adapter calls the service bean, not your controller), which is exactly why the
declared contract is carried at all: so YOUR enforcement layer, the orchestration or gateway in front of
the MCP endpoint, can apply the declared requirements before invoking a capability. Gating the endpoint
(the section above) remains your responsibility either way.

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
