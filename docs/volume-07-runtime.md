# Volume 7: Runtime

Status: Stable for the Spring runtime's MVP responsibilities; later responsibilities are described as direction. This volume is intentionally detailed, because the runtime is where the generated layer meets the real system, and it is the part the Technical Spike (Volume 8) exists to prove.

---

## 1. Why the runtime exists

A backend produces generated code, but generated code does not run itself. Something must host it, connect it to the real system, and keep it alive. That something is the Runtime. In Tibyan's model, the runtime is what makes a legacy system AI-operable in practice: it places the generated MCP layer inside or beside the real application and binds each tool to real logic.

## 2. Why the runtime is not an ExecutionAdapter

These are often confused, so the distinction is stated plainly. The ExecutionAdapter is a small, stateless interface that answers one question: given a capability and arguments, run it and return a result. The Runtime is a whole hosting environment with many responsibilities, of which invoking a capability is only one. Folding the runtime into the adapter would mislabel an environment as a function and would, over time, stuff lifecycle and observability concerns into a place never meant to hold them (ADR-0006). A runtime contains an ExecutionAdapter; it is not one.

```mermaid
flowchart TD
    subgraph RT[Runtime]
      DISC[Bean and capability discovery]
      DI[Dependency injection]
      REG[Tool registration]
      CFG[Configuration]
      EA[ExecutionAdapter]
      HC[Health checks]
      OBS[Observability]
      LC[Lifecycle]
    end
    GEN[Generated layer] --> RT
    EA --> APP[Real application logic]
```

## 3. Runtime responsibilities

| Responsibility | What it does |
|---|---|
| Discovery | Finds the real beans and the capabilities the tools must reach |
| Dependency injection | Resolves the dependencies the generated and host code need |
| Tool registration | Registers the generated tools with the running MCP server |
| Configuration | Reads settings (transport, exposure, limits) from the host application |
| Execution | Invokes real logic through the ExecutionAdapter |
| Health checks | Reports whether the layer and its targets are ready and live |
| Observability | Emits logs, metrics, and traces for tool calls |
| Lifecycle | Starts the MCP server at startup and shuts it down cleanly |
| Runtime plugins | Allows runtime-level extensions (for example, an auth interceptor) later |

## 4. The Spring runtime (MVP)

`tibyan-runtime-spring` is distributed as a Spring Boot starter (ADR-0007). Adding one dependency activates auto-configuration, which discovers the target bean, registers the generated tools, provides the `SpringExecutionAdapter`, and starts the MCP server inside the application's own context. For the MVP, the responsibilities exercised are discovery, tool registration, bean access, and running the MCP server. Health, observability, and lifecycle hardening come later, but the structure is in place from the start.

One dependency, nothing else. A truly untouched app needs only this starter on its classpath; it needs no `spring.ai.mcp.server` block and no launch property. Two pieces make that true:

1. MCP server defaults. The starter contributes default values for `spring.ai.mcp.server.name` and `spring.ai.mcp.server.version` through an `EnvironmentPostProcessor`, added as the lowest-precedence property source. The app needs no MCP config of its own, yet anything it does set (in `application.yml`, a profile, an environment variable, or a `-D` property) still wins. The starter names the MCP server; it overrides nothing. The advertised version is DERIVED, not hardcoded: it is the runtime module's own Maven version, read from `tibyan/runtime-version.properties`, a resource filtered at build time, so what an MCP client sees in `serverInfo` is the version that actually produced the server. Note the `@project.version@` delimiters in that file: `spring-boot-starter-parent` sets `resource.delimiter` to `@` and disables the default `${...}` form, so the `${...}` spelling is copied through unsubstituted. If the resource is missing or unfiltered the runtime advertises `unknown` rather than a plausible but wrong number. The literal it replaced had gone stale, reading `0.1.0` through the 0.2.0 and 0.3.0 releases.

2. Manifest discovery without a property. The runtime finds the generated capabilities manifest by a small, fixed resolution order: an explicit `tibyan.capabilities` location if the host set one (still supported), then the conventional classpath location `classpath:/tibyan/capabilities.json`, then the conventional file location `file:tibyan/capabilities.json` relative to the working directory. The common path is to ship the manifest as a normal resource at `tibyan/capabilities.json`, where it is found with no property. An explicit `tibyan.capabilities` that resolves to nothing fails loudly rather than silently falling through, and finding no manifest at any location fails with a message listing everywhere it looked. The runtime exists to host generated tools, so finding none is a real misconfiguration, not a reason to start serving nothing.

