# Volume 15: Future RFCs

Status: Process plus seeded drafts. Significant changes to the IR, the contracts, or the platform's scope go through an RFC before they are built. This volume defines the RFC process and the template, and seeds the RFCs that the vision (Volume 1, section 9) already implies, all in Draft.

---

## 1. The RFC process

An RFC (Request for Comments) is how a non-trivial change is proposed, discussed, and decided before code is written. It protects the core's stability: anything that would touch the IR, the plugin API, or the platform's scope is an RFC, not an ad hoc commit.

| Status | Meaning |
|---|---|
| Draft | Written, not yet under formal discussion |
| Discussion | Open for feedback |
| Accepted | Agreed; ready to be scheduled into a phase |
| Implemented | Built and released |
| Rejected | Decided against, with the reason recorded |
| Superseded | Replaced by a later RFC |

When an RFC is accepted, the relevant decision is also captured as an ADR (Volume 14), and the work is scheduled into the roadmap (Volume 13).

## 2. RFC template

```
# RFC-XXXX: Title
Status: Draft
Author:
Created:

## Summary
One paragraph: what this proposes.

## Motivation
Why now. What problem it solves. What breaks if we do nothing.

## Proposal
The design. For IR changes, the exact objects, fields, types, and validation rules.

## Impact
Which volumes and modules change. Backward compatibility and migration.

## Alternatives
What else was considered and why this is preferred.

## Open questions
What is still undecided.
```

## 3. Seeded RFCs

These are the changes the long-term vision already points to. They are intentionally Draft and out of MVP scope (Volume 4, section 7).

### RFC-0001: Workflows in the IR
Status: Draft. Summary: add a Workflow element that sequences capabilities into multi-step business processes. Motivation: operating a system means running processes, not only single calls. Open questions: how to represent branching and compensation without leaking an engine's concepts into the neutral IR.

### RFC-0002: Permissions and governance
Status: Draft. Summary: add Permission and Policy elements so the model carries who may invoke a capability and under what rules, and a governance layer that enforces them at the runtime. Motivation: central, model-driven control of AI access is a core part of the moat (Volume 1, section 9.3). Open questions: how policies compose, and how they map onto the host system's existing authorization.

### RFC-0003: Events in the IR
Status: Draft. Summary: add an Event element for occurrences the system emits or reacts to. Motivation: agents and workflows need to respond to change, not only poll. Open questions: delivery semantics, and how events relate to capabilities.

### RFC-0004: Database provider
Status: Draft. Summary: a CapabilityProvider that builds IR from a database schema. Motivation: proves the IR accepts a non-code source and unlocks systems without readable application source (Volume 13, P6). Open questions: how much business meaning can be recovered from a schema alone.

### RFC-0005: .NET provider and runtime
Status: Draft. Summary: a provider and a runtime for the .NET ecosystem. Motivation: proves the platform crosses ecosystems and strengthens the cross-stack moat (Volume 13, P7). Open questions: in-process hosting on .NET, and source analysis tooling.

### RFC-0006: Plugin marketplace
Status: Draft. Summary: a distribution and discovery mechanism for third-party plugins. Motivation: grows an ecosystem around a stable plugin API (Volume 10, section 7). Open questions: versioning guarantees, trust, and compatibility signaling.

## 4. How to add an RFC

Copy the template in section 2, give it the next number, open it as Draft, and link it from this volume. Keep it Draft until there is a real intent to build it, so this list stays a map of direction rather than a backlog of speculation.
