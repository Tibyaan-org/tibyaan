# Experiment: the private enterprise benchmark (redacted)

Privacy (absolute, permanent, this and every future session): this is a real, large, multi-module Spring
application, adopted 2026-07-12 as a permanent third benchmark, the first external-adopter-scale app. It
is PRIVATE. This ledger records ONLY structural counts and shape categories, never the application's
name, domain, module names, entity names, or any code. Generic names (OrderOffer, DiscountOffer) stand in
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
  distinct tables, distinct meaning), not an accidental duplicate. Generic illustration: an `Offer` in an
  ordering context and a different `Offer` in a discount context.
- This is the evidence for phase Entity Identity (Volume 4 section 3.7, Volume 5 section 4.12). It is not
  a discovery gap and not a mis-bind: the survey classified all 111 entities correctly with mis-bound 0;
  simple-name keying simply cannot represent the model, so nothing is emitted.

## 4. Entity Identity forecast (BLOCKED to EMITTED)

- With phase Entity Identity, the 3 colliding simple names (6 entities) receive deterministic
  disambiguated display names derived from structural package context (generically OrderOffer and
  DiscountOffer), so both validation rules pass, the IR writes, and both backends generate.
- Row (projected): IR moves from BLOCKED to EMITTED. DISCOVERED STAYS 4. This phase grants identity and
  emission, NOT discovery: raising the discovered count is the separate, ranked-second discovery-gap
  phase. So this is not a coverage win. The win is that the tool can emit output for a real multi-module
  application at all, from zero emitted to 4 capabilities plus the full 111-entity model rendered as
  OpenAPI schemas. mis-bound 0, and the latent simple-name-collapse mis-bind (which would have merged two
  distinct entities) is removed.
- Read this correctly: the headline is emission, not coverage. Discovered 4 before, discovered 4 after;
  the difference is BLOCKED before, EMITTED after. Any reader who sees "discovered 4" and calls the phase
  a small win has misread it: the phase turns a totally unusable result into a usable one.

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