So adopting Tibyan is: add the one dependency, and place the generated `tibyan/capabilities.json` on the classpath. Nothing in the app's own configuration changes.

The binding the `SpringExecutionAdapter` resolves is the real service method, discovered by the provider and carried in the IR as the opaque form `<Bean>#<method>` (Volume 5, section 4.1). The adapter splits that form, resolves the bean and method through the `ApplicationContext`, and invokes it reflectively at run time. It never depended on the binding being a convention: when the provider produced a convention and now that it produces a discovered fact, the adapter is identical, because both are the same opaque string. This keeps rule 3 below honest: the runtime is the only place that maps the opaque binding to a real call, and it maps whatever the IR carries, with no change to backends or the IR.

```java
@AutoConfiguration
public class TibyanAutoConfiguration {
    @Bean ToolRegistrar toolRegistrar(ApplicationContext ctx) { /* register generated tools */ }
    @Bean SpringExecutionAdapter executionAdapter(ApplicationContext ctx) { /* resolve and invoke beans */ }
    @Bean McpServer tibyanMcpServer(ToolRegistrar reg) { /* start MCP inside this context */ }
}
```

## 5. Lifecycle

```mermaid
sequenceDiagram
    participant App as Legacy app startup
    participant AC as Tibyan auto-config
    participant Reg as ToolRegistrar
    participant MCP as MCP server
    App->>AC: context refresh
    AC->>AC: discover target beans
    AC->>Reg: register generated tools
    Reg->>MCP: start server with tools
    MCP-->>App: ready, accepting agent calls
```

## 6. Future runtimes

| Runtime | Host | Note |
|---|---|---|
| Quarkus | A Quarkus application | Same role, different host (build-time DI) |
| Micronaut | A Micronaut application | Same role, compile-time injection |
| .NET | A .NET application | Crosses ecosystems; proves the runtime concept is not Java-only |

A new runtime is a peer that hosts the same generated layer. Backends do not change when a runtime is added (ADR-0006), because backends target the ExecutionAdapter contract, not a specific host.

## 7. Rules

1. The runtime hosts and binds; it contains no IR and no generation logic.
2. The runtime owns the ExecutionAdapter implementation for its host.
3. The runtime is the only place that knows how a capability's opaque binding maps to a real call.

## 8. Known gaps and deferred work (as of the MVP)

These are conscious gaps left at the end of the MVP (phases -1 through P3), recorded so they are not mistaken for oversights and so a later phase can close them deliberately. None of them block the MVP, which is verified end to end (Volume 2, section 5). The provider-side shape gaps have since been measured against a second real app and ranked by evidence (docs/experiments/second-app-trial.md); the phases that close them are Volume 13, Tier 2.

1. Binding is now discovered, not assumed (this gap is closed). A capability's binding was once synthesized by convention: `get<Entity>` bound to `<Entity>Service#findById`. It is now read from the real service method by the enabled ServiceExtractor, matched by shape, not by name (Volume 5, section 4.1): the provider finds the one `@Service` method that returns the entity and takes its id, and emits `<ServiceClassSimpleName>#<methodName>`. A service not named `<Entity>Service`, or a finder not named `findById`, is discovered correctly; a read with no backing method, or more than one, fails loudly with a located error rather than mis-binding. The runtime still resolves the same opaque `<Bean>#<method>` form, so the adapter is unchanged. What remains assumed and bounded: only single-id-parameter reads are discovered (lists, multi-argument finders, writes, and actions are not), the capability name is still the `get<Entity>` convention, and the identifier is the first `@Id` field. Those are recorded in Volume 5, section 4.1 as the next shape gaps to close. Pointing the tool at a second real app has since been done: the pms-backend trial confirmed and ranked these gaps by counted evidence, with zero mis-bindings observed (docs/experiments/second-app-trial.md; Volume 13, Tier 2).

2. App configuration is now just the one dependency (this gap is closed). The acceptance goal is that modernizing an app costs exactly one added dependency. The example app once also needed two things the starter did not supply: a launch property, `-Dtibyan.capabilities=...`, pointing at the generated manifest, and a small `spring.ai.mcp.server` block in its `application.yml` naming the MCP server. Both are gone. The starter now supplies the MCP server name and version as defaults through an `EnvironmentPostProcessor` (lowest precedence, so the app can still override), and the runtime discovers the manifest by a fixed order (explicit `tibyan.capabilities`, then `classpath:/tibyan/capabilities.json`, then `file:tibyan/capabilities.json`), failing loudly if none is found (Volume 7, section 4). The example app proves it: its `application.yml` has no MCP block, and it starts and serves getProduct over genuine MCP with no launch property.

