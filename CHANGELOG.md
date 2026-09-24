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

## Unreleased

### Added

- **The caller reaches the tool (spike SEC-0; the first half of SEC-1).** The runtime supplies its own WebMVC
  SSE transport provider and the router for it, captures the caller the host authenticated on each MCP request
  into the SDK's per-request transport context, and runs every tool invocation with that caller as the
  thread's `SecurityContext`, cleared afterwards, so a host's own method security fires on the bound bean. It
  validates no token and reads no header: the host authenticates, the runtime observes. Spring Security is
  optional; a host without it starts unchanged. Enforcement of the compiled contract and the anonymous-caller
  refusal (SEC-1, S3 to S5) follow.
- **`tibyan describe`, the description drafter (phase Describe).** The one command that calls a model: for
  every pending entry of `tibyan/drafts.json` with no description it drafts one from the code the tool
  fingerprints and writes it back under `descriptionOrigin: ai-draft`, recording the model id and the
  fingerprint; the entry stays pending until a named human approves it, and a source change marks the draft
  stale on the next `tibyan draft`. Anthropic Messages API over the JDK HTTP client, no SDK, key from
  `ANTHROPIC_API_KEY` only; `--dry-run` prints the prompts without a key. Every other command stays offline,
  and a test guards it.
- **A service registers only the tools it can execute (F9).** At startup, after the exposure policy has
  decided, the runtime resolves every remaining tool's bound class against the application context and
  registers only those exactly one bean hosts; the rest are named on one log line with the reason (no bean
  of the class, or more than one). A manifest generated for a whole reactor and shipped into every service
  therefore advertises, on each service, only what that service hosts. A name in `tibyan.writes.allow` or
  `tibyan.security.exposeUnverifiable` that the service does not host stays a valid entry and is reported.
- **Structured inputs (DL-2b).** A service method parameter typed by an in-tree DTO the runtime can
  construct (a record, or a class with a no-argument constructor, Lombok `@NoArgsConstructor` or `@Data`)
  now binds as a shape: the IR gains `Shape` and `SHAPE_REF` (validation rules 15 and 16), the drafts file
  shows the whole object inline with every skipped field and its reason, the MCP manifest carries a `shapes`
  section and a nested object schema, OpenAPI renders a component schema per shape, and the runtime walks
  the JSON object against the shape (an unknown property is refused by name, an illegal enum value by path)
  before binding it with Jackson. An `@Entity` parameter is refused; a DTO with no mappable field, one the
  runtime cannot construct, or a self-referential one refuses by name. A DTO field change flips an approved
  entry to `STALE_DRAFT`. On the adopter benchmark 246 more drafted methods bind on every input.
- **Collection inputs (DL-2a).** A service method parameter declared `List`, `Set`, `Collection` or
  `Iterable` of a modeled primitive or an in-tree enum now binds, in the drafts file, the attestation intake
  and the reviewed tier, as its element type carried as an array: the IR gains `multipleInputs` on a
  capability (validation rule 14), the drafts file shows `"multiple": true` on the input, the MCP manifest
  renders it, OpenAPI renders an array parameter or body property, and the runtime builds the declared
  collection element by element, refusing an illegal enum element by index with the legal values named. A
  raw, wildcard, nested or array collection still refuses by name; a collection of a DTO waits for DL-2b.
  On the adopter benchmark 59 more drafted methods bind on every input.
- **The binding is qualified on every tier, and the runtime resolves it by class identity (phase Binding
  Identity).** Every capability's `binding` is now `<canonical qualified class name>#<method>`, for example
  `com.acme.inventory.ProductService#findById`, in the IR JSON, both backends' output and the drafts file.
  The runtime resolves the class part to exactly one bean whose user class has that name, refuses zero or
  many by name, and no longer falls back to the bean name or the simple class name. Found on a real host:
  four classes shared the simple name `LookupServiceImpl`, and a reviewed tool resolved to the wrong one.
- **The binding is qualified on every tier, and the runtime resolves it by class identity (phase Binding
  Identity).** Every capability's `binding` is now `<canonical qualified class name>#<method>`, for example
  `com.acme.inventory.ProductService#findById`, in the IR JSON, both backends' output and the drafts file.
  The runtime resolves the class part to exactly one bean whose user class has that name, refuses zero or
  many by name, and no longer falls back to the bean name or the simple class name. Found on a real host:
  four classes shared the simple name `LookupServiceImpl`, and a reviewed tool resolved to the wrong one.
