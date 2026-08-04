# Experiment: the private enterprise benchmark (redacted)

Privacy (absolute, permanent, this and every future session): this is a real, large, multi-module Spring
application, adopted 2026-07-12 as a permanent third benchmark, the first external-adopter-scale app. It
is PRIVATE. This ledger records ONLY structural counts and shape categories, never the application's
name, domain, module names, entity names, or any code. Generic names (CrmNote, SupportNote) stand in
for real entities where an example is needed. The two earlier benchmarks (pms-backend, erp-backend) have
their own ledgers; this one is redacted the same way the apps are private, only more strictly.

---

## 1. Structural profile (counts only)

- Modules: 169. Java files: 1587. `@Entity` classes: 111. `@Service` classes: 158. Parse failures: 0
  (the codebase parsed 100 percent clean at the Java 17 language level; no `$IMPORT`-style placeholder
  corruption, unlike pms-backend).
- Architecture shape: multi-module, bounded-context (domain-driven). The same simple entity name is
  legitimately reused across contexts, each mapped to its own module and its own table.

## 2. Survey result (first run, 2026-07-12, counts only)

- DISCOVERED: 4 (all read-by-id). REFUSED: 107 (NO_BACKING_METHOD 96, NO_IDENTIFIER 10,
  AMBIGUOUS_BINDING 1). DEFERRED: 31. PARTIAL: 6. SKIPPED-BY-PARSE: 0. MIS-BOUND: 0.
- GENERATION: BLOCKED. `scan --out` refused to write the IR because validation failed on 3 duplicate
  simple entity names (across 6 entities). No IR, no MCP server, no OpenAPI document for the entire
  application. This is the only total, output-blocking failure the benchmark surfaced.
- mis-bound 0 confirmed adversarially: all 4 discovered bindings and the 1 AMBIGUOUS refusal were
  independently re-verified against source (each correctly bound by primary key to the right entity, or
  correctly refused). The core never-wrong guarantee held at this scale.

## 3. The collisions (counts and shapes only)

- 3 simple names collide, each declared by 2 distinct entity classes, in 2 distinct modules, mapped to 2
  distinct tables: 6 entities of 111. Every collision is legitimate bounded-context reuse (distinct fields,
  distinct tables, distinct meaning), not an accidental duplicate. Generic illustration: a `Note` in a
  crm context and a different `Note` in a support context.
- This is the evidence for phase Entity Identity (Volume 4 section 3.7, Volume 5 section 4.12). It is not
  a discovery gap and not a mis-bind: the survey classified all 111 entities correctly with mis-bound 0;
  simple-name keying simply cannot represent the model, so nothing is emitted.

## 4. Entity Identity forecast (BLOCKED to EMITTED)

- With phase Entity Identity, the 3 colliding simple names (6 entities) receive deterministic
  disambiguated display names derived from structural package context (generically CrmNote and
  SupportNote), so both validation rules pass, the IR writes, and both backends generate.