The manifest is also no longer copied by hand. The `tibyan-maven-plugin` runs the existing pipeline during the app's build (bound to `generate-resources`) and writes `tibyan/capabilities.json` onto the classpath output. It invokes the same `TibyanCompiler` and `McpBackend` the CLI uses, so its output is byte-identical to `tibyan generate` by construction, not a second generator; the plugin module holds only Maven wiring. The example app no longer commits a manifest: its build generates one from its own source, and the end-to-end test asserts that build-generated manifest is byte-identical to the CLI output. Adopting Tibyan is now: add the dependency, add the build plugin once, build. What is still manual: configuring the build plugin once in the app's pom, and choosing when to regenerate (the plugin runs on every build, so the manifest tracks the source). Auto-discovery of the plugin without a pom entry is not attempted and is not planned for the MVP.

3. Coverage is read-only get-by-id only. The Entity and Service extractors are enabled (the Service extractor discovers the binding, Volume 5, section 4.1), so the IR holds entities and one read-only `get<Entity>` capability each. Lists, filtered queries, writes, and richer operations are not modeled; write and action capabilities are future IR (Volume 4, section 7). The second-app trial quantified this gap on a real app: services there return DTO and result-set types, and list and filter reads are the bulk of the real read API (Volume 13, Tier 2, phases E2 and E4). The CLI offers only the spring provider and the mcp backend, so a different source stack or output shape needs a new plugin.

4. Argument coercion across the MCP boundary. Arguments arrive from an MCP client as JSON, so a numeric id arrives as an Integer while the bound method expects a Long. The `SpringExecutionAdapter` coerces a small, fixed set of primitive cases (for example Integer to Long, text to BigDecimal) and no more. This is intentionally minimal for the MVP and will need a real, complete coercion strategy later, covering all primitive types, nullability, and clear failures on mismatch.

5. Plugin discovery is not real SPI yet. The pipeline composes plugins through their interfaces, and the CLI, as the composition root, injects the concrete Spring provider and MCP backend directly. The service-provider discovery described in Volume 10, section 2 is the target, not the current state: it would need a `name()` on the plugin interfaces plus a `META-INF/services` declaration in each plugin module. Deferred to the plugin-system phase. Recorded in Volume 10, section 2 so no one assumes discovery already exists.

6. Two materializations of one tool contract (a recorded decision, not a defect). The runtime registers tools at run time from the generated capabilities manifest, as dynamic tool callbacks. The backend also generates a tool class per capability, which is the materialization for a standalone generated project that is compiled and hosted on its own. Both forms honor exactly one contract: `tool(args) -> ExecutionAdapter.invoke(new CapabilityRef(name), args)`. This is the intended design: the manifest is the in-context hosting path, the generated class is the standalone path, and neither calls a service or framework directly.

7. Health, observability, and lifecycle hardening. Sections 3 and 4 list discovery, tool registration, bean access, and running the MCP server as the responsibilities exercised in the MVP. Health checks, observability (logs, metrics, and traces for tool calls), and lifecycle hardening (clean startup and shutdown ordering) are named in section 3 but not yet implemented. They remain later work, cross-referenced here so this gap list is complete.

8. Runtime invocation of a paged list read (opened by the phase E4 design, Volume 5, section 4.10; Volume 6, section 3.3). A list-all read whose bound method is the no-argument `findAll()` is fully executable today: the adapter invokes it and serializes the returned collection. A list-all read whose bound method takes a `Pageable` (the paged shape) needs the adapter to supply one, and what `Pageable` to supply (an unpaged default, or a first page) is a runtime paging decision the E4 design deliberately leaves out of scope. Until it is designed, the paged list capability is discovered, modeled with `outputMultiple` plus a `paged` disclosure, and rendered by both backends, but its live invocation is deferred. The honest interim, when the build lands, is that the adapter passes an unpaged or default `Pageable` and serializes the page content (never the envelope), recorded here so the boundary is explicit and not mistaken for a completed behavior.

