# RFC-0007: An outside-in provider, and spike OI-0 that would test it

Status: Draft. NOT ratified, NOT scheduled, no code. Written 2026-10-18.
Seeded in Volume 15, section 3.

## Summary

Every built Tibyan capability comes from reading source (Volume 5). This RFC asks whether a second
CapabilityProvider could build drafts from a system's OUTSIDE, its published HTTP contract, for systems
whose source Tibyan cannot read. It proposes a spike, OI-0, with four questions, states which tier such
capabilities could land in (the reviewed tier only, never VERIFIED or ATTESTED), and names the evidence
that would have to exist before any build. It is written now because two outside-in projects were placed
in `lap/` for review (section 7); they are the occasion, not the evidence.

## 1. What this is, and what it is not

It is the "OpenAPI provider" row of Volume 5, section 5 ("imports systems that already publish a
contract"), made concrete enough to spike. Input: an OpenAPI document served or shipped by a running
system the adopter OWNS and operates. Output: the same IR, so both backends and the write gate apply
unchanged.

It is NOT the generic web-level tool. That option was assessed and refused on evidence on 2026-09-23
(Volume 13, section 3, "v2 direction"), and nothing here reopens it. Three things stay out of scope by
name:

1. Driving a user interface with a browser. Not deterministic, so it cannot satisfy hard rule 10.
2. Third-party sites the adopter does not own. That is Rindler's territory
   (docs/competitive-landscape.md, sections 3.1 and 6), and the 2026-09-23 decision covers it.
3. Any model in the proof or label path (section 5).

## 2. Which tier the capabilities land in

| Tier | Outside-in? | Why |
|---|---|---|
| VERIFIED | Never | The discovery rules prove facts from source: the by-primary-key load (section 4.1.1), read-safety (4.15.2.1), the entity key. A contract states none of them. |
| ATTESTED | Never, under the current intake | An attestation target is a source declaration, and the intake proves preconditions (local tie, subject entity, by-primary-key load) from the code. With no code there is nothing to check the claim against. |
| REVIEWED | Yes, as drafts only | A draft is a named human's decision carried faithfully (Volume 5, section 4.17). That is the only honest home for a capability whose behavior Tibyan cannot inspect. |

Label policy inside the reviewed tier, proposed:

- POST, PUT, PATCH, DELETE: WRITE. A declared mutation is taken at its word, because erring toward WRITE
  is the safe direction, and it stays behind `tibyan.writes.allow` as today.
- GET, HEAD: UNKNOWN, reason "declared read, unproven". Never READ, so `readOnlyHint` is never true on
  an outside-in tool. An HTTP method is the server's convention, not a proof, and Q2 below measures how
  often that convention lies on a real app.

The four zero invariants are claims about the VERIFIED and ATTESTED tiers, so they are untouched by
construction: no outside-in capability can ever enter those tiers. `review-drift 0` would need its own
definition, because today's fingerprint is over a source slice (Q3).

## 3. Spike OI-0: the four questions

The control app is as-backend. It is the only benchmark with both readable source AND a published
contract (its controllers are generated from OpenAPI YAML through springdoc, per its root `pom.xml`), so
every outside-in answer can be checked against what source reading already says about the same
operation. Spike code is a throwaway outside the reactor: no module, no hard rule touched.

**Q1, INPUT.** From the as-backend contract alone, how many operations yield a draft whose inputs and
output map onto the closed primitive set and `Shape` (DL-2b), and how many are refused, by cause? This
is the census, the outside-in counterpart of as-backend-trial.md section 10.

**Q2, LABEL HONESTY.** Join each operation to the service method its controller calls (controller
discovery exists since Controller-Authority, section 4.14) and compare the HTTP-method label against the
source label from `tibyan draft`. Report the full confusion table. The cell that matters is a GET whose
service method labels WRITE: each one is a mutation an outside-in provider would have shipped under a
read verb.

**Q3, BINDING AND DRIFT.** Binding Identity (Volume 7, section 12) requires `<canonical FQCN>#<method>`,
and an unqualified manifest fails startup. An outside-in binding has no class: it would be
`<HTTP method> <path>` against a base URL. Does that fit the manifest and Volume 4 rule 7 without IR
growth, and what does the drift fingerprint cover (the operation's contract fragment)? If the answer is
"only with a new binding kind", that is IR growth under hard rule 5: the spike stops and reports.

**Q4, EXECUTION AND THE CALLER.** Today the ExecutionAdapter invokes a bean in the host's own process,
and SEC-1 only OBSERVES the caller the host authenticated (Volume 7, section 15.5: it reads no headers,
validates no tokens). An outside-in tool runs in a separate process and must call the target over HTTP,
which means forwarding a credential. Can an HTTP ExecutionAdapter run inside the existing Spring runtime
and pass the MCP caller's bearer through unchanged, with no token stored, minted or widened? If it
cannot, the spike stops: Tibyan holding credentials is a security decision for the maintainer, not a
spike workaround.

Forecast, stated before any run so a miss is a finding:

- Q1: most operations bind, because as-backend's contract is the source of its controllers and its DTOs
  are already shapes.
- Q2: NONZERO GET-over-WRITE cells. as-backend-trial.md section 10.2 found 3 of 30 READ labels hiding
  a write through a bean call (source reading catches them since DL-1.1; an HTTP verb cannot), so some
  read verbs over such services are expected.
- Q3: needs a binding kind, so the spike most likely ends here with a decision, not a build.
- Q4: a forwarding adapter is feasible; whether it is acceptable is the decision.

Exit: the four answers and the Q2 table recorded in the as-backend ledger as a new dated section, a no
on any question reported as a no and never worked around (CLAUDE.md, "If a phase does not work cleanly:
stop").

## 4. The evidence that would justify a build

The spike answers whether it CAN work. Only the following says whether it SHOULD be built, per the
evidence-first directive and Volume 13's promotion rule. All four are required:

- **E1, a real system.** A named adopter system (redaction allowed, as with the enterprise benchmark)
  that the adopter owns and operates, whose source Tibyan cannot read: not Java or Spring, vendor-built,
  or source not held. Recorded in a ledger with structural counts.
- **E2, a contract.** That system publishes an OpenAPI document, or can produce one. Without it, Q1's
  answer for that system is zero and nothing is built.
- **E3, a request.** The adopter asks for it, as Controller-Authority was asked for. Internal ambition
  does not count.
- **E4, the spike's answers.** OI-0 run, with the Q2 table published. A large GET-over-WRITE count does
  not kill the idea (the label policy already refuses READ), but it must be known.

Not evidence: that a competitor exists, that code is available in `lap/`, or that a model could be
trained. If E1 to E4 hold, this would also be the "second provider proves source neutrality" item of
Volume 13, Tier 3, and the maintainer would choose between it and RFC-0004 (the database provider).

## 5. Where an AI model could and could not go

The standing verdict holds: a probabilistic label is the "plausible binding" the design forbids, so no
model labels, binds, maps types or proves anything. Two slots exist, both already built or owned
elsewhere:

- **Describing drafts.** `tibyan describe` (Volume 5, sections 4.15.5.3 and 4.15.5.6) drafts a
  description under origin `ai-draft` for a human to confirm. It would work on outside-in drafts if its
  slice were the contract fragment instead of source; that is a small question for the build, not the
  spike.
- **Planning and routing.** Choosing which tool to call is the agent's job, on the MCP client side.
  Racore's bundled 0.5B local planner is that pattern. Tibyan serves tools to whatever agent the adopter
  brings; it does not ship a planner.

Training a new model is not on this path at any step.

## 6. Decisions for the maintainer

- **D1, timing.** Whether to run OI-0 at all now. Recommended: not before 0.4.0 is cut, and not before
  E1 exists, since without E1 the spike answers a question nobody has asked.
- **D2, input scope.** Contract only (recommended), or contract plus recorded HAR traffic. HAR gives
  examples, not types, so it can never be the only input.
- **D3, the GET label.** UNKNOWN (recommended), or READ when Q2 measures zero disagreements. Even a zero
  on as-backend is one app, so UNKNOWN stays the safer default.

## 7. What the `lap/` projects contribute

Ideas only. No code is reusable: Tibyan is Apache-2.0 Java; the rindler CLI is LGPL-3.0 Go, and copying
it would bring its license with it; Racore Browser ships under a non-commercial evaluation license,
"all rights reserved". The two zip copies under `docs/` should not be committed for the same reason.

Ideas worth keeping, each already compatible with Tibyan's discipline:

- **Ran versus got something (rindler).** Its run report keeps "did the attempt finish" apart from "what
  did the source actually hold", and exits non-zero when a run finished but retrieved nothing. Over HTTP,
  a 200 with an empty body is exactly that ambiguity, so an outside-in result should report both.
- **Fail with the fix (rindler `doctor`).** Diagnose a broken setup and print the remedy, not the
  symptom. The same spirit as Tibyan's located errors.
- **A human approval boundary (Racore).** External side effects stop for explicit approval. Tibyan's
  write gate already does this, by exact name and default off.
- **A small local model whose output is validated, never trusted (Racore).** The same stance as section
  5: the model proposes, deterministic code decides.
