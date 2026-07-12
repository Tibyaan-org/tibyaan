# Competitive landscape

Status: external-comparison document, first written 2026-07-12. This file is deliberately NOT part of
the numbered design volumes. The volumes contain only verified fact about Tibyaan itself; this file
contains external claims and positioning analysis, a different category of content held to a different
standard, and it must stay separate. First entry: Rindler.

## How to read this document (the honesty rules it is written under)

1. Every claim about Rindler in this document is sourced from Rindler's own public marketing and
   documentation (its website, product docs, and YC listing, as read in July 2026) and is NOT
   independently verified. We have not run Rindler, not audited its pipeline, and not reproduced any of
   its reliability figures. The whole of section 2 is vendor claims.
2. Every claim about Tibyaan cites the document or ledger in this repository where it is proven.
   Tibyaan's verified facts and Rindler's unverified claims are two different confidence levels, and
   the wording throughout keeps them apart: "Rindler claims" versus "Tibyaan is measured to".
3. The comparison does not frame Tibyaan as universally superior, because that framing would be false.
   For each axis it states where each approach is the correct choice given its input, including the
   axes where Rindler's approach is the only sane option. A comparison in which one side wins every
   axis is marketing, not analysis, and this document rejects it.

## 1. Summary

Two systems, built independently and aimed at different territory, concluded that the right way to put
AI agents in front of an existing system is a small, typed, pre-verified tool interface served over
MCP, not raw access to the underlying surface (raw DOM for the web, raw code for backends). They differ
fundamentally in input, method, and truth model. The one-sentence difference: Rindler makes websites
operable from the OUTSIDE, by driving a real browser and observing behavior; Tibyaan makes backend
systems operable from the INSIDE, by reading source and proving structure.

## 2. What Rindler is (vendor claims, unverified)

Everything in this section restates Rindler's own materials, neutrally and without endorsement. None of
it has been independently verified.

Rindler is a Y Combinator-backed infrastructure company, founded in 2025 by two MIT CS graduates
(Michael Serrano and Arthur De Los Santos, previously researchers at MIT CSAIL). It positions itself as
"the translation layer between AI agents and the web." The problem it names: most websites have no API,
raw browser automation is brittle (it breaks when the UI changes), and dumping raw HTML into an LLM's
context spends a large number of tokens for little signal.

Its pipeline, as described: a user points the system at a URL, an ingest engine crawls the site (a
shallow "Fast" crawl or a deeper type-detecting "Deep" crawl) and produces a "site config", a structured
description of the site's screens, the data on each screen, and the actions available (search, filter,
add to cart, submit a form). An automated verifier then checks that the mapping produces reliable
structured results before it is considered usable; low-coverage crawls are discarded as partial rather
than saved. The result is served from a hosted MCP server (mcp.rindler.ai) that agents reach over HTTP
with OAuth 2.0 PKCE, exposing exactly four tools:

- `start_session`: opens a supported site, returns a session id, the current screen's structured data,
  and the actions valid there.
- `dispatch_action`: performs an action on the current screen and returns the updated structured data.
- `extract_content`: re-reads the current screen as structured records.
- `close_session`: releases the browser session.

Behind those four tools, Rindler's servers drive a real browser against the actual site and handle
navigation, retries, and error recovery server-side. Its listed sub-processors (Browserbase, Kernel,
Railway) suggest managed cloud-browser and deployment infrastructure rather than a self-run browser
farm.

For sites requiring login, Rindler describes this model: it opens a single-use link (expiring in 15
minutes) to a Rindler-managed browser on the site's real login page; the user signs in themselves,
including MFA, and confirms; Rindler stores only the resulting session cookie, encrypted with
AES-256-GCM under a per-user key, not the password. Users can optionally opt in to stored credentials
for automatic login on frequently used sites. Rindler states it does not process payments or store card
numbers; shopping carts are handed off to the merchant's own checkout.

Two claims deserve an explicit unverified flag beyond the blanket one above. First, self-healing:
Rindler claims that because site configs are structural mappings rather than hardcoded CSS or XPath
selectors, a mapping adapts automatically when a site's layout changes, with the verifier continuously
re-confirming reliability. Second, reliability figures such as "3x fewer failed tasks" are the
company's own benchmarks. Both are the vendor's descriptions of its own system, not measurements anyone
outside the company has published.