9. Security enforcement of the declared authority contract (opened by phase Controller-Authority, Volume 5, section 4.14; Volume 6, sections 3.5 and 4.3; built 2026-07-19). The manifest may carry `requiredAuthorities` (a compiled any-of authority sequence the application declares for a read) or `securityUnverifiable` (a declared contract exists that could not be compiled). The runtime this phase DOES NOTHING with either field: it loads them tolerantly (the additive-field discipline, proven by a loader test) and does not authenticate callers, validate tokens, or check authorities. Enforcement is deliberately the consumer's layer, which is the requesting adopter's own architecture (an orchestration layer that enforces the declared contract before invoking the capability). Two facts are recorded so this boundary is not misread. First, the bypass fact that motivates the whole phase: in the surveyed real apps the declared security sits on the CONTROLLER methods, and the execution adapter invokes the SERVICE bean directly, so the application's own enforcement point is not on the invocation path the tool generates; carrying the declared contract in the manifest is what lets an external layer re-impose it. Second, the inverse case: a host application that declares method security ON ITS SERVICE beans (none of the three benchmarks does) would have Spring's method-security interceptor fire on the adapter's invocation too, and with no `Authentication` in the security context the call would be denied at runtime; if a real adopter hits this, the behavior is the app's own security working as declared, and the resolution (supplying an authenticated context to the adapter) is part of this same named deferral. The optional future enforcement hook, when evidence asks for it, is a runtime plugin that validates a supplied principal's granted authorities against `requiredAuthorities` before invoking, refusing unverifiable capabilities by default; it enters through its own designed phase, never silently.

10. Attested capabilities at the runtime (opened by phase Attestation, Volume 5, section 4.15; designed 2026-07-20, pending ratification). Invocation of an attested capability is IDENTICAL to a verified one, argued rather than assumed: the binding is a structurally resolved `<Bean>#<method>` like any other (attestation fills semantic holes, never structural ones, so there is nothing different to invoke), and a runtime that behaved differently would imply the tool doubts what its labels state exactly. What the runtime adds is one consumer control and one tolerance: the loader tolerates the additive `attested`/`attestation` manifest fields (the standing additive-field discipline, with a loader test at build), and a property, `tibyan.attested.expose` (default `true`), lets a consumer exclude attested capabilities from registration wholesale, for surfaces that must be verified-only. Default true because committing `tibyan/attestations.json` through review IS the adopter's deliberate opt-in; the property serves the consumer who did not make that choice. Two named non-goals this phase: no per-call runtime warning (the labeling is the honesty mechanism, and it is complete), and no principal-passing for the context-dependent reads (the me-scoped `RequestContext` reads stay excluded from attestation entirely until a principal design exists; that future design is invocation architecture and would be its own ratified phase).

## 9. EI-R: runtime argument binding and closed-set enforcement (phase Enum Inputs; designed 2026-08-04, RATIFIED and BUILT 2026-08-05)

A LATENT DEFECT, found while designing the enum-input phase and recorded here because it is that phase's
blocking precondition rather than a detail of it. `SpringExecutionAdapter` resolves the target method with
`bean.getClass().getMethod(methodName, parameterTypes)`, where each entry of `parameterTypes` comes from
`IrTypes.toJavaClass(input.type())`. A STRING input yields `String.class`, but the real signature of a
filtered read takes a domain enum (`CostCategory`), so the lookup throws `NoSuchMethodException` and the
capability is UNINVOKABLE. TIMESTAMP has the same problem: it yields `Instant.class` against a real
`LocalDate` parameter.

Why it has never been observed: every capability shipped to date has exactly one identifier-typed input.
Verified against the current IR of both public benchmarks, every discovered pms input is LONG and every
discovered erp input is UUID. The defect is real, latent, and NOT enum-specific.

EI-R, the fix, ratified as a build step that PRECEDES the enum-input build:

1. Resolve the target method by NAME AND ARITY rather than by reconstructed parameter types. When more than
   one same-arity overload matches, REFUSE LOUDLY with a located error naming the candidates. The tool never
   guesses which overload a capability binds to.
2. Convert each incoming argument to that method's DECLARED parameter type, read by reflection: the existing
   primitive coercions, the `java.time` types, and enums.
3. For an enum-typed parameter, match the incoming value by the SAME rule the provider used to advertise the
   set (Volume 5, section 4.16.4): the constant name for a bare enum, the `@JsonValue` literal for a
   value-mapped enum. The two must agree by construction, because a set advertised by one rule and matched by
   another is the MIS-CONSTRAINED failure.
4. VALIDATE an incoming value against the carried set BEFORE invoking, refusing with a located error that
   names the legal values. This is the enforcement the schema `enum` promises, and it is the reason the
   backends may render it at all.

Invariant for the EI-R build: every existing capability invokes identically, proven by the unchanged
end-to-end test and the unchanged example-app goldens. If EI-R proves larger than one session, the honest
outcome is that enum inputs do not ship, not that they ship unenforced.

### 9.1 EI-R actual: built and measured (2026-08-05)

