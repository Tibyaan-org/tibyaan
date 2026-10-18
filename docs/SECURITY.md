# Security posture (v1)

Read this before you expose anything. Tibyan generates a layer that executes real code in your
application. This document states plainly what that means, what v1 protects, and what it deliberately
leaves to you.

## What exposing tools actually does

The generated MCP server runs inside your application's own process and calls your real service methods
against your real data. A tool named `getProduct` invokes the actual `ProductService#findById` bean and
returns the actual product. There is no sandbox and no copy: an agent that can reach the MCP endpoint can
invoke every exposed capability with the same access your service methods already have.

## The write gate (phase DL-1, built 2026-09-23), and the read-only boundary it replaces

Through 0.3.0 the tool served read-only capabilities only: a write was refused at discovery and never reached
a manifest. From DL-1 on the boundary is a GATE, not a refusal, and it sits in three places at once:

1. NOTHING PROVES A WRITE. The discovery rules still emit reads only. A write or action enters the manifest
   solely as a REVIEWED capability: a `tibyan draft` entry that a named human approved in
   `tibyan/drafts.json`, pinned by a fingerprint and by the tool's own label, and named `reviewed_<name>` so the
   tier is visible in the name on every surface. A write without a reviewer fails validation (core rule 12).
2. WRITES ARE OFF BY DEFAULT. The runtime registers a WRITE or ACTION capability only when its EXACT name is
   listed in `tibyan.writes.allow`. No pattern, no prefix, no "all": opening fifteen writes means writing
   fifteen names, and a listed name that does not resolve, is not a write, is blank or repeated fails startup.
   Reviewed READ capabilities register by default (`tibyan.reviewed.expose`, default true), because the
   committed approvals are the opt-in; set it false for a verified-and-attested-only surface.
3. DECLARED SECURITY STILL BYPASSES. The layer calls the service method directly, so a controller's declared
   authority does not run (see the section below). A reviewed capability whose fronting controller declares a
   contract the tool cannot compile is `securityUnverifiable` and does NOT register until its exact name is
   listed in `tibyan.security.exposeUnverifiable`, the one list for that stake in any tier. The attested-only
   global switch `tibyan.attested.exposeSecurityUnverifiable` opens attested capabilities only, never a
   reviewed one.

The two exact-name lists carry no provenance of their own: the host's version control is the record of who opened
what, and when (Volume 7 section 10, decision D3). Review the commit that adds a name as you would review the
code it exposes.

What the tool guarantees for a reviewed capability is carriage, the fingerprint pin, label drift and the gate.
It does not guarantee the description is true; the named reviewer does, exactly as an attester does. There is
no dry run and no in-runtime confirmation in DL-1: a client that honors tool hints is the confirmation, and the
pinned Spring AI bridge cannot yet carry those hints (recorded as a gap in Volume 7, section 11), so today the
name prefix and the first sentence of the description are what a client sees. Through SEC-1 (the section below) an
allowed write refuses an anonymous caller by default; treat it as an admin action reachable by anyone your
authentication admits to the endpoint, and gate the endpoint accordingly.
## The caller at the tool boundary (phase SEC-1, built 2026-09-24)

From SEC-1 on (Volume 7 section 15; ADR-0017) the generated layer knows WHO is calling, and it learns it from
you, never on its own. The runtime supplies the MCP transport provider and, per request, captures the
principal YOUR security filter chain authenticated (Spring Security's `Authentication`, read through the
servlet request; an anonymous request captures nothing). It validates no token, reads no header and keeps no
session: the host authenticates, the runtime observes. Four things follow, in this order:

1. THE CALLER RUNS THE CALL. Every tool invocation runs with the captured `Authentication` as the executing
   thread's `SecurityContext`, cleared afterwards, so method security you declare on a SERVICE bean fires on
   the invocation path exactly as it does on your own. Spring Security is optional in the runtime; a host
   without it starts unchanged and captures nobody.
