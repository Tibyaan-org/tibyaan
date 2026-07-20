# Experiment: third real app trial (erp-backend)

> The benchmark target is a private codebase, not committed to this repository (it is gitignored). This ledger is the published evidence: a stranger can read the numbers and the per-entity outcomes here without access to the source.

Status: complete. Date: 2026-07-02. This is a generalization experiment, not a build. No tool code
was changed. The target app was read only, never compiled, never built, never added to the reactor,
and the tibyan maven plugin was never run on it. The only outcome that would have forced a hard stop,
a mis-bound capability, did not occur: see the MIS-BOUND section.

The question this experiment answers: do the discovery patterns built from pms-backend evidence (the
4.2 view reads with one-hop delegation, plus associations, enums, and FLOAT from E3) transfer to a
codebase they were not derived from, and what does erp-backend exhibit that the rule library has
never seen? The transfer verdict is section 7.

Tool used: the reactor CLI jar `tibyan-cli-0.1.0-SNAPSHOT-jar-with-dependencies.jar`, built this
session from main, unchanged. Command: `tibyan scan examples/erp-backend` for the survey (exit 0),
and `tibyan scan examples/erp-backend --out ./generated/erp.ir.json` to attempt writing the IR
(exit 2; see section 8, the IR-consistency finding).

Reactor note: `examples/erp-backend` is not in the parent pom `<modules>` list, so no detach was
needed and no pom change was made. It was added to `.gitignore` (as `/examples/erp-backend`,
replacing a prior over-broad `/examples` line a placement step had left, so the ignore is precise
and consistent with `/examples/pms-backend`). Nothing about the tibyaan build changed.

---

## 1. The app at a high level, and how it differs from pms-backend

A single-module Spring Boot application (one `pom.xml`, package root `com.luisi`), an ERP covering
contacts, finances, products, CRM, delivery, and HR. About 300 Java source files. It is structurally
unlike pms-backend in ways that turn out to be the whole story:

| Dimension | pms-backend | erp-backend |
|---|---|---|
| Modularity | 14 Maven modules | one module |
| Identifier type | `Long` / `Integer` | `UUID` (23 of 25), `String` (2: User.cognitoId, DeletedUser.userId) |
| Composite keys | 3 (`@IdClass`) | none |
| By-id read return type | a view (`TenantVTO`, `VendorVTO`) | the entity itself (`Contact findById(UUID)`, `Product findById(UUID)`) |
| Repository access | hand-written interface, one hop to Spring Data | services call Spring Data repositories directly |
| Repositories | hand-written `@Repository` impls delegating | 25 Spring Data interfaces, only 3 `@Repository` classes |
| Mapping idiom | MapStruct mapper classes, VTO returns | mostly no mapping (return entity); 3 MapStruct mappers exist |
| Collection associations | none | common: `@OneToMany`/`@ManyToMany` `Set`/`List`/`Map` on 10 of 25 entities |
| Embeddable value types | none | `@Embeddable Address` embedded in 3 entities |
| Money type | `Double` (a defect) | `BigDecimal` (39 fields; correct) |
| Unparseable files | 27 placeholder files | none |

The single most consequential difference: erp-backend's by-id reads return the entity, so they are
section 4.1 (entity-returning) candidates, the opposite of pms-backend's section 4.2 (view-returning)
reads. The two apps exercise different rules almost disjointly.

---

## 2. Inventory (the denominator, read-only)

- Entity classes: 25 (annotated `@Entity`).
- Identifier styles: 25 single `@Id`; no `@EmbeddedId`, no `@IdClass`. 23 map to UUID; 2 to String;
  1 (`EmailTemplate`) has an enum-typed `@Id` (`@Id @Enumerated EmailTemplateType type`), a latent
  no-identifier case (section 3).
- `@Service` classes: 26, with the interface plus `implementation` split, like pms.
- Read-method shapes: the by-id read is `findById(id)` returning the entity, pervasively. Lists and
  filters return `Page<Entity>` or `List<Entity>` and `Page<NameIdDTO>` projections.
- Mapping idiom: mostly none for by-id reads (the entity is returned directly). Lombok `@Data`,
  `@Builder`, `@AllArgsConstructor` are used on nearly every entity (140 files touch Lombok). Three
  MapStruct `@Mapper` interfaces exist (products), used by list/projection paths, not the by-id reads.
- Structurally unlike pms: single module; UUID and String ids; entity-returning reads; direct Spring
  Data repositories; dense collection associations; an `@Embeddable` value type; `byte[]` fields;
  correct `BigDecimal` money.

Candidate read-by-id capabilities in the tool's sense (one per entity): 25.

---

## 3. Outcome table (entity to outcome to rule or cause to pattern to message)

The whole-app survey completed at exit 0. Every one of the 25 entities received a per-entity outcome;
nothing was skipped by parse. Discovered capabilities are all section 4.1.

| Entity | Outcome | Rule / cause | Pattern | Message (representative) |
|---|---|---|---|---|
| ContactType | DISCOVERED | 4.1 | entity-returning | `getContactType -> ContactTypeServiceImpl#findById; returns the entity ContactType directly` |
| Account | DISCOVERED | 4.1 | entity-returning | `getAccount -> AccountServiceImpl#findById` |
| Ticket | DISCOVERED | 4.1 | entity-returning | `getTicket -> TicketServiceImpl#findById` |
| Truck | DISCOVERED | 4.1 | entity-returning | `getTruck -> TruckServiceImpl#findById` |
| BarcodeType | DISCOVERED | 4.1 | entity-returning | `getBarcodeType -> BarcodeTypeServiceImpl#findById` |
| Brand | DISCOVERED | 4.1 | entity-returning | `getBrand -> BrandServiceImpl#findById` |
| Unit | DISCOVERED | 4.1 | entity-returning | `getUnit -> UnitServiceImpl#findById` |
| Contact | FAILED | UNMAPPED_FIELD_TYPE | NEW: embeddable | `unmapped type 'Address' on field 'Contact.billingAddress'` |
| Quote | FAILED | UNMAPPED_FIELD_TYPE | NEW: embeddable | `unmapped type 'Address' on field 'Quote.deliveryAddress'` |
| Delivery | FAILED | UNMAPPED_FIELD_TYPE | NEW: embeddable | `unmapped type 'Address' on field 'Delivery.address'` |
| ContactCategory | FAILED | UNMAPPED_FIELD_TYPE | NEW: collection | `collection field 'ContactCategory.children' of type 'Set<ContactCategory>' is a to-many shape, deferred` |
| ContactGroup | FAILED | UNMAPPED_FIELD_TYPE | NEW: collection | `Set<Contact>` deferred |
| Transaction | FAILED | UNMAPPED_FIELD_TYPE | NEW: collection | `List<Payment>` deferred |
| SMSMessage | FAILED | UNMAPPED_FIELD_TYPE | NEW: collection | `List<String>` deferred |
| Product | FAILED | UNMAPPED_FIELD_TYPE | NEW: collection | `Set<Contact>` deferred |
| ProductCategory | FAILED | UNMAPPED_FIELD_TYPE | NEW: collection | `Set<ProductCategory>` deferred |
| ProductGroup | FAILED | UNMAPPED_FIELD_TYPE | NEW: collection | `Set<Product>` deferred |
| VariationGroup | FAILED | UNMAPPED_FIELD_TYPE | NEW: collection (Map) | `Map<String,UUID>` deferred |
| EmailTemplate | FAILED | UNMAPPED_FIELD_TYPE | NEW: collection (Map); enum-@Id latent | `Map<String,String>` deferred (its enum `@Id` never reached) |
| Payment | FAILED | UNMAPPED_FIELD_TYPE | NEW: byte array | `byte[]` reported as a to-many shape (message nuance, section 8) |
| OutboundEmail | FAILED | NO_BACKING_METHOD | no by-id read (cause d) | `no service method backs read capability 'getOutboundEmail'` |
| OutboundNotification | FAILED | NO_BACKING_METHOD | no by-id read | `no service method backs read capability 'getOutboundNotification'` |
| DeletedUser | FAILED | NO_BACKING_METHOD | no by-id read | `no service method backs read capability 'getDeletedUser'` |
| InventoryTransaction | FAILED | NO_BACKING_METHOD | no by-id read | `no service method backs read capability 'getInventoryTransaction'` |
| User | FAILED | AMBIGUOUS_BINDING | NEW: String-id shape collision | `backed by more than one candidate: [UserService#findById (entity-returning), UserService#findByUsername (entity-returning)]` |