The ratified EI-R (section 9) implemented exactly. RUNTIME ONLY: `tibyan-core`, the provider, and both
backends are untouched, so generated output cannot move and did not. `mvn clean verify` green across 12
modules.

WHAT WAS BUILT, item by item against the ratified list:

1. Method resolution by NAME AND ARITY, replacing the reconstructed-parameter-type lookup. Bridge and
   synthetic methods are skipped, which the design did not anticipate and which is load bearing: javac gives
   a generic interface implementation an erased bridge of the same name and arity, and without the skip an
   ordinary `Finder<String>` would have refused as ambiguous. Two GENUINE same-arity overloads refuse loudly
   and name the candidates.
2. Arguments converted into the resolved method's DECLARED parameter types, including the `java.time` types
   and enums, with primitive parameters boxed and a missing value for a primitive refused with a located
   message rather than a reflective failure.
3. Enum matching by the SAME wire-value rule the provider will use to advertise, implemented in a new
   `EnumWireValues`: constant names for a bare enum (shape a), the `@JsonValue` literals for a value-mapped
   enum (shape b), and a fallback to constant names when more than one `@JsonValue` accessor makes the shape
   ambiguous (shape c), which is safe precisely because the provider advertises nothing in that case. The
   `@JsonValue` annotation is matched BY TYPE NAME, so Jackson does not become a dependency of the runtime
   module and an application without Jackson simply takes shape (a). The annotation's optional boolean is
   honoured rather than assumed.
4. An illegal value refused BEFORE invoking, with the legal values named in the message. This is the
   enforcement a schema closed set promises, and it now exists.

A SMALL CLEANUP recorded rather than left silent: `IrTypes.toJavaClass` had exactly one caller, the defect
site, and is removed. `IrTypes.jsonSchemaType` is untouched and still drives the tool input schema.

MEASURED. All 27 runtime tests green, including the three pre-existing `SpringExecutionAdapterTest` cases
that are the regression guard for the ratified invariant "every existing capability invokes identically";
the end-to-end MCP test and the example-app goldens are unchanged. Both public benchmarks are BYTE-IDENTICAL
on the serialized IR, the generated MCP output and the generated OpenAPI document, verified by `diff` with
zero differences, and their refusal-cause ledgers are UNCHANGED, which is the correct outcome for a
runtime-only step: a provider refusal cannot move when the provider did not change.

WHAT THIS STEP DELIBERATELY DOES NOT DO. It renders no closed set on any surface, because that is EI-1 and
because the ratified sequencing rule forbids rendering before this step exists, not after. Nothing in the
IR carries a value set yet, so the runtime's enforcement currently derives the legal values from the
DECLARED ENUM ITSELF at invocation time. That is the same set EI-1 will advertise, by construction, which
is the whole point of implementing one rule in one place.

Fixtures EI-R1 to EI-R11 (`EnumInputRuntimeBindingTest`): a bare enum bound by constant name; a value-mapped
enum bound by its wire literal; a value-mapped enum REFUSING the constant name, which is the MIS-CONSTRAINED
guard, since accepting it would mean accepting a value outside the advertised set; an illegal value refused
with the legal values named; a `LocalDate` bound from ISO text; a mixed-arity read binding every parameter
to its declared type; two genuine overloads refusing; a bridge method NOT creating a false ambiguity; an
unknown method naming the arity; the legal-value set read from the enum and never synthesized, with a
non-enum carrying none; and a CHARACTER-EXACT replica of the real pms `CostCategory` declaration proving all
eight literals in declaration order, that `renovation` matches, and that `RENOVATION` does not.

## 10. Per-capability attested exposure (phase Reviewed Exposure; designed 2026-09-20, RATIFIED 2026-09-23, build pending)

STATUS: RATIFIED 2026-09-23 AS WRITTEN, NOT BUILT. The maintainer ratified this section on 2026-09-23 without
amendment, so the five decisions of section 10.9 stand at their recommended values, recorded one by one in
section 10.10. Under rule 12 this frozen text is committed before any code, in a session that built nothing.
The build is a separate later session (one phase per session). It OPENS with the maintainer's discussion, whose
subject the maintainer has not yet stated, then re-reads sections 10.9 and 10.10; any amendment to this section
that the discussion produces is recorded in section 10.10, dated, before the first line of code, never during
the build.

### 10.1 The evidence

Candidate 1 of the first real attestation run (Volume 13, section 3; docs/experiments/as-backend-trial.md
section 9.4, finding F1), ranked first because it is the only finding where the design pushed a real adopter
into a strictly LESS safe configuration than the one they wanted.

