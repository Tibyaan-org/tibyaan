# Experiment: second real app trial (pms-backend)

Status: complete, and adopted as the standing benchmark (Volume 13, section 5). After each Tier 2
phase, the tool is re-run against this app and section 8 below gains a dated entry with the new
totals: discovered, failed loudly by cause, and mis-bound. Discovered rising while mis-bound stays
zero is the project's health metric; mis-bound greater than zero becomes the top priority over
everything.

Date of the baseline run: 2026-07-02. This is an evidence-gathering experiment, not a build. No
tool code was changed. The target app was read only, never compiled, never built, and never
added to the reactor. The only outcome that would have forced a hard stop, a mis-bound
capability (a silent wrong result), did not occur: see the MIS-BOUND section.

Tool used: the existing CLI jar `tibyan-cli-0.1.0-SNAPSHOT-jar-with-dependencies.jar`, unchanged.
Commands: `scan <path> --out ...` against the whole app and, to probe beneath the first failure,
against each domain module in isolation. The provider reads source as text with JavaParser
(Volume 5). No `generate` was run: no scan reached a valid IR to generate from.

Reactor note: `examples/pms-backend` was already outside the parent pom `<modules>` list, so it
was never wired into the build. No detach was needed and no pom change was made this session.
Nothing about the tibyaan build changed, so `mvn clean verify` is unaffected.

---

## 1. The app at a high level

A multi-module Maven property-management backend, one Spring Boot application composed of about
fourteen domain modules (property, lease, billing, maintenance, inspection, community, chat,
notification, file, staff, user, vendor, admin, audit) plus a reporting module and a set of the
author's private foundation libraries under `library/`. Only structure is described below; no
business logic is reproduced.

- Entity classes: 43 (annotated `@Entity`).
- Identifier styles:
  - 40 entities: a single primitive-mapped id. Mostly `Long id`; five use `Integer id`
    (Domain, Event, EventParameter, Role, UserStatus); one names it `Long userId` (UserSettings).
    These map cleanly to the closed primitive set.
  - 3 entities: a composite key via `@EmbeddedId` (EventAttendee, GroupMember, UserRole). These
    are join tables with multi-column keys, not a single `@Id` field. Cause code (c).
- Read-method shapes on the `@Service` layer (sampled, read only). The dominant shape is that
  services return DTO/VTO or result-set types, not the JPA entity:
  - by-id reads return a view type, not the entity: `findById(Long)` returns `TenantVTO`,
    `getVendorById(Long)` returns `VendorVTO`.
  - list, filter, and multi-argument reads are common: `getAllByFilter(...)` returns
    `TenantResultSet`; `getMyVendors(Long, Integer, Integer)` and `getVendorsByBuilding(...)`
    return `VendorResultSet`; `getVendorsByBuildingId(Long)` returns `List<VendorVTO>`.

The tool models exactly one read shape (Volume 5, section 4.1): a single-id-parameter method,
declared on an `@Service`, whose return type simple name equals the entity name. Almost nothing
in this app has that shape, because the service boundary speaks DTOs and result sets.

---

## 2. Inventory (the denominator)

Entities grouped by module, with identifier. Candidate reads are the `@Service` methods that
return an entity or a collection of it; in this app they overwhelmingly return VTO or result-set
types instead, which is itself the finding (see section 5, gaps 2 and 4).

| Module | Entities | Id style |
|---|---|---|
| property | Property, Building, Unit, Tenant | single Long |
| lease | Lease, LeaseDocument, LeaseSignatureRequest, LeaseTimeEntry | single Long |
| billing | Invoice, Cost, RentalPayment | single Long |
| maintenance | Ticket, TicketComment, TicketAttachment | single Long |
| inspection | Inspection, InspectionItem | single Long |
| community | Group, CommunityEvent, GroupMember, EventAttendee | single Long; GroupMember and EventAttendee composite @EmbeddedId |
| chat | ChatRoom, ChatMessage | single Long |
| notification | Notification, Event, EventParameter, Domain | Long; Event, EventParameter, Domain are Integer |
| file | PPFile, FileVersion, DailyCounter | single Long |
| staff | Staff, StaffInvoice, TimeEntry | single Long |
| user | User, Role, OTP, UserSettings, UserStatus, UserRole | Long; Role and UserStatus Integer; UserSettings Long userId; UserRole composite @EmbeddedId |
| vendor | Vendor | single Long |
| admin | Admin, SystemSetting, FileTypePolicy | single Long |
| audit | AuditLog | single Long |
| reporting | (none) | n/a |