- Row (projected): IR moves from BLOCKED to EMITTED. DISCOVERED STAYS 4. This phase grants identity and
  emission, NOT discovery: raising the discovered count is the separate, ranked-second discovery-gap
  phase. So this is not a coverage win. The win is that the tool can emit output for a real multi-module
  application at all, from zero emitted to 4 capabilities plus the full 111-entity model rendered as
  OpenAPI schemas. mis-bound 0, and the latent simple-name-collapse mis-bind (which would have merged two
  distinct entities) is removed. Caveat (the design's honest failure boundary): the phase does not
  guarantee EMITTED for every colliding app. If a disambiguated name cannot be expanded to a globally
  unique readable name (a residual display collision), that app stays IR-blocked with a precise located
  error instead of the generic duplicate-name failure. This benchmark's 3 collisions have cleanly
  diverging packages with no residual clash, so it moves to EMITTED; the build confirms.
- Read this correctly: the headline is emission, not coverage. Discovered 4 before, discovered 4 after;
  the difference is BLOCKED before, EMITTED after. Any reader who sees "discovered 4" and calls the phase
  a small win has misread it: the phase turns a totally unusable result into a usable one.

## 4a. Entity Identity actual (BLOCKED to EMITTED, built and measured, 2026-07-13)

The phase is built (Volume 5 section 4.12) and re-run against this benchmark. The forecast is confirmed;
the transition is measured on both the pre-phase commit and the current build (counts and shapes only,
the app stays private).

- Pre-phase (measured): 111 entities, 4 discovered, validation FAILED with exactly 3 duplicate-name
  errors, so NO output was produced. This is BLOCKED, reproduced precisely: the 3 errors are the 3
  colliding simple names (6 entities) the survey found.
- Current build (measured): 111 entities, 4 discovered (by-id 4, list 0), failed 107 (NO_IDENTIFIER 10,
  NO_BACKING_METHOD 96, AMBIGUOUS_BINDING 1), mis-bound 0, validation PASSES, and both backends generate
  (MCP manifest plus tool sources, and the OpenAPI document rendering all 111 entities as schemas). This
  is EMITTED. The 3 collisions have cleanly diverging packages, so the batch fixpoint disambiguated them
  with no residual clash, exactly as forecast; no app-level residual-collision fail-loud occurred.
- DISCOVERED STAYS 4, confirmed. The phase granted emission and identity, not discovery. Of the 4
  discovered reads, 2 are entity-returning (section 4.1) and 2 are view-returning (section 4.2); both
  key-resolution paths were exercised on the real app, and the tool's mis-bound counter is 0.
- mis-bound 0 hand-verified. Each discovered binding resolves its return type (or its load repository's
  entity) to the intent entity by key, so a wrong binding is structurally impossible; the EI-6 fixture
  proves the same guard in isolation (a finder of one context's entity never binds the other). The latent
  simple-name-collapse mis-bind is removed: two distinct same-named entities can no longer merge.

## 5. What this benchmark ranked next (counts and shapes only), recorded for the roadmap

This app produced the evidence that ranked the post-v1 build order (Volume 13). Recorded here as counts
and shape categories so the ranking is auditable without the source.

- RANK 1 (this phase, Entity Identity): the only total, output-blocking failure, and a prerequisite for
  every other win (nothing downstream matters while the model cannot be emitted). Structural, not
  idiom-bound: simple-name collision is the default reality of any bounded-context multi-module app.
- RANK 2 (separate, ranked-second, NOT this phase): discovery-gap rules. Of 16 sampled aggregate-root
  entities, 13 were discovery gaps (a real read-by-id exists that the current rules miss) and 3 were
  honest refusals; and all 10 NO_IDENTIFIER refusals are discovery gaps (the `@Id` is inherited from a
  parent the rules do not follow). Recurring defeating-shape categories: (a) the by-primary-key load is
  pulled into a private same-class helper the matcher does not step into; (b) the service is typed against
  a bodiless port interface whose implementation lives in another module, so there is no
  interface-to-implementation resolution across the module boundary; (c) the `@Id` is inherited via a
  `@MappedSuperclass` or `@Inheritance(JOINED)` parent one `extends` hop away. A fourth family
  (aggregate-enrichment reads: a clean by-primary-key load plus a second repository call plus a
  multi-argument composite mapper) belongs to Attestation, not to a new discovery rule. These shapes are
  idiom-bound to a private foundation library; whether they generalize is not yet proven.
- RANK 3 (deferred, trigger fired but weak): Attestation. The filter/wrapper ceiling was hit, but weakly:
  of 31 deferred reads, only 2 are real user-facing query capabilities (a filtered search and a
  version-list read); the other 29 are event-handler enums, notification inserts, and request-context
  accessors, all correctly refused. The ratified Attestation trigger technically fired but is
  out-prioritized: an adopter cannot meaningfully hit the filter ceiling of a tool that cannot emit a
  single capability for their app.

This is the redacted evidence base; the ranked-build decision it drove is recorded in Volume 13.

## 6. Discovery-Gap classification and forecast (counts and shapes only, 2026-07-13)

Phase Discovery-Gap (RANK 2) was designed here (Volume 5, section 4.13). Its mandatory first step was an
honest classification of the three recurring shapes as general (a standard Spring/JPA pattern worth a
rule) or foundation-specific (fires only because of this app's private foundation conventions, worth much
less). The classification was made from a full read-only census of this benchmark's by-id reads, cross-
checked against the two public benchmarks. Recorded here by counts and shapes only.

### 6.1 The by-id read census (39 in-scope reads)

The application's single-id by-id reads were fully enumerated (not sampled): 39 in-scope reads, in these
structural categories.

- 5 CLEAN-DIRECT: load directly in the public body (framework `findById`, or an in-tree repository-port
  `selectById` that is the already-handled section 4.2 condition 4b one-hop) plus at most one in-tree
  mapper call, no guard, no second access. These already fall under sections 4.1 and 4.2. They span 4
  entities; one entity has two competing view reads, so it is a correct AMBIGUOUS_BINDING (a future
  named-reads question, not a discovery gap), which is why 4 capabilities emit from 5 clean reads.
- 6 SOFT-DELETE-GUARDED: single persistence access, but a post-load existence guard
  (`if (x.getIsDeleted()) throw NOT_FOUND`) sits between load and return. 3 are true private-same-class
  helper hops (the load is in a private `getX`/`validateX` helper carrying the guard); 3 are direct
  loads with the same guard inline. All 6 are correctly refused: the guard is an existence filter, so
  presenting the read as a plain by-id read would misstate what it returns. The guard is pervasive
  (roughly 59 occurrences across service bodies).
- 12 MULTI-ACCESS (aggregate enrichment): a load plus one or more further repository or proxy accesses
  feeding a multi-argument composite mapper. Attestation territory, not a structural discovery rule.
- 15 SERVICE-OR-FEIGN DELEGATION: no local load; the body forwards to a Feign client or another service
  (a twin-proxy-per-capability pattern inflates this bucket). Out of scope; there is no local read to
  bind.
- 1 STUB: a public read that constructs a hardcoded view with literal values, no persistence access.

Separately, the 10 NO_IDENTIFIER entities are `@Entity` children with no direct `@Id` that inherit a
single `@Id` from an in-tree base one `extends` hop away: 4 via an `@MappedSuperclass`, 6 via an
`@Inheritance(JOINED)` parent. No composite parent, no not-in-tree parent, no ambiguous inheritance.

### 6.2 The classification (general versus foundation-specific)

- Inherited `@Id` (shape c): GENERAL. `@MappedSuperclass` and `@Inheritance(JOINED)` id inheritance are
  core JPA, and here the 3 bases are in-tree in the app's own model modules, not in the foundation
  library. Worth a rule.
- Private-helper hop (shape a): MIXED. The general core, stepping through one private same-class
  delegation to a clean by-primary-key load under section 4.2's disciplines, is worth a rule. What is
  incidental and decisive here is the pervasive soft-delete guard: the clean, unguarded helper-hop count
  is 0, so the general rule earns nothing on this app. Refusing the guarded instances is correct (the
  tool cannot tell a benign soft-delete guard from an authorization or business filter without
  interpreting it, the same lesson as P1b's inner-fetch-is-a-filter).
- Port-to-implementation (shape b): NOT a general gap; the "across the module boundary" framing is a
  mischaracterization. The census found 81 repository ports, 80 with an in-tree `@Repository`
  implementation (56 carrying a verbatim by-primary-key delegation), 0 with an implementation outside the
  scanned source; and 131 service-port reads the extractor already reaches by scanning `@Service` classes
  directly. The "another module" is an intra-application Maven module (api, repository, core, model), not
  a foundation jar; the provider parses all app modules into one unit set, so a module boundary blocks no
  resolution. The only genuine version, a port whose implementation ships in an unscanned binary
  dependency, has count 0 here and is unreachable by a source-only tool anyway. No new rule.

### 6.3 Forecast and the headline finding

Row (projected, build deferred): the general rules unlock ZERO new discovered capabilities. Inherited
`@Id` recovers the identifier for all 10 NO_IDENTIFIER entities, turning each into a read intent, but all
10 are internal polymorphic-subtype rows (per-channel notification-log subtypes, event-method-config
subtypes, order-line-item subtypes) with no clean backing by-id read, so all 10 become NO_BACKING_METHOD:
a cause relabel (NO_IDENTIFIER 10 to 0, NO_BACKING_METHOD 96 to 106), not a discovery. The helper-hop rule
reaches the 3 true private hops, all soft-delete-guarded, so all are refused. Port-to-implementation adds
nothing. Net: discovered 4 to 4, mis-bound 0.

The headline, stated plainly because finding it was the point: the gap between the un-discovered reads and
the zero a safe general rule reaches is the result. Of the 39 by-id reads, 5 are already handled (one an
ambiguous pair), 6 are correctly refused soft-delete-guarded reads, 12 are aggregate-enrichment
(Attestation), 15 are delegation (out of scope), 1 is a stub; and the 10 NO_IDENTIFIER are internal rows
with no read. Almost all of this application's un-discovered reads depend on its own foundation idioms
(the pervasive soft-delete guard, the aggregate and twin-proxy patterns) or are internal non-read
entities. A safe general rule reaches none of them here. This is a real measurement of the tool's reach on
a foundation-heavy codebase: the general structural rules are correct but inert here, and the remaining
user-facing coverage lives in Attestation (the guarded and aggregate reads), not in more discovery rules.
The build is deferred on the P1b precedent (a rule ratified for its boundary, built when an app exists on
which it discovers).

## 6.4 Discovery-Gap actual (built and measured, 2026-07-13, counts and shapes only)

The phase was built, general scope only (the human elected to build rather than defer; the rules are
unchanged). The forecast held to the number.

- Row (actual): discovered 4 (by-id 4, list 0), UNCHANGED, exactly as forecast. Failed 107, now
  NO_BACKING_METHOD 106 and AMBIGUOUS_BINDING 1, with NO_IDENTIFIER 0. mis-bound 0. The forecast cause
  relabel is confirmed to the number: NO_IDENTIFIER 10 to 0, NO_BACKING_METHOD 96 to 106.
- Inherited `@Id` fired on all 10 and resolved all 10: every one of the 10 entities that had been reported
  NO_IDENTIFIER inherits a single, readable, in-tree `@Id`, so each now forms a read intent. None of the
  three refusals was needed (no not-in-tree parent, no inherited composite key, no ambiguous hierarchy),
  which confirms the census. All 10 then failed NO_BACKING_METHOD, because they are internal
  polymorphic-subtype rows with no by-id read. The rule is now telling the truth about them (they have an
  identifier; what they lack is a read) instead of wrongly reporting that they have no identifier. It
  discovered nothing, exactly as forecast.
- The private-helper hop fired on all 3 true hops and refused all 3: each was reached through the private
  helper and refused on its post-load soft-delete guard, with the extra-statement message. This is the
  designed outcome, verified live: the guard, not the hop, is the ceiling. It discovered nothing, exactly
  as forecast.
- The foundation-specific gaps stay refused. Nothing in the aggregate-enrichment, delegation, guarded, or
  stub categories became a capability. Discovered above 4 would have meant a general rule overreached into
  foundation-specific territory; it did not.
- mis-bound 0, hand-verified, with the riskiest hop scrutinized. The generated output (MCP manifest and
  tool sources, and the OpenAPI document) is BYTE-IDENTICAL to the pre-build output, so the 4 discovered
  bindings are the same 4: nothing was added and no binding moved. No inherited-`@Id` trace bound anything
  at all (all 10 resolved entities failed NO_BACKING, so no capability derives from the inheritance walk,
  and no wrong parent's id could be bound), and no helper-hop trace bound anything (all 3 refused). No
  interface-to-implementation trace exists, because the phase implements no such rule.

The headline is confirmed by measurement, not argument: on this benchmark the general rules discover ZERO
new capabilities. What the build changed is honesty, not coverage: 10 entities are no longer mislabeled as
having no identifier, and 3 guarded reads now say precisely why they are refused. The remaining
user-facing coverage (the guarded and aggregate-enrichment reads) is Attestation territory, not more
discovery rules.

## 7. Controller-Authority census and forecast (counts and shapes only, 2026-07-19)

Phase Controller-Authority (Volume 5, section 4.14; the first real-user-requested phase) was designed
from a full read-only census of the three benchmarks. This section records this benchmark's half,
redacted as always: counts and structural shapes only, synthetic names where an example is needed.

### 7.1 The controller and security census

- Controllers: 40 `@RestController` implementation classes over 38 OpenAPI-generated `@Controller`
  INTERFACES (the same split idiom as pms-backend: the generated interface carries
  `@RequestMapping(method = RequestMethod.GET)` and the parameter annotations; the implementation carries
  the body and the security annotation). All parse clean at Java 17.
- GET endpoints: 70 (all `@RequestMapping(method = GET)` on the interfaces; zero `@GetMapping` on
  controllers). For context: POST 29, PUT 62, DELETE 21, plus a handful of PATCH.
- GET parameter shapes: roughly 22 by-id (exactly one `@PathVariable`; ids are `Long`, a minority
  `Integer`, a few `String`, no `UUID`); the dominant remainder is filtered list GETs carrying roughly 8
  to 12 `@RequestParam` filters each (roughly 240 `@RequestParam` declarations in total, with a
  hand-rolled page-number/page-size/page-offset Integer trio and two ordering enums; Spring `Pageable`
  appears nowhere); a minority of no-parameter lookup GETs; zero `@RequestBody`-on-GET.
- GET return types: every GET returns `ResponseEntity<X>`; X is a per-entity `*ResultSet` list envelope
  (roughly 44; the shape is a `_list` plus `total` pagination wrapper, one bespoke class per entity), a
  single `*VTO` detail view (roughly 23), one raw `List<view>`, one binary `Resource`, one
  principal/context object. Notably NO controller GET returns a raw entity class; the controller surface
  is uniformly view-shaped.
- GET bodies: 100 percent one-statement delegation, `return ResponseEntity.ok(service.method(args));`,
  with zero try/catch, zero branching, zero mapping in any controller body.
- Security, the headline consistent with pms: `@Secured` ONLY. 177 occurrences across 30 of the 40
  implementation classes, ALL method-level, ALL on controllers (services carry zero security
  annotations). Every usage carries EXACTLY ONE constant (`@Secured(CONST)` or
  `@Secured(value = {CONST})`): a statically star-imported `public static final String` from a per-module
  scopes holder class, whose initializer is a single string literal, a 6-digit numeric permission code
  (synthetic analogue: `SC_READ_WIDGET = "480012"`). No multi-authority arrays, no `ROLE_` prefixes, no
  privilege-word strings. ZERO `@PreAuthorize`, `@PostAuthorize`, `@RolesAllowed`, `@PreFilter`,
  `@PostFilter`; there is no SpEL security expression anywhere in the codebase.
- Enablement: `@EnableMethodSecurity(securedEnabled = true)` on a configuration class in an IN-TREE
  shared library module of the reactor; ZERO `@EnableGlobalMethodSecurity`. The modern-enablement gate of
  the compiled set (Volume 5, section 4.14.4) passes. One `SecurityFilterChain` with `requestMatchers`
  rules exists (never read by the tool; the reason absence is not "unprotected").
- Roughly 10 implementation classes carry NO method security (auth flows, self-profile, internal
  service-to-service controllers), falling back to the URL chain; one module's scopes holder class exists
  but is entirely commented out, so its controllers are method-security-free. Real-world ABSENT states on
  a secured application.

### 7.2 The fronting census of the 4 discovered capabilities

Each discovered capability's binding was traced to its controller fronting (strict anchor means: a GET
handler whose body is exactly one statement delegating verbatim to the binding, per section 4.14.2):

- Capability 1 (view-returning, section 4.2): fronted by EXACTLY ONE strict anchor carrying a
  single-constant `@Secured` whose constant resolves in-tree to a single string literal (a numeric code).
  COMPILES a one-element `requiredAuthorities` sequence.
- Capability 2 (view-returning, section 4.2): NO fronting controller GET exists; the read is consumed
  only by an internal inter-service proxy. ABSENT.
- Capabilities 3 and 4 (entity-returning, section 4.1): NO fronting controller GET exists for either; one
  lives in a module that ships no controller at all, the other in an adapter library consumed only by
  repository code. ABSENT. This is structural, not incidental: no controller GET in this codebase returns
  a raw entity class, so an entity-returning service read can never be the verbatim delegate of a GET
  endpoint here.
- The AMBIGUOUS entity (the correct AMBIGUOUS_BINDING refusal from section 6.1): its two competing view
  reads are fronted by TWO DISTINCT strict one-statement anchors on two distinct controllers (a public
  route and an internal route), NEITHER carrying `@Secured`. Under the pooling decision the anchors are
  NOT a tiebreaker, so the entity stays AMBIGUOUS: the live case proving the no-precedence rule matters
  on a real app.

### 7.3 Forecast (after the Controller-Authority build, stated before it)

- Row (projected): discovered 4 (by-id 4, list 0), UNCHANGED. Failed, deferred, partial rows UNCHANGED.
  mis-bound 0, now including the extended security-contract sense: no authority attaches to any
  capability except capability 1's own single compiled literal.
- Capability 1 gains `requiredAuthorities` with its one literal code; capabilities 2, 3, and 4 stay
  ABSENT (no method-security declaration found on any associated route; NOT a claim of unprotectedness,
  the URL chain gates the app). Generated output changes ADDITIVELY on exactly one capability's entries
  in the MCP manifest and the OpenAPI document; everything else byte-identical.
- The AMBIGUOUS entity stays AMBIGUOUS (anchors are never tiebreakers), and no `securityUnverifiable`
  marker is forecast anywhere: every declaration on a fronting anchor of a discovered capability is
  single-mechanism, single-constant, literal-resolving, and agreement is trivial (one anchor each).
- The dominant filtered GET surface (the `*ResultSet` list endpoints with their `@RequestParam` filters
  and `@Secured` codes) stays refused per the E6b verdict, and its 170-plus declared codes attach to
  NOTHING (no orphan authorities). Stated for scale: this app declares its security richly, and almost
  all of that declaration sits on reads beyond the structural ceiling; the by-id slice the tool can carry
  is real but small, exactly one compiled contract today.

## 7.4 Controller-Authority actual (built and measured, 2026-07-19, counts and shapes only)

The phase was built (Volume 5, section 4.14) and re-run against this benchmark. The forecast held to the
number. Recorded by counts and shapes only; no real name, route, or authority value is reproduced.

- Row (actual): discovered 4 (by-id 4, list 0), UNCHANGED. Failed 107 (NO_BACKING_METHOD 106,
  AMBIGUOUS_BINDING 1), partial 6, mis-bound 0, all UNCHANGED. The phase added no capability and moved no
  binding; the generated output's 4 discovered bindings are the same 4.
- Declared security: `declared 1 (compiled 1, unverifiable 0)`. EXACTLY ONE of the 4 discovered reads is
  fronted by a strict one-statement controller anchor carrying a single-constant `@Secured` whose constant
  resolves in-tree to a single numeric-code string literal, so it compiled a one-element
  `requiredAuthorities` sequence, exactly as the section 7.2 fronting census predicted. The modern gate
  passed (an in-tree `@EnableMethodSecurity(securedEnabled = true)` in a shared library module; no legacy
  annotation).
- The other THREE discovered reads are ABSENT: no fronting controller GET exists for them at all (one is
  consumed only by an internal proxy; two are entity-returning reads with no controller in reach, and
  structurally no controller GET in this codebase returns a raw entity). No `securityUnverifiable` marker
  appeared anywhere, exactly as forecast.
- The AMBIGUOUS entity stayed AMBIGUOUS: its two competing view reads are each fronted by a strict
  one-statement anchor, and because a controller anchor is never a tiebreaker (the no-precedence rule), the
  entity did not resolve. The live no-tiebreaker case, confirmed on a real app.
- mis-bound 0, including the security contract: the one compiled read carries exactly its own anchor's
  single authority; the pervasive filtered-list surface (its many `@Secured` codes) attaches to NOTHING,
  because those reads are refused and have no capability to decorate (no orphan authorities). The tool
  declares one honest by-id contract and refuses the rest.

## 8. Attestation forecast: the attestable ceiling (counts and shapes only, 2026-07-20)

Phase Attestation (Volume 5, section 4.15) restated against this benchmark's standing census (sections
6.1 and 7), redacted as always: counts and shape categories only. The vocabulary: ATTESTABLE UP TO N
GIVEN A KNOWLEDGEABLE HUMAN, never a promise, and here the knowledgeable human is the adopting team, not
the tool's maintainer, which makes this app the truest test of the assertion experience.

- AT-0 (structural floor and envelope unwrap): the per-entity list-envelope wrappers (the `_list` plus
  `total` shape, roughly 44 envelope list endpoints) become visible-but-refused-filtered via the T2 body
  tie where their service bodies expose the repository access; the twin-proxy and delegation-built
  wrappers with no in-tree persistence tie refuse as OPAQUE_WRAPPER, counted. New discoveries forecast: 0.
  Discovered stays 4, mis-bound 0.
- AT-1 (predicate attestation): attestable up to 8: the 6 soft-delete-guarded by-id reads (3 helper-hop,
  3 inline; the human asserts the guard's existence condition, the tool has already verified everything
  else including mutation-freedom through the ratified walks) and the 2 real user-facing filtered query
  reads the deferral census identified (of the 31 deferred reads, only those 2 are genuine query
  capabilities; the other 29 are event-handler enums, notification inserts, and request-context
  accessors, structurally excluded or non-reads).
- AT-2 (shape attestation): attestable up to 12: the aggregate-enrichment reads (a verified
  by-primary-key load plus proxy or repository enrichment feeding a composite mapper), each attested for
  its enrichment sentence over the structurally proven load.
- AT-3 (boundary, deferred): up to 15 delegation reads (the Feign and cross-service facades, where
  read-only-ness itself must be asserted) wait for AT-3's separate ratification with its stronger guards.
  Security contracts: 0 unverifiable today (section 7.4: one compiled, rest absent).
- EXCLUDED: the 1 hardcoded stub (no read exists to describe), the 10 internal polymorphic-subtype rows
  (no read to attest), the AMBIGUOUS entity (attestation never selects bindings; its two competing view
  reads stay a refusal), and every write and MQ consumer.

The ceiling in one line: 4 verified + up to 20 attestable now (AT-1 8, AT-2 12), + up to 15 more behind
AT-3's own ratification. Attested counts are reported separately from discovered, never summed; every
existing discovered row stays byte-identical through every sub-phase, and movement is a regression
finding. The open verification named in the design applies doubly here: this app's attester is a real
adopting team, and their real attestation rate, decline rate, and staleness churn are the numbers that
will decide whether AT-2 and beyond proceed.

## 9. Read-safety receiver awareness: forecast (counts and shapes only, 2026-08-04)

DESIGN HALF, pending ratification. The rule is Volume 5 section 4.15.2.1. No tool code changed.

THIS ROW IS AN ARGUMENT, NOT A MEASUREMENT, and it is labelled as one. The private enterprise benchmark's
source is not present in the working tree this design session ran in; only this redacted ledger is. No
census of its write-set call sites was run, and none is claimed. The forecast below rests on the call
graph, which is checkable in the tool's own source, not on that application's code.

THE FORECAST: BYTE-IDENTICAL BY CONSTRUCTION. The read-safety check has exactly one call site in the tool,
the attestation intake (Volume 5 section 4.15.4, condition 3, part 2). This benchmark has no
`tibyan/attestations.json`, so that intake never runs on it, so the refined clause is never reached. No
read can change verdict. Discovered stays 4, mis-bound 0, mis-safe 0. This is the same additivity argument
that carried AT-1 on this benchmark (fixture ATT-11), and it holds for the same structural reason.

WHERE THIS BENCHMARK MATTERS LATER, stated by shape only. Its two largest refused populations are the
aggregate-enrichment reads and the guarded reads. In-application aggregation is the shape the current
receiver-blind clause falsely refuses, and the guarded reads are category A targets whose bodies branch.
So this benchmark is expected to be among the heaviest beneficiaries of the refinement once AT-2 exists and
entries are authored against it, and it is the natural place to measure whether the carve-out is too narrow
in practice. Until then it is untouched, and no count is forecast for it beyond the unchanged 4.

REDACTION HELD: counts and shape categories only, no names, no paths, no identifiers.

## 9.1 Read-safety receiver awareness: BUILT, untouched by construction (2026-08-04)

The ratified rule (Volume 5 section 4.15.2.1) built. This benchmark is UNTOUCHED, and the reason is
structural rather than measured: read-safety has exactly one call site in the tool, the attestation intake,
and this benchmark has no committed record file, so the refined clause is never reached. Discovered stays 4,
mis-bound 0, mis-safe 0.

As section 9 stated, this row remains an argument rather than a measurement: that source is not present in
the working tree the build ran in. Nothing was scanned and no count is claimed beyond the unchanged 4.

The expectation recorded in section 9 stands for later: its aggregate-enrichment population is the shape the
old receiver-blind clause would have falsely refused, so this is the benchmark on which to test whether the
carve-out is too narrow in practice, once AT-2 exists and entries are authored against it.

REDACTION HELD: counts and shape categories only.

## 10. Enum-typed inputs: forecast (counts and shapes only, 2026-08-04)

DESIGN HALF, pending ratification. The rule is Volume 5 section 4.16.

THIS ROW IS AN ARGUMENT, NOT A MEASUREMENT, and is labelled as one for the same reason as section 9: that
source is not present in the working tree this design session ran in, so no census was run and none is
claimed.

UNTOUCHED, by construction twice over. This benchmark has no committed record file, so the attestation
intake never runs on it; and its discovered capabilities are by-id reads whose inputs are identifiers, so no
input value constraint can attach to any of them. Discovered stays 4, mis-bound 0, mis-constrained 0.

Recorded for later: whether its enums are bare or value-mapped is unknown here and matters, because the two
idioms advertise different sets. When this benchmark is next available, the wire-value rule (shape (a)
versus shape (b)) should be checked against its declarations before any entry is authored against a filtered
read of it.

REDACTION HELD: counts and shape categories only.