Business model, as stated: a free tier (site catalog, free mapping of new sites, full MCP access) and
an enterprise tier (dedicated mappings, authenticated sessions at scale, higher limits, SLA-backed
support).

## 3. Feature-by-feature comparison

Each axis below states what Rindler does (per its own materials, unverified), what Tibyaan does (with
the citation), and where each approach is the correct choice. No axis is a one-sided win, and several
are Rindler's to take on its own ground.

### 3.1 Input: a live website versus source code

| Rindler (vendor claims) | Tibyaan (cited) |
|---|---|
| A URL to a live third-party website, treated as a black box | The application's own source tree, read as a white box |

Rindler's input is a running site it does not own and cannot see inside; all it can do is load pages
and observe. Tibyaan's input is the source of a Spring application the adopter owns: the provider
parses the AST with JavaParser and extracts entities and reads from the code itself (Volume 5;
Volume 16 fixes the stack). Where each is right: for a third-party website, black-box observation is
not merely a reasonable choice, it is the only possible one, because there is no source to read. For a
backend you own, the source is the ground truth, and reading it is strictly stronger evidence than
observing behavior from outside; treating your own system as a black box would discard the best
evidence available.

### 3.2 Method: inference checked by a verifier versus structural proof that refuses

| Rindler (vendor claims) | Tibyaan (cited) |
|---|---|
| LLM inference over DOM and behavior, generalized into a site config, checked by an empirical verifier | Structural proof from framework contracts and AST evidence; anything unprovable is refused with a located reason |

Rindler describes inferring a site's schema and actions (page types, fields, interactive elements) and
then empirically verifying that the inferred mapping works, discarding low-coverage results. Tibyaan
never infers: a capability is emitted only when the code structurally proves which real method backs it
(the discovery rules of Volume 5, sections 4.1 to 4.11), and every case the rules cannot decide is
refused loudly with a located error rather than guessed (CLAUDE.md, "fail loud, never guess"; the
refusal ledgers in docs/experiments/second-app-trial.md and third-app-trial.md). Where each is right:
inference plus an empirical verifier is the correct method when behavior is the only observable, since
verification then bounds the error of a guess that cannot be avoided. Structural proof is the correct
method when the source is readable, because it removes the guess entirely instead of bounding it.

### 3.3 Truth model: "we tested it and it worked" versus "we can prove what this tool does"

| Rindler (vendor claims) | Tibyaan (cited) |
|---|---|
| Empirical: the mapping worked when verified, and is re-verified over time | Structural: the binding is proven from source, and regenerated from source at build time |

