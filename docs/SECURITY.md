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

## The declared authority contract in the manifest (phase Controller-Authority, built 2026-07-19)

With the Controller-Authority phase built (Volume 5, section 4.14; built 2026-07-19), the manifest can
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

## Attested capabilities: who owns what (phase Attestation, designed, pending build)

Once the Attestation phase is built (Volume 5, section 4.15; designed 2026-07-20), a capability can exist
in a THIRD state alongside tool-verified and refused: HUMAN-ATTESTED. A person on your team asserts the
semantics the tool cannot prove (what a filter means, what an opaque read returns), the assertion lives in
a reviewed, committed file in your repository (`tibyan/attestations.json`), and the tool carries it
character-exactly, labeled on every surface, with the asserter's name, the origin of the words, the date,
and a structural fingerprint of the code it was asserted against.

The division of responsibility, stated plainly so nobody discovers it in an incident review:

- THE TOOL GUARANTEES FAITHFUL CARRIAGE OF YOUR FILE: the claim you ship is exactly the claim in your
  committed `tibyan/attestations.json`, character for character, attached to exactly the method the entry
  targets, labeled `attested_` in the tool name itself and human-attested in every description and
  document, never presented as verified, never counted among the verified capabilities, and invalidated
  loudly the moment the fingerprinted code changes. The tool does not authenticate who typed an entry (it
  cannot tell an original authoring from a later edit); the integrity of the file, that each entry says
  what its named asserter stands behind, is owned by your normal code review and version control, which is
  exactly why the record is a committed, reviewed file and not an ephemeral tool state.
- Attested capabilities EXECUTE exactly like verified ones, with the same access; the label changes what
  the tool CLAIMS about a call, never how the call is made (the binding is structural either way).
- THE ASSERTER OWNS THE CLAIM'S TRUTH. If the assertion is false (the filter matches by containment, the
  attester wrote "prefix"), the tool cannot know, will carry the false sentence exactly, and an agent will
  act on it. Attesting is publishing documentation an agent executes against: review attestations with the
  same seriousness as code, because the provenance line names the asserter, not the tool.
- The blast radius is bounded, and the bound is stated at its true strength, not overstated: before any
  assertion is accepted, the tool runs a read-safety check over everything it read (the target body and
  what it structurally resolves through) and refuses if it sees a write there, so no write is visible in
  the code the tool read, and the binding is always structural (never a wrong write through that code,
  never a wrong binding). The honest residue, stated openly: a write hidden BELOW what the tool read (in a
  hand-written repository or query-builder implementation, exactly the code the assertion covers) is not
  caught. So the guarantee is "no write in what the tool read," not "no write anywhere." That residue is
  smallest for a directly-typed read and largest for a remote delegation, which is why attested authority
  contracts and remote delegations, where a false assertion would breach a SECURITY boundary rather than
  mislabel data, are deliberately DEFERRED to their own sub-phase (AT-3) with a mandatory read-only
  affirmation, and are not attestable until it is ratified.
- If the tool's structural evidence contradicts an assertion, at intake or after a code change, the
  attestation is REFUSED loudly and nothing ships; the tool never silently prefers either side.
- Two exposure controls, with two defaults. `tibyan.attested.expose=false` gives a verified-only surface:
  no attested capability is registered at all. And attested reads whose declared security the tool marked
  `securityUnverifiable` do NOT register by default (the adapter bypasses your controller security gate, so
  a read whose only declared gate is both unverifiable and bypassed should not ship without a deliberate
  choice); set `tibyan.attested.exposeSecurityUnverifiable=true` to include them once you have resolved
  what their declared security means.

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