Total: 43 entities across 14 entity-bearing modules, plus reporting with no entities. Candidate
read-by-id capabilities in the tool's sense (one per entity): 43.

---

## 3. Outcome table (entity / candidate capability to outcome to cause to message)

Every scan failed to emit a single capability. The provider aborts a scan on the first
unparseable file, so most modules never reached entity extraction; the outcome for those
entities is decided by that abort, not by an individual verdict on the entity. Messages repeat
within a module, so one representative message is quoted per module.

Cause codes: (a) multi-parameter finder, (b) list/query/search read, (c) composite or non-@Id
id, (d) no single backing @Service method, (e) ambiguous match, (f) other/new. This app is
dominated by (f) causes, named explicitly below:
- (f1) unparseable source, language level: JavaParser is configured below Java 14, so switch
  expressions (Java 12+) and `instanceof` type patterns (Java 14+) fail. The app is Java 17.
- (f2) unparseable source, placeholder tokens: files contain literal `$IMPORT`, `$IMPORT_LINE`,
  and a corrupted `nimport` token; they are not valid Java as text.
- (f3) unmapped entity field type: an entity field whose type is another entity (an association)
  is not in the closed primitive set, so the entity extractor fails loudly.

| Module (entities) | Outcome | Cause | Exact message (representative) |
|---|---|---|---|
| WHOLE APP scan | FAILED LOUDLY, exit 3 | f1 | `Provider error: failed to parse ...\library\mq-adapter\...\ActiveMQListenerInterceptor.java: [(line 23,col 13) Use of patterns with instanceof is not supported ... supported starting from 'JAVA_14' language level ...]` |
| vendor (Vendor) | FAILED LOUDLY, exit 3 | f2 | `Provider error: failed to parse ...\VendorServiceImpl.java: [(line 1,col 36) Parse error. Found  "nimport" <IDENTIFIER> ...]` |
| user (User, Role, OTP, UserSettings, UserStatus, UserRole) | FAILED LOUDLY, exit 3 | f2 (UserRole also c, latent) | `Provider error: failed to parse ...\ActivationServiceImpl.java: [(line 4,col 64) Parse error. Found  "$IMPORT" <IDENTIFIER> ...]` |
| staff (Staff, StaffInvoice, TimeEntry) | FAILED LOUDLY, exit 3 | f2 | `Provider error: failed to parse ...\InvoiceServiceImpl.java: [(line 7,col 64) Parse error. Found  "$IMPORT" <IDENTIFIER> ...]` |
| notification (Notification, Event, EventParameter, Domain) | FAILED LOUDLY, exit 3 | f2 | `Provider error: failed to parse ...\NotificationServiceImpl.java: [(line 1,col 42) Parse error. Found  "nimport" <IDENTIFIER> ...]` |
| maintenance (Ticket, TicketComment, TicketAttachment) | FAILED LOUDLY, exit 3 | f2 | `Provider error: failed to parse ...\TicketServiceImpl.java: [(line 1,col 41) Parse error. Found  "nimport" <IDENTIFIER> ...]` |
| inspection (Inspection, InspectionItem) | FAILED LOUDLY, exit 3 | f2 | `Provider error: failed to parse ...\InspectionServiceImpl.java: [(line 13,col 64) Parse error. Found  "$IMPORT" <IDENTIFIER> ...]` |
| file (PPFile, FileVersion, DailyCounter) | FAILED LOUDLY, exit 3 | f2 | `Provider error: failed to parse ...\FileStorageServiceImpl.java: [(line 5,col 64) Parse error. Found  "$IMPORT" <IDENTIFIER> ...]` |
| community (Group, CommunityEvent, GroupMember, EventAttendee) | FAILED LOUDLY, exit 3 | f2 (GroupMember, EventAttendee also c, latent) | `Provider error: failed to parse ...\EventServiceImpl.java: [(line 14,col 64) Parse error. Found  "$IMPORT" <IDENTIFIER> ...]` |
| chat (ChatRoom, ChatMessage) | FAILED LOUDLY, exit 3 | f2 | `Provider error: failed to parse ...\WebSocketChatController.java: [(line 1,col 32) Parse error. Found  "nimport" <IDENTIFIER> ...]` |
| admin (Admin, SystemSetting, FileTypePolicy) | FAILED LOUDLY, exit 3 | f2 | `Provider error: failed to parse ...\AdminServiceImpl.java: [(line 1,col 35) Parse error. Found  "nimport" <IDENTIFIER> ...]` |
| billing (Invoice, Cost, RentalPayment) | FAILED LOUDLY, exit 3 | f1 | `Provider error: failed to parse ...\BillingScheduler.java: [(line 138,col 16) Switch expressions are not supported ... supported starting from 'JAVA_12' language level ...]` |
| lease (Lease, LeaseDocument, LeaseSignatureRequest, LeaseTimeEntry) | FAILED LOUDLY, exit 3 | f1 | `Provider error: failed to parse ...\LeaseControllerImpl.java: [(line 180,col 17) Use of patterns with instanceof is not supported ... 'JAVA_14' language level ...]` |
| property (Property, Building, Unit, Tenant) | FAILED LOUDLY, exit 3 | f3 | `Provider error: unmapped Java type 'Property' on field 'Building.property'. The closed primitive type set (Volume 4 section 4) has no equivalent. Extend the type mapping or exclude the field; do not coerce it.` |
| audit (AuditLog) | FAILED LOUDLY, exit 3 | d | `Provider error: no service method backs read capability 'getAuditLog' for entity 'AuditLog'. Looked for a public method on an @Service class whose return type is 'AuditLog' and whose single parameter maps to LONG; no @Service methods were found. The binding is discovered, not assumed ...` |
| reporting (none) | NO CAPABILITY, exit 0 | n/a | Valid empty IR: `"entities": [], "capabilities": []`. Correct: the module has no `@Entity`. |