None of the NO_BACKING_METHOD failures carried a P1b or P2 near-miss: erp-backend has no
view-returning by-id reads at all, so the pms-derived deferred patterns had zero occurrences here.

---

## 4. Totals

- Entities in scope: 25. Candidate read-by-id capabilities: 25.
- DISCOVERED: 7, all by rule 4.1 (entity-returning). Rule 4.2 (view-returning): 0.
- MIS-BOUND / WRONG: 0.
- FAILED: 18.
  - UNMAPPED_FIELD_TYPE: 13. NEW patterns pms-backend never showed: collection associations
    (`Set`/`List`/`Map`, to-many) 10; embeddable value type `Address` 3. One of the collection
    failures (`Payment.checkFrontImage`, `byte[]`) is a binary blob mis-labeled as a to-many shape
    (section 8).
  - NO_BACKING_METHOD: 4 (OutboundEmail, OutboundNotification, DeletedUser, InventoryTransaction).
    No by-id read method exists; these are cause d (write-heavy or list-only entities), not P1b/P2
    near-misses. Likely the correct answer, as with pms-backend's audit and DailyCounter.
  - AMBIGUOUS_BINDING: 1 (User). NEW pattern: a String identifier makes `findByUsername(String)`
    shape-indistinguishable from `findById(String)`, both entity-returning, so two candidates tie.
    Correctly refused, never guessed.
- SKIPPED by parse: 0. Genuine: erp-backend has no unrendered placeholder files; E1's Java 17
  language level and collect-and-continue held on a codebase they were not tuned for.
- COMPOSITE_KEY: 0. NO_IDENTIFIER: 0 surfaced (EmailTemplate's enum `@Id` is latent behind its
  collection failure, so its identifier was never evaluated).

---

## 5. MIS-BOUND section (the alarm case), with per-discovery hand verification

None. Zero mis-bound or wrong capabilities. On a codebase the rules were not derived from,
discoveries are guilty until proven correct, so each of the 7 was verified by opening the actual
service source:

| Capability | Binding | Verified |
|---|---|---|
| getContactType | ContactTypeServiceImpl#findById | `public ContactType findById(UUID id)` returns the ContactType entity by its UUID id. Correct. |
| getAccount | AccountServiceImpl#findById | `public Account findById(UUID id)` returns Account by its UUID id. Correct. |
| getTicket | TicketServiceImpl#findById | `public Ticket findById(UUID id)` returns Ticket; body loads via a custom query `findByIdWithAssociations(id)`, but rule 4.1 binds on return type and shape, and the method genuinely returns the Ticket for that id. Only one Ticket-returning single-arg finder exists, so not ambiguous. Correct. |
| getTruck | TruckServiceImpl#findById | `public Truck findById(UUID id)` returns Truck by UUID id. Correct. |
| getBarcodeType | BarcodeTypeServiceImpl#findById | `public BarcodeType findById(UUID id)` returns BarcodeType by UUID id. Correct. |
| getBrand | BrandServiceImpl#findById | `public Brand findById(UUID id)` returns Brand by UUID id. Correct. |
| getUnit | UnitServiceImpl#findById | `public Unit findById(UUID id)` returns Unit by UUID id. Correct. |

Every discovery binds to the right method on the right entity, and none has a view (all are
entity-returning, so no view claim to check). The one thing rule 4.1 does not verify, that the load
is by primary key (unlike 4.2), was sound here because the finders genuinely read by id; the User
AMBIGUOUS case is exactly where that shape-only trust is thinnest, and there the tool refused rather
than risk a wrong bind. The alarm stayed silent.

---

## 6. Generation

Not run to completion. `tibyan scan --out` refused to write the IR at exit 2, because the discovered
model does not pass semantic validation: extracted entities carry associations to entities that
failed extraction, leaving dangling `ENTITY_REF` fields and `Relation`s (for example
`Ticket.contact -> Contact`, and relations to Product, Quote, Transaction, all of which failed on
collections or `Address`). This is the IR-consistency finding in section 8. The seven discovered
capabilities are themselves valid (their outputs are the extracted entities), but the model as a
whole is not, so generation is currently blocked on this app. Not fixed; a fix is proposed in
section 8 and deferred.

---

## 7. The transfer verdict

Stated plainly, including the uncomfortable part.

- Rule 4.1 (entity-returning, the oldest rule, from the original MVP before pms-backend existed)
  transferred and did all the work: 7 of 7 discoveries are 4.1. It generalized to a codebase it was
  not derived from without a change.
- Rule 4.2 (the pms-derived view rule, with one-hop delegation and mapper resolution) fired zero
  times here. It did not misfire; erp-backend simply has no view-returning by-id reads for it to
  match. The elaborate machinery E2 built from pms-backend evidence (the one-hop trace, the mapping
  step resolution, the deferred P1b and P2 patterns) engaged not at all, because erp's read idiom is
  different. It was, in hindsight, fitted to pms-backend's boundary style.
- E1 (parse resilience) transferred cleanly: 0 skipped, no aborts, all 25 entities classified. The
  Java 17 level and collect-and-continue are genuinely codebase-independent.
- E3 transferred partially and revealed its pms-shaped assumptions. Associations map to `ENTITY_REF`
  and enums to STRING as designed, but two shapes pms-backend never contained now dominate the
  failures: collection associations (10 entities) and embeddable value types (3). E3's decision to
  defer collections, cheap on pms-backend (zero there), is the single largest blocker here. FLOAT
  (E3c), justified by pms-backend's `Double`-money defect, found no evidence: erp uses `BigDecimal`
  correctly, so FLOAT is inert here, neither wrong nor useful.

The implication for whether the rule library compounds across apps or must grow per-app: it does both,
unevenly, and the honest answer leans toward the uncomfortable one. The most general rule (4.1)
compounds; it is the same rule that fired on the example app and now on erp-backend. But the
app-specific rule (4.2) did not compound onto erp-backend at all, and the biggest coverage gains here
come from shapes the library has never modeled (collections, embeddables). Discovery does not simply
accumulate: coverage on a new app is bounded by how much its idioms overlap the ones already built,
and pms-backend and erp-backend overlap surprisingly little (their discoveries came from disjoint
rules). The core generalizes, the fail-loud and mis-bound-zero guarantees held perfectly on a third
app, and each rule is additive and never mis-fired on an app it was not built for. But the number
that matters, discovered over entities, is idiom-bound: 28 percent here (7 of 25), and the rest waits
on patterns not yet built. The rule library is a growing set of recognizers, not a solved problem;
every real app is likely to bring at least one shape it has not seen.

---

## 8. What would unlock the most discoveries here (ranked), and one consistency finding

Ranked by entities blocked on this app. Do not build any of these from this app alone; this is the
evidence, to be weighed with other apps.

1. Collection associations, 10 entities (E4 territory, and more). Today a single to-many field
   (`Set`, `List`, `Map`, `@OneToMany`, `@ManyToMany`) fails the whole entity's extraction, so even
   an entity with a perfectly good `findById` (for example Product, Transaction) never extracts and
   never discovers. Two distinct changes hide here: modeling collection outputs (E4, list reads), and
   a smaller change to let a to-many field be skipped or noted during extraction rather than
   aborting the entity, so the entity still extracts and its by-id read can still be found. The second
   is the higher-leverage unlock here: it would let several of these 10 entities discover via 4.1.
2. Embeddable value types, 3 entities (NEW pattern). `@Embeddable Address` embedded in Contact,
   Quote, Delivery resolves to an in-tree class that is neither `@Entity` nor enum, so the field is
   unmapped and the entity fails. A new decision is needed: map an `@Embeddable` as a nested value
   (its fields inlined or a value-object reference), or skip it so the entity extracts. Not P1b/P2.