This is the deepest difference between the two systems, deeper than any feature. Rindler's truth is
empirical and probabilistic: "we ran it and it produced valid structured output", a claim that is true
at verification time and decays as the site drifts, which is exactly why it needs continuous
re-verification and self-healing. Tibyaan's truth is structural: "this tool is bound to this method
because the source proves it", a claim that holds until the source changes, at which point the layer is
regenerated from that source at build time by the Maven plugin (docs/STATUS.md, the build-time
generation entry; Volume 11). One precision matters to keep Tibyaan's side honest: the proof covers the
binding and shape of a read, not the business meaning of arbitrary code. Where meaning is not
structurally provable, Tibyaan refuses rather than claims (Volume 5, section 4.11; Volume 13, "The
structural ceiling"). Both truth models are honest relative to their evidence: empirical verification
is the strongest truth available for a black box, and proof is stronger but purchasable only where the
source can be read.

### 3.4 Failure mode: self-healing recovery versus refusal

| Rindler (vendor claims) | Tibyaan (cited) |
|---|---|
| May occasionally act wrongly on a live site; relies on verification and self-healing to detect and recover | Refuses at generation time when uncertain; zero wrong bindings across every run on both benchmark apps |

Rindler's failure mode, implied by its own architecture, is that an inferred mapping can be wrong or go
stale, so an agent occasionally acts incorrectly against a live site, and the verifier plus the claimed
self-healing loop exist to catch and repair that (recovery quality is the vendor's claim, unverified).
Tibyaan's failure mode is refusal: anything it cannot prove is never generated, so the failure surface
is coverage, not correctness. The measured record: 27 capabilities across two real, unmodified
benchmark apps with zero wrong bindings on every run of every phase (Volume 13, "The structural
ceiling"; per-entity ledgers in docs/experiments/second-app-trial.md and third-app-trial.md). The cost
is equally measured: on the view-heavy pms benchmark Tibyaan discovers 2 reads out of a 43-entity app
and refuses the rest by name, because that app's idiom sits almost entirely above the structural
ceiling (docs/experiments/second-app-trial.md; Volume 5, section 4.11.5). The tradeoff, stated without
a winner: Rindler buys coverage with a safety net, Tibyaan buys trust with a coverage ceiling. Which is
correct depends on the blast radius of a wrong action: a failed consumer-web errand is retried and
shrugged off, so coverage-with-recovery is the right trade there; a business backend silently reading
the wrong entity into an agent's context is the failure Tibyaan exists to never produce, so
refusal-over-guessing is the right trade there.

### 3.5 Runtime shape: a hosted browser fleet versus an in-process generated layer

| Rindler (vendor claims) | Tibyaan (cited) |
|---|---|
| An ongoing hosted service: cloud browser sessions, per-call server-side work, rented infrastructure | A generated layer inside the target app's own process: one dependency plus one build plugin, no vendor infrastructure at runtime |

Every Rindler call is served by its hosted fleet: a browser session opens on its servers, actions run
there, and the agent talks to mcp.rindler.ai. Tibyaan has no runtime service at all: the adopter adds
the `tibyan-runtime-spring` dependency and the `tibyan-maven-plugin` to their build, the manifest is
generated from their own source at build time, and the MCP server runs inside their application's own
process (README quickstart; docs/STATUS.md, the Direction B entries; Volume 7). The business-shape
implication, noted without overclaiming: a continuously operated browser fleet is
venture-infrastructure-shaped (ongoing operating cost, usage-metered economics, an SLA to sell), while
a generated in-process layer is dependency-shaped (cost concentrated at build time, nothing metered, an
artifact on Maven Central; docs/STATUS.md records 0.1.0 published 2026-07-08). Neither shape is
superior in the abstract: the fleet is unavoidable when the thing being operated is other people's
websites, and the in-process layer is available only because the thing being operated is an application
the adopter builds themselves.

### 3.6 Domain: the open web versus backends you own

| Rindler (vendor claims) | Tibyaan (cited) |
|---|---|
| The open consumer web and third-party sites with no API | Spring and Java backends whose source the adopter owns |

Rindler targets sites its users do not control and that offer no API. Tibyaan targets Spring Boot 3.x
backends whose source is in hand (README, "Requirements and compatibility"; Volume 5 scopes the one
built provider to Spring source). These domains are largely non-overlapping: one is systems you cannot
read, the other is systems you can. The theoretical overlap (pointing a browser driver at your own web
UI instead of reading your own source) resolves the same way both times: where source exists, reading
it yields strictly better evidence, and where it does not, observation is all there is. Each tool is
correct in its own domain and unavailable in the other's: Tibyaan cannot say anything about a site
without source, and browser observation of an owned backend would be choosing weaker evidence on
purpose.

### 3.7 Action scope: side-effectful actions versus read-only by design

| Rindler (vendor claims) | Tibyaan (cited) |
|---|---|
| Already performs side-effectful actions (search, filter, add to cart, form submission), with checkout handed to the merchant | v1 is read-only; writes are refused at the discovery level and deferred behind a policy gate that does not exist yet |

Rindler's `dispatch_action` already performs state-changing actions on live sites, with a
human-confirmed session loop and payments explicitly excluded (vendor description). Tibyaan v1
discovers reads only: write and action capabilities are refused at the discovery level, so a write is
never generated into a manifest at all, and any future writes are designed to arrive off by default
behind an explicit policy gate (docs/SECURITY.md, "The read-only boundary"; Volume 13, Tier 3). Read
this axis honestly: Rindler is simply ahead on action scope today, and that is real. The gap reflects
the two domains' different risk calculus rather than a capability Tibyaan attempted and failed: an
add-to-cart on a consumer site is recoverable in a way a mutation in a business system of record is
not, so read-only-first is the deliberate boundary Tibyaan chose for its domain, and side-effectful
operation is the correct ambition for Rindler's.

### 3.8 Auth and security posture

| Rindler (vendor claims) | Tibyaan (cited) |
|---|---|
| User performs the login themselves in a managed browser; only the session cookie is stored, encrypted per-user; OAuth 2.0 PKCE agent-to-server | The layer inherits the host app's own process and data access, adds no auth of its own, and the operator must gate the endpoint |

Rindler, operating other people's gated sites from outside, describes a credential architecture: the
user logs in personally (MFA included) through a single-use expiring link, Rindler keeps only the
encrypted session cookie, and agent access to Rindler itself is OAuth 2.0 PKCE. Tibyaan, running inside
the adopter's own process, inherits that process's database connections and data access, adds no
authentication or authorization layer of its own, and states plainly that gating the MCP endpoint is
the operator's job: bind it to localhost or put existing network controls or an authenticating reverse
proxy in front of it (docs/SECURITY.md). Where each is right: Rindler must build credential machinery,
because from outside a site there is no access to inherit, and the model it describes is a considered
answer to that position. Tibyaan's inherit-and-gate posture is the honest answer for an in-process
layer, but it is also the less built-out of the two: v1 has no auth surface at all, SECURITY.md says so
without hedging, and the exposure decision rests entirely on the operator. An adopter who needs
per-caller authorization today gets it from their own infrastructure, not from Tibyaan.

## 4. What this validates

Two teams that, as far as we can tell, have never interacted, working on opposite sides of the
source-availability line, independently landed on the same shape: do not hand the agent the raw surface
(raw DOM, raw code); pre-map the system into a small, typed, verified-before-served tool interface and
expose it over MCP. Rindler's four session tools and Tibyaan's generated capability manifest (Volume 6;
Volume 16 for the MCP stack) are that same shape grown from different soil. Independent convergence is
real evidence that the shape of the idea is sound. It is not evidence of anything more: it says nothing
about Tibyaan's market success, which currently stands at zero external adopters by our own accounting
(docs/STATUS.md: 0.1.0 published 2026-07-08, next action is get external signal), and the convergence
itself is known to us only through Rindler's own materials.

## 5. Where the approaches converge on the same wall

Rindler's mapping engine faces the same problem Tibyaan's E6 design mapped from the inside: semantics
that cannot be verified from structure. What a button does, what a field filters, what a predicate
selects: Tibyaan's census found that exactly this class of read, the filtered and projected majority,
is not structurally provable in the general case (Volume 5, section 4.11, where the filtered read is
counted as the majority of both benchmark apps' read APIs), and Volume 13 records the resulting
boundary as "The structural ceiling". Facing that same wall, the two systems made opposite choices.
Rindler infers meaning and then empirically verifies the inference, because the open web offers no
source to read: inference-plus-verifier is the only sane option on its input. Tibyaan refuses, and
routes unverifiable meaning to a future explicitly-labeled human attestation mechanism (Volume 13,
"Attestation"), because on owned source an alternative to guessing exists and the never-claim
constraint forbids presenting a guess as verified (Volume 5, section 4.11.3).

Stated plainly, so this section cannot be read as a victory lap: this is not proof that Tibyaan's
choice is universally better. It is proof of something narrower and more useful: when you cannot read
the source, inference plus a verifier is forced; when you can, structural proof becomes available.
Tibyaan's white-box position is an advantage of DOMAIN, not of cleverness. On Rindler's input,
Tibyaan's method cannot run at all; on Tibyaan's input, Rindler's method would be voluntarily settling
for weaker evidence. Each system is the correct epistemology for its side of the wall.

## 6. Complementarity (a positioning observation, not a plan)

A real enterprise owns both kinds of system at once: backends whose source it holds (Tibyaan's provable
territory) and third-party web dependencies with no API and no source (Rindler's inference territory,
per its own positioning). An agent asked to operate "the whole business" would face both, and the two
mechanisms do not compete for the same input: the two input types demand different epistemologies, so
verified in-process tools for owned systems and inferred-and-verified browser tools for external ones
are complementary layers of one agent-facing surface. This paragraph is explicitly a positioning
observation and nothing more: it is NOT a roadmap commitment, NOT a partnership plan, and NOT a
suggestion that Tibyaan build browser automation. Tibyaan's roadmap remains governed by Volume 13 and
its evidence triggers, unchanged by anything in this document.