Notes on the two modules that reached entity extraction (proof the loud failure is real, not an
early bail):
- property parsed all files, entered the entity extractor, and failed on the first association
  field it met (`Building.property` of type `Property`). It did not coerce or skip; it named the
  field and stopped.
- audit parsed cleanly, entered extraction, formed the intent `getAuditLog`, searched the
  `@Service` layer for a method returning `AuditLog` taking a `Long`, found none (audit services
  return DTOs and write events), and failed with a located, explanatory error. This is exactly
  the discovered-not-assumed contract of Volume 5, section 4.1 behaving correctly.

---

## 4. Totals

- Entities in scope: 43. Candidate read-by-id capabilities: 43.
- DISCOVERED: 0.
- MIS-BOUND / WRONG: 0.
- FAILED LOUDLY: 43 (every entity; no capability was ever emitted). Plus the whole-app scan
  itself failed loudly at exit 3 before any domain module was reached.
- NO CAPABILITY, correct empty result: reporting (0 entities), exit 0.

FAILED LOUDLY by proximate cause (entities blocked, by the wall each module hit first):
- f2 unparseable source, placeholder tokens (`$IMPORT` / `nimport`): 31 entities across 10
  modules (vendor, user, staff, notification, maintenance, inspection, file, community, chat, admin).
- f1 unparseable source, language level below Java 17 (switch expressions, instanceof patterns):
  7 entities across 2 domain modules (billing, lease), and the whole-app scan aborted here too,
  inside the `library/mq-adapter` infrastructure code.
- f3 unmapped association field type: 4 entities (property module).
- d no backing read-by-id `@Service` method: 1 entity (audit).

Latent causes, proven by source inspection but never reached because parsing failed first:
- c composite `@EmbeddedId` id: 3 entities (EventAttendee, GroupMember, UserRole).
- f3-b (a distinct new cause) service returns DTO/VTO, not the entity: pervasive. Every sampled
  by-id finder returns a view type (`findById` to `TenantVTO`, `getVendorById` to `VendorVTO`),
  so shape rule 3 (return simple name equals entity name) would fail for essentially all 40
  single-key entities even if every file parsed.
- b list/filter/multi-argument reads: pervasive (`getAllByFilter`, `ResultSet` returns,
  `List<...>` returns, `getMyVendors(id, page, size)`). These are the bulk of the real read API.
- a multi-parameter finder: seen (`getMyVendors`, `getVendorsByBuilding`).
- e ambiguous match: not observed.

---

## 5. MIS-BOUND section (the alarm case)

None. Zero mis-bound or wrong capabilities were produced, because zero capabilities were produced
at all. Every scan either failed loudly with a located `ProviderException` naming the file and
the reason, or (reporting) produced a correct empty IR for a module that genuinely has no
entities. The empty reporting IR is not a silent wrong result: it is an honest, accurate empty
answer for a module with no `@Entity` classes.

This is the good result. The one thing the tool is designed never to do, hand back a plausible
but wrong binding, did not happen even once against a large, messy, real app.

---

## 6. Ranked gaps to build next (by capabilities blocked in THIS app)