3. IR consistency for partial models (a finding, blocks generation for the whole app). E3 emits an
   `ENTITY_REF` field and a `Relation` for every association, including to entities that failed
   extraction and are absent from the model. The result fails semantic validation (dangling
   references), so `tibyan scan --out` exits 2 and generation is blocked, even though the 7
   discoveries are valid. The fix is a provider decision: emit an association field or relation only
   when the target entity is in the model, or drop dangling references with a recorded note. Precisely
   described, deliberately not made this session.
4. String-id shape collision, 1 entity (User), NEW and arguably permanent. `findById(String)` and
   `findByUsername(String)` are shape-indistinguishable when the id is a String, so the pool is
   ambiguous and refused. Disambiguating would require preferring the name `findById`, which violates
   the structural-not-nominal principle (Volume 5, section 4.2.7). The refusal is probably the correct
   permanent answer; a nominal tiebreak is a decision to make deliberately, if ever.
5. No-backing entities, 4 (OutboundEmail, OutboundNotification, DeletedUser, InventoryTransaction).
   No by-id read exists; likely the correct answer, no unlock, as with pms-backend's write-heavy
   entities.

Two message-accuracy nuances noticed, not bugs: `byte[]` fields are reported as a "to-many shape"
(they are binary blobs, not collections; a BYTES-style refusal message would be more honest), and an
enum-typed `@Id` (EmailTemplate) is latent behind a collection failure, so its would-be NO_IDENTIFIER
never surfaces.

---

## 9. Comparison: pms-backend versus erp-backend

| Measure | pms-backend | erp-backend |
|---|---|---|
| Entities | 43 | 25 |
| Discovered | 2 (both rule 4.2, view/one-hop) | 7 (all rule 4.1, entity-returning) |
| Mis-bound | 0 | 0 |
| Skipped by parse | 27 (placeholder files) | 0 |
| Failed | 41 | 18 |
| Dominant failure | NO_BACKING_METHOD 38 (services return VTOs; parse skips) | UNMAPPED_FIELD_TYPE 13 (collections 10, embeddable 3) |
| Composite key | 3 | 0 |
| Ambiguous | 0 | 1 (String-id collision) |
| Id type | Long / Integer | UUID / String |
| Read idiom | view-returning (4.2) | entity-returning (4.1) |
| Rule that fired | 4.2 only | 4.1 only |

The headline: the two benchmarks discover through disjoint rules. Neither app's discoveries would
have been found by the rule the other app exercised. The union covers both; neither alone
generalizes.

---

## 10. Partial-model integrity forecast (stated before the build, 2026-07-02)

The partial-model integrity phase (Volume 5, sections 4.7 model closure, 4.8 field tolerance, 4.9
the survey contract; designed, pending ratification) is forecast here number by number, so the build
is measured against a stated prediction. These are projections and do not appear as run rows in the
benchmark log until an actual run produces them.

### 10.1 erp-backend forecast (after the build)

Verified from source this session. All 25 entities extract once field tolerance stops a single
unmodelable field from vetoing an entity, and model closure keeps the model valid.

- Discovered: 16, up from 7, all via rule 4.1. The 7 current discoveries are unchanged (ContactType,
  Account, Ticket, Truck, BarcodeType, Brand, Unit). Nine new, each with a hand-verified single
  `public <Entity> findById(UUID id)`: Contact, Quote, ContactCategory, ContactGroup, Transaction,
  Product, ProductCategory, ProductGroup, Payment. Rule 4.2 still fires zero times.
- Failed: 9. NO_BACKING_METHOD 7: the current 4 (OutboundEmail, OutboundNotification, DeletedUser,
  InventoryTransaction), plus SMSMessage and VariationGroup (extract now, but have no `findById`),
  plus Delivery. Delivery has two independent reasons to refuse rule 4.2, both verified: its load is
  a custom `@Query` (`deliveryRepository.findByIdWithRelationships`, not a framework by-primary-key
  read, the P1b pattern on a direct Spring Data repository), and its mapping step `mapToDTO(delivery)`
  is an implicit-this call on the service itself, not a resolvable mapper receiver. NO_IDENTIFIER 1:
  EmailTemplate, whose enum-typed `@Id` surfaces now that its blocking `Map` field is tolerated.
  AMBIGUOUS_BINDING 1: User, unchanged (`findById(String)` and `findByUsername(String)` still tie).
- UNMAPPED_FIELD_TYPE: 0 (tolerance skips all). COMPOSITE_KEY: 0. Skipped-by-parse: 0.
- Partial: 13 entities carry skipped fields, roughly two dozen field-skips in total (20 to-many
  collections, 3 `@Embeddable Address`, 1 `byte[]`, and the exact total is a build-time count). Of the
  13 partial, 9 are discovered-partial and 4 are failed-partial. The other 12 entities extract
  complete and carry no partial note.
- Generation: now succeeds for all 16 discoveries. `tibyan scan --out` writes a valid IR (closure
  guarantees it), and `tibyan generate --backend mcp` produces a valid manifest, where before the run
  exited 2 and generated nothing.
- Mis-bound: exactly 0. The 9 new discoveries were verified from source to return the right entity by
  its id; tolerance changed only whether they reached discovery, never how matching decided.

Row: discovered 16, failed 9 (NO_BACKING_METHOD 7, NO_IDENTIFIER 1, AMBIGUOUS_BINDING 1),
skipped-by-parse 0, partial 13, mis-bound 0. Total 16 plus 9 equals 25.

The one named uncertainty: the exact skipped-field count (the "roughly two dozen") depends on how many
unmapped fields each of the 13 entities carries and is confirmed only at build time; the partial
entity count (13) and the discovered count (16) are firm from the source verification.

### 10.2 pms-backend forecast (after the build)

No movement, and the reason is concrete. After E3, all 43 pms-backend entities already extract
(the E3-plus-E2 row shows zero UNMAPPED_FIELD_TYPE), because pms-backend has no to-many collection,
no `@Embeddable`, no `byte[]`, and no unresolvable-type fields for tolerance to skip. And because all
43 extract, no entity is absent, so model closure prunes nothing. The row is identical to the
E3-plus-E2 row: discovered 2, failed 41 (NO_BACKING_METHOD 38, COMPOSITE_KEY 3), skipped-by-parse 27,
partial 0, mis-bound 0. If any pms entity turns out to carry a tolerated field shape, its only effect
would be a partial note, never a change in discovered or mis-bound; none is expected.

### 10.3 The hard bar

Unchanged and absolute: mis-bound exactly 0 on both apps. Tolerance and closure must never soften
discovery: the 4.1 and 4.2 matching rules, eligibility, and the never-guess pooling are untouched, and
a partial entity's discovery must bind to exactly the method it would have bound to with a complete
shape. A single mis-binding on either benchmark stops all phase work.

---

## 11. E5 build result: actual versus forecast (2026-07-02)

The partial-model integrity build (Volume 5 sections 4.7 to 4.9) was run against both benchmarks. The
hard bar held: mis-bound 0 on both. `mvn clean verify` is green, the example-app golden byte-identical.

### 11.1 erp-backend, number by number

| Metric | Forecast (section 10.1) | Actual | Match |
|---|---|---|---|
| Discovered | 16 | 16 | yes |
| Mis-bound | 0 | 0 | yes |
| NO_BACKING_METHOD | 7 | 6 | no (composition, see below) |
| NO_IDENTIFIER | 1 (EmailTemplate) | 1 (EmailTemplate) | yes |
| AMBIGUOUS_BINDING | 1 (User) | 2 (User, Quote) | no (composition) |
| UNMAPPED_FIELD_TYPE | 0 | 0 | yes |
| Partial entities | 13 | 13 | yes |
| Skipped fields | ~two dozen | 22 | within the stated uncertainty |
| scan --out | valid, exit 0 | valid, exit 0 | yes |
| Generation | 16 tools | 16 tools | yes |

The discovered count, mis-bound, partial count, and generation all matched the forecast exactly. Two
per-entity compositions deviated, and both are findings explained, not massaged. They cancel in the
count (one entity left discovered, one entered), which is why discovered held at 16.

