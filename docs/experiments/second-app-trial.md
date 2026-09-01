# Experiment: second real app trial (pms-backend)

> The benchmark target is a private codebase, not committed to this repository (it is gitignored). This ledger is the published evidence: a stranger can read the numbers and the per-entity outcomes here without access to the source.

Status: complete, and adopted as the standing benchmark (Volume 13, section 5). After each Tier 2
phase, the tool is re-run against this app and section 8 below gains a dated entry with the new
totals: discovered, failed loudly by cause, and mis-bound. Discovered rising while mis-bound stays
zero is the project's health metric; mis-bound greater than zero becomes the top priority over
everything.

RE-BASELINED 2026-07-20 (see section 9): the adopter repaired the 27 placeholder-corrupted source files
(they carried trivially-repairable `$IMPORT`/`nimport` tokens, an artifact of the benchmark copy, not
genuinely unparseable code), so the app now parses 100 percent clean and the STANDING baseline is now
discovered 5 (by-id: getUnit, getLease, getStaff, getUser, getVendor), skipped-by-parse 0, mis-bound 0.
The dated 2026-07-02 baseline below (discovered 2, skipped-by-parse 27) is preserved as the accurate record
of the corrupted copy every prior phase was measured against; section 9 is the current baseline.

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
| 2026-07-02 | E4 build (list reads), pms-backend | 2 | 0 | 41 (NO_BACKING_METHOD 38, COMPOSITE_KEY 3) | 27 files | by-id 2, list 0, list-refused 0, partial 0. Byte-identical to the E5 row: pms has no framework findAll list-all (its list API is filtered and `*ResultSet`-shaped). 3 named deferrals only, a recorded finding: the `*ResultSet` envelope reads are outside rule condition 2 (not a recognized collection container), and most `List<VTO>` reads are in the 27 unparseable placeholder files. getUnit and getLease unchanged. See section 8.7 |
| 2026-07-02 | E4 build (list reads), erp-backend | 25 | 0 | 9 (NO_BACKING_METHOD 6, NO_IDENTIFIER 1, AMBIGUOUS_BINDING 2) | 0 files | by-id 16 (byte-identical to E5), list 9, list-refused 1 (ProductCategory LIST_AMBIGUOUS), partial 13. `listEmailTemplate` discovers though `getEmailTemplate` is NO_IDENTIFIER (lists are identifier-independent). 56 named deferrals (forecast "~44" was the stated uncertainty). Generates 25 through both backends. See third-app-trial.md, section 13 |

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