Ordered by how many real capabilities each gap actually blocked here, evidence first.

1. **Per-file parse resilience, and lift the JavaParser language level to the app's Java version.**
   Highest impact by a wide margin: this gate stopped 12 of 14 entity-bearing modules (38 of 43
   entities) and the whole-app scan itself, before entity extraction even began. The provider
   aborts the entire scan on the first file it cannot parse. Two distinct triggers share this
   gate: (f1) the parser is configured below Java 14 while the app is Java 17, so legitimate
   modern syntax (switch expressions, `instanceof` patterns) is rejected; and (f2) some files are
   genuinely invalid as text (`$IMPORT` placeholders, a corrupted `nimport`). Raising the language
   level is a clear correctness fix. Collect-and-continue per file (skip and record an unparseable
   file, keep scanning) is the structural fix that turns one bad file among hundreds from a total
   failure into a logged skip, and it is what would let the run reach the deeper gaps below and
   report them as real per-entity evidence rather than being masked. Scoping the walk to relevant
   source (entities and services) rather than every file, including private libraries and
   infrastructure, would further shrink the failure surface: the whole-app scan died in
   `library/mq-adapter`, code that is not even part of the domain.

2. **Match service reads that return a DTO/VTO, not only reads that return the entity itself.**
   Second highest by latent reach: this would otherwise block essentially all 40 single-key
   entities. Shape rule 3 requires the return type simple name to equal the entity name, but this
   app's service boundary returns view types (`findById` to `TenantVTO`, `getVendorById` to
   `VendorVTO`). Real layered systems return DTOs from services and entities only from
   repositories. Until the matcher can relate a view type back to its entity, discovery finds
   nothing here even with perfect parsing. Proven by source, reached in code only in audit (which
   failed with "no @Service methods were found" of the entity-returning shape).

3. **Handle entity fields that reference other entities (associations).** Blocked all 4 property
   entities at the first association (`Building.property`). Real domain entities have
   relationships; the closed primitive set has no place for them and the extractor fails loudly.
   A modeling decision is needed: represent an association as a reference to another entity's id,
   or skip association fields, rather than treating them as unmapped primitives.

4. **Model list, filter, and multi-argument reads (causes a and b).** These are the majority of
   the actual read API in this app (`getAllByFilter`, `ResultSet` and `List` returns, paged
   finders). None are expressible today. Lower rank only because parsing and DTO matching block
   the run before these are reached, but by volume of real capabilities this is large.

5. **Read-by-id with no backing service method (cause d).** Write-heavy modules such as audit
   have no entity-returning finder. The current behavior (fail loud, name what was sought) is
   correct; the gap is only that such entities yield no capability, which may be the right answer.

6. **Composite `@EmbeddedId` identifiers (cause c).** Three join entities (EventAttendee,
   GroupMember, UserRole). Smallest count, and arguably out of scope for read-by-single-id.

---

## 7. Honesty verdict

Yes. The tool behaved correctly: it failed loud and it never mis-bound. Against a large, real,
and notably messy application (43 entities, modern Java 17 syntax, service methods that return
DTOs, entity associations, composite keys, and even source files containing unrendered generator
placeholders), the tool produced exactly zero capabilities and zero wrong bindings. Every failure
was a located `ProviderException` that named the offending file or field and explained what it
looked for, the whole-app scan exited non-zero (3) rather than pretending success, and the one
module that produced an IR (reporting) produced a correct empty one because it truly has no
entities. The discovered-not-assumed binding rule was observed working in the audit module: it
searched for a real method of the right shape, did not find one, and refused rather than falling
back to a convention. The evidence for the alarm case is that it stayed empty. The tool did not
over-fit, did not coerce, and did not guess. The failure mode this experiment guards against, a
silent wrong result, did not occur.

---

## 8. Benchmark log (dated entries, one per re-run)