2. THE COMPILED CONTRACT IS ENFORCED. A tool whose manifest entry carries `requiredAuthorities` refuses
   before invoking unless the caller is authenticated and holds at least one of them as a granted authority,
   compared exactly, literal for literal (no `ROLE_` normalization: the contract is what your application
   declared). The refusal names the tool and the contract, never the caller's authorities. This is not
   optional and no switch lowers it: a contract admits nobody it cannot check, so a principal that is not a
   Spring Security `Authentication` is refused on a contract-carrying tool too.
3. ANONYMOUS WRITES ARE REFUSED BY DEFAULT. Under `tibyan.security.requireCaller` (default `true`) an
   unauthenticated call to any reviewed WRITE or ACTION is refused with a located message, whatever
   `tibyan.writes.allow` says: the allowlist decides what registers, the caller gate decides who may call it.
   A verified or reviewed READ with no contract keeps the behavior of the sections above. Set the property
   `false` only knowingly, for a host with no caller to capture; the startup line reports the choice, and a
   host that keeps the default without Spring Security is warned at startup that every write and every
   contract-carrying tool will refuse.
4. THE STARTUP LINE SAYS SO. One `caller gate` line reports the property, whether Spring Security is on the
   class path, how many registered tools carry a compiled contract, and how many are security-unverifiable
   and open only by list or switch.

What this does NOT do, so the boundary is not misread: it does not validate or issue tokens; it never widens
what a list or switch opens; it never lowers a compiled contract to a hint; and it does not decide what a
caller may see INSIDE a result. A read whose approved description says it narrows to the caller's role is
narrowed by your application, not by the runtime; the runtime only makes sure the application sees the real
caller when it does. A `securityUnverifiable` tool carries no contract to enforce, so the exact-name list
stays its only gate. Gating the endpoint on the network (the section below) remains your responsibility; the
caller gate makes the endpoint behind your authentication behave like the rest of your application.

## What the generated layer inherits, and what you must gate

The layer inherits your application's process, its database connections, and its data access. It does not
add an authentication layer of its own: from SEC-1 on it reads the caller YOUR authentication established and
enforces the contract your application declared (the section above); it still authenticates nobody itself.

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

What changed with SEC-1 (built 2026-09-24, the section above): the runtime now ENFORCES `requiredAuthorities`
against the caller your security chain authenticated, and runs every invocation as that caller. What does not
change: the generated layer still bypasses your HTTP security (the adapter calls the service bean, not your controller), which is exactly why the
declared contract is carried at all: so YOUR enforcement layer, the orchestration or gateway in front of
the MCP endpoint, can apply the declared requirements before invoking a capability. Gating the endpoint
(the section above) remains your responsibility either way.

## Attested capabilities: who owns what (phase Attestation; AT-1 built 2026-07-24)

As of AT-1 (the attestation core plus the PREDICATE category; Volume 5, section 4.15), a capability can
exist in a THIRD state alongside tool-verified and refused: HUMAN-ATTESTED. A person on your team asserts
the semantics the tool cannot prove (in AT-1, what a filter or guard selects; the SHAPE and BOUNDARY
categories are the later sub-phases AT-2 and AT-3), the assertion lives in a reviewed, committed file in
your repository (`tibyan/attestations.json`), and the tool carries it
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

## What `tibyan describe` sends, and to whom

`tibyan describe` is the only command that makes a network call. It sends the fingerprinted slice of each
described entry (the method, its imports, the repository declarations it calls, its return type, its parameter
shapes) to the provider named by `--provider`, Anthropic by default or OpenAI, and to no one else; under OpenAI
the request carries `store: false`. Each provider's key is read from its own environment variable only.
`--dry-run` prints exactly what would be sent and sends nothing. No other command, and nothing at runtime,
calls a model.

## Reporting a vulnerability

Please report suspected security issues privately to the maintainer at ahmed0elseginy@gmail.com rather
than opening a public issue, and allow reasonable time for a fix before any public disclosure.
