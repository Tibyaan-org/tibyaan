# Experiment: third real app trial (erp-backend)

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