| Date | Tool state | Discovered | Mis-bound | Failed loudly (by cause) | Skipped by parse | Notes |
|---|---|---|---|---|---|---|
| 2026-07-02 | MVP plus three hardening steps, unchanged | 0 | 0 | 43 (f2: 31, f1: 7, f3: 4, d: 1) | (n/a, scan aborted) | Baseline. Whole-app scan aborted at the first unparseable file (exit 3); per-entity outcomes are by the wall each module hit first, not a real per-entity classification |
| 2026-07-02 | E1: Java 17 language level, collect-and-continue, scoped walk | 0 | 0 | 43 per entity (UNMAPPED_FIELD_TYPE 24, NO_BACKING_METHOD 19) | 27 files | Scan completes end to end (exit 0). Every one of the 43 entities gets a located per-entity outcome, and every unparseable file is named with its parse location, instead of a single abort. See the interpretation below |
| 2026-07-02 | E3 build (associations, enums, FLOAT); E2 not yet built | 0 | 0 | 43 (NO_BACKING_METHOD 43) | 27 files | All 24 formerly UNMAPPED_FIELD_TYPE entities now extract, so every entity reaches binding and fails there. The IR gained the full shapes: 20 ENTITY_REF association fields deduped to 19 MANY_TO_ONE relations, 21 enum fields carrying their value sets, and 8 FLOAT fields. `mvn clean verify` green; golden manifest byte-identical. See section 8.4 |
| 2026-07-02 | E3 plus E2 build (the joint measurement) | 2 | 0 | 41 (NO_BACKING_METHOD 38, COMPOSITE_KEY 3) | 27 files | getUnit bound to UnitServiceImpl#findById (view UnitVTO) and getLease bound to LeaseServiceImpl#findById (view LeaseVTO), both through pattern P1a one-hop. The ratified joint exit: discovered greater than zero with mis-bound 0. `mvn clean verify` green; golden manifest byte-identical. See section 8.5 |
| 2026-07-02 | Third app added: erp-backend, tool unchanged (post-E2, post-view) | 7 | 0 | 18 (UNMAPPED_FIELD_TYPE 13, NO_BACKING_METHOD 4, AMBIGUOUS_BINDING 1) | 0 files | A second standing benchmark now exists at examples/erp-backend (25 entities, UUID ids, entity-returning reads). Baseline: all 7 discoveries via rule 4.1; rule 4.2 fired zero times; mis-bound 0. Full generalization writeup: docs/experiments/third-app-trial.md |
| 2026-07-02 | E5 build (partial-model integrity), pms-backend | 2 | 0 | 41 (NO_BACKING_METHOD 38, COMPOSITE_KEY 3) | 27 files | partial 0 (0 fields). Byte-identical to the E3-plus-E2 row: pms has no tolerated field shapes and no absent entities, so tolerance skips nothing and closure prunes nothing. Proof the phase is additive |
| 2026-07-02 | E5 build (partial-model integrity), erp-backend | 16 | 0 | 9 (NO_BACKING_METHOD 6, NO_IDENTIFIER 1, AMBIGUOUS_BINDING 2) | 0 files | partial 13 (22 fields skipped). Discovered up from 7 to 16, all via rule 4.1; scan --out now writes a valid IR and generation succeeds for all 16 (was exit 2). Two forecast composition swaps, both verified and mis-bound 0: Quote is AMBIGUOUS (a competing getInvoice), VariationGroup discovers via getVariationGroup. See third-app-trial.md, section 11 |

Each Tier 2 phase (Volume 13, section 3) appends one row after its re-run. A row's totals must
come from an actual run of the tool as built at that date, never from a projection.

pms-backend remains the primary benchmark for the Tier 2 phases (its view-returning idiom exercises
rule 4.2). erp-backend is the second standing benchmark: its entity-returning idiom exercises rule
4.1, and together the two apps stop any single phase from being tuned to one codebase. The health
metric is unchanged: discovered rising while mis-bound stays exactly zero, on both apps.

Second-backend note (2026-07-02, no new benchmark row): both benchmark IRs now render to a valid
OpenAPI 3.0 document through the new `tibyan-backend-openapi`, alongside the MCP manifest, proving the
IR feeds more than one output with zero change to the core or any provider (Volume 6, section 4). The
erp IR yields 16 GET operations (13 entity schemas disclosing their partial fields via
`x-tibyan-partial`), and the pms IR yields 2 view operations (each declining to enumerate the view's
fields via `x-tibyan-view`); both validate against the OpenAPI 3.x schema (swagger-parser). This is an
output-side proof, not a scan, so discovery numbers are unchanged and no benchmark row is added.

Methodology note, forward-looking (partial-model integrity, phase E5, pending ratification): once E5
is built, a row gains a `partial N (M fields skipped)` figure alongside the existing columns. It is a
new dimension, not a redefinition: `discovered`, `failed by cause`, `mis-bound`, and `skipped-by-parse`
keep their historical meaning, so all rows above stay comparable. `partial` counts discovered-or-failed
entities whose IR shape is incomplete because a field was skipped or a reference dropped; it never
inflates `discovered` and never masks a mis-binding (Volume 5, section 4.9). The forecast rows for E5
are in docs/experiments/third-app-trial.md, section 10.

