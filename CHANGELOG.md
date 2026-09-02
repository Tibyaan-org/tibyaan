# Changelog

All notable changes to Tibyaan are recorded here. Coordinates are groupId `io.github.tibyaan-org`
on Maven Central.

Versioning is semantic-versioning-shaped with a pre-1.0 caveat, stated in full in
[docs/RELEASING.md](docs/RELEASING.md) section 2: **before 1.0.0, treat every minor bump as
potentially breaking.** A minor bump may change what the survey discovers, the shape of the generated
manifest and IR JSON, and the CLI output. The compatibility boundary is the plugin API
(`tibyan-plugin-api`); the IR JSON and runtime manifest formats are additive where possible but are
not stable before 1.0.0.

---

## 0.3.0 (2026-09-03)

The attestation release. Everything a human can assert about a read that the tool cannot prove
structurally, plus the enum-input constraint and one mis-bind fix.

### Behavior changes an adopter should read before upgrading

- **A capability discovered by 0.2.0 may disappear in 0.3.0.** The Read-Identity phase added a
  by-primary-key body proof to the entity-returning discovery path, which previously matched on
  method signature alone. On a real application this had bound a read-by-id capability to a method
  that filters by a foreign key and returns the first hit: a wrong binding that now correctly
  refuses. If a tool vanishes from your manifest after upgrading, this is why, and the previous
  binding was not safe to rely on. Re-read the survey's refusal ledger.
- **The runtime binds invocation arguments to the target method's declared parameter types**, not to
  types reconstructed from the IR. Capabilities with an enum or date parameter were previously
  uninvokable; they now invoke. An illegal enum value is refused before invoking, naming the legal
  values.
- **`attested_` is a reserved capability-name prefix.** Core validation rejects a discovered
  capability that uses it and an attested capability that omits it.
- Generated manifests and IR JSON carry new optional fields (see below). The 0.3.0 runtime tolerates
  them. Keep the runtime and the manifest that generated it at the same version.

### Added

- **Attestation.** A human may assert, in a committed `tibyan/attestations.json`, the semantics of a
  read the tool cannot tie structurally. The tool still proves everything around the assertion:
  binding, types, read-safety over the fingerprinted slice, the subject entity's existence,
  non-contradiction, and the by-primary-key load wherever an identifier is claimed. A SHA-256
  fingerprint over the target slice is recomputed every scan, so a stale or misattached attestation
  refuses loudly rather than carrying. Categories, in the order they were built:
  - `predicate` (AT-1), what a filtered read SELECTS.
  - `shape` (AT-2), what a read RETURNS.
  - `both`, the compound two-hole category, closed by one entry with one minted name.
  - `boundary` (AT-3), a remote delegation (member C2, including read-only-ness itself, the one
    property the tool cannot check for that shape) and the security contract (member C1, carried by
    the boundary entry on the read it already attests). An attested authority is never a compiled
    authority, and the two can never both be present on one capability.
- **The draft lifecycle (AT-4).** An entry with origin `ai-draft` is a third thing, neither attested
  nor refused: queued for review, counted apart, and shipping nothing (no capability, no IR entry,
  no manifest or OpenAPI surface). Confirmation is a per-entry named human act,
  `tibyan attest --confirm <target> --by <name>`. There is deliberately no confirm-all, in the CLI or
  the API beneath it. The AI assist that would produce drafts is **not** built; any drafter works
  today, human or otherwise, so long as the entry carries that origin.
- **Enum inputs.** A capability input may carry a closed value set, read from a resolved in-tree enum
  declaration: constant names for a bare enum, `@JsonValue` literals for a value-mapped one. Both
  backends render it as a schema-level closed set and the runtime enforces it. Anything the tool
  cannot read refuses the capability rather than shipping an unconstrained input.
- **Controller discovery with declared authorities.** Controllers are extracted and their declared
  `@PreAuthorize` authorities compiled onto the capability. The IR carries a compiled declared
  contract; it does not model a permission system.
- `tibyan attest`, a new CLI command: stamps an entry's fingerprint, and confirms a draft.
- Runtime exposure controls: `tibyan.attested.expose` (default `true`),
  `tibyan.attested.exposeSecurityUnverifiable` (default `false`), and
  `tibyan.attested.exposeBoundary` (default `false`).
- The OpenAPI backend serves attested capabilities under `/attested/<name>`, with a collision check.
- The survey reports ATTESTED and DRAFT as their own classes, counted separately and never summed
  into the discovered total.

### Fixed

- Read-safety is receiver-aware. A mutating-named call is read-safe only if its receiver is provably
  a local, freshly constructed, allowlisted JDK container that never escapes to persistence.
  Previously the check tested the call name before looking at the receiver, so ordinary
  in-application aggregation (`someLocalMap.merge(...)`) was refused as a mutating body. This was the
  project's first defect found by its own measurement.
- Read-safety no longer throws a `NullPointerException` on a read whose body calls a non-field
  receiver (a local, a builder, a static type).
- E6a envelope transparency surfaces only collection reads, never by-id reads, as opaque wrappers.

### IR growth

Two additions, both optional and both earned by a ratified, golden-tested phase: the `Attestation`
component (core validation rules 8, 9 and 11) and the per-input value constraint (rule 10).

### Invariants

`mis-bound 0`, `attestation-drift 0`, `mis-safe 0`, and the new `mis-constrained 0` hold across every
benchmark. Every attestation phase measured byte-identical generated output on all three benchmarks,
so each is provably additive to discovery.

---

## 0.2.0 (2026-07-17)

- **Entity Identity: entities are keyed by qualified FQCN, not simple name.** This was an
  output-blocking failure on real multi-module applications, where legitimately reused names across
  bounded contexts collapsed into one another and the tool emitted nothing.
- Discovery-gap rules, general scope: inherited `@Id`, and the private-helper hop. Measured at zero
  new capabilities discovered, by design; the build changed the honesty of the refusal ledger, not
  coverage.

## 0.1.0 (2026-07-08)

First publication to Maven Central. The v1 implementation: the Spring capability provider, the
Capability IR, the MCP and OpenAPI backends, the Spring runtime, the CLI, and the Maven plugin,
covering read-by-id and the unfiltered list-all across two real applications with zero wrong
bindings.