Deviation 1, Quote is AMBIGUOUS, not discovered. Forecast had Quote discovering via rule 4.1
(`QuoteServiceImpl#findById` returns Quote). Actual: Quote is AMBIGUOUS_BINDING, because
`QuoteServiceImpl` also declares `getInvoice(UUID)`, which loads Quote by primary key and maps it in
one step via `reportGenerationService.generateInvoice(quote)` returning `byte[]`, a genuine rule 4.2
match (view type `byte[]`). Two real by-id reads of Quote (the entity and the invoice rendering) tie,
so the pool is ambiguous and refuses. Correct behavior, never a guess; the forecast missed the
competing method because the source survey searched only for `findById`.

Deviation 2, VariationGroup discovers, not NO_BACKING. Forecast had VariationGroup as NO_BACKING (no
`findById`). Actual: it discovers via `VariationGroupServiceImpl#getVariationGroup(UUID)`, whose body
is `return variationGroupRepository.findById(groupId).orElseThrow(...)`, a differently-named
entity-returning finder that rule 4.1 catches by shape. The forecast's `findById`-only search missed
it. Correct discovery, hand-verified below.

Hand-verification of the nine new discoveries (guilty until proven correct):

| Capability | Binding | Verified |
|---|---|---|
| getContact | ContactServiceImpl#findById | `public Contact findById(UUID)` returns Contact by its UUID id |
| getContactCategory | ContactCategoryServiceImpl#findById | `public ContactCategory findById(UUID)` returns it by id |
| getContactGroup | ContactGroupServiceImpl#findById | `public ContactGroup findById(UUID)` returns it by id |
| getTransaction | TransactionServiceImpl#findById | `public Transaction findById(UUID)` returns it by id |
| getProduct | ProductServiceImpl#findById | `public Product findById(UUID)` returns Product by id |
| getProductCategory | ProductCategoryServiceImpl#findById | `public ProductCategory findById(UUID)` returns it by id |
| getProductGroup | ProductGroupServiceImpl#findById | `public ProductGroup findById(UUID)` returns it by id |
| getPayment | PaymentServiceImpl#findById | `public Payment findById(UUID)` returns Payment by id |
| getVariationGroup | VariationGroupServiceImpl#getVariationGroup | body loads `variationGroupRepository.findById(groupId)` and returns the VariationGroup; entity-returning, one UUID param |

Quote, forecast as the ninth new discovery, is instead AMBIGUOUS (verified above). The other eight
findById discoveries and getVariationGroup are all correct, right method on the right entity, UUID id.
The seven pre-existing discoveries are unchanged. Mis-bound is 0.

### 11.2 pms-backend, number by number

Byte-identical to the E3-plus-E2 row, as forecast: discovered 2, failed 41 (NO_BACKING_METHOD 38,
COMPOSITE_KEY 3), skipped-by-parse 27, partial 0, mis-bound 0. pms has no to-many collection, no
`@Embeddable`, no `byte[]`, no unresolvable-type field, so tolerance skips nothing; and all 43
entities already extract, so closure prunes nothing. The phase moved pms not at all, which is the
proof it is additive.

### 11.3 The model-closure finding

Model closure (section 4.7) was implemented exactly as ratified: a pass after extraction drops every
`ENTITY_REF` field and `Relation` whose target is absent, and discloses it. During the build a
structural fact surfaced: given field tolerance (section 4.8), every scanned `@Entity` extracts, and
an `ENTITY_REF` is emitted only for a type that resolves in the registry as an `@Entity`, so every
`ENTITY_REF` target is present. The scan path therefore never produces a dangling reference, and
closure never fires on any real input; on erp it dropped nothing, and the acceptance shape (the
discoveries generate) is met because tolerance leaves no dangling reference to begin with.

This is recorded, not worked around. Closure is retained as the standing producibility invariant, so a
model that passes validation is always producible whatever fraction extracted, even if a future phase
reintroduces a way for an entity to be absent (a parse-skipped entity file that another entity
resolves against, or a later shape tolerance does not cover). Because the scan path cannot exercise it,
its drop-and-disclose logic is unit-tested directly on a synthetic model (PartialModelIntegrityTest,
G1 and G2). The invariant holds; its trigger is currently unreachable, and that is a healthy state, not
a gap.

---

## 12. E4 list-read census and forecast (erp-backend, read-only, 2026-07-02)

The design half of phase E4 (list reads, Volume 5, section 4.10, pending ratification) needs the
list-read inventory of both benchmarks. This section is erp-backend's, traced from source, read only,
no tool change. The pms-backend census and forecast are in docs/experiments/second-app-trial.md,
section 8.6.

### 12.1 The census (every @Service method returning a collection or page)

Classified by shape: (a) bare `findAll()`, no parameters; (b) `findAll(Pageable)`, a lone framework
paging parameter; (c) filtered, one or more domain parameters; (d) view-returning list or projection;
(e) other or scalar. Shapes (a) and (b) additionally require the body to be a verbatim framework
pass-through (`return <repo>.findAll(...)`) to count as the modeled list-all shape; a no-parameter
method whose body transforms the result is not shape (a), it is a transformed read, grouped with the
deferrals.

| Shape | Count | Detail |
|---|---|---|
| (a) bare `findAll()`, clean pass-through | 3 methods | Account, Transaction, Truck (each `return <repo>.findAll();`) |
| (b) `findAll(Pageable)`, clean pass-through | 8 methods | Contact, ContactCategory, ContactGroup, ProductGroup, VariationGroup, EmailTemplate (one each), ProductCategory (one of its two) |
| (a)+(b) modeled list-all total | 11 methods, 10 entities | ProductCategory has both a bare and a paged clean method (the indistinguishable pair) |
| (c) filtered, one or more domain parameters | ~30 methods | `searchByFields` (Account, User, Truck 10-param, EmailTemplate), `findAll(String search, Pageable)` (ContactType, Brand, BarcodeType, Unit, ContactCategory, ProductCategory), `findByCategory`/`findBySupplier`/`findByParentId`/`findByContactId`/`findByProductId`/`findByNameContaining`/`search`/`findContacts`/`getHasAccounts`/`getHasNoAccounts`/`findByAccountId`/`getMessagesByContact`/`getMessagesByNumber` |
| (d) view-returning list or projection | ~9 methods | `getListableContacts`/`getListableProducts` (`Page<NameIdDTO>`), `getSupplierContacts`/`findByAccountType` (`List<NameIdDTO>`), `QuoteServiceImpl#findAll` (`List<QuoteReadDTO>`), `DeliveryServiceImpl#findAll` (`List<DeliveryReadDTO>`), `findAllTickets` (`List<TicketReadDTO>`), `findCompletedByType` (`List<TransactionReadDTO>`), `getHierarchicalCategoryDTOs` (`List<HierarchicalCategoryDTO>`) |
| transformed body, no domain parameter (not shape a) | 5 methods | `Transaction#findCompleted`, `#findCompletedWithAssociations` (post-`findAll` stream-filter), `User#findAll` (post-filter deleted users), `Product#findAll(Pageable)` (custom query + `convertToFullName` + manual `PageImpl`), `Contact#findByHasNumber` (derived query) |
| (e) other or scalar | several | `getUnknownSenders`, `listCognitoGroups` (`List<String>`), mapping helpers (`mapToInventoryTransactionResponse`), and writes returning a list (`create`) |

Repository contract confirmed for the modeled shape: the loads resolve to Spring Data repositories of
the element type, for example `ContactRepository extends JpaRepository<Contact, UUID>` and
`EmailTemplateRepository extends JpaRepository<EmailTemplate, EmailTemplateType>`, so the framework
`findAll` / `findAll(Pageable)` is inherited and resolvable by the same `RepositoryCatalog` E2 built.

### 12.2 The modeled positives (11 methods, 10 entities)