### 8.1 Interpretation of the E1 run (2026-07-02)

The E1 run is the first that survived this app end to end. It is run with the built CLI:
`tibyan scan examples/pms-backend` (no `--out`), which now exits 0 and prints a per-entity outcome
report plus a named, located skip for every file it could not parse.

What changed from the baseline, and what it means:

- The scan no longer aborts. The baseline died at exit 3 on the first unparseable file, so 38 of 43
  entities were never classified. E1 walks the whole app, parses collect-and-continue, and returns a
  real per-entity outcome for all 43. This is the exit criterion for E1 (Volume 13, phase E1) met.
- The f1 language-level failures are gone. Raising the parser to Java 17 means switch expressions and
  `instanceof` patterns now parse; none of the seven f1 entities fail on syntax anymore. The 27
  remaining skips are all f2, the genuinely invalid `$IMPORT` and `nimport` placeholder files, which
  are not valid Java as text and should stay unparseable.
- Discovery is still 0, as expected. E1 adds no discovery power; DTO-aware matching is E2. Mis-bound
  is still 0: the discovery rule emits a capability only on an exact single-shape match, so a wrong
  binding cannot be produced. Discovered 0 with mis-bound 0 is the honest, healthy state for this
  phase.
- The failure causes are now real per-entity verdicts, not proximate module walls. Of the 43:
  UNMAPPED_FIELD_TYPE 24 and NO_BACKING_METHOD 19.

New sub-findings, surfaced only because E1 now reaches every entity (recorded for later phases, not
fixed here, E1 is parse resilience only):

- Of the 24 UNMAPPED_FIELD_TYPE failures: 13 are true entity associations (for example
  `Building.property` of type `Property`), which is the E3 case; 8 are enum-typed fields (for example
  `Property.type` of type `PropertyType`); and 3 are `Double` fields (for example
  `LeaseTimeEntry.hoursWorked`), which the type mapper deliberately leaves unmapped because the closed
  set has no floating-point primitive. Enums and `Double` are new, distinct type-mapping decisions
  that E3 (or a dedicated type-mapping step) should weigh, separate from associations.
  Census correction from the E3 design study (2026-07-02): this 13/8/3 split counts only each
  entity's first-blocking field, and it is verified exact as such; but extraction stops at the first
  unmapped field, so more hides behind it. The full census (Volume 5, section 4.4.1): 20 association
  fields on 15 entities, 21 enum fields on 13 entities, and 8 `Double` fields on 7 entities,
  including both joint-exit targets (Lease and Unit both carry `Double` rent fields behind their
  first-reported failures).
- The 19 NO_BACKING_METHOD failures are entities that extracted cleanly but whose service either did
  not parse (its file is among the 27 skips) or returns a DTO or view type rather than the entity.
  Both are the E2 case: once the placeholder files are set aside and DTO-returning reads are matched,
  these are where discovery should first rise.
- No entity reached NO_IDENTIFIER or AMBIGUOUS_BINDING here. Correction from the E2 design study
  (2026-07-02): EventAttendee and GroupMember do not use `@EmbeddedId`; they use `@IdClass` with two
  plain `@Id Long` fields, so they extracted cleanly, the first-`@Id` assumption formed a half-key
  read intent for each, and both failed as NO_BACKING_METHOD (they are in the 19). Only UserRole hides
  its composite key behind UNMAPPED_FIELD_TYPE (its `Role` association fails first). The half-key
  intent is a latent mis-bind hazard: had a one-Long method matched, a composite-key entity would have
  bound to part of its key. The E2 rule closes this with an eligibility condition (Volume 5,
  section 4.2, condition 0).

### 8.2 E2 expectation (stated forecast, 2026-07-02, before any build; not a run row)

The E2 design session (the rule in Volume 5, section 4.2) pre-registers the following forecast, so
the build is measured against a stated prediction rather than a moving target. These numbers are a
projection and deliberately do not appear in the run table above; the build session appends the real
run row and compares it to this.

- After the E2 build alone: discovered 0, mis-bound 0, skipped-by-parse 27, failed 43 with a cause
  shift: unmapped field type 24, no backing method 17, composite key 2 (EventAttendee and GroupMember
  move to the new composite-key cause under the eligibility tightening). Discovered cannot rise
  because none of the 19 has any single-id-parameter read in a parseable service: 15 have their whole
  service layer among the 27 unparseable placeholder files, 2 are the composite-key join entities,
  1 has an empty parsed service (AuditLog), and 1 has only a mutating counter method (DailyCounter).
  What must visibly improve is diagnostics: near-miss reporting (which method came close and which
  rule condition it failed) and skipped-file context on every NO_BACKING_METHOD message.