Methodology note, forward-looking (list reads, phase E4, pending ratification): once E4 is built,
`discovered` is annotated by kind, `discovered N (by-id A, list B)`, because an entity can now yield a
read-by-id, a list, both, or (for a list-only entity such as erp's EmailTemplate) a list without a
read-by-id. This is a split, not a redefinition: `A` is directly comparable to every historical
`discovered` figure above (all of which were by-id), so the rows stay readable, and `A` plus `B`
equals `N`. `failed` keeps counting by-id outcomes at the entity level, so `by-id A` plus `failed`
still partitions the entities as before. A new outcome, `LIST_AMBIGUOUS` (two or more unfiltered
list-all methods for one entity), is reported on a separate `list-refused` line and totalled apart from
`failed`, so an entity that discovers by-id but has an ambiguous list is never double-counted. Filtered
and view-returning list reads are reported as named deferrals, not failures, so they never enter
`failed`, `list-refused`, or the missed-capability count. `mis-bound` keeps its
meaning, extended: a list bound to the wrong entity's collection, or a filtered method presented as
unfiltered, is mis-bound (Volume 5, section 4.10.9). The E4 forecast rows are in this document,
section 8.6 (pms), and docs/experiments/third-app-trial.md, section 12 (erp).

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

### 8.6 E4 list-read census and forecast (pms-backend, read-only, 2026-07-02)

The design half of phase E4 (list reads, Volume 5, section 4.10, pending ratification) needs the
list-read inventory of both benchmarks. This is pms-backend's, traced from source, read only, no tool
change. The erp-backend census and forecast are in docs/experiments/third-app-trial.md, section 12.

The census (every `@Service` method returning a collection or page), by shape:

| Shape | Count | Detail |
|---|---|---|
| (a) bare `findAll()`, clean framework pass-through | 0 | pms has no unfiltered entity-returning list read |
| (b) `findAll(Pageable)`, clean framework pass-through | 0 | pms never forwards a lone `Pageable`; it uses Integer `pageNum`/`pageSize` with filters |
| (c) filtered, one or more domain parameters, returning a `*ResultSet` view-envelope | ~35 | `getAllByFilter` (Tenant, Property, Lease), `getMyVendors`/`getVendorsByBuilding`, `getMyInvoices`/`getInvoicesByBuilding`, `getPaymentsByBuilding`/`getMyPayments`, `getCostsByBuilding`, `getStaffList`, `getMy*`/`getBuilding*` time-entries and invoices, `getChatRooms`/`getChatMessages`, `getGroupsByBuilding`/`getPendingGroups`, `getEventsByBuilding`/`getPendingEvents`, `getMyTickets`/`getTicketsByBuilding`/`getAllTickets`, `getMyInspections`/`getInspectionsByBuilding`/`getAllInspections`, `getAllUsers`/`getAllUsersByFilters`, `findAllByProperty`, `findAll(String, ...)`, `getAllByProperty` |
| (d) view-returning `List<VTO>`, filtered by a foreign key | ~7 | `getVendorsByBuildingId(Long)` (`List<VendorVTO>`), `getAllVersions(String)` (`List<FileVTO>`), `getDocumentsByLeaseId(Long)` (`List<LeaseDocumentVTO>`), `getNotificationsForUser(Long)` (`List<NotificationVTO>`), `getStaffByBuilding(Long)`/`getMyStaff(Long)` (`List<StaffVTO>`), `findPropertiesByBuildingIds(List<Long>)` (`List<PropertyProxyResult>`) |
| (e) other or scalar | 1 | `extractRoleIds(String)` (`List<Integer>`, a library JWT helper) |

The decisive fact: pms has zero methods in the modeled list-all shape (a)+(b). Every one of its ~42
list reads takes at least one domain parameter and returns a custom `*ResultSet` envelope or a `List`
of a view type. pms's entire list API is in the E4 deferred set (filtered, view-returning, or both).
This is not an artifact of the 27 unparseable files: the property and lease modules parse cleanly, and
their list reads (`TenantServiceImpl#getAllByFilter` to `TenantResultSet`,
`UnitServiceImpl#findAll(String, ...)` to `UnitResultSet`, `LeaseServiceImpl#getAllByFilter` to
`LeaseResultSet`) are reachable and still deferred, because their shape is filtered and view-returning,
not because they were skipped.

The forecast (after the E4 build, stated before it):

- Discovered: 2 (by-id 2, list 0). Unchanged. The two view-returning by-id reads (getUnit, getLease)
  are untouched. No list capability is discovered, because pms has no unfiltered entity-returning list
  read for the modeled rule to match.
- Deferred list reads (named, not failed): roughly 42 methods, all shape (c) or (d), each reported per
  method with its reason. These are the direct evidence for the successor filtered/view-list phase, and
  they are the reason pms, not erp, should drive that phase's design.
- By-id failed: 41, unchanged (NO_BACKING_METHOD 38, COMPOSITE_KEY 3). Skipped-by-parse: 27, unchanged.
  Partial: 0, unchanged. Mis-bound: 0. LIST_AMBIGUOUS: 0 (no entity has even one list-all method, so
  none can have two).

Row (projected, not a run row until the build): discovered 2 (by-id 2, list 0), by-id failed 41
(NO_BACKING_METHOD 38, COMPOSITE_KEY 3), skipped-by-parse 27, partial 0, mis-bound 0.

The split finding, stated as plainly as the E2 sequencing finding was: the modeled E4 rule discovers
list capabilities on erp-backend and zero on pms-backend, because pms's list idiom is entirely filtered
and view-returning. Modeling filtered and view-returning list reads honestly is a separate phase with
its own semantics rule (a filter parameter's meaning cannot be verified structurally, Volume 5,
section 4.10.2), and pms is its benchmark. The Volume 13 E4 exit criterion ("at least one list or filter
read on pms-backend in each module") cannot be met by the modeled list-all rule and is re-scoped in the
roadmap: E4's exit is stated against erp-backend (list discovered greater than zero, mis-bound 0), and
the pms list API moves to the successor phase.

### 8.7 Interpretation of the E4 build run (pms-backend, actual versus forecast, 2026-07-02)

The E4 build was re-run: `tibyan scan examples/pms-backend`, exit 0.

| Metric | Forecast (8.6) | Actual | Match |
|---|---|---|---|
| Discovered (by-id) | 2 | 2 | yes |
| Discovered (list) | 0 | 0 | yes |
| Mis-bound | 0 | 0 | yes |
| NO_BACKING_METHOD | 38 | 38 | yes |
| COMPOSITE_KEY | 3 | 3 | yes |
| Skipped by parse | 27 | 27 | yes |
| Partial | 0 | 0 | yes |
| Named deferrals | ~42 | 3 | no (a finding, below) |

The split finding held exactly: pms discovers no list capability, because it has no unfiltered
entity-returning list read. getUnit and getLease are byte-identical to their E5 row, the whole point of
an additive phase. Mis-bound stayed 0.

Finding, recorded not massaged: the named-deferral count is 3, not the forecast's ~42. The reason is a
property of the ratified rule, not a defect. Rule 4.10 condition 2 recognizes a list read only when the
return type is one of six collection containers (`List`, `Set`, `Collection`, `Iterable`, `Page`,
`Slice`). pms's ~35 list reads return a custom `*ResultSet` envelope type (`TenantResultSet`,
`VendorResultSet`), which is none of those, so the structural rule classifies them as not-a-list-read
and does not enumerate them; recognizing them would require unwrapping the opaque envelope class, which
is exactly the paged-envelope analysis section 4.10.6 defers. And of pms's ~7 `List<VTO>` reads, most
live in the 27 unparseable placeholder files, so they never reach the service catalog. What surfaces as
a named deferral is the handful of container-returning methods in parseable files: three, two of them
infrastructure `getAll` helpers and one `findPropertiesByBuildingIds` returning `List<PropertyProxyResult>`.
The forecast's ~42 was a human census that read the `*ResultSet` classes and counted them as list reads;
the structural rule cannot, and deliberately does not. The deferral ledger names what the rule
recognizes as a list read; an opaque envelope return is not one. This is correctness-neutral: list
discovered is 0, mis-bound is 0, and every by-id row is byte-identical, which are the properties that
matter. It sharpens the case for E6: pms's list API is not just filtered, it is hidden behind opaque
envelope types that a filter-semantics phase must decide whether and how to see.

### 8.8 P1b census and forecast (custom-query by-pk terminal, pms-backend, read-only, 2026-07-02)

The design half of P1b (Volume 5 section 4.2.10, pending ratification) needs the custom-query by-id read
inventory. This is pms-backend's, traced from source, read only, no tool change. The erp-backend census
is in docs/experiments/third-app-trial.md, section 14.

Census. pms-backend has exactly one custom-query by-primary-key terminal, Tenant, and it is a one-hop
hand-written case:

| Chain | Text | Ties to E and id |
|---|---|---|
| `TenantServiceImpl#findById` returns `TenantVTO` (view); body loads `tenantRepository.selectById(id).orElseThrow(...)`, maps `propertyMgtMapper.toTenantVTO(tenant)` | two-statement 4.2 shape | mapper resolves in-tree, `PropertyMgtMapper#toTenantVTO(Tenant) : TenantVTO` |
| `TenantRepository#selectById` (hand-written interface); its single `@Repository` impl `TenantRepositoryImpl#selectById` body is `return tenantJpaRepository.findByIdWithRelations(id);` | one-hop delegation, verbatim `id` | delegate is the `@Query` terminal |
| `TenantJpaRepository extends JpaRepository<Tenant, Long>`, `findByIdWithRelations` carries `@Query("SELECT t FROM Tenant t JOIN FETCH t.user LEFT JOIN FETCH t.unit un LEFT JOIN FETCH un.building b LEFT JOIN FETCH b.property WHERE t.id = :id")`, `@Param("id") Long id` | `SELECT t` (root), fetch joins only, `WHERE t.id = :id` | selects Tenant by its `@Id` field `id`, named parameter |

Every other pms hand-written repository (`Vendor`, `Lease`, `LeaseDocument`, `Unit`, `Admin`) delegates
`selectById` to the framework `findById` (P1a, already handled), so Tenant is the sole P1b occurrence, as
the E2 census recorded.

The decisive finding (design-closure, 2026-07-02), the number that decides the phase. Applying the
strict rule (Volume 5 section 4.2.10, ratified) to Tenant's query verbatim: the WHERE, `WHERE t.id = :id`,
is a single equality between the id property and the method's single parameter and NOTHING ELSE, so it
PASSES P5. But the first fetch, `JOIN FETCH t.user`, is an INNER join fetch (written without `LEFT`), and
`Tenant.user` is a nullable `@ManyToOne` (`@ManyToOne @JoinColumn(name = "user_id")`, no `optional = false`,
no `nullable = false`, so nullable by JPA default). An inner join fetch on a nullable association drops the
Tenant when its user is null: this query returns nothing for a Tenant that exists but has no user, so it
is not equivalent to a by-primary-key load. It carries a hidden existence filter and FAILS the strict rule
at P4. Verdict: FAIL. getTenant is not discovered. The first-draft forecast (discovered 2 to 3) was wrong
because it read the WHERE alone and waved the fetch joins through; reading the join semantics flips it.

Forecast (build deferred; a target for the eventual build):

- Discovered: 2, UNCHANGED. getUnit and getLease are the only discoveries; getTenant stays
  NO_BACKING_METHOD, its near-miss message changing from the generic custom-query note to the inner-fetch
  refusal (Volume 5 section 4.2.10.3), the counts unmoved.
- Failed: NO_BACKING_METHOD 38 unchanged; COMPOSITE_KEY 3 unchanged. Skipped-by-parse 27 unchanged. List 0
  unchanged. Mis-bound 0, and never at risk since the rule emits nothing.
- Row (projected forecast, build deferred, not a run row): discovered 2 (by-id 2, list 0), failed 41
  (NO_BACKING_METHOD 38, COMPOSITE_KEY 3), skipped-by-parse 27, mis-bound 0. Byte-identical to the current
  pms row.

pms is therefore not the one-entity unlock the first draft claimed; it is a refusal, and P1b discovers
zero here. The rule's value on pms is that it names Tenant's inner-fetch filter honestly rather than
binding a filtered query as getTenant.

### 8.9 E6 filtered-read census (filter-semantics-plus-envelope, pms-backend, read-only, 2026-07-02)

The E6 design (Volume 5 section 4.11) needs the filtered-read inventory. This is pms-backend's, from
source. pms is the E6 benchmark because its entire read API is filtered and envelope-shaped. Counts:

| Shape | Count | Note |
|---|---|---|
| (a)/(b)/(c) filtered reads (single, multi, and filter-plus-paging), returning a `*ResultSet` envelope | ~35 | `getAllByFilter` (Tenant, Property, Lease), `getMy*`/`getBuilding*`, `getStaffList`, `getChatRooms`, and the rest of section 8.6 shape (c) |
| (d) projection or view-returning `List<VTO>` filtered by a foreign key | ~7 | section 8.6 shape (d) |
| (e) envelope-wrapped, existence hidden by an opaque wrapper | ~35 | the `*ResultSet` types; the same reads as (a) to (c), counted here for the visibility half |
| (f) collection filters returning a collection | folded into (a) to (d) | |

The decisive facts for the design:

- Visibility: roughly 35 reads are invisible to the tool today because their return type is an opaque
  `*ResultSet` wrapper, not a recognized container (E4 saw only 3 deferrals for this reason, section 8.7).
  The wrapper is structurally unwrappable: `TenantResultSet` declares one collection field,
  `List<TenantVTO> data`, plus a `Long total`, so the tool could resolve the class, read the element view,
  and tie it back to Tenant through the 4.2 view machinery. But every such read is filtered, so unwrapping
  lands at the filter wall and yields no capability.
- Filter semantics: pms hides them in imperative code, not in any query text.
  `getAllByFilter(fullName, email, orderBy, orderDir, pageNum, pageSize, pageOffset)` builds a
  `TenantSearchFilter` and calls `tenantRepository.selectAllByFilters(filter)`, which a hand-written
  `TenantQueryBuilder` executes. What `fullName` and `email` filter, and how, is nowhere readable; it is in
  the query builder's Java. pms has zero Spring Data derived-query filters and zero readable filter
  predicates.

So on pms there is no structural way to know any filter's meaning, and no honest way to represent one.
This is the evidence behind the E6 recommendation to REFUSE for v1 with a documented ceiling: pms's
ceiling is essentially its entire read API beyond getUnit and getLease (Volume 5 section 4.11.5).

### 8.10 E6a transparency census (envelope visibility, pms-backend, read-only, 2026-07-02)

The E6a design (Volume 5 section 4.11.7) needs the wrapper-transparency inventory: for each wrapper
return type, can the tool prove a read of E exists behind it without reading the wrapper's internals?

Wrapper types in use: roughly thirty-plus per-entity `*ResultSet` classes (`TenantResultSet`,
`VendorResultSet`, `RentalPaymentResultSet`, `TicketResultSet`, `LeaseResultSet`, and so on),
OpenAPI-generated, each declaring one collection field `List<VTO> data` and a `Long total`. None is
generic. A single generic `ListResultSet<T>` exists in the common library but no read returns it, so the
generic tie (T1) is inert on pms in practice.

Transparency classification:

| Class | Count | Basis |
|---|---|---|
| TRANSPARENT via T2 (body has a repository-of-E access returning a collection of E) | roughly 12, in the parseable modules (property, lease, billing) | `getAllByFilter`, `findAllByProperty`, `getAllByProperty`, `getPaymentsByBuilding`, `getMyPayments`, `getMyInvoices`, `getInvoicesByBuilding`, `getCostsByBuilding`, `getMyTimeEntries`, each body `List<E> xs = eRepo.selectAllByFilters(filter); return mapper.toEResultSet(xs, count);` |
| Not reached by E6a: return a `*ResultSet` but sit in unparseable placeholder-file modules | roughly 23 | vendor, user, staff, notification, maintenance, inspection, file, community, chat, admin; invisible by parse skip, not by wrapper (an E1 residue, genuinely invalid `$IMPORT` source) |
| OPAQUE (no provable repository-of-E tie) | the proxy reads | `PropertyMgtProxyInternalServiceImpl#findPropertiesByBuildingIds` returns `List<PropertyProxyResult>` from a proxy service call, not a repository of an entity; refused `OPAQUE_WRAPPER` |

The decisive facts:

- Every transparent wrapper read on pms is filtered. Each body calls `eRepository.selectAllByFilters(filter)`
  (a hand-written query builder) with domain parameters. So T2 makes them visible, and 4.10 then refuses
  them as filtered. Zero of them discover.
- The generic tie (T1) matters for generality and for a future app, but is inert here, so pms transparency
  rests entirely on the body tie (T2).

Forecast (build deferred; framed as visibility, not discovery):

- Visibility gained: roughly 12 reads move from invisible to visible-and-classified, all in property,
  lease, and billing. All roughly 12 are VISIBLE-BUT-REFUSED-FILTERED (named deferrals); new discoveries 0.
- The roughly 23 `*ResultSet` reads in the ten unparseable modules stay parse-skipped; E6a does not reach
  them.
- Row (projected, build deferred): discovered 2 (by-id 2, list 0), UNCHANGED; visibility gained roughly 12
  (all filtered deferrals); mis-bound 0; getUnit and getLease byte-identical. The headline is visibility,
  not discovery: the deferral ledger becomes complete for the parseable modules, and it confirms
  structurally that pms's wrapper reads are all filtered, feeding the E6b decision.
- Named uncertainty: the roughly 12 is bounded by how many `*ResultSet` reads sit in parseable service
  files (property, lease, billing); firm at build time.

### 8.11 Entity Identity forecast (pms-backend, read-only, 2026-07-13)

Phase Entity Identity (Volume 4 section 3.7, Volume 5 section 4.12) adds a qualified `key` to each entity
and derives the display `name` from it, disambiguating only where simple names collide across bounded
contexts. This forecasts the phase against pms-backend, before any build.

Collision census: pms-backend has NO colliding simple names among its 43 entities; every `@Entity`
simple name is unique across the app. So the display-name rule's condition 1 applies to every entity
(unique simple name, display equals simple name), and no entity is renamed.

Forecast (build deferred): byte-identical in generated output. Row (projected): discovered 2 (by-id 2,
list 0), failed 41 (NO_BACKING_METHOD 38, COMPOSITE_KEY 3), skipped-by-parse 27, mis-bound 0, UNCHANGED.
getUnit and getLease keep their names and bindings exactly; the MCP manifest and the two-operation
OpenAPI document are byte-identical; the survey's `collisions resolved` line is omitted (zero). The only
change is additive in the serialized IR: each extracted entity gains a `key` field (its fully-qualified
class name), while reference tokens and display names are unchanged, so an IR-serialization golden
updates with a visible additive diff and the backend goldens stay byte-identical. Any colliding simple
name found at build time (this read-only census found none) would be a regression finding, recorded
then. mis-bound stays 0.

### 8.12 Entity Identity actual (pms-backend, built and measured, 2026-07-13)

The phase is built (Volume 5 section 4.12). Re-run against pms-backend, the forecast is confirmed
exactly. Row (actual): discovered 2 (by-id 2, list 0), failed 41 (NO_BACKING_METHOD 38, COMPOSITE_KEY 3),
list-refused 0, skipped-by-parse 27, partial 0, mis-bound 0, UNCHANGED. The 3 deferred list reads are
unchanged. The read-only census held: no colliding simple names were found at build (0 regressions), so
every entity kept its simple name as its display name.

Byte-identical proven, not asserted. The generated output (the MCP tool sources and
`tibyan/capabilities.json`, and the OpenAPI document) was generated on the current build and on the
pre-phase commit (parent of the first Entity Identity commit) and compared with `diff -r`: zero
differences. getUnit and getLease keep their names and bindings exactly. The only change is the additive
`key` field now carried on each serialized entity. mis-bound stays 0.

### 8.13 Discovery-Gap forecast (pms-backend, read-only, 2026-07-13)

Phase Discovery-Gap (Volume 5, section 4.13, designed 2026-07-13) adds two general provider rules,
inherited `@Id` (following an in-tree `@MappedSuperclass` or `@Inheritance` parent to the entity's single
`@Id`) and the private-helper hop (stepping through one private same-class delegation to reach a clean
by-primary-key load). This forecasts the phase against pms-backend, before any build.

Shape census (read-only): pms-backend exhibits NONE of the three shapes. Every one of its 43 entities
declares a direct `@Id`, and the tree contains no `@MappedSuperclass` and no `@Inheritance`, so the
inherited-`@Id` rule never fires. Its reads load directly in the service body or through a hand-written
repository port whose in-tree `@Repository` implementation is the existing section 4.2 condition 4b
one-hop (already handled), never through a private same-class helper, so the helper-hop rule never fires.
No port has an implementation outside the scanned source.

Forecast (build deferred): byte-identical. Row (projected): discovered 2 (by-id 2, list 0), failed 41,
skipped-by-parse 27, mis-bound 0, UNCHANGED. getUnit and getLease keep their names and bindings; the MCP
manifest and OpenAPI document are byte-identical. Any occurrence of one of these shapes at build time
(this read-only census found none) would be a finding, recorded then. mis-bound stays 0.

### 8.14 Discovery-Gap actual (pms-backend, built and measured, 2026-07-13)

The phase is built, general scope only (Volume 5, section 4.13). Re-run against pms-backend, the
forecast is confirmed exactly. Row (actual): discovered 2 (by-id 2, list 0), failed 41 (COMPOSITE_KEY 3,
NO_BACKING_METHOD 38), list-refused 0, skipped-by-parse 27, partial 0, mis-bound 0, UNCHANGED.

The read-only census held: pms-backend exhibits none of the three shapes, so neither general rule fires.
Every entity declares a direct `@Id` (the inherited-`@Id` walk is never entered), and no read delegates
its load to a private same-class helper (the helper hop is never entered; pms's one-hop load through an
injected repository port is the existing condition 4b, unchanged). No new refusal message appears.

Byte-identical proven, not asserted: the generated MCP tool sources, `tibyan/capabilities.json`, and the
OpenAPI document were generated on the current build and on the pre-build commit and compared with
`diff -r`: zero differences. mis-bound stays 0, and there is nothing for the new traces to mis-bind here
because neither fires.

### 8.15 Controller-Authority census and forecast (pms-backend, read-only, 2026-07-19)

Phase Controller-Authority (Volume 5, section 4.14; the first real-user-requested phase) was designed
from a full read-only census of the three benchmarks. This section records the pms half and the pms
forecast. No tool change was made in this session.

The controller census:

- Concrete controller beans: 36 (27 `@RestController`, 9 `@Controller` whose handlers return
  `ResponseEntity`), plus 42 GENERATED `@Controller` interfaces (the OpenAPI-generator split idiom: the
  interface carries `@RequestMapping(method = RequestMethod.GET)` and the `@PathVariable`/`@RequestParam`
  annotations; the concrete class implements it and carries the body and the security annotation). One
  controller file is unparseable (the WebSocket STOMP controller, a `$IMPORT_LINE` placeholder defect) and
  is already named by the E1 skip list; 35 of 36 concrete controllers parse.
- GET endpoints: roughly 90 distinct (89 `@RequestMapping(method = GET)` declarations, 6 of them
  duplicated between a standalone reports controller and its dead generated interface, plus 7 direct
  `@GetMapping` in the lease controller). For context: POST 59, PUT 16, DELETE 23, PATCH 16.
- GET parameter shapes: roughly 20 by-id (exactly one `@PathVariable`, `Long` or `String`), roughly 40
  filtered (`@RequestParam` filters plus a hand-rolled `pageNum`/`pageSize`/`pageOffset` Integer triple;
  Spring `Pageable` appears nowhere), roughly 11 no-parameter (mostly "me"-scoped reads that pull the
  caller id from a request context, not parameters), 0 `@RequestBody`-on-GET.
- GET return types: every GET returns `ResponseEntity<X>`; X is a per-resource `*ResultSet` envelope
  (roughly 45), a `*VTO` (roughly 30), summary or chart types (roughly 7), generated `*200Response`
  wrappers (2), `List<...>` (2), `byte[]` (2), `Integer` (1).
- GET bodies: overwhelmingly one-statement `return ResponseEntity.ok(service.method(args));` (roughly 80
  of 90); the remainder are two-to-three-statement bodies (a request-context read first). No try/catch,
  no assembly logic in GET handlers.
- Security: `@Secured` ONLY. 119 occurrences, all method-level, all on controllers (none on services),
  every value a statically-imported `public static final String` role constant from the user module
  (`PROPERTY_MANAGER_ROLE = "1"`, `SUPERINTENDENT_ROLE = "2"`, `RESIDENT_ROLE = "3"`), in one-to-three
  element arrays. ZERO `@PreAuthorize`, `@PostAuthorize`, `@RolesAllowed`, `@PreFilter`, `@PostFilter`.
  Enablement: `@EnableMethodSecurity(securedEnabled = true)` in the in-tree security-adapter library; no
  legacy `@EnableGlobalMethodSecurity`. URL layer: one active `SecurityFilterChain` (public paths
  `permitAll`, then `anyRequest().authenticated()`), which the tool never reads, and which is why absence
  of a method-level declaration is never presented as "unprotected".
- The two live anchors, verified from source: `UnitControllerImpl#_getUnitById` is exactly
  `return ResponseEntity.ok(unitService.findById(unitId));` under
  `@Secured({PROPERTY_MANAGER_ROLE, SUPERINTENDENT_ROLE})`, with its GET mapping and `@PathVariable` on
  the generated `UnitController` interface; `LeaseControllerImpl#_getLeaseById` is exactly
  `return ResponseEntity.ok(leaseService.findById(leaseId));` under `@Secured({PROPERTY_MANAGER_ROLE})`,
  mapping on the generated `LeaseController` interface. Those two service methods ARE the discovered
  bindings of getUnit and getLease.

Forecast (after the Controller-Authority build, stated before it):

- discovered 2 (by-id 2, list 0), UNCHANGED; failed, skipped, partial rows UNCHANGED; mis-bound 0.
- getUnit gains `requiredAuthorities ["1", "2"]` and getLease gains `requiredAuthorities ["1"]`: both
  anchors are strict one-statement delegations, both `@Secured` declarations are single-mechanism
  method-level, every constant resolves in-tree to a single string literal, and the modern-enablement
  gate passes. The compiled values are the literal strings "1" and "2", carried verbatim; the tool never
  prettifies them into the constant names, because the literal is what the security layer matches.
- The MCP manifest and the OpenAPI document change ADDITIVELY on exactly those two capabilities (the new
  field plus one description clause each; `x-tibyan-required-authorities` on the OpenAPI operations);
  every other byte is identical. The goldens update deliberately with a visible diff, the E2 view-field
  precedent.
- Assumption to verify at build: no OTHER GET handler invokes `unitService.findById` or
  `leaseService.findById` (the associated-endpoint sweep must find exactly one route each). A second
  associated route would move the state to `securityUnverifiable (conflicting-declarations)` per the
  composition rule, and finding one is a forecast deviation to record, not to absorb silently.
- Everything else is inert here: the roughly 40 filtered GETs refuse as deferred filtered reads and their
  `@Secured` contracts attach to nothing (no orphan authorities); the `*ResultSet` list endpoints stay
  outside rule 4.10's pool exactly as before; the unparseable WebSocket controller stays a named E1 skip.
### 8.16 Controller-Authority actual (pms-backend, built and measured, 2026-07-19)

The phase is built (Volume 5, section 4.14) and re-run against pms-backend. The forecast held to the
number.

- Row (actual): discovered 2 (by-id 2, list 0), failed 41 (COMPOSITE_KEY 3, NO_BACKING_METHOD 38),
  list-refused 0, skipped-by-parse 27, partial 0, mis-bound 0, UNCHANGED. Every discovered, failed, and
  skip count is identical to the pre-phase row: the phase added no capability and moved no binding.
- Declared security: `security: declared 2 (compiled 2, unverifiable 0)`. Both discovered reads compiled
  a contract through their strict controller anchors, exactly as forecast:
  - getUnit compiled `requiredAuthorities ["1", "2"]` from `UnitControllerImpl#_getUnitById` under
    `@Secured({PROPERTY_MANAGER_ROLE, SUPERINTENDENT_ROLE})`, the constants resolving in-tree (static
    imports of `UserRoles`) to the literals "1" and "2".
  - getLease compiled `requiredAuthorities ["1"]` from `LeaseControllerImpl#_getLeaseById` under
    `@Secured({PROPERTY_MANAGER_ROLE})`, resolving to "1".
  The modern-enablement gate passed (`@EnableMethodSecurity(securedEnabled = true)` in the in-tree
  security-adapter library; no `@EnableGlobalMethodSecurity`). The forecast assumption held: the
  associated-endpoint sweep found exactly one GET route for each of the two bindings, so both COMPILED
  rather than turning conflicting; no second route exists.
- Generated output changed ADDITIVELY on exactly those two entries and nowhere else. The MCP manifest
  gained one `requiredAuthorities` field and one description clause per entry ("Requires any of the
  declared authorities: 1, 2." and "... 1."), and the OpenAPI document gained one
  `x-tibyan-required-authorities` extension and one summary clause per operation. Every other byte is
  identical.
- mis-bound 0, hand-verified and now covering the security contract: getUnit carries exactly its own
  anchor's [1, 2] and getLease exactly its own [1], never swapped, never crossed. A wrong authority would
  be a mis-bind, and there is none. The bindings are unchanged (UnitServiceImpl#findById,
  LeaseServiceImpl#findById), hand-verified against source.

## 9. RE-BASELINE (2026-07-20): the parse defects were repaired, discovered 2 to 5

The standing pms baseline is re-based here. This is an EXTERNAL event, not a tool phase: the adopter
repaired the 27 placeholder-corrupted source files, so the app now parses 100 percent clean. Every dated
row above (2026-07-02 through 2026-07-19) measured the CORRUPTED copy and stands as an accurate record of
that state; from 2026-07-20 the standing baseline is the clean copy recorded here.

Honest finding, stated plainly because it matters for benchmark integrity: the "27 unparseable files" were
NOT genuinely unparseable business code. They carried trivially-repairable placeholder tokens
(`$IMPORT`/`$IMPORT_LINE` bare lines, and a `nimport` mangle where a template's `\n` lost its backslash),
confined to the import block of each file, class bodies intact, at most 1 to 2 bad lines each. They were an
artifact of how the benchmark copy was produced, not a property of the real application. A meaningful part
of pms's apparent hostility (the standing "skipped-by-parse 27") was therefore an artifact; repairing it
unlocked three real by-id reads that were always there, locked behind corrupted service files.

New standing baseline (measured 2026-07-20, tool unchanged from the 0.2.0 line plus the Controller-Authority
build):

- discovered 5 (by-id 5, list 0): getUnit, getLease, getStaff, getUser, getVendor. The three new reads,
  Staff, User, and Vendor, are each the clean by-primary-key-load-then-map shape rule 4.2 already discovers
  (StaffServiceImpl#getStaffById, UserServiceImpl#getUserById, VendorServiceImpl#getVendorById, each a
  one-hop `selectById` through a hand-written repository to a Spring Data `findById`, mapped once to a VTO),
  previously invisible only because their service files did not parse.
- failed 38 (COMPOSITE_KEY 3, NO_BACKING_METHOD 35), list-refused 0, skipped-by-parse 0 (was 27), partial 0,
  mis-bound 0. Entities found (parseable) 43, unchanged: the corrupted files were services and one
  controller, never entity files, which is why the entity denominator held at 43 while three bindings were
  locked.
- Deferred list reads 9 (was 3): now that billing, vendor, chat, staff, and the other repaired modules
  parse, more filtered `*ResultSet` and `List<VTO>` reads are visible and correctly DEFERRED. This is
  honest visibility, not new coverage; those reads are the filtered majority and stay refused as before.

Declared security (phase Controller-Authority): `declared 4 (compiled 3, unverifiable 1)`.

- getUnit compiled `["1", "2"]`, getLease `["1"]`, getStaff `["1"]`, each from a strict one-statement
  controller anchor under `@Secured` role constants resolving in-tree, mis-bound 0 (each carries exactly its
  own anchor's authorities).
- getVendor is `unverifiable (conflicting-declarations)`, hand-verified as correct: its only fronting GET,
  `VendorControllerImpl#_getVendorById(String vendorId)`, takes a STRING path variable and calls
  `vendorService.getVendorById(Long.parseLong(vendorId))` in a two-statement body, so it is an associated
  but NON-STRICT anchor (the argument is a coercion, not the verbatim id; the body is not the one-statement
  delegation). The `@Secured({PROPERTY_MANAGER_ROLE})` on it is clean, but the tool will not certify a
  contract through a route whose id forwarding it cannot prove, so it marks the read unverifiable rather
  than attach an authority it did not verify. A ratified `String`-to-`Long` id-coercion tolerance (a named,
  cheap future decision) would flip this to compiled `["1"]`.
- getUser is ABSENT: no method-level security declaration was found on its fronting route (not a claim it is
  unprotected; the app gates every request behind a `SecurityFilterChain` the tool does not read).

What did NOT change, and why the headline is still honest: mis-bound 0. The filtered majority is untouched,
43 filtered GET endpoints across the app (enumerated 2026-07-20; the ledger's earlier "~40" figure), the
roughly 33 `*ResultSet` envelope reads, the aggregate reads (Building, Property, ChatRoom), the me-scoped
context reads, and Tenant's inner-`JOIN FETCH` custom query, all stay correctly refused. Those are the
Attestation territory: a structural tool tops out here, and the honest path to the filtered surface is
human-attested semantics, not a guess. The parse repair is rung 1 of the coverage ladder (Volume 13,
Attestation section, and the 2026-07-20 coverage census); it moved discovered 2 to 5, and the filtered
volume still needs Attestation.

Impact on the project-wide totals: the two-public-benchmark supported total moves 27 to 30 (erp-backend 25,
pms-backend 5), and the three-benchmark total moves 31 to 34 (plus the private enterprise benchmark 4).
These are standing-fact updates; the historical "27 capabilities" is preserved as the v1-complete milestone
record (measured 2026-07-02 against the corrupted pms copy).

Ledger correction folded in from the 2026-07-20 census (now moot since all files parse, recorded for
accuracy): section 8.10's module attribution was backwards against the source it described, it placed the
transparent parseable `*ResultSet` reads in "property, lease, billing" and listed chat among the unparseable
modules, whereas the billing service impls were the corrupted ones and the chat service impls parsed; the
GET denominator is exactly 89 (the ledger's "~90"); the `*ResultSet` reads count 33 (the ledger's "~35").

## 10. Attestation forecast: the attestable ceiling (pms-backend, read-only, 2026-07-20)

Phase Attestation (Volume 5, section 4.15) was designed with pms as the motivating case: the maintainer,
testing as a real adopter, hit the filter ceiling here (5 tools of an 89-GET read surface, section 9) and
the written trigger fired. This section restates the refusal ledger in the new vocabulary, per attestable
category, and states the ceiling honestly: ATTESTABLE UP TO N GIVEN A KNOWLEDGEABLE HUMAN, never a
promise. All numbers are against the repaired copy (section 9) and the 2026-07-20 coverage census (89
GETs exact).

Per sub-phase, at the GET-endpoint denominator:

- AT-0 (structural floor and envelope unwrap), the RE-BASED E6a forecast: the ratified 2026-07-02 E6a
  forecast (section 8.10, roughly 12 visible and roughly 23 parse-skipped) predated the parse repair; on
  the repaired copy ALL of pms parses, so all roughly 33 `*ResultSet` service reads become
  VISIBLE-BUT-REFUSED-FILTERED via the T2 body tie, and ZERO stay parse-skipped. New discoveries: 0
  (every envelope read is filtered, exactly as the E6a design predicted). The P1b floor matcher changes
  Tenant's near-miss wording to the ratified inner-fetch refusal; counts byte-identical. Discovered stays
  5, mis-bound 0.
- AT-1 (predicate attestation): attestable up to 44 endpoints: the 43 FILTERED GETs (the envelope list
  reads, the filtered-direct reads, the me-scoped paginated reads) plus Tenant's existence-condition by-id
  read (the P1b inner-fetch shape, attested as "returns the tenant with this id only if it has an
  associated user"). The 16-read semantic sample (2026-07-20 census) is the honest calibration: a
  knowledgeable attester could cleanly attest about 10 of 16 and should honestly DECLINE about 6 of 16,
  whose semantics no one-sentence assertion can carry truthfully (dual-identity columns: billing tenantId
  stores User ids; staffId stores staff-or-user depending on write path; hidden inner-join existence
  filters on nullable associations). Extrapolated, the realistic AT-1 outcome is roughly two thirds of
  the ceiling, and the declined third is the attester's honesty working, not a defect.
- AT-2 (shape attestation): attestable up to 8 more (CORRECTED 2026-07-21 by the Gate 1 verification,
  section 11; the earlier "7" was cited to an unrecorded census and was wrong): the aggregate by-id reads
  `BuildingServiceImpl#findById`, `PropertyServiceImpl#findById`, `ChatRoomServiceImpl#getChatRoomById`,
  `TicketServiceImpl#getTicketById`, `GroupServiceImpl#getGroupById`, `EventServiceImpl#getEventById`,
  `UserManagementServiceImpl#getUserDetails`, and
  `PropertyMgtProxyInternalServiceImpl#findPropertyByBuildingId`, each with its structurally proven
  by-primary-key load and an attested enrichment sentence. The previously-listed "payments summary" is NOT
  in this bucket: `getPaymentsSummary` performs no by-primary-key load at all (its only access is
  `selectAllByFilters`), so it is a filtered read and belongs to the PREDICATE category. Opaque-wrapper residue
  after AT-0 is expected near zero here (every `*ResultSet` ties via T2 on this app).
- AT-3 (boundary, deferred): 1 candidate today, getVendor's security contract (unverifiable,
  conflicting-declarations, section 9); pms has no delegation reads. Not attestable until AT-3 is
  separately ratified.
- EXCLUDED, staying refused regardless of attestation (counts CORRECTED 2026-07-21, section 11): the 11
  context reads under the strict zero-argument reading (me-scoped, the missing runtime principal cannot be
  asserted into existence; 13 if the two context reads whose shape is binary or scalar are counted here,
  21 if context-reading paginated reads are included, and those wider readings overlap the filtered
  count), the 9 declared binary and stub endpoints of which 8 are live-implemented (downloads, counters,
  the health stub, the two hardcoded empty-ResultSet stubs: no read exists to describe), and the 2
  controller-level String-to-Long coercion shapes, only 1 of them a by-id read (a structural tolerance
  decision, named, not attestation).

Accounting note (stated honestly rather than forced to add up): the 89-GET buckets predate the parse
re-baseline, so they do not close cleanly against the post-repair numbers. The pre-repair census bucketed
2 backed-by-discovered, 43 filtered, 19 by-id-blocked, 10 binary and stub, 9 context, 6
unparseable-backed; the repair promoted 3 by-id-blocked reads (Staff, User, Vendor) to discovered and
resolved the 6 unparseable-backed into their real buckets, so the by-id-blocked, filtered, and residual
reads redistribute across the attestable and excluded columns. The exact partition of all 89 is precisely
what AT-0's complete worklist produces; until it runs, the ceiling below is APPROXIMATE, and the roughly
11 endpoints not itemized in the attestable-or-excluded lists above are the reclassified
by-id-blocked and unparseable-backed reads AT-0 assigns.

The ceiling, stated in one line as an ESTIMATE (not a computed count; the Gate 1 verification, section 11,
corrected the per-category numbers and found the 89 denominator is a DECLARED count including roughly 7
mappings on interfaces no class implements): 5 verified + up to roughly 45 (AT-1, the 43 filtered GETs plus
Tenant's existence-condition read plus the reclassified payments summary) + up to 8 (AT-2) = ATTESTABLE UP
TO roughly 57 of 89 declared GETs, about 64 percent of the declared surface and about three quarters of the genuinely
tool-worthy reads, IF a knowledgeable human attests everything attestable, which the sample says a
truthful attester will not do. Attested counts are reported SEPARATELY from discovered, never summed:
the health line becomes "discovered 5, attested K, mis-bound 0, attestation-drift 0".

The hard bar, extended: every existing discovered row (the 5 verified reads, their bindings, their
security contracts) stays byte-identical through every Attestation sub-phase; movement is a regression
finding that stops the build. Mis-bound 0 continues to cover bindings and carriage; the truth of an
attested sentence is the named asserter's, labeled as such on every surface.

THE OPEN VERIFICATION, named: the real attestation rate (attested versus declined versus
refused-at-intake), the time per attestation, and the staleness churn under real development are
unmeasurable without a real attesting human. The first-use measurement is pre-registered in the design
(Volume 5, section 4.15.11): per-entry time, assist usage, intake refusals, declines with reasons, and
one month of invalidation counts. pms, with the maintainer as the knowledgeable human, is the designated
first measurement.

## 11. GATE 1: forecast traceability verification (pms-backend, read-only, 2026-07-21)

Before ratifying the Attestation design (Volume 5, section 4.15), every number in its forecast was traced
to a source: a committed census row, a ledger row, or an explicit counted sample. This section is that
verification, and it is also the correction it forced. THE HEADLINE: the forecast FAILED traceability on
its pms per-category counts, so ratification did NOT proceed and AT-0 was NOT built this session.

THE ROOT CAUSE, stated plainly because it is a process finding, not just an arithmetic one: several pms
numbers were cited to "the 2026-07-20 coverage census," a real, adversarially-verified 5-agent
enumeration, which was NEVER RECORDED IN THIS LEDGER. It lived only in a session artifact. The project's
own standard is that the ledgers are the published evidence and a stranger can audit the numbers without
the source; a number whose only basis is an unrecorded run does not meet it. Re-verifying those numbers
against source (2026-07-21, read-only) found that four of four were wrong.

Traced and VERIFIED (committed source, unchanged):

| Number | Claimed basis | Actual source | Verdict |
|---|---|---|---|
| pms 89 GET endpoints | 2026-07-20 census | section 9 correction ("GET denominator is exactly 89") | VERIFIED as a DECLARED count, see the caveat below |
| pms discovered 5 | section 9 | section 9, measured re-baseline | VERIFIED |
| pms 33 `*ResultSet` reads | section 9 | section 9 correction ("the `*ResultSet` reads count 33") | VERIFIED |
| pms 43 filtered GETs | section 9 | section 9 ("43 filtered GET endpoints, enumerated 2026-07-20") | VERIFIED |
| pms Tenant existence-condition read (+1 to 44) | P1b census | section 8.8 and Volume 5 section 4.2.10.1 (the inner-fetch refusal) | VERIFIED |
| pms AT-0 visibility: all 33 become visible, 0 parse-skipped | re-based E6a | section 8.10 (the T2 census) plus section 9 (skipped-by-parse 0) | VERIFIED |
| erp up to ~30 (AT-1) and ~14 (AT-2), ~44 total | ledger section 15 | section 15's census table (~30 filtered, ~9 projection, 5 transformed) | VERIFIED |
| enterprise 6 guarded, 12 aggregate, 15 delegation | ledger section 6.1 | section 6.1's by-id census | VERIFIED |
| enterprise 2 genuine filtered query reads of 31 deferred | ledger section 5 | section 5, RANK 3 | VERIFIED |

Traced and UNSUPPORTED, corrected against source (2026-07-21 enumeration, read-only):

| Number | Claimed basis | What source says | Verdict |
|---|---|---|---|
| AT-2 attestable "up to 7" aggregate by-id reads | 2026-07-20 census (unrecorded) | EIGHT, not seven. Six of the seven named are confirmed (Building, Property, ChatRoom, Ticket, Group, Event); the "payments summary" is REFUTED (`RentalPaymentServiceImpl#getPaymentsSummary` has NO by-primary-key load, its only access is `selectAllByFilters`, so it is a filtered read and belongs to the PREDICATE category, not SHAPE); and TWO were missed (`UserManagementServiceImpl#getUserDetails`, a by-pk load plus a second repository access plus view mutation, and `PropertyMgtProxyInternalServiceImpl#findPropertyByBuildingId`, three persistence accesses behind one id) | CORRECTED to 8 |
| EXCLUDED "9 context reads" | 2026-07-20 census (unrecorded) | ELEVEN under the strict reading (zero-argument handler, identity from RequestContext in the handler or its service), 13 if the two context reads whose shape is binary or scalar are counted here, and 21 if context-reading paginated reads are included. Under no reading is it 9 | CORRECTED to 11 (strict), with the wider readings named |
| EXCLUDED "10 binary and stub endpoints" | 2026-07-20 census (unrecorded) | NINE declared, of which 8 are live-implemented; the tenth was reachable only by double-counting an interface-only download that no class implements | CORRECTED to 9 declared, 8 live |
| EXCLUDED "3 String-to-Long coercion shapes" | 2026-07-20 census (unrecorded) | TWO controller-level coercions (`_getVendorById`, `_getVendorsByBuilding`), and only ONE of those is a by-id read. The 3 came from conflating controller-level coercion with service-level coercion: at least nine other handlers pass a raw `String` through and the `Long.parseLong` happens one layer down in the service, which is a different shape | CORRECTED to 2 (1 strictly by-id) |

Marked as an ESTIMATE with its basis named (not a census, and not correctable by enumeration):

- "A knowledgeable attester could cleanly attest about 10 of 16 and should DECLINE about 6 of 16." This is
  a JUDGMENT SAMPLE, not a count: 16 filtered reads were traced to their query builders on 2026-07-20 and
  judged one by one for whether a single honest sentence could carry their semantics. Its basis is that
  per-read tracing; its limit is that it is 16 of 43 and the judgment is one reviewer's. It stands as a
  calibration estimate, explicitly labeled, never as a forecast count. The real rate is the phase's named
  open verification and is unmeasurable without a real attesting human.

One further caveat the verification surfaced, recorded so the denominator is not over-trusted: the 89 is a
count of DECLARED GET mappings, and roughly seven of them sit on generated interfaces that NO concrete
class implements (three admin controllers, three property controllers, one lease controller), so they are
declared but not live. The live implemented GET surface is smaller than 89. Any percentage stated against
89 is therefore a percentage of the declared surface, and the exact live partition is precisely what
AT-0's worklist produces.

Net effect on the pms ceiling: the AT-1 and AT-2 category counts move (AT-2 from 7 to 8, with one member
reclassified from SHAPE to PREDICATE), and the excluded counts move (context 9 to 11, binary 10 to 9,
coercion 3 to 2). The ceiling is therefore restated as an ESTIMATE of roughly 57 of 89 declared GETs
rather than a computed 56, and it stays explicitly approximate until AT-0's worklist produces the exact
partition. This is the same conclusion the section 10 accounting note reached, now with the specific
numbers corrected and the reason recorded.

## 12. AT-0 actual: the structural floor and envelope unwrap, built and measured (pms-backend, 2026-07-24)

AT-0 built the two already-ratified rules (E6a envelope transparency, Volume 5 section 4.11.7; the P1b
strict by-primary-key custom-query matcher, section 4.2.10), provider-only, no core change and no
attestation machinery. This section records the actual run against the re-baselined pms copy (the repaired
source, section 9), the worklist section 11 said AT-0 would produce.

Headline invariants, all held: discovered 5 (by-id 5, list 0), BYTE-IDENTICAL to the section 9 baseline
(getUnit, getLease, getStaff, getUser, getVendor, same bindings); mis-bound 0; skipped-by-parse 0; ZERO
new discoveries. P1b discovers zero: pms has clean by-primary-key `@Query` shapes, but every one that
feeds a discoverable read fails the strict rule (Tenant's `findByIdWithRelations` fails on its inner
`JOIN FETCH t.user`, an existence filter, exactly the P1b-5 refusal), so the floor draws its line and adds
no capability, precisely the ratified forecast.

The E6a visibility gain (the AT-0 product, the completed worklist). The wrapper reads that were previously
NOT_LIST (silently unseen by the list survey) are now named deferrals. The list survey's E6a-surfaced
partition:

- 51 `*ResultSet` reads refuse as MULTI-READ ASSEMBLY: each builds its wrapper from more than one
  repository access (the data query plus a count/total query), which 4.10 condition 7 refuses (more than
  one repository access is not a single provable read). These are the filtered list reads the E6 census
  called out; they are now visible and countable rather than unseen.
- 5 reads refuse OPAQUE_WRAPPER: `getMyActiveLease` (LeaseVTO), `getPaymentsSummary` (PaymentsSummary),
  `getCostsByCategory` and `getPaymentsCollection` (ChartSeries aggregates), and `findByEmail`
  (UserProxyResult). Each is a single collection access on a hand-written repository interface
  (`LeaseRepository`, `RentalPaymentRepository`, `CostRepository`, `UserRepository`) that does not resolve
  to a Spring Data repository of an in-tree entity, so the tie to an entity is not structurally provable.
  These are the aggregate and proxy reads (AT-2 SHAPE and AT-3 BOUNDARY territory); AT-0 makes them
  visible and refuses them honestly, never guessing a tie from the wrapper's name.
- 9 `List<VTO>` reads stay deferred as view/projection list reads (4.10.6); these were already deferred
  before E6a (the return type is a recognized container of a non-entity), so they are not an E6a change.

Correction to the 2026-07-02 E6a forecast (section 8.10), recorded honestly. That forecast, written against
the corrupted copy, predicted the `*ResultSet` reads would surface as "visible-but-refused-filtered" via a
body tie to a Spring Data repository of E. The repaired copy shows two refinements: (1) the `*ResultSet`
reads refuse more precisely as MULTI-READ ASSEMBLIES (they carry a count query alongside the data query),
a 4.10 condition-7 refusal, not a plain filtered-parameter refusal; and (2) the tie the forecast assumed
(a direct Spring Data repository of E) does not hold for pms's hand-written repository interfaces, whose
Spring Data delegate sits one hop away (the same one-hop shape the discovered reads use via
`selectById`/`RepositoryImpl`), so the single-access aggregate reads refuse OPAQUE_WRAPPER rather than
filtered-deferred. The invariants the forecast asserted (discovered byte-identical, mis-bound 0, visibility
gained, zero discoveries) all hold; only the deferral labels are more precise. E6a's implemented tie is the
direct Spring Data resolution; resolving the one-hop hand-written-interface delegation for E6a is a named,
build-deferred refinement (no benchmark case turns on it: the multi-read reads refuse on condition 7 first,
and the single-access aggregate reads are AT-2/AT-3 semantics the human attests, not reads a wider tie
should discover).

A build-time correction, flagged (rule 13, and it changed a matching outcome so it is not merely
diagnostic). The first E6a cut decided list-vs-by-id from the framework read names (`findById`), so pms's
`selectById`-shaped by-id reads were wrongly surfaced as OPAQUE_WRAPPER list-deferrals, including
`getUserById` and `getVendorById`, which the by-id path already discovers (getUser, getVendor). This was a
deviation from the ratified section 4.11.7 (V): a single-entity tie is a by-id candidate owned by
4.1/4.2/P1b, never the list route. The fix decides cardinality from the repository method's DECLARED return
type, so a non-collection access is left to the by-id path and never surfaced. This dropped the opaque
count from 22 to the 5 genuine aggregate/proxy collection reads and removed the contradiction of a
discovered read appearing as a deferral. The exact worklist above is the post-fix, ratified-faithful
partition.

`mvn clean verify` is green, the MCP and OpenAPI goldens and the example-app e2e byte-identical (getProduct
unchanged: the example app has no wrapper or custom-query shapes). The worklist this section records is the
input AT-1 consumes: the 51 multi-read and 5 opaque reads are the visible refusals a human can later attest
for what their predicates and shapes mean.

## 13. AT-1 actual: the attestation core and the PREDICATE category, built (pms-backend, 2026-07-25)

AT-1 built the attestation core (the first-class `Attestation` component in `tibyan-core`, core rules 8
and 9, the committed `tibyan/attestations.json` intake, the SHA-256 fingerprint lifecycle) and the
PREDICATE category. This section records the benchmark measurement and pre-registers the first real
measurement.

Headline invariant, held: pms discovered 5 (by-id 5, list 0), BYTE-IDENTICAL to the AT-0 baseline
(getUnit, getLease, getStaff, getUser, getVendor, same bindings); mis-bound 0; skipped-by-parse 0. The
survey Totals line is character-identical to the AT-0 row: pms carries NO committed `attestations.json`,
so the intake reads no entries, adds no attested capability, and prints no attested block. ATTESTED IS 0.
The phase is provably additive: with no record file the model and every outcome are byte-identical
(fixture ATT-11), and the two exposure controls plus the survey attested class only appear once a record
exists.

THE MECHANISM IS PROVEN ON FIXTURE CODE, NOT ON THE BENCHMARK. Per the ratified first-measurement
discipline, no attestation was authored against pms this session; the end-to-end proof (intake,
validation, the fingerprint lifecycle, the IR, both backends, the survey, all carriage fixtures) runs on
synthetic fixture code (`attest-predicate`, and the CLI `attest`-then-`scan` flow), so the first pms
numbers are not polluted by tool-authored guesses.

THE FIRST REAL MEASUREMENT, pre-registered and PENDING: pms-with-the-maintainer, a later interactive
session. The maintainer works through the pms PREDICATE worklist (the AT-0 refusal ledger, section 12: 51
multi-read `*ResultSet` reads and the filtered single reads), attesting each read's filter or guard
meaning or declining it, item by item, through the real `tibyan attest` and intake mechanism now built.
That session records, per entry, the attested count, the declined count with reasons, the intake-refusal
count with causes, and the real attestation rate against the Gate-1-corrected forecast (section 10: up to
roughly 45 predicate reads, with the 16-read judgment sample predicting a truthful attester declines
about a third). Until that human runs the flow, the design's central quantity, the real attestation rate,
is unmeasured and stated as unmeasured (section 4.15.11, the open verification). This build proved the
mechanism carries a human's words character-exactly and refuses everything it cannot structurally frame;
it did not, and could not, measure how many pms reads a knowledgeable human will actually attest.

`mvn clean verify` is green, the MCP and OpenAPI goldens and the example-app e2e byte-identical
(getProduct carries no attestation, so every existing manifest byte is unchanged).

## 14. First-measurement ATTEMPT on pms: zero attestations, three findings (2026-07-25)

The first measurement was attempted: the maintainer set out to attest the pms PREDICATE worklist through
the real mechanism. It produced ZERO attestations and three findings of three different kinds, which is
worth more than an attestation count. Recorded honestly, because a first contact with reality is supposed
to produce exactly this. Discovered stayed 5, byte-identical; mis-bound and attestation-drift 0.

Method: three representative pms reads (one each from property, notification, billing) were run through
the real intake as throwaway structural probes (obviously non-semantic content, in the gitignored pms
tree, never committed, deleted after). None reached the point where the maintainer's assertion would
matter; each was stopped by a STRUCTURAL gate that fires before the semantic hole, two of them by a crash.

FINDING 1, a BUG (fixed this session, commit 33678ae). `TenantServiceImpl#getAllByFilter` failed at
condition 3 (below), but `NotificationServiceImpl#getNotificationsForUser(Long)` and
`RentalPaymentServiceImpl#getPaymentsSummary(String)` CRASHED the scan with a NullPointerException at
`ReadSafety.checkBody`: `MUTATING_INFRA_TYPES` is an immutable `Set`, whose `contains(null)` throws, and
`fieldTypes.get(receiverField)` is null whenever a call's receiver is not a declared field (a local
variable, a builder, a static type reference), which real read bodies are full of. It is a defect, not a
design question: fixed with a null guard (no rule change, no matching-outcome change) and a regression
fixture. The only thing worth noting is that it survived AT-1's whole 31-test fixture battery, which
exercised read-safety only on well-formed fixture bodies that call exclusively on repository fields. The
first real worklist crashed a path 31 green tests did not: fixture-proven is not user-proven, now
demonstrated in the repo.

FINDING 2, a PRE-REGISTERED DECISION reopening ON SCHEDULE (deferred, a small design session). Phase E3
ratified enums as STRING-plus-values-annotation rather than a first-class type, and recorded the exact
reopening condition: revisit only if enums become capability INPUTS. They just did. An attested PREDICATE
filter takes parameters; pms's filters take domain enums (`OrderDirections`), and the intake refuses them
at condition 3 (`parameter 'orderDir' has type 'OrderDirections', which does not map to a modeled type`).
The deferred-with-a-written-trigger discipline paid out: the trigger fired months later from a direction
nobody was watching. E3's reasoning stands; what changed is that the agent-facing INPUT side now needs the
closed value set enforceable rather than merely described. Not a surprise gap, a scheduled decision.

FINDING 3, a WORKLIST MIS-CATEGORIZATION that re-ranks the sub-phases. pms's PREDICATE items tie their
output element to the entity only through hand-written one-hop repositories: `TenantRepository` is
`public interface TenantRepository {` with NO Spring Data supertype (the Spring Data repos are the
separate `*JpaRepository` classes), and the service reads call the hand-written interface. The AT-1
intake resolves the element tie only through a DIRECT Spring Data repository, so these reads carry a
coexisting SHAPE hole and are `both`/shape, which is AT-2, not AT-1 predicate. So the AT-0 pms worklist
(section 12) was optimistic: items marked attestable-in-AT-1 actually require the sub-phase after it. The
honest reading: pms's real first measurement cannot happen until AT-2 exists. Reality re-ranked the
roadmap for the second time in this project (the first was the enterprise app forcing entity-identity
ahead of attestation); both times the re-ranking came from contact, not from a census.

Forecast versus actual: the forecast (section 10, Volume 5 section 4.15.11) predicted up to roughly 45
pms predicate reads under AT-1, with a knowledgeable attester declining about a third. Actual: 0
attestable under AT-1 as built, and 0 declined, because the maintainer never reached a decline; the
mechanism refused or crashed at the structural frame first. The divergence is a finding about the
forecast's sample, not a number to reconcile: the sample assumed a T2 element tie the built intake
resolves only for direct Spring Data repos, assumed enum inputs would map, and could not anticipate the
read-safety crash. The measurement row: worklist about 65 filtered/refused reads; attestable-under-AT-1
0; attested 0; declined 0; one bug, one scheduled decision, one mis-categorization.

The erp read-only tie check that follows (third-app-trial.md section 26) settles the sequencing: erp uses
direct Spring Data repos, so its filtered reads tie cleanly and DO attest under AT-1 as built. erp, not
pms, is the app where the first measurement can run under AT-1 as it stands.

## 15. First real measurement on pms: 10 authored entries, 0 attested, four causes (2026-08-01)

The first measurement that got past target resolution. The maintainer selected a set from a source-level
triage of the 65-read worklist (the triage grouped the worklist by where each read's predicate is decided:
7 framework-decidable, 14 builder-backed with no ambiguity flags, 36 builder-backed with a flag, 6
mutations, 2 infrastructure). The selected set was the 14 builder-backed no-flag reads. Sentences were
drafted from source, stamped with `tibyan attest`, and put through intake. NO TOOL CODE CHANGED THIS
SESSION; the reactor is unchanged at commit b46d542.

### What was authored, and what was declined before authoring

Four of the 14 were declined at drafting, on source evidence, and never became entries:

- `UserDetailsSecurityService#loadUserByUsername(String)`: a security-boundary read. BOUNDARY is category
  C, deferred to AT-3 by name with elevated stakes. Worth recording that this one WOULD probably have
  tied: its body calls a direct `UserJpaRepository` field, so unlike its neighbours it has no shape hole.
  It is deferred by design, not blocked by mechanism.
- `InvoiceServiceImpl#getMyInvoices(InvoiceStatus, ...)` (staff) and
  `TimeEntryServiceImpl#getMyTimeEntries(TimeEntryStatus, ...)`: context reads. Both resolve the acting
  user from `requestContext.get(USER_ID, Long.class)`, a predicate that is not in the signature, so no
  parameterMeanings entry can cover it and a caller cannot control it. These are the excluded-set context
  reads the design names.
- `InvoiceServiceImpl#getMyInvoices(Long userId, ...)` (billing): DECLINED on a dual-identity defect found
  while drafting. `resolveStaffId(userId)` is
  `staffService.getStaffByUserId(userId).map(...).orElse(userId)`, so a user with no staff record falls
  back to filtering `staffId` by their `userId`. The read then returns the invoices of whatever staff
  record happens to share that number: another person's invoices. No true one-sentence claim exists, and
  exposing it to an agent is a data-disclosure path. This is the first decline in the project made for the
  reason the design predicted declines would happen, and it was reached by reading the code, not by the
  intake refusing.

Ten entries were authored and stamped. All 10 targets RESOLVED (condition 1 passed on every one), which is
itself a first: the previous attempt never got that far.

### The measured result

`tibyan scan examples/pms-backend`: discovered 5, ATTESTED 0, mis-bound 0, attestation-drift 0,
skipped-by-parse 0. Ten intake refusals in three classes:

| Cause | n | Reads |
|---|---|---|
| STRUCTURAL_FRAME (enum input) | 3 | `CostServiceImpl#getCostsByBuilding`, `InvoiceServiceImpl#getBuildingInvoices` (staff), `TimeEntryServiceImpl#getBuildingTimeEntries` |
| READ_SAFETY (`merge`) | 3 | `ReportingServiceImpl#{getCostsByCategory,getPaymentsCollection,getRevenueVsCost}` |
| CATEGORY_MISMATCH (shape hole) | 4 | `RentalPaymentServiceImpl#getPaymentsSummary`, `InvoiceServiceImpl#getInvoicesByBuilding` (billing), `PPFileServiceImpl#getAllVersions`, `UserManagementServiceImpl#getUserDetails` |

ADDITIVITY VERIFIED: the serialized IR is BYTE-IDENTICAL with and without the record file present (`diff`,
zero differences), so 10 refused entries change nothing about the verified 5. erp re-scanned unchanged at
discovered 25 (by-id 16, list 9), mis-bound 0.

### The four findings

FINDING 1, the enum-input decision reproduced and now QUANTIFIED. Section 14's finding 2 predicted this;
the measurement sizes it. Three of ten entries refuse purely because a domain enum (`CostCategory`,
`InvoiceStatus`, `TimeEntryStatus`) is a capability input and E3 mapped enums to STRING-plus-annotation
rather than a first-class type. Every one of the three is otherwise well-formed. This is 30 percent of an
authored set lost to a decision whose written reopening trigger has already fired.

FINDING 2, A NEW DEFECT: the read-safety write-set check is RECEIVER-BLIND. `ReadSafety.checkBody` tests
`WRITE_SET.contains(call.getNameAsString())` before it looks at the receiver at all, so
`costByCategory.merge(...)` on a local `LinkedHashMap` refuses with "a mutating body is not a read". All
three reads are genuinely read-only: they load once and aggregate into a local map. The refusal message is
false as written. This is the same family as the crash fixed in 33678ae (fixture bodies only ever called
repository fields, so no fixture ever put a write-set NAME on a non-persistence receiver), and it is worse
than a nuisance: `merge` and `remove` are how in-application aggregation is written, so this systematically
refuses the AT-2 aggregate category the roadmap is heading toward. NOT FIXED THIS SESSION. Making the
write-set check receiver-aware changes matching outcomes (bodies that refuse today would attest), so it is
not a rule-13 diagnostic amendment; it needs its own ratified design. Recorded as the highest-priority
input to that design.

FINDING 3, the shape hole CONFIRMED. Four of ten refuse with the CATEGORY_MISMATCH the AT-1 intake was
built to emit: the output entity tie is not structurally provable, because pms ties its element through
hand-written one-hop repositories (`CostRepository`, `BillingRepository`, `RentalPaymentRepository`,
`PPFileRepository`, `FileVersionRepository`, `UserRoleRepository`, `InvoiceRepository`,
`TimeEntryRepository` are all `public interface X {` with no Spring Data supertype). Section 14's finding 3
said pms's real measurement waits for AT-2; this is that statement measured rather than argued. The
refusal names its own remedy ("use category shape or both").

FINDING 4, the traps a knowledgeable attester actually hits. Drafting the sentences surfaced four things no
structural rule would have caught, and each one changed the sentence:

- `RentalPaymentServiceImpl#getPaymentsSummary` sums in the application over a filter that never sets
  pagination, and `SearchFilter` defaults to `pageSize 25` with no ORDER BY. The summary covers at most 25
  arbitrary payments. The authored sentence says so in capitals; the obvious sentence would have been false.
  Eight other worklist reads inherit the same silent cap.
- The three `ReportingServiceImpl` reads default an omitted date range to first-of-current-year through
  today (`resolveDateRange`), so an omitted parameter means year-to-date, not all-time.
- `UserManagementServiceImpl#getUserDetails` returns a hardcoded 10 GiB storage quota and zeroed file and
  folder counts; the sentence has to disclaim them as placeholders.
- `getRevenueVsCost` applies its lower date bound in the application and its upper bound in the database,
  and counts only `"paid"` payments as revenue.

The forecast said a knowledgeable attester would decline about a third. Measured on this set: 4 of 14
declined before authoring (29 percent), which lands almost exactly on the forecast, though for one reason
the forecast did not name (the dual-identity fallback) and three it did (boundary, context, context).

### Measurement row

Worklist about 65; selected 14; declined before authoring 4; authored 10; targets resolved 10; ATTESTED 0;
refused 10 (enum-input 3, read-safety false positive 3, shape hole 4); mis-bound 0; attestation-drift 0;
IR byte-identical. One new defect, one quantified scheduled decision, one confirmation, four attester traps.

### What this changes

The blocking order is now measured rather than assumed. Three things stand between this set and a nonzero
attested count, and all three are decisions, not discoveries:

1. the receiver-aware read-safety fix (finding 2), which blocks 3 here and every aggregate read later;
2. the E3 enum-input decision (finding 1), which blocks 3 here and the erp enum-param subset;
3. AT-2 SHAPE (finding 3), which blocks 4 here and is already the named next sub-phase.

Nothing of AT-2/3/4 is built. The record file lives at `examples/pms-backend/tibyan/attestations.json`
(that path is gitignored, so the ten entries are a local working artifact, not a committed claim). The
authored sentences are DRAFTS: their provenance line names the maintainer as asserter, and that line only
becomes true once the maintainer has read the ten sentences and confirmed them. AI MAY PROPOSE, ONLY A
HUMAN MAY CONFIRM.

## 16. Read-safety receiver awareness: forecast (pms-backend, read-only, 2026-08-04)

DESIGN HALF, pending ratification. The rule is Volume 5 section 4.15.2.1. This section records the pms
forecast and the census it rests on. No tool code changed; the benchmark was read only.

THE CENSUS. Every call across `examples/pms-backend/modules` whose simple name is in the AT-1 write set
(`save`, `saveAll`, `insert`, `update`, `delete`, `deleteById`, `deleteAll`, `persist`, `merge`, `flush`,
`remove`, `refresh`, `detach`), classified by receiver:

| Receiver class | Count | Verdict under the refinement |
|---|---|---|
| Repository-typed field | 186 | REFUSE (R1), unchanged |
| Other field (service, client) | 10 | REFUSE (R3), unchanged |
| Provably local, fresh allowlisted container | 4 | permitted (the carve-out) |
| Static type reference | 1 | REFUSE (R9), unchanged |

The 10 other-field calls are all controller delegations to genuine service writes
(`*ControllerImpl#_updateX`, `#_deleteX`); none is inside a read. The static one is
`FileStorageServiceImpl#deleteFile -> Files.delete`, a real filesystem write, and it stays refused under
L1 because the receiver is a type reference, not a local.

THE FOUR PROVABLE-LOCAL CALL SITES, verified against source condition by condition:

| Call site | Read | Local | Initializer | L1..L6 |
|---|---|---|---|---|
| `costByCategory.merge` | `ReportingServiceImpl#getCostsByCategory` | declared in method | `new LinkedHashMap<>()` | all hold |
| `collectionByStatus.merge` | `ReportingServiceImpl#getPaymentsCollection` | declared in method | `new LinkedHashMap<>()` | all hold |
| `costByMonth.merge` | `ReportingServiceImpl#getRevenueVsCost` | declared in method | `new TreeMap<>()` | all hold |
| `revenueByMonth.merge` | `ReportingServiceImpl#getRevenueVsCost` | declared in method | `new TreeMap<>()` | all hold |

Each receiver is a bare simple name, each local is assigned exactly once by its own declarator, no
allowlisted container name is shadowed by an in-tree declaration anywhere in pms or erp, and none of the
four locals is ever passed as an argument to a call on a repository or persistence-infrastructure field
(checked line by line in `ReportingServiceImpl`: the maps are merged into and then streamed into
`ChartSeriesPoint` values, and nothing else).

THE FORECAST. Intake outcome on the ten authored entries of section 15, before and after:

| Refusal cause | Today | After the fix |
|---|---|---|
| STRUCTURAL_FRAME (enum input) | 3 | 3 |
| READ_SAFETY | 3 | 0 |
| CATEGORY_MISMATCH (shape hole) | 4 | 7 |
| Total refused | 10 | 10 |
| ATTESTED | 0 | 0 |

Discovered stays 5 and BYTE-IDENTICAL; mis-bound 0; attestation-drift 0; mis-safe 0. Read-safety has one
call site (attestation intake), so nothing outside the ten entries can move. Any movement in the
discovered number is a REGRESSION FINDING, not a number to reconcile.

WHY THE COVERAGE GAIN IS ZERO AND THE FIX IS STILL LOAD-BEARING. All three reads move from a false
refusal to a true one: they are `ChartSeries`-returning aggregates tied through hand-written one-hop
repositories (`CostRepository`, `RentalPaymentRepository`), so the output entity tie is unprovable and
they belong to AT-2. The fix buys correctness of the refusal reason now, and it removes a blanket block
on the in-application aggregation shape that AT-2 is specifically for. Without it, a large part of
category B would refuse before reaching the gate designed to judge it.

WHAT STAYS REFUSED THAT A HUMAN MIGHT THINK IS SAFE, recorded so the narrowness is not mistaken for a
bug: an aggregation into a map from a factory call (`Collections.emptyMap()`, `List.of()`), into a
field-held accumulator, through a chained receiver (`byKey.get(k).remove(x)`), or into a parameter-passed
collection. All refuse. Per the MIS-SAFE bias, a false refusal is a coverage miss and a false pass is a
safety breach, and the design prefers the miss.

## 16.1 Read-safety receiver awareness: BUILT and measured (pms-backend, 2026-08-04)

The ratified rule (Volume 5 section 4.15.2.1) implemented exactly, provider only. `mvn clean verify` green.
The section 16 forecast held to the number.

MEASURED, before and after, on the same ten authored entries:

| Refusal cause | Forecast | Before | After |
|---|---|---|---|
| STRUCTURAL_FRAME (enum input) | 3 | 3 | 3 |
| READ_SAFETY | 0 | 3 | 0 |
| CATEGORY_MISMATCH (shape hole) | 7 | 4 | 7 |
| Total refused | 10 | 10 | 10 |
| ATTESTED | 0 | 0 | 0 |

THE THREE READS THAT MOVED, each to its true next cause, hand-verified against the ratified impact table:

| Read | Before | After |
|---|---|---|
| `ReportingServiceImpl#getCostsByCategory` | READ_SAFETY, "a write-set call 'merge' is visible" | CATEGORY_MISMATCH, "the read's output entity tie is not structurally provable" |
| `ReportingServiceImpl#getPaymentsCollection` | READ_SAFETY, same | CATEGORY_MISMATCH, same |
| `ReportingServiceImpl#getRevenueVsCost` | READ_SAFETY, same | CATEGORY_MISMATCH, same |

THIS CAUSE-COUNT CHANGE IS THE INTENDED EFFECT, NOT A REGRESSION, and it is the whole product of the
phase. Before the build the tool said three read-only aggregates were mutating bodies, which was false.
After it, it says their element tie is unprovable, which is true and is what AT-2 exists to address. No
read gained a capability and none was newly attested.

BYTE-IDENTICAL, verified by `diff`, not asserted: the serialized IR, the generated MCP output, and the
generated OpenAPI document are all byte-identical to the pre-build commit. Discovered stays 5 (by-id 5,
list 0), mis-bound 0, attestation-drift 0, skipped-by-parse 0.

THE MIS-SAFE PROBE ON REAL CODE, the check that matters most. An attestation was authored against
`CostServiceImpl#deleteCost(String)`, a genuine write whose body ends in
`costRepository.delete(cost)`, deliberately claiming a delete is a read. Intake refused it:

```
[READ_SAFETY]: a write-set call 'delete' on the repository field 'costRepository' (CostRepository)
is visible in the body of 'deleteCost' (read-safety, section 4.15.2.1, R1)
```

A genuine persistence write is not classified safe on real application code, and the located reason now
names the receiver and the refusal case. The probe file was removed and the real record restored; the
re-scan totals are unchanged. `mis-safe 0`.

The census in section 16 is confirmed by the build: exactly the four forecast call sites were accepted as
provable locals, and the other 197 write-set call sites across pms modules (186 on repository fields, 10 on
other fields, 1 on a static type reference) all still refuse. The refusal total staying at 10 with the other
seven entries keeping their exact prior causes is the evidence that the carve-out did not widen.

## 17. Enum-typed inputs: forecast (pms-backend, read-only, 2026-08-04)

DESIGN HALF, pending ratification. The rule is Volume 5 section 4.16. No tool code changed; the benchmark
was read only.

THE THREE BLOCKED ENTRIES, and their enum declarations verified against source:

| Entry | Enum parameter | Declaration | Shape | Constants |
|---|---|---|---|---|
| `CostServiceImpl#getCostsByBuilding` | `CostCategory` | `com/pms/billing/model/generated/CostCategory.java` | (b) value-mapped | 8 |
| `InvoiceServiceImpl#getBuildingInvoices` | `InvoiceStatus` | `com/pms/staff/model/generated/InvoiceStatus.java` | (b) value-mapped | 3 |
| `TimeEntryServiceImpl#getBuildingTimeEntries` | `TimeEntryStatus` | `com/pms/staff/model/generated/TimeEntryStatus.java` | (b) value-mapped | 3 |

All three resolve in-tree and all three are the OpenAPI-generated idiom: a `@JsonValue` accessor over a
constructor-assigned field, so the accepted wire values are the LITERALS, not the constant names.
`CostCategory` advertises `maintenance, utilities, insurance, taxes, supplies, renovation, security, other`,
NOT `MAINTENANCE, ...`. Advertising the constant names here would advertise eight values the application
rejects, which is the MIS-CONSTRAINED failure the wire-value rule exists to prevent.

THE CONSTRUCTOR-LITERAL TRAP IS PRESENT IN THIS BENCHMARK and is what makes the rule's precision load
bearing. `com/pms/lease/model/enums/LeaseStatus.java` declares `DRAFT("Draft"), ACTIVE("Active"), ...` with a
Lombok `@Getter` and NO `@JsonValue`. Those literals are decorative labels and the wire form is the constant
name. A rule that read constructor literals wherever it found them would advertise `Draft, Active, ...`,
every one of which the application would reject. Shape (b) is conditioned on `@JsonValue`, never on the
presence of a literal, precisely because of this pair.

THE FORECAST:

| Refusal cause | Today | After EI-R and EI-1 |
|---|---|---|
| STRUCTURAL_FRAME (enum input) | 3 | 0 |
| CATEGORY_MISMATCH (shape hole) | 7 | 10 |
| Total refused | 10 | 10 |
| ATTESTED | 0 | 0 |

All three unblocked entries refuse at their NEXT gate, the output entity tie, because pms ties through
hand-written one-hop repositories. The near-term coverage gain on pms is ZERO, for the same structural
reason as the read-safety phase: pms is blocked on AT-2, not on this. Discovered stays 5 and BYTE-IDENTICAL
BY CONSTRUCTION, because every discovered pms capability has exactly one LONG input and no discovered read
can carry an enum parameter. mis-bound 0, mis-constrained 0.

## 17.1 EI-R built: the runtime can now bind an enum or date parameter (pms-backend, 2026-08-05)

The ratified EI-R (Volume 7 section 9) built, runtime only. pms is UNCHANGED and that is the correct
outcome, stated so it is not mistaken for a null result: EI-R touches neither the provider nor the backends,
so no refusal cause can move and none did.

Measured: discovered 5, attested 0, refusals 10 with causes UNCHANGED at STRUCTURAL_FRAME 3 and
CATEGORY_MISMATCH 7. The serialized IR, the generated MCP output and the generated OpenAPI document are
BYTE-IDENTICAL to the pre-build commit, verified by `diff`. mis-bound 0, attestation-drift 0.

THE FORECAST IN SECTION 17 IS NOT YET DUE. It predicted the three STRUCTURAL_FRAME entries moving to
CATEGORY_MISMATCH; that movement belongs to EI-1, the provider half, which is the next build. What EI-R
delivers for pms is that those three reads, once EI-1 lets them through, will be INVOKABLE: before this
step the adapter would have looked for `getCostsByBuilding(String, String, Instant, Instant, Integer,
Integer)` and thrown, because it reconstructed parameter types from the IR instead of reading the declared
ones.

The wire-value rule is verified against this benchmark's real declaration. A character-exact replica of
`com/pms/billing/model/generated/CostCategory.java` proves the runtime accepts all eight literals in
declaration order (`maintenance`, `utilities`, `insurance`, `taxes`, `supplies`, `renovation`, `security`,
`other`) and REFUSES the constant name `RENOVATION`, which the application would also reject. Accepting the
constant name would mean accepting a value outside the set the provider will advertise, which is the
MIS-CONSTRAINED failure.

---

## Read-Identity re-measurement (2026-08-31), pms-backend

Phase Read-Identity (Volume 5, section 4.1.1) tightened the section 4.1 entity-returning path to
prove its by-primary-key load. pms is unaffected by construction: all 5 of its discovered reads come
through the section 4.2 view path, and it has ZERO section 4.1 capabilities, so there was nothing on
this benchmark for the new proof to judge.

Baseline and result, both measured on the current tree: discovered 5 (by-id 5, list 0), failed 38
(COMPOSITE_KEY 3, NO_BACKING_METHOD 35), skipped-by-parse 0, partial 0, mis-bound 0, before and
after. The serialized IR, the generated MCP output and the generated OpenAPI document are all
BYTE-IDENTICAL to a worktree built at the pre-build commit, verified by `diff -r`.

pms is recorded here as the control: a benchmark that exercises none of a phase's new machinery and
must therefore not move by a single byte. It did not.

## AT-2 (SHAPE) re-measurement (2026-09-01), pms-backend

Attestation AT-2 built the SHAPE category. pms is the app whose ledger section 10 estimated "up to 8 more
under AT-2", the aggregate by-id reads (Building, Property, ChatRoom, Ticket, Group, Event,
`getUserDetails`, `findPropertyByBuildingId`).

Measured: discovered 5, failed 38 (COMPOSITE_KEY 3, NO_BACKING_METHOD 35), mis-bound 0, attested 0, with
the serialized IR, generated MCP output and generated OpenAPI document BYTE-IDENTICAL to a worktree built
at the pre-build commit. pms carries no `tibyan/attestations.json`, so nothing could move, and the phase is
provably additive as fixture ATT-11 requires.

The "up to 8" is unchanged as an estimate and is now bounded by one thing the estimate did not account for:
AT-2 builds the shape category only, not the compound `both`. An aggregate whose inputs are the identifier
and paging is attestable now; an aggregate that also takes a domain filter has a predicate hole as well and
needs `both`, which is not built. Whether all 8 fall on the clean side is a question for the person who
attests them, which is the open verification this phase does not close.

## EI-1 re-measurement (2026-09-01), pms-backend

Step EI-1 built the input value constraint. pms contributed the phase's most important negative: its
hand-written `LeaseStatus` declares `DRAFT("Draft"), ACTIVE("Active")` with a Lombok getter and NO
`@JsonValue`, so Jackson serializes the CONSTANT NAME and those literals are decorative labels. Fixture
EI-3 reproduces that shape and pins that the tool carries DRAFT, never Draft. A rule that read constructor
literals whenever it saw them would have advertised values this application rejects, which is the
mis-constrained breach.

Measured: discovered 5, failed 38, mis-bound 0, attested 0, with serialized IR, generated MCP output and
generated OpenAPI document all BYTE-IDENTICAL to a worktree built at the pre-build commit.

The one measured change on any benchmark is here, and it is a ledger line rather than a capability: pms's
survey gains two `enum-parsed-in-body` entries, `LeaseServiceImpl#updateStatus(Long, String)` and
`LeaseSigningServiceImpl#applyWebhookUpdate(String, String)`. Both are WRITES, so neither was ever a
candidate; they are named because their closed set sits in the body rather than the signature, and the
ratified rule is that such a read carries no constraint, is not refused on that account, and is recorded so
the case is visible rather than silently unconstrained.

Section 4.16.9's pms forecast stands unchanged: the three authored entries that this step unblocks at the
parameter gate then refuse at their NEXT gate, the output entity tie, because pms ties through hand-written
one-hop repositories. pms is blocked on AT-2, not on this.

## AT-3 (BOUNDARY) re-measurement (2026-09-01)

Attestation AT-3 built the BOUNDARY category, member C2: the remote delegation, attested for what it
returns AND for read-only-ness itself, which is the one property the tool cannot check for that shape.

Measured: BYTE-IDENTICAL across the serialized IR, the generated MCP output, and the generated OpenAPI
document, verified by `diff -r` against a worktree built at the pre-build commit. This app carries no
`tibyan/attestations.json`, so nothing could move, and the phase is provably additive.

The counted evidence for this category lives on the private enterprise benchmark (15 by-id delegation
reads), not here, so byte-identical is the expected and correct result rather than a disappointment. What
matters for this ledger is the negative: a phase that adds a whole new epistemic class, three IR fields,
two core rules, and a runtime exposure control moved not one byte of any existing output.