- **`tibyan draft`, and the reviewed tier (phases DL-0 and DL-1, the v2 direction).** `tibyan draft <path>`
  writes `tibyan/drafts.json`: one labeled candidate for every public service method, reads and writes
  (`VERIFIED`, `ATTESTED`, `READ`, `WRITE`, `UNKNOWN`). An entry a named human approves in that file is
  admitted at `tibyan scan` as `reviewed_<name>` after eight checks (resolve, stale, label drift, superseded,
  inputs bind, output describable, review complete, label to kind) and reaches both backends with its class
  in the first sentence. Writes and actions exist for the first time, as reviewed capabilities only, and
  register at the runtime only by exact name in `tibyan.writes.allow` (default empty). New runtime
  properties: `tibyan.reviewed.expose` (default true), `tibyan.writes.allow`, and
  `tibyan.security.exposeUnverifiable`, the one exact-name list that opens a security-unverifiable
  capability of any tier; the attested-only global switch is unchanged and never opens a reviewed tool.
  The IR gains the `Review` component and validation rules 12 and 13; a model without approved drafts is
  byte-identical to 0.3.0 output.
- **The READ label of a draft now closes over calls into other beans (DL-1.1).** A draft is READ only when
  every field call in its slice resolves to an in-tree bean whose method is itself read-only (four hops), a
  MapStruct mapper, a Feign or RestTemplate GET, or a JDK type; a message publisher behind a bean is WRITE,
  reflection or an unresolved type is UNKNOWN. On the adopter benchmark this moved 130 READ labels, 3 of which
  the reading sample had shown to hide a write.
- **The closure now sees through configuration classes, providers, fluent HTTP clients and
  profile-selected implementations (DL-1.2).** `@Configuration` classes count as beans, `ObjectProvider` and
  `Provider` lookups are transparent, `Gson` and `PasswordEncoder` are pure, `RestClient` and `WebClient` go by
  their verb, and a field with several in-tree implementations takes the most severe label across them.
- **Overloads are disambiguated and Lombok accessors are read (DL-1.3).** A call to a bodiless getter of a
  declared field is READ, and a call to one of several same-arity overloads is resolved by the static type of
  each argument the tool can read (a parameter, a typed local, a field, a literal, an enum constant, a `new`);
  an argument it cannot type is named in the reason.

### Fixed

- **The startup line "registered N MCP tool(s)" now names only the tools it registered.** It listed every
  manifest capability, excluded ones included; excluded tools are now logged one per line with their reason
  (rule-13 diagnostic amendment, Volume 7 section 10.6).

- **The MCP server now advertises the version it actually is.** The runtime reported
  `spring.ai.mcp.server.version` as a hardcoded `0.1.0`, and never bumped it, so every MCP client
  that connected to a Tibyaan server through the 0.2.0 and 0.3.0 releases was told it was talking to
  0.1.0. The value is now derived from the runtime module's own Maven version at build time. If you
  read that version programmatically, expect it to change from `0.1.0` to the real one.
- **`tibyan version` and `tibyan --version` derive their version the same way.** The CLI's
  `TOOL_VERSION` was the last hand-edited version literal, kept in step with the POM only by a step
  in the release checklist. Because a resolved-at-runtime value is not a compile-time constant, the
  command now declares a picocli `versionProvider` instead of a `version` attribute. Output format is
  unchanged.

Between them, no version the tool reports is written by hand any more: `mvn versions:set` is the
single place a version is stated. Both literals had gone stale, which is what prompted this.

---

## 0.3.0 (2026-09-03)

The attestation release. Everything a human can assert about a read that the tool cannot prove
structurally, plus the enum-input constraint and one mis-bind fix.

### Behavior changes an adopter should read before upgrading

- **A reviewed READ with an array or an object input is a POST in the OpenAPI document.** A query string
  cannot carry an object honestly, so such a read renders as `POST /reviewed/<name>` with a JSON body; its
  `x-tibyan-kind` still says READ. Reads with only scalar inputs stay GET.
- **A manifest generated by 0.3.0 or earlier no longer starts on the 0.4.0 runtime.** Its bindings are
  simple class names; the runtime refuses the manifest at startup with a message naming the capability and
  the fix (regenerate with the same Tibyaan version as the runtime), because resolving a simple-name binding
  is exactly the mis-bind Binding Identity closed. An existing `tibyan/drafts.json` re-qualifies its
  `binding` field on the next `tibyan draft`, with every review, reviewer, description and approved
  fingerprint kept; the attestation record needs no edit. Two `@Service` classes that share a simple name
  across packages and were refused by the duplicate-binding rule now both emit, provided their capability
  names differ.
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