- After E2 and E3 together (E3 clearing associations and enums so the census entities extract):
  Unit and Lease flip to DISCOVERED through pattern P1a, each with exactly one candidate; predicted
  discovered: up to 2, contingent on E3 clearing every field of those two entities. Tenant stays
  refused with the named P1b near-miss (custom `@Query` terminal); Building, Property, and ChatRoom
  stay refused as P2 (composite view assembly).
- The hard bar, restated: mis-bound must be exactly 0 on every run. A single mis-binding stops all
  phase work (Volume 13, section 5).

### 8.3 E3 and joint E3 plus E2 expectation (stated forecast, 2026-07-02, before any build; not run rows)

From the E3 design session (the three rules in Volume 5, sections 4.4 to 4.6, pending ratification),
with the ratified sequencing (E3 built first, then E2, measured jointly):

- After the E3 build alone (all three decisions; E2 not yet built): discovered 0, mis-bound 0,
  skipped-by-parse 27, failed 43, now all NO_BACKING_METHOD. All 24 UNMAPPED_FIELD_TYPE entities
  clear extraction (associations map to ENTITY_REF plus Relation, enums to STRING plus values, Double
  to FLOAT), and none finds an entity-returning backing method because none exists in parseable
  source (the E1 and E2 studies proved this), so they join the existing 19. What visibly changes is
  the model, not the count: all 43 entities extract with full shapes, the IR gains 19 unique
  MANY_TO_ONE relations, enum fields carry their closed value sets, and FLOAT fields appear.