| Entity | Method | Return | Body | E4 outcome |
|---|---|---|---|---|
| Contact | ContactServiceImpl#findAll(Pageable) | Page<Contact> | `return contactRepository.findAll(pageable);` | listContact (paged) |
| ContactCategory | ContactCategoryServiceImpl#findAll(Pageable) | Page<ContactCategory> | `return contactCategoryRepository.findAll(pageable);` | listContactCategory (paged) |
| ContactGroup | ContactGroupServiceImpl#findAll(Pageable) | Page<ContactGroup> | `return contactGroupRepository.findAll(pageable);` | listContactGroup (paged) |
| Account | AccountServiceImpl#findAll() | List<Account> | `return accountRepository.findAll();` | listAccount |
| Transaction | TransactionServiceImpl#findAll() | List<Transaction> | `return transactionRepository.findAll();` | listTransaction |
| Truck | TruckServiceImpl#findAll() | List<Truck> | `return truckRepository.findAll();` | listTruck |
| ProductGroup | ProductGroupServiceImpl#findAll(Pageable) | Page<ProductGroup> | `return productGroupRepository.findAll(pageable);` | listProductGroup (paged) |
| VariationGroup | VariationGroupServiceImpl#findAll(Pageable) | Page<VariationGroup> | `return variationGroupRepository.findAll(pageable);` | listVariationGroup (paged) |
| EmailTemplate | EmailTemplateServiceImpl#findAll(Pageable) | Page<EmailTemplate> | `return emailTemplateRepository.findAll(pageable);` | listEmailTemplate (paged) |
| ProductCategory | ProductCategoryServiceImpl#findAll() and #findAll(Pageable) | List / Page<ProductCategory> | both clean pass-throughs | LIST_AMBIGUOUS (two candidates), no capability minted |

Two facts this table proves, both requested in the design brief:

- `listEmailTemplate` is discovered even though `getEmailTemplate` is not. EmailTemplate's `@Id` is an
  enum, so its by-id read fails NO_IDENTIFIER (section 11.1), but a list-all needs no bindable id, so
  `listEmailTemplate` is clean. E4 adds a capability on an entity E5 could not read at all.
- ProductCategory is the structurally-indistinguishable pair, live: `findAll()` and `findAll(Pageable)`
  are both clean list-all methods, and because paging is unmodeled they produce the identical
  capability. The tool refuses (LIST_AMBIGUOUS) rather than prefer the paged overload, which would be a
  nominal guess. ProductCategory keeps its by-id discovery (getProductCategory); only its list
  candidate refuses.

### 12.3 The forecast (after the E4 build, stated before it)

- Discovered: 25 (by-id 16, list 9). The 16 by-id discoveries are byte-identical to the E5 row
  (E4 is additive; the list pool and the by-id pools are disjoint by construction, Volume 5,
  section 4.10.5). Nine new list capabilities: listContact, listContactCategory, listContactGroup,
  listAccount, listTransaction, listTruck, listProductGroup, listVariationGroup, listEmailTemplate.
- List-refused: 1, LIST_AMBIGUOUS (ProductCategory, two clean list-all methods). A new cause code.
- By-id failed: 9, unchanged (NO_BACKING_METHOD 6, NO_IDENTIFIER 1, AMBIGUOUS_BINDING 2). The entity
  denominator is preserved: by-id discovered 16 plus by-id failed 9 equals 25 entities.
- Deferred list reads (named, not failed): roughly 44 methods across shapes (c), (d), transformed, and
  (e), each reported per method under "Deferred list reads" with its reason (filtered, view-returning,
  transformed body, or non-framework terminal). None is counted as a discovery or a failure; they are
  out of E4 scope by design, and are the evidence for the successor filtered/view-list phase.
- Partial: 13, unchanged. Skipped-by-parse: 0, unchanged. Mis-bound: exactly 0.
- Generation: the 9 new list capabilities generate to MCP (each tool carries `outputMultiple: true`,
  the paged ones also `paged: true`) and to OpenAPI (each a GET on the collection path returning
  `{type: array, items: {$ref to the entity}}`, the paged ones carrying `x-tibyan-paged`). The no-arg
  list reads (listAccount, listTransaction, listTruck) are fully executable; the paged reads carry the
  deferred runtime-paging note (Volume 6, section 3.3).

Row (projected, not a run row until the build): discovered 25 (by-id 16, list 9), list-refused 1
(LIST_AMBIGUOUS), by-id failed 9 (NO_BACKING_METHOD 6, NO_IDENTIFIER 1, AMBIGUOUS_BINDING 2),
skipped-by-parse 0, partial 13, mis-bound 0.

The one named uncertainty: the exact count of deferred list reads ("roughly 44") depends on final
per-method classification at build time; the 9 discovered list capabilities, the 1 LIST_AMBIGUOUS, and
the unchanged 16 by-id are firm from source verification.

### 12.4 The mis-bound guard, hand-checked from source

The nine forecast list bindings were each checked against the actual service body: every one is a
single `return <repo>.findAll(...);` on a Spring Data repository of exactly the element entity, with no
post-filter, so none presents a subset as the whole and none binds the wrong entity's collection. The
two live traps were confirmed to refuse: `Transaction#findCompleted()` (a no-parameter method that
post-filters status COMPLETED) is refused on the body-shape condition, so listTransaction binds to
`findAll()` and never to `findCompleted()`; and ProductCategory's two clean methods refuse as
ambiguous. Mis-bound stays 0 in the forecast, and the two ways a list can mis-bind are both structurally
prevented.

---

## 13. E4 build result: actual versus forecast (erp-backend, 2026-07-02)

The E4 build (Volume 5 section 4.10) was run: `tibyan scan examples/erp-backend`, exit 0, and
`tibyan scan --out` plus `tibyan generate` for both backends. The hard bar held: mis-bound 0.

| Metric | Forecast (section 12.3) | Actual | Match |
|---|---|---|---|
| Discovered total | 25 | 25 | yes |
| Discovered by-id | 16 | 16 | yes |
| Discovered list | 9 | 9 | yes |
| List-refused (LIST_AMBIGUOUS) | 1 (ProductCategory) | 1 (ProductCategory) | yes |
| By-id failed | 9 (NO_BACKING 6, NO_IDENTIFIER 1, AMBIGUOUS 2) | 9 (same) | yes |
| Partial | 13 | 13 | yes |
| Mis-bound | 0 | 0 | yes |
| Generation (both backends) | 25 | 25 | yes |
| Named deferrals | ~44 (stated uncertainty) | 56 | within the stated uncertainty |

Every firm number matched exactly. The 16 by-id discoveries are byte-identical to the E5 row, bindings
and partial qualifiers alike (E4 is additive by construction: the list pool and the by-id pool are
disjoint). The 9 list capabilities are exactly the forecast ones, and `listEmailTemplate` discovers
though `getEmailTemplate` fails NO_IDENTIFIER, the identifier-independence proof live on the benchmark.
ProductCategory keeps its by-id discovery and refuses only its list candidate (two clean `findAll`
methods), LIST_AMBIGUOUS.

The one soft number resolved: 56 named deferrals against the forecast's "~44" (which section 12.3
flagged as the one uncertainty, confirmed only at build time). The build-time classifier names every
collection-container-returning `@Service` method that is not a clean list-all, which is more inclusive
than the hand census: it includes view-DTO-returning reads, mapping helpers that return collections, and
write methods that return a collection (a `create` returning `List<DTO>`). Each is a truthful deferral
(the phase does not model it), so the higher count is honest, not a miss; the reason string names why
each is deferred. This is within the stated uncertainty and is correctness-neutral.

Hand-verification of the nine list discoveries (guilty until proven correct), each opened at source:

| Capability | Binding | Verified |
|---|---|---|
| listAccount | AccountServiceImpl#findAll | `public List<Account> findAll() { return accountRepository.findAll(); }`; AccountRepository extends JpaRepository<Account, UUID>; element Account; not paged |
| listContact | ContactServiceImpl#findAll | `public Page<Contact> findAll(Pageable p) { return contactRepository.findAll(p); }`; element Contact; paged |
| listContactCategory | ContactCategoryServiceImpl#findAll | `Page<ContactCategory> findAll(Pageable)` returns `contactCategoryRepository.findAll(pageable)`; element ContactCategory; paged |
| listContactGroup | ContactGroupServiceImpl#findAll | `Page<ContactGroup> findAll(Pageable)` returns `contactGroupRepository.findAll(pageable)`; element ContactGroup; paged |
| listEmailTemplate | EmailTemplateServiceImpl#findAll | `Page<EmailTemplate> findAll(Pageable)` returns `emailTemplateRepository.findAll(pageable)`; EmailTemplateRepository extends JpaRepository<EmailTemplate, EmailTemplateType>; element EmailTemplate; paged. getEmailTemplate is NO_IDENTIFIER (enum @Id), and the list is independent of it |
| listProductGroup | ProductGroupServiceImpl#findAll | `Page<ProductGroup> findAll(Pageable)` returns `productGroupRepository.findAll(pageable)`; element ProductGroup; paged |
| listTransaction | TransactionServiceImpl#findAll | `public List<Transaction> findAll() { return transactionRepository.findAll(); }`; element Transaction; not paged. The sibling `findCompleted()` post-filters and is correctly refused, so listTransaction binds to findAll, not findCompleted |
| listTruck | TruckServiceImpl#findAll | `public List<Truck> findAll() { return truckRepository.findAll(); }`; element Truck; not paged |
| listVariationGroup | VariationGroupServiceImpl#findAll | `Page<VariationGroup> findAll(Pageable)` returns `variationGroupRepository.findAll(pageable)`; element VariationGroup; paged |

Every list binds to a verbatim `findAll` on a Spring Data repository of exactly the element entity, with
no post-filter, so none presents a subset as the whole and none binds the wrong entity's collection.
Product gets no list (its `findAll(Pageable)` is a custom-query transform with a manual `PageImpl`,
correctly refused). Mis-bound is 0.

Generation was confirmed end to end: `tibyan generate --backend mcp` and `--backend openapi` from the
erp IR each produce all 25 capabilities. A representative paged list, `listContact`, renders in the MCP
manifest as `"output": "ENTITY_REF(Contact)", "outputMultiple": true, "paged": true` with the
description `List all Contact. Returns a page of results; the total count and page envelope are not
modeled.`, and in OpenAPI as a GET on `/contact` whose 200 schema is `{"type": "array", "items":
{"$ref": "#/components/schemas/Contact"}, "x-tibyan-paged": true}` with the page-envelope disclosure.
The Contact component still discloses its partiality (the E5 rider rides through the element schema).

---

## 14. P1b census and forecast (custom-query by-pk terminal, erp-backend, read-only, 2026-07-02)

The design half of P1b (Volume 5 section 4.2.10, pending ratification) needs the custom-query by-id read
inventory. This is erp-backend's, traced from source, read only, no tool change. The pms-backend census
is in docs/experiments/second-app-trial.md, section 8.8.

Census. erp uses a `findByIdWith*` `@Query` idiom widely, all direct on Spring Data repositories, all
clean by-primary-key selects (`SELECT [DISTINCT] <alias> FROM <E> <alias> LEFT JOIN FETCH ... WHERE
<alias>.id = :id`, named parameter). Classified by what consumes each, since condition 4 (and P1b) is
only consulted for a view-returning service read:

| Repository `@Query` method | JPQL (WHERE) | Consumed by service `findById` returning | P1b relevance |
|---|---|---|---|
| `DeliveryRepository#findByIdWithRelationships` | `... WHERE d.id = :id` | `DeliveryReadDTO` (view) | P1b clears the load; the mapper still refuses (below) |
| `ProductRepository#findByIdWithAssociations` | `... WHERE p.id = :id` | `Product` (entity) | 4.1 already; body never read |
| `ContactRepository#findByIdWithAssociations` | `... WHERE c.id = :id` | `Contact` (entity) | 4.1 already |
| `ContactGroupRepository#findByIdWithContacts` | `... WHERE ...id = :id` | `ContactGroup` (entity) | 4.1 already |
| `TicketRepository#findByIdWithAssociations` | `... WHERE ...id = :id` | `Ticket` (entity) | 4.1 already |
| (framework `findById`, not a `@Query`) | n/a | `QuoteServiceImpl#getInvoice -> byte[]` (view) | P1a already; Quote AMBIGUOUS today |

Filtered custom queries, refused and structurally distinct (not by-primary-key): `DeliveryRepository#searchByFields`
(`WHERE (:search IS NULL OR ...) AND (:status IS NULL OR d.status = :status) AND ...`, eight parameters),
`ProductRepository#findProducts`, `EmailTemplateRepository#searchByFields`. These are multi-parameter, so
condition 2 already refuses them; they are the live proof that the by-id `@Query` shape and the filtered
`@Query` shape are structurally separable in this app, not merely name-separable.

The two questions the task posed, answered from source:

- Delivery: `DeliveryServiceImpl#findById` returns `DeliveryReadDTO`, loads `deliveryRepository.findByIdWithRelationships(id).orElseThrow(...)` (the clean by-pk `@Query`, direct), then maps `mapToDTO(delivery)`. `mapToDTO` is a private method on the service itself, called with no receiver (implicit `this`). Under condition 9 this does not resolve to a mapper on a field, a static factory, a constructor, or a method reference, so Delivery has TWO independent refusals today: the P1b load (which P1b clears) and the implicit-this mapper (which P1b does not touch). P1b alone does not unlock Delivery.
- Quote: `QuoteServiceImpl#getInvoice(UUID)` returns `byte[]` and loads via the framework `quoteRepository.findById(id)` (verified at source), maps `reportGenerationService.generateInvoice(quote)`. Its load is framework, not custom, so getInvoice is already a P1a 4.2 match today, competing with the entity-returning `findById` (getQuote via 4.1). Quote is therefore AMBIGUOUS today, and P1b does not change its pool: getInvoice was never a P1b near-miss.
- Ticket: `TicketServiceImpl#findById` returns `Ticket` (entity), so it is a section 4.1 discovery already; 4.1 binds on the return shape and never reads the body, so the fact that Ticket's body loads via `findByIdWithAssociations` (a custom query) is irrelevant. P1b's design does not touch already-discovered capabilities, verified here on Ticket, Product, Contact, and ContactGroup.

Delivery's query itself, checked against the strict rule (design-closure, 2026-07-02): its five fetches
are all `LEFT JOIN FETCH` and its WHERE is the single id equality, so the query PASSES the strict rule
(unlike pms Tenant, whose query fails on an inner `JOIN FETCH t.user`; second-app-trial.md section 8.8).
Delivery is refused only at the mapper, not the load.

Forecast (build deferred; a target for the eventual build): byte-identical to the current erp row. Delivery stays refused (its
mapper), Quote stays AMBIGUOUS (its framework load), the four entity-returning reads are untouched
(4.1). The only visible change is Delivery's near-miss message shifting from the load terminal to the
same-service mapper. Row (projected): discovered 25 (by-id 16, list 9), list-refused 1, failed 9
(NO_BACKING_METHOD 6, NO_IDENTIFIER 1, AMBIGUOUS_BINDING 2), skipped-by-parse 0, partial 13, mis-bound 0.
This makes erp the honest negative control for P1b: a benchmark that has the clean by-pk `@Query` shape in
abundance yet gains no discovery from it, because the one view-returning consumer is blocked by an
unrelated mapper rule. Combined with the design-closure finding on pms (Tenant's query fails the strict
rule on an inner-fetch existence filter, section 8.8), P1b discovers ZERO across both benchmarks. There is
no one-entity unlock; the strict rule refuses everything it surveyed, which is the correct behavior. P1b
is ratified as the by-primary-key floor E6 builds on (Volume 5 section 4.2.10.6), and its standalone build
is deferred (section 4.2.10.7).

---

## 15. E6 filtered-read census (filter-semantics-plus-envelope, erp-backend, read-only, 2026-07-02)

The E6 design (Volume 5 section 4.11) needs the filtered-read inventory. This is erp-backend's, from
source. Unlike pms, erp's filters are directly typed (no envelope wrappers) and its filters live in the
query, so erp has no visibility half; its interest is the semantics half and the derived-query exception.
Counts:

| Shape | Count | Note |
|---|---|---|
| (a)/(b)/(c) filtered reads, returning `Page<E>` or `List<E>` directly | ~30 | section 12 shape (c): `searchByFields`, `findAll(String search, Pageable)`, `findByCategory`, `findBySupplier`, `findByParentId`, and the rest |
| (d) projection or view-returning list | ~9 | section 12 shape (d): `Page<NameIdDTO>`, `List<XxxReadDTO>` |
| transformed body | 5 | section 12 |
| (e) envelope-wrapped, existence hidden | 0 | erp uses no `*ResultSet` wrappers; its reads are directly typed and already visible (E4 deferred them by name) |

The split within erp's filters, which is the whole reason erp matters to E6:

- Spring Data derived-query filters: many, and their meaning is framework-defined. `findByNameContainingIgnoreCase(String name, Pageable)`, `findByParentId(UUID parentId, Pageable)`, `findByProductCategoryId`, `findByAccountType(AccountType, Pageable)`, `findByMethod`, `findByTransactionId`, `findByAccountIdAndStatus`, `findByTypeAndStatus`. The framework parses the method name against the entity's properties and generates the predicate deterministically; reading it is reading a framework contract, not nominal inference. This is the one structural avenue for a verified filter (Volume 5 section 4.11.3).
- Custom `@Query` filters: `searchByFields` (`WHERE (:search IS NULL OR ...) AND ...`), `findProducts`, `findContacts`. Arbitrary JPQL, meaning in the text, not derivable from the name; structurally as opaque as pms's query builders once the WHERE is more than the id equality.

erp is therefore the negative-plus-partial control for E6: it shows the one honest structural avenue
exists (derived-query filters) but is a minority mixed with unreadable `@Query` filters, and it confirms
that the majority of filtered reads have no structural representation. This supports the E6 recommendation
to defer with a documented ceiling (Volume 5 section 4.11.4): erp's ceiling is roughly 44 deferred reads
beyond its 25 discoveries, of which only the derived-query subset is even a candidate for a future narrow
structural rule.

---

## 16. E6a transparency census (envelope visibility, erp-backend, read-only, 2026-07-02)

The E6a design (Volume 5 section 4.11.7) makes wrapper-hidden reads visible. erp uses no wrapper or
envelope return types: every read returns `Page<E>`, `List<E>`, `Set<E>`, or a directly typed view
collection (`Page<NameIdDTO>`, `List<XxxReadDTO>`), all of which 4.10 already recognizes (and defers by
name when filtered or view-returning). There is no `*ResultSet`-style wrapper anywhere in erp.

So E6a is inert on erp: there is no unrecognized wrapper return type for it to make transparent, and it
recognizes nothing new. Forecast (build deferred): byte-identical. Discovered 25 (by-id 16, list 9),
mis-bound 0, unchanged; visibility gained 0. erp confirms E6a is a pms-idiom phase: it addresses the
envelope-wrapping style pms uses and erp does not.

---

## 17. Entity Identity forecast (erp-backend, read-only, 2026-07-13)

Phase Entity Identity (Volume 4 section 3.7, Volume 5 section 4.12) adds a qualified `key` to each entity
and derives the display `name` from it, disambiguating only where simple names collide across bounded
contexts. This forecasts the phase against erp-backend, before any build.

Collision census: erp-backend is a single-module app of 25 entities with no colliding simple names;
every `@Entity` simple name is unique. The display-name rule's condition 1 applies to every entity, and
no entity is renamed. (This is expected: erp is single-module, so the multi-context name reuse that
motivates the phase does not arise here; erp is the negative control that proves the phase is inert
where there are no collisions, just as the private enterprise benchmark is the positive case.)

Forecast (build deferred): byte-identical in generated output. Row (projected): discovered 25 (by-id 16,
list 9), failed 9, list-refused 1, partial 13, mis-bound 0, UNCHANGED (25 entities = 16 by-id discovered +
9 by-id failed; the stale "failed 18" was the pre-E3/E4 first-run number). Every capability keeps its name and binding; the
16 GET operations (with 13 partial entity schemas) and the MCP manifest are byte-identical; the survey's
`collisions resolved` line is omitted (zero). The only change is additive in the serialized IR: each
extracted entity gains a `key` field, while reference tokens, display names, and the partiality
disclosures are unchanged, so an IR-serialization golden updates with a visible additive diff and the
backend goldens stay byte-identical. Any colliding simple name found at build time (this read-only
census found none) would be a regression finding. mis-bound stays 0.

## 18. Entity Identity actual (erp-backend, built and measured, 2026-07-13)

The phase is built (Volume 5 section 4.12). Re-run against erp-backend, the forecast is confirmed
exactly. Row (actual): discovered 25 (by-id 16, list 9), failed 9 (NO_IDENTIFIER 1, NO_BACKING_METHOD 6,
AMBIGUOUS_BINDING 2), list-refused 1 (the ProductCategory list overload), partial 13 (22 fields skipped),
mis-bound 0, UNCHANGED. The read-only census held: no colliding simple names at build (0 regressions),
so every entity kept its simple name.

Byte-identical proven, not asserted. The generated output (all 25 MCP tool sources and
`tibyan/capabilities.json`, and the OpenAPI document) was generated on the current build and on the
pre-phase commit and compared with `diff -r`: zero differences across all 27 generated files. Every
get and list capability keeps its name and binding, and every partiality disclosure is unchanged. The
only change is the additive `key` field on each serialized entity. mis-bound stays 0. erp is the
negative control confirmed: the phase is inert where there are no collisions.

## 19. Discovery-Gap forecast (erp-backend, read-only, 2026-07-13)

Phase Discovery-Gap (Volume 5, section 4.13, designed 2026-07-13) adds two general provider rules,
inherited `@Id` and the private-helper hop. This forecasts the phase against erp-backend, before any
build.

Shape census (read-only): erp-backend exhibits NONE of the three shapes. All 25 entities declare a direct
`@Id`; no `@Entity` uses `extends`, and the tree has no `@MappedSuperclass` or `@Inheritance`, so the
inherited-`@Id` rule never fires. Reads load directly via Spring Data repositories (all 25 `*Repository`
extend `JpaRepository`) and return the entity or a view in the public body, never through a private
same-class helper, so the helper-hop rule never fires. Every hand-written port has its implementation
in the scanned source. erp is the negative control for this phase just as it was for Entity Identity:
single-context, direct-repository, no inheritance, no delegation indirection.

Forecast (build deferred): byte-identical. Row (projected): discovered 25 (by-id 16, list 9), failed 9,
list-refused 1, partial 13, mis-bound 0, UNCHANGED. Every capability keeps its name and binding; the MCP
manifest and OpenAPI document are byte-identical. Any occurrence of one of these shapes at build would be
a finding. mis-bound stays 0.

## 20. Discovery-Gap actual (erp-backend, built and measured, 2026-07-13)

The phase is built, general scope only (Volume 5, section 4.13). Re-run against erp-backend, the forecast
is confirmed exactly. Row (actual): discovered 25 (by-id 16, list 9), failed 9 (NO_IDENTIFIER 1,
NO_BACKING_METHOD 6, AMBIGUOUS_BINDING 2), list-refused 1, partial 13 (22 fields skipped), mis-bound 0,
UNCHANGED.

The read-only census held: erp-backend exhibits none of the three shapes. All 25 entities declare a direct
`@Id` and none uses `extends`, so the inherited-`@Id` walk is never entered; reads load directly in the
public body, so the helper hop is never entered. erp is the negative control confirmed for this phase as
it was for Entity Identity: the general rules are inert where the shapes are absent. Notably its one
remaining NO_IDENTIFIER entity stays NO_IDENTIFIER (it declares no `@Id` and extends nothing, so there is
no ancestor to inherit from), which is the correct outcome and a useful guard that the walk does not
invent an identifier.

Byte-identical proven, not asserted: all 25 generated MCP tool sources, `tibyan/capabilities.json`, and
the OpenAPI document were generated on the current build and on the pre-build commit and compared with
`diff -r`: zero differences. mis-bound stays 0.

## 21. Controller-Authority census and forecast (erp-backend, read-only, 2026-07-19)

Phase Controller-Authority (Volume 5, section 4.14) was designed from a full read-only census of the
three benchmarks. This section records the erp half and the erp forecast. No tool change was made in this
session.

