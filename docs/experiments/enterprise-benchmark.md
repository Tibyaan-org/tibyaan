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