- After the E3 plus E2 builds (the joint measurement): discovered 2, getUnit bound to
  UnitServiceImpl#findById (view UnitVTO) and getLease bound to LeaseServiceImpl#findById (view
  LeaseVTO), both through pattern P1a with exactly one candidate each. Failed 41: NO_BACKING_METHOD
  38 (with near-miss diagnostics naming Tenant's P1b chain and the P2 composites Building, Property,
  ChatRoom) and COMPOSITE_KEY 3 (EventAttendee, GroupMember, and UserRole, which E3 unblocks from
  extraction and E2's eligibility then refuses on its two @Id fields). Skipped-by-parse 27.
  Mis-bound exactly 0. This meets the ratified joint exit criterion.
- Reconciliation with the 8.2 forecast: 8.2's "up to 2 (Unit, Lease), contingent on E3 clearing
  every field of those two entities" resolves to exactly 2, and the census shows the contingency
  lands on decision (c): both entities carry `Double` fields, so the joint exit is reachable only if
  FLOAT is ratified. 8.2's E2-alone row predicted COMPOSITE_KEY 2; the joint row predicts 3 because
  E3 surfaces UserRole (previously hidden behind its unmapped `Role` association).
- The hard bar is unchanged: mis-bound exactly 0 on every run, or everything stops.

### 8.4 Interpretation of the E3 build run (actual versus forecast, 2026-07-02)

The E3 build is done and re-run with the freshly built CLI: `tibyan scan examples/pms-backend`, exit 0.

Actual: discovered 0, mis-bound 0, skipped-by-parse 27, failed 43 all NO_BACKING_METHOD. The IR now holds all 43 entities with full shapes: 20 ENTITY_REF association fields deduped to 19 unique MANY_TO_ONE relations, 21 enum fields each carrying its value set as an annotation, and 8 FLOAT fields. Zero UNMAPPED_FIELD_TYPE remain.

Actual versus forecast: this matches the section 8.3 "E3 build alone" row exactly, on every number: discovered 0, mis-bound 0, skipped 27, failed 43 all NO_BACKING_METHOD, and the model counts (19 relations, 21 enum value sets, 8 FLOAT fields) as predicted. No deviation to explain.

One forecast wording is corrected here rather than massaged. The build instruction's summary line said the E3-alone run should show "COMPOSITE_KEY becomes 3 as UserRole's key surfaces." That conflicts with both the ratified rule text (Volume 5, section 4.4.3, "UserRole moves to the identifier cause once section 4.2 condition 0 is built, and to NO_BACKING_METHOD in the interim") and the section 8.3 E3-alone row (all NO_BACKING_METHOD). The COMPOSITE_KEY cause is section 4.2 condition 0, which is phase E2 and was correctly not built this session, so the actual run shows 43 NO_BACKING_METHOD and zero COMPOSITE_KEY. COMPOSITE_KEY 3 is the joint E3-plus-E2 forecast (section 8.3, second bullet), not the E3-alone one. The E3-alone run is right; the summary line's mention of COMPOSITE_KEY was a slip against the design, resolved in favor of the ratified rule.

The @IdClass join entities behaved as the design predicts for the interim: EventAttendee and GroupMember (two plain `@Id Long` fields) extract and form a half-key read intent on the first `@Id`, which nothing backs, so they are NO_BACKING_METHOD; UserRole extracts (its `@Id @ManyToOne Role` now maps to ENTITY_REF plus a relation, and its first `@Id`, `userId`, is a Long) and is likewise NO_BACKING_METHOD. The half-key intent is harmless here (nothing backs it) and is closed by E2's eligibility condition 0, which moves these three to COMPOSITE_KEY in the joint run.

### 8.5 Interpretation of the joint E3 plus E2 run (actual versus forecast, 2026-07-02)

With the E2 build in place (Volume 5, section 4.2, conditions 0 to 10), the benchmark was re-run: `tibyan scan examples/pms-backend`, exit 0.

Actual, number by number against the section 8.3 joint forecast:

| Metric | Forecast | Actual | Match |
|---|---|---|---|
| Discovered | 2 | 2 | yes |
| Mis-bound | 0 | 0 | yes |
| Failed, NO_BACKING_METHOD | 38 | 38 | yes |
| Failed, COMPOSITE_KEY | 3 | 3 | yes |
| Skipped by parse | 27 | 27 | yes |

Every number matched the forecast. The two discoveries are exactly the forecast ones, with the exact bindings and views:

- getUnit, bound to `UnitServiceImpl#findById`, view `UnitVTO`. Structural evidence: loads Unit by primary key through `UnitRepository#selectById`, whose implementation `UnitRepositoryImpl` delegates one hop to `UnitJpaRepository#findById` (a Spring Data repository of Unit), then maps via `PropertyMgtMapper#toUnitVTO(Unit) : UnitVTO`. Pattern P1a, one-hop.
- getLease, bound to `LeaseServiceImpl#findById`, view `LeaseVTO`. Structural evidence: loads Lease by primary key through `LeaseRepository#selectById`, whose implementation `LeaseRepositoryImpl` delegates one hop to `LeaseJpaRepository#findById` (a Spring Data repository of Lease), then maps via `LeaseMgtMapper#toVTO(Lease) : LeaseVTO`. Pattern P1a, one-hop.

The three COMPOSITE_KEY entities are EventAttendee, GroupMember, and UserRole, moved from NO_BACKING_METHOD (their E3 interim state) by eligibility condition 0. Tenant remains refused with the named P1b near-miss (its `TenantRepository#selectById` delegates to a custom `@Query`, `findByIdWithRelations`); Building, Property, and ChatRoom remain refused as P2 (a second repository access after the load). This is the correct end state of the phase: the 38 NO_BACKING_METHOD refusals carry named near-misses, not silence.

Generation was confirmed end to end for the two discoveries: `tibyan generate --backend mcp` from the pms-backend IR produces a valid manifest with `getUnit` and `getLease`, each carrying its binding, `ENTITY_REF` output, and `id: LONG` input (output only, into `./generated`, not committed).

One finding, recorded rather than worked around: the ratified representation (section 4.2.5) puts the view type's name on the capability as a `view` annotation, and it is present in the IR (`getUnit` carries `view: [UnitVTO]`, `getLease` carries `view: [LeaseVTO]`). It does not appear in the generated MCP manifest, because the MCP backend does not serialize capability annotations, and no backend change was in scope for this provider-only phase. Surfacing the view in the manifest, so an agent reading the manifest learns the concrete shape's name as section 4.2.5 envisions, is a small, deliberate MCP backend enhancement, deferred to a backend phase. The IR side of the ratified rule is complete.

Finding closed (2026-07-02, backend-only phase): the MCP backend now emits the `view` field on a view-carrying tool and adds a one-sentence description clause (Volume 6 section 3.1). Regenerating the pms-backend manifest confirms it: `getUnit` carries `"output": "ENTITY_REF(Unit)", "view": "UnitVTO"` and `getLease` carries `"output": "ENTITY_REF(Lease)", "view": "LeaseVTO"`. Discovery numbers are unchanged (this was output only, not a new discovery), so no new benchmark row is needed. The example-app golden stayed byte-identical, and the runtime tolerates the new field explicitly.