Measured on 2026-09-20 (ledger section 9.8): all 15 attested capabilities on as-backend print
`unverifiable (conflicting-declarations)`. Under the ratified G8 default (Volume 5, section 4.15.7) none of
them registers. The adopter had read and confirmed each of the fifteen by name, wanted those fifteen and
nothing else, and the only control available was `tibyan.attested.exposeSecurityUnverifiable=true`, which is
capability-wide: it opens every security-unverifiable attested capability in the service, including ones
attested next month by someone else and never reviewed for exposure. Verified in the adopter repository: the
flag is set true in `application-local.properties` of both hosting services, and only there. Two facts
follow from that one observation, and the design rests on both: the adopter's review was per capability while
the control was per service, and the adopter treated exposure as a per-ENVIRONMENT decision.

On this application today the two answers coincide, since every attested capability is unverifiable and all
fifteen were reviewed. The defect is what happens at the sixteenth.

### 10.2 What is NOT reopened

- The refusal. The authority is declared on the controller and the tool binds to the service method beneath
  it, so the adapter bypasses the declared gate (section 8, gap 9). `securityUnverifiable` is the true
  verdict and it stays on the capability, in the manifest and on both surfaces, whether or not the
  capability is exposed. Exposure by review never clears it, never rewrites a description, and never turns
  an unverifiable contract into a verified one.
- The G8 default. With no configuration, a security-unverifiable attested capability does not register.
- The wholesale control `tibyan.attested.expose` and the boundary control `tibyan.attested.exposeBoundary`.
- The provider, the IR, the core, and both backends. This phase is RUNTIME ONLY.

### 10.3 The rule

One new runtime property, `tibyan.attested.exposeReviewed`, a list of capability names.

An attested, security-unverifiable capability registers if and only if the wholesale control is on, the
boundary control admits it where it is a boundary capability, AND EITHER the global
`exposeSecurityUnverifiable` is true OR the capability's exact name is in `exposeReviewed`.

Conditions, all of which must hold for a list entry to be honoured:

- X1, EXACT NAMES ONLY. An entry is the full minted capability name (`attested_citiesByFilters`), matched
  case-sensitively and whole. No wildcard, no prefix, no pattern, no "all". A pattern that matches a
  capability attested tomorrow is the global switch again under another spelling, which is the defect this
  phase exists to remove. Opening fifteen reads means writing fifteen names.
- X2, EVERY ENTRY MUST RESOLVE. Each listed name must be a capability present in the loaded manifest. An
  unknown name fails startup with a located error naming the entry. This is what keeps the list honest over
  time, and it closes a loop by construction: when the attested code changes, the fingerprint no longer
  matches, the scan refuses the entry as STALE_ATTESTATION, the capability leaves the manifest, and the
  exposure entry that named it now fails loud instead of silently waiting to re-open whatever takes that
  name later.
- X3, EVERY ENTRY MUST BE NEEDED. Each listed name must be attested AND security-unverifiable. A name that is
  verified, or attested with a compiled or absent contract, registers without the list, so an entry for it
  records a review stake that does not exist; it fails startup the same way. A list that may contain inert
  entries is a list nobody can audit.
- X4, THE LIST NEVER WIDENS ANOTHER CONTROL. With `tibyan.attested.expose=false` nothing attested registers,
  listed or not. A boundary capability still requires `exposeBoundary=true`; the list is not a substitute
  for it and there is no per-capability boundary list, because no real use of the boundary category exists
  yet to design one from (Volume 13, the open verification).
- X5, NO DUPLICATES, NO BLANKS. A blank or repeated entry fails startup. It costs nothing and it keeps the
  list a set of deliberate acts.

### 10.4 Why a runtime property, and not the attestation record

The alternative is a field on the entry in `tibyan/attestations.json`, carried through the IR into the
manifest. It is refused, for three reasons.

1. Exposure is a DEPLOYMENT decision and the record is a BUILD artifact. The one real adopter opened these
   reads in a local profile and nowhere else. One manifest ships to every environment; only configuration
   can say "here, but not in production".
2. It would be IR growth under rule 5 with no claim on the IR's purpose. The IR says what a capability is.
   Whether one host chooses to serve it is not a fact about the capability.
3. It would mix two acts that section 4.15.2.3 guard 1 already teaches the project to keep apart: attesting
   what a read means, and signing for a security stake. A per-entry flag invites confirming both in one
   keystroke.

The cost, stated rather than hidden: a property carries no provenance. It does not say who reviewed the
capability or when. The host's own version control is the only record of that. Decision D3 asks whether that
is acceptable.