The controller census:

- Controller classes: 29 `@RestController` concrete classes (no `@Controller` view controllers). 14 of
  them split into a PLAIN in-tree interface carrying the `@GetMapping`/parameter annotations plus an
  `Impl` carrying `@RestController` and the bodies (the mapping is never duplicated on the impl); 15 are
  standalone with `@GetMapping` directly on the class. Bean names are qualified to avoid collisions
  (`adminProductController` versus `salesProductController`); Product, ProductCategory, Brand, Contact,
  Account, Transaction, Quote, and Delivery each have BOTH an admin and a sales controller. All
  controller sources parse clean.
- GET endpoints: 104, all via `@GetMapping` (zero `@RequestMapping(method = GET)`). For context: POST 27,
  PUT 2, DELETE 21, PATCH 20.
- GET parameter shapes: 30 by-id (exactly one `@PathVariable`, typically `UUID`), 7 path-id plus
  `Pageable`, 2 enum-keyed path variables (`EmailTemplateType`), 22 `@RequestParam` filtered (1 to 10
  filter params, all `required = false`), 29 no-parameter, 13 `Pageable`-only, 1 mixed. `Pageable` is
  pervasive (roughly 40 signatures); `@RequestBody`-on-GET is 0.
- GET return types: 102 of 104 return `ResponseEntity<X>` (X: entity 21, DTO 6, `Page<Entity>` 35,
  `Page<DTO>` 4, `List<Entity>` 16, `List<DTO>` 15, `List<String>` 2, `byte[]` 3); 2 return raw types
  (`byte[]`, `SseEmitter`). Entity-typed GETs dominate (72 of 104), consistent with erp's
  entity-returning service idiom.
- GET bodies: 39 are pure one-statement delegations (`return ResponseEntity.ok(service.x(...));`); 65
  wrap the one service call in try/catch status-mapping boilerplate or a trivial null-check branch; none
  contains business logic.
- Security, the headline: ZERO. No `@PreAuthorize`, `@PostAuthorize`, `@Secured`, `@RolesAllowed`,
  `@PreFilter`, `@PostFilter`, no `@EnableMethodSecurity`, no `SecurityFilterChain`, and no Spring
  Security dependency in the pom at all. erp is the security-absent benchmark: the phase's extractor must
  produce clean ABSENT states here, not crash and not invent.
- The pooling evidence (why the phase pools instead of minting per-endpoint capabilities): every one of
  the 25 discovered capabilities' service methods has at least one controller GET delegating to it, and
  several have MORE than one. `transactionService.findById` is invoked by FOUR controller methods (admin
  and sales `findById`, both one-statement strict anchors, plus admin and sales `findDetailsById`, both
  NON-strict associated endpoints whose bodies nest the load inside a mapping call);
  `productService.findById` and `accountService.findById` are each fronted by both an admin and a sales
  controller. A capability-per-endpoint model would emit up to four indistinguishable tools for one read.

Forecast (after the Controller-Authority build, stated before it):

- discovered 25 (by-id 16, list 9), UNCHANGED; failed, list-refused, partial rows UNCHANGED; mis-bound 0.
- Output BYTE-IDENTICAL: with zero security declarations anywhere, every capability's security state is
  ABSENT, which emits no field, no marker, and no description change. erp is the negative control proving
  the phase adds nothing where nothing is declared.
- The composition rule is exercised live and invisibly: Transaction's four associated endpoints (two
  strict, two non-strict) carry zero declarations, so the state is ABSENT; had any one of them declared a
  contract, the non-strict routes would force `securityUnverifiable (conflicting-declarations)`, and that
  exact shape is fixture CA-13's third case.
- The anchor machinery finds real strict anchors here (the 39 one-statement GETs, including by-id and
  list-all delegations to discovered bindings) and attaches nothing, which is the designed no-op. The 65
  try/catch bodies are associated-but-non-strict endpoints and, with no declarations, change nothing.
- Nothing else moves: filtered and Pageable-carrying GETs stay deferred exactly as rule 4.10 already
  classifies their service methods; the enum-keyed EmailTemplate GET anchors nothing by-id (the entity has
  no by-id capability; its listEmailTemplate is unaffected).

## 22. Controller-Authority actual (erp-backend, built and measured, 2026-07-19)

The phase is built (Volume 5, section 4.14) and re-run against erp-backend. The forecast held: erp is the
negative control, byte-identical.

- Row (actual): discovered 25 (by-id 16, list 9), failed 9 (NO_IDENTIFIER 1, NO_BACKING_METHOD 6,
  AMBIGUOUS_BINDING 2), list-refused 1, partial 13, mis-bound 0, UNCHANGED.
- Declared security: NONE. The scan emitted no `security:` survey line, because the application has zero
  method-security annotations and no Spring Security dependency, so every capability's state is ABSENT
  and no field is emitted.
- Generated output BYTE-IDENTICAL: the MCP manifest was regenerated and carries 25 tools and ZERO
  `requiredAuthorities`/`securityUnverifiable` fields; the OpenAPI document carries ZERO
  `x-tibyan-required-authorities`/`x-tibyan-security-unverifiable` extensions and no `securitySchemes`.
  ABSENT emits nothing, so both documents are identical to the pre-phase output.
- The pooling and anchor machinery ran live and invisibly: the strict one-statement anchors exist (the
  admin controllers), the multi-route cases exist (Transaction fronted by four controller methods,
  Product and Account by both admin and sales controllers), and with zero declarations everywhere the
  composition rule landed on ABSENT for all 25. mis-bound 0. erp confirms the phase adds nothing where
  nothing is declared.

## 23. Attestation forecast: the attestable ceiling (erp-backend, read-only, 2026-07-20)

Phase Attestation (Volume 5, section 4.15) restated against this benchmark's standing refusal ledger
(sections 12, 15, 16), in the new vocabulary: ATTESTABLE UP TO N GIVEN A KNOWLEDGEABLE HUMAN, never a
promise.

- AT-0 (structural floor and envelope unwrap): INERT here, as the ratified E6a forecast already states
  (section 16): erp uses no wrapper types (its reads return `Page<E>`, `List<E>`, `Page<NameIdDTO>`
  directly), so transparency recognizes nothing new. The P1b floor matcher confirms Delivery's custom
  query as a clean by-primary-key load (section 14) with the read still refused on its mapper; counts
  byte-identical. Discovered stays 25, mis-bound 0.
- AT-1 (predicate attestation): attestable up to roughly 30: the filtered reads of the section 15 census
  (derived-query filters, custom-`@Query` filters like `searchByFields`, and the domain-parameter list
  reads E4 defers by name). Named nuance, recorded for the roadmap: the DERIVED-QUERY subset
  (`findByNameContainingIgnoreCase`, `findByParentId`, `findByAccountType`) is the one family whose
  meaning a future STRUCTURAL rule could verify from the framework contract (the section 4.11.3
  exception); if that rule is ever built, those reads move from the attestable column to the verified
  column, the preferred direction, and the attestable ceiling shrinks accordingly. Attesting them first
  and verifying them later is honest in both states.
- AT-2 (shape attestation): attestable up to roughly 14: the roughly 9 projection and view lists
  (`Page<NameIdDTO>`, `List<XxxReadDTO>`) and the roughly 5 transformed-body reads (the
  `findCompleted()` post-filter shape, the manual `PageImpl` reconstructions, Delivery's implicit-this
  mapper read), each attested for what it returns, with the tool still refusing any whose body mutation
  check fails.
- AT-3 (boundary, deferred): 0 candidates today: erp declares no security at all (section 21) and has no
  delegation reads.
- EXCLUDED: the binary and scalar returns (`byte[]` reports, `SseEmitter`, `List<String>` helpers), the
  AMBIGUOUS_BINDING pairs (Quote, ProductCategory list pair: attestation never selects bindings), and
  every write.

The ceiling in one line: 25 verified + up to roughly 44 attestable (AT-1 roughly 30, AT-2 roughly 14).
Attested counts are reported separately from discovered, never summed; every existing discovered row
stays byte-identical through every sub-phase, and movement is a regression finding.