### 10.5 What "reviewed" does and does not mean

Naming a capability in the list is the adopter asserting one thing: that they have looked at what the
declared gate on the fronting controller protects, and accept this read being served with that gate
bypassed, in this environment. The tool verifies none of that and says so. The list is not a verification,
it is a narrower opt-in. Its whole value is its width: the blast radius of the opt-in equals the set of
names a person wrote down.

THE HONEST RESIDUE. The fingerprinted slice covers the target body, imports, the repository field and
interface, and the return type declaration. It does not cover the fronting controller. If the controller's
declared authority changes, the capability name stays listed and stays open, provided the verdict is still
unverifiable. Decision D2 asks whether to pin the reviewed verdict as well as the name.

### 10.6 Startup honesty, and one incidental defect

On startup the runtime logs, once: the names registered; and the names excluded, each with its reason
(`attested-closed`, `boundary-closed`, `security-unverifiable-not-reviewed`). When the global
`exposeSecurityUnverifiable` is true the runtime logs a WARN naming every capability it opened that is NOT in
`exposeReviewed`, since those are exactly the ones nobody named.

INCIDENTAL DEFECT FOUND WHILE DESIGNING, recorded and not fixed here: `TibyanAutoConfiguration` logs
"registered N MCP tool(s)" with the correct count from the callbacks but takes the NAME list from
`manifest.tools()`, so the line names every capability in the manifest, excluded ones included. An operator
reading that line today is told a closed capability is registered. The count is right and the names are
wrong. It changes no registration outcome, so it is a rule-13 diagnostic amendment, and it belongs to this
phase's build because this phase is what makes the exclusion set worth reading.

### 10.7 Pre-registered fixtures (RX-1 to RX-10)

- RX-1, the positive: two unverifiable attested capabilities, one listed, global false: exactly the listed
  one registers, and verified tools are untouched.
- RX-2, the default holds: no list, global false: neither registers (the existing G8 test, unchanged).
- RX-3, the global flag still works and is unchanged: global true, no list: both register, with the WARN
  naming both.
- RX-4, the primary safety guard: a listed name does not open a SECOND, unlisted unverifiable capability.
- RX-5, no patterns: `attested_*`, a bare prefix, and a wrong-case name each fail startup as unknown names;
  none of them opens anything.
- RX-6, X2: an unknown name fails startup with the entry named.
- RX-7, X3: a listed verified capability fails startup; a listed attested capability whose contract is not
  unverifiable fails startup.
- RX-8, X4: `expose=false` with a populated list registers nothing attested; a listed boundary capability
  with `exposeBoundary=false` does not register.
- RX-9, X5: a blank and a duplicate entry each fail startup.
- RX-10, binding forms: the comma-separated property form and the YAML list form bind to the same set. This
  is a fixture because `@Value` does not read a YAML list, so the build must bind through the Spring
  `Binder` rather than the way the three existing booleans are read.

### 10.8 Forecast

Runtime only, so the serialized IR, the generated MCP output and the generated OpenAPI document are
BYTE-IDENTICAL on every benchmark BY CONSTRUCTION, and discovered, attested, mis-bound, attestation-drift and
mis-constrained cannot move. Any movement is a regression finding.

as-backend: with the fifteen names listed per hosting service and the global flag removed, the registered
set is the same set as today. THE MEASURED GAIN TODAY IS ZERO, and it should be reported as zero. What
changes is the sixteenth capability: today it opens on the day it is attested, after this phase it does not
until someone names it. pms and erp carry no record file and are inert.

THE IDIOM CAVEAT, carried from the ledger: every attested capability here is unverifiable because this
author's applications declare authority on controllers and bind tools to services, and all three available
applications share that foundation. Whether that is general is not settled by one app. The phase is still
justified on the narrower ground that a security opt-in whose only granularity is "everything" is wrong for
any adopter who has even one unverifiable capability they did not review.

### 10.9 Open decisions for the maintainer, each with a recommendation

- D1, FAIL STARTUP OR WARN on an X2, X3 or X5 violation. Recommendation: FAIL STARTUP. It is the project's
  own discipline, the fix is a one-line edit, and a warning in a startup log is where a rotting security
  list goes to be ignored. The cost is real and is stated: a regenerated manifest can stop a host from
  starting until its list is corrected. That is the intended behaviour, and it is also the strongest
  argument against it.
- D2, PIN THE VERDICT AS WELL AS THE NAME (`name` against `name:conflicting-declarations`). Recommendation:
  NAME ONLY for this phase, with the residue of section 10.5 recorded. The closed reason set is coarse, so
  pinning it would catch few real authority changes while making every entry longer and more fragile; the
  honest fix for that residue is fingerprinting the fronting declaration, which is a provider phase with its
  own evidence bar.
- D3, PROVENANCE. Recommendation: ACCEPT A BARE LIST, and say in docs/SECURITY.md that the host's version
  control is the record of who opened what. A structured form (name plus reviewer plus date) is more
  ceremony than one adopter's evidence supports.
- D4, THE GLOBAL FLAG. Recommendation: KEEP IT, unchanged in meaning and default, with the WARN of section
  10.6. Removing or deprecating it is a breaking change to a published 0.3.0 property on the evidence of one
  application.
- D5, THE PROPERTY NAME. Recommendation: `tibyan.attested.exposeReviewed`, beside the three controls it
  joins. `Reviewed` names the act the adopter is performing, which `allow` or `names` would not.

### 10.10 Ratification record (2026-09-23)

Ratified by the maintainer on 2026-09-23, as written, in a session that built nothing. Rule 12 is the reason
for the separate commit: the frozen design lands before implementation begins, so the build implements the
ratified rule rather than the rule drifting to fit the build.

WHAT IS RATIFIED: the runtime-only scope and the not-reopened list of section 10.2; the rule and conditions X1
to X5 of section 10.3; the refusal of the record-field alternative in section 10.4; the meaning of "reviewed"
and its honest residue in section 10.5; the startup logging of section 10.6, with the incidental defect
recorded there fixed in the build as a rule-13 diagnostic amendment; fixtures RX-1 to RX-10 as the build's
pre-registered tests; and the forecast of section 10.8, a measured gain of zero today and byte-identical
benchmarks by construction.

THE FIVE DECISIONS of section 10.9, each standing at its recommendation because the section was ratified as
written, none separately chosen:

- D1: an X2, X3 or X5 violation FAILS STARTUP with a located error naming the entry.
- D2: the list pins the NAME ONLY. The residue of section 10.5 stands recorded; fingerprinting the fronting
  declaration is a provider phase with its own evidence bar, not this one.
- D3: a BARE LIST, no provenance fields. The build adds one sentence to docs/SECURITY.md: the host's version
  control is the record of who opened what, and when.
- D4: the global flag `tibyan.attested.exposeSecurityUnverifiable` is KEPT, unchanged in meaning and default,
  and gains the WARN of section 10.6.
- D5: the property is `tibyan.attested.exposeReviewed`.

THE OPENING OF THE BUILD SESSION. The maintainer has things to discuss before the build starts; what they are
is not recorded here. The build session therefore begins with that discussion, then with sections 10.9 and
10.10, and either confirms the five values above or records an amendment here, dated, before code. A decision changed after code has started is exactly the drift
rule 12 exists to prevent.

THE EXIT CRITERION, restated from sections 10.6 to 10.9 so the build session knows when it is done, adding
nothing to them: RX-1 to RX-10 green as tests in the runtime module; the startup line of section 10.6 names
only registered capabilities, with the fix flagged as a rule-13 amendment in that session's summary;
docs/SECURITY.md carrying the D3 sentence; every benchmark byte-identical across serialized IR, generated MCP
output and generated OpenAPI document against a worktree at the pre-build commit, with the five counts of
section 10.8 unmoved; and on as-backend, with the fifteen names listed per hosting service and the global flag removed from the local
profile, the same fifteen registered, which is the forecast gain of zero reported as zero.

## 11. The write gate and the reviewed tier at the runtime (phase DL-1; designed 2026-09-23, PENDING RATIFICATION)

Designed in Volume 5, section 4.17.6. A reviewed READ registers when `tibyan.reviewed.expose` is true (default
true, decision D2). A WRITE or ACTION registers only when its exact name is in `tibyan.writes.allow`, a list under
section 10's five conditions, default empty: the write gate. Section 4.14's security composition applies to
reviewed capabilities, so a security-unverifiable one does not register by default; which exact-name list opens it
is decision D1 of section 4.17.11, which proposes amending section 10 D5 to one list for any tier before section 10
is built. Tool hints (`readOnlyHint`, `destructiveHint`) are carried if the pinned stack can carry them; the pinned
MCP SDK jar shows no annotation class, so the build verifies and, failing that, records the gap and relies on the
name prefix and the first sentence. Startup names every write it did not register with `write-not-allowed`. Not
built by name: dry-run and an in-runtime confirmation step. Not ratified, not built.
