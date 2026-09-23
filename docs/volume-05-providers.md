# Volume 5: Capability Providers

Status: Stable for the abstraction and the Spring provider. Future providers are described as direction. A Capability Provider is any source that produces Capability IR. It is the only part of the system that reads the original system, and it produces IR and nothing else.

---

## 1. The abstraction

```mermaid
flowchart TD
    CP[CapabilityProvider] --> SA[SourceAnalyzer]
    SA --> SP[SpringCapabilityProvider]
    SP --> EX[Extractors]
    CP --> DB[Database provider]
    CP --> OA[OpenAPI provider]
    CP --> MAN[Manual builder]
    CP --> AI[AI-assisted provider]
```

```java
public interface CapabilityProvider {
    CapabilityModel provide(ProviderInput input);
}
public interface SourceAnalyzer extends CapabilityProvider { }   // a provider that reads code
```

## 2. Why provider is broader than analyzer

Not every input analyzes source code. An OpenAPI file, a database schema, or a human using a manual builder all describe a system without any code analysis. If the top abstraction were named Analyzer, those inputs would not fit it, and the name would have to change later. CapabilityProvider is the general contract; SourceAnalyzer is the specialization for inputs that read code (ADR-0003).

## 3. The provider contract

A provider takes an input that identifies a source and returns a Capability IR. It must:

1. Produce only IR. It never generates output and never knows what backends exist.
2. Map source types into the closed primitive type system (Volume 4, section 4).
3. Be deterministic. The same source yields the same IR.
4. Depend only on the plugin API and the core, never on a backend or a runtime (Volume 12, section 5).

## 4. The Spring provider (MVP)

`SpringCapabilityProvider` is a SourceAnalyzer that reads a Spring and Java project from source using JavaParser (ADR-0010). It is organized as extractors. The Entity and Service extractors are enabled: the Entity extractor reads entities and their read intents, the Service extractor discovers the real binding, and the provider joins the two (section 4.1). The repository, controller, and annotation extractors remain placeholders. This refines ADR-0011, which enabled only the Entity extractor at the start of the MVP.

| Extractor | Reads | Status |
|---|---|---|
| EntityExtractor | Classes annotated as entities, their fields, and the read intent for each | Enabled |
| ServiceExtractor | Service classes and their public methods, to discover a read capability's real binding | Enabled (read-by-id binding discovery) |
| RepositoryExtractor | Repository interfaces and their queries | Placeholder; the E2 build enables it to catalog Spring Data repositories (entity and id type arguments), hand-written repository interfaces, and their one-hop delegating implementations, the load evidence of section 4.2 |
| ControllerExtractor | Controllers, to learn exposed operations | Enabled (phase Controller-Authority, section 4.14, built 2026-07-19): a neutral GET-endpoint catalog whose anchors decorate existing capabilities with the declared security contract, granting no discovery |
| AnnotationExtractor | Annotations, as generic hints | Placeholder |

The extractor pattern keeps the provider correct from day one and lets coverage grow without reshaping the module. Each placeholder extractor will contribute an independent slice of the IR. The Entity and Service extractors are different: they cooperate to produce one fact (the discovered binding), and that cooperation is a join the provider owns, so the extractors never depend on each other (section 4.1).

### 4.1 Binding discovery (the read capability's real method)

A capability's binding (for example `ProductService#findById`) names the real logic the runtime invokes. It is discovered from the source, read from the actual service method, not assumed from the entity name. The binding stays opaque in the IR (Volume 4, section 3.4): it names what to invoke, never how. The runtime resolves it to a real bean and method (Volume 7, section 4 and rule 3).

Two extractors collaborate, joined by the provider so that neither depends on the other:

- EntityExtractor reads each `@Entity` and produces the entity, its fields, and a read intent: the desired capability `get<Entity>`, its single id input (name and mapped type), and its entity-reference output. The read intent carries no binding.
- ServiceExtractor reads each `@Service` class and produces a neutral catalog of its public methods: the owning service's simple name, the method name, the return type's simple name, and the parameter types as written. It knows nothing about entities.
- SpringCapabilityProvider joins them: for each read intent it finds the one service method that backs it, and emits the capability with the discovered binding. The matching is the provider's coordination job, so the extractors stay independent.

The matching rule, the contract, is kept deliberately shape-based, not name-based. A service method M backs the read capability `get<Entity>` for entity E, whose id maps to primitive type `idType`, when all of these hold:

1. M is declared on a class annotated `@Service`.
2. M is `public` and not `static`.
3. M's return type, by simple name, equals E's name (M returns the entity).
4. M declares exactly one parameter, and that parameter's type maps, through the same Java-to-primitive mapping the entity fields use, to `idType` (M takes the entity's id).

The rule is expressed in types and shapes, never in the literal names `Product`, `ProductService`, or `findById`. A finder named `loadById` or `byId`, on a service named `CatalogStore` rather than `<Entity>Service`, still matches if its shape fits. A method with the same shape on a `@RestController` does not match, because rule 1 requires a `@Service`.

Outcomes:

- Exactly one method matches: the capability is emitted with binding `<ServiceClassSimpleName>#<methodName>`, taken from that method.
- No method matches: the provider fails with a located `ProviderException` that names the entity, the return type and single parameter type it sought, and the `@Service` methods it did see. It does not fall back to any convention.
- More than one method matches: the provider fails with a `ProviderException` listing the candidates. The backing method must be unambiguous; the provider will not guess.

Binding string format: unchanged. Discovery emits `<ServiceClassSimpleName>#<methodName>`, exactly the shape the runtime's `SpringExecutionAdapter` already parses (`<Bean>#<method>`, Volume 7, section 4). For the example app the discovered binding is `ProductService#findById`, identical to what the earlier convention produced; only its source changed, from assumed to read, so the adapter and the generated output are unchanged.

What is still assumed or unsupported, recorded so it is closed deliberately later. The second-app trial (docs/experiments/second-app-trial.md) measured these against a large real app and ranked them by capabilities blocked; the resulting phases are Volume 13, Tier 2:

- Parse behavior (closed by phase E1, see section 4.3). Once the top-ranked gap: the provider aborted the whole scan on the first unparseable file, the JavaParser language level was below the Java version of real apps (the trial app is Java 17), and the walk covered every file. It blocked 38 of 43 entities before extraction began. Phase E1 raised the language level to Java 17, made the survey collect-and-continue per file, and scoped the walk; the pms-backend scan now completes end to end and classifies all 43 entities (docs/experiments/second-app-trial.md, section 8.1).
- The matcher relates a service method to an entity only when the method returns the entity itself. Real layered systems return DTO and VTO types from services; the trial found this blocks essentially every single-key entity there (phase E2, section 4.2 below).
- Entity fields whose type is another entity (associations) have no mapping and fail loudly as unmapped types. Blocked four entities in the trial (phase E3).
- Only single-id-parameter reads are discovered. Lists, filtered queries, multi-argument finders, writes, and actions are not modeled; list and filter reads are the bulk of the trial app's real read API (phase E4), and write and action capabilities are future IR (Volume 4, section 7).
- The capability name is still the `get<Entity>` convention. Only the binding is discovered, not the business name of the capability.
- The entity's identifier is the first field annotated `@Id`. Composite or non-`@Id` identifiers are out of scope (three join entities in the trial; deliberately unscheduled).
- Type matching uses the closed primitive set; an id whose type is not in that set (Volume 4, section 4) cannot be matched and is surfaced as an unmapped type.
- Matching resolves the return type by simple name (condition 3). Phase Entity Identity (section 4.12.4, built 2026-07-13) supersedes this with key-based resolution, so a colliding simple name resolves to the right entity or refuses loud; the change is byte-identical when the simple name is unique. The same supersession applies to the entity, enum, and list-element type resolution in sections 4.2, 4.4, 4.5, and 4.10.


### 4.1.1 The by-primary-key body proof on the entity-returning path (phase Read-Identity; designed 2026-08-31, RATIFIED 2026-08-31)

THE DEFECT, precisely, and it is a mis-bind. Section 4.1's four conditions are signature-level in their entirety: an `@Service` class, a public non-static method, a return type that resolves to E, and one parameter that maps to E's `idType`. Nothing in them looks at the body. So the by-primary-key fact, which section 4.2.1 names "the safety-critical discriminator" and which section 4.2 condition 4 anchors on the framework contract precisely because "names cannot carry it", is never established on the entity-returning path. Section 4.2 refuses `findByUserId` and admits `selectById` only after tracing the load; section 4.1 admits either, and admits a filtered query, an element extraction, a remote call, and a nullable return along with them.

THE LIVE INSTANCE, found on a real application 2026-08-31 and the reason this section exists. In a large multi-module ERP and pharmacy inventory system (as-backend, adopted here as a fourth named app; its structural profile is 159 entities, 219 deferred reads, 6 discovered capabilities before this phase), the read capability `getCPESCustomerProduct(id: LONG)` bound to `CustomerProductAdapterServiceImpl#validateCustomerProductExistence(Long id)`. That method satisfies all four of section 4.1's conditions and does none of what its capability name promises: it treats `id` as a FOREIGN key (a product id, not the entity's primary key), builds a `CustomerProductSearchFilter`, calls `selectAllByFilters`, returns element 0 of the filtered list, and on a miss calls a remote product adapter and either returns `null` or throws. An agent calling `getCPESCustomerProduct` with a CPESCustomerProduct primary key receives a different entity instance or an exception. This is exactly the `findByUserId` trap of section 4.2.1, admitted through the path that does not check.

WHAT THIS MEANS FOR `mis-bound 0`, stated without softening. The counter reported 0 truthfully with respect to what the rules admitted, and the rules were wrong: section 4.1's contract had no by-primary-key clause for the counter to enforce. The invariant was not being measured on that path at all. It is restored by this section, which puts both paths under one proof, and not by any change to the counter.

WHY THIS IS A RATIFIED REFINEMENT AND NOT A RULE-13 DIAGNOSTIC AMENDMENT. It changes matching outcomes: methods admitted today are refused after it. It therefore takes its own ratification before any code, exactly as the read-safety receiver-awareness refinement did (section 4.15.2.1).

WHAT THE FIX CONVERGES ON, which is the argument for it. This is the same convergence argument section 4.15.2.1 made. The by-primary-key proof is not a new liberty or a new restriction invented here; it is the ratified rule everywhere else the question is asked. Section 4.2 condition 4 asks it of the view-returning body, section 4.13.3 asks it of the private-helper hop, section 4.2.10 asks it of a custom query. The entity-returning path is the fourth place the same question arises and the only one that never asks it. The refinement makes the four agree.

#### The rule

A method M that satisfies section 4.1 conditions 1 to 4 is a candidate only when its body also satisfies all of the following. Conditions 5 to 8 are section 4.2's conditions 6, 5, and 4 applied verbatim, by the same code, with the same refusals. Condition 9 is the entity-returning path's own shape, and it is what replaces section 4.2 conditions 7 to 9 (the mapping proof), because on this path there is no mapping to prove.

5. LOAD PRESENCE AND UNIQUENESS. M's body contains exactly one persistence access: exactly one call whose receiver is a field of the service whose declared type the repository catalog resolves as a repository type. Zero persistence accesses refuses (the body loads nothing, so there is nothing to prove by-primary-key). More than one refuses (a multi-read assembly is not a single provable read).
6. NO MUTATION. No call on a repository-typed field carries a write-set name (`save`, `saveAll`, `insert`, `update`, `delete`, `deleteById`, `deleteAll`, `persist`, `merge`, `flush`). Identical to section 4.2 condition 6.
7. VERBATIM ID ARGUMENT. The argument to the load is M's single parameter, by name, verbatim, not an expression over it. Identical to section 4.2 condition 5.
8. BY-PRIMARY-KEY PROOF. The load is a by-primary-key read of E, established exactly as section 4.2 condition 4 establishes it and by the same code: direct (4a) against an in-tree Spring Data repository of E whose key resolves to E by section 4.12.4 and whose id type matches, or the one-hop delegation trace (4b) through a hand-written repository interface and its single in-tree `@Repository` implementation, or the P1b provably clean custom query (section 4.2.10). Every refusal that path can produce is produced here unchanged.
9. IDENTITY SHAPE. M's body returns the loaded entity itself, untransformed, in exactly one of two forms:
   a. fused: a single `return` whose expression is the load followed only by Optional unwrapping (`orElseThrow(...)`, `orElse(...)`, `get()`);
   b. two-statement: one local declared and initialized from the load (with an optional unwrap chain), followed immediately by `return <that local>;` as a bare name, with no other statement in the body.
   Everything else refuses, and the refusal names what it saw: a mapping call, a navigation (`x.getUnit()`), an element extraction (`list.get(0)`), an `Optional.map`, a conditional, a guard, a second local, or any additional statement. A body that wants to transform what it loaded is not returning the entity the capability names, whatever its declared return type says.

The candidate pool of section 4.2 condition 10 is unchanged: 4.1 candidates and 4.2 candidates form one set, exactly one wins, zero is NO_BACKING_METHOD, more than one is AMBIGUOUS_BINDING. A method refused by conditions 5 to 9 leaves the pool and appears in the NO_BACKING_METHOD near-miss list with its located reason, by the existing machinery of section 4.2.4. No new cause code is introduced, because no new outcome exists: a read the tool cannot prove is a read it does not have.

#### What this deliberately does not do

- THE PRIVATE-HELPER HOP: see THE BUILD-FORCED CORRECTION below. The ratified text as first committed excluded it here on a premise the build falsified; it is included, under section 4.13.3's own trace.
- IT GRANTS NO DISCOVERY. Every method this section admits was already admitted by section 4.1. The amendment can only remove candidates, never add one. Any measured increase in discovery is therefore a regression finding, not a win.
- IT DOES NOT TOUCH SECTIONS 4.2, 4.10, 4.13, OR THE ATTESTATION READ-SAFETY CHECK. The view path, the list path, the discovery-gap rules, and the attestation intake are unchanged, and the golden-file output of every capability discovered through them must be byte-identical after this build.

#### The forecast (measured baselines 2026-08-31, checked after the build)

The baselines below were re-measured on the current trees immediately before this design. They differ from the health line recorded in earlier ledgers (5 / 25 / 4) because the benchmark applications have evolved since those runs; the before-and-after comparison that matters is against these same-tree baselines, not against the historical numbers.

| App | Discovered before | Of those, 4.1-path | Forecast after | Movement |
|---|---|---|---|---|
| pms-backend | 5 | 0 | 5 | none; generated output byte-identical |
| erp-backend | 35 | 2 | 35 | none; generated output byte-identical |
| as-backend | 6 | 3 | 5 | `getCPESCustomerProduct` refused |

The two erp-backend 4.1-path reads (`SyncedUserAdapterServiceImpl#getUserById`, `OrderServiceImpl#getOrderById`) and two of the three as-backend ones are the same clean shape: a single `selectById(id)` on a hand-written repository whose single `@Repository` implementation is exactly `return <jpaRepo>.findById(id);`, which is condition 8's one-hop trace (4b) and condition 9's shape (a) or (b). They survive. The third as-backend read is the mis-bind, and it refuses at condition 8: `selectAllByFilters` is not a by-primary-key read of CPESCustomerProduct. Any other movement, in either direction, on any of the three apps is a regression finding and stops the phase.

SECTION 4.1'S OWN "STILL ASSUMED OR UNSUPPORTED" LIST is amended by this section: its first entry is no longer true of the entity-returning path, which now reads the body it binds to.

#### THE BUILD-FORCED CORRECTION (2026-08-31, during the build session, recorded here rather than left to diverge)

WHAT CHANGED. The ratified text above first said: NO PRIVATE-HELPER HOP, an entity-returning read whose load sits one private same-class hop away refuses at condition 5, justified as "a deliberate coverage miss: no benchmark presents the shape". The build falsified that premise on its first full test run. Fixture DG-3b (`dg-positive/helper/BarService`, committed by the Discovery-Gap phase on 2026-07-13) presents exactly the shape, and its own file comment states its purpose: "An ENTITY-returning read reached through a private helper. The public method returns the entity itself, so section 4.1 discovers it on its signature alone; this fixture is the regression guard that the helper hop never disturbs the entity-returning path." Excluding the hop turned that guard red and would have dropped `getBar`, a read whose load is a clean, provable, already-ratified by-primary-key load.

THE CORRECTION. The section 4.13.3 trace runs on the entity-returning path as well, under exactly the same conditions 1 to 3 (a unique same-class call taking the id verbatim, pinned to exactly one private declaration, whose body is the clean by-primary-key load and nothing else). Only its final step differs, and it differs the same way everything else on this path differs: where the view path requires the helper's value to feed one resolvable mapping call, this path requires it to be returned untransformed, condition 9. Fixture RID-3 pins the corrected behavior.

WHY THIS IS THE RIGHT CORRECTION AND NOT A WIDENING. Refusing the hop here while accepting it on the view path would have re-created, inside this very section, the inconsistency the section exists to remove: the same load, proven by the same trace, admitted through one return type and refused through another. The convergence argument above is the argument for including it. The rule still cannot admit a method section 4.1 admitted nothing about, because the helper's own body is held to the same by-primary-key proof.

WHY IT IS RECORDED HERE INSTEAD OF SILENTLY BUILT. Rule 13 permits a mid-build amendment that changes no matching outcome. This one does change a matching outcome, so it does not qualify, and the alternative rule (stop the phase) would have left a half-built tree over a premise that was simply a factual error about the fixture set. The design is corrected in the volume, in the same commit as the code that implements it, so the ratified text and the built behavior do not diverge. What this costs is honesty about the ratification: the forecast table above was computed under the excluding rule, and it happens to be unaffected, because none of the five 4.1-path reads on the three benchmark applications uses a private helper. The correction moves fixture coverage, not benchmark coverage.
### 4.2 DTO-aware read discovery (designed 2026-07-02; RATIFIED 2026-07-02 as written)

The trial's second-ranked gap is that real service boundaries return view types, so a rule is needed for when a DTO- or VTO-returning service method backs a read of an entity. This section is that rule, designed from a read-only study of the benchmark app before any code, exactly as the binding rule in section 4.1 was: the rule enters this volume in one commit, the code follows in a later session, only after this rule is ratified. Until ratification, no matching code may be written.

#### 4.2.1 Evidence (read-only study of pms-backend, 2026-07-02)

Every by-id read visible in the benchmark's parseable source was traced from the service method down to the persistence call. Census of the six single-id, view-returning reads found in parsed `@Service` classes:

| Read | Body shape | Load chain terminal | Pattern |
|---|---|---|---|
| TenantServiceImpl#findById | load, unwrap, one mapper call | custom `@Query` (`findByIdWithRelations`, JPQL `WHERE t.id = :id`) | P1b |
| UnitServiceImpl#findById | load, unwrap, one mapper call | framework `findById` via one-hop delegation | P1a |
| LeaseServiceImpl#findById | load, unwrap, one mapper call | framework `findById` via one-hop delegation | P1a |
| BuildingServiceImpl#findById | load, map, second repository access, view mutation | framework `findById` | P2 |
| PropertyServiceImpl#findById | load, map, then loads buildings and units into the view | framework `findById` | P2 |
| ChatRoomServiceImpl#getChatRoomById | load, session-context read, authorization check, two more loads, multi-argument mapping | framework `findById` | P2 |

The recurring structural facts, and what they mean for the rule:

- The service layer never calls Spring Data directly. Every service depends on a hand-written repository interface (for example `TenantRepository`, a plain interface, not a Spring Data type). Its `@Repository` implementation delegates to a Spring Data interface (`TenantJpaRepository extends JpaRepository<Tenant, Long>`) one hop down. In four of five sampled implementations, the by-id method's body is exactly one statement, `return <jpaRepo>.findById(<param>);`, the framework's by-primary-key read. In one (Tenant), the terminal is a custom `@Query` whose by-primary-key fact lives inside a JPQL string.
- The mapping step is one call with the loaded entity as its sole argument: a MapStruct mapper declared in-tree (`public abstract TenantVTO toTenantVTO(Tenant tenant)` on `@Mapper PropertyMgtMapper`). The mapper declaration ties view to entity by declared parameter type, in code, with no name inspection.
- The by-primary-key fact is the safety-critical discriminator, and names cannot carry it. `TenantRepository` declares `Optional<Tenant> selectById(Long id)` and `Tenant findByUserId(Long userId)` side by side: identical Java shapes, one loads by primary key, one by a foreign key. Only the framework contract (`findById` inherited from a Spring Data repository of E) or the JPQL text distinguishes them. The rule therefore anchors on the framework contract and refuses the JPQL case.
- Live trap cases that a shape-only rule would mis-bind, all present in parsed source: `LeaseServiceImpl#getMyActiveLease(Long tenantId)` (filter query by tenant, first element, mapped; one Long parameter, returns LeaseVTO); `TenantServiceImpl#getMyUnit(Long userId)` (loads Tenant by user id, returns a Unit view off a navigation, `tenant.getUnit()`); `UserMgtProxyInternalServiceImpl#findById(Long)` (pure delegation, no load in the body, delegate unparseable); `resetPassword(Long)` returning String (a write with a read's signature); `FileIdGeneratorServiceImpl#generate(Integer domainId)` (one parameter of Domain's id type, no load of Domain); `DailyCounterServiceImpl#getNewCount()` (a mutating counter behind a read-sounding name); `UserDetailsSecurityService#loadUserByUsername(String)` (loads by email, second repository access, mutates the view). The rule's conditions refuse every one of these structurally.
- Composite-key hazard, exposed by tracing E1's outcomes: `EventAttendee` and `GroupMember` use `@IdClass` with two `@Id Long` fields. Section 4.1's "first `@Id` field" assumption formed a half-key read intent (`getEventAttendee(eventId)`). Nothing backed it, so no harm occurred, but if a matching one-Long method had existed, a composite-key entity's read could have bound to half its key. The eligibility rule below closes this.

Reachability of the 19 NO_BACKING_METHOD entities (why the benchmark number cannot move from this rule alone): 15 have their entire service layer among the 27 unparseable placeholder files (admin 3, billing 3, PPFile 1, inspection 1, maintenance 2, notification 2, user 3); 2 are the composite-key join entities (also unparseable services); 1 has an empty parsed service (AuditLog, fields only, no methods); 1 has only a mutating counter method (DailyCounter). None has any single-id-parameter read in a parsed `@Service`. The six reads in the census all belong to entities currently blocked at extraction (UNMAPPED_FIELD_TYPE, phase E3's causes: associations and enums). The consequences are stated in 4.2.9.

#### 4.2.2 The rule

A service method M backs the read capability `get<E>` for entity E, whose identifier maps to primitive type `idType`, when ALL of the following hold. Conditions 1 and 2 restate section 4.1's frame; the rest are new.

0. Eligibility (tightened): E has exactly one field annotated `@Id` and no `@EmbeddedId`. An entity with more than one `@Id` field or an `@EmbeddedId` forms no read intent at all; it is reported with a composite-key cause, never read by part of its key.
1. M is declared on a class annotated `@Service`, is `public`, and is not `static`.
2. M declares exactly one parameter, and that parameter's type maps to `idType`.
3. M's declared return type is not `void`. (If its simple name equals E's name, M is a section 4.1 candidate; this rule covers the case where it does not.)
4. M's body contains exactly one persistence access, the load, and it is one of:
   a. a call to one of the framework by-primary-key reads, `findById`, `getById`, or `getReferenceById`, on a field of the service whose declared type resolves in-tree to a Spring Data repository of E: an interface extending one of the recognized supertypes (`JpaRepository`, `CrudRepository`, `ListCrudRepository`, `PagingAndSortingRepository`, `ListPagingAndSortingRepository`) whose first type argument is E and whose second maps to `idType`; or
   b. a call to a method M2 on a field whose declared type resolves in-tree to a hand-written repository interface, where M2 is declared to return E or `Optional<E>` and take one parameter of `idType`, and the interface's single in-tree `@Repository` implementation implements M2 with a body that is exactly one return statement performing case (a) with the parameter forwarded verbatim (the one-hop delegation trace).
5. The argument to the load (and, in 4b, the argument forwarded by the delegation) is M's single parameter, verbatim: the parameter name itself, not an expression over it.
6. No other repository access appears anywhere in M's body: no call on any other repository-typed field, and no call anywhere whose target is a repository-typed field and whose name is a mutation (`save`, `saveAll`, `insert`, `update`, `delete`, `deleteById`, `deleteAll`, `persist`, `merge`, `flush`).
7. Apart from the load, M's body is exactly one of the two read shapes:
   a. fused: a single `return` whose expression is the load followed only by Optional unwrapping (`orElseThrow(...)`, `orElse(...)`, `get()`) and at most one `Optional.map(<mapping step>)`; or
   b. two-statement: one local variable declared and initialized from the load (with optional unwrap chain), followed immediately by `return <mapping step>(<that local>);`, with no other statements in the body.
8. The mapping step, when present, is a single call whose sole argument is the loaded entity value itself: an instance method on a field of the service, a static factory, a constructor, or a method reference inside `Optional.map`. A navigation (`x.getUnit()`), an element extraction (`list.get(0)`), or a multi-argument call disqualifies. When there is no mapping step (the fused form returns the unwrapped load), M is a section 4.1 candidate by construction.
9. The mapping step's callee resolves in-tree to a declaration whose single parameter's type is E by simple name and whose return type equals M's declared return type. An unresolvable callee refuses: the evidence is incomplete, and incomplete evidence is a refusal, not a guess. This resolution is the rule's type check; it is what makes the view's derivation from E a stated fact of the code rather than an inference.
10. Pooling and uniqueness: all methods matching section 4.1 (entity-returning) or this rule (view-returning) for entity E form one candidate set, with no precedence between the two kinds. Exactly one candidate: the capability is emitted with binding `<ServiceClassSimpleName>#<methodName>`. Zero: NO_BACKING_METHOD, with the near-miss diagnostics of 4.2.4. More than one: AMBIGUOUS_BINDING, listing all candidates. The provider never picks.

#### 4.2.3 The explanation

Every match must be explainable, and the explanation is part of the scan report's detail for the discovered entity. It contains, in code terms: the binding (service and method); the load evidence (the repository field and its declared type; whether the chain is direct or one-hop, and for one-hop, the interface method and the implementing class; the terminal framework method; and the Spring Data declaration cited as `<Interface> extends <Supertype><E, idType>`); the mapping evidence (the callee and its in-tree declaration, `<Mapper>#<method>(<E>) : <View>`); and the view type's simple name. Name resemblance between the view and the entity is never part of the evidence and never appears as a reason.

#### 4.2.4 Refusals and diagnostics

- Zero candidates stays NO_BACKING_METHOD with a located message, extended two ways. Near-misses: any method that passed conditions 1 to 3 but failed a later condition is named with the first condition it failed, in the rule's terms (for example: "load resolves through TenantRepository#selectById, whose implementation delegates to TenantJpaRepository#findByIdWithRelations, a custom query; by-primary-key evidence unavailable, deferred pattern P1b"). Skipped-file context: when any `@Service` or `@Repository` file under the scanned source was a parse skip, the message states how many, so an absence of candidates is never mistaken for an absence of code.
- More than one candidate stays AMBIGUOUS_BINDING, listing every candidate with its kind (entity-returning or view-returning).
- Composite-key entities are refused at eligibility (condition 0) with their own cause, distinct from "no identifier", naming the `@Id` fields or the `@EmbeddedId` seen.

#### 4.2.5 What the IR says about the output

Decision: the capability's output remains `ENTITY_REF(E)`, and the capability carries an annotation, name `view`, whose value is the returned view type's simple name.

Why: semantically the capability reads E; the rule's whole burden is proving, structurally, that the returned view is derived from a by-primary-key load of E in one step. The IR describes what a capability means, not its wire schema, and `ENTITY_REF(E)` with the view noted is the smallest representation that does not lie: an agent reading the manifest learns it is reading E and learns the concrete shape has a name of its own. The alternative, a first-class view or projection concept carrying the view's fields, is deliberate IR growth under Volume 4, section 7: it enters through its own designed step with validators and golden tests if and when evidence shows agents need field-level view schemas. It is explicitly not smuggled in here. The `Annotation` hook exists precisely for hints that must not grow the model prematurely (Volume 4, section 3.6).

#### 4.2.6 Covered and deferred patterns

Covered by this rule (from the census):

- P1a, minimal load-then-map with a framework by-primary-key terminal, direct or one-hop (Unit, Lease; 2 of the 6 census reads).

Deferred, each keeping a loud refusal with a named diagnostic:

- P1b, minimal shape but the load terminal is a custom `@Query` (Tenant; 1 of 6). Closing it means reading JPQL where-clauses or accepting a user-supplied assertion that a repository method is the by-primary-key read. Both are decisions for a later phase; neither is guessed now.
- P2, composite view assembly: the correct by-id load followed by further loads and view enrichment (Building, Property, ChatRoom; 3 of 6). These are real reads, but their result is assembled from more than the entity; expressing them honestly needs more than a one-step derivation and is deferred.
- Delegation-only facades (the proxy services): the evidence lives in the delegate, and tracing service-to-service chains is out of scope (in the benchmark the delegates are unparseable anyway).
- Non-primary-key single-argument reads (`getMyActiveLease`, `getMyUnit`, `findByUserId` shapes): permanently out of scope for `get<E>` by design. They are different capabilities (future named reads), not failed matches of this one.
- List, filter, and aggregate reads: phase E4 and later (Volume 4, section 7 discipline).

#### 4.2.7 What this rule will never do

- Never match on name similarity between a view and an entity (`TenantVTO` ~ `Tenant`), not even as a tiebreaker; names of views, services, and repository methods on hand-written interfaces carry no evidence weight. The only names with weight are framework contracts: the annotations already trusted (`@Entity`, `@Id`, `@Service`, `@Repository`), the recognized Spring Data supertypes, and the framework's own by-primary-key methods, whose semantics are defined by the framework, not by the application's style.
- Never score field overlap between a view and an entity.
- Never fall back: zero evidence is a refusal, partial evidence is a refusal, unresolvable evidence is a refusal.
- Never accept a non-verbatim argument flow: an expression, navigation, or element extraction is never "the id" or "the entity".
- Never bind a method whose body contains any mutation access or any second load.
- Never read by part of a composite key.

#### 4.2.8 Pre-registered fixtures (the build session must include these before the code exists)

Positives, one per covered form:

- F1: direct Spring Data load, fused form: `return mapper.toVto(repo.findById(id).orElseThrow(...));` with the repository declared `extends JpaRepository<E, Long>` in-tree.
- F2: one-hop delegation (the Unit and Lease shape): hand-written interface plus `@Repository` implementation whose body is `return jpaRepo.findById(id);`, service in the two-statement form.
- F3: `Optional.map` with a method reference: `return repo.findById(id).map(mapper::toVto).orElseThrow(...);`.

Refusals, each asserting the located diagnostic:

- F4: name-similar but structurally unrelated: a `TenantVTO`-returning method whose body loads via Building's repository (or performs no load). Must not match `getTenant`; this is the mandated proof that names carry nothing.
- F5: non-primary-key load: `findByUserId(Long)` declared on the Spring Data interface (a derived non-pk query), wrapped in an otherwise perfect body. Must refuse.
- F6: composite assembly (Building shape): second repository access and view mutation. Must refuse.
- F7: write in disguise: framework `findById` load, then mutation and `save`, then a mapped return. Must refuse on the mutation access.
- F8: custom-query terminal (Tenant shape): one-hop delegation ending in `@Query findByIdWithRelations`. Must refuse with the P1b near-miss naming the chain.
- F9: navigation argument (`getMyUnit` shape): `return mapper.toUnitVTO(tenant.getUnit());`. Must refuse.
- F10: delegation-only facade (proxy shape): no load in the body. Must refuse.
- F11: ambiguity across kinds: one entity-returning and one view-returning candidate for the same entity. Must refuse as AMBIGUOUS_BINDING listing both.
- F12: composite-key entity (`@IdClass`, two `@Id` fields): no read intent formed; the composite-key cause is reported. This is the eligibility tightening's test.

#### 4.2.9 Benchmark prediction and the sequencing finding

Stated before the build, so the build is measured against a forecast rather than a moving target:

- After the E2 build alone: discovered 0, mis-bound 0. Unchanged totals, because none of the 19 NO_BACKING_METHOD entities has any single-id read in a parseable service (4.2.1). What changes is honesty: NO_BACKING_METHOD messages gain near-miss and skipped-file context, and the two composite-key entities move from NO_BACKING_METHOD to the composite-key cause (predicted causes: unmapped field type 24, no backing method 17, composite key 2).
- After E2 and E3 together (E3 clearing the census entities' field types, associations and enums): Unit and Lease flip to DISCOVERED through P1a, each with exactly one candidate (the census found no competing methods). Predicted discovered: up to 2, contingent on E3 clearing every field of those entities, with mis-bound exactly 0. Tenant stays refused with the named P1b near-miss; Building, Property, and ChatRoom stay refused as P2.
- The hard bar is unchanged and absolute: mis-bound must be exactly 0 on every run. One mis-binding stops all phase work.

The sequencing finding, plainly: phase E2's exit criterion as written in Volume 13 ("discovered greater than zero on pms-backend") is not achievable by the E2 build alone on this benchmark. The first rise in discovered requires E2 and E3 together.

Ratification (2026-07-02): this rule is ratified as written, conditions 0 through 10, the ENTITY_REF-plus-view-annotation representation, fixtures F1 to F12, and the deferred patterns. Sequencing was decided: E3 is designed and built before the E2 build, and E2 plus E3 are measured jointly against the benchmark with the shared exit criterion, discovered greater than zero (forecast: Unit and Lease via P1a) and mis-bound exactly zero. The E2 build session follows the E3 build.

#### 4.2.10 P1b: the custom-query by-primary-key terminal (designed 2026-07-02; RATIFIED 2026-07-02 as the strict by-primary-key boundary; BUILT 2026-07-24 as Attestation sub-phase AT-0)

This is the deferred half of condition 4 (patterns 4.2.6). Today the load evidence must be a framework by-primary-key read (`findById`, `getById`, `getReferenceById`) on a Spring Data repository of E, reached directly (4a) or as the single delegate of the one-hop hand-written trace (4b). P1b answers the deferred question: when may a custom `@Query` method serve as that same load evidence? The bar is unchanged: structural proof of the by-primary-key claim, or refusal. Names carry no weight; the JPQL text is read, never the method name.

The design-closure of 2026-07-02 tightened this rule and re-examined its one apparent discovery, and it changed the outcome. The first design draft (also 2026-07-02) permitted any `JOIN FETCH` clause, including an inner one, on the assumption that a fetch join "never removes rows." That assumption is false for an inner `JOIN FETCH`: an inner join is an existence filter, and it drops the root entity when the fetched association is absent. Applying the corrected, strict rule to the only clean-looking discovery, pms Tenant, flips it from discovered to refused (section 4.2.10.1). The decisive finding follows: on both benchmarks there is NO custom query that is both provably nothing-but-a-by-primary-key load and feeding a discoverable read. P1b therefore discovers zero, and its ratified value is the strict refusal boundary it draws, inherited by E6 as a floor (section 4.2.10.6). Refusal is not the primary path of this rule; on this evidence it is the total path.

##### 4.2.10.1 Evidence and the decisive finding (read-only, both apps)

Every custom-query by-id read was traced to source. The full census is in the experiment ledgers (docs/experiments/second-app-trial.md, section 8.8; docs/experiments/third-app-trial.md, section 14). Six by-primary-key-shaped custom `@Query` methods exist across the two apps. Condition 4 (and therefore P1b) is consulted only when such a query is the load evidence of a view-returning service read, which is true for exactly two of the six; the other four feed a service `findById` that returns the entity, so they are section 4.1 discoveries whose bodies 4.1 never reads (P1b never touches them, verified on erp Product, Contact, ContactGroup, and Ticket: `TicketServiceImpl#findById` returns Ticket, so its custom-query body is irrelevant to its existing 4.1 discovery). The two that matter, each applied against the strict rule below:

- pms Tenant, `TenantServiceImpl#findById -> TenantVTO`, one-hop through `TenantRepository#selectById` to the `@Query` method. Its text, verbatim from source: `"SELECT t FROM Tenant t JOIN FETCH t.user LEFT JOIN FETCH t.unit un LEFT JOIN FETCH un.building b LEFT JOIN FETCH b.property WHERE t.id = :id"`. The WHERE is a single equality between the id property and the method's parameter, and PASSES P5. But the first fetch, `JOIN FETCH t.user`, is an INNER join fetch, and `Tenant.user` is a nullable `@ManyToOne` (declared `@ManyToOne @JoinColumn(name = "user_id")`, with no `optional = false` and no `nullable = false`, so nullable by JPA default). An inner join fetch on a nullable association drops the Tenant when its user is null: this query returns nothing for a Tenant that exists but has no user, so it is not equivalent to a by-primary-key load. It carries a hidden existence filter, and under the strict rule (P4 below) it FAILS. Verdict: FAIL. getTenant is not discovered.
- erp Delivery, `DeliveryServiceImpl#findById -> DeliveryReadDTO`, direct on the Spring Data repository. Its text: `"SELECT d FROM Delivery d LEFT JOIN FETCH d.truck LEFT JOIN FETCH d.driver LEFT JOIN FETCH d.transaction t LEFT JOIN FETCH t.customer LEFT JOIN FETCH t.supplier WHERE d.id = :id"`. Every fetch is a LEFT JOIN FETCH and the WHERE is the single id equality, so this query PASSES the strict rule: it is a genuine by-primary-key load. But Delivery does not discover, for a reason unrelated to P1b: `DeliveryServiceImpl#findById` maps via `mapToDTO(delivery)`, an implicit-this call with no receiver, which refuses at condition 9 (the mapper does not resolve to a field, a static factory, a constructor, or a method reference). Verdict on the query: PASS; on the read: refused at the mapper.

So the two clean-looking candidates fail for two different reasons: Tenant's query filters (inner fetch on a nullable association), and Delivery's query is a clean by-primary-key load whose consumer is blocked by an unrelated mapper rule. There is no case in either benchmark where a strict by-primary-key custom query feeds a discoverable view read. The blunt version of the finding, corrected from the first draft: the by-primary-key custom queries in the wild are not as clean as they look; the moment you read the join semantics rather than the WHERE alone, the one that seemed pure carries a filter. P1b discovers zero.

The contrast with the openly filtered custom queries is structurally clear and not a matter of naming: erp `DeliveryRepository#searchByFields`, `ProductRepository#findProducts`, `EmailTemplateRepository#searchByFields` are multi-parameter with `AND`/`OR`/`LIKE` predicates, refused already by condition 2 and by P5.

##### 4.2.10.2 The ratified rule: a custom `@Query` is a by-primary-key load only when provably nothing but one

Condition 4's terminal set is amended. The load in 4a (the direct call), and the single delegate call in the 4b one-hop implementation body, may serve as the by-primary-key load evidence when, in addition to a framework read, they call a repository method annotated `@Query` that is a VERIFIED by-primary-key select of E. Nothing else in rule 4.2 changes: conditions 5 to 9 (the verbatim argument, the single-access body, the two read shapes, the one resolvable mapping step) still apply to the service method exactly as written.

A `@Query` method is a verified by-primary-key select of E when ALL of the following hold. The rule is strict on purpose: it accepts only a query it can prove is nothing but a by-primary-key load.

- P1. It is declared on a Spring Data repository of E (an interface extending a recognized supertype whose first type argument is E), reached directly or as the 4b one-hop delegate terminal. P1b adds no new repository resolution; it reuses 4a and 4b's scoping exactly.
- P2. It carries a `@Query` annotation with a literal string value, and that value is JPQL. A `@Query(nativeQuery = true)` does not qualify: native SQL references tables and columns the tool has not mapped to E and its identifier, so the structural link to E is unprovable. A `@Query` referencing a named query by name only (its text defined elsewhere, for example a `@NamedQuery` or an XML mapping), or a repository method with no `@Query` at all, does not qualify: the query text must be present and readable in the annotation. Unlocatable text is a refusal, never an assumption.
- P3. The JPQL, read as a narrow token template (not interpreted), is a single-entity select of the root: `SELECT [DISTINCT] <alias> FROM <E> <alias> <fetch clauses> WHERE <predicate>`, where `<alias>` is the sole select item and is exactly the FROM alias, and `<E>` by simple name is E. A projection (`SELECT new <Dto>(...)`, `SELECT <alias>.<field>`, `SELECT COUNT(...)`, or more than one select item) does not qualify: its result is not E, so it is not a load of E.
- P4 (corrected in the design-closure, the pivotal condition). Every clause between FROM and WHERE is a `LEFT JOIN FETCH`. A LEFT (outer) join fetch is a pure eager-load directive: it adds related data to the returned E and never removes a root row, so it does not change the by-primary-key claim. An inner `JOIN FETCH` (a fetch join written without `LEFT`) does NOT qualify: it is an existence filter that drops the root E when the fetched association is absent, so it can return nothing for an E that exists by its id. The tool refuses it structurally, from the query text alone, without reasoning about whether the association happens to be mandatory; that a particular association is non-nullable is a fact the query text does not carry, and the rule does not go looking for it. A plain `JOIN` carrying an `ON` or `WITH` condition, or any join that is not a LEFT fetch, likewise does not qualify.
- P5. The WHERE clause is EXACTLY one equality between E's identifier and the method's single parameter: one operand is `<alias>.<idField>`, where `<idField>` is E's `@Id` field name (the actual identifier field the entity declares, not the literal token "id" assumed), and the other operand is the method's single parameter, referenced as a named parameter (`:name`, bound by `@Param("name")` or the parameter's own name) or the positional `?1`. There is no other predicate: no `AND`, no `OR`, no second comparison, no function applied to the identifier or the parameter.
- P6. The parameter binding is verbatim, extending condition 5's discipline to the query: the WHERE's parameter reference resolves to the method's single parameter (condition 2's id parameter). A WHERE that binds a literal, or references a parameter the method does not declare, does not qualify.

The tool reads a bounded JPQL subset: it locates the `SELECT`, `FROM`, `LEFT JOIN FETCH`, and `WHERE` tokens, the alias, the join kinds, and the single equality; it does not evaluate expressions, resolve JPQL functions, or model the query language. Any query that does not fit the template refuses. This keeps the tool a structural matcher, not a JPQL interpreter (section 4.2.10.4).

##### 4.2.10.3 The honesty argument, and the two hidden-filter refusals stated verbatim

The rule's whole job is to draw the line between a by-primary-key load and a filtered read, because binding a filtered read as `get<E>` would tell an agent, in the tool description it acts on, that the capability reads E by its id when it actually reads only the rows that also pass a hidden condition. That is a filtered read wearing a by-id read's clothes, and emitting it would be the project's first semantic lie. A filter can hide in two places, and both are refused:

> Filtered-WHERE refusal (P1b, verbatim): a `@Query` whose WHERE clause is anything other than the single equality of P5, in particular any WHERE containing a further predicate joined by `AND` or `OR` (for example `WHERE e.id = :id AND e.status = 'ACTIVE'`), is NOT a by-primary-key load. It is refused with a located message, "custom query on <Repo>#<method> filters beyond the primary key (WHERE has more than the id equality); it is a filtered read, not a by-id load, and is deferred, not bound as get<E> (Volume 5 section 4.2.10, P5)."

> Inner-fetch refusal (P1b, verbatim): a `@Query` containing an inner `JOIN FETCH` (a fetch join written without `LEFT`) is NOT a by-primary-key load, because the inner join drops the root entity when the fetched association is null, so the query can return nothing for an entity that exists by its id. It is refused with a located message, "custom query on <Repo>#<method> has an inner JOIN FETCH on <assoc>, an existence filter that excludes rows whose association is absent; it is not a by-id load and is deferred, not bound as get<E> (Volume 5 section 4.2.10, P4)."

The counterpoint, argued structurally: a LEFT JOIN FETCH is the one join the rule admits, because it is provably non-filtering from the query text alone. It keeps every root E row and only widens what is eagerly loaded onto it, so the row set is still "E with that id, or nothing." No predicate, and no inner join, is admitted; the id equality of P5 and the LEFT fetches of P4 are the entire permitted query.

##### 4.2.10.4 What P1b will never do

- Never read query semantics beyond the by-primary-key claim: it verifies the SELECT is of E, the joins are LEFT fetches, and the WHERE is exactly the id equality, and nothing more. It does not evaluate, optimize, or interpret the query.
- Never treat an inner join, or any predicate beyond the id equality, as non-filtering: a construct that can drop a matching-id row is a filter, and a filter is a refusal.
- Never accept native SQL (`nativeQuery = true`): the structural tie to E and its `@Id` is through JPQL entity and field names, which native SQL does not use.
- Never accept a query it cannot locate as literal text (a named or externally-defined query): unlocatable evidence is a refusal.
- Never infer from a method name: `findByIdWithRelations` is trusted for its read JPQL, not its name; and being named for the id does not save it when its join semantics filter, which is exactly why Tenant refuses.
- Never relax conditions 5 to 9: the verbatim argument, the single-access body, and the one resolvable mapping step are unchanged. P1b only widens what counts as the load terminal, and only to the strict by-primary-key select.

##### 4.2.10.5 Outcomes per affected entity, and the pool

P1b terminals join the 4.2 candidate pool with no precedence (condition 10). The strict rule discovers no entity on either benchmark:

- pms Tenant: REFUSED. Its `@Query` (`findByIdWithRelations`) fails P4 on the inner `JOIN FETCH t.user` (nullable association, an existence filter), so it is not verified by-primary-key evidence and getTenant is not emitted. Tenant stays NO_BACKING_METHOD, and its near-miss message becomes the inner-fetch refusal (section 4.2.10.3) instead of the generic "custom query, deferred" it carries today.
- erp Delivery: REFUSED, on the mapper, unchanged by P1b. Its `@Query` passes the strict rule (all LEFT fetches), but `mapToDTO(delivery)` is an implicit-this call that refuses at condition 9. The load is no longer the reason; the mapper is.
- erp Quote: UNCHANGED, stays AMBIGUOUS. `getInvoice` loads via the framework `findById` (a P1a match today, competing with the entity-returning `findById` as getQuote), so it never depended on P1b.
- erp Product, Contact, Ticket, ContactGroup: UNCHANGED. Each service `findById` returns the entity and is a section 4.1 discovery whose body 4.1 never reads. P1b does not evaluate their custom-query loads.

##### 4.2.10.6 Re-scope: P1b is E6's ratified lower boundary

P1b is the narrow, strict, by-primary-key-only boundary; it is not folded into E6, and E6 is not folded into it. E6 (the filter-semantics problem) inherits the majority case this census surfaced: custom queries that do MORE than a by-primary-key load, whether by a WHERE predicate, an inner-fetch existence filter, a projection, or a returned collection. Designing how to represent those honestly is precisely E6's job.

The ratified relationship, recorded so E6's design session inherits it: P1b's strict rule is E6's LOWER BOUNDARY. E6 designs upward from the floor P1b sets, and it may never present a query that does more than a by-primary-key load as an unfiltered by-id read. P1b's two refusals (the filtered WHERE and the inner-fetch filter) are the floor; whatever E6 builds to represent filtered and projected reads, it builds on top of that floor and never beneath it. A query that is exactly a by-primary-key load is a get<E> (P1b's domain); a query that is anything more is E6's, and it is labelled for what it is, never as a plain get.

##### 4.2.10.7 Build recommendation: DEFER the standalone P1b build

Recommended, and recorded: the standalone P1b build is DEFERRED. It discovers zero entities on both benchmarks, so a session that implements it would move no benchmark number; its whole value is the strict refusal boundary, and that value is captured by ratifying the rule here and carrying it into E6 as the floor (section 4.2.10.6). Building a JPQL token matcher to discover nothing is not worth a session.

The exact trigger for building it later, so the deferral is not open-ended: P1b is built when either (a) E6 is built, since E6 must implement the same by-primary-key floor to know what it is building above, so P1b's matcher lands as part of E6's load-classification; or (b) a future benchmark app presents a custom `@Query` that passes the strict rule (all LEFT fetches, single id-equality WHERE) AND feeds a resolvable view read, which would make P1b discover a real capability and earn its own build. Until one of those triggers, P1b stands as DESIGNED and RATIFIED, BUILD DEFERRED.

##### 4.2.10.8 Pre-registered fixtures (for the eventual build, whenever it lands)

Positives:

- P1b-1: direct Spring Data, clean by-pk `@Query`: a view-returning service method whose body is `V m = repo.findByIdX(id).orElseThrow(...); return mapper.toV(m);`, where `repo` is `extends JpaRepository<E, Id>` and `findByIdX` carries `@Query("SELECT e FROM E e WHERE e.id = :id")`. Discovers.
- P1b-2: one-hop hand-written to a `@Query` terminal (the Tenant chain shape, but with a clean query): a hand-written interface whose single `@Repository` implementation delegates in one return statement to a Spring Data `@Query` by-pk method whose fetches are all LEFT. Discovers.
- P1b-3: LEFT JOIN FETCH positive: the same as P1b-1 but the `@Query` is `"SELECT e FROM E e LEFT JOIN FETCH e.child WHERE e.id = :id"`. Discovers (a LEFT fetch does not change the claim).

Refusals, each asserting the located diagnostic:

- P1b-4 (the filtered-WHERE guard, the most important positive-refusal fixture): `"SELECT e FROM E e WHERE e.id = :id AND e.status = 'ACTIVE'"`, single id parameter. Must REFUSE with the verbatim P5 message; getE is not emitted.
- P1b-5 (the inner-fetch guard, the Tenant shape): `"SELECT e FROM E e JOIN FETCH e.other WHERE e.id = :id"`, an inner fetch. Must REFUSE with the verbatim P4 inner-fetch message. This is the fixture that encodes the design-closure correction, and it is the reason Tenant refuses.
- P1b-6: multi-parameter refusal: a `@Query` by-pk-looking method with a second parameter beyond the id. Refuses (condition 2, restated for the query).
- P1b-7: native query refusal: `@Query(value = "SELECT * FROM e_table WHERE id = :id", nativeQuery = true)`. Refuses on P2.
- P1b-8: unlocatable query refusal: a repository method whose `@Query` references a named query by name (no literal JPQL). Refuses on P2.
- P1b-9: projection refusal: `@Query("SELECT new com.x.EDto(e.id, e.name) FROM E e WHERE e.id = :id")`. Refuses on P3 (the result is not E).
- P1b-10 (pooling and mis-bound guard): an entity with both a framework-loaded view read and a clean P1b-loaded view read refuses as AMBIGUOUS_BINDING listing both; and the P1b-1 entity's discovered binding is exactly the verified method, output ENTITY_REF(E) with the view annotation, never a sibling entity's query.

##### 4.2.10.9 Forecast (build deferred; a target for the eventual build)

Even deferred, the build target is recorded number by number (ledgers: pms docs/experiments/second-app-trial.md section 8.8; erp docs/experiments/third-app-trial.md section 14). Under the ratified strict rule:

- pms: discovered 2, UNCHANGED. Tenant does not flip (its `@Query` fails P4 on the inner fetch), so getUnit and getLease remain the only discoveries. Its NO_BACKING_METHOD near-miss message changes from the generic custom-query note to the inner-fetch refusal; the counts do not move. Failed 41 (NO_BACKING_METHOD 38, COMPOSITE_KEY 3), skipped-by-parse 27, mis-bound 0. All byte-identical to the current row.
- erp: discovered 25, UNCHANGED. Delivery stays refused on its mapper, Quote stays AMBIGUOUS, the four entity-returning 4.1 reads are untouched; Delivery's near-miss message shifts from the load terminal to the mapper. Failed 9, mis-bound 0. Byte-identical to the current row.
- Both apps: discovered delta zero, every existing discovered row byte-identical, mis-bound 0. The mis-bound bar is not merely met but never at risk, since the rule emits nothing.

This is the honest close: P1b's strict rule refuses everything it surveyed, which is the correct behavior, and its ratified purpose is to be the by-primary-key floor E6 builds on, not a source of discovery.

### 4.3 Scan resilience: two entry points, the language level, collect-and-continue, and the walk rule (phase E1)

Reading a large, messy, real app is not the same job as producing an artifact from a clean one, so the provider has two entry points, and they behave differently on purpose:

- `scan` is the resilient survey. It walks the whole source, parses collect-and-continue, and returns the discovered IR together with a per-entity outcome for everything it could parse and a named, located skip for every file it could not. It aborts only when it cannot run at all: the source root is missing, is not a directory, or cannot be walked. This is what `tibyan scan` runs, and what the pms-backend benchmark measures.
- `provide` is the strict plugin-api contract used to produce an artifact. It runs the same survey and then refuses the whole scan if any file could not be parsed or any entity failed for a hard cause, returning a clean IR or throwing. This is what the build and `tibyan generate` run: you do not ship a manifest that silently missed a tool. An entity with no single `@Id` is tolerated (it yields no capability but is not an error), matching the read-by-id scope.

Both share one discovery rule (section 4.1). The split changes only whether a located failure is recorded and the survey continues, or aborts the whole run. Neither ever guesses a binding.

Three properties make the survey survive real source (phase E1):

1. Language level. The parser is configured at Java 17, the language level of the target apps (Volume 16, section 3.1). Parsing below the source's level rejects legitimate modern syntax (switch expressions, `instanceof` type patterns, records, text blocks) as if it were malformed. This alone removes the trial's f1 failures.
2. Collect-and-continue. A file that fails to parse, or cannot be read, is recorded as a located `ParseSkip` (the file and the parser's first-line, located message) and the walk continues. One bad file among hundreds is a logged skip, never a total failure. This does not weaken fail-loud: it relocates the loudness from "abort everything on the first bad file" to "name every bad file and keep going". The skips are part of the result, so a partial scan can never masquerade as a complete one, and a genuinely invalid file (an unrendered `$IMPORT` placeholder) stays a skip, it is not coerced into a parse.
3. The walk rule. The walk covers every `.java` file under the source root except those in build-output or tool-metadata directories (`target`, `build`, `out`, `bin`, `.git`, `.idea`, `.mvn`). It does not try to guess which files are "relevant" by their path: only `@Entity` and `@Service` classes contribute to the IR regardless of where they sit, and any file that will not parse becomes a located skip. Keeping the rule to a small, stated set of excluded directories, and letting collect-and-continue absorb the rest, is deliberately simpler and more robust than clever per-app exclusions. It is why the whole-app scan, which once died inside a private foundation library, now completes: that library's unparseable files are skips, not a wall.

Every entity the survey parses receives exactly one outcome: discovered, or failed with one located cause (`UNMAPPED_FIELD_TYPE`, `NO_IDENTIFIER`, `NO_BACKING_METHOD`, or `AMBIGUOUS_BINDING`). A mis-binding, a wrong capability, cannot appear, because the discovery rule emits only on an exact single-shape match; the survey states a mis-bound count and it is zero by construction. What phase E1 deliberately does not do: it adds no discovery power. Discovered stays where it was until DTO-aware matching (section 4.2, phase E2) and later phases open it.

### 4.4 Associations: entity-typed fields (E3 decision a; designed and RATIFIED 2026-07-02)

#### 4.4.1 Evidence: the full field census of the 24 blocked entities

E1's decomposition of the 24 UNMAPPED_FIELD_TYPE failures (13 associations, 8 enums, 3 Double) counted only each entity's first-blocking field, because extraction stops at the first unmapped type. This design step re-read every field of all 24 entities. The full census:

- Association fields: 20, across 15 entities. Every one is `@ManyToOne` to a class annotated `@Entity` elsewhere in the same source tree. There is not a single `@OneToMany`, `@ManyToMany`, `@OneToOne`, or `@Embedded` anywhere in the application's sources (verified by sweep), so to-one coverage is total coverage here.
- Enum fields: 21, across 13 entities. Every one is typed by a `public enum` declared in the same source tree, and every annotated one is `@Enumerated(EnumType.STRING)` (section 4.5).
- `Double` fields: 8, across 7 entities, most hidden behind an association or enum failure that E1 reported first (section 4.6). This is materially more than the first-cause count of 3, and it includes both joint-exit target entities: Lease (`monthlyRent`, `securityDeposit`) and Unit (`rentAmount`).

Per-entity clearing math (an entity clears only when every field maps): decision (a) alone clears 8 of 24 (ChatMessage, ChatRoom, FileVersion, Event, EventParameter, Building, OTP, UserRole); (b) alone clears 5 (CommunityEvent, Group, Inspection, Ticket, Property); (c) alone clears 2 (StaffInvoice, TimeEntry); (a) plus (b) clear 4 more (LeaseDocument, LeaseSignatureRequest, Staff, User); (b) plus (c) clear 2 more (LeaseTimeEntry, Vendor); (a) plus (c) clear Tenant; all three together clear Lease and Unit. All 24 clear only with all three decisions.

Two structural facts that shape the rules: `User.status` is typed `UserStatus`, which is an `@Entity` in this app, while `Lease.status` is typed `LeaseStatus`, which is an enum; the same naming pattern means two different things, so classification must come from finding the declaration, never from the name. And `UserRole` declares an `@Id @ManyToOne` field (`Role role`) under `@IdClass`: an association can be part of a composite key, so field mapping and read eligibility are separate concerns, handled by separate rules.

#### 4.4.2 The decision: provider mapping onto the existing IR, no growth

The IR already holds everything associations need: `Field.type` is "a primitive, or a reference to another entity" (`ENTITY_REF`, Volume 4, sections 3.3 and 4), `Relation` carries from, to, and kind (Volume 4, section 3.5), and the validators already check both (rules 3 and 4). Associations are therefore pure provider mapping, not IR growth. Nothing in Volume 4 changes for this decision.

#### 4.4.3 The rule

A field F declared on entity A maps to `ENTITY_REF(T)` and contributes a `Relation`, when ALL hold:

1. F is a persistent field: not `static`, not `@Transient` (the existing skips).
2. F's declared type, resolved by simple name (the last segment when the source writes a fully qualified name), is declared in the same scanned source tree as a class annotated `@Entity`. The declaration must be found; a type that merely looks like an entity name carries nothing.
3. F is annotated with exactly one of `@ManyToOne` or `@OneToOne`. The JPA annotation is the code's own declaration of the relationship and its kind; the provider reads it, it never infers it.
4. The field appears in A's IR shape as `name: ENTITY_REF(T)`, nullable per the existing nullable rule.
5. The model gains `Relation(from = A, to = T, kind)`, where `@ManyToOne` gives MANY_TO_ONE and `@OneToOne` gives ONE_TO_ONE. Identical (from, to, kind) triples are emitted once: two fields on ChatRoom referencing User produce two `ENTITY_REF(User)` fields but one Relation, because the fields carry the references and the Relation is the entity-level summary. Relations are emitted in a deterministic order (by from, then to, then kind).
6. An `@Id` association field maps under this rule like any other field. Whether the entity is readable is a separate, later question answered by eligibility (section 4.2, condition 0): a composite key containing an association refuses the read, but the entity and its shape still extract.

Refused, each keeping the existing UNMAPPED_FIELD_TYPE cause with the message enriched to name what was seen and why it does not map:

- An entity-typed field with no `@ManyToOne` or `@OneToOne` annotation (the relationship is not declared; the provider will not infer one).
- A field whose type resolves in-tree to a class that is neither an `@Entity` nor an enum (a value object, a library type): stays unmapped, message names the resolved declaration.
- A field whose type does not resolve in-tree at all: stays unmapped, message says resolution failed and where it looked.
- Collection associations (`@OneToMany`, `@ManyToMany`, any `List`, `Set`, or `Map` field): deferred to the list-modeling phase (E4 territory, Volume 4 section 7 discipline). Zero such fields exist in the benchmark (the census sweep), so this deferral costs nothing today and is not designed on zero evidence. The message names the field and the deferred shape.
- `@Embedded` types: deferred the same way; zero in the benchmark.

What the entity's IR shape claims, argued: the association field appears as a field of type `ENTITY_REF(T)` AND as a Relation, not one or the other. The field is part of the entity's real shape, and omitting it would under-describe the entity; the Relation is the model-level fact that backends and future consumers can use without walking fields. The MCP tool's output schema claims the field references entity T, which is honest at the semantic level this IR speaks at: the concrete wire shape is the view's business (section 4.2.5 already carries the view annotation), and a view that flattens `room` to `roomId` does not contradict "this field references ChatRoom".

What this rule will never do: infer a relationship kind that is not annotated; resolve a target by name resemblance instead of by finding its declaration; map a collection by taking its element type; or emit a Relation whose target entity is not in the model (the validator would rightly reject it, and the provider refuses first).

Pre-registered fixtures for the build session: A1, `@ManyToOne` to an in-tree entity maps to `ENTITY_REF` plus a MANY_TO_ONE Relation. A2, `@OneToOne` maps with kind ONE_TO_ONE. A3, two fields on one entity referencing the same target produce two fields and one deduplicated Relation. A4, an entity-typed field with no relationship annotation refuses, naming the field. A5, a `List` field refuses with the deferred-shape message. A6, a field typed by an in-tree non-entity class refuses, naming the resolved declaration. A7, a field declared with a fully qualified entity type resolves by last segment. A8, the name trap: a field whose type simple name looks entity-ish but resolves to nothing in-tree refuses (resolution is structural). A9, an `@Id @ManyToOne` field maps as a field while eligibility still refuses the composite-key read.

Benchmark effect of (a) alone: 8 of 24 entities clear extraction (7 move to NO_BACKING_METHOD; UserRole moves to the identifier cause once section 4.2 condition 0 is built, and to NO_BACKING_METHOD in the interim). Contributes to clearing 15 of 24 in combination. Expected new relations in the benchmark IR once all three decisions land: 19 unique MANY_TO_ONE relations. New cause codes: none; refusals stay UNMAPPED_FIELD_TYPE with enriched messages.

### 4.5 Enums (E3 decision b; designed and RATIFIED 2026-07-02)

#### 4.5.1 Evidence

21 enum-typed fields across 13 of the 24 blocked entities. Every enum type is declared `public enum` in the scanned tree (111 enum declarations exist in the app), every annotated field is `@Enumerated(EnumType.STRING)`, and one field (`Staff.payFrequency`) declares its enum with a fully qualified name. What actually crosses the MCP boundary is the JSON serialization of the service's return value, and Java enums serialize as their constant names; the database storage strategy (string or ordinal) never reaches the wire, so it carries no weight in the rule.

#### 4.5.2 The decision: STRING plus a values annotation, no IR growth

An enum field maps to the existing STRING primitive, and the field carries an annotation, name `enum`, whose values are the enum's constant names in declaration order. Argued: the wire form of the field is genuinely a string, so STRING does not lie; and the closed value set is genuinely useful to an agent, so it must not be dropped, which is exactly what the IR's `Annotation` hook exists for (Volume 4, section 3.6): a hint carried without growing the type system prematurely. The alternative, a first-class ENUM primitive in the closed set, would touch the core, every validator, every backend, and every future provider for information the annotation already carries losslessly; it stays a deferred growth candidate under Volume 4, section 7, to be reopened if enums are ever needed as capability inputs (where a schema-level value constraint would earn its cost). For MVP reads, enum fields appear only in outputs.

#### 4.5.3 The rule

A field F on entity A maps to STRING with an `enum` annotation when ALL hold:

1. F is a persistent field (not `static`, not `@Transient`).
2. F's declared type, resolved by simple name (last segment for fully qualified declarations), is declared in the same scanned source tree as an `enum`.
3. The annotation carries the constant names, read from that declaration, in declaration order (deterministic; constants with bodies or constructor arguments contribute their names only).
4. `@Enumerated` presence, absence, or mode changes nothing: the wire form is the constant name in all cases, and the rule maps the wire truth.

Refused, staying UNMAPPED_FIELD_TYPE with an enriched message: a field whose type cannot be resolved in-tree (the provider cannot confirm it is an enum, so it cannot enumerate its values; incomplete evidence is a refusal, not a bare STRING); a type that resolves to something other than an enum falls to the association rule (section 4.4) or its refusals.

What this rule will never do: decide enum-ness from a type's name or naming pattern (the `UserStatus` entity versus `LeaseStatus` enum pair in the evidence is the standing proof); map an unresolvable type to STRING without its values; invent, reorder, or filter constants; or read the storage mode into the model.

Pre-registered fixtures: B1, an in-tree enum field with `@Enumerated(EnumType.STRING)` maps to STRING with the constants in declaration order. B2, a bare enum field with no `@Enumerated` maps identically. B3, a fully-qualified enum declaration resolves by last segment. B4, a field typed by an unresolvable type refuses, naming the failed resolution. B5, the name trap: a field of type `XStatus` where `XStatus` is an in-tree `@Entity` maps under the association rule, not this one.

Benchmark effect of (b) alone: 5 of 24 entities clear extraction (all move to NO_BACKING_METHOD). Contributes to clearing 13 of 24 in combination. New cause codes: none.

### 4.6 Inexact numerics (E3 decision c; designed and RATIFIED 2026-07-02)

#### 4.6.1 Evidence, and why this decision grew

The closed set refused inexact numerics by design, and E1's first-cause count (3 `Double` fields) made refusal look cheap. The full census says otherwise: 8 `Double` fields across 7 of the 24 entities (`hoursWorked` twice, `totalAmount`, `monthlyRent`, `securityDeposit`, `leaseMonthlyRent`, `rentAmount`, `rating`), and among the blocked entities are both ratified joint-exit targets, Lease and Unit. Keeping the refusal makes the ratified E2 plus E3 exit criterion (discovered greater than zero, forecast Unit and Lease) unachievable on this benchmark: not a corner case but a gate.

#### 4.6.2 The decision: add FLOAT to the closed set, as flagged, deliberate IR growth

Recommendation: the closed primitive set gains one member, FLOAT, a 64-bit binary floating point, inexact by declaration. This is deliberate IR growth under Volume 4, section 7 discipline: it is recorded in Volume 4 with the census as its evidence, and the build carries its own validators, JSON round-trip, and golden-file updates.

The alternatives, argued and rejected:

- Keep refusing: honest but now demonstrably expensive (7 of 24 entities, both exit targets), and refusal states nothing true about the source; it merely declines to model it. Rejected on the new evidence; it was the right default until the count was real.
- Map to DECIMAL with a lossy-representation annotation: DECIMAL means exact in this IR ("for money and quantities", Volume 4, section 4). A source `Double` is not exact, and an agent told DECIMAL may assume exact arithmetic. An annotation apologizing for a wrong type is worse than the right type. Rejected as dishonest.
- Omit the field with an annotation: the wire JSON still contains the field (the views expose it), so the IR would under-describe every read's real output. Rejected.

FLOAT states exactly what the source declares: an inexact numeric. That some of these fields are money-as-Double (`totalAmount`, `monthlyRent`) is a defect of the modeled system, and the model's job is to describe the system as it is, not to repair it by coercion; the description is what makes the defect visible.

#### 4.6.3 The rule

1. Java `double`, `Double`, `float`, and `Float` map to FLOAT.
2. `BigDecimal` continues to map to DECIMAL. No coercion crosses the exact-inexact line in either direction, ever: never `Double` to DECIMAL, never `BigDecimal` to FLOAT.
3. FLOAT is an ordinary primitive thereafter: usable as a field type, subject to the same validators, serialized as a JSON number.
4. An identifier of type FLOAT does not form a read intent (an inexact primary key is not a lookup key the tool will bind); this is an eligibility refusal with a located message, alongside the composite-key rule.

What this rule will never do: coerce between exact and inexact representations; guess a precision or scale; or treat FLOAT as acceptable input for an id.

Pre-registered fixtures: C1, a `Double` field maps to FLOAT. C2, `double`, `float`, and `Float` map to FLOAT. C3, `BigDecimal` still maps to DECIMAL and no cross-coercion occurs. C4, IR JSON round-trips FLOAT and the golden files update deliberately with a visible diff. C5, an entity whose `@Id` is `Double` forms no read intent and refuses with a located message.

Benchmark effect of (c) alone: 2 of 24 entities clear extraction (StaffInvoice, TimeEntry, both to NO_BACKING_METHOD). Contributes to clearing 7 of 24 in combination, including both joint-exit targets. New cause codes: none.

### 4.7 Model closure: the dangling-reference rule (partial-model integrity, decision 1; designed and RATIFIED 2026-07-02)

The third-app trial (docs/experiments/third-app-trial.md, section 8) found the one case where valid discoveries exist but cannot be generated: an association field on an extracted entity targets an entity that is not in the model (it failed extraction, or its file was a parse skip), so the emitted `ENTITY_REF` field and `Relation` dangle, the model fails validation rules 3 and 4 (Volume 4, section 5), `tibyan scan --out` exits 2, and generation is blocked for the whole app, including its otherwise valid discoveries. On erp-backend that blocked all 7 real discoveries.

The invariant this rule must guarantee, stated first because it is the point: a model that passes validation is always producible from any real app, whatever fraction of it extracted. Producibility must not depend on the app extracting completely; a scan that discovers 7 of 25 must still generate those 7.

The options, argued honestly:

- (a) Close the model: when an association's target is not among the extracted entities, drop the `ENTITY_REF` field and its `Relation`, and disclose the omission (the entity is marked partial, section 4.9). The model stays closed and valid by construction.
- (b) Emit an unresolved marker and teach validation to accept a reference to a name not in the model. This is IR growth (a new unresolved-reference concept), and it is weighed against the growth bar (Volume 4, section 4) and fails it: the current MCP backend does not emit an entity's field schema into the tool at all (the tool's output is `ENTITY_REF(E)`, not E's fields), so a consumer sees no difference between a dropped field and an unresolved-marked one. Growth that buys the consumer nothing is not justified.
- (c) Cascade-refuse the referencing entity. This throws away valid discoveries (Ticket references three absent entities on erp; refusing Ticket would lose getTicket, and anything referencing Ticket would fall next). It is the opposite of the goal.

Decision: option (a). Because the tool schema does not expose entity fields today, dropping a dangling field costs the consumer nothing visible and guarantees producibility; it is the smallest honest surface, and it needs no IR growth.

The rule. After every entity has been through extraction (section 4.8), and before validation, the provider runs a closure pass over the model:

1. Let `present` be the set of entity names that extracted into the model.
2. For every entity field of type `ENTITY_REF(T)` where `T` is not in `present`: drop the field from the entity, and record it as a dropped reference in the entity's partial note (section 4.9). The field is never left in the model pointing at an absent entity.
3. For every `Relation(from, to, kind)` where `from` or `to` is not in `present`: drop the relation. (A relation whose `from` is absent cannot occur, since relations are emitted only by extracted entities; the `to` case is the live one.)
4. A capability's `output` of `ENTITY_REF(E)` is never dropped or altered: a capability is only emitted for an entity that extracted, so its output entity is always present. Closure touches entity fields and relations, never capabilities or bindings.

After the pass, every `ENTITY_REF` and every `Relation` in the model targets an entity that exists, so validation rules 3 and 4 hold by construction, and the model is producible.

What this rule will never do: emit a reference to an entity not in the model; drop a capability or change a binding to make a model validate (discovery is untouched, section 4.8); silently drop a reference without disclosing it (every drop is in the survey and the entity's partial note); or grow the IR to represent an unresolved reference.

Refusal and disclosure: closure does not refuse anything; it prunes and discloses. An entity that lost a reference is partial (section 4.9). Nothing about which capabilities are discovered changes.

Acceptance shape (for the build): on erp-backend, with closure in place, the 7 currently-discovered capabilities generate successfully; `tibyan generate --backend mcp` from the erp IR produces a valid manifest for them, where before it exited 2.

Pre-registered fixtures: G1, an entity A with a good `findById` and a `@ManyToOne B` where B is absent from the model (B's file does not parse, or B fails a non-tolerated case): A extracts, discovers via 4.1, and its `ENTITY_REF(B)` field plus the A-to-B relation are dropped and disclosed; the model validates. G2, generation: `generate` on the G1 model produces a valid manifest for getA. G3, a relation whose target is present is kept unchanged (closure prunes only the dangling).

Build finding (2026-07-02), recorded not worked around: with field tolerance (section 4.8) built, every scanned `@Entity` extracts, and an `ENTITY_REF` is emitted only for a type the registry resolves as an `@Entity`, so every reference target is present and the scan path never produces a dangling reference. Closure therefore never fires on real input (it dropped nothing on either benchmark), and the acceptance shape, erp's discoveries generating, is met because tolerance leaves no dangling reference to begin with. Closure is retained as the standing producibility invariant, so a validated model is always producible even if a later phase reintroduces a way for an entity to be absent (a parse-skipped entity file another entity resolves against, or a shape tolerance does not cover). Because the scan path cannot exercise it, the G1 and G2 fixtures are realized as a direct unit test of the drop-and-disclose logic on a synthetic model, not as scan fixtures; G3 (a present target kept) is exercised in the scan path. The invariant holds; its trigger is currently unreachable, which is a healthy state, not a gap (docs/experiments/third-app-trial.md, section 11.3).

### 4.8 Field tolerance: skip-with-a-note versus veto (partial-model integrity, decision 2; designed and RATIFIED 2026-07-02)

The trial's field-veto finding: a single field the provider cannot map today aborts the whole entity's extraction (`toField` throws, the entity is excluded). On erp-backend a to-many collection blocked 10 entities and an `@Embeddable Address` blocked 3, so entities with a perfectly good `findById` never extracted and never discovered. The trial's arithmetic is that tolerating these is worth more discoveries than any new matching rule: nine of the blocked entities have an entity-returning `findById` and would discover via the existing rule 4.1 the moment they extract.

The hard constraint, stated first: tolerance must never soften discovery. The rules 4.1 and 4.2, eligibility (condition 0), and the never-guess pooling (condition 10) are untouched. Tolerance changes only whether an entity reaches discovery, never how discovery decides. It cannot manufacture a capability: the only entity-derived inputs to matching are the entity name and the `@Id` type, and tolerance changes neither. Skipping a field can only make an entity's shape incomplete; it can never make a wrong capability.

The rule. During extraction, a field whose type the closed set and the association and enum rules cannot map is skipped with a located note, and the entity extracts without it, rather than the field vetoing the entity. Specifically, `toField` skips (records a note, emits no field) instead of throwing for:

1. To-many collections: `Set`, `List`, `Map`, `Collection`, `Iterable`, generic or raw, and `@OneToMany` / `@ManyToMany` fields. Note: "to-many field, deferred to the list-modeling phase (E4); skipped, not modeled." Output modeling of collections is explicitly E4, not this phase; here they are only prevented from vetoing.
2. Binary and primitive arrays: `byte[]` and other arrays of a scalar. Note: "binary or array field with no scalar mapping in the closed set; skipped, not modeled as a value." This corrects the trial's message nuance, where `byte[]` was reported as a to-many shape; it is a blob, not a collection of business items, and its note now says so.
3. In-tree value types: a field whose type resolves in the scanned tree to a class that is neither `@Entity` nor enum, including `@Embeddable` value objects. Note: "in-tree value type (for example @Embeddable), not an entity or enum; skipped." When the class carries `@Embeddable`, the note names it as such.
4. Unresolvable types: a field whose type is not found in the scanned tree at all (a library type). Note: "type not resolvable in the scanned source tree; skipped."
5. Entity-typed fields with no declared relationship: an entity-typed field with no `@ManyToOne` or `@OneToOne`. Note: "entity-typed field with no declared relationship; skipped rather than inferred." (Section 4.4's refusal to infer a relationship kind stands; the change is that it skips the field instead of vetoing the entity.)

What must still veto, and what still refuses: nothing about a field's type vetoes the whole entity any more; skipping a field never yields a wrong model, only an incomplete one, disclosed. What still refuses is discovery, unchanged: a composite key still refuses to form a read intent (COMPOSITE_KEY); an `@Id` that maps to no bindable primitive, or to FLOAT, still forms no read intent (NO_IDENTIFIER); an ambiguous pool still refuses (AMBIGUOUS_BINDING); an entity with no backing method still refuses (NO_BACKING_METHOD). Extraction is now tolerant; discovery is exactly as strict as before.

The latent-identifier nuance, resolved: once a blocking field no longer vetoes the entity, an entity whose `@Id` is itself unmappable surfaces its identifier case instead of hiding behind the field failure. On erp-backend, EmailTemplate has an enum-typed `@Id` and a blocking `Map` field; today it fails as UNMAPPED_FIELD_TYPE on the map, so its identifier is never evaluated. With tolerance, the map is skipped, EmailTemplate extracts, and its enum `@Id` (which the primitive mapper does not map) surfaces as NO_IDENTIFIER, the existing cause for an `@Id` that maps to no bindable primitive (the enum-to-STRING field mapping is not the same as an enum-typed lookup key, which is out of scope). The message may name the enum specifically; the cause is the existing NO_IDENTIFIER.

Interaction with closure (4.7): tolerance runs per field during extraction; closure (4.7) runs once over the whole model afterward. A field skipped by tolerance and a reference dropped by closure both make the entity partial (4.9). Tolerance reduces how many entities are absent, which reduces how much closure has to prune; closure remains the guarantee that holds regardless.

What this rule will never do: skip a field in a way that changes the `@Id` or the entity name and thus the matching inputs; let a skipped field cause a capability to be discovered that strict discovery would not emit; coerce a skipped type into a near-neighbor to avoid skipping it; or skip a field silently (every skip is a located note).

Pre-registered fixtures: T1 (the leverage), an entity with a `Set<Other>` field and a good `findById` returning the entity extracts partial (the collection skipped and noted) and discovers via 4.1. T2, an `@Embeddable` value-type field is skipped, the entity extracts. T3, a `byte[]` field is skipped with the binary note, not the to-many note. T4, an unresolvable library-type field is skipped, the entity extracts. T5 (latent id), an entity with a blocking collection and an enum-typed `@Id` extracts once the collection is skipped and surfaces NO_IDENTIFIER. T6 (negative, tolerance did not soften discovery), an entity that extracts partial but has no backing `findById` still fails NO_BACKING_METHOD, and a composite-key entity that extracts partial still fails COMPOSITE_KEY. T7 (mis-bound guard), the T1 entity's discovered binding is exactly the right method, proving tolerance did not perturb matching.

### 4.9 The partial-model survey contract (partial-model integrity, decision 3; designed and RATIFIED 2026-07-02)

With tolerance (4.8) and closure (4.7), an entity can be discovered and also carry skipped fields. The survey must report this without gaming the health metric (discovered rising while mis-bound stays zero).

What partial means, and why discovered stays honest: a partial entity is one that lost at least one field, to tolerance or to closure. A capability discovered for a partial entity is a fully valid capability: its binding invokes the real service method, which returns the real object with all its fields at run time; only the IR's description of the entity's field shape is incomplete. The current MCP tool does not expose an entity's field schema at all, so a discovered-partial capability and a discovered-complete one are identical to a consumer today. Counting a partial discovery as discovered is therefore honest: the capability is real. The one thing that must not be silent is the incompleteness of the entity shape, so it is disclosed.

The rule:

1. Disclosure in the IR (recommended, no growth). An entity that lost fields carries a `partial` annotation (the existing Annotation hook, Volume 4, section 3.6), whose values name the skipped and dropped field names. A validated IR is then self-describing about its own incompleteness; a consumer reading the IR can tell the shape is partial without re-deriving it. No new IR object or type is added; this is the same hook that carries `view` and `enum`.
2. Disclosure in the survey (required). A discovered entity that is partial reports as discovered with a qualifier, for example `getProduct -> ProductService#findById (partial: 3 fields skipped)`, and each skip or drop is a located note. A failed entity that is partial reports its cause as today, with the partial note added.
3. The benchmark row gains a partial dimension, not a redefinition. The `discovered`, `failed by cause`, `mis-bound`, and `skipped-by-parse` columns keep their historical meaning, so rows stay comparable across the phases. A new field, `partial N (M fields skipped)`, is added, and the experiment log carries a one-line methodology note the first time it appears, so the jump in `discovered` on erp-backend is read correctly: it is a genuine increase in real capabilities, enabled by tolerance, with the partial-shape caveat disclosed, not a moved goalpost.

The metric discipline is unchanged: discovered rising while mis-bound stays zero remains the health signal, and mis-bound is still the one alarm. Partiality is orthogonal: it measures how complete each discovered entity's shape is, and it can be watched falling over time as later phases (E4 for collections, an embeddable decision, and so on) model more field shapes, but it never inflates discovered and never masks a mis-binding.

What this rule will never do: count a refused or absent entity as discovered; hide a partial shape behind a clean discovered line; redefine an existing benchmark column so historical rows stop being comparable; or add an IR object to carry partiality when the annotation hook suffices.

A flagged, deferred concern (stated plainly, not thinned): when a later backend phase emits an entity's field schema into the generated tool, a partial entity's schema will under-describe the object the runtime actually returns, and that backend must surface the partiality there (for example, marking the schema open or noting omitted fields) so an agent is not misled. Today no backend emits entity field schemas, so this does not arise; it is recorded here as the one place partiality becomes consumer-visible, to be handled by that backend phase, not this one.

> Ratification rider (2026-07-02): when any future backend emits entity field schemas, the planned OpenAPI backend included, partiality must surface in that schema. This is not an optional note; it is a standing requirement inherited by that backend's own design session, which must state how it discloses a partial entity's omitted fields before it ships. The `partial` annotation (rule 1 above) is the source of truth that backend reads.

Pre-registered fixtures: S1, the survey line for a discovered partial entity carries the `(partial: ...)` qualifier and the entity's IR carries the `partial` annotation naming the skipped fields. S2, a fully-modeled discovered entity carries no `partial` annotation and no qualifier (absence means complete). S3, the survey totals line reports a partial count distinct from discovered and failed.

### 4.10 List reads: the unfiltered list-all capability (phase E4; designed, RATIFIED, and BUILT 2026-07-02)

Phase E4 is the trial's fourth-ranked gap: real service APIs are dominated by reads that return many of an entity, and the tool models none of them. This section is that rule, designed before any code exactly as 4.1, 4.2, and E3 to E5 were: it enters the volume in one commit, the code follows only after ratification. It is the largest design surface since 4.2, and its first duty is to draw its own boundary honestly.

#### 4.10.1 Evidence: the list-read census of both benchmarks (read-only, 2026-07-02)

Every `@Service` method returning a collection or page of an entity was traced from source in both benchmarks. The full census, with per-method classification and bodies, is in the experiment logs (docs/experiments/third-app-trial.md, section 12, and docs/experiments/second-app-trial.md, section 8.6). Counts by shape:

| Shape | erp-backend | pms-backend |
|---|---|---|
| (a) bare `findAll()` / (b) bare `findAll(Pageable)`, body a verbatim framework pass-through returning a collection of E | 11 methods across 10 entities | 0 |
| (c) filtered, one or more domain parameters (returns a collection of E or a view) | many (search, category id, supplier id, account type, status, and the multi-field `searchByFields`) | ~35, every one returning a custom `*ResultSet` envelope |
| (d) view-returning list or projection (`Page<NameIdDTO>`, `List<XxxReadDTO>`, `List<VendorVTO>`) | 8 (projection or stream-and-map) | ~7 `List<VTO>` plus the 35 `*ResultSet` envelopes (all view-shaped) |
| (e) other or scalar (`List<String>`, mapping helpers, writes returning a list) | several, named | 1 (`extractRoleIds`, a library JWT helper) |

The decisive facts the census establishes:

- erp exposes a clean, structurally verifiable list-all shape: 10 entities have a service method whose body is exactly `return <repo>.findAll();` or `return <repo>.findAll(<pageable>);`, where `<repo>` resolves to a Spring Data repository of the returned element type (for example `ContactRepository extends JpaRepository<Contact, UUID>`, `EmailTemplateRepository extends JpaRepository<EmailTemplate, EmailTemplateType>`). This is the framework by-collection contract, the list analogue of 4.1's by-primary-key contract, and it is provable from the same repository resolution the E2 `RepositoryCatalog` already performs.
- pms exposes no such shape at all. Every one of pms's ~35 list reads takes domain filter parameters and returns a custom `*ResultSet` view-envelope (`TenantResultSet`, `VendorResultSet`), and its ~7 `List<VTO>` reads each filter by a foreign key (`getVendorsByBuildingId(Long)`, `getDocumentsByLeaseId(Long)`). Not one is an unfiltered, entity-returning `findAll`. pms's entire list API lives in the deferred set below.
- The most dangerous shape is a method that looks unfiltered but is not: erp's `TransactionServiceImpl#findCompleted()` takes no parameters yet its body is `findAll().stream().filter(status == COMPLETED).toList()`. Binding a `listTransaction` to it would present a filtered subset as the whole collection, a mis-binding by the definition in 4.10.9. The rule must refuse it structurally.
- List reads are identifier-independent, and this is a genuine coverage gain: `EmailTemplate` fails the by-id read (its `@Id` is an enum, NO_IDENTIFIER, 4.8), yet `listEmailTemplate` is a clean, honest capability, because a list-all needs no bindable id. E4 discovers capabilities on entities where E1 to E5 discover none.
- Plurality: one entity (`ProductCategory`) declares two qualifying list-all methods, `findAll()` and `findAll(Pageable)`. Because paging is unmodeled (4.10.3), these produce the identical capability and the tool cannot choose between them. This is the structurally-indistinguishable pair, live in the benchmark.

#### 4.10.2 The scoping decision, stated first: model list-all, defer the rest by name

E4 models exactly one list shape: the unfiltered list-all read, where a `@Service` method returns a collection of an entity by delegating verbatim to the framework `findAll` on a Spring Data repository of that entity. It defers, by name and with a loud diagnostic, every other list shape:

- Filtered reads (any domain parameter beyond a lone `Pageable`/`Sort`): deferred to a successor phase (provisionally E6, filtered and multi-argument list reads). A parameter's meaning is a semantic claim the tool cannot verify structurally: a `UUID categoryId`, a `String search`, a `Long tenantId`, and a `Long buildingId` are the same Java shape, and only their names, which carry no evidence weight (4.2.7), suggest what they filter. "Filter by category" in a tool description is a claim an agent will act on, and the tool cannot substantiate it. Copying the parameter name into an input does not neutralize the claim; the name IS the claim. Filtered reads therefore get their own designed honesty rule later, not a thinned rider here.
- View-returning and projection lists (`Page<NameIdDTO>`, `List<XxxReadDTO>`, mapper-transformed lists): deferred. Unlike 4.2's by-id view rule, these offer no single in-tree mapping step to resolve the element type against the entity; they are Spring Data projections declared on the repository, or stream-and-map transforms in the body. Extending the 4.2 machinery to lists is a separate decision the census does not yet justify (the projection type `NameIdDTO` is even shared across entities, carrying less entity information than pms's per-entity VTOs).
- Paged-envelope wrapper types (pms's `*ResultSet`): deferred with the filtered reads, since every one is also filtered.

The split is not thinning; it is the honest boundary the census draws. A ratifiable E4 that models list-all and defers filters cleanly is worth more than one that models everything thinly. The consequence, stated as plainly as the E2 sequencing finding was: E4 discovers list capabilities on erp-backend and zero on pms-backend, because pms has no unfiltered entity-returning list read. The Volume 13 exit criterion for E4 ("at least one list or filter read on pms-backend in each module") is therefore not met by this rule and is re-scoped to the honest core (4.10.9 and Volume 13).

#### 4.10.3 The IR representation

The capability's `output` is the element type, `ENTITY_REF(E)`, and its `outputMultiple` flag is true (Volume 4, sections 3.4 and 4). The `inputs` map is empty: a list-all takes no domain input, and a forwarded `Pageable`/`Sort` is not modeled as an input (it is framework paging, not a capability parameter). A read whose terminal is the paged framework `findAll(Pageable)` additionally carries a `paged` annotation (the existing hook, Volume 4, section 3.6), disclosing that the concrete wire is a page envelope rather than a bare array; the modeled output is still the element collection. Ordering and uniqueness are never modeled (Volume 4, section 4). This is the smallest representation that does not lie: it says "this capability returns many E", discloses that a paged wire envelope exists, and claims nothing about order, count, or page shape.

#### 4.10.4 The rule

A `@Service` method M yields the list capability `list<E>` for entity E when ALL of the following hold. Conditions 1 and 2 restate the frame; the rest are the list-all contract.

1. M is declared on a class annotated `@Service`, is `public`, and is not `static`.
2. M's declared return type is a collection of E: one of `List`, `Set`, `Collection`, `Iterable`, `Page`, or `Slice`, whose single type argument, resolved by simple name in the scanned tree, is a class annotated `@Entity`. That entity is E, the element type. A raw collection, a collection of a primitive, or a collection of a non-entity type (a view, a projection, `String`) does not match this rule.
3. M declares either no parameters, or exactly one parameter whose type is the framework paging type `Pageable` or `Sort`. Any other parameter, of any type, disqualifies M from this rule (it is a filtered read, deferred; 4.10.6). `Pageable`/`Sort` are recognized structurally as framework types, never by name of the argument.
4. M's body contains exactly one persistence access, the load, and it is a call to the framework by-collection read `findAll` on a field of M's service whose declared type resolves in-tree to a Spring Data repository of E: an interface extending one of the recognized supertypes (`JpaRepository`, `CrudRepository`, `ListCrudRepository`, `PagingAndSortingRepository`, `ListPagingAndSortingRepository`) whose first type argument is E. The repository's second type argument (the id type) is not consulted: a list-all is independent of the entity's identifier.
5. If M declares a `Pageable`/`Sort` parameter, the argument to `findAll` is that parameter, verbatim (`findAll(pageable)`); if M declares no parameter, the call is the no-argument `findAll()`. No expression over the parameter, and no manufactured `Pageable`, is accepted.
6. M's body is exactly one read shape: a single `return` whose expression is the `findAll` call itself, with nothing wrapped around it. No `stream`, no `filter`, no `map`, no `subList`, no `PageImpl` reconstruction, no second call, no local transformation. The returned value is precisely what the framework `findAll` returned. This is the condition that refuses `findCompleted()` (a post-`findAll` `stream().filter(...)`), `ProductServiceImpl#findAll(Pageable)` (a custom-query load plus `convertToFullName` plus a manual `PageImpl`), and `DeliveryServiceImpl#findAll()` (a custom query plus `map`).
7. No other repository access appears anywhere in M's body: no call on any other repository-typed field, and no mutation call (`save`, `saveAll`, `insert`, `update`, `delete`, `deleteById`, `deleteAll`, `persist`, `merge`, `flush`) anywhere. A list method that writes is not a read.

Diagnostics amendment (ratified 2026-07-02, during the build). Condition 3's refusal separates domain parameters from framework paging parameters, so a filtered read like `findByCategory(UUID categoryId, Pageable pageable)` is refused with a diagnostic that names the domain parameter specifically (`takes domain parameter(s) [categoryId]`), rather than a generic "takes 2 parameters". This is diagnostics-only: it changes no matching outcome (the method is refused either way, and no domain parameter name is ever copied into an input, section 4.10.2), and it makes the refusal more honest by naming what the reader must look at. It was made mid-build and flagged as an amendment in that session's summary (CLAUDE.md rule 13), and is ratified retroactively here.

#### 4.10.5 Naming and pooling

The capability is named `list<E>`, where `<E>` is the entity's simple name (`listContact`, `listTransaction`, `listEmailTemplate`). The name is minted from the entity, never from the method: a method named `findAll`, `getAll`, or anything else yields `list<E>` all the same, because the method name carries no evidence weight. Entity names are unique within the module, so `list<E>` is unique.

E4 models exactly one list-all capability per entity, so its pool is exactly the set of methods on all `@Service` classes that satisfy 4.10.4 for E:

- Exactly one method in the pool: `list<E>` is emitted, bound to `<ServiceClassSimpleName>#<methodName>`.
- Zero methods: no list capability for E (the entity may still have a by-id capability, or none). This is not a failure; it is the absence of a list-all read.
- More than one method: LIST_AMBIGUOUS, a new cause, listing every candidate. Because paging is unmodeled, `findAll()` and `findAll(Pageable)` are indistinguishable at the capability level and both fall in this pool; the tool refuses rather than prefer one (preferring the paged overload would be a nominal or heuristic guess, forbidden). `ProductCategory` is the live case.

The list pool and the by-id pools (4.1, 4.2) never intersect: a by-id candidate returns E singular and takes an id, a list candidate returns a collection of E and takes no domain argument. `get<E>` and `list<E>` are different capabilities with different names and coexist freely. E4 is therefore strictly additive to by-id discovery: it cannot move, mask, or perturb any `get<E>` result, and any such movement on re-run is a regression finding (4.10.9). The plurality the roadmap anticipated (findAll, findActive, findByCategory as several list reads of one entity) is real, but findActive and findByCategory are filtered reads, deferred; within E4's scope an entity has at most one list-all, so their naming-apart is a problem for the filtered-reads phase, not this one.

#### 4.10.6 Covered and deferred patterns

Covered: P-List-a, the unfiltered list-all with a framework `findAll` terminal, no-argument or `Pageable`/`Sort`-forwarded, direct on a Spring Data repository of E (erp's `listContact`, `listTruck`, `listAccount`, and so on).

Deferred, each keeping a loud, named diagnostic so an absence of a list capability is never silent:

- P-List-filter: a domain parameter present (erp's `findByCategory(UUID, Pageable)`, `searchByFields(...)`, `findBySupplier`, and all of pms's `*ResultSet` reads). Diagnostic names it a deferred filtered list read. The bulk of both apps' real list API; its own phase.
- P-List-view: the element type is a view or projection, or the body maps the loaded collection (erp's `getListableContacts` returning `Page<NameIdDTO>`, `QuoteServiceImpl#findAll` returning `List<QuoteReadDTO>`, pms's `List<VendorVTO>` reads). Diagnostic names it a deferred view-returning list read.
- P-List-derived: the load terminal is not the framework `findAll` but a derived or `@Query` collection method (`findByActiveTrue`, `findAllWithAssociations`). Diagnostic names the non-framework terminal, the list analogue of the 4.2 P1b refusal. This is also the boundary that forbids query-derivation from Spring Data method names: the tool never reads `findActive` or `findByStatus` as "list active" or "filter by status"; that would be nominal inference, refused by principle.
- P-List-hop: the load delegates one hop through a hand-written repository interface to a Spring Data `findAll`. The 4.2 one-hop trace could extend here, but no benchmark evidence requires it yet (erp calls Spring Data directly; pms's list reads are all filtered), so it is deferred rather than designed on zero evidence.

#### 4.10.7 What this rule will never do

- Never treat a parameter's name as its meaning: it will not read `categoryId`, `status`, or `search` as a filter, and it refuses any read with a domain parameter rather than copying the name into an input.
- Never derive a query from a Spring Data method name: `findByActiveTrue` is never "list active"; only the framework `findAll` contract counts.
- Never present a filtered collection as the whole: a body that post-filters, maps, slices, or reconstructs the framework result refuses, so a subset is never bound to `list<E>`.
- Never model order, uniqueness, or paging as fact: `List` and `Set` are the same "many E", and a page envelope is disclosed, never claimed.
- Never prefer one indistinguishable method over another: two qualifying list-all methods refuse as LIST_AMBIGUOUS.
- Never bind a method that writes, or that touches a second repository.
- Never let a list discovery change a by-id discovery: the pools are disjoint by construction.

#### 4.10.8 Pre-registered fixtures (the build session must include these before the code exists)

Positives:

- L1, bare `findAll()`: a `@Service` method `List<E> findAll()` whose body is `return repo.findAll();`, `repo` declared `extends JpaRepository<E, ID>` in-tree, yields `listE` with `output ENTITY_REF(E)`, `outputMultiple true`, empty inputs, no `paged` annotation. (erp's `listTruck`, `listAccount`, `listTransaction` shape.)
- L2, paged `findAll(Pageable)`: `Page<E> findAll(Pageable pageable)` whose body is `return repo.findAll(pageable);` yields `listE` with `outputMultiple true`, empty inputs, and a `paged` annotation. (erp's `listContact`, `listContactGroup` shape.)
- L3, `Set`/`Slice`/`Iterable`/`Collection` element containers each map to the same `outputMultiple` output (container variety does not change the IR).
- L4, identifier independence: an entity whose `@Id` maps to no bindable primitive (an enum id) still yields `listE` from a clean `findAll(Pageable)`, proving the list rule ignores the id type. (erp's `listEmailTemplate`; the entity's `getE` remains NO_IDENTIFIER, and both outcomes coexist.)

Refusals, each asserting the located diagnostic:

- L5, filtered read: `Page<E> findByCategory(UUID categoryId, Pageable)` refuses with the deferred filtered-list diagnostic and mints no `listE`; the fixture asserts no input named `categoryId` is ever produced (the proof the tool never claims filter semantics).
- L6, unfiltered-looking but body-filtered: `List<E> findCompleted()` whose body is `return repo.findAll().stream().filter(...).toList();` refuses on condition 6, proving a filtered subset never binds to `listE`.
- L7, view-returning list: `Page<NameIdDTO> getListable(Pageable)` or a `stream().map(mapper::to).toList()` body refuses with the deferred view-list diagnostic.
- L8, non-framework terminal: `List<E> findAllActive()` whose body calls `repo.findByActiveTrue()` refuses with the deferred derived-terminal diagnostic (the anti-nominal-inference proof).
- L9, mutation in body: a collection-returning method that also calls `repo.save(...)` refuses on condition 7.
- L10, the structurally-indistinguishable pair (coexistence and ambiguity): an entity with both `findAll()` and `findAll(Pageable)`, both clean, refuses as LIST_AMBIGUOUS listing both, minting no `listE`. (erp's `ProductCategory`.)
- L11, mis-bound guard: an entity with exactly one clean `findAll` on the right repository yields a `listE` bound to exactly that method on that service, and its output element type is exactly E (not a sibling entity). This is the list analogue of the by-id mis-bound guard.
- L12, coexistence of get and list: an entity with both a by-id `findById` (4.1) and a clean `findAll` yields both `getE` and `listE`, independently, proving the pools are disjoint and E4 is additive.

#### 4.10.9 Benchmark forecast and the split finding

Stated before the build, so the build is measured against a forecast. The number-by-number forecasts are in the experiment logs (erp: docs/experiments/third-app-trial.md, section 12; pms: docs/experiments/second-app-trial.md, section 8.6). In summary:

- erp-backend: 9 new list capabilities (`listContact`, `listContactCategory`, `listContactGroup`, `listAccount`, `listTransaction`, `listTruck`, `listProductGroup`, `listVariationGroup`, `listEmailTemplate`), one of them (`listEmailTemplate`) on an entity with no by-id capability. `ProductCategory` refuses as LIST_AMBIGUOUS (two qualifying methods). `Product` gets no list (its `findAll` is a custom-query transform). The 16 by-id discoveries are unchanged. Mis-bound 0.
- pms-backend: 0 new list capabilities, because pms has no unfiltered entity-returning list read; all ~42 list reads are deferred as filtered or view-returning, each named in the survey. The existing row (discovered 2, mis-bound 0) is unchanged. This is the split finding made concrete, and it is why E4's exit criterion is re-scoped.
- The hard bar is unchanged and absolute: mis-bound exactly 0 on both apps, and every existing `get<E>` outcome byte-identical (E4 is additive). A single mis-binding, or any movement in an existing discovery, stops the phase.

Mis-bound for a list capability means either of two things, and both are counted: a `list<E>` bound to a method that returns a collection of the wrong entity (an element-type error), or a filtered method presented as unfiltered (a subset bound to `list<E>`, the `findCompleted()` hazard). The rule refuses both structurally (conditions 2 and 6), and fixtures L6 and L11 guard them.

### 4.11 E6: the filter-semantics-plus-envelope problem, DEFERRED with a documented ceiling (designed 2026-07-02)

E6 owns everything that does more than an unfiltered by-primary-key or list-all read: filtered reads (a WHERE with predicates beyond the id equality), projected and view-returning custom queries, and envelope-wrapped reads (the pms `*ResultSet` wrappers that hide whether a read even exists). P1b froze the lower boundary, a query that is nothing but a by-primary-key load (section 4.2.10); E6 is the majority above it. This section is the design half, and its honest conclusion is a deferral with a stated ceiling, not a discovery rule. The governing constraint is inherited and absolute: the tool may never present a filtered read as if its filter semantics were verified, because a parameter's name is a claim an agent acts on. Any option that cannot meet that constraint is rejected regardless of how much coverage it would add.

Ratified split and verdict (2026-07-02, Volume 13): E6 is split into E6a (envelope transparency, making wrapped reads visible and classified, designed in section 4.11.7 below) and E6b (the filter-semantics decision, deferred with the ceiling of sections 4.11.4 to 4.11.6, human-attested its leading candidate). E6a is first, because an honesty decision about a filter cannot be made on a read that cannot be seen; E6a grants visibility, never discovery. The modeled-opaque rejection (section 4.11.3) is a standing rejection, never revisited casually: an MCP tool's name and signature are the claim, and a disclaimer is invisible at the moment of action.

#### 4.11.1 The census: the filtered read is the majority, and pms hides it in envelopes

From the read-only census of both benchmarks (full counts in the ledgers: docs/experiments/second-app-trial.md section 8.9, docs/experiments/third-app-trial.md section 15). By shape:

- (a) single-predicate filter beyond id, (b) multi-predicate filter, (c) filter plus paging: pms has roughly 35 such reads, every one returning a `*ResultSet` envelope; erp has roughly 30, returning `Page<E>` or `List<E>` directly.
- (d) projection or view-returning custom query: pms roughly 7 (`List<VTO>` by a foreign key); erp roughly 9 (`Page<NameIdDTO>`, `List<XxxReadDTO>`).
- (e) envelope-wrapped reads where the existence of a read is hidden by an opaque wrapper: pms roughly 35 (the `*ResultSet` types); erp 0 (its filtered reads are directly typed).
- (f) collection filters (`findByX` returning a collection): both apps, folded into (a) to (c) above.

Two facts decide the design. First, the filtered read is the majority of real data access on both apps: after E1 to E5 and E4, pms discovers 2 reads and erp 25, and essentially everything else, dozens of reads per app, is filtered, projected, or wrapped. Second, and decisively, the two apps differ in where the filter lives, and pms is the harder and the designated benchmark:

- erp's filters are in the query, and split in two: some are Spring Data derived queries (`findByNameContainingIgnoreCase`, `findByParentId`, `findByProductCategoryId`, `findByAccountType`), whose meaning the framework defines from the method name against the entity's properties; the rest are custom `@Query` with arbitrary JPQL (`searchByFields` with `:search IS NULL OR ...`), whose meaning is in the query text.
- pms's filters are in imperative code. `TenantServiceImpl#getAllByFilter(fullName, email, orderBy, orderDir, pageNum, pageSize, pageOffset)` builds a `TenantSearchFilter` and calls `tenantRepository.selectAllByFilters(filter)`, which a hand-written `TenantQueryBuilder` executes. What `fullName` and `email` filter, and how (exact match, prefix, contains), is nowhere in any query text: it is in the query builder's Java. It is structurally invisible, full stop. pms has zero derived-query filters and zero readable filter predicates.

So on pms, the designated E6 benchmark whose entire list API is filtered and envelope-shaped, there is no structural way to know any filter's meaning. That is the ground truth this design must answer to.

#### 4.11.2 Part 1: the envelope sub-problem (seeing the read), and why it is a separate prerequisite

The pms `*ResultSet` wrappers make a read invisible: E4 named only 3 deferred list reads on pms, not the roughly 42 the census counts, because the recognized-container rule cannot see into an opaque wrapper return type (section 4.10.6; docs/experiments/second-app-trial.md section 8.7). Part 1 asks whether the tool can structurally see through a wrapper to determine that a read of E exists.

It can, and the mechanism is bounded: `TenantResultSet` is not generic over the entity (it is a per-entity named class, not `ResultSet<Tenant>`), so there is no type argument to read; but it declares exactly one collection field, `List<TenantVTO> data`, plus a `Long total`. The tool can resolve the wrapper class in-tree, find its single collection field, read the element type (`TenantVTO`), and resolve that view back to Tenant through the existing 4.2 view machinery (the mapper `toTenantVTO(Tenant)` ties it). That is a real, structural recognition of an envelope-wrapped collection read of Tenant, without interpreting the wrapper's semantics.

But seeing the read buys nothing on its own, and this is the honest point: every pms envelope read that the tool would unwrap is a filtered read (`getAllByFilter`, `getMy*`, `getBuilding*`), so unwrapping the envelope lands exactly at the filter wall of part 2. Unwrapping envelopes only to refuse the filtered reads inside them adds no capability. There is not even a clean unfiltered-list-all hiding in the envelopes: the nominally unfiltered ones (`getAllTickets(pageNum, pageSize)`) return a view collection and page manually with `Integer` parameters, so they are view-returning and manually paged, not the clean `findAll` shape E4 models. Envelope-unwrapping is therefore its own prerequisite sub-phase, larger than it looks and worth building only after, and if, a representation for what it reveals exists. It is deferred with the rest of E6.

#### 4.11.3 Part 2: the filter-representation question, the three honest shapes weighed against the never-claim constraint

A filtered read can take only three shapes, and each is judged by the one test: does it ever present the filter's meaning as verified when it is not?

- REFUSE. Filtered reads are refused, loud and classified, and the ceiling is documented (section 4.11.5). This meets the constraint trivially, because it claims nothing. Its cost is coverage: the majority of both apps stays unreachable. It is the safe answer, and on the structural-only principle it is the correct one, because a filter's meaning is genuinely not structurally provable in the general case, and pms proves it (the meaning is in query-builder Java).
- MODELED-OPAQUE: expose the filter as a typed input whose name is copied but whose semantics are declared unclaimed in every surface. This is the path E4 rejected for single filters, reconsidered at scale, and it fails the constraint harder at scale, not softer. The copied parameter name lives in the structured tool schema, the part an agent acts on directly; a prose disclaimer lives in a description the agent may never read and an orchestrating agent certainly will not read across dozens of tools. The name is the claim, and a disclaimer beside a claim does not unmake it. The dilemma is unescapable: copy the domain name and you make an unverified claim; genericize it to `filter1` and the tool is useless. There is no honest and useful middle. pms makes it worse still: the tool cannot even write a truthful disclaimer, because it does not know what `fullName` filters. Rejected, permanently, on the constraint.
- HUMAN-ATTESTED: a person asserts a filter's meaning, and the tool records it as HUMAN-ATTESTED, a category explicitly distinct from tool-verified, and carries that distinction through to every consumer surface. This is the only option that can both add coverage and meet the constraint, because it never presents the filter as verified; it presents it as attested. But its architectural weight is real and must be paid honestly. It would be the project's first human-in-the-loop mechanism and the first time the tool carries a claim it did not prove. The zero-mis-bound record is a guarantee about tool-verified bindings; attestation introduces a human-error failure mode outside that guarantee, so the messaging must be exact: "never guesses" survives (a human asserted, the tool did not guess), but "structural proof or refusal" gains a third state, attested, that must be labelled as such wherever it appears, never blurred into verified. And it is not a discovery rule at all; it is a capability-authoring mechanism (an attestation format, a way for a human to author it, IR carriage, backend disclosure, and a verified-versus-attested marker on every surface), which is a different kind of design that deserves its own phase, not a rider on E6.

A structural exception, recorded precisely because it is easy to get wrong: Spring Data derived queries are the one place a filter's meaning is structurally verifiable, and reading them is not the nominal inference the earlier phases refused. The prior rule (never derive a query from a method name, section 4.10.7) is about arbitrary names: a method may be called anything, so `getActiveWidgets` proves nothing. But a Spring Data derived-query method name is a framework DSL, not an arbitrary name: the framework parses `findByNameContainingIgnoreCase` against the entity's `name` property and generates the predicate deterministically, and it rejects the method at startup if the property does not resolve. Reading it is reading a framework contract, the same class as reading `findById`. So a verified-derived-query filter is honest and structural. But it covers only derived queries: it does nothing for custom `@Query` filters (arbitrary JPQL) and nothing at all for pms (zero derived queries, all query-builder). It is a narrow structural sub-phase, not a solution to the majority, and it still needs the representation and the paging design that the rest of E6 needs.

#### 4.11.4 Recommendation: REFUSE for v1, defer E6 with the ceiling documented

Recommended, and ratifiable: for v1, the entire filter-semantics-plus-envelope pattern is REFUSED, and E6 is deferred, not built. The recommendation is not a failure to find a rule; it is the finding that a filter's meaning is not structurally provable in the general case, so the structural-only tool must refuse it, exactly as it refuses any evidence it cannot prove. The three options resolve as: MODELED-OPAQUE rejected permanently on the never-claim constraint; HUMAN-ATTESTED recorded as the one honest way to represent an unverified filter, deferred to its own future phase because it is a human-in-the-loop authoring mechanism and a real departure from structural-only, not an E6 matching rule; verified-derived-query filters recorded as the one narrow structural avenue, an erp-only minority, deferred as its own sub-phase; and envelope-unwrapping recorded as a prerequisite that pays off only once a representation exists, deferred with the rest.

Filtered, projected, and envelope reads continue to be handled exactly as they are today: named deferrals in the survey (section 4.10.6; Volume 11 section 3.3), never emitted as capabilities, never mis-bound. No IR growth, no new discovery rule, no backend change. The deferral is the behavior, and it is already correct.

#### 4.11.5 The coverage ceiling, stated plainly per app

This is the honest boundary of what the structural-only tool addresses, and it is large. It is a finding about addressable scope, not a defect.

- pms-backend: discovered 2 (getUnit, getLease). The ceiling holds essentially the entire rest of the read API: roughly 42 list reads (35 `*ResultSet` filtered and envelope-hidden, 7 `List<VTO>` foreign-key filtered), the filtered and non-primary-key by-id reads (`getAllByFilter`, `getMyUnit`, `getMyActiveLease`, `findByUserId` shapes), and Tenant's inner-fetch read (P1b-refused). pms is the app that proves the ceiling: its idiom is filtered and envelope-wrapped end to end, and structural-only discovery reaches almost none of it.
- erp-backend: discovered 25 (16 by-id, 9 list-all). The ceiling holds roughly 44 deferred reads: roughly 30 filtered (derived-query and custom-`@Query`), roughly 9 projection or view lists, 5 transformed-body reads, and the view-returning custom-query reads (Delivery). Of erp's ceiling, only the derived-query subset is even a candidate for the narrow structural exception; the `@Query` and transformed reads are not.

The one-line honest statement of the tool's reach: the structural-only tool tops out at unfiltered reads, get by primary key and list all. Filtered, projected, and envelope reads, the majority of both real apps, are beyond it without either human attestation (any filter) or a framework-contract derived-query rule (the derived-query minority only), and both are future.

#### 4.11.6 Reopening triggers

The question is reopened, as its own designed phase with ratification records first (rule 12), under any of:

- Human-attested capabilities are adopted as a product direction. This is the only path that unlocks the majority, and it is a deliberate architectural decision (the first carried-but-unproven claim), not a matching-rule extension. Its design session must define the attestation format and the verified-versus-attested carriage before any code.
- A narrow structural win is wanted sooner: verified Spring Data derived-query filters, on the evidence that enough clean derived queries exist to justify the derived-query grammar parser and a filtered-read representation. This is honest and structural but erp-shaped and partial, and it must still answer the paging and representation questions.
- Envelope-unwrapping is wanted independently, once either of the above provides something to represent. On its own it reveals only reads the tool would then refuse, so it is never built first.

Until one of these triggers, E6 stands as DESIGNED, DEFERRED WITH A DOCUMENTED CEILING. No fixtures and no forecast are pre-registered, because no buildable discovery rule is recommended; the deliverable is this ceiling and these triggers.

#### 4.11.7 E6a: the envelope transparency rule (visibility, not discovery; designed 2026-07-02; CONSOLIDATED into the post-v1 Attestation phase, Volume 13; BUILT 2026-07-24 as Attestation sub-phase AT-0)

E6a is the first half of the ratified E6 split: it makes a read that hides behind a wrapper or envelope return type VISIBLE AND CLASSIFIED, so it can pass through the existing rules and be counted, rather than being skipped unseen at the return-type gate. The framing is the whole point and is stated first: E6a changes a read from invisible to visible and classified; it does NOT grant discovery. Once a read is visible, it still passes through 4.1, 4.2, 4.10, and P1b, and the E6b-pending filter refusal, exactly as any other read. A filtered read seen through a wrapper is still refused as filtered, now visibly and countably instead of hidden. The expected outcome is therefore many newly visible reads, mostly refused as filtered, few or zero new discoveries, and a failure ledger that becomes complete. E6a is not designed toward discoveries.

AS-BUILT NOTE (AT-0, 2026-07-24, recorded per rule 13). The rule below is built in `ListReadMatcher` exactly as its conditions state, with two implementation facts recorded so the ratified text and the built behavior do not silently diverge. First, cardinality (V, the list-vs-by-id routing) is decided from the repository method's DECLARED return type, not from the framework read name: pms's by-id reads use `selectById`, not the framework `findById`, so a name-only test wrongly surfaced them as opaque list-deferrals. A non-collection access is left to the by-id path (4.1/4.2/P1b) and never surfaced here; only a collection access is classified by E6a. Second, the T2 body tie is resolved against DIRECT Spring Data repositories (the `catalog.springDataRepo` resolution); a collection access on a hand-written repository interface whose Spring Data delegate sits one hop away resolves to no in-tree entity and therefore refuses OPAQUE_WRAPPER rather than filtered-deferred. Extending T2's tie through the one-hop hand-written-interface delegation is a named, build-deferred refinement: no benchmark case turns on it (pms's multi-read `*ResultSet` reads refuse on the more-than-one-access condition first, and its single-access hand-written-repo reads are the aggregate and proxy shapes a human attests under AT-2 and AT-3, not reads a wider tie should discover). The measured result is in docs/experiments/second-app-trial.md section 12 and third-app-trial.md section 24.

##### 4.11.7.1 The transparency census (read-only, both apps)

Full counts in the ledgers (pms docs/experiments/second-app-trial.md section 8.10; erp docs/experiments/third-app-trial.md section 16). What it establishes:

- pms wraps almost every list read in a per-entity `*ResultSet` type (`TenantResultSet`, `RentalPaymentResultSet`, and roughly thirty more), OpenAPI-generated, each declaring one collection field (`List<VTO> data`) and a `Long total`. None is generic (there is a single generic `ListResultSet<T>` in the common library, but no read returns it). Every such read builds the wrapper in its body from a persistence access on a repository of the entity that returns a collection of it: `List<Tenant> tenants = tenantRepository.selectAllByFilters(filter); ... return mapper.toTenantResultSet(tenants, count);`. So the read of E is provable from the body's repository access, not from the wrapper. Every one of these reads is filtered (`selectAllByFilters(filter)` with domain parameters).
- erp uses no wrapper types: its reads return `Page<E>`, `List<E>`, or `Page<NameIdDTO>` directly, all already recognized (or deferred by name) by 4.10. E6a is inert on erp.

##### 4.11.7.2 The rule

A method whose declared return type W is not already recognized (not E, not a view under 4.2, not a recognized collection container under 4.10) is made visible as a read of E, and handed to the existing rules, when transparency holds. Transparency is one of two structural ties to E, and nothing else:

- T1, generic tie: W is a generic type and one of its type arguments is E (an in-tree `@Entity`) or a view of E (a type that resolves to E through the 4.2 view machinery). The wrapper's payload is E, provable from the type argument alone, without reading W's fields. This covers `Wrapper<E>` and `ListResultSet<TenantVTO>`.
- T2, body tie: the method body contains exactly one persistence access on a repository-typed field (the 4.2 repository resolution), and that access's declared return type is E, `Optional<E>`, or a collection of E (`List<E>`, `Set<E>`, and so on). The read of E is proven by the repository access's return type, without reading W's fields. This covers the pms `getAllByFilter`/`selectAllByFilters` pattern.

When T1 or T2 holds, the method is visible as a read of E and is routed to the existing rules unchanged (condition V below). When neither holds, the wrapper is opaque and the read is refused (condition O below).

- V, visibility is not discovery (the crucial non-grant). Once transparent, the read is classified by the existing rules with no modification and no precedence: if the tie is to a collection of E, the method is a list-read candidate and 4.10's conditions apply in full, so a filtered or transformed body (which every pms wrapper read has) is deferred as filtered exactly as any other list read; if the tie is to a single E, the method is a by-id candidate and 4.1, 4.2, and P1b apply unchanged. E6a adds no discovery rule and bypasses no honesty rule. The only thing E6a changes is that the read is now seen and counted; whether it discovers is decided entirely by the rules it is handed to, and for a filtered read the answer is refusal.
- O, opaque refusal. A wrapper return type for which neither T1 nor T2 holds, its tie to E not structurally provable, is refused with a located cause, `OPAQUE_WRAPPER`, naming the wrapper type and stating that the read's existence cannot be proven without reading the wrapper's internals or trusting its name. This covers a wrapper built from a service-to-service proxy call whose delegate is unparseable or returns a proxy type with no repository-of-E tie. The read stays invisible-by-refusal: counted, never guessed.

##### 4.11.7.3 What E6a will never do

- Never read the wrapper's fields or interpret its contents or semantics. The tie to E is the type argument (T1) or the repository access's return type (T2), never the wrapper's internal shape.
- Never assume a wrapper wraps E from its name: `TenantResultSet` is not trusted to wrap Tenant by its name; the tie must be T1 or T2. A `TenantResultSet` whose body reads a repository of Building is a read of Building, not Tenant.
- Never unwrap a wrapper whose tie to E is not structurally provable: that is `OPAQUE_WRAPPER`, a refusal.
- Never grant discovery or bypass a filter refusal. Visibility is not discovery; a filtered read seen through a wrapper is still refused (filtered).
- Never make a read of the wrong entity visible: T1 and T2 tie to exactly the E proven by the type argument or the repository access, so a wrong-entity mis-classification cannot occur.

Collections through wrappers (a wrapper of a list of E) are in E6a scope via T1 or T2 (the dominant pms shape). A wrapper of a wrapper, or one whose payload resolves to E only through several hops, is a named deferral, out of E6a scope, decided by the census if it ever appears.

##### 4.11.7.4 Pre-registered fixtures (for the eventual build)

- E6a-1 (transparent generic that then discovers): a method returning `Box<E>` (generic, E the type argument) whose body is a clean framework `findAll` of E. Unwraps via T1 to a list read and discovers `list<E>`. This proves visibility can lead to discovery when the underlying read is clean; the benchmarks contain no such case, which the forecast states.
- E6a-2 (transparent body, filtered, still refused, the common case): a method returning `EResultSet` (non-generic) whose body is `List<E> xs = eRepo.selectAllByFilters(filter); return map(xs);` with domain parameters. Visible via T2, then refused as a filtered list read (4.10 condition 3). This is the fixture that proves visibility without discovery.
- E6a-3 (opaque, refused): a method returning `EResultSet` whose body has no provable repository-of-E access (built from a proxy call to an unresolvable delegate). Must refuse with `OPAQUE_WRAPPER`.
- E6a-4 (name trap): a method returning `TenantResultSet` whose single persistence access is on a repository of Building. The tie is to Building, never to Tenant; the wrapper name carries nothing. Must classify as a read of Building (and then refuse it if filtered), never Tenant.
- E6a-5 (mis-classification guard): a transparent wrapper read is classified as a read of exactly the entity its type argument or repository access proves, never a sibling; and a transparent-but-filtered read never appears as a discovery.

##### 4.11.7.5 Forecast (visibility gained, not discoveries gained)

Stated before the build, framed as visibility, not discovery (full number-by-number in the ledgers):

- pms: roughly 12 `*ResultSet` reads move from invisible to visible, the ones in the three parseable modules (property, lease, billing). Every one is filtered, so all roughly 12 become VISIBLE-BUT-REFUSED-FILTERED, named deferrals in the survey. New discoveries: 0. The other roughly 23 `*ResultSet` reads sit in the ten unparseable placeholder-file modules (vendor, user, staff, notification, maintenance, inspection, file, community, chat, admin); they stay invisible by parse skip, which is not E6a's scope but the residue of the genuinely invalid source E1 already accounts for. The existing discoveries (getUnit, getLease) are byte-identical; mis-bound 0. Named uncertainty: the roughly 12 depends on how many `*ResultSet` reads sit in parseable service files; it is bounded by the property, lease, and billing modules.
- erp: byte-identical. No wrapper return types exist, so E6a recognizes nothing new; discovered 25, mis-bound 0, unchanged.
- The headline is visibility gained (roughly 12 on pms), discoveries gained 0. The failure and deferral ledger becomes more complete: pms's hidden wrapper reads are now counted as filtered deferrals rather than silently unseen, which is the correct and honest result. E6a confirms structurally what the E6 census asserted, that pms's wrapper reads are all filtered, which is exactly why E6b's filter decision is the one that matters, and E6a on its own moves no discovery number.

This is the honest close of E6a's design: it is a visibility phase whose expected and correct benchmark result is many newly visible reads, all refused as filtered, and zero new discoveries. It feeds the E6b, human-attested case directly: where a wrapper is opaque and the tool cannot even prove a read exists, a human could assert what it returns, which the tool cannot.

### 4.12 Entity identity: qualified keying and display-name disambiguation (phase Entity Identity; designed and ratified 2026-07-13, built 2026-07-13)

This is the provider-side realization of the identity split ratified in Volume 4, section 3.7: the Spring provider assigns each entity a qualified `key` and derives its display `name` from that key, disambiguating only where simple names collide. It is designed from the private enterprise benchmark. Per rule 12 the frozen rule was committed first and the build followed in a later session, implementing this text exactly. The build is recorded across the three benchmark ledgers (docs/experiments, the Entity Identity `actual` sections): both public benchmarks are byte-identical in generated output (proven by a `diff -r` against the pre-phase commit, zero differences), and the private enterprise benchmark moved from BLOCKED (3 duplicate-name validation errors, no output) to EMITTED (valid, 4 capabilities, both backends generate) with its discovered count unchanged at 4 and mis-bound 0. The fixtures EI-1 to EI-15 (section 4.12.6) are all green.

#### 4.12.1 Evidence (read-only, the private enterprise benchmark)

A real multi-module, bounded-context Spring application was surveyed (structural counts only, the app is private): 169 Maven modules, 111 `@Entity` classes, 158 `@Service` classes, parsed clean (0 skipped by parse). Three simple names collided across six entities: each colliding name is declared by two distinct entity classes, in two distinct modules, mapped to two distinct database tables. In generic terms, a `Note` in a crm context and a different `Note` in a support context, distinct classes with distinct fields and distinct tables. The tool keyed entities by simple name (the original `name` field, Volume 4 rule 1), so it saw three duplicate names, failed validation, and wrote no IR: no MCP server, no OpenAPI document, nothing, for the entire application. This is the only total, output-blocking failure the benchmark surfaced. Crucially it is not a discovery gap and not a mis-bind: the survey still classified all 111 entities correctly (4 discovered, 107 refused, mis-bound 0), and fail-loud caught the collision rather than merging the two entities. But nothing could be emitted, which gates every other improvement.

#### 4.12.2 The keying rule

Every `@Entity` E gets a `key`: its fully-qualified class name, package plus simple name, dot-separated, exactly as the source declares it. It is the entity's identity.

Scope, stated to disambiguate an overloaded word. A whole multi-Maven-module application scans into a single `CapabilityModel`; the key-uniqueness and display-name-uniqueness checks are over that one model. "Unique within the module" in Volume 4 rule 1 means within that one `CapabilityModel` (the IR scope), not a Maven module. The collisions this phase handles are precisely simple names reused ACROSS Maven modules within the one model.

1. The IR field is neutral (Volume 4, section 3.7): a provider-unique qualified string. The Spring provider derives it from the Java fully-qualified class name, which JavaParser already resolves from the package declaration and the class name. For a nested entity class the key is the canonical FQCN including the enclosing type (`a.b.Outer.Inner`), so the key the provider mints and the FQN the resolver derives from an import of that nested type agree. No normalization beyond that: no generics (an entity is a plain class), no array marks, no case folding. A future provider derives its own qualified identity (a database provider: schema plus table).
2. Genuine-duplicate check (identity): two entities with the same `key` are, in the ordinary case, the same source class seen twice, a real error that fails loud with a located message naming the key and both source files, exactly as the pre-phase rule failed on a duplicate name. Keying by qualified name does not weaken this; it narrows it to real duplicates.
3. Split-package hazard, named honestly. Two DISTINCT entity classes can in principle share the same package and simple name in two different Maven modules (a split package), yielding identical keys for distinct classes. The FQCN alone cannot tell this from a real duplicate. So the provider distinguishes by source file: if two entities have the same key but come from different source files, it reports a located split-package clash, worded distinctly from a true duplicate, and does not silently merge them. Folding the Maven module coordinate into the key to make such a pair distinct is a noted option, deferred unless a real app needs it (the benchmark's collisions all had diverging packages, so this was not observed). Either way the never-merge property holds: a same-key pair fails loud, it is never collapsed.
4. A collision of simple names with DISTINCT keys (the benchmark's case) is not a duplicate. The two entities are distinct, both retained, and disambiguated for display by section 4.12.3.

#### 4.12.3 The display-name rule (the heart)

The display `name` is what every consumer surface derives from (the capability name `get<name>`, the MCP tool name, the OpenAPI schema and path, Volume 6, section 3.4). It must stay the clean simple name wherever it can, disambiguate readably and deterministically where it can, and fail loud rather than ever let two entities share a display name.

1. Unique simple name: if E's simple name is unique across the whole model, its display `name` is exactly that simple name. No prefix, no change. This is the hard no-regression clause: any application with no colliding simple names, including both public benchmarks (pms-backend, erp-backend), keeps every simple name and generates byte-identical output whatever its module count. The build verifies this against the existing goldens.
2. The segment transform, defined precisely (determinism and collision-freedom both rest on it). A disambiguating prefix is built from package segments; the transform from a package segment to a display token is: `token(segment)` upper-cases the segment's first character and leaves every other character unchanged (internal case, digits, and underscores preserved). It is deterministic. It is NOT injective (two segments differing only in the case of their first character, `order` and `Order`, both map to `Order`), so the rule never assumes distinct segments yield distinct tokens; any resulting name collision is caught by the global-uniqueness check (condition 4) and resolved by expansion or a loud failure, never a silent merge.
3. Colliding simple name, the depth-parameterized candidate (a pure function of E's key and a depth d, never of peers' in-progress names). If a simple name S is shared by two or more entities with distinct keys, disambiguation draws on E's package segments in a fixed order of relevance:
   a. Split each colliding key into package segments then the simple name. Compute the colliding set's longest common leading package prefix. E's qualifier sequence is its segments that diverge from that common prefix (from the boundary toward the class name, the context-carrying segments), followed, if more qualification is ever needed, by the common-prefix segments toward the root. This ordering is a pure function of the key set.
   b. `Candidate(E, d)` for d >= 1 is the first d qualifier segments, each passed through the transform (condition 2) and assembled in source (package) order, prepended immediately before S. `Candidate(E, 0)` is the bare simple name S. Larger d is a longer, more-qualified name; the maximum d is E's package length. The candidate is DEFINED at every depth, including the fold case (condition 4e), so there is no undefined-behavior gap.
   c. Zero-diverging case: an entity whose package equals the common prefix exactly (a strict prefix of its peers') has no diverging qualifier, so it starts at d = 0 (bare S) and its peers disambiguate around it. At most one colliding entity can be here (two would be identical keys, failed at 4.12.2). Its bare S is still subject to the global fixpoint (condition 4), so a bare name that clashes globally is expanded like any other.
   d. Worked example. Keys `example.crm.model.entity.Note` and `example.support.model.entity.Note` share `example`; the diverging qualifiers `crm` and `support` transform to distinct tokens, so d = 1 yields `CrmNote` and `SupportNote`, capabilities `getCrmNote`/`getSupportNote`, components `CrmNote`/`SupportNote`.
4. The deterministic batch fixpoint over the WHOLE model (this replaces the first draft's order-sensitive per-entity loop and is what makes condition 7's determinism true; CLAUDE.md rule 10 demands it). Display names are assigned by a simultaneous fixpoint over the frozen key set, not a sequential walk:
   a. Initialize. Every uniquely-named (immovable) entity holds its simple name and never changes. Every colliding (movable) entity E is placed at depth `d(E) = 1`, or 0 if it is the zero-diverging entity (condition 3c).
   b. Each round, recompute every entity's current display name `Candidate(E, d(E))` from the frozen keys and current depths. Find every group of two or more entities sharing a current name. In each such group, every MOVABLE entity increments its depth by one; immovable entities never move. The increments are computed from the round's frozen state and applied simultaneously, so the result does not depend on any processing order.
   c. Repeat until a round makes no change (ACCEPT: all display names are globally unique) or some movable entity is already at its maximum depth and still shares a name (FAIL LOUD: a located display-collision error naming the clashing entities and their keys). Rounds are bounded by the longest package length, so it terminates.
   d. Order-independence, the point of the batch form. Each round's expansion set and each new depth are pure functions of the previous round's state, and the initial state is a pure function of the key set, so the whole assignment, INCLUDING whether the model emits or fails loud, is a pure function of the key set. Two mutually-colliding movable entities both expand in the same round, so no scan order can change the outcome. This closes the order-dependence the second review found (where a sequential loop could flip a whole model between emit and fail-loud with filesystem enumeration order).
   e. The transform-fold case (condition 2). If two colliding keys' qualifier tokens are identical up to some depth (the transform folded distinct segments, `order` and `Order` both to `Order`), their candidates stay equal through those depths; the fixpoint keeps expanding both until a later qualifier separates them or both reach maximum depth still equal, at which point it fails loud (fixture EI-10). Because `Candidate` is defined at every depth (condition 3b), the fold is handled by expansion-or-fail-loud, never by undefined behavior.
5. Never a numeric suffix. `Note_2`, `Note1`, and the like are rejected: unreadable to an agent, unstable, and structurally meaningless. The disambiguator is always structural context (package segments); when structural context cannot separate two names, the answer is a loud failure (condition 4c), not an invented suffix.
6. Never rename a non-colliding entity. Only a colliding (movable) entity ever changes; a uniquely-named entity is immovable and keeps its simple name unconditionally. When a movable entity's name clashes with an immovable one, the movable entity expands or, at maximum depth, fails loud (condition 4c); the immovable one never moves.
7. Determinism and stability. The batch fixpoint (condition 4) is a pure function of the full set of entity keys, so the same source yields the same display names, and the same emit-or-fail-loud outcome, on every run: golden-stable and order-independent, satisfying CLAUDE.md rule 10. Honest stability note: adding or removing an entity can change the disambiguated names of entities it collides with, or move the model across the emit/fail-loud boundary (a visible, golden-caught diff); it never affects an entity whose simple name is unique and does not collide.
8. The failure boundary, stated honestly and now decidable (this corrects the first draft, which wrongly claimed distinct keys always disambiguate). Distinct keys USUALLY disambiguate to distinct readable names, but not always: a movable entity can reach maximum depth still sharing a name, with an immovable unique name it cannot out-qualify, or a peer whose qualifier tokens fold identically, and then the model fails loud with a located display-collision error. Under the batch fixpoint (condition 4) this emit-or-fail-loud outcome is a pure function of the key set, so the boundary is decidable and order-independent. There is no silent merge and no guess; identical keys are the separate genuine-duplicate failure (4.12.2). Accordingly the forecast (4.12.7) admits an app CAN stay IR-blocked on a residual display collision; the private benchmark's three collisions have cleanly diverging packages with no residual, so it moves to emitted, which the build confirms.

#### 4.12.4 Resolving entity-typed references and matches by key (supersedes the by-simple-name clauses)

Once entities are keyed, every place the provider ties a Java type to an entity must resolve to the entity's KEY, not to a simple-name string match, or a colliding simple name would either fail to match its own entity or match both (a silent mis-bind). This subsection defines the resolver and states exactly which earlier clauses it supersedes. It is the load-bearing correctness step: the phase's mis-bound-0 claim rests on it, not only on the keying and display split.

1. The resolver: a type-agnostic scoping stage, then a typed match. Resolving a Java type reference T (a field type, a service method's return type, an enum type, a list element type) has two stages. First, resolve T to a single declaring fully-qualified name by Java's own name resolution against the reference's compilation unit:
   0. if T is written fully qualified in source (`private com.x.order.Note offer;`), that FQCN is the resolution directly;
   a. else a single-type import of T (`import a.b.Note;`) resolves T to `a.b.Note`;
   b. else a type named T declared in the SAME package as the reference;
   c. else, if exactly one on-demand (wildcard) import `import a.b.*;` reaches an in-tree type `a.b.T`, that one (this assumes source that compiles; a wildcard reference a Java compiler would call ambiguous reaches more than one type and so refuses at step d);
   d. else T is ambiguous, two or more in-tree types of that simple name are in scope or none is: the provider refuses loud, naming the reference and the candidates. It never picks a default.
   Second, MATCH the resolved FQCN against the declaration set the caller needs: an entity reference (sections 4.1, 4.2, 4.4, 4.10) matches the entity whose `key` equals the FQCN; an enum field (section 4.5) matches the in-tree ENUM declaration at that FQCN. Enums are not keyed (only `@Entity` classes are, section 4.12.2), so enum resolution narrows to one enum declaration by FQCN, the same scoping applied to a different declaration set, and refuses when two same-named enums are in scope. This is the never-guess bar: the resolver yields exactly one declaration or refuses; it never resolves a colliding simple name to a wrong-but-valid one, which would pass validation and ship as a silent mis-bind.
2. What this supersedes. Entity Identity supersedes each of the following simple-name (or last-segment) matches with the resolver above; each stays byte-identical when the simple name is unique:
   - the capability-NAME mint: section 4.1 mints the read intent `get<Entity>` and section 4.10 mints `list<E>` from the entity's simple name at extraction. They now mint from the entity's DISPLAY name, computed after the global disambiguation fixpoint (section 4.12.3, condition 4), so two colliding entities become `getCrmNote`/`getSupportNote` (and `listCrmNote`/`listSupportNote`), never two `getNote`, which would fail validation rule 2 and re-block output. Byte-identical when the simple name is unique;
   - the binding MATCH: sections 4.1 condition 3 and 4.2 condition 3 ("M's return type, by simple name, equals E's name") now hold when M's return type resolves, by the resolver, to the entity whose key equals E's key (fixtures EI-1, EI-6);
   - the type resolution: sections 4.4 condition 2 (entity field), 4.10 condition 2 (list element type), and 4.5 condition 2 (enum field) resolve by the resolver, not by last segment. An entity reference that cannot be narrowed to one key, or an enum reference that cannot be narrowed to one enum declaration, refuses loud rather than picking one of two same-named types.
3. Emitting the reference. Having resolved to one entity by key, the provider emits that entity's display `name` in the `ENTITY_REF` or `Relation`, so the reference token's shape is unchanged (Volume 4, sections 3.5 and 4) and, when the resolved name is a unique simple name, byte-identical to today. A resolved cross-context reference emits the correct disambiguated name, `ENTITY_REF(CrmNote)`, never the other context's entity.

Parallel hazard, now made loud: service-class simple names. The binding string stays `<ServiceClassSimpleName>#<methodName>` (section 4.1), which keys the runtime's bean resolution on the service class's SIMPLE name. On a bounded-context app two service classes can share a simple name just as two entities can, so two distinct capabilities could carry the SAME binding string. Rather than let that ship as a silent runtime mis-bind, core validation gains a binding-uniqueness rule (Volume 4, section 5, rule 7): if two emitted capabilities carry the same binding string, validation FAILS LOUD with a located message, consistent with the provable-or-refuse ethos. So the phase does not silently emit an unprovable binding; it refuses. What it does NOT do is RESOLVE the ambiguity by qualifying the binding target (a separate follow-on, if evidence shows a real service-name collision that two same-simple-name beans would otherwise resolve). The mis-bound-0 strengthening is therefore: entity identity is fully resolved, and the service-class hazard is converted from a possible silent mis-bind into a loud refusal, not left open.

#### 4.12.5 What this rule will never do

It never renames a uniquely-named entity; it never emits a numeric or scan-order-dependent disambiguator; it never merges two distinct keys into one entity, and it never lets two distinct entities share a display name (a residual display collision fails loud, condition 4c, it does not merge); it never resolves an ambiguous cross-context type reference by guessing. It relaxes no matching CONDITION and adds no discovery rule; what it changes is that entity-reference and match resolution move from simple-name string equality to key-based resolution (section 4.12.4), which is byte-identical to today except on a collision. It does grant EMISSION where a collision previously blocked it, so a colliding app with two clean by-id reads goes from zero emitted capabilities to two, but the per-entity DISCOVERY count is unchanged: raising it is the separate, evidence-ranked discovery-gap phase (Volume 13), whose shapes (the private-helper hop, the interface-to-implementation resolution, the inherited `@Id`) are explicitly not in this phase.

#### 4.12.6 Pre-registered fixtures (EI-1 to EI-15)

- EI-1 (happy path): two entities same simple name `Note`, different packages/keys, each with a clean by-id read; both discover, producing distinct `getCrmNote`/`getSupportNote` and distinct components `CrmNote`/`SupportNote`.
- EI-2 (no regression): a no-collision model (single or multi context); every entity keeps its simple name, MCP manifest and OpenAPI byte-identical to pre-phase (`getProduct`, component `Product`).
- EI-3 (identity guard): the same qualified key declared twice; validation fails loud with a located message naming the key.
- EI-4 (unambiguous cross-context reference): a field typed `Note`, single-type-imported from the crm package, resolves to `ENTITY_REF(CrmNote)`, never the support `Note`.
- EI-5 (ambiguous reference refuses): an unqualified `Note` reference the resolver cannot narrow to one key refuses loud, naming the candidates; no reference formed.
- EI-6 (mis-bound guard, discovery): two same-named entities never merge; each `get<name>` binds to its own entity's method resolved to its own key; a service method returning one context's `Note` is not a candidate for the other.
- EI-7 (deeper-divergence disambiguation): three entities share a simple name, two sharing their first diverging segment; per-entity minimal expansion (a pure function of the key set) yields three distinct readable names, no numeric suffix.
- EI-8 (global collision vs a unique name, resolved by expansion): a disambiguated name would equal a distinct unique entity's simple name, but the colliding entity has a further package segment; global reconciliation (condition 4b) expands it to a distinct name; the unique entity is never renamed.
- EI-9 (global collision, unresolvable, fails loud): a disambiguated name equals a distinct unique entity's simple name and the colliding entity has no further segment; the provider fails loud with a located display-collision error naming both keys, never merging and never renaming the unique entity.
- EI-10 (transform collision fails loud): two colliding keys whose diverging segments differ only where the token transform folds them (for example `order` versus `Order`, or `order_v2` versus `orderV2`) and no further segment separates them; the global check catches the identical tokens and fails loud, it does not silently merge.
- EI-11 (unqualified same-package reference, correct bind): an entity-typed field with no import whose type is declared in the same package resolves by same-package scoping (resolver step b) to the correct key; no false refusal.
- EI-12 (wildcard-only colliding reference refuses): a field typed by a simple name reachable only through a wildcard import that could match more than one in-tree entity key refuses loud (resolver step d), rather than guessing.
- EI-13 (zero-diverging-segments): two colliders where one's package is a strict prefix of the other's; the prefix-package entity keeps the bare simple name and its peer disambiguates around it, deterministically.
- EI-14 (mutually-colliding movable entities, batch determinism): two distinct collision sets whose disambiguated names transiently clash across sets, with one member holding a zero-diverging bare name; the batch fixpoint (condition 4) expands the movable entities simultaneously to a deterministic, order-independent assignment, and a variant where a blocking unique name forces a deterministic fail-loud. Proves the emit-or-fail-loud outcome does not depend on scan order (CLAUDE.md rule 10).
- EI-15 (split-package clash): two DISTINCT entity classes with the same package and simple name in different source files (identical keys); the provider fails loud with the split-package-worded message, distinct from the same-file genuine duplicate (EI-3), and never merges them.

#### 4.12.7 Forecast

The three-benchmark forecast, number by number, is in the ledgers (docs/experiments/second-app-trial.md and third-app-trial.md, the Entity Identity forecast sections; the private benchmark by structural counts and shapes only). In summary: both public benchmarks are byte-identical in generated output (no colliding simple names in either; any collision found at build is a regression finding), and the private enterprise benchmark moves from IR-BLOCKED to IR-EMITTED with its discovered count unchanged, because raising that count is the separate discovery-gap phase. One honest caveat from the failure boundary (conditions 4c and 8): this phase does not guarantee every colliding app becomes emittable. An app with a residual display collision (a disambiguated name that cannot be out-expanded to a globally-unique readable name) stays IR-blocked, now with a precise located display-collision error instead of the generic duplicate-name failure. The private benchmark's three collisions have cleanly diverging packages and no residual clash, so it moves to emitted; the build confirms. This phase's win is that the tool can emit output for a real multi-module application at all, and, where it structurally cannot, it says exactly why.

### 4.13 Discovery-gap rules: the reads the current matcher misses, classified general versus foundation-specific (phase Discovery-Gap; designed and ratified 2026-07-13, BUILT 2026-07-13, general scope only)

Build status (2026-07-13). The two general rules are built exactly as ratified: the inherited-`@Id` eligibility rule (4.13.2) and the general core of the private-helper hop (4.13.3). NO port-to-implementation rule was added, because 4.13.4 designs none; its foundation-specific instances stay refused, and the build proves it (fixture DG-5). The forecast held to the number on all three benchmarks (4.13.7): both public benchmarks byte-identical (proven by a `diff -r` of the generated MCP and OpenAPI output against the pre-build commit, zero differences across all files), and the private enterprise benchmark discovered 4 to 4 with NO_IDENTIFIER 10 to 0 and NO_BACKING_METHOD 96 to 106, mis-bound 0. Both rules fired and both refused exactly where the design said they would: all 10 inherited identifiers resolved (and all 10 entities then failed NO_BACKING_METHOD, having no by-id read), and all 3 private-helper hops were reached and refused on their soft-delete guard. The private benchmark's generated output is byte-identical to the pre-build output, so the 4 discovered bindings are the same 4: nothing was added and no binding moved.

Two diagnostic-only amendments are flagged here (CLAUDE.md rule 13), neither of which changes a matching outcome:

1. Condition 5 said an unmappable inherited `@Id` is "reported with the UNMAPPED_FIELD_TYPE cause, exactly as a direct unmapped `@Id` is." The second clause is the binding one and the cause name was wrong about existing behavior: a direct unmappable `@Id` is reported NO_IDENTIFIER (section 4.1), not UNMAPPED_FIELD_TYPE. The build therefore mirrors the direct case exactly, NO_IDENTIFIER with a located message naming the declaring ancestor. Using UNMAPPED_FIELD_TYPE would additionally have been a regression: that cause aborts the strict `provide` path, so an entity with an unmappable inherited id would have failed a whole scan that a direct one does not.
2. Condition 4 required the ambiguous-hierarchy case to be "a distinct failure" with "a located ambiguous-hierarchy message," without naming a cause code. It is reported under NO_IDENTIFIER (no single identifier could be determined) with its own located message, explicitly distinct from the composite-key message. The refusal itself is exactly as ratified.

This is the RANK 2 finding from the private enterprise benchmark (Volume 13, section 3; docs/experiments/enterprise-benchmark.md, section 5). The benchmark surfaced a set of by-id reads the current rules do not discover, in three recurring structural shapes. This section designs the response, before any code; per rule 12 the frozen rule is committed first and the build is a separate later session. Until ratification, no discovery-gap code is written.

The mandatory first step, done before any rule is designed, is an honest classification of each shape as GENERAL (a standard Spring/JPA pattern that appears across ordinary codebases independent of any foundation library, worth a real rule) or FOUNDATION-SPECIFIC (a shape that fires only because of how this project's own private foundation library structures its services, ports, and delegation, worth much less because the next real app will not share it, and a rule for it is benchmark-chasing) or MIXED (general in principle but the benchmark's instances are foundation-flavored). The classification, with source evidence from all three benchmarks, decides scope: real rules are designed only for the GENERAL shapes and the general core of MIXED shapes; FOUNDATION-SPECIFIC shapes get no matching rule and are recorded as named, deferred categories that stay refused.

The classification's headline, stated plainly because it is the finding: on this benchmark the general rules unlock ZERO new discoveries. The gap between the reads the benchmark leaves un-discovered and the zero a safe general rule reaches is itself the result. A full enumeration (4.13.7) puts numbers on it: of 39 in-scope by-id reads, 5 already fall under sections 4.1 and 4.2, leaving 34 un-discovered (6 soft-delete-guarded, 12 aggregate-enrichment, 15 delegation, 1 stub), plus 10 NO_IDENTIFIER entities that are internal subtype rows with no by-id read. (This full census supersedes the earlier, coarser figure of 23 from a 16-entity sample recorded in section 5 of the ledger; the shape of the finding is the same, the number is now exact.) It measures how much of this application's readability depends on its own foundation idioms and on internal non-read entities, versus standard Spring: on this app, almost all of it. The general rules are still correct and worth having (they would discover on a standard Spring app that has these shapes without the foundation flavor, as fixtures DG-1 to DG-3 encode), but on this benchmark they discover nothing, which follows the P1b precedent exactly (section 4.2.10): a rule ratified for the strict boundary it draws, its build deferred until an app exists on which it discovers.

#### 4.13.1 The classification, with evidence (read-only, all three benchmarks)

The three shapes the benchmark named, each traced to source and classified:

- Shape (c), inherited `@Id` via a mapped superclass or `@Inheritance` parent: GENERAL. Evidence: 10 entities are reported NO_IDENTIFIER because they have no direct `@Id`; each is an `@Entity` child that inherits a single `@Id` from an in-tree base one `extends` hop away. By parent kind: 4 inherit from an `@MappedSuperclass` carrying one `@Id`; 6 inherit from an `@Entity` parent annotated `@Inheritance(strategy = JOINED)` carrying one `@Id` (4 under one such base, 2 under a second). No composite parent, no parent outside the scanned source, no multi-hop, no ambiguous inheritance. Both `@MappedSuperclass` id inheritance and `@Inheritance(JOINED)` are core JPA, present in ordinary codebases with no foundation library involved. The public benchmarks confirm the shape is not universal (neither pms-backend nor erp-backend has any inheritance: all 43 pms and 25 erp entities declare a direct `@Id`, and neither tree contains a single `@MappedSuperclass` or `@Inheritance`), but where it appears it is standard JPA. Verdict: GENERAL, worth a rule.

- Shape (a), the private-helper hop: MIXED. Evidence: from a full enumeration of the 39 in-scope single-id by-id reads on the benchmark, the private-helper hop proper is 3 reads that delegate the load to a PRIVATE method in the same class, for example `Foo f = getFoo(id); return mapper.toFooVTO(f);` where the private `getFoo(id)` performs the actual repository load. The current section 4.2 refuses these because the public body performs no persistence access (the load is hidden in the private helper), so there is no load to verify. Following one private same-class delegation to reach the load is a general Java structuring pattern, and its general core (a public read whose sole indirection is a private same-class helper that does exactly the clean by-primary-key load) can be traced with the same disciplines section 4.2 already applies: a verbatim id argument, exactly one persistence access, no mutation, no second access, and the helper returns the entity with no transformation of its own. That general core is worth a rule. What is incidental, and decisive on this benchmark, is that ALL 3 of these private helpers carry a post-load soft-delete guard (`if (foo.getIsDeleted()) throw ...`) after the load, and a further 3 direct reads carry the same guard inline in the public body: the clean, unguarded helper-hop count is exactly 0. The helper exists precisely to hold that shared guard (it is reused by the entity's activate, deactivate, and delete commands), and the guard is pervasive across the application (roughly 59 guard occurrences across service bodies). Verdict: MIXED; design the general core, refuse the guarded instances (4.13.3), and expect zero discovery on this app because every instance is guarded.

- Shape (b), the port-to-implementation across module boundaries: NOT a general gap; the "across the module boundary" framing is a mischaracterization on this app. Evidence: the reads described as blocked by "an interface-to-implementation resolution across the module boundary" load through a hand-written repository port, an interface `FooRepository { Optional<Foo> selectById(id); }`, whose in-tree `@Repository` implementation does exactly `return jpaRepository.findById(id);` on a Spring Data repository of Foo. That is precisely the section 4.2 condition 4b one-hop trace, which the tool ALREADY handles when the implementation is in the scanned source. The census confirms the scale and the resolution: 81 hand-written repository ports, 80 with an in-tree `@Repository` implementation (56 of them carrying a verbatim by-primary-key `return jpaRepository.findById(id);`), the 81st an empty, unused stub interface with no implementation and no caller, and 0 whose implementation is outside the scanned source; and 131 service-port reads that the extractor already reaches because it enumerates `@Service` classes directly and never needs interface-to-implementation resolution for them. The "another module" of the finding is a Maven module INSIDE the scanned application (each domain is split into api, repository, core, and model modules), not a foundation jar: the provider parses every application module into one unit set, so a module boundary blocks no resolution. The public benchmarks agree (every hand-written `*Repository` and `*Service` port in pms and erp has its `*Impl` in the scanned source; the only implementation-less interfaces are Spring Data `*JpaRepository` types, which are the recognized framework contract, not a gap). The only genuine version of this shape is a port whose implementation is NOT in the scanned source, in a binary dependency the tool cannot read; a source-only tool with no bytecode analysis (the MVP boundary, Volume 16) cannot follow it, and the honest answer is that it stays refused. On all three benchmarks that case count is zero. Verdict: no new rule. The in-tree case is already covered by condition 4b and by direct `@Service` scanning; the not-in-tree case is a documented, unreachable boundary, not a shape a source-only matching rule can close.

A fourth family the benchmark surfaced, aggregate-enrichment reads (a clean by-primary-key load plus a second repository or proxy access plus a multi-argument composite mapper), is not a discovery-gap shape at all: section 4.2 already refuses it correctly (condition 6, the single-access body), and representing it honestly belongs to Attestation, not to a new matching rule. It is named here only to keep the accounting complete.

#### 4.13.2 The inherited-`@Id` eligibility rule (general shape c)

Eligibility (section 4.2, condition 0) is extended so that an entity's single `@Id` may be inherited, resolved structurally through the in-tree superclass chain. The entity E forms a read intent `get<E>(id)` when ALL of the following hold; discovery (sections 4.1 and 4.2) then runs over that intent unchanged.

1. E declares no direct `@Id` field and no `@EmbeddedId` (if it declares its own identifier, existing eligibility applies and this rule is not consulted).
2. E declares an `extends` clause whose superclass resolves to exactly one in-tree class P. Resolution uses only the SCOPING stage of the section 4.12.4 resolver (its Java name resolution against E's compilation unit to a single fully-qualified name, steps 0 to d), then matches that FQCN against the set of ALL in-tree class declarations, entities, mapped superclasses, and plain classes alike, not against the entity-key match set: a `@MappedSuperclass` (which 4 of this benchmark's shape-c parents are) is not an `@Entity` and is not keyed (section 4.12.2), so the entity-key match would wrongly miss it. The FQCN-to-declaration match is deterministic (FQCNs are unique) and keeps the same refuse-on-ambiguity bar. A superclass that does not resolve to an in-tree declaration (it lives in a binary dependency, for example a foundation library that is not in the scanned source) is unresolvable: E stays NO_IDENTIFIER with a located note ("E extends P, which is not in the scanned source; the inherited identifier cannot be read"). The tool never assumes an unseen parent carries an `@Id`.
3. Walk the WHOLE in-tree superclass chain from P upward (P, then P's resolved in-tree superclass, and so on), collecting every identifier declaration (`@Id` and `@EmbeddedId`) found on any ancestor. The walk does not stop at the first identifier, because deciding the identifier is unambiguous requires seeing the whole chain. Each hop is resolved the same way as condition 2 (the scoping stage, matched against all in-tree class declarations); the walk ends at the root of the in-tree chain or at the first ancestor that is not in the scanned source. If an identifier was found strictly below such a not-in-tree hop, the walk still has the complete in-tree picture; if none was found before the chain leaves the scanned source, E stays NO_IDENTIFIER (condition 2's refusal, the identifier cannot be read).
4. The collected identifier declarations across the whole walked chain are EXACTLY ONE `@Id` field and no `@EmbeddedId`. That single `@Id`, on whichever ancestor declares it, is E's inherited identifier. An `@EmbeddedId`, or more than one `@Id` field, on a single ancestor is a composite key: E is reported with the COMPOSITE_KEY cause (section 4.2, condition 0), never read by part of its key. Two different ancestors each declaring an `@Id` (an ambiguous hierarchy, or a diamond of `@MappedSuperclass` mixins) is a distinct failure, an ambiguous inherited identifier: E is refused with a located ambiguous-hierarchy message naming both declaring ancestors and their `@Id` fields, never resolved by guessing which `@Id` was meant. Both are refusals; neither is a genuine identifier the intent can use.
5. The single inherited `@Id` field's type maps, through the same Java-to-primitive mapping entity fields use, to a bindable primitive. A FLOAT inherited `@Id` forms no read intent (section 4.6, rule 4), exactly as a direct FLOAT `@Id` does. An inherited `@Id` whose type maps to no bindable primitive (an unresolved or unmapped custom type) is reported with the UNMAPPED_FIELD_TYPE cause, located at the inherited `@Id` field on its declaring ancestor, exactly as a direct unmapped `@Id` is, rather than left as a bare NO_IDENTIFIER.
6. The recovered identifier is E's own: it fixes E's read intent `get<E>(id)` and its id input type. It changes nothing about discovery. The backing method is still found by sections 4.1 and 4.2 over E, resolved by key (section 4.12.4), so a child in a JOINED hierarchy binds to its own read and never to the parent's or a sibling's. Recovering the identifier moves E from NO_IDENTIFIER to a read intent; whether that intent then discovers a capability or fails NO_BACKING_METHOD is decided by the unchanged discovery rules.

This is a pure eligibility widening: it lets the tool see an identifier the entity genuinely has (a standard-JPA inherited `@Id`) instead of wrongly reporting NO_IDENTIFIER, and it refuses every case it cannot read structurally. It adds no discovery power of its own and cannot bind anything; the mis-bound-0 guarantee is untouched.

#### 4.13.3 The private-helper-hop trace (the general core of shape a, strict)

Section 4.2's load (condition 4) is reached through one PRIVATE same-class helper, under the same disciplines, with a strict guard-refusal that keeps the never-claim-a-filter bar intact. A service method M backs `get<E>` through a private helper when ALL of the following hold, in addition to section 4.2 conditions 0 to 3.

1. M's body is one of section 4.2's two read shapes (condition 7), except that the "load" position is not a repository call but a call `H(<M's id parameter>)` to a method named H, taking M's single id parameter verbatim (condition 5).
1a. The helper resolves to exactly one declaration, by a never-guess resolver. The call `H(id)` must resolve, by Java overload resolution against the id parameter's static type, to EXACTLY ONE method declaration that is `private` and declared on M's own class (the same `@Service` class). This is the trace's one same-class method-call resolution, and it carries the same never-pick-a-default bar as the type resolver (section 4.12.4, step d): if the tool cannot pin a single most-specific private declaration (an overloaded or shadowed helper set it will not adjudicate, or an applicable set with no unique most-specific member), it refuses loud rather than reading one candidate's body. Only the single resolved declaration's body is read for conditions 2 and 3. This closes the hole where a clean overload's body is read while the id argument actually dispatches to a guarded one, which would ship a guarded read as a plain `get<E>`.
2. H is followed exactly ONE hop. The resolved H's body is exactly the clean by-primary-key load and nothing else: it performs exactly one persistence access, which is the section 4.2 condition 4 load (the framework by-primary-key read 4a, or the one-hop hand-written repository trace 4b), with the id parameter forwarded verbatim; its body is a single `return <load>;` or a local initialized from `<load>` and returned, where `<load>` is the persistence call followed only by Optional unwrapping (`orElseThrow(...)`, `orElse(...)`, `get()`). H returns E or `Optional<E>` and performs no mapping step of its own.
3. The strict extra-statement refusal. The resolved H's body contains NO statement other than the load and its Optional unwrap: no conditional before or after the load, no `if (...) throw`, no filter, no second access, no mutation. A guard in either position, the pervasive post-load soft-delete check `if (loaded.getIsDeleted()) throw ...` among them, disqualifies. The reason is the never-claim bar, not distaste for the guard: the tool cannot tell a benign soft-delete guard from an authorization guard (`if (loaded.getOwnerId() != caller) throw`) or a business-state filter (`if (loaded.getStatus() != ACTIVE) throw`) without interpreting the condition's meaning, which it must never do; some such guards hide rows per caller or per state, which would make `get<E>` a filtered read wearing a by-id read's clothes. The refusal is worded structurally, "private helper H contains a statement beyond the by-primary-key load and its Optional unwrap," so it names the defect (an extra statement) accurately whether the guard sits before or after the load, exactly as section 4.2 refuses any body that is not one of its two clean shapes.
4. Back in M, the value H returns is fed to exactly one resolvable mapping step (section 4.2 conditions 8 and 9) or returned directly (a section 4.1 entity-returning read reached through a private helper). Everything about the mapping, the verbatim single argument, the in-tree resolvable callee, the ENTITY_REF(E) output with the view recorded, is unchanged.
5. Exactly one private hop, no cross-class step. H must be private and on M's own class. A helper that is not private, or on another class, is not this shape: a cross-class hop through a hand-written repository interface is condition 4b (already handled); a cross-class hop to another service or a remote client is out of scope (section 4.2.6, delegation-only facades). A private helper that itself delegates to a further method is refused: the trace is one private hop and reads no deeper.
6. Pooling and key resolution are unchanged (section 4.2, condition 10; section 4.12.4). A helper-hop candidate joins the section 4.1 and 4.2 candidate pool with no precedence; exactly one candidate discovers, zero refuses, more than one is AMBIGUOUS_BINDING.

#### 4.13.4 Port-to-implementation: no new rule (the honest boundary)

No rule is designed for shape (b). Its in-tree form, a hand-written repository port with a single in-tree `@Repository` implementation delegating verbatim to a Spring Data by-primary-key read, is already section 4.2 condition 4b and needs nothing new; the multi-module scan already gathers every in-tree implementation across module boundaries into one model, so a module boundary blocks no resolution when the implementation is in the scanned source. Its only genuine form, a port whose implementation is not in the scanned source (in a binary dependency), is unreachable by a source-only tool: the MVP does no bytecode or reflection analysis (Volume 16), so it cannot read an implementation it cannot see, and it refuses with a located note ("the load resolves through port P whose implementation is not in the scanned source"). This is a documented boundary of a source-only tool, not a shape a matching rule can close by trying harder. Reopening it would require a bytecode-reading provider, which is a separate future provider (section 5), not a discovery-gap rule.

#### 4.13.5 What these rules will never do

- Never assume an unseen superclass carries an `@Id`: an `extends` to a class not in the scanned source leaves the entity NO_IDENTIFIER, it does not invent an inherited identifier.
- Never read by part of an inherited composite key: a `@MappedSuperclass` or parent with an `@EmbeddedId` or two `@Id` fields on one ancestor is COMPOSITE_KEY, exactly as a direct composite key is.
- Never guess an ambiguous inherited identifier: two ancestors each declaring an `@Id` is refused with a located ambiguous-hierarchy message, never resolved to the nearer one.
- Never read a private helper the call cannot pin to one declaration: an overloaded or shadowed helper set that Java overload resolution does not narrow to a single most-specific private method refuses loud, it never reads one candidate's body while the id dispatches to another.
- Never step into more than one private same-class hop, and never step into a cross-class method: one private hop, on the method's own class, or the shape does not apply.
- Never tolerate a guard, filter, mutation, or second access inside the private helper, in any position: it cannot verify such a guard is benign without interpreting it, so it refuses, keeping the never-claim-a-filter bar.
- Never follow a port whose implementation is not in the scanned source: a source-only tool refuses what it cannot read rather than guessing an implementation.
- Never relax section 4.2's disciplines or section 4.12.4's resolution: every new trace re-anchors identity to the key resolver (and the helper call to Java overload resolution) and pools under the no-precedence, refuse-on-ambiguity rule, so mis-bound stays 0.

#### 4.13.6 Pre-registered fixtures (for the eventual build, whenever it lands)

Positives, one per general rule, each on a standard-Spring shape without the foundation flavor:

- DG-1: inherited `@Id` via `@MappedSuperclass`. A child `@Entity` with no direct `@Id` extends an in-tree `@MappedSuperclass` declaring one `@Id`; a clean section 4.1 or 4.2 backing read of the child exists. `get<Child>` discovers, its id input the inherited `@Id`, binding the child's own method resolved by key.
- DG-2: inherited `@Id` via `@Inheritance(JOINED)`. As DG-1 but the parent is an `@Entity` annotated `@Inheritance(strategy = JOINED)` carrying the `@Id`. Discovers.
- DG-3: clean unguarded private-helper hop. A public read whose body is `Foo f = getFoo(id); return mapper.toFooVTO(f);`, where private `getFoo(id)` is exactly `return fooRepository.findById(id).orElseThrow(...);` (no guard), and the mapper resolves in-tree. Discovers, binding the public method, output ENTITY_REF(Foo) with the view recorded.
- DG-3b: entity-returning helper hop. As DG-3 but the helper returns Foo and the public method returns it directly (a section 4.1 read reached through a private helper). Discovers.

Foundation-specific and strict refusals, each asserting the located diagnostic:

- DG-4 (the guarded-helper refusal, the pervasive benchmark case and the most important refusal fixture): the DG-3 shape but the private helper adds `if (foo.getIsDeleted()) throw ...` after the load. Must REFUSE with the extra-statement message ("private helper H contains a statement beyond the by-primary-key load and its Optional unwrap"); `getFoo` is not emitted. A variant with the guard BEFORE the load must refuse with the same message, proving the wording is position-independent. This fixture encodes the strict extra-statement refusal and the reason the benchmark discovers zero from this shape.
- DG-5 (not-in-tree port): a read whose load resolves through a repository or service port with no in-tree implementation (the implementation is in a binary dependency). Must REFUSE with the "implementation not in the scanned source" note.
- DG-6 (inherited `@Id`, parent not in-tree): a child whose `extends` target is not in the scanned source. Must stay NO_IDENTIFIER with the "superclass not in the scanned source" note, never inventing an inherited id.
- DG-7 (inherited composite key): a child inheriting an `@EmbeddedId` or two `@Id` fields on one ancestor. Must report COMPOSITE_KEY; no read intent is formed.
- DG-7b (ambiguous inherited hierarchy): a child whose chain has two different ancestors each declaring one `@Id`. Must REFUSE with the ambiguous-hierarchy message naming both declaring ancestors and their `@Id` fields, distinct from the composite-key cause, never resolving to the nearer `@Id`.
- DG-7c (unmapped inherited id type): a child inheriting a single `@Id` whose type maps to no bindable primitive (and is not FLOAT). Must report UNMAPPED_FIELD_TYPE located at the inherited `@Id`, exactly as a direct unmapped `@Id` is.
- DG-8 (two private hops): a public read whose private helper itself delegates to a further private method. Must REFUSE (one private hop only).
- DG-8b (unresolvable private helper): a public read whose helper call `getFoo(id)` matches an overloaded or shadowed private set that Java overload resolution does not narrow to one most-specific declaration (for example a clean `getFoo(Long)` and a guarded `getFoo(Object)`). Must REFUSE loud, never read one candidate's body. This is the fixture that closes the helper-resolution mis-bound hole (condition 1a).
- DG-9 (cross-class or service delegation): a public read delegating to another service or a remote or Feign client. Must REFUSE (out of scope, delegation-only facade).

Mis-bound guards, one per new trace:

- DG-10 (inherited-`@Id` mis-bound guard): a child in a JOINED hierarchy whose parent and a sibling also have reads; the child's `get<Child>` binds to the child's own method resolved by key (section 4.12.4), never to the parent's or the sibling's, and outputs the child's entity.
- DG-11 (helper-hop mis-bound guard): with two same-named entities across bounded contexts (as in section 4.12), a helper-hop read binds to its own service method (its helper call pinned to one declaration by overload resolution, condition 1a) and its load repository resolves to its own entity by key, never a sibling entity of the same simple name. Also carries the section 4.2 mutation and second-access disqualifiers through the private hop: a helper that mutates or performs a second repository access refuses, and an entity with a helper-hop candidate and a competing direct read refuses as AMBIGUOUS_BINDING listing both.

#### 4.13.7 The three-benchmark forecast, and the headline finding

The forecast is recorded number by number in the ledgers (docs/experiments/second-app-trial.md and third-app-trial.md, the Discovery-Gap forecast sections; the private benchmark by counts and shapes only). In summary:

- pms-backend: no change. It has none of the three shapes (no inheritance, no private-helper hop, no not-in-tree port; its one-hop repository load is condition 4b, already handled). discovered 2, byte-identical, mis-bound 0.
- erp-backend: no change. Same: no inheritance, direct Spring Data reads, no private-helper hop. discovered 25, byte-identical, mis-bound 0.
- The private enterprise benchmark: ZERO new discoveries under the general rules. Inherited `@Id` recovers the identifier for all 10 NO_IDENTIFIER entities, turning each into a read intent, but all 10 are internal polymorphic-subtype rows (per-channel notification-log subtypes, event-method-config subtypes, order-line-item subtypes) with no clean backing by-id read, so all 10 become NO_BACKING_METHOD: a cause relabel (NO_IDENTIFIER 10 to 0, NO_BACKING_METHOD 96 to 106), not a discovery. The private-helper-hop rule reaches the 3 true private-same-class hops, and every one carries a post-load soft-delete guard, so all are refused (strict); their near-miss messages gain the guard-refusal reason. Port-to-implementation adds nothing (no not-in-tree case; the in-tree ports are already condition 4b or direct `@Service` scanning). Net: discovered 4 to 4, mis-bound 0.

The headline, stated plainly because the task of this classification was to find it: the general rules unlock zero discoveries here, and the gap between the un-discovered reads and that zero is the finding. A full enumeration of the 39 in-scope single-id by-id reads breaks down as: 5 clean-direct reads that already fall under sections 4.1 and 4.2 (among the entities they touch, one has two competing view reads and is the correct AMBIGUOUS_BINDING the survey reports, a future named-reads question rather than a discovery gap; the rest are among the 4 by-id capabilities the tool already discovers, so the discovery-gap rules add nothing to this bucket); 6 soft-delete-guarded reads (3 through a private helper, 3 inline), all correctly refused because the tool cannot tell the guard from an authorization or business filter without interpreting it; 12 aggregate-enrichment reads (a clean load plus a second access, Attestation territory, not a matching rule); 15 service or Feign delegation reads (out of scope, no local load to bind); and 1 stub returning a hardcoded view (no backing). Separately, the 10 NO_IDENTIFIER entities are internal subtype rows with no by-id read (the cause relabel above). Almost all of this application's un-discovered reads depend on its own foundation idioms (the pervasive soft-delete guard, the aggregate and twin-proxy patterns) or are internal non-read entities; a safe general rule reaches none of them here. This is a real measurement of the tool's reach: on a foundation-heavy codebase, the general structural rules are correct but inert, and the remaining user-facing coverage lives in Attestation (the guarded and aggregate reads), not in more discovery rules. It sharpens the roadmap rather than growing the tool.

#### 4.13.8 Build recommendation: DEFER, per the P1b precedent

Recommended, and recorded: the discovery-gap build is DEFERRED. Both general rules discover zero on all three benchmarks (the inherited-`@Id` rule relabels 10 causes; the private-helper-hop rule refuses every guarded instance), so a build session would move no benchmark number, exactly the P1b situation (section 4.2.10.7). The rules are ratified here for the correctness and the boundary they draw: inherited `@Id` stops the tool wrongly reporting NO_IDENTIFIER for a standard-JPA inheritance child, and the private-helper-hop rule states, with a fixture, exactly why a guarded helper is refused. The build triggers when either (a) a future benchmark app presents an inheritance child that has a clean backing by-id read (DG-1 or DG-2 discovering a real capability), or (b) a future app presents a clean, unguarded private-helper hop (DG-3 discovering a real capability). Until one of those triggers, the discovery-gap rules stand DESIGNED and, on ratification, RATIFIED, BUILD DEFERRED. The inherited-`@Id` eligibility change is small and self-contained, so ratification may elect to build it alone for the honesty of the cause relabel; the default recommendation, given zero discovery, is to defer with P1b.

### 4.14 Controller discovery and declared-authority extraction (phase Controller-Authority; designed 2026-07-19, ratified and BUILT 2026-07-19)

Build status (2026-07-19). The phase is built exactly as ratified, and the three-benchmark forecast (section 4.14.9) held to the number. The ControllerExtractor is alive (a neutral GET-handler catalog); the anchor rule (4.14.2), the pooling decorator (4.14.3), the declared-authority compiler with the parseable-SpEL and gated-`@Secured` boundary (4.14.4), and the three-state representation (4.14.5) are implemented in the Spring provider with no `tibyan-core` change and no IR growth (both states ride the Annotation hook); the two backends surface the states additively (Volume 6, sections 3.5 and 4.3). Fixtures CA-1 to CA-18 are green. Measured: pms discovered 2 unchanged, getUnit compiled `["1", "2"]` and getLease `["1"]`, additive on exactly those two entries; erp discovered 25 unchanged and BYTE-IDENTICAL (zero declarations, all ABSENT); the private enterprise benchmark discovered 4 unchanged, exactly 1 compiled a single-authority contract and 3 ABSENT, its AMBIGUOUS entity staying AMBIGUOUS (the live no-tiebreaker case); mis-bound 0 on all three, now including the security contract (each read carries exactly its own anchor's authorities). `mvn clean verify` is green; the example-app e2e and every existing backend golden are byte-identical (getProduct is ABSENT). The full record is across the three ledgers (the Controller-Authority `actual` sections) and Volume 13.

FORECAST-BLIND on the requesting user's system, recorded plainly and NOT softened: the requesting adopter's inventory system was NOT provided, so the build proceeded WITHOUT it. The phase is measured on fixtures plus the three existing benchmarks; the inventory-benchmark measurement is PENDING first contact and is the phase's open verification. Crucially, the `@PreAuthorize` half of the compilation set (the shape the adopter named) is exercised ONLY by fixtures, because `@PreAuthorize` occurs zero times across all three existing benchmarks; whether the adopter's real `@PreAuthorize` usage falls inside the parseable set or degrades to unverifiable markers is unknown until the inventory benchmark arrives (section 4.14.9).

One clarification flagged (CLAUDE.md rule 13), changing no ratified matching outcome. The CA-18 fixture text bundles four anchor-shape refusals and says "with a declaration present the composition rule yields conflicting-declarations"; that holds for the three shapes that are still GET-mapped associated routes (a two-statement body, a `status().body()` envelope, a nested second call). The fourth shape, a `@RequestMapping` with no method element, is by condition 2 NOT GET evidence, so it is not a handler and not an associated route at all: its declaration is ignored and the read stays ABSENT, which is condition 2 governing over the fixture summary. The build implements condition 2 exactly and the CA-18 fixture asserts ABSENT for that shape.

This is the project's FIRST REAL-USER-REQUESTED phase. A first adopter with a large inventory system requested two things: capability discovery from `@RestController` classes, and extraction of the required privileges declared on those endpoints (authorities, explicitly not roles) so that an external orchestration layer can enforce authorization before invoking a capability. The orchestration itself (prompt planning, login, token validation) is out of scope; it is the consumer's application layer. Tibyan's job is narrower and structural: compile the security contract the source DECLARES, and carry it honestly in the manifest, or say loudly that it could not.

Per rule 12 the frozen design was committed first and the build implemented this text exactly.

The requesting adopter's application (referred to only as "the inventory benchmark" if it is ever provided, under the same absolute redaction as the private enterprise benchmark) was NOT available for the design or the build. The design and the build are therefore made from a full read-only census of the three existing benchmarks plus pre-registered fixtures, and the inventory benchmark measurement is recorded as PENDING (section 4.14.9).

#### 4.14.1 Evidence: the controller and security census of all three benchmarks (read-only, 2026-07-19)

Every controller class, GET endpoint, and security annotation in the three benchmarks was censused from source. The full per-app records are in the ledgers (docs/experiments/second-app-trial.md section 8.15, third-app-trial.md section 21, enterprise-benchmark.md section 7, the last by counts and shapes only). The counts that decide this design:

| Fact | pms-backend | erp-backend | private enterprise benchmark |
|---|---|---|---|
| Concrete controller beans | 36 (27 `@RestController`, 9 `@Controller` returning `ResponseEntity`) | 29 (all `@RestController`) | 40 (all `@RestController`) |
| Mapping idiom | `@RequestMapping(method = RequestMethod.GET)` on generated in-tree `@Controller` INTERFACES; the concrete class implements them (89 GET), plus 7 direct `@GetMapping` | direct `@GetMapping` (104 GET); 14 controllers split into a plain in-tree interface carrying the mappings plus an impl | `@RequestMapping(method = RequestMethod.GET)` on generated in-tree interfaces (70 GET); zero `@GetMapping` on controllers |
| By-id GET shape (one `@PathVariable`, nothing else) | ~20 | 30 | ~22 |
| Filtered GET shape (`@RequestParam`) | ~40 | 22 (plus 13 `Pageable`-only) | dominant (roughly 240 `@RequestParam` declarations) |
| GET return idiom | every GET returns `ResponseEntity<X>` | 102 of 104 return `ResponseEntity<X>` | every GET returns `ResponseEntity<X>` |
| GET body shape | overwhelmingly one statement, `return ResponseEntity.ok(service.m(args));` | 39 one-statement pass-throughs; 65 with try/catch status-mapping boilerplate | 100 percent one-statement delegation |
| `@PreAuthorize` occurrences | 0 | 0 | 0 |
| `@Secured` occurrences | 119 (method-level, controllers only, multi-constant arrays) | 0 | 177 (method-level, controllers only, single-constant) |
| `@Secured` value style | statically-imported `public static final String` constants whose literals are numeric codes ("1", "2", "3") | none | statically-imported `public static final String` constants whose literals are numeric permission codes |
| Other method security (`@PostAuthorize`, `@RolesAllowed`, `@PreFilter`, `@PostFilter`) | 0 | 0 | 0 |
| Enablement | `@EnableMethodSecurity(securedEnabled = true)`, in-tree; no `@EnableGlobalMethodSecurity` | no Spring Security dependency at all | `@EnableMethodSecurity(securedEnabled = true)`, in-tree; no `@EnableGlobalMethodSecurity` |
| URL-level rules | one `SecurityFilterChain` (public-path `permitAll`, then `anyRequest().authenticated()`) | none | one `SecurityFilterChain` with `requestMatchers` rules |

The headline, stated plainly because the guardrail demanded it be checked: THE PRE-REGISTERED PARSEABLE-SpEL BOUNDARY MATCHES ZERO REAL OCCURRENCES ON THE CURRENT BENCHMARKS. There is not a single `@PreAuthorize` in any of the three apps, so `hasAuthority(...)`/`hasAnyAuthority(...)` extraction, the shape the requesting adopter named, is exercised only by fixtures until the inventory benchmark arrives. The measurable declared-authority idiom in the wild here is a different one: `@Secured` with in-tree string constants, 296 method-level occurrences across the two secured apps, always on controllers, never on services. That shape is structurally MORE tractable than SpEL (it is a literal string array with no expression language), and this census pulls it into scope under strict conditions (section 4.14.4), exactly as the E4 and discovery-gap censuses reshaped their phases. Had the design stuck to the SpEL-only boundary, the authority feature would have been inert on every application we can measure.

Two further census facts anchor the rest of the design:

- The pooling case is live, not hypothetical. In pms, `UnitControllerImpl#_getUnitById` is exactly `return ResponseEntity.ok(unitService.findById(unitId));` under `@Secured({PROPERTY_MANAGER_ROLE, SUPERINTENDENT_ROLE})`, and `LeaseControllerImpl#_getLeaseById` is the same one-statement delegation to `leaseService.findById` under `@Secured({PROPERTY_MANAGER_ROLE})`: the two service methods are precisely the discovered bindings of getUnit and getLease. In erp, every discovered by-id and list service method has at least one controller GET delegating to it, and several have MORE than one (Transaction's `findById` is invoked by four controller methods across the admin and sales controllers). A design that minted a capability per controller endpoint would emit duplicate tools for one read.
- Declared security lives at the controller boundary and nowhere else. In both secured apps, services carry zero security annotations; every `@Secured` sits on the controller method. The MCP runtime invokes the service bean directly through the execution adapter, bypassing the controller layer entirely, which is exactly why the adopter needs the declared contract carried in the manifest: the enforcement point the app relies on is not on the invocation path the tool generates.

#### 4.14.2 The controller anchor rule (declared surface, provable delegation, no new discovery)

The first design decision, argued and settled: in this phase a controller endpoint GRANTS NO DISCOVERY. It is an ANCHOR: structural evidence that an already-verified capability is fronted by the application's declared public surface, and the carrier of that surface's declared security contract. The alternative, letting a controller's declared contract (return type plus a delegation) stand as discovery evidence on its own, was considered and refused: the controller's declared return type says what the endpoint claims to return, but only the backing service body proves what is actually loaded, and every discipline the project has (4.1, 4.2, 4.10, 4.13) lives in that body. A controller can therefore confirm and decorate a read the service rules have proven; it can never rescue one they refused (a guarded, filtered, or aggregate service read stays refused whatever its controller declares). Controller-ONLY reads (a controller method that performs the repository load itself, with no service behind it) appear zero times in the three censuses and are a NAMED DEFERRAL, not a rule.

A controller GET handler H is a STRICT ANCHOR for an existing capability C (binding `S#m`, entity E) when ALL of the following hold:

1. H is a public instance method on a concrete class that is either annotated `@RestController`, or annotated `@Controller` with H's declared return type being `ResponseEntity` of something (the framework contract that makes the return value the response body; pms's 9 concrete `@Controller` implementations are this shape). An interface is never a handler bean. A `@Controller` method returning anything else is not response-body evidence and does not anchor (an explicit `@ResponseBody` is a named deferral; the censuses found zero GET occurrences).
2. H's effective mapping declares GET, found in exactly one place: `@GetMapping` on H itself; or `@RequestMapping(method = RequestMethod.GET)` (a single method element, exactly GET) on H itself; or either of those on the single in-tree interface method H overrides, where H's class implements that interface and the interface method's signature matches H's. This is the generated-interface idiom both pms and the private benchmark use (mapping and parameter annotations on the interface, body and security on the implementation). Refusals: a `@RequestMapping` with no method element or more than one method is not GET evidence; a mapping found BOTH on H and on an interface method, or on more than one implemented interface, is an ambiguous mapping source and refuses; an interface that does not resolve in-tree by the section 4.12.4 scoping refuses. The mapping's path VALUE is never read and never modeled: the IR carries no endpoint or route concept (ADR-0013), and this phase does not change that.
3. Parameter shape, read from the declaration that carries the mapping (the interface method in the split idiom, H itself otherwise). For a by-id capability: exactly one parameter, annotated `@PathVariable`, whose type maps through the standard Java-to-primitive mapping to E's idType. Validation and documentation annotations on the parameter (`@NotNull`, `@Valid`, `@Parameter`, and the like) are tolerated; any `@RequestParam`, any `@RequestBody`, or any additional parameter of any kind disqualifies. A query-parameter read is a filtered read, and the E6b verdict applies verbatim: a filter's meaning is not structurally provable, a copied parameter name IS a claim, so the endpoint refuses as a deferred filtered read, never anchoring anything. For a list capability: no parameters at all, or exactly one parameter of the framework type `Pageable` or `Sort` forwarded verbatim (the mirror of section 4.10 condition 3).
4. H's body is EXACTLY one statement: `return <expr>;` where `<expr>` is either `<call>` or `ResponseEntity.ok(<call>)`, and nothing else. `ResponseEntity.ok(x)` is accepted as a recognized framework envelope with framework-defined semantics (a 200 wrapping of the body), the same class of trust as `Optional` unwrapping in section 4.2 condition 7; any other `ResponseEntity` factory, any builder chain (`status(...).body(...)`, headers), any logging statement, any try/catch, any local variable, or any second statement disqualifies. The strictness is the DG-4 lesson applied here: the tool cannot tell benign boilerplate from a semantic step without interpreting it, so any statement beyond the delegation refuses.
5. `<call>` is a single method call, not nested in or composed with any other call, on a field of H's class whose declared type resolves in-tree (the section 4.12.4 scoping stage) to either the `@Service` class S itself, or an interface implemented by EXACTLY ONE in-tree `@Service` class, which is S. More than one in-tree implementation refuses (the section 4.13.4 bar, unchanged). The invoked method resolves on S to exactly the capability's bound method m, by name and parameter type; the argument is H's mapped parameter, verbatim (the `@PathVariable` id for a by-id anchor; the `Pageable`/`Sort` for a paged list anchor; nothing for a no-argument list anchor). An expression over the parameter is never the id.
6. Return-type consistency: the mapping declaration's return type, unwrapped of `ResponseEntity`, resolves in-tree to the same type m declares (E itself, the same view type the capability's `view` annotation records, or the same collection-of-E shape for a list). In a one-statement delegation this holds by construction; it is stated as a condition so an override-variance trick cannot slip a differently-typed surface past the anchor.

ASSOCIATED ENDPOINTS, defined for the security rule: any GET-mapped handler (per conditions 1 and 2) whose body contains, anywhere, a call that resolves per condition 5 to C's binding is ASSOCIATED with C. Strict anchors are the associated endpoints that pass conditions 3 to 6. The distinction matters in section 4.14.4: a declared contract compiles only when the tool can account for EVERY associated route, not just the clean ones. Non-GET handlers are not associated endpoints: a write endpoint that internally performs the read's load is a different operation, and its declared contract is not the read's.

#### 4.14.3 Pooling across sources: the decision, argued both ways

The question: a controller read and a service read can reach the same underlying capability; are controller-discovered and service-discovered candidates one pooled set, or two distinct capabilities?

The case for DISTINCT capabilities, stated fairly: a controller endpoint is the application's own declared public surface, with its own path, its own security contract, and its own DTO shape; an internal service read is a different thing, and an agent might legitimately want to know which reads the application chose to publish. Modeling them apart would preserve that distinction.

The case for POOLING, and why it wins:

- A distinct controller capability is an Endpoint in disguise. ADR-0013 deliberately removed Endpoint and Service from the IR; each backend derives its own surface from capabilities. A "controller capability" would need a path, a verb, and a wire shape to mean anything distinct from the service read it fronts, and every one of those is the framework concept the IR refuses to carry. The declared-surface FACT the distinct model wants is carried more honestly as evidence on the one capability (the anchor confirms the read is published; the security contract is the part of that surface an agent actually needs).
- Two capabilities for one read is the duplication the rules exist to prevent. The census makes this concrete: erp's `transactionService.findById` is invoked by four controller methods, and Product and Account are each fronted by two controllers (admin and sales). A per-endpoint capability model would emit up to four tools that are secretly the same read, differing only in route, which the IR does not model, so they would be four indistinguishable tools. Two sources must never silently produce two capabilities that are secretly the same read; pooling makes that structural.
- The binding already names the honest invocable. The runtime's execution adapter invokes `<Bean>#<method>` on the application context; the service method is directly invocable with the capability's typed inputs. Binding a controller method instead would drag `ResponseEntity` unwrapping and web-binding semantics into the adapter for no gain, since the controller body is, by the anchor proof, nothing but the service call.

DECISION: POOL, with the controller as evidence and decorator, never as a capability minter. Precisely: controller anchors attach to the existing capability produced by sections 4.1, 4.2, and 4.10; they add no candidate to any pool, they change no capability name (naming stays `get<E>`/`list<E>` from the display name), and they mint no new binding. Two consequences are stated as rules:

- Controller evidence is NEVER a tiebreaker. An entity whose service pool is AMBIGUOUS_BINDING (two competing service methods) stays AMBIGUOUS even if a controller endpoint delegates to exactly one of them. Using the anchor to pick would be precedence between sources, and the no-precedence, refuse-on-ambiguity rule (section 4.2 condition 10) admits none. The refusal message may NAME the anchor as context (a diagnostics-only enrichment); the outcome does not move.
- The ControllerExtractor (the placeholder since P1) is enabled as a neutral catalog producer, symmetrical with the Entity and Service extractors: it reads each concrete controller class and produces, per GET handler, the mapping evidence (where the mapping was found, GET-ness), the parameter shape as written, the return type as written, the one-statement delegation target as written (field name, called method, argument), and the security annotations as written (mechanism, values, level). It knows nothing about entities, services, or capabilities; the PROVIDER joins its catalog against the discovered capabilities (resolving delegation targets by the section 4.12.4 scoping and the single-implementation rule), exactly as the provider joins the Entity and Service extractors today. No extractor depends on another.

#### 4.14.4 The declared-authority extraction rule

The honesty invariant, stated first and plainly: for every capability, the manifest either carries the COMPLETE declared authority contract, compiled from expressions the tool can fully and structurally parse, or it declares that a security contract exists that the tool could not verify. Nothing in between: never a partial authority list, never a transformed or guessed string, never a declaration silently dropped.

DETECTION versus COMPILATION. Two different sets, and the distinction carries the invariant:

- The DETECTION set is every method-security annotation the tool recognizes as a declaration that a security contract exists: `@PreAuthorize`, `@PostAuthorize`, `@Secured`, `@RolesAllowed`, `@PreFilter`, `@PostFilter` (and the jakarta.annotation.security trio `@RolesAllowed`, `@PermitAll`, `@DenyAll`). Detecting is cheap and total: the annotation's presence is the fact, whatever its value.
- The COMPILATION set is the far smaller set of declarations the tool can turn into a complete, literal authority sequence. A detected declaration outside the compilation set NEVER compiles partially; it marks the capability SECURITY-UNVERIFIABLE (section 4.14.5).

The effective declaration of a method, per mechanism, follows the framework's own most-specific-wins lookup, which is a documented Spring Security contract, not an interpretation: the annotation on the concrete method; else on the concrete class; else on the interface method the concrete method overrides; else on that interface. Class-level therefore applies to every handler the class declares unless a method overrides it, and the split idiom (mapping on the interface, security on the implementation) resolves naturally. If declarations from MORE THAN ONE mechanism apply to the same method (for example a class-level `@PreAuthorize` and a method-level `@Secured`), the mechanisms are enforced by independent interceptors and BOTH apply; a flat authority list cannot carry that conjunction, so the result is unverifiable (reason `mixed-mechanisms`), never a merge and never a pick.

THE COMPILATION SET, exactly two shapes:

(a) `@PreAuthorize` whose ENTIRE expression is exactly one of:
   - `hasAuthority('<literal>')`
   - `hasAnyAuthority('<literal>', '<literal>', ...)`
   read as a bounded token template (the P1b discipline applied to SpEL): the tool locates the one function name, the parenthesized single-quoted string literals, and end of expression. It never evaluates SpEL. Whitespace variance is tolerated; ANYTHING else in the expression, a second clause, an operator, a variable, a non-literal argument, disqualifies the whole expression. The compiled contract is the literal sequence, with ANY-OF semantics: the framework grants the call when the principal holds at least one of the listed authorities, and `hasAuthority('X')` is the one-element case of the same semantics.

(b) `@Secured` where EVERY value is a string literal, or a constant reference that resolves under the constant rule below to a string literal, PROVIDED the modern-enablement gate holds: at least one in-tree class is annotated `@EnableMethodSecurity` with `securedEnabled = true`, and NO in-tree class is annotated `@EnableGlobalMethodSecurity`. The gate is the framework-contract anchor that makes the claim true: under `@EnableMethodSecurity(securedEnabled = true)` the `@Secured` values are matched LITERALLY against the principal's granted authorities with any-of semantics (the same semantics as `hasAnyAuthority`), whereas the legacy `@EnableGlobalMethodSecurity` route processes `@Secured` through a role-prefix voter whose matching depends on configuration the tool will not interpret. Without the in-tree modern gate, a `@Secured` declaration is detected but unverifiable (reason `no-modern-enablement`). Both secured benchmarks pass this gate with the annotation in their own scanned tree.

The CONSTANT RULE (what makes shape (b) real on the evidence, since all 296 benchmark occurrences use constants, not inline literals): an annotation value written as a name resolves by Java scoping against the annotation's compilation unit, the section 4.12.4 discipline applied to static members: a static single-member import of that name; else a static wildcard import reaching exactly one in-tree `public static final String` field of that name; else a qualified reference `<Class>.<NAME>` whose class resolves in-tree; else a same-class constant. The resolved field must be `public static final String` with an initializer that is EXACTLY one string literal: no concatenation, no method call, no conditional, no reference to another constant. The compiled value is that literal. Anything else, an unresolvable name, an ambiguous wildcard, a non-literal initializer, a not-in-tree declaring class, refuses the whole declaration to unverifiable (reason `unresolvable-constant`). The tool never resolves a constant INSIDE a SpEL string (SpEL cannot reference Java constants without a type operator, and a type operator is outside the template); the constant rule applies to `@Secured`'s Java-level values only.

REFUSED TO UNVERIFIABLE, each with its named reason, none ever compiled partially:

- `hasRole(...)`, `hasAnyRole(...)` (reason `role-expression`), and `@RolesAllowed` (reason `role-expression`): the framework TRANSFORMS the written string before matching, prepending a configurable role prefix, so the written literal is not the authority the principal must hold, and verifying the effective string means reading configuration. The rule is mechanism-general: THE TOOL NEVER TRANSFORMS AN AUTHORITY STRING. It carries only strings the framework matches literally. A role written as its full authority via `hasAuthority('ROLE_X')` compiles fine, because there the literal IS the matched string. This also honors the adopter's own framing: privileges, explicitly not roles.
- `hasPermission(...)` (reason `unparseable-expression`): domain-object authorization delegating to a configured `PermissionEvaluator` bean; its meaning is code, not a literal.
- Any bean reference (`@myBean.check(...)`), any method call, any `#parameter` or `principal`/`authentication` reference, any string concatenation or variable (reason `unparseable-expression`): SpEL the template does not cover is never evaluated.
- Any COMPOUND expression, including a pure and/or/not composition of otherwise-compilable atoms (reason `unparseable-expression`). This is deliberate and worth its sentence: `hasAuthority('A') and hasAuthority('B')` is structurally parseable, but it is an ALL-OF contract, and the flat any-of `requiredAuthorities` list cannot carry all-of without lying about the semantics. Rather than grow a boolean expression tree into the manifest on zero benchmark evidence, compounds are refused by name and deferred; if the inventory benchmark shows real compound usage, that is counted evidence for a richer representation, designed then, not smuggled now.
- `permitAll()`, `denyAll()`, `isAuthenticated()`, `isAnonymous()`, and the jakarta `@PermitAll`/`@DenyAll` (reason `unparseable-expression`, named deferral): parseable atoms that produce no authority sequence; representing "explicitly public" or "authenticated but no specific authority" honestly needs its own decision, deferred until an app shows the shape on a read endpoint (the censuses found none).
- `@PostAuthorize`, `@PreFilter`, `@PostFilter` PRESENT on any effective declaration (reason `result-annotation`): these are result-dependent or row-filtering contracts. A `@PostFilter` on a list read filters rows per caller, so presenting that read as a plain list-all with pre-invocation authorities would be the filtered-read lie in security clothing. Presence alone marks unverifiable, whatever else is declared.

COMPOSITION OVER THE CAPABILITY (the agreement rule; this is where the anchor set from 4.14.2 does its work). A capability C's declared contract is compiled only when the tool can account for every declared route to the read:

1. Collect C's associated endpoints (section 4.14.2) and C's own binding method's effective declaration (services carry none in any current benchmark, but the rule covers it: a declaration on the service method itself is a declaration on every route through it).
2. If NO detected declaration exists anywhere (no associated endpoint declares, the binding declares nothing): C's security state is ABSENT (section 4.14.5). This is erp's entire surface.
3. If at least one detected declaration exists: C compiles ONLY IF every associated endpoint is a STRICT anchor, every effective declaration in play (each anchor's, plus the binding's if any) is in the compilation set, and all of them compile to the IDENTICAL authority sequence (same values, same order; sequence equality is chosen over set equality because it is the simplest deterministic rule, and no benchmark case needs the looser one; relaxing to set equality is a recorded option if real evidence prices it). The compiled sequence is carried once.
4. ANY divergence refuses to unverifiable: two anchors with different sequences, or a declared anchor and an undeclared associated route (the read is reachable without the declared contract), or a declaration on a non-strict associated endpoint (the tool cannot prove what that route's declaration governs) (all reason `conflicting-declarations`); or any declaration outside the compilation set (its own reason). The unverifiable marker is the honest summary of every one of these: a declared contract exists, and the tool will not present a partial or contradicted version of it.

#### 4.14.5 The security-state representation, and what absence means

Three states, exactly three, carried through the existing Annotation hook with NO IR growth (the argument against a first-class field is in Volume 4, section 7.1):

- COMPILED: the capability carries an annotation `requiredAuthorities` whose values are the compiled literal sequence. Semantics, stated once and carried to every surface: the application declares that invoking this read requires the caller to hold AT LEAST ONE of the listed authorities (any-of), matched literally by the application's security layer. The strings are carried verbatim, however opaque (pms's are "1" and "2"; the private benchmark's are numeric codes): the literal IS the contract, and prettifying it would be a transformation the rule forbids.
- SECURITY-UNVERIFIABLE: the capability carries an annotation `securityUnverifiable` whose value is the reason token, one of the closed list: `mixed-mechanisms`, `role-expression`, `unparseable-expression`, `unresolvable-constant`, `no-modern-enablement`, `conflicting-declarations`, `result-annotation`. Meaning: a declared security contract EXISTS on this read's surface and the tool could not compile it completely; the capability must not be treated as carrying no contract, and must not be exposed by a consumer that intends to honor declared security until a human resolves what the declaration means.
- ABSENT: neither annotation. Meaning, defined precisely because the census shows the naive reading would be false: NO method-security declaration was found on the capability's binding method or any associated controller endpoint. Absence is NOT a claim that the read is unprotected, and the manifest documentation states this: both secured benchmarks gate every request behind a `SecurityFilterChain` with URL rules (`requestMatchers`, `anyRequest().authenticated()`) that live in imperative configuration code this tool does not read (reading a filter-chain builder is interpretation, not structure; a named non-goal). Absence means "no declared method-level contract was found to carry", nothing more. The two states the naive reading conflates, "declared unprotected" and "not analyzed", are therefore BOTH inside ABSENT, disclosed as such; splitting them honestly would require compiling `permitAll` and the URL chain, both named deferrals.

Survey contract (rendered by the CLI when the phase is built, the section 4.9 precedent): the survey gains one line, emitted only when nonzero, `security: declared K (compiled M, unverifiable N)`, and each discovered capability's detail names its state, its compiled sequence or its reason token, and the anchor evidence (the controller class and method each contract came from). A security-free app's survey is byte-identical. Refused and deferred endpoints never contribute authorities anywhere: a `@Secured` on a filtered GET is not extracted, because there is no capability to attach it to (no orphan authorities).

Mis-bound, extended for this phase and counted against the same absolute bar: a WRONG AUTHORITY attached to a capability (an authority the declared source does not require for that read, or another endpoint's contract attached to it) is a mis-bind of the security contract and counts identically to a wrong binding. The composition rule makes it structurally hard (contracts attach only through resolved strict anchors, and disagreement refuses), and fixture CA-11 guards it.

#### 4.14.6 What this phase will never do

- Never mint a capability from a controller: no discovery, no rescue of a refused service read, no controller-only read (named deferral), no second capability for an already-discovered read.
- Never model a route: no path, no verb, no endpoint concept in the IR (ADR-0013 stands).
- Never use an anchor as a tiebreaker between ambiguous service candidates.
- Never transform an authority string: no role-prefix expansion, no case folding, no prettifying of opaque codes.
- Never evaluate SpEL, never resolve a security bean, never read a `SecurityFilterChain` or its matchers.
- Never emit a partial authority list: a declaration compiles completely or marks the capability unverifiable.
- Never let absence claim safety: absence means no declaration found, never "unprotected", and the surfaces say so.
- Never attach an authority across reads: a contract reaches a capability only through a strict anchor resolved to that capability's own binding by the section 4.12.4 discipline.
- Never claim enforcement: the manifest carries the declared contract; the runtime enforces nothing this phase (Volume 7, section 8, gap 9).

#### 4.14.7 Pre-registered fixtures (CA-1 to CA-18; the build session must include these before the code exists)

Positives:

- CA-1 (the requested shape): a direct `@GetMapping("/{id}")` handler on a `@RestController`, body `return ResponseEntity.ok(service.findById(id));` delegating to a discovered 4.1 read, method-level `@PreAuthorize("hasAuthority('INVENTORY_READ')")`. The capability compiles `requiredAuthorities [INVENTORY_READ]`.
- CA-2: as CA-1 with `@PreAuthorize("hasAnyAuthority('A_READ', 'B_READ')")`. Compiles the two-element sequence, any-of.
- CA-3: class-level `@PreAuthorize` and no method-level: the class contract applies to the handler and compiles. CA-3b: class-level plus a DIFFERENT method-level `@PreAuthorize`: the method-level wins alone (most-specific), compiling only the method's sequence.
- CA-12 (the census shape, the one live on two benchmarks): mapping on an in-tree generated `@Controller` interface (`@RequestMapping(method = RequestMethod.GET)`, `@PathVariable` on the interface method), concrete `@RestController` implementation whose method is one statement `return ResponseEntity.ok(service.findById(id));` under `@Secured({CONST_A, CONST_B})` with statically-imported in-tree `public static final String` constants with literal initializers, and an in-tree `@EnableMethodSecurity(securedEnabled = true)`. Compiles the two literals in order.
- CA-15: the `ResponseEntity.ok` unwrap positive is CA-1 itself; the paired refusal is below.
- CA-16 (list pooling): a no-parameter `@GetMapping` returning `List<E>` delegating verbatim to the discovered list-all binding decorates `list<E>` with its compiled contract; a `Pageable`-forwarding variant decorates the paged list capability; a handler delegating to a NON-pool method (the `findCompleted()` shape) anchors nothing and its annotation attaches nowhere.
- CA-17: the split-idiom variant of CA-12 where the concrete class is `@Controller` (not `@RestController`) returning `ResponseEntity`, the pms minority shape. Anchors and compiles.

Refusals and markers, each asserting the exact state and located diagnostic:

- CA-4 (the security honesty boundary, most important with CA-6): `@PreAuthorize("hasPermission(#id, 'Thing', 'read')")` on an otherwise perfect anchor. The capability is emitted, marked `securityUnverifiable: unparseable-expression`, with NO authority list. Never partial, never silently unprotected.
- CA-5: `@PreAuthorize("@securityService.canRead(#id)")`, a custom bean call. Unverifiable, `unparseable-expression`.
- CA-6: compound SpEL, both variants: `hasAuthority('A') and hasAuthority('B')` (parseable atoms, all-of semantics the flat list cannot carry) and `hasAuthority('A') or #id < 100`. Both unverifiable, `unparseable-expression`; the first is the fixture that pins the deliberate compound refusal.
- CA-6b: `@PreAuthorize("hasRole('MANAGER')")` and a `@RolesAllowed("MANAGER")` variant. Unverifiable, `role-expression`; asserts no `ROLE_MANAGER` expansion is ever emitted.
- CA-7 (absence): a clean anchored capability with no security annotation anywhere. Emitted with NEITHER annotation; its manifest entry is byte-identical to the pre-phase entry.
- CA-8 (the E6b guard): a `@GetMapping` with `@RequestParam` filters carrying `@PreAuthorize("hasAuthority('X')")`. The endpoint refuses as a deferred filtered read, anchors nothing, and X attaches to NO capability (the no-orphan-authorities proof).
- CA-9 (pooling): one read reachable via a strict controller anchor and discovered from the service: ONE capability, the service binding, decorated. CA-9b: a controller delegating to a service method the rules refuse (a guarded read): no capability, no anchor, nothing attached.
- CA-10 (the name-trap): `@PreAuthorize("hasAuthority(AUTH_READ)")`, a SpEL variable that LOOKS like a constant. Unverifiable, `unparseable-expression`: the tool never resolves a name inside SpEL, even when an in-tree constant of that name exists.
- CA-11 (the mis-bound guard): two entities, two controllers, two service reads, distinct `@Secured` constants; each capability carries exactly its own anchor's compiled sequence, never the sibling's; and with the section 4.12 colliding-name model, an anchor whose delegation resolves to one context's entity never decorates the other's capability.
- CA-12b: the CA-12 shape with a constant whose initializer is a concatenation (`PREFIX + "_READ"`). Unverifiable, `unresolvable-constant`. CA-12c: the CA-12 shape with NO in-tree `@EnableMethodSecurity(securedEnabled = true)` (and a variant with a legacy `@EnableGlobalMethodSecurity` present). Unverifiable, `no-modern-enablement`.
- CA-13 (conflicts): two strict anchors to one binding with DIFFERENT compiled sequences; a declared strict anchor plus an undeclared associated route; a declaration sitting on a NON-strict associated endpoint (extra statements). All three: unverifiable, `conflicting-declarations`, never the stricter or looser set picked.
- CA-14: `@PostFilter("...")` on a list anchor (and a `@PostAuthorize` by-id variant). Unverifiable, `result-annotation`: a row-filtering declaration never lets the read pose as a plain list with authorities.
- CA-18 (anchor-shape refusals): the CA-12 shape with a body of two statements (a log line before the return), with `ResponseEntity.status(HttpStatus.OK).body(...)` instead of `ok(...)`, with a nested second call (`service.map(service.findById(id))`), and with a `@RequestMapping` carrying no method element. Each fails the anchor conditions; with no declaration anywhere the capability stays ABSENT and unchanged, and with a declaration present the composition rule yields `conflicting-declarations` (the CA-13 third case).

#### 4.14.8 IR, core, and module impact

No IR growth and no `tibyan-core` change: both states ride the existing Annotation hook, the precedent of `view`, `enum`, `partial`, and `paged` (the growth-bar argument is Volume 4, section 7.1). The work lands in the Spring provider (the ControllerExtractor catalog, the anchor join, the authority compiler), the two backends (Volume 6, sections 3.5 and 4.3), and an explicit runtime loader-tolerance test (Volume 7, section 8, gap 9 names the enforcement deferral). The Permissions element of Volume 4, section 7 stays future: this phase carries a compiled declaration of the source, it does not model who may invoke what.

#### 4.14.9 Three-benchmark forecast, the pending fourth, and the build recommendation

Stated before the build, number by number (full rows in the ledgers):

- pms-backend: discovered 2 (unchanged), mis-bound 0. Both capabilities gain COMPILED contracts through their strict anchors: getUnit compiles `requiredAuthorities ["1", "2"]` (from `_getUnitById` under `@Secured({PROPERTY_MANAGER_ROLE, SUPERINTENDENT_ROLE})`, the constants resolving in-tree to literals "1" and "2"), and getLease compiles `requiredAuthorities ["1"]` (from `_getLeaseById` under `@Secured({PROPERTY_MANAGER_ROLE})`). The enablement gate passes (`@EnableMethodSecurity(securedEnabled = true)` in the in-tree security-adapter library; no legacy annotation). The MCP manifest and OpenAPI document change ADDITIVELY on exactly those two entries (the new fields plus one description clause each, the E2 view-field precedent); every other byte is identical, and the goldens update deliberately with a visible diff. The forecast assumes no other GET handler invokes those two bindings; the build verifies the associated-endpoint sweep and any second route found is a forecast deviation to investigate, not silently absorbed.
- erp-backend: discovered 25 (unchanged), mis-bound 0, output BYTE-IDENTICAL. The app has zero security annotations and no Spring Security dependency, so every capability is ABSENT and no field is emitted. The pooling machinery is exercised live and invisibly: the strict anchors exist (the admin one-statement controllers), the multi-route cases exist (Transaction with two strict anchors and two non-strict associated endpoints; Product and Account fronted by both admin and sales controllers), and with zero declarations everywhere the composition rule lands on ABSENT for all. erp is the negative control proving the phase adds nothing where nothing is declared.
- The private enterprise benchmark (counts and shapes only): discovered 4 (unchanged), mis-bound 0. The fronting census (docs/experiments/enterprise-benchmark.md, section 7) is exact: ONE discovered capability is fronted by exactly one strict anchor under a single-constant `@Secured` resolving to a literal numeric code, and compiles a one-element `requiredAuthorities` sequence (the enablement gate passes in-tree); the other THREE have no fronting controller GET at all (one consumed only by an internal proxy, two entity-returning reads with no controller in reach, and structurally no controller GET in that codebase returns a raw entity) and stay ABSENT. Output changes additively on exactly one capability. The app's AMBIGUOUS entity is the live no-tiebreaker case: its two competing view reads are each fronted by a strict anchor, and it stays AMBIGUOUS. No `securityUnverifiable` marker is forecast anywhere on this app.
- The inventory benchmark: NOT provided this session; measurement PENDING. It is the only known app exercising the `@PreAuthorize` half of the compilation set. Recorded plainly: if its `@PreAuthorize` usage turns out to be dominated by `hasPermission`, custom beans, or compound expressions, the feature there will degrade to honest unverifiable markers, and THAT outcome, an adopter whose declared contracts the structural boundary cannot compile, is the signal that authority carriage needs the attestation mechanism (a human asserting what an expression means) sooner than expected. The current evidence does not show it; on the apps we can measure, the census moved the other way (the real idiom is MORE parseable than SpEL).

Existing discovered rows stay byte-identical everywhere except the forecast additive security fields; any movement in a discovered count or an existing binding is a regression finding. Mis-bound 0 is absolute and now includes the security contract: one wrong authority stops the phase.

Build recommendation: BUILD, after ratification, unlike P1b and Discovery-Gap. Those discovered zero and moved no number; this phase changes real output on two of three benchmarks (compiled contracts on pms and the private benchmark) and delivers the first real user's actual request. The build session commits this ratified design first (rule 12), enables the ControllerExtractor, implements the anchor rule, the authority compiler, and the backend surfaces exactly as written here, runs fixtures CA-1 to CA-18, and re-runs all three benchmarks against this forecast.

### 4.15 Attestation: the human-attested third category of knowledge (phase Attestation; designed 2026-07-20, RATIFIED 2026-07-21; sub-phases AT-0 to AT-4; AT-0 BUILT 2026-07-24, AT-1 BUILT 2026-07-25, AT-2 BUILT 2026-09-01, AT-3 RATIFIED and BUILT 2026-09-01, member C2 then member C1; AT-4 draft lifecycle BUILT 2026-09-01, the assist itself deferred with its open questions named in 4.15.5.1)

This is the largest design in the project, and the first that is not a matching rule: it is a capability-AUTHORING mechanism. Per rule 12 this frozen design is committed first; each sub-phase's build is a separate later session implementing this text exactly. Until ratification, no attestation code is written.

THE TRIGGER, recorded. Coverage on view-heavy and foundation-heavy codebases is now the product's binding constraint. The structural ceiling is proven from three directions (Volume 13): pms discovers 5 of an 89-endpoint read surface even after its parse defects were repaired (docs/experiments/second-app-trial.md, sections 8.9 and 9), erp discovers 25 with roughly 44 deferred above the ceiling, and the private enterprise benchmark discovers 4 with its guarded, aggregate, and filtered majority refused. Three phases (P1b, E6a, E6b) independently established that the remainder is unreachable by honest structural rules. The maintainer, testing the tool as a real adopter on pms, hit exactly the wall Attestation was designed-and-waiting for and rejected the coverage as unadoptable. The written trigger (a real adopter hitting the filter or wrapper ceiling on a real application) has FIRED.

WHAT ATTESTATION IS, the frame every decision below must honor. Three categories of knowledge, exactly three, never blurred:

- TOOL-VERIFIED: proven from structure by the ratified rules. The existing 34 capabilities. Never touched by this phase.
- HUMAN-ATTESTED: asserted by a person who knows the code, carried character-exactly, labeled distinctly on every surface. NOT a guess (the tool invents nothing; every attested word was typed or confirmed by a person) and NOT a claim of verification (never presented as proven, anywhere, at any level).
- REFUSED: neither. The default, unchanged.

The standing E6b rejection (section 4.11.3) binds this design as a constraint, not a memory: an MCP tool's NAME and SIGNATURE are the claims an agent acts on, and a disclaimer beside a claim does not unmake it. Attested capabilities must therefore be honest at the name-and-signature level (section 4.15.6), not only in descriptions and metadata. Any part of this design that drifts toward exposing unconfirmed semantics with a disclaimer is rejected by that precedent.

THE NEW INVARIANT, this phase's mis-bound-0: ZERO ATTESTATION DRIFT. What the tool exposes is character-exactly what the human asserted, attached to exactly the code element they asserted it about, labeled human-attested on every surface (survey, IR, MCP manifest, tool name and description, OpenAPI), with complete provenance (who asserted, how the words were produced, when, against which structural fingerprint of the code). Attested never renders as verified anywhere; verified is never downgraded or mixed; attested counts are never summed into discovered. A drifted, mislabeled, misattached, or stale-but-carried attestation is this phase's mis-bind and stops everything.

#### 4.15.1 The sub-phase split, stated first

The design is honestly bigger than one buildable phase. It is split BY NAME into ordered sub-phases, each independently ratifiable and buildable, none thinned to fit:

- AT-0, the structural floor and the envelope unwrap (build-only; designs already ratified). Builds the E6a transparency rule exactly as ratified (section 4.11.7: T1 generic tie, T2 body tie, V visibility-not-discovery, O OPAQUE_WRAPPER refusal, fixtures E6a-1 to E6a-5) and the P1b strict by-primary-key matcher exactly as ratified (section 4.2.10: a provably clean custom query is VERIFIED and needs no attestation; everything above the floor is classified, never guessed). AT-0 adds no attestation machinery and forecasts zero new discoveries on the current benchmarks; its product is a complete, classified refusal ledger, the worklist every later sub-phase consumes. Its E6a forecast is RE-BASED from the ratified text: the 2026-07-02 forecast predated the pms parse repair (roughly 12 visible, roughly 23 parse-skipped); on the repaired copy all of pms parses, so all roughly 33 `*ResultSet` reads become visible-but-refused-filtered and zero stay parse-skipped (ledger section 10).
- AT-1, the attestation core: the record, intake, lifecycle, carriage, and the PREDICATE category (section 4.15.2, category A). The machinery (sections 4.15.3 to 4.15.8) plus the E6b core: filtered and guarded reads attested for what their predicates mean. The pms unlock.
- AT-2, the SHAPE category (section 4.15.2, category B): opaque wrappers, aggregate enrichment, unresolvable projections, attested for what they return. Depends on AT-1's machinery, adds one category.
- AT-3, the BOUNDARY category, elevated stakes (section 4.15.2, category C): security contracts and remote delegations, where a false attestation breaches a security boundary rather than mislabeling data semantics. DEFERRED BY NAME with its guards sketched, not thinned into AT-1 (section 4.15.2, category C states why).
- AT-4, the AI assist (section 4.15.5): optional and separable. The design stands entirely without it.

Dependency order: AT-0 before AT-1 (a read must be visible and classified before it can be attested); AT-1 before AT-2, AT-3, AT-4. One sub-phase per build session, ratified design first, benchmarks re-run after each.

#### 4.15.2 The attestable set: which refusals accept attestation, and what the human asserts

The boundary that governs every category: AN ASSERTION FILLS ONLY A SEMANTIC HOLE, NEVER A STRUCTURAL ONE. The tool still verifies, structurally and non-negotiably, everything it can see: the target method exists with the asserted signature, the binding resolves uniquely through the section 4.12.4 key resolver, every asserted parameter exists with a type that maps, the visible body passes the read-safety check below, and the output entity tie holds wherever T1, T2, or the view machinery can prove it. The assertion supplies exactly the meaning structure cannot reach, and nothing else. An assertion that tries to supply a structural fact (a binding that does not resolve, a parameter that does not exist, a read where no persistence access exists) is REFUSED at intake (section 4.15.4).

THE READ-SAFETY CHECK, and its honest limit stated precisely, because the mutation-freedom claim is the load-bearing safety property of the whole phase and an earlier draft over-claimed it. The verified rules earned mutation-freedom two ways at once: the write-set of section 4.2 condition 6 (no call named `save`/`insert`/`update`/`delete`/`persist`/`merge`/`flush` on a repository-typed field) AND the shape strictness of conditions 6 and 7 (exactly one persistence access, no second access, no extra statement). Attestable bodies necessarily abandon the shape strictness (a filter is built, an aggregate enriches, a guard branches), so the write-set alone does NOT prove mutation-freedom on them: a setter on a loaded managed entity (persisting via JPA dirty checking at transaction commit, no repository call), an `EntityManager` or `JdbcTemplate`-typed field, a `@Modifying` or native `UPDATE` query, or a write inside a called non-repository method are all invisible to it. The check is therefore TIGHTENED for attestable targets and its guarantee stated for exactly what it proves:

- IN THE FINGERPRINTED SLICE (the target body and every in-tree declaration the category's checks read, section 4.15.4), the tool refuses the attestation if it sees: any write-set call (condition 6); any call to a method on a field typed `EntityManager`, `EntityManagerFactory`, `JdbcTemplate`, `NamedParameterJdbcTemplate`, `SqlSession`, or a `javax`/`jakarta.persistence` mutating type; any setter call (`set*`) on a value that originates from a persistence access (a loaded entity); any repository method annotated `@Modifying` in its in-tree declaration; or any `@Query` whose text (the P1b JPQL token read, section 4.2.10) begins with `UPDATE` or `DELETE` or is `nativeQuery` with a non-`SELECT` verb. A body that survives all of these is READ-SAFE AS FAR AS THE SLICE SHOWS.
- THE LIMIT, stated everywhere the safety property is claimed (this section, 4.15.7, SECURITY.md), never hidden: the check proves "no mutation is visible in the fingerprinted slice," NOT "the read performs no write anywhere." A hand-written repository or query-builder implementation two hops below the slice is NOT read (the verified rules read it; attestable reads by definition do not, because that implementation IS the filter the assertion covers), so a write hidden there is undetected. This is the exact reason CATEGORY C (remote delegations, where the read-only body is entirely unseen) is deferred with its own mandatory read-only affirmation, and the reason category B is restricted to a LOCAL persistence tie below: the further the real work sits from what the tool read, the weaker the structural half of the safety property, until at the delegation boundary it is zero and only a human's explicit affirmation can carry it.

#### 4.15.2.1 Receiver awareness in the write-set clause (designed, RATIFIED, and BUILT 2026-08-04)

THE DEFECT, precisely. The write-set clause above is the only clause of the read-safety check that judges a call by its NAME alone. It tests membership of the call's simple name in the write set and refuses, before it has looked at the receiver at all. So `costByCategory.merge(category, amount, Double::sum)`, on a `LinkedHashMap` the method itself constructed three lines earlier, is refused with "a mutating body is not a read". That sentence is false about that body: the read loads once and aggregates into a local map. The first real measurement on pms (docs/experiments/second-app-trial.md section 15) hit this on three of ten authored entries, all three genuinely read-only.

WHY THIS IS A RATIFIED REFINEMENT AND NOT A RULE-13 DIAGNOSTIC AMENDMENT. Rule 13 permits a mid-build change that alters only the wording or precision of a message and no matching outcome. This alters a matching outcome: bodies that refuse read-safety today would pass it. Passing is not discovery and not attestation (see the impact table below), but it is a different verdict at a gate, so it takes its own ratification before any code.

WHAT THE FIX CONVERGES ON, which is the argument for it. Receiver scoping is not a new liberty in this tool; it is the ratified rule everywhere ELSE the same question is asked. Section 4.2 condition 6 and section 4.10 condition 7 both require the receiver to be a repository-typed field before a mutating name counts as a write, and section 4.13.3 condition 3 does the same on the private-helper hop. The attestation check is the fourth and newest place the question is asked and the only one that asks it without the receiver. The three older rules were measured against the benchmarks and never produced this false refusal, because their receiver scoping was there from the start.

BUT THE ATTESTATION CHECK MUST NOT SIMPLY COPY THAT SCOPING, and this is the heart of the design. The discovery rules can afford to scope the write set to repository fields alone because their shape strictness (exactly one persistence access, no second access, no extra statement) leaves no room for a body to write through anything else. Attestable bodies abandon that strictness by definition, which is the whole reason this section tightened the check in the first place. Scoping the clause to repository fields would therefore silently admit `someService.save(entity)` on a service-typed field, an undetected write. The correct rule is not "refuse only on repository receivers" but "permit only on PROVABLY LOCAL NON-PERSISTENT receivers, refuse on everything else including everything unprovable".

THE RECEIVER CLASSIFICATION. A call whose simple name is in the write set is classified by its receiver into exactly three classes. Only the second is new; the first and third behave exactly as today.

- PERSISTENT: the receiver is a field whose declared type the repository catalog resolves as a repository type, or one of the persistence-infrastructure types the clause above already names, or a value that originates from a persistence access. REFUSE.
- PROVABLY LOCAL AND NON-PERSISTENT: every one of conditions L1 to L6 below holds. The call is not a write to application state, and read-safety does not refuse on it. This is the carve-out, and it is the only thing this refinement adds.
- UNPROVABLE: anything else, including every case where the tool cannot decide. REFUSE. This is the default, and the classification is biased so that it stays the default.

THE PROVABLE-LOCAL CONDITIONS. All six must hold, judged from the JavaParser AST of the fingerprinted slice alone, with no symbol solving, no type inference beyond what the initializer literally writes, no reflection, and no bytecode.

- L1, SIMPLE-NAME RECEIVER. The receiver expression is a bare simple name (`counts.merge(...)`). A field access (`this.counts`, `holder.counts`), a chained call (`byKey.get(k).remove(x)`), a static type reference (`Files.delete(p)`), an array access, and any other expression form all fail L1.
- L2, DECLARED LOCAL. That name resolves to a local variable declared by a variable-declaration statement inside the target method's own body. A method parameter, a field, a lambda parameter, a catch parameter, a for-each variable, and a variable declared in an enclosing lambda all fail L2.
- L3, FRESH CONSTRUCTION. The declarator carries an initializer that is an object-creation expression, `new T<...>(...)`. An initializer that is a method call fails L3, including the ones that look harmless (`List.of()`, `Collections.emptyMap()`, `entity.getItems()`, `repo.findAll()`). This is deliberate: distinguishing a harmless factory call from a call that hands back a persistence-managed collection requires tracing the tool cannot honestly do, so the design refuses the whole shape by name rather than admitting it on a heuristic.
- L4, ALLOWLISTED CONTAINER, WITH A NO-SHADOW GUARD. `T`'s simple name is on a closed allowlist of JDK mutable containers: `HashMap`, `LinkedHashMap`, `TreeMap`, `ConcurrentHashMap`, `EnumMap`, `IdentityHashMap`, `HashSet`, `LinkedHashSet`, `TreeSet`, `ArrayList`, `LinkedList`, `ArrayDeque`, `PriorityQueue`, `StringBuilder`, `StringBuffer`. In addition, no in-tree declaration may carry that simple name, and the compilation unit must not import that simple name from any package other than `java.util`, `java.util.concurrent`, or `java.lang`. The allowlist is closed and extending it is a design change, not a build decision.
- L5, SINGLE ASSIGNMENT. The name is assigned exactly once in the body, by its own declarator. Any further assignment to it anywhere in the body defeats the proof, whatever the assigned value.
- L6, NO PERSISTENCE ESCAPE. The name never appears as an argument to any call whose receiver is a repository field or a persistence-infrastructure field, anywhere in the body, in any order. The check is deliberately order-insensitive: a local passed to persistence before it is mutated and a local passed after are treated identically, because reasoning about the difference is reasoning about aliasing.

THE REFUSAL CASES, each carrying its own located reason so a refused body says which condition it failed and where.

- R1, receiver is a repository-typed field. (The safety case. Unchanged from today.)
- R2, receiver is a persistence-infrastructure field.
- R3, receiver is any OTHER field: a service, a client, a mapper, a cache, a facade. Unprovable, refuse. This is the case that forbids copying the discovery-path scoping, and it is why `someService.save(x)` still refuses.
- R4, receiver is a method parameter, or a local whose initializer is a parameter (an alias).
- R5, receiver originates from a persistence access: a local initialized from a repository call, or from a getter on a value that was, so a managed collection is never mistaken for a fresh one. Subsumed by L3 in practice, stated separately because it is the case the guarantee most depends on.
- R6, receiver's constructed type is not on the allowlist, or its simple name is shadowed by an in-tree declaration, or it is imported from an unexpected package.
- R7, receiver is reassigned anywhere in the body.
- R8, receiver escapes to a persistence receiver as an argument.
- R9, receiver expression is not a bare simple name.
- R10, the method has no analyzable body. (Unchanged.)

WHAT THIS REFINEMENT WILL NEVER DO.

- It never permits a write-set call whose receiver locality it cannot prove from the written AST. "Cannot tell" refuses, always.
- It never touches the other clauses of the read-safety check. The persistence-infrastructure clause, the setter-on-a-loaded-entity clause, the `@Modifying` clause, and the mutating-`@Query` clause are unchanged, and a body still has to survive all of them.
- It never widens the guarantee's wording. The claim stays exactly "no write to application state is visible in the fingerprinted slice", never "the read performs no write anywhere". The residue below the slice (a hand-written query builder two hops down) is unchanged and is still stated wherever the guarantee is stated, per the LIMIT bullet above and section 4.15.7.
- It never treats a field-held collection as local, even a `private final ConcurrentHashMap`. A field outlives the call; a fresh local does not. That is the whole distinction.
- It never grants discovery. The discovery rules (4.1, 4.2, 4.10, 4.13.3) do not call this check and are not amended by this section.

THE READ-SAFETY MIS-ANALOG, named: MIS-SAFE. This phase's version of mis-bound is MIS-SAFE, defined as a call that writes to application state being classified as read-safe. `mis-safe 0` is absolute and joins `mis-bound 0` and `attestation-drift 0` as a reported invariant. The asymmetry is stated plainly and is the design's governing bias: a FALSE REFUSAL is a coverage miss, costing one read that a human would have been allowed to attest; a FALSE PASS is a safety breach, because read-safety is the structural half of the mutation-freedom property the entire attestation phase rests on. The design prefers the miss every time. It is therefore acceptable, correct, and expected that this refinement still refuses some genuinely read-only aggregation bodies whose locality it cannot prove: an aggregation into a map obtained from a factory method, into a field-held accumulator, or through a chained receiver all stay refused, and none of them is a defect in this rule.

MATCHING-OUTCOME IMPACT, and why it is honestly near zero in the short term. Read-safety has exactly ONE call site in the tool, the attestation intake (section 4.15.4, condition 3, part 2), and it is evaluated after parameter-type mapping and BEFORE the output entity tie. So the only reads whose verdict can move are reads with an authored attestation entry that already passed parameter mapping. Passing read-safety removes a false refusal; it grants nothing. Each affected read proceeds to the next gate and is judged there on its own merits.

| Read | Verdict today | Verdict after | Next gate reached | Outcome after |
|---|---|---|---|---|
| `ReportingServiceImpl#getCostsByCategory` (pms) | READ_SAFETY | passes | output entity tie | CATEGORY_MISMATCH (shape hole, AT-2) |
| `ReportingServiceImpl#getPaymentsCollection` (pms) | READ_SAFETY | passes | output entity tie | CATEGORY_MISMATCH (shape hole, AT-2) |
| `ReportingServiceImpl#getRevenueVsCost` (pms) | READ_SAFETY | passes | output entity tie | CATEGORY_MISMATCH (shape hole, AT-2) |

Three reads change verdict, and all three land on a different refusal, not on an attestation. ATTESTED stays 0 on every benchmark. The value of the fix is therefore not coverage: it is that the refusal reasons become TRUE, and that the in-application aggregation shape stops being falsely blocked before it ever reaches the gate that should judge it. That second half is the load-bearing part. Category B (AT-2) is by definition the aggregate and wrapper category, and in-application aggregation is written with `merge` and `remove` on local collections, so under the current clause a large part of AT-2's intended intake would refuse for a reason that has nothing to do with shape. This refinement is a precondition for AT-2 being measurable at all, which is why it precedes both the E3 enum-input decision and AT-2 in the sequence.

PRE-REGISTERED FIXTURES (RS-1 to RS-8). RS-3 is the most important: it is the safety guard, and it must fail loudly if the carve-out is ever implemented too broadly.

- RS-1, the pms aggregate shape, now correctly read-safe: a method that loads through a repository once, declares `Map<String, Double> byKey = new LinkedHashMap<>();`, and calls `byKey.merge(...)` in a loop. Read-safety PASSES. A sibling case uses `remove` on a fresh `ArrayList` and also passes.
- RS-2, the whole-body assertion: the same body additionally proves that passing read-safety did not grant attestation; the entry still refuses at its next gate.
- RS-3, THE SAFETY GUARD: a write-set call on a repository-typed field (`costRepository.delete(x)`) in an otherwise identical aggregate body. REFUSES, R1. This fixture is the reason the carve-out is conditioned on provable locality rather than on receiver-is-not-a-repository.
- RS-4, the alias and escape guard: a collection obtained from a repository call or from a loaded entity's getter, then mutated (`List<Item> items = entity.getItems(); items.remove(x);`). REFUSES, L3 and R5. A second case constructs a fresh list, passes it to `repository.saveAll(batch)`, and mutates it. REFUSES, L6 and R8.
- RS-5, the unresolvable-type guard: a fresh construction of a type not on the allowlist, and separately an in-tree class named `HashMap` shadowing the JDK name. Both REFUSE, R6.
- RS-6, the parameter-aliased receiver: a method parameter of a collection type mutated directly, and a local initialized from that parameter and then mutated. Both REFUSE, L2 and R4.
- RS-7, the other-field guard: `someService.save(x)` and `someService.delete(x)` on a service-typed field inside a read body. REFUSE, R3. This is the case that would have been silently admitted by copying the discovery-path scoping, and it is why that scoping was not copied.
- RS-8, the MIS-SAFE guard: a body containing a genuine persistence write in every form the check knows (repository write-set call, `EntityManager` call, setter on a loaded entity, `@Modifying` repository method, mutating `@Query`), each alongside a legitimate provable-local aggregation in the same body. Every one REFUSES. A local carve-out never rescues a body that also writes.

FORECAST, number by number, all three benchmarks. Read-safety runs only at attestation intake, so a benchmark with no record file cannot move at all; that is a fact of the call graph, not an estimate.

- pms-backend: THREE reads change read-safety verdict, the three `ReportingServiceImpl` aggregates, on FOUR call sites (`getRevenueVsCost` has two, `costByMonth.merge` and `revenueByMonth.merge`; the other two reads have one each). All four satisfy L1 to L6 as verified read-only against source: bare simple names, locals declared in the method, `new LinkedHashMap<>()` and `new TreeMap<>()` initializers, allowlisted types with no in-tree shadow, single assignment, no escape to any persistence receiver. Intake outcome after the fix: attested 0 unchanged, refusals still 10, redistributed as enum-input 3, shape hole 7 (was 4), read-safety 0 (was 3). Discovered stays 5, BYTE-IDENTICAL. A census of every write-set-named call across pms modules (186 on repository fields, 10 on other fields, 4 provably local, 1 on a static receiver) confirms the carve-out fires on exactly those 4 and nothing else: the 10 other-field calls are controller delegations to genuine service writes, and the static `Files.delete` stays refused under L1.
- erp-backend: INERT. The same census finds 116 write-set calls on repository fields, 19 on other fields (controller delegations to service deletes), 12 on receivers that are not provable locals, and ZERO provable-local write-set calls anywhere in the application. No read changes verdict, no entry exists to change, discovered stays 25 BYTE-IDENTICAL, and the fix is a negative control here exactly as E6a was.
- The private enterprise benchmark: BYTE-IDENTICAL BY CONSTRUCTION, and this row is stated as an argument rather than a measurement because that source is not present in this working tree, only its redacted ledger. It has no `tibyan/attestations.json`, so the attestation intake never runs on it, so the single call site of this check is never reached. Discovered stays 4. Its aggregate-enrichment and guarded reads, counted only by shape in its ledger, are the population this refinement most affects LATER, once AT-2 exists and entries are authored against them; today they are untouched.

Across all three: discovered rows BYTE-IDENTICAL, and any movement in a discovered number is a REGRESSION FINDING to be reported, not reconciled. `mis-bound 0` and `mis-safe 0` are absolute.

PER RULE 12, the build is a separate later session that commits this ratified text first and then implements it exactly.

CATEGORY A, PREDICATE attestation (AT-1): what a filter, guard, or parameter SELECTS. The E6b core.

- Accepts: filtered list and search reads (envelope-wrapped or directly typed; pms `getAllByFilter`, erp `searchByFields`); filtered single reads (the foreign-key shapes, `getMyActiveLease(Long tenantId)`, `getVendorsByBuildingId`); by-id reads carrying an existence condition (the P1b inner-fetch refusal, pms Tenant, and the soft-delete-guarded by-id reads of the enterprise benchmark: structurally identical holes, a by-primary-key load plus a predicate the tool cannot interpret).
- The human asserts, minimally and sufficiently: (1) ONE result-semantic sentence, what the read returns as a whole ("returns the tenants matching the given filters, paged"); (2) PER DOMAIN PARAMETER, one meaning sentence ("fullName: matches tenants whose full name contains this text, case-insensitive"); (3) for an existence-condition read, the condition's meaning ("returns the tenant with this id only if it has an associated user; otherwise not found"). Every input MUST be covered (see the coverage rule below); an uncovered name in a tool schema is exactly the modeled-opaque claim the E6b verdict rejects.
- THE `paging` MARK, restricted (added after adversarial review; an unrestricted mark re-admitted modeled-opaque). A parameter may be covered by the reserved `paging` mark INSTEAD of a sentence ONLY WHEN it is structurally recognizable as framework paging or ordering: the Spring types `Pageable` or `Sort`, or a parameter belonging to the manually-paged idiom the tool recognizes structurally (an `Integer`/`int` parameter whose name, case-insensitively, is one of a fixed set `page`, `pageNum`, `pageNumber`, `pageSize`, `size`, `pageOffset`, `offset`, `limit`, or an ordering parameter of a `Sort.Direction`-like enum or a `String`/enum whose name is `orderBy`/`sortBy`/`orderDir`/`sortDir`/`direction`). The mark is NOT a free choice: intake refuses a `paging` mark on any parameter outside this recognized set, so a domain parameter (`email`) can never be hidden behind it. And the mark DOES travel to every surface: a `paging`-marked input renders with the fixed, tool-authored description `Paging or ordering parameter.`, so no input ever stands in an attested schema with its meaning unstated. This keeps the anti-modeled-opaque guard structural, not a matter of trust.
- Coverage rule (this is intake condition 4, section 4.15.4): every one of the target's inputs has exactly one coverage in `parameterMeanings`: a `paging` mark (restricted above), the reserved `id` mark for the structurally-proven by-primary-key identifier (a category A existence-condition read keeps its structural id), or a human meaning sentence. The keyset of `parameterMeanings` equals the set of the capability's inputs, which is what makes the completeness invariant checkable by `tibyan-core` (Volume 4, rule 8).
- THE GUARD SHAPE, defined structurally (added after adversarial review; "minus the guard refusal" was underspecified). For an existence-condition read, the ONE statement the assertion is permitted to cover, beyond the section 4.13.3 clean load and its single mapping step, is a GUARD of exactly this shape: a single `if` whose condition is side-effect-free (only field accesses, getter calls, comparisons, and boolean operators, no assignment and no non-getter call) and whose only branch is a `throw`. Anything else the body contains, an assignment, a setter on the loaded entity, a second access, a non-getter call, a loop, is NOT a guard and is not covered by the assertion: it fails the read-safety check and the read stays refused. The guarded read is the cleanest AT-1 attestation precisely because its one extra statement has a proven, narrow shape.
- The tool still verifies: the target's signature and parameter types; the binding; the read-safety check over the visible body and, for the guarded shape, over the section 4.13.3 helper walk (with the one permitted guard shape above the only tolerated extra statement); the output element tie via T1/T2/view machinery where provable. If the element tie is NOT provable, the read also has a shape hole; the entry may cover both holes (the compound rule, section 4.15.3), or AT-1 alone leaves it refused with the worklist naming the remaining shape hole.

CATEGORY B, SHAPE attestation (AT-2): what a read RETURNS when structure cannot tie it. The E6a-opaque core, restricted to a LOCAL persistence tie.

- THE LOCAL-TIE PRECONDITION (added after adversarial review; it is what keeps category B honest). A category B target is accepted ONLY IF its fingerprinted slice contains at least one in-tree persistence access the read-safety check could inspect: a repository access (the T2 shape, even when its element type does not resolve to a nameable entity, which is exactly the hole the assertion fills) or a same-class enrichment whose accesses the check reads. An opaque wrapper or aggregate whose real work is a DELEGATION (a service-to-service or Feign call, an unparseable or unresolvable delegate: precisely the canonical OPAQUE_WRAPPER of section 4.11.7.2 condition O, and the twin-proxy aggregate of the enterprise benchmark) has NO local persistence to inspect, so its read-safety is structurally unprovable. Those route to CATEGORY C (AT-3) BY NAME, never to AT-2; the read-safety limit above is why. Category B thus admits the aggregate whose load is local and the wrapper whose repository access is in-tree, and refuses the delegation-built wrapper to AT-3.
- Accepts (under the precondition): local aggregate-enrichment by-id reads (the clean by-primary-key load plus further in-tree accesses and a composite mapper: pms Building, Property, ChatRoom; the enterprise benchmark's 12 local aggregates); in-tree opaque wrapper returns whose repository tie the tool sees but whose element type it cannot resolve to a nameable entity; projections and hand-built VTOs the view machinery cannot resolve but whose load is local.
- The human asserts, minimally: (1) the subject entity ("this read is a read of Building"); (2) the result-shape sentence ("returns the building with this id, enriched with its units and their count"); (3) for an aggregate, that the enrichment does not change WHICH root row is returned (the by-id claim survives the enrichment). The assertion never enumerates the wire fields (the tool does not model them, and the OpenAPI surface stays a permissive object exactly as Volume 6, section 4.1's partiality rule renders a view).
- The tool still verifies: the local-tie precondition; for an aggregate, the by-primary-key load of the asserted entity IS structurally present (section 4.2 condition 4; the assertion covers the enrichment, never the load); the asserted entity exists in the model and no structural evidence contradicts the assertion (a body whose single repository access provably reads a DIFFERENT entity refuses the assertion, section 4.15.7); and the read-safety check over the whole slice.

CATEGORY C, BOUNDARY attestation (AT-3, DEFERRED BY NAME, elevated stakes). Two members, deliberately grouped because a false attestation in either breaches a SECURITY boundary rather than mislabeling data:

- Security contracts: a SECURITY-UNVERIFIABLE capability from section 4.14 (an unparseable expression, an unresolvable constant, conflicting declarations) attested with its required authorities. A false attestation here becomes an access-control decision in the consumer's enforcement layer: the stakes are not "an agent misreads data" but "a caller is granted or denied access on a false premise."
- Remote delegations: the delegation-only facades (the enterprise benchmark's 15 Feign and cross-service reads). The local method is invocable and its signature checks, but the tool cannot see the remote side, so the human must assert READ-ONLY-NESS ITSELF ("invoking this performs no write anywhere"). A false attestation here silently enables a write behind a read-labeled tool, violating the standing read-only policy gate (SECURITY.md).
- Why deferred rather than thinned into AT-1: every AT-1 and AT-2 target passes the read-safety check over what the tool actually read (a local persistence tie the check can inspect, no write visible in the slice), so the worst false attestation there mislabels the semantics of a call the tool proved has no visible write. Category C's members have NO local persistence in the slice at all (a delegation's real work is behind an unseen remote or cross-service call), so even the slice-level read-safety is vacuous and the read-only property rests entirely on a human's word. That is the elevated stake, and it is the first place the SAFETY property, not just the data semantics, would rest on the assertion. That deserves its own ratification with stronger guards, sketched now so the deferral is not vague: a distinguished asserter field (the attester asserts they own the security decision, not merely the code knowledge), a mandatory explicit `readOnly` affirmation for delegations, a separate survey class so boundary attestations are never visually mixed with data attestations, and a consumer-facing marker distinct in name (`attestedBoundary`), all to be argued in AT-3's own design session. Until AT-3 is ratified and built, these refusals stay refused.

EXCLUDED, each with its reason, none attestable in any sub-phase of this design:

- Writes and actions: the standing policy gate (SECURITY.md; Volume 2 NFR-S2). Out, unchanged, and an assertion that a mutating body "is really a read" is refused at intake as a structural contradiction.
- Parse failures: nothing was seen, so there is no element to attach an assertion to and no fingerprint to pin it against. The honest fix is fixing the source (the pms re-baseline proved how cheap that can be), never attesting into the dark.
- Stubs and no-persistence bodies: a body with no persistence access and no delegation (pms's hardcoded empty-ResultSet endpoints) has no read to describe. An assertion here would fill a structural hole (the read's existence), which assertions never do. Refused at intake.
- Ambiguous bindings (AMBIGUOUS_BINDING, LIST_AMBIGUOUS): choosing among structurally valid candidates is selecting a BINDING, a structural fact. Attestation never selects bindings; that hazard is exactly what zero-drift forbids entering through the side door. The ambiguity stays a refusal (the future named-reads question, per the ledgers).
- Context-dependent reads (the me-scoped `RequestContext` reads, pms's 9): the missing piece is a runtime INPUT, the caller's identity, which no assertion can conjure. Exposing them today would invoke with an empty context and fail or lie. Excluded until a principal-passing runtime design exists (a named future decision, Volume 7), which is invocation architecture, not attestation.
- Binary and stream returns (`byte[]`, `Resource`, SSE): the wire shape is outside the modeled JSON surface. Named deferral, not an attestation problem.
- Composite keys and other eligibility refusals: structural, unchanged.

What this set will never do: never attest a write or action; never let an assertion create a binding, a parameter, or a read that structure does not show; never accept an assertion for a category outside the ratified set; never blur the category taxonomy (a predicate assertion cannot smuggle a shape claim; each hole is filled by its own category or stays refused).


#### 4.15.2.2 AT-2 build record: what category B resolved, and the three decisions the ratified text left open (BUILT 2026-09-01)

The SHAPE category is built exactly as category B above specifies: the local-tie precondition, the asserted subject entity's existence in the model, structural non-contradiction, the by-primary-key obligation for an aggregate, and the read-safety check over the whole slice. What follows is the record of what the build had to decide, because the ratified text stated each of these as a sentence rather than as a checkable condition, and a build that quietly picked an interpretation would be exactly the drift rule 14 exists to prevent.

DECISION 1: THE AGGREGATE DISCRIMINATOR IS THE RESERVED `id` MARK. Category B says "for an aggregate, the by-primary-key load of the asserted entity IS structurally present", and it does not say how the tool knows an entry claims an aggregate. The build reads the claim off the coverage map: an entry that covers any input with the reserved `id` mark is claiming a by-primary-key read, and intake then requires that load to be present, of the asserted entity, in the body. The argument is that this is what the mark already means (section 4.15.2 defines it as "the reserved `id` mark for the structurally-proven by-primary-key identifier"), and until now nothing proved it: AT-1 checked only that the marked parameter's TYPE was an identifier type. So the obligation makes the mark honest rather than adding a new one. Scope, stated because it is an asymmetry: the obligation is enforced for SHAPE entries only. Extending it to the predicate path would change AT-1 outcomes and belongs to its own decision, not to this build. Fixture: the foreign-key target, where the mark is claimed over a filter query and refuses.

DECISION 2: A SENTENCE-COVERED INPUT MEANS THE TARGET ALSO HAS A PREDICATE HOLE. The text says "a `shape` entry against a target with uncovered domain parameters refuses (use `both`)", but intake condition 4 forbids an uncovered input outright, so no shape entry can ever reach that refusal as literally worded. The build resolves it structurally: only the reserved `paging` and `id` marks are STRUCTURAL coverage; a meaning sentence is a PREDICATE claim. So a shape entry that covers any input with a sentence is carrying a predicate assertion, refuses, and names `both`. This is the "never blur the category taxonomy" rule (section 4.15.9) made checkable. It is also the honest reason AT-2 alone unlocks less than the section 4.15.11 ceilings imply: those ceilings count targets whose category is attestable, and a filtered read whose element type is also unresolvable needs `both`, which is not built.

DECISION 3: THE FINGERPRINTED SLICE IS ONE FUNCTION OF THE TARGET, NOT OF THE CATEGORY. Section 4.15.4 enumerates "the wrapper class declaration" among the slice members, which is a category B member; but the CLI's stamping contract is `fingerprintFor(target)`, with no category in hand. A category-dependent slice would give one target two fingerprints, so changing an entry's category would silently re-stamp it, which is the drift the fingerprint exists to catch. The build therefore adds the target's RETURN TYPE declaration, where it resolves to exactly one in-tree type, to the slice for every category. The return type is frame-load-bearing for a predicate entry too, since the output tie reads it, so this strengthens AT-1 rather than perturbing it. A colliding simple name is left OUT of the slice rather than pinned to an arbitrary declaration, because pinning by scan order would make the fingerprint non-deterministic (rule 10). Cost, stated: this changes the fingerprint of every existing target, so any already-stamped entry goes STALE and must be re-attested. No real attestation exists in the field yet (the open verification below), so the cost is zero in practice and is recorded rather than discovered later.

SCOPE, DELIBERATE: `both` IS NOT BUILT. AT-2's ratified scope is "adds one category", and the compound entry that fills a predicate hole and a shape hole at one target with one minted name is not that category. It refuses with a located "not yet ratified and built" message naming both built categories. Every compound target therefore stays refused, which is the correct conservative outcome and the main limit on what this build unlocks.

ONE SURFACE ADDITION, and Volume 6 is amended to match in the same commit: the manifest and OpenAPI attestation objects now carry `subjectEntity`, present only where it exists. On a shape claim the subject IS the claim, so leaving it off the surfaces would have broken character-exact carriage (ATT-4) for the one field category B adds. A predicate attestation carries none, so an AT-1 manifest is byte-identical to what it was.

MEASURED (2026-09-01): all three benchmark applications are BYTE-IDENTICAL across the serialized IR, the generated MCP output, and the generated OpenAPI document, verified by `diff -r` against a worktree built at the pre-build commit. None of them carries a `tibyan/attestations.json`, so the phase is provably additive exactly as fixture ATT-11 requires: pms 5 discovered, erp 35, as-backend 5, mis-bound 0, attested 0 everywhere.

THE OPEN VERIFICATION, unchanged and now more pointed: no real attesting human has run this flow, and the build did not manufacture one. Authoring an entry would mean the TOOL typing an assertion about code it does not know, which section 4.15.9 forbids in the first line ("every attested word was typed or confirmed by a named person"). The fixtures prove the mechanism; the first real attestation is the adopting team's to write, and the first-use measurement designed in section 4.15.11 is what decides whether AT-3 proceeds.


#### 4.15.2.3 AT-3, the BOUNDARY category: the design session category C was deferred to (designed 2026-09-01, RATIFIED 2026-09-01)

Category C above deferred this sub-phase BY NAME and sketched four guards to be argued in its own session. This is that session. Nothing here loosens category C's statement of the stakes; it makes each sketched guard a checkable condition, and it adds one refusal the sketch did not name.

WHY THIS CATEGORY IS DIFFERENT, restated because every condition below descends from it. For AT-1 and AT-2 the tool's read-safety check runs over a body with a LOCAL persistence tie it could actually inspect, so the worst a false attestation does is mislabel the semantics of a call the tool proved has no visible write. Category C's members have no such tie. For a remote delegation the real work is behind a call the tool cannot see, so slice-level read-safety is very nearly vacuous and the READ-ONLY property itself rests on a human's word. For a security contract the assertion is not about data at all: it becomes an access-control decision in the consumer's enforcement layer. In both, a false assertion breaches a SECURITY boundary rather than mislabelling data. That is the elevated stake, and it is why this category gets its own name, its own count, its own marker, its own asserter field, and its own exposure default.

THE TWO MEMBERS, and what each one's human asserts.

- C1, THE SECURITY CONTRACT. Target: a capability the tool already emitted carrying `securityUnverifiable: <reason>` (section 4.14). The tool saw that a declared contract EXISTS and could not compile it. The human asserts the authorities that contract requires.
- C2, THE REMOTE DELEGATION. Target: a read whose body has NO in-tree persistence access, which is exactly what AT-2's local-tie precondition routes here. The human asserts the subject entity and the result shape, as in category B, AND asserts READ-ONLY-NESS ITSELF: that invoking this performs no write anywhere, including behind the delegation the tool cannot see.

An entry may cover a target that is both, and then it carries both assertions; intake requires whichever the target's holes demand and refuses the rest.

C1 RESOLVED (2026-09-01, the session after the C2 build): the targeting model the scope split below could not settle is settled, and the answer narrows C1 rather than widening it. A C1 assertion is carried BY A BOUNDARY ENTRY, on the read that entry is already attesting; it is never an entry of its own against an already-emitted capability. That single decision dissolves all three blockers at once: the target is a refused read like every other attestation target, one entry still mints one capability so binding uniqueness is untouched, and no human assertion is ever attached to a VERIFIED capability. What stays refused, with its own located reason, is C1 OVER A VERIFIED CAPABILITY. Two arguments, and the second is the decisive one. Core rule 9 would force such a capability to be renamed `attested_`, breaking the name a consumer already binds to. And more fundamentally, a verified capability whose declared contract the tool could not compile has a SOURCE problem: the honest fix is to make the declaration readable, not to assert over it and leave the unreadable declaration in place. Asserting there would convert a fixable source defect into a permanent human liability, which is the opposite of what this project does with every other refusal.

WHAT THE TOOL VERIFIES AROUND A C1 ASSERTION, given that section 4.14's declared-security composition runs AFTER intake and therefore cannot be consulted at intake time. Intake checks the assertion's own shape: every asserted authority is a non-empty string and the values are distinct. The composition then runs as it always has, and CORE RULE 11 GAINS THE CONTRADICTION GUARD it needs as a result: a capability must never carry BOTH a compiled `requiredAuthorities` and a non-empty `assertedAuthorities`. If the composition proved a real contract for that read, the assertion is at best redundant and at worst contradicts a fact the tool established, and the model fails loud rather than shipping two answers to one question. This is the only place the two axes can collide, and it is checked where the whole model is visible.

WHAT A C1 ASSERTION IS NOT, restated because it is the condition the whole member exists under: it does not populate `requiredAuthorities`, it does not clear `securityUnverifiable`, and it renders under its own name on every surface. A consumer's enforcement layer therefore reads three separate facts, never one blended one: the tool could not compile this contract, a named person asserts these authorities, and that person is named as the security owner.

SCOPE SPLIT as first recorded (2026-09-01, the C2 build session), retained because it is the accurate record of what that build decided and why, and superseded above only for C1: THAT BUILD IMPLEMENTED C2 ONLY. C1 stays refused, with its own located message, for a reason that is structural and not a matter of effort. C2's target is a REFUSED read, which is the targeting model every attestation category already uses: one entry, one refused read, one minted capability. C1's target is a capability the tool ALREADY EMITTED, and that does not fit the model without a decision this session is not willing to guess at. Minting a second capability for it collides with binding uniqueness (Volume 4, rule 7); enriching the emitted one would put a human assertion onto a VERIFIED capability, which is the blur section 4.15.9 forbids outright; and folding the authority assertion into the same entry that attests the read makes `boundary` behave as a compound category, which is the thing `both` exists for and `both` is not built. Each of those three is a real design decision with a security consequence, and the category's whole reason for deferral was that its decisions carry security consequences. So C1 is refused by name, its fields (`assertedAuthorities`) exist in the component and are validated by rule 11 so the shape is settled, and the targeting decision is its own step. The counted evidence backs this order: the enterprise benchmark's 15 delegation reads are C2, and its declared-security contracts are the smaller set.

#### THE FOUR RATIFIED GUARDS, each as a condition

GUARD 1, THE DISTINGUISHED ASSERTER. A boundary entry carries `securityOwner`, a non-empty name, IN ADDITION to `assertedBy`. The two are separate fields on purpose and may name different people: `assertedBy` says who wrote the words, `securityOwner` says who OWNS THE SECURITY DECISION this entry makes. Signing the second is a different act from knowing the code, and the design refuses to let one signature stand for both. A boundary entry without it is refused; a predicate or shape entry that carries it is refused too, because a data attestation must never look like a security one.

GUARD 2, THE MANDATORY READ-ONLY AFFIRMATION. A C2 entry carries `readOnlyAffirmed: true`, explicitly. Absent, false, or any other value refuses. The affirmation is not a formality and its wording in the record is the claim itself: the asserter states that invoking this capability performs no write ANYWHERE, which is the property the tool cannot check for this member and the standing read-only policy gate depends on (SECURITY.md). There is no default and no inference: the tool never treats silence as an affirmation.

GUARD 3, THE SEPARATE SURVEY CLASS. Boundary attestations are counted and reported as their own class, `attested-boundary K`, NEVER summed into `attested` and never into `discovered`. The health line becomes `discovered N, attested K, attested-boundary B, mis-bound 0, attestation-drift 0, mis-safe 0, mis-constrained 0`. A reader who scans only the totals must be able to see that a security-boundary assertion is in play without reading a single entry.

GUARD 4, THE CONSUMER-FACING MARKER, DISTINCT IN NAME. Two distinctions, because the sketch asked for the name and the name is where an agent acts:

- THE MINTED NAME carries the class: a boundary capability is named `attested_boundary_<name>`. It still begins with the reserved `attested_` prefix, so core rule 9 is unchanged in both directions and no verified capability can collide with it. An agent listing tools sees the elevated class before reading a description, exactly as section 4.15.6 argued for `attested_`.
- THE SURFACES carry `attestedBoundary: true` (MCP) and `x-tibyan-attested-boundary: true` (OpenAPI), alongside the existing attested marker rather than instead of it, and the description leads with a STRONGER label than the data categories use: `Human-attested BOUNDARY, not tool-verified: its read-only-ness or its security contract rests on a human assertion, not on structure.`

#### THE FIFTH GUARD, ADDED IN THIS SESSION AND NOT IN THE SKETCH

GUARD 5, EXPOSURE IS OPT-IN, DEFAULT OFF. A boundary capability does not register in the runtime unless `tibyan.attested.exposeBoundary=true` is set deliberately. The two existing controls do not cover this case: `tibyan.attested.expose` defaults to TRUE and would ship a boundary tool by default, and `exposeSecurityUnverifiable` governs only C1's marker, not C2's read-only claim. The argument for adding it is the same argument that deferred this category: for every other attested capability the tool proved no write is visible in what it read, and for C2 it proved nothing of the kind. A capability whose read-only-ness is a human's word should require a deliberate choice to expose, in the same way the project already requires one for a bypassed security gate. This is the one place this session went beyond the sketch, and it is recorded as such rather than folded in silently.

#### WHAT THE TOOL STILL PROVES, and the refusal the sketch did not name

Around every boundary assertion the structural frame is unchanged and non-negotiable: the target resolves to exactly one in-tree declaration; at most one entry per target; every parameter exists and its type maps (with the Enum Inputs rule applying exactly as elsewhere); the coverage rule is complete and exact; the name is valid, unprefixed by the human, and unique after minting; provenance is complete and tool-stamped; and the fingerprint matches the slice on this run's code. A boundary entry earns no relief from any of these.

READ-SAFETY STILL RUNS, and its result is honestly weaker here, which is the point. The check runs over the visible body and refuses on a visible write, so a local `save` before a delegation still refuses the entry. What it cannot do is see past the delegation, and the affirmation of guard 2 is precisely the human taking responsibility for that residue. The design states the split rather than blurring it: the tool proves no write is visible in what it read, the human affirms no write happens anywhere, and the surfaces say which is which.

THE REFUSAL THE SKETCH DID NOT NAME, and it is the most important condition in this section: AN ATTESTED AUTHORITY IS NEVER A COMPILED AUTHORITY. A C1 assertion does NOT populate `requiredAuthorities`, and it does NOT clear the `securityUnverifiable` marker. It rides its own field, `assertedAuthorities`, and the unverifiable marker STAYS on the capability, because it remains true: the tool could not compile that contract and still cannot. Rendering an asserted authority as a compiled one would be the exact shape section 4.15.9 forbids ("never present attested as verified, on any surface, in any count, at any level, including the name"), and it would do it in the one place where the consequence is an access-control decision. A consumer's enforcement layer therefore sees three separate facts: the contract is unverifiable, a named person asserts these authorities, and that person is named as the security owner.

THE LOCAL-TIE MIRROR. C2 requires the ABSENCE of an in-tree persistence access, which is the exact complement of category B's precondition. A target with a local tie is a category B target and refuses here with "use category shape"; a target without one is a category C target and refuses in AT-2 with "use category boundary". The two preconditions partition the space, so no target is attestable under both and none falls between them.

#### THE MIS-ANALOG: MIS-BOUNDED

This sub-phase's version of mis-bound. A capability is MIS-BOUNDED when an asserted authority is rendered as a compiled authority anywhere, when a boundary attestation is counted as a data attestation or as discovered, when a C2 entry ships without an explicit read-only affirmation, or when a boundary capability registers in a runtime that was not deliberately told to expose one. `mis-bounded 0` is absolute and joins the other four invariants.

#### PRE-REGISTERED FIXTURES (ATT-13 to ATT-19)

- ATT-13, C1 positive: a `securityUnverifiable` capability with an authority assertion emits with `assertedAuthorities`, the unverifiable marker STILL PRESENT, `requiredAuthorities` still absent, and the boundary marker and name prefix on every surface.
- ATT-14, C2 positive: a delegation-only read with a subject entity, a result shape, and `readOnlyAffirmed: true` emits as `attested_boundary_<name>`.
- ATT-15, the affirmation guard: the same C2 entry with the affirmation absent, false, or non-boolean refuses, three cases, each located.
- ATT-16, the asserter guard: a boundary entry with no `securityOwner` refuses; a predicate or shape entry that carries one refuses.
- ATT-17, the partition guard: a boundary entry on a target WITH a local persistence tie refuses naming category shape; a shape entry on a delegation refuses naming category boundary (the AT-2 behavior, asserted here as the mirror).
- ATT-18, the never-compiled guard, this sub-phase's mis-bind fixture: an asserted authority never appears in `requiredAuthorities`, never clears `securityUnverifiable`, and never renders on any surface as a compiled contract.
- ATT-19, the counting and exposure guards: `attested-boundary` is counted separately from `attested` and from `discovered` on every surface; and a boundary capability does not register unless `exposeBoundary` is set.

#### FORECAST

The private enterprise benchmark holds the counted evidence for this category (ledger section 8): 15 by-id delegation reads for C2, plus its declared-security contracts for C1, all currently refused. That source is not in the working tree, so this row is an argument and not a measurement, exactly as the enum row was. pms, erp, and as-backend carry no record file, so all three are BYTE-IDENTICAL by construction: nothing can move without an authored entry, and no discovered capability is touched by any condition above. Any movement on a discovered count is a regression finding that stops the phase.

THE OPEN VERIFICATION, and for this category it is sharper than for the others: no real attesting human has run this flow, and for a boundary entry the person signing is taking on a security decision, not a documentation one. The first-use measurement designed in section 4.15.11 applies here with one addition: record whether the security owner and the asserter were the same person, because a flow where they always are is a flow where guard 1 is decoration.

#### 4.15.3 The assertion experience: the worklist, the record, and the format decision

The worklist. The scan survey's refusal ledger becomes the attestation worklist: every refusal whose category is attestable gains one line, `attestable: <category> (assert: <what>)`, naming exactly which assertion would expose it and which sub-phase carries that category (a hole whose category is not yet built says so: `attestable under AT-2, not yet ratified`). A refusal outside the attestable set says nothing new. The survey is already the honest map of what the tool saw; this makes it the to-do list a team can work through.

The format decision, argued. Three candidates:

- Annotations in the legacy source: REJECTED. Tibyaan's core promise is that the host system is never edited; requiring `@TibyanAttest` annotations in the adopter's code breaks that promise, forces a dependency into the host, and puts assertions where the tool's own users may not have write access.
- A CLI dialogue alone: REJECTED as the record (kept as the experience). An interactive session is how a human attests comfortably, but a dialogue's output must land somewhere reviewable; ephemeral answers are unreviewable, undiffable, and invisible to the team.
- A COMMITTED FILE in the adopter's repository: ACCEPTED. `tibyan/attestations.json`, hand-editable JSON (no new dependency; the project's own surfaces are hand-rolled JSON), diffable, reviewable in a pull request (a team's natural confirmation ritual), attributable through version control on top of its own provenance fields, and it survives re-scans because it lives beside the source it describes. The CLI dialogue (`tibyan attest`, built in AT-1) WRITES entries into this file; the file, not the dialogue, is the record. A team member without the CLI can author an entry by hand; intake validation treats both identically.

The record, one entry per attestation:

```json
{
  "target": "com.pms.tenant.core.service.TenantServiceImpl#getAllByFilter(String,String,String,OrderDirections,Integer,Integer,Integer)",
  "category": "predicate",
  "name": "searchTenants",
  "resultSemantic": "Returns the tenants matching the given filters, paged.",
  "parameters": {
    "fullName": "Matches tenants whose full name contains this text, case-insensitive.",
    "email": "Matches tenants whose email contains this text.",
    "orderBy": "paging", "orderDir": "paging",
    "pageNum": "paging", "pageSize": "paging", "pageOffset": "paging"
  },
  "provenance": {
    "assertedBy": "a.elseginy",
    "origin": "human-authored",
    "date": "2026-07-21",
    "fingerprint": "sha256:..."
  }
}
```

- `target` is the structural anchor: the declaring class's fully-qualified name, the method name, and the parameter types as written (fully qualified where an overload would otherwise be ambiguous), resolved through the section 4.12.4 discipline to exactly one declaration or refused. AT MOST ONE attestation entry may target a given declaration (intake condition, section 4.15.4): a target has one attested capability, never two, which is the pooling discipline (section 4.14.3) carried into attestation so two entries can never mint two capabilities for one read.
- `name` is the capability name the human mints (the tool prefixes it, section 4.15.6). Filtered and shaped reads are business reads; the structural `get<E>`/`list<E>` naming does not apply to them (this is the one deliberate exception to the display-name-derivation rule of Volume 4, section 3.7 and Volume 6, section 3.4, carved explicitly there): the human who knows what the read means names it, the tool reserves the class prefix, and uniqueness is enforced against all capabilities.
- `category` is `predicate`, `shape`, or `both`. It must MATCH the holes the tool found at that target: a `predicate` entry against a target with an unresolved element tie refuses (it has a shape hole too, use `both`); a `shape` entry against a target with uncovered domain parameters refuses (use `both`). The `both` category is the compound case (a filtered read whose element type is also unresolvable): one entry, one minted name, one capability, whose `parameterMeanings` covers the predicate hole and whose `resultSemantic` plus the subject-entity field covers the shape hole. This closes the multi-hole target: exactly one entry per target, filling every hole that target has or refusing.

BUILT 2026-09-01, with one build decision recorded rather than left to diverge. "The category must match the holes" is enforced on the PREDICATE half only, and enforced in both directions there: a `shape` entry with a sentence-covered input refuses and names `both`, and a `both` entry with no sentence-covered input refuses and names `shape`. The presence of a meaning sentence IS the presence of a predicate hole, so that half is cleanly decidable. The SHAPE half is not separately enforced, and the reason is structural rather than an omission: the E6a transparency tie (T2, built as AT-0) resolves a wrapper subject from the repository access behind it, so a wrapper whose ELEMENT type is not an in-tree entity can still have a provable tie, and "the element type is unresolvable" therefore does not decide whether a shape hole exists. What guards the case an unprovable-tie precondition would have guarded is the shape frame NON-CONTRADICTION check, which applies to a compound entry unchanged: an assertion that disagrees with what the target single repository access provably reads refuses. Enforcing an unprovable-tie precondition on `both` alone would also have made the compound category STRICTER than `shape`, which no ratified text asks for. Everything else about the compound entry is the shape frame run unchanged: the local-tie precondition, the asserted subject existence, non-contradiction, the by-primary-key obligation of the reserved id mark, and read-safety. `both` adds a hole to fill; it never removes a check.
- The `date` and every provenance field come from the FILE, authored data, never from a clock the tool reads: generation stays deterministic (CLAUDE.md rule 10), same inputs, same output, byte for byte.
- `fingerprint` is computed by the tool, never hand-typed. A hand-authored entry that omits it does NOT ship on that run: intake refuses it with a located "not fingerprinted, run tibyan attest to stamp it against the current code" message (section 4.15.4, condition 6). The `tibyan attest` CLI computes the fingerprint over the current code AND stamps `date` in the same action, so a fingerprint always co-arrives with the date of the code it pins; there is no path that emits a capability from an un-fingerprinted or human-fingerprinted entry. Its definition and role: section 4.15.4.

Who attests: whoever the adopting team trusts to know the code, recorded by name in `assertedBy` (a non-empty free string; the tool does not authenticate humans, version control and the team's review process do). The file is the team's shared record; the PR that adds an attestation is the team's confirmation ceremony.

#### 4.15.4 Intake validation and the re-scan lifecycle: staleness by construction

INTAKE, at attestation time, loudly, never discovered later at generation time. Every entry is validated when the file is read, and a failing entry is REFUSED with a located message naming the entry and the failure; a refused entry produces no capability and no partial carriage:

1. The `target` resolves to exactly one in-tree declaration (section 4.12.4 scoping; zero or many refuses), and no other entry targets the same declaration (at most one attestation per read, section 4.15.3).
2. The target's refusal category matches `category`, and that category's sub-phase is ratified and built (an AT-2 assertion under an AT-1-only build refuses with "category not yet ratified"; a delegation-backed target with no local persistence tie refuses to AT-3 by name, section 4.15.2 category B).
3. The structural frame verifies: signature and parameter names exist as asserted; every parameter type maps through the standard mapping; the READ-SAFETY CHECK (section 4.15.2) passes over the whole fingerprinted slice, with the one permitted guard shape the only tolerated extra statement for an existence-condition read; for shape (and `both`), the by-primary-key load or the local-tie precondition and the wrapper's non-contradiction hold (section 4.15.7).
4. Coverage is complete and exact: the keyset of `parameterMeanings` equals the capability's input set; every input carries a human meaning sentence, a restricted `paging` mark (section 4.15.2), or the reserved `id` mark for the structurally-proven identifier; none is uncovered (the anti-modeled-opaque guard) and none names a non-input.
5. The minted `name` is valid (`[A-Za-z][A-Za-z0-9_]*`), does NOT itself begin with the reserved `attested_` prefix (so the tool's prefixing never yields `attested_attested_`), and is unique across ALL capabilities after prefixing; core validation independently reserves the prefix in both directions (Volume 4, rule 9).
6. Provenance is complete AND tool-stamped: non-empty `assertedBy`, a valid `origin` token (a `human-authored` or `ai-drafted-human-confirmed`; `ai-draft` refuses), a `date`, and a `fingerprint`. An entry missing its fingerprint is REFUSED with the "run tibyan attest to stamp it" message, never silently completed and shipped (section 4.15.3); the CLI computes the fingerprint and stamps the date together against the current code.

THE RE-SCAN LIFECYCLE, the hardest sub-problem, decided: PIN TO A STRUCTURAL FINGERPRINT, INVALIDATE LOUDLY ON MISMATCH.

- The fingerprint is a SHA-256 over the normalized source (comments stripped, whitespace collapsed) of THE STRUCTURAL SLICE INTAKE READ, concatenated in a stated canonical order: the slice's declarations are sorted by their fully-qualified name, then each declaration's normalized text is joined with a separator, and the SHA-256 is taken over that single string (a pure function of the source, deterministic, CLAUDE.md rule 10). The slice is everything the tool read to accept the assertion's frame, enumerated completely so nothing load-bearing drifts unpinned: (1) the target method's full declaration and body; (2) the COMPILATION UNIT IMPORTS of every file in the slice (a changed import silently re-resolves a type the tie depended on); (3) every in-tree declaration the category's checks visited, which for the T2 tie means the repository FIELD declaration, the repository INTERFACE declaration and its entity type argument, and the resolved implementation body for a one-hop trace; the guarded helper's body; the wrapper class declaration; and for an anchor-carried read the controller method and the overridden interface method. The principle, now enforced by the completeness of that list: the attestation is pinned to everything that could change the frame, so if the frame changes, the fingerprint changes.
- On every scan, the fingerprint is recomputed and compared. MATCH: the attestation carries. MISMATCH: the attestation is INVALIDATED LOUDLY, the capability returns to REFUSED with a new located cause, `STALE_ATTESTATION`, naming the entry, the target, and that the code changed since attestation; the survey lists it in the worklist as needing re-attestation; generation simply does not emit it. A stale attestation silently carried is impossible by construction, because carriage is gated on the fingerprint comparison in the same code path that emits the capability, and there is no path (section 4.15.3) that emits from an entry the tool did not itself fingerprint on this run's code.
- The alternatives, and why they lose: invalidate-on-ANY-repo-change is uselessly noisy (an unrelated file invalidating every attestation trains teams to rubber-stamp re-attestation, the worst outcome); trusting the entry until a human notices is silent drift, forbidden by the invariant. The fingerprint is the narrowest honest pin: exactly the read slice and what it resolves through, nothing else.
- The honest limit, stated (and it is the SAME limit as the read-safety check, section 4.15.2): the fingerprint covers what intake READ. Semantics can drift BELOW the fingerprinted slice (a hand-written query builder two hops down, whose body the tool never reads because it is the filter the human attested, changes while the slice is byte-identical). The tool cannot fingerprint what it never read, so the posture (section 4.15.7 and SECURITY.md) states plainly: the fingerprint detects change at the asserted element and everything it structurally resolved through; drift beneath that (the query builder's own logic) is the asserting team's ongoing responsibility, which is one more reason the record lives reviewably in their repository. This is not a weakness hidden by the fingerprint; it is the exact boundary of the structural half of the guarantee, stated wherever the guarantee is stated.
- Re-attestation is a fresh assertion: the human re-confirms (or corrects) against the changed code, the CLI recomputes the fingerprint and stamps a fresh date, and the old entry is replaced in the file (version control keeps the history). There is no "strip the fingerprint to re-arm it" shortcut: an entry missing its fingerprint is refused, not silently recycled, so a stale entry cannot be laundered back to fresh by a one-line edit.

#### 4.15.5 The AI-assist boundary (AT-4, optional and separable), ratified into rule text

AI MAY PROPOSE. ONLY A HUMAN MAY CONFIRM. ONLY CONFIRMED CLAIMS SHIP.

- The assisted flow: the assist reads a refused element's context (the same structural slice the fingerprint covers) and DRAFTS a proposed assertion into a draft state (`"origin": "ai-draft"`, a state intake REFUSES). A human reviews the draft in the `tibyan attest` dialogue or in the file, corrects or accepts it, and the act of confirmation rewrites the origin to `ai-drafted-human-confirmed` with the confirmer's name in `assertedBy`. Only then does intake accept the entry.
- Provenance distinguishes `human-authored` from `ai-drafted-human-confirmed`, permanently. Argued: the two claims have the same authority (a human owns both), but HOW the words were produced is provenance fact, recording it costs nothing, and hiding it would be the design's first dishonesty about itself. Every surface that shows provenance shows the origin token verbatim.
- What the assist may NEVER do, as rule text: never auto-confirm; never batch-confirm (confirmation is a per-entry human action; the record shape enforces it, one origin rewrite per entry, and the CLI offers no confirm-all); never draft for a category outside the ratified attestable set or a sub-phase not yet built; never let a draft reach ANY output (intake refuses `ai-draft` loudly, so a draft cannot reach the IR, a manifest, a document, or a survey count; drafts appear only in the worklist as proposals awaiting review).
- Separability: AT-1 through AT-3 stand entirely without AT-4. If AT-4 is never built, the design loses convenience and nothing else.


#### 4.15.5.1 AT-4 build record: the draft lifecycle is built, the assist itself is not (BUILT 2026-09-01, SPLIT recorded)

Section 4.15.5 ratifies a BOUNDARY, not an implementation. It says what the assist may never do and what must happen around a draft; it does not say where a drafting model lives, which model it is, or what determinism means for a call to one. That distinction decides this build, so it is stated before anything else.

WHAT IS BUILT: THE DRAFT LIFECYCLE, which is the whole human half of the section and every one of its safety properties.

- A DRAFT IS A THIRD THING, neither attested nor refused. An entry whose `origin` is the reserved token `ai-draft` is recorded as a `DraftProposal` and reported in its own queue, counted apart from `attested` and from `discovered`, and it produces nothing: no capability, no IR entry, no manifest entry, no OpenAPI operation. AT-1 enforced the same outcome by REFUSING the entry under `PROVENANCE`, because a draft had nowhere else to go. It now has somewhere to go, so the mechanism changed and the invariant did not. The separation is deliberate and it is not cosmetic: a proposal doing exactly what the flow asks of it is not a failure, and reporting it as one trains a team to ignore the single queue this sub-phase exists to keep honest.
- THE DRAFT'S WORDS ARE NOT PRINTED on any tool surface. The survey line names the target, the proposed name, and the category, and stops there. A drafted sentence rendered by the tool can be read, quoted, or pasted as though a person had claimed it, and no person has. Review happens in the file or in the confirm dialogue, where the reviewer knows they are looking at a proposal.
- CONFIRMATION IS A PER-ENTRY HUMAN ACT, enforced by construction rather than by a check. `tibyan attest <path> --confirm <target> --by <name>` rewrites exactly one entry's origin to `ai-drafted-human-confirmed` and records the confirmer in `assertedBy`. There is no `--confirm-all` and no multi-target form, in the CLI or in the provider API beneath it, because an option that confirmed a queue would BE the batch-confirm the rule text forbids. A team with ten drafts reviews ten drafts.
- THE CONFIRMER IS NAMED OR THERE IS NO CONFIRMATION. A blank `--by` refuses. The entire value of the rewritten origin is that it records who took the claim on, and an anonymous confirmation records nothing.
- CONFIRMING REWRITES ONLY PROVENANCE. The tool does not edit a claim's words, before or after confirmation. Confirming means a person read the proposal and takes it on as their own claim; a person who wants different words edits the file, which is the same reviewable record either way.
- CONFIRMING A NON-DRAFT REFUSES. Confirming a `human-authored` entry would relabel a person's own claim as AI-drafted, which is a lie in the one field whose entire job is to be true about how the words were produced.
- THE DRAFT TOKEN IS EXACT. Any other unrecognized origin remains a `PROVENANCE` refusal, so a typo is still reported as the defect it is rather than quietly parked in the review queue.

WHAT IS NOT BUILT: THE ASSIST, the thing that produces a draft. This build adds no model call, and the reason is that doing so is not a build decision:

- Tibyan's stack is fixed (Volume 16) and contains no model client. Adding one is a Volume 16 decision, not a phase decision.
- It would be the first network call in a tool whose entire contract is deterministic, offline source analysis, and CLAUDE.md rule 10 makes determinism a property of generation. Quarantining a non-deterministic drafting path from the deterministic generation path is a design, not a detail.
- Module placement is unresolved under rule 6 (one plugin per module): the assist is not a provider, not a backend, and not a runtime.
- Credentials, offline behaviour, cost, and model choice are all unanswered, and none of them is answered by section 4.15.5.

Naming that split honestly is the point of this record. The ratified separability clause already says AT-1 through AT-3 stand entirely without AT-4 and that its absence costs convenience and nothing else; what this build shows is that the same is true INSIDE AT-4. The lifecycle is the safety half and it is built; the drafting is the convenience half and it waits for its own ratified design, which must answer the four questions above before any code.

WHAT THIS MEANS FOR AN ADOPTER TODAY: any producer of drafts can be used, including a person with an editor or an external assistant, so long as what lands in `tibyan/attestations.json` carries `"origin": "ai-draft"`. The tool then queues it, refuses to ship it, and requires a named human to confirm it. The boundary holds regardless of who or what drafted, which is the property that matters and is the reason the lifecycle was worth building first.

MEASURED (2026-09-01): all three benchmarks BYTE-IDENTICAL across the serialized IR, the generated MCP output, the generated OpenAPI document, AND the survey text, verified by `diff -r` against a worktree at the pre-build commit. None carries a record file, so no draft queue can appear and the phase is provably additive.

#### 4.15.6 Name-and-signature honesty: how attested capabilities satisfy the E6b verdict

The E6b rejection says a description is not enough, so the attested distinction must live where the agent acts: the name and the schema.

- THE NAME carries the category. Every attested capability's name is `attested_<name>`: `attested_searchTenants`, `attested_getBuilding`. The prefix is applied by the tool, never typed by the human, and is RESERVED in both directions by core validation: no verified capability may carry a name beginning with `attested_`, and no attested capability may omit it. An agent reading a tool list sees the epistemic class of every tool before reading a single description; an orchestrator filtering tools can include or exclude attested tools by name alone. Argued against the alternative (a clean name plus metadata): that is exactly the disclaimer-beside-a-claim shape E6b rejected; the name IS the first claim, so the name carries the mark.
- THE SIGNATURE carries only covered names. Every input in an attested capability's schema is either structural (a path-variable id, a `paging`-marked parameter) or carries its human-attested meaning sentence as its per-input description in the manifest and the OpenAPI parameter. An input with neither cannot exist (intake condition 4), so the schema never contains an unclaimed name. This is the precise inversion of modeled-opaque: there, a copied name stood in the schema with its meaning unclaimed; here, a name enters the schema ONLY because a person claimed its meaning, and the claim travels with it.
- THE DESCRIPTION leads with the label. The synthesized description's FIRST sentence is the class: `Human-attested, not tool-verified.` followed by the result-semantic sentence verbatim, then `Asserted by <assertedBy> (<origin>), <date>.` The label is first because descriptions get truncated, and the one sentence that must survive truncation is the class.
- The OUTPUT stays honest: where the element tie is structural (T1/T2/view machinery) the output is the proven `ENTITY_REF`; where the shape is attested (category B) the output carries the attested subject entity WITH the attested marker, and the OpenAPI response schema stays a permissive object that declines to enumerate fields it does not know, exactly as Volume 6, section 4.1's partiality rule already renders a view.

#### 4.15.7 Wrongness and responsibility: the design's soul

A human attestation can be FALSE. The design does not pretend otherwise, and this section states the tool's exact position.

THE DIVISION OF RESPONSIBILITY, stated once and carried to SECURITY.md: the tool guarantees FAITHFUL CARRIAGE OF THE FILE, the asserter and the reviewing team own TRUTH AND INTEGRITY. Precisely, and bounded honestly (an earlier draft over-claimed that the tool guarantees the words were "typed or confirmed by the named person"; it cannot, because it cannot tell a legitimate authoring from a later hand-edit, and it does not authenticate humans): the tool guarantees that every attested claim it exposes is reproduced CHARACTER-EXACTLY FROM THE `tibyan/attestations.json` FILE, is attached to exactly the declaration the entry targets, is labeled human-attested on every surface with the entry's complete provenance, was structurally validated around its edges at intake, and is invalidated loudly the moment the fingerprinted code changes. The tool does NOT guarantee the claim is TRUE, and never implies it (that is what the label means). It does NOT guarantee the words were authored by the person `assertedBy` names rather than edited in later, and never implies it: the integrity of the file, that its entries say what their named asserters stand behind, is owned by the adopting team through the same mechanism that owns the integrity of the rest of their repository, code review and version control. This is why the record is a committed, reviewed file and not an ephemeral tool state: the team's PR process is the authentication the tool deliberately does not perform. The asserter (and the reviewing team) owns the truth of the sentence, exactly as they own the truth of their own API documentation, with two improvements: this documentation is pinned to a fingerprint and labeled with its epistemic class, so it cannot silently outlive the code it described nor be mistaken for a proof.

WHEN STRUCTURE CONTRADICTS AN ASSERTION, the conflict wins and both sides lose: the tool REFUSES the attestation loudly, at intake or at re-scan, and never silently prefers either side. An assertion that a mutating method is a read: refused (the mutation is structural fact). An assertion that an opaque wrapper read returns Building when the body's one repository access provably reads Tenant: refused (the tie is structural fact). An assertion whose target signature no longer matches the code: refused as stale. The tool never "corrects" an assertion to match structure (that would put words in the asserter's mouth) and never lets an assertion override structure (that would let a human unprove a proof). The refusal message names both sides of the contradiction and asks the human to resolve it in the file.

WHAT A FALSE-BUT-CONSISTENT ATTESTATION COSTS, stated so nobody discovers it in production: if the human asserts that `fullName` matches by prefix when it actually matches by containment, the tool cannot know, will carry the false sentence exactly, and an agent will act on it. The blast radius is bounded by the categories, and the bound is stated at its true strength, not overstated: in AT-1 and AT-2 every target passes the READ-SAFETY CHECK (section 4.15.2), so NO WRITE IS VISIBLE IN THE FINGERPRINTED SLICE and the binding is always structural (never a wrong write through the slice, never a wrong binding). The honest residue, carried openly: a write hidden BELOW the slice (in a hand-written repository or query-builder implementation the tool does not read, exactly the code the assertion covers) is not caught, so the guarantee is "no write in what the tool read," not "no write anywhere." That residue shrinks to nothing for a directly-typed read whose whole load is in the slice, and is largest for a category B local aggregate whose enrichment calls in-tree methods the check does read; it is precisely why the LOCAL-TIE PRECONDITION (category B) exists and why CATEGORY C (delegations, where the slice sees no persistence at all, so the residue is the whole call) is deferred with a MANDATORY read-only affirmation. The bound is real and it is the reason the taxonomy exists; it must never be blurred and never be stated as more than "read-safe as far as the tool read."

ADDITIONAL CONSUMER-FACING CAUTION, argued against the never-claim constraint. Two runtime controls, with two different defaults for two different stakes:

- `tibyan.attested.expose` (default TRUE) excludes ALL attested capabilities from registration wholesale, for a consumer who wants a verified-only surface. Default true, argued: committing the attestations file under review IS the adopter's deliberate opt-in; a second mandatory ceremony for the ordinary data-mislabeling stake would add friction without honesty.
- `tibyan.attested.exposeSecurityUnverifiable` (default FALSE), the exception the adversarial review forced. An attested read whose fronting controller declares a security contract surfaces `securityUnverifiable` by the section 4.14 composition (its filtered fronting GET is a non-strict anchor by construction, the recorded 4.14 interaction), and the execution adapter invokes the service bean DIRECTLY, bypassing that controller gate (Volume 7, gap 9's bypass fact). Registering such a read live by default would put a tool whose only declared gate is simultaneously unverifiable AND bypassed onto the surface without a second thought, which on pms is dozens of reads behind 119 method-level `@Secured` declarations. So these do NOT register by default: a `securityUnverifiable` attested capability is excluded unless this property is explicitly set true. This is a security-boundary stake, and unlike the ordinary attested exposure it defaults CLOSED. It is stated here rather than deferred to AT-3 because the exposure happens in AT-1 (the certification of those routes is what AT-3 defers, not the fact that AT-1 would otherwise ship them).

No per-call runtime warning is added: invocation honesty is not the issue (the binding is structural and the call is the same reflective invocation, argued in Volume 7), and a runtime nag would imply the tool doubts what its labels already state exactly. The two controls, not a nag, carry the caution.

What this section will never allow: the tool taking credit for an attested claim (it is never presented as verified); the tool taking blame silently for a false one (the provenance names the asserter on every surface); an attestation surviving the code it described (the fingerprint); or a contradiction resolved by preference (it is always a refusal).

#### 4.15.8 Representation: IR growth, argued against the Volume 4 bar

Decision: ONE first-class optional component, `Attestation`, on Capability, plus two core validation rules. NOT the Annotation hook, argued precisely because the hook was the right call four times before (view, enum, partial, paged): those are single facts a backend surfaces or ignores. An attestation is a structured, multi-field claim whose COMPLETENESS is itself an invariant (zero drift requires: category token from the closed set, non-empty asserter, origin token, date, fingerprint, per-parameter coverage). Flattening that into string-list annotations makes completeness unverifiable by `tibyan-core` without parsing string conventions, which is exactly where drift breeds. The growth bar (Volume 4, section 4) is met on all three tests, and the full argument lives in Volume 4, section 3.8: counted evidence (the fired trigger and the per-benchmark ceilings, ledgers sections 10, 23, 8), truthful naming (the component is named for exactly what it is and carries the epistemic class explicitly), and no coercion of anything that exists (verified capabilities are untouched; `Attestation` is absent on every one of them, and the serialized IR of an attestation-free model is byte-identical).

Core validation gains two rules (Volume 4, section 5, rules 8 and 9): an attested capability's `Attestation` must be complete (every required field, every input covered), and the `attested_` prefix is reserved in both directions. `tibyan-core` stays zero-dependency; the component is plain records.

The survey contract: ATTESTED is a new outcome class alongside discovered and refused, REPORTED SEPARATELY AND NEVER SUMMED: `discovered N (by-id A, list B), attested K, ...`, and the health metric evolves to `discovered rising, attested labeled, mis-bound 0, attestation-drift 0`. The verified count stays pure forever; a single number that mixed them would be the quiet version of rendering attested as verified.

The runtime: the loader tolerates the new manifest fields (the additive-field discipline, proven per phase since the view field); invocation is IDENTICAL for attested capabilities (the binding is a structurally resolved `<Bean>#<method>` like any other; what differs is what the tool CLAIMS about the call, not how it makes it); the one addition is the expose property above.

A recorded interaction with phase Controller-Authority (section 4.14), so the build is not surprised by it: the declared-authority composition applies to attested capabilities UNCHANGED, and its strict-anchor conditions were written for by-id and list-all shapes, so the fronting GET of a filtered read (which carries `@RequestParam` filters and, in the benchmarks, multi-statement bodies) is an associated but NON-STRICT route. By section 4.14's composition rule, an attested capability whose fronting route declares security will therefore surface `securityUnverifiable (conflicting-declarations)`, which is honest: the tool cannot certify a contract through a route whose parameter forwarding it has not proven. Extending strict-anchor certification to attested filtered routes (every attested parameter forwarded verbatim through a one-statement body) is a real, bounded future decision, named here and deferred to the AT-3 design session alongside the other security-adjacent choices; until then, attested capabilities carry their declared contracts as unverifiable markers, never as compiled authorities.

#### 4.15.9 What this phase will never do

- Never guess: every attested word was typed or confirmed by a named person; the tool authors nothing (the assist drafts, and drafts cannot ship).
- Never present attested as verified, on any surface, in any count, at any level, including the name.
- Never let an assertion fill a structural hole: bindings, parameters, reads, and mutation-freedom are proven or refused, never asserted (until AT-3 deliberately and separately ratifies the two boundary exceptions with their own guards).
- Never carry a stale attestation: the fingerprint gates emission in the same path that emits.
- Never edit the host system: the record lives in `tibyan/attestations.json`, never in the adopter's source.
- Never auto-confirm, batch-confirm, or ship a draft.
- Never sum attested into discovered, and never let the mis-bound-0 claim silently cover attested truth: mis-bound 0 covers bindings and carriage; attestation-drift 0 covers the new surface; the truth of an attested sentence is the asserter's, labeled.

#### 4.15.10 Pre-registered fixtures (ATT-1 to ATT-12; each sub-phase's build must include its subset before the code exists)

- ATT-1 (AT-1 positive, the pms shape): a filtered envelope read (`getAllByFilter` over a query builder) with a complete predicate attestation discovers `attested_searchTenants`: binding structural, every parameter carrying its sentence or `paging`, output element tied via T2, label and provenance on IR, manifest, OpenAPI, and survey.
- ATT-2 (AT-2 positive): an OPAQUE_WRAPPER read with a shape attestation emits with the attested subject entity and a permissive schema; an aggregate by-id read attests its enrichment and emits with the structurally proven load.
- ATT-3 (AT-1 positive, the foundation-gap shape): a soft-delete-guarded by-id read attested with its existence condition emits as an attested by-id read; the DG-4 refusal stands for the same code without the attestation.
- ATT-4 (character-exact carriage): every asserted string appears byte-identically on every surface; one altered character anywhere is a test failure. The core drift guard.
- ATT-5 (label separation): attested never renders as verified; `discovered` counts exclude attested everywhere (survey, report, totals); a verified capability never gains an `Attestation`; the manifest `attested` field never appears on a verified tool.
- ATT-6 (intake refusals): a nonexistent target; a parameter not on the method; an unmappable type; an uncovered domain parameter; a name collision; missing provenance; a category mismatch; a category whose sub-phase is unbuilt. Each refuses loudly with a located message and produces nothing.
- ATT-7 (structural contradiction): an assertion on a mutating body; a shape assertion contradicted by the body's repository tie; each refused, the message naming both sides.
- ATT-8 (staleness, the lifecycle guard): the fixture changes the fingerprinted method body after attestation; the next scan reports STALE_ATTESTATION, the capability returns to refused, generation omits it; re-attestation restores it. A stale entry carried is a build-stopping failure.
- ATT-9 (AT-4 boundary): an `ai-draft` entry never reaches any output; confirmation is per-entry; the confirmed entry carries `ai-drafted-human-confirmed` and the confirmer's name.
- ATT-10 (the drift mis-bind guard, this phase's mis-bound fixture): a mislabeled (attested rendered verified), misattached (fingerprint or target of a different method), or altered attestation is caught by core validation or the carriage tests; any escape is the phase's mis-bind and stops everything.
- ATT-11 (byte-identity): an app with no attestations file, and every existing benchmark IR, generates byte-identical output; the phase is provably additive.
- ATT-12 (prefix reservation): a verified capability named `attested_x` fails validation; an attested capability without the prefix fails validation.

#### 4.15.11 Forecast in the new vocabulary: the attestable ceiling per benchmark

Stated as "attestable up to N given a knowledgeable human," never as a promise: the ceiling counts refusals whose category is attestable; how many a real attester attests, declines, or discovers to be unattestable is exactly what cannot be forecast without a real human. Full rows: pms ledger section 10, erp section 23, the enterprise ledger section 8 (redacted).

- pms (the motivating case, post re-baseline: 89 DECLARED GETs, discovered 5). Numbers corrected 2026-07-21 by the Gate 1 traceability verification (ledger section 11), which found the per-category counts had been cited to an unrecorded census and were wrong; the corrected figures below are source-verified, and the ceiling is stated as an ESTIMATE rather than a computed count. AT-0 makes all 33 `*ResultSet` reads visible-but-refused-filtered (the re-based E6a forecast; zero remain parse-skipped). Attestable up to roughly 45 under AT-1 (the 43 filtered GETs, Tenant's existence-condition read, and `getPaymentsSummary`, which was previously miscategorised as an aggregate but performs no by-primary-key load) and up to 8 more under AT-2 (the aggregate by-id reads, source-enumerated: Building, Property, ChatRoom, Ticket, Group, Event, `getUserDetails`, and `findPropertyByBuildingId`). Excluded and staying refused: 11 context reads under the strict zero-argument reading (13 or 21 under wider readings that overlap the filtered count), 9 declared binary and stub endpoints of which 8 are live, and 2 controller-level coercion shapes, 1 of them by-id (a structural tolerance decision, not attestation). Ceiling ESTIMATE if everything attestable were attested: roughly 57 of 89 declared GETs, about 64 percent of the declared surface; the 89 includes roughly 7 mappings on generated interfaces that no class implements, so the live surface is smaller and the exact partition is what AT-0's worklist produces. The calibration on how much of that ceiling a truthful attester would actually take is an ESTIMATE from a 16-read judgment sample (2026-07-20), not a census: about 10 of 16 cleanly attestable and about 6 of 16 honestly declined (dual-identity columns, hidden inner-join filters); its basis is per-read tracing to the query builders and its limit is that it covers 16 of 43 reads and reflects one reviewer's judgment.
- erp (discovered 25): attestable up to roughly 30 filtered reads under AT-1 and roughly 14 projection and transformed reads under AT-2 (ledger section 15's census). The derived-query subset remains the one candidate for a future STRUCTURAL filter rule that would need no attestation; if that rule is ever built, those reads move from the attestable column to the verified column, the preferred direction.
- The private enterprise benchmark (counts only): attestable under AT-1 up to 8, the 6 soft-delete-guarded by-id reads (the solid floor: each load already structurally verified, only the guard's meaning attested) plus the 2 genuine user-facing filtered query reads the deferral census identified (of its 31 deferred reads only those 2 are real query capabilities; the other 29 are event-handler enums, notification inserts, and request-context accessors, structurally excluded or non-reads, ledger section 5). Under AT-2 up to 12, the aggregate-enrichment reads. The 15 by-id delegation reads (where read-only-ness itself must be attested, no local persistence in the slice) and the declared-security contracts wait for AT-3. The internal subtype rows, the stub, and the ambiguous pair stay refused. Ceiling: 4 verified plus up to 20 now (AT-1 8, AT-2 12) plus up to 15 behind AT-3.
- EVERY existing discovered row stays byte-identical on every benchmark: Attestation adds a separately counted category and must not move a single verified number; any movement is a regression finding that stops the build.

THE OPEN VERIFICATION, named: no real attesting human has run this flow, so the design's central quantity, the real attestation rate (attested versus declined versus refused-at-intake), is unmeasurable until first real use. The first-use measurement is designed now: the first adopter session records, per entry, time to attest, assist used or not, intake refusals hit, and declines with reasons; and the first month records staleness churn (invalidations per code change). Those numbers, not internal predictions, decide whether AT-2 through AT-4 proceed, exactly as benchmark evidence has decided every phase before.


### 4.16 Enum-typed capability INPUTS (the E3 reopening; phase Enum Inputs; designed 2026-08-04, RATIFIED; two build steps, EI-R BUILT 2026-08-05 in Volume 7 sections 9 and 9.1, EI-1 BUILT 2026-09-01)

#### 4.16.1 The trigger, recorded

E3 ratified enums as STRING plus a values annotation for entity fields and recorded one reopening
condition verbatim: a first-class ENUM primitive "stays a deferred growth candidate under Volume 4,
section 7, to be reopened if enums are ever needed as capability inputs (where a schema-level value
constraint would earn its cost)". That condition has FIRED, and it fired from a direction E3 could not see:
attested PREDICATE filters take parameters, and real filters take domain enums. The measurements are
recorded, not asserted:

- pms, first real measurement (docs/experiments/second-app-trial.md section 15): three of ten authored
  entries refused STRUCTURAL_FRAME on a domain enum parameter (`CostCategory`, `InvoiceStatus`,
  `TimeEntryStatus`), each otherwise well formed. That is 30 percent of an authored set.
- erp, the tie check (docs/experiments/third-app-trial.md section 26): erp's filtered reads tie cleanly and
  DO attest under AT-1 as built, with the enum-parameter subset named as the one blocked remainder.

THE OUTPUT DECISION IS NOT REOPENED. E3's STRING-plus-values-annotation stands for entity fields. This
section decides the INPUT side only, and it is designed to keep the two consistent rather than to split
them.

#### 4.16.2 The core question, argued before a representation is chosen

An agent supplying an enum value needs the CLOSED SET, and it needs it to be binding. A described-but-
unenforced set is the input-side analog of the MODELED-OPAQUE trap this project permanently rejected: the
tool would render a constraint into a schema, the agent would reasonably treat it as the contract, and
nothing would hold the agent or the application to it. So the decision is not "how do we describe the
values" but "what is the smallest representation that makes the closed set ENFORCEABLE".

OPTION A, STRING plus a first-class enumerated-values constraint that the backends render as a schema-level
closed set and the runtime validates at invocation.

OPTION B, a first-class ENUM member of the closed type set, the value set carried in the type.

THE DECISION IS OPTION A, in a specific form argued in 4.16.3: a first-class, per-input, optional
constraint component on Capability, NOT an Annotation-hook entry and NOT a new Type.

Is "STRING with an enforced values constraint" itself a small dishonesty? No, and the reason is the same
reason E3 gave for outputs. The wire form of an enum-valued input genuinely IS a string: JSON has no enum
type, and JSON Schema, OpenAPI, and the MCP tool schema all express a closed set as
`{"type": "string", "enum": [...]}`. STRING names the wire truth and the constraint REFINES it. Calling it
STRING would only be a dishonesty if the constraint were decorative, which is exactly what 4.16.6 forbids.

Three further arguments decide it, in order of weight.

1. POSITIONAL CONSISTENCY. The same Java enum appears as an entity field (an output) and as a filter
   parameter (an input). Under Option B, `InvoiceStatus` would be STRING-plus-annotation in one position and
   ENUM in the other, an asymmetry with no truth behind it: it is one type with one wire form. Option B
   therefore forces a choice between an incoherent model and reopening the output decision, and the trigger
   explicitly does not reopen outputs.
2. THE GROWTH BAR (Volume 4, section 4). Option B fails test 2. A new primitive must "tell the truth about
   the source, naming what the source actually declares rather than papering over it with a near-neighbor".
   The source declares a Java enum whose wire form is a string; ENUM would name the Java construct, not the
   wire. Option B also crosses the line the E4 collection-output note drew: `Type` is deliberately flat and
   closed, and an `ENUM(values...)` member would be the FIRST type carrying a payload, which is precisely
   the wedge that opens type algebra. The note's own words apply unchanged: a payload-carrying `Type` would
   open all of that the moment it existed.
3. WHAT B BUYS THAT A DOES NOT: nothing that survives inspection. Every consumer of the constraint (the MCP
   schema, the OpenAPI parameter, the runtime validator) needs the value LIST and the fact that the wire
   form is a string. A carries both. B carries the same two facts inside a heavier vehicle and forces every
   validator, every backend's type rendering, the JSON round-trip, and every future provider to learn a new
   type shape.

Why NOT the Annotation hook, which is where E3 put the output values. Volume 4, section 3.8's own argument
against flattening `Attestation` into annotations applies here verbatim and decisively: a constraint keyed
by a mangled string (`enumValues:status`) is not checkable by `tibyan-core`, because the validator cannot
confirm that the key names a real input, that the input is STRING-typed, or that the value list is
non-empty and distinct, "which is exactly where drift breeds". This phase's invariant (a rendered set is an
enforced set) is a completeness invariant, and a validator that cannot see the structure cannot enforce it.
So the carrier is first-class, but it is a COMPONENT, not a Type. The output side keeps its annotation
because its invariant is weaker: an output annotation is a hint a backend may ignore, while an input
constraint is a contract a backend renders and a runtime enforces.

#### 4.16.3 The IR shape (the full argument is Volume 4, section 3.9)

One optional component on Capability: a map from input name to a value set, absent on every capability that
has none, so an enum-free model serializes byte-identically. Core gains validation rule 10: every key names
an existing input, every keyed input is STRING-typed, and every value set is non-empty with distinct values.
No primitive is added, `Type` is untouched, and `inputs` keeps its `Map<String, Type>` shape.

#### 4.16.4 STRUCTURAL PROVABILITY: when the closed set may be carried

The never-guess bar applied to a value set. The tool carries a set it READ from a resolved declaration, or
it carries none. It never infers values from a parameter's name, from usage, from call sites, or from a
naming pattern.

A parameter P of a capability carries a value set when ALL of these hold.

- EI-1, IN-TREE RESOLUTION. P's declared type resolves, by the section 4.12.4 key resolver, to exactly one
  in-tree declaration, and that declaration is a Java `enum`. This is the same in-tree resolution E3 already
  performs for enum FIELDS; nothing new is invented.
- EI-2, READABLE CONSTANTS. The enum's constants are read directly from that declaration, in declaration
  order. A constant with a body or constructor arguments contributes its name; the set is deterministic.
- EI-3, THE WIRE-VALUE RULE, and it is the one this design would be wrong without. The set carried is the
  set of values the APPLICATION ACCEPTS ON THE WIRE, which is not always the constant names.
  - Shape (a), BARE ENUM: the declaration carries no `@JsonValue` anywhere. The wire form is the constant
    name (Jackson's default), and the carried set is the constant names.
  - Shape (b), VALUE-MAPPED ENUM: exactly one member is annotated `@JsonValue`, it is a no-argument accessor
    whose body is a single `return <field>;`, that field is assigned in the enum's single constructor from
    exactly one parameter, and EVERY constant passes exactly one string literal in that position. The
    carried set is those literals, in declaration order.
  - Shape (c), ANYTHING ELSE: REFUSE to constrain. A `@JsonValue` on a method with logic, a `@JsonValue` on
    a non-String member, more than one `@JsonValue`, a constant that passes a non-literal or the wrong
    arity, or a value the tool cannot read as a single literal. The tool does not interpret; it reads or it
    declines.
- EI-4, THE SUBSET INVARIANT. The carried set must be a SUBSET of what the application accepts. This is what
  makes shape (b) safe in the presence of a `@JsonCreator`: a creator may accept MORE than the tool
  advertises (aliases, case-insensitivity), and advertising a subset is harmless because an agent
  constrained to the advertised set only ever sends accepted values. The tool therefore never interprets a
  `@JsonCreator`; it relies on `@JsonValue` for what it advertises. Advertising a SUPERSET is the failure
  this design forbids absolutely (section 4.16.7).

THE CONSTRUCTOR-LITERAL TRAP, named because the benchmarks contain it and a naive rule walks straight into
it. pms's hand-written `LeaseStatus` declares `DRAFT("Draft"), ACTIVE("Active"), ...` with a Lombok
`@Getter` and NO `@JsonValue`. Those literals are decorative labels; Jackson serializes the constant name
`DRAFT`. A rule that read constructor literals whenever it saw them would carry `["Draft", "Active", ...]`,
every value of which the application would reject. Shape (b) is therefore conditioned on `@JsonValue`
proving that the field IS the wire form, never on the mere presence of a constructor literal.

WHAT REFUSAL MEANS HERE, decided rather than left open. When EI-1 to EI-3 do not hold, the parameter type
does not map at all and the CAPABILITY IS REFUSED, exactly as today, with the existing STRUCTURAL_FRAME
cause and an enriched message naming which condition failed. It does NOT fall back to an unconstrained
STRING input. The reasoning is the never-claim constraint read in the input direction: an unconstrained
STRING input on a parameter the application will only accept a closed set for is a schema that invites an
agent to send a value the call will reject, which is a worse outcome than a named refusal. This is the same
choice E3 made for fields (an unresolvable enum field refuses rather than degrading to a bare STRING) and
it keeps the two sides consistent.

Two cases refuse for reasons worth naming separately because they look like successes.

- THE STRING-PARSED-INTERNALLY CASE. A parameter declared `String` that the body parses into an enum
  (`CostCategory.fromValue(raw)`, `Status.valueOf(raw)`). The closed set is NOT in the signature, and the
  tool would have to read the body to find it. It carries NO constraint: the parameter stays an
  unconstrained STRING and the capability is not refused on this account, because the declared contract
  genuinely is a string. The refusal ledger records it as a named deferral, `enum-parsed-in-body`, so the
  case is visible rather than silently unconstrained. This is the one place where an unconstrained STRING is
  correct, because the signature is telling the truth.
- THE NOT-IN-TREE CASE. A parameter typed by an enum from a binary dependency. EI-1 fails, the tool cannot
  read the constants, and the capability REFUSES.

#### 4.16.5 The attestation split: the human attests MEANING, the tool proves VALUES

This is the elegant case of the whole Attestation phase, and it is worth stating as a principle rather than
a mechanism. For an attested PREDICATE filter whose parameter is a provable enum, the two sources of truth
divide cleanly along their competence:

- THE TOOL proves the closed VALUE SET, structurally, from the enum declaration. It never asserts what the
  values mean.
- THE HUMAN attests the parameter's MEANING, the sentence the coverage rule already requires. They never
  supply the value set, and an attestation cannot widen, narrow, reorder, or override it.

Neither can substitute for the other, which is why the split is the right shape: a value set is exactly the
kind of fact structure can prove and a human should not be trusted to retype, and a filter's meaning is
exactly the kind of fact structure cannot reach. It is the boundary of section 4.15.2 (an assertion fills
ONLY a semantic hole) applied to a parameter instead of a read.

The two mixed cases are decided explicitly.

- ENUM PROVABLE, SEMANTICS NOT ATTESTED: the read STAYS REFUSED. Making an enum input representable removes
  a type-mapping refusal; it grants no capability and bypasses nothing. A filtered read with no attestation
  is refused by the structural rules exactly as before, and the value set is simply never reached.
- SEMANTICS ATTESTED, ENUM NOT IN-TREE-PROVABLE: the capability REFUSES per 4.16.4, and the intake refusal
  names the enum condition that failed. An attestation cannot supply the value set, because the value set is
  a structural fact and 4.15.2's boundary forbids an assertion from filling a structural hole. A human who
  wants that read must make the enum resolvable, not assert its values.

Coverage is unaffected: an enum parameter is a DOMAIN parameter, so it still requires a meaning sentence.
It may never carry the `paging` mark and never carries the `id` mark.

#### 4.16.6 ENFORCEMENT, and the honest split of what this design delivers

THE HONESTY INVARIANT: ANY SURFACE THAT SHOWS THE CLOSED SET MUST MEAN IT IS ENFORCED, NEVER MERELY
SUGGESTED. A schema `enum` is a DECLARATION, not enforcement: a well-behaved MCP client will respect it, and
a buggy or hostile one will send anything. Enforcement happens where the call is made, in the runtime.

A LATENT RUNTIME DEFECT FOUND WHILE DESIGNING THIS, recorded because it is a precondition and not a detail.
`SpringExecutionAdapter` resolves the target method with
`bean.getClass().getMethod(methodName, parameterTypes)` where each entry of `parameterTypes` comes from
`IrTypes.toJavaClass(input.type())`. For a STRING input that yields `String.class`. The real signature takes
`CostCategory`. The lookup throws `NoSuchMethodException` and the capability is UNINVOKABLE. The same is
true of TIMESTAMP, which yields `Instant.class` against a real `LocalDate` parameter. This has never been
observed because every capability shipped to date has exactly one id-typed input (verified: all discovered
pms inputs are LONG, all discovered erp inputs are UUID), so the defect is latent and is not enum-specific.

It is nonetheless BLOCKING for this phase: shipping a schema that advertises a closed set for a tool that
throws on first invocation would be a larger dishonesty than the one this phase set out to fix.

THE PHASE IS THEREFORE SPLIT BY NAME INTO TWO BUILD STEPS, in this order, one per session per the standing
rule.

- EI-R, RUNTIME ARGUMENT BINDING (built FIRST). The adapter resolves the target method by name and ARITY,
  refusing loudly when more than one same-arity overload matches (never guessing which), then converts each
  incoming argument to that method's DECLARED parameter type by reflection, including enums (matched by the
  same rule the provider used: constant name for shape (a), `@JsonValue` literal for shape (b)) and the
  `java.time` types. It also validates an incoming value against the carried set BEFORE invoking, refusing
  with a located error naming the legal values. Requires no IR growth. Its own invariant: every existing
  capability keeps invoking identically, proven by the unchanged end-to-end test.
- EI-1, THE ENUM INPUT CONSTRAINT (built SECOND). Core component and rule 10, the provider rule of 4.16.4,
  and the backend surfaces of Volume 6.

RATIFIED SEQUENCING RULE: the backends do not render a closed set until EI-R is green. Until then the
constraint may exist in the IR but no surface may show it, because a shown set that is not enforced is
precisely the input-side modeled-opaque claim. If EI-R proves larger than one session, the honest outcome is
that enum inputs do not ship, not that they ship unenforced.

#### 4.16.7 The mis-analog: MIS-CONSTRAINED

This phase's version of mis-bound. A capability is MIS-CONSTRAINED when it advertises a value the
application would reject, or advertises a set the tool did not read from a resolved enum declaration, or
shows a closed set on a surface that does not enforce it. `mis-constrained 0` is absolute and is reported
alongside `mis-bound 0`, `attestation-drift 0`, and `mis-safe 0`.

The bias, stated as the other phases state theirs: advertising a SUBSET of the accepted values is safe and
acceptable, advertising a SUPERSET is a breach. Every condition of 4.16.4 is written so that an unreadable
or ambiguous declaration produces no set at all rather than a guessed one, and so a false refusal is a
coverage miss while a false constraint is a breach. The design prefers the miss.

What this rule will never do: infer a value set from a parameter name, a naming pattern, or call-site
literals; read a value set from a `@JsonCreator`'s body; carry constructor literals as wire values without a
`@JsonValue` proving they are the wire form; widen or reorder a set; let an attestation supply, extend, or
override a set; or render a set on any surface that does not enforce it.

#### 4.16.8 Pre-registered fixtures (EI-1 to EI-9)

- EI-1, the provable bare enum: an in-tree `enum` with no `@JsonValue` used as a filter parameter carries
  its CONSTANT NAMES, in declaration order, and renders as an enforced closed set on the MCP schema and the
  OpenAPI parameter.
- EI-2, the value-mapped enum: a generated-style enum with `@JsonValue` on a single-return accessor carries
  the LITERALS (`maintenance`, not `MAINTENANCE`), proving the wire-value rule.
- EI-3, THE CONSTRUCTOR-LITERAL TRAP, the most important negative: an enum with constructor literals and NO
  `@JsonValue` (the pms `LeaseStatus` shape) carries the CONSTANT NAMES, never the labels.
- EI-4, the attestation split: an attested PREDICATE filter whose enum parameter carries the tool-proven set
  and the human-attested meaning sentence, both present, on every surface, with the attestation unable to
  alter the set.
- EI-5, the not-in-tree enum: a parameter typed by an unresolvable enum REFUSES with STRUCTURAL_FRAME naming
  condition EI-1, and no set is carried.
- EI-6, the string-parsed-internally case: a `String` parameter the body parses into an enum carries NO
  constraint, is NOT refused on that account, and appears in the ledger as `enum-parsed-in-body`.
- EI-7, shape (c) refusals: `@JsonValue` on a method with logic, two `@JsonValue` members, and a constant
  passing a non-literal each refuse to constrain and therefore refuse the capability.
- EI-8, semantics-not-attested: a filtered read with a provable enum parameter and no attestation stays
  REFUSED; the enum rule bypasses nothing.
- EI-9, THE MIS-CONSTRAINED GUARD: a body of cases proving the tool never advertises a value it did not
  read (name-similar enums, a same-named enum in another bounded context resolved by key, an enum whose
  constants are added in a different file) and never renders a set on an unenforced surface.

#### 4.16.9 Forecast, all three benchmarks and the standing worklists

DISCOVERED IS BYTE-IDENTICAL BY CONSTRUCTION, not merely by measurement, and the reason is checkable: a
discovered by-id read takes exactly one identifier parameter and a discovered list-all read takes none, so
no discovered capability can have an enum input. Verified against the current IR of both benchmarks: every
discovered pms input is LONG and every discovered erp input is UUID.

- pms-backend. THREE authored entries unblock: `CostServiceImpl#getCostsByBuilding` (`CostCategory`, 8
  constants, shape (b), literals `maintenance` to `other`), `InvoiceServiceImpl#getBuildingInvoices`
  (`InvoiceStatus`, 3 constants, shape (b)), `TimeEntryServiceImpl#getBuildingTimeEntries`
  (`TimeEntryStatus`, 3 constants, shape (b)). ALL THREE THEN REFUSE AT THEIR NEXT GATE, the output entity
  tie, because pms ties through hand-written one-hop repositories, so each moves STRUCTURAL_FRAME to
  CATEGORY_MISMATCH (AT-2). Intake outcome: refused 10 unchanged, redistributing to shape hole 10, enum
  input 0, read safety 0; ATTESTED 0 UNCHANGED. Discovered 5, byte-identical. The true near-term coverage
  gain on pms is ZERO, exactly as with the read-safety phase, and for the same structural reason: pms is
  blocked on AT-2, not on this.
- erp-backend. THE REAL PAYOFF, and it is still not measurable this phase. erp's enums are BARE (no
  `@JsonValue` anywhere in the sampled declarations), so shape (a) applies and the carried set is the
  constant names. A census of erp service methods with an in-tree-resolvable enum parameter found 14, of
  which 10 are READS (`AccountServiceImpl#searchByFields`, `ContactServiceImpl#findByAccountType`,
  `DeliveryServiceImpl#searchByFields`, `EmailTemplateServiceImpl#findActiveByType`,
  `EmailTemplateServiceImpl#findByType`, `PaymentServiceImpl#findAll`, `QuoteServiceImpl#searchByFields`,
  `TicketServiceImpl#findAll`, `TransactionServiceImpl#findCompletedByType`,
  `TruckServiceImpl#searchByFields`) and 4 are writes, correctly excluded. Because erp ties cleanly through
  direct Spring Data repositories, those 10 become ATTESTABLE once a human authors entries for them: this is
  the first phase whose payoff is gated on an attester rather than on a further build. MEASURED this phase:
  erp has no record file, so attested stays 0 and discovered stays 25, byte-identical.
- The private enterprise benchmark. Untouched, and this row is an ARGUMENT rather than a measurement because
  that source is not present in the working tree. It has no record file, and its discovered capabilities are
  by-id reads with identifier inputs, so no constraint can attach. Discovered stays 4. Counts and shapes
  only; redaction absolute.

Across all three: discovered byte-identical, generated output byte-identical, `mis-bound 0` and
`mis-constrained 0` absolute. Any movement in a discovered number is a REGRESSION FINDING.

A FINDING RECORDED, NOT ACTED ON, because the trigger explicitly does not reopen outputs. The wire-value
rule of EI-3 implies that E3's OUTPUT annotation may be inaccurate on the value-mapped idiom: an entity
field typed by a `@JsonValue` enum currently carries the CONSTANT NAMES in its `enum` annotation, while the
wire carries the literals. On pms's generated enums those differ (`MAINTENANCE` versus `maintenance`). This
is recorded here as evidence for a future decision and is deliberately NOT changed by this phase; the output
annotation is a hint a backend may ignore, not a contract a runtime enforces, so the stake is lower, but the
inconsistency is real and should be resolved when outputs are next opened.


#### 4.16.10 EI-1 build record: the ratification reconciliation and the four build decisions (BUILT 2026-09-01)

THE RATIFICATION RECONCILIATION, done first because rule 12 forbids building an unratified design. This section's heading has read RATIFIED since 2026-08-04, Volume 13's enum entry reads RATIFIED, CLAUDE.md records the constraint as "designed and pending its BUILD", and validation rule 10 was already written into Volume 4, section 5. But the headings of Volume 4, section 3.9 and Volume 6, sections 3.7 and 4.5 still read "pending ratification": they were written in the same session as this section and never updated when the phase was ratified. The weight of evidence is that the phase IS ratified and three headings went stale, so the build proceeded and corrected them rather than treating one stale heading as a veto over four concurring records. Recorded here so the reconciliation is visible rather than assumed.

DECISION 1: THE CONSTRAINT IS CARRIED AT INTAKE, WHICH IS THE ONLY PLACE IT CAN APPLY. Section 4.16.9 already establishes that no DISCOVERED capability can have an enum input, because a by-id read takes one identifier and a list-all read takes none. So the provider rule of section 4.16.4 has exactly one call site: the attestation intake's parameter mapping (section 4.15.4, condition 3). An in-tree provable enum parameter now maps to STRING and carries its value set; a shape (c) enum refuses the entry with its located reason; a not-in-tree enum refuses with the existing unmapped-type message, enriched to name condition EI-1. This is not a narrowing of the ratified rule, it is the ratified rule meeting the only inputs that exist.

DECISION 2: THE `enum-parsed-in-body` LEDGER IS A NEW SURVEY CHANNEL. Section 4.16.4 requires that the STRING-PARSED-INTERNALLY case be recorded as a named deferral so it is "visible rather than silently unconstrained", and no existing channel carried it: it is not a refusal, not a deferred list read, and not an entity outcome. The build adds `ScanReport.enumParsedInBody`, one entry per read whose String parameter is fed to an in-tree enum inside the body, detected structurally (a call whose receiver is a bare name resolving to an in-tree ENUM, taking that parameter verbatim). It is a diagnostic: those reads carry no constraint and are NOT refused on that account, exactly as ratified.

DECISION 3: BYTE-IDENTITY IS THE SERIALIZER'S JOB, NOT THE RECORD'S. `inputValues` is never null on a Capability, so an unconstrained capability holds an empty map; the empty map is omitted from the serialized IR by a Jackson mixin scoped to that ONE property. A type-wide NON_EMPTY was rejected because it would also drop the empty `annotations` list every unannotated capability currently serializes, which would change existing output. And the annotation lives in `tibyan-pipeline`, never on the record: `tibyan-core` carries no Jackson annotations because it carries no dependencies (CLAUDE.md rule 1). Both `Capability` and `ScanReport` keep their previous constructor signatures as compatibility overloads, so adding a constraint is deliberate rather than accidental.

DECISION 4: `mis-constrained 0` IS REPORTED ONLY WHERE A CONSTRAINT EXISTS. The invariant is absolute (section 4.16.7) but printing it on every scan of every constraint-free app would change the totals line of every existing benchmark for no information. It is emitted when at least one capability carries a value set, exactly as `attestation-drift 0` is emitted only when an attestation exists.

MEASURED (2026-09-01), and the forecast of section 4.16.9 held: pms 5 discovered, erp 35, as-backend 5, with the serialized IR, the generated MCP output and the generated OpenAPI document all BYTE-IDENTICAL to a worktree built at the pre-build commit. DISCOVERED IS BYTE-IDENTICAL BY CONSTRUCTION, not merely by measurement, for the reason section 4.16.9 gives. The one measured change on any benchmark is pms's survey gaining two `enum-parsed-in-body` lines, both of them WRITES (`LeaseServiceImpl#updateStatus`, `LeaseSigningServiceImpl#applyWebhookUpdate`), correctly named as visible-and-unconstrained rather than refused. erp and as-backend produce no such lines.

WHAT IS STILL GATED ON AN ATTESTER, restated so the payoff is not misread as delivered. erp's 10 enum-parameter reads (section 4.16.9) become ATTESTABLE by this build, not attested: erp has no record file, so attested stays 0. This is the first phase whose payoff is gated on a human authoring entries rather than on a further build, and the tool does not and will not author them.
### 4.17 DL-1: reviewed drafts, the third tier and the write gate (v2 direction; designed and RATIFIED 2026-09-23 as written; BUILT 2026-09-23, build record at 4.17.13)

STATUS: RATIFIED 2026-09-23 AS WRITTEN AND BUILT THE SAME DAY (build record, decisions and measurements at section
4.17.13). The maintainer ratified this section without amendment, so the six
decisions of section 4.17.11 stand at their recommendations (recorded one by one in section 4.17.12) and the six
constitution changes of section 4.17.9 are ratified with it. Under rule 12 this frozen text is committed before any
code; the build is its own later session. This is the phase the v2 direction (Volume 13 section 3) names as the one
after spike DL-0.

#### 4.17.1 The evidence

Spike DL-0 (docs/experiments/as-backend-trial.md section 10, commit fdd5ec3). On as-backend the draft-first
surface is 990 candidates from 272 concrete service classes against the 20 the refusal-first tool serves: VERIFIED
5, ATTESTED 15, READ 401 (263 with every input mapped), WRITE 204 (58 with every input mapped), UNKNOWN 365 (362
void). The labels held on every proven target in both directions. Two facts from the census shape this design:
138 READ and 146 WRITE drafts carry an input the mapper cannot map (List<Long> 40, an in-tree enum 33,
List<Integer> 26, a DTO 20), so input binding is on the critical path; and the maintainer's reading sample of the
READ labels is still owed, so this design must not let a wrong READ label ship silently, which is what the
per-entry human approval and the name prefix are for.

#### 4.17.2 The record: tibyan/drafts.json is the file, and the review is an edit

The drafts file DL-0 writes becomes the record, exactly as tibyan/attestations.json is the record for attestations
(section 4.15.3): committed beside the source, reviewed through the team's own version control, never edited by the
tool except where stated. Each entry keeps DL-0's tool-written fields (name, target, binding, label, reason,
capability, returnType, inputs) and gains:

- `fingerprint`, TOOL-STAMPED at draft time over the same structural slice section 4.15.4 fingerprints, so an
  approval is pinned to the code it approved.
- `description`, REVIEWER-WRITTEN, the sentence the tool never authors. Empty means not approvable.
- `descriptionOrigin`, `human` (default) or `ai-draft`: who produced the words, kept permanently, shown on every
  surface exactly as AT-4's origin token is.
- `review`: `pending` (the tool's value), `approved` or `rejected` (a reviewer's values).
- `reviewedBy` and `reviewedOn`: the named human and the date, both required for `approved`.
- `name`: the reviewer MAY rename the minted default; the tool applies the tier prefix (4.17.4) itself.

RE-RUNNING `tibyan draft` MERGES, NEVER OVERWRITES: an existing entry keeps every reviewer field; a new method
appends a pending entry; a target that no longer resolves is marked `stale` in place with its old fingerprint kept;
a target whose recomputed label differs from the recorded one is marked with both labels and its review is RESET
to `pending`, because a person approved a READ and the code is now a WRITE. Nothing is deleted by the tool.

REVIEW IS A BATCH ACT BY DESIGN: a reviewer approves any number of entries in one edit and one commit. There is no
per-entry command and no `--confirm` for drafts. The named human, the date and the fingerprint on each entry are
what make a batch honest; AT-4's per-entry rule stays in force for ATTESTATIONS, whose sentences carry safety claims
(predicate, shape, boundary) that a draft never does.

#### 4.17.3 Intake: what the provider proves about an approved entry before it enters the IR

Intake runs at scan, for every entry with `review: approved`, and REFUSES with a located cause or admits; a
`pending` entry is counted in its own queue and produces nothing; a `rejected` entry produces nothing and is not
counted as a refusal. The conditions, all of which must hold:

- RV-A, RESOLVE. The target resolves to exactly one public instance method of one concrete `@Service` class
  (section 4.15.3 condition 1, the same resolver). Else `TARGET_UNRESOLVED`.
- RV-B, NOT STALE. The recomputed fingerprint equals the stamped one. Else `STALE_DRAFT`, and the survey names the
  entry; the same loud invalidation attestations have.
- RV-C, NO LABEL DRIFT. The recomputed label equals the recorded label. Else `LABEL_DRIFT` naming both. This is the
  guard that keeps a reviewer's approval attached to what they read.
- RV-D, NOT ALREADY PROVEN. The target is neither a discovered binding nor an attested target. Else `SUPERSEDED`:
  the proven tier wins, and the entry is reported so the reviewer can delete it. A reviewed capability never
  shadows a verified or attested one.
- RV-E, EVERY INPUT BINDS. Each parameter maps to a primitive through the type mapper, or resolves to an in-tree
  enum whose closed value set section 4.16 reads (the EI-1 path, so the runtime's EI-R enforcement applies
  unchanged), or is a paging parameter (`Pageable`, `Sort`) the runtime already binds. A collection, a DTO, or an
  unresolvable type refuses with `INPUT_UNMAPPED` naming the parameter and its type. Collection inputs are DEFERRED
  TO DL-2 BY NAME (decision D6), with the as-backend count as the trigger evidence.
- RV-F, THE OUTPUT IS DESCRIBABLE. `void` yields no output; a primitive maps; an in-tree entity yields an
  `ENTITY_REF`; anything else yields the permissive object of Volume 6 section 4.1 (the partiality rule), exactly
  as an attested SHAPE output does. Never a refusal: a reviewer who approved a DTO-returning read accepted that
  shape.
- RV-G, THE HUMAN HALF IS PRESENT. `description`, `reviewedBy` and `reviewedOn` are non-empty and `review` is
  `approved`. Else `REVIEW_INCOMPLETE`.
- RV-H, LABEL TO KIND. READ becomes kind READ with `readOnly` true; WRITE becomes kind WRITE with `readOnly`
  false; UNKNOWN becomes kind ACTION with `readOnly` false (decision D4). VERIFIED and ATTESTED never reach here
  (RV-D).

Read-safety is NOT re-run as a gate for WRITE and ACTION: the label already says a mutation is visible or unknown,
and the reviewer approved a write as a write. It IS re-run for READ through RV-C, because a READ label is a
read-safety verdict and drift in it is drift in the safety claim.

#### 4.17.4 IR growth: the Review component, two kinds that now exist, and two core rules (Volume 4 section 3.10)

One optional component on Capability, `Review(label, reviewedBy, reviewedOn, descriptionOrigin, fingerprint)`,
plain records, `tibyan-core` stays zero-dependency. `CapabilityKind.WRITE` and `ACTION`, declared since the MVP and
never produced, are produced from this phase on, only by this intake, only with a `Review` attached.

Core validation gains two rules. RULE 12, REVIEW COMPLETENESS AND NECESSITY: a capability carrying a `Review` must
carry every field non-empty with `label` from the closed set; and a capability of kind WRITE or ACTION MUST carry a
`Review`, because no discovery rule proves a write and none ever will under this design, so a write without a named
reviewer is a write nobody signed for. RULE 13, THE REVIEWED PREFIX, reserved in both directions exactly as rule 9
reserves `attested_`: a capability carrying a `Review` is named `reviewed_<name>`, and no other capability is. The
E6b verdict stands: the epistemic class lives in the name, not only in a description that gets truncated.

Argued against the Volume 4 section 4 bar. Counted evidence: 990 candidates on one app, of which the two proven
tiers cover 20. Truthful naming: the component is named for what it is, a human review, and carries the tool's
own label beside the human's name so neither is mistaken for the other. No coercion: verified and attested
capabilities are untouched; an IR without approved drafts serializes byte-identically to today's. Rule 5's
"read-only capabilities" clause is rewritten by this phase, section 4.17.9.

#### 4.17.5 Backends (Volume 6, sections 3.9 and 4.6)

MCP manifest, per tool, additive fields the runtime already tolerates: `kind` (READ, WRITE, ACTION), `tier`
(`verified`, `attested`, `reviewed`), and for a reviewed tool `review{label, reviewedBy, reviewedOn,
descriptionOrigin}`. The description's FIRST sentence is the class, before the reviewer's words: `Reviewed by
<reviewedBy> (<descriptionOrigin>), not tool-verified; the tool labeled it <label>.` followed by the reviewer's
sentence verbatim. OpenAPI: a reviewed READ renders as today's GET; a WRITE or ACTION renders as a POST operation
whose request body carries the inputs, tagged `reviewed`, with the same first sentence. Both golden files change
deliberately and are re-baselined in the build.

#### 4.17.6 Runtime (Volume 7 section 11): the write gate, the hints, and what is deliberately not built

- REGISTRATION. A reviewed READ registers when `tibyan.reviewed.expose` is true (default TRUE, decision D2: the
  committed approvals are the opt-in, as for attested). A WRITE or ACTION registers ONLY when its exact name is in
  `tibyan.writes.allow`, a list with Volume 7 section 10's five conditions applied verbatim (exact names, every
  entry resolves, every entry is needed, the list widens nothing else, no blanks or duplicates), default EMPTY.
  This is the write gate the direction names, and the section 10 list is its first member in shape.
- SECURITY COMPOSITION. Section 4.14's declared-authority composition runs for reviewed capabilities exactly as for
  attested ones, so a reviewed capability fronted by a secured controller carries `securityUnverifiable` and does
  NOT register by default, because the adapter bypasses that controller (Volume 7 gap 9), and for a WRITE that is
  the first-order risk. Which list opens it is decision D1.
- HINTS. The tool definition carries `readOnlyHint` true for verified, attested and reviewed READ tools, and
  `readOnlyHint` false with `destructiveHint` true for WRITE and ACTION, so a client that gates on hints (the
  directory policy and the coding agents do) asks before a write. Carriage depends on the pinned stack exposing tool
  annotations through the Spring AI callback path. VERIFIED AT BUILD (2026-09-23): the pinned MCP SDK 0.18.3 DOES
  carry them (`McpSchema.ToolAnnotations` with readOnlyHint and destructiveHint exists in mcp-core), but the Spring
  AI 1.0.9 bridge does not: `ToolDefinition` has name, description and inputSchema only, and
  `McpToolUtils.toSyncToolSpecification` never sets annotations. So the hints are NOT carried in DL-1; the name
  prefix and the first sentence remain the channel (they are already the E6b channel), a tripwire test fails the
  day a stack bump adds an annotations surface, and the alternative path the pinned stack does offer (registering
  `McpServerFeatures.SyncToolSpecification` beans directly) is recorded for the Volume 16 session.
- STARTUP HONESTY. The startup log names every WRITE and ACTION it did NOT register with the reason
  `write-not-allowed`, beside section 10's exclusion reasons, and the registered-names defect of section 10.6 is
  fixed in the same place.
- INVOCATION is the same reflective invoke; enum inputs bind through EI-R unchanged. NOT BUILT, named: dry-run
  (no rollback-only transaction wrapping exists, and adding one is a design of its own), and an in-runtime
  confirmation step (the client's hint-driven confirmation is the confirmation in DL-1).

#### 4.17.7 CLI (Volume 11 section 3.5)

`tibyan draft` exists and gains the merge and the fingerprint stamp. There is no review command: review is editing
the file. `tibyan scan` reports `reviewed N` beside `discovered` and `attested`, never summed, plus `drafts pending
M` and `drafts refused K` with each refusal's cause and target. `tibyan generate` and the Maven plugin emit reviewed
capabilities from the IR like any other. The survey prints the tool's label and the reviewer's name for every
reviewed capability.

#### 4.17.8 The description assist stays outside the tool

The file contract is the assist boundary, unchanged from AT-4: any agent may fill `description` and set
`descriptionOrigin: ai-draft`; only a named human's `approved` ships it. Adding a model call to the tool remains a
Volume 16 decision with the open questions of section 4.15.5.1, and DL-1 does not take it. On as-backend the
existing adoption prompt is the assist.

#### 4.17.9 What this phase changes in the constitution, listed for the ratification

1. CLAUDE.md rule 5: the IR is entities plus capabilities of kind READ, WRITE and ACTION; the ledger gains the
   `Review` component and rules 12 and 13; "read-only capabilities" is no longer the IR's definition.
2. The refusal bias is restated: a false refusal is a coverage miss and a false pass is a safety breach FOR A
   PROOF; a labeled, reviewed capability is neither a proof nor a pass, it is a named human's decision carried
   faithfully, and the tool's guarantee for it is carriage, fingerprint, label drift and the gate, never truth.
3. "Fail loud, never guess" applies to proofs and labels: the tool never upgrades a label to a proof, never
   authors a description, and refuses every intake condition loudly. It no longer applies to EMISSION: an
   unprovable method is drafted, not refused.
4. The read-only boundary of docs/SECURITY.md becomes the write gate: writes exist, are off by default, and open by
   exact name only.
5. AT-4's no-batch rule is scoped to attestations; drafts are reviewed in batch by design (4.17.2).
6. "Zero wrong bindings" and the four zero invariants are claims about the VERIFIED and ATTESTED tiers. A new
   counter, `review-drift 0` (no approved entry whose fingerprint or label moved without being caught), joins them
   as the reviewed tier's invariant.

#### 4.17.10 Pre-registered fixtures (RV-1 to RV-12) and the forecast

- RV-1: an approved READ draft with mapped inputs becomes `reviewed_<name>`, kind READ, readOnly true, Review
  complete, on IR, manifest, OpenAPI and survey.
- RV-2: an approved WRITE draft becomes kind WRITE, readOnly false, and does NOT register without
  `tibyan.writes.allow`; with its exact name listed it registers and invokes end to end (the example app gains
  one write method for this, the Q3 the spike deferred).
- RV-3: an approved UNKNOWN draft becomes kind ACTION and is gated like a write.
- RV-4: a `pending` entry produces nothing and is counted as pending; a `rejected` entry produces nothing and is
  not a refusal.
- RV-5: RV-B, a stamped fingerprint that no longer matches refuses STALE_DRAFT.
- RV-6: RV-C, a READ approved and then made a WRITE refuses LABEL_DRIFT naming both labels.
- RV-7: RV-D, an approved entry whose target is a discovered binding refuses SUPERSEDED; the verified capability
  is unchanged.
- RV-8: RV-E, a collection input refuses INPUT_UNMAPPED naming it; an in-tree enum input binds with its closed set
  and EI-R refuses an illegal value at invoke.
- RV-9: RV-G, a blank description or reviewer refuses REVIEW_INCOMPLETE.
- RV-10: rule 12, a WRITE capability without a Review fails core validation; rule 13, the prefix both ways.
- RV-11: re-running `tibyan draft` keeps reviewer fields, appends new methods, marks a vanished target stale, and
  resets a label-changed entry to pending.
- RV-12: an IR with no approved drafts serializes byte-identically, and the three benchmarks, which carry no
  approved drafts, stay byte-identical across IR, MCP and OpenAPI output.

FORECAST. pms and erp: byte-identical, no record file. as-backend: byte-identical until the maintainer approves
entries; the first approvals are the reading sample of section 10, so the phase's first real measurement is the
sample's error rate and the count of approved entries that pass intake. Every one of the five counters unmoved.

#### 4.17.11 Open decisions for the maintainer, each with a recommendation

- D1, THE SECURITY-UNVERIFIABLE LIST. Section 10's `tibyan.attested.exposeReviewed` is ratified and unbuilt.
  Recommendation: amend section 10 D5 before its build to ONE exact-name list for security-unverifiable
  capabilities of any tier, `tibyan.security.exposeUnverifiable`, with section 10's five conditions, so the
  adopter does not maintain two lists with one meaning.
- D2, DEFAULT EXPOSURE OF REVIEWED READS. Recommendation: TRUE, as for attested: the committed approvals are the
  opt-in, and a second switch adds friction without honesty.
- D3, THE PREFIX. Recommendation: `reviewed_`, reserved both ways (rule 13), because E6b was ratified on evidence
  and a truncated description is the case it was ratified for.
- D4, UNKNOWN DRAFTS. Refuse them, or admit them as kind ACTION behind the write gate. Recommendation: ACTION
  behind the gate; 362 of the 365 are void methods a reviewer may legitimately want served, and the gate is what
  makes that safe to allow by name.
- D5, UNRESOLVABLE OUTPUTS. Refuse, or the permissive object. Recommendation: the permissive object, the same
  partiality rule attested SHAPE already uses; a reviewer approving a read accepted its shape.
- D6, COLLECTION INPUTS. Recommendation: DEFER TO DL-2 by name with the trigger recorded: 66 READ entries on
  as-backend carry List<Long> or List<Integer>, so DL-2 has its evidence the day DL-1 ships.

#### 4.17.12 Ratification record (2026-09-23)

Ratified by the maintainer on 2026-09-23, as written, in the session that built spike DL-0 and designed this
section; the build is the next session (one phase per session). The six decisions, each standing at its
recommendation because the section was ratified as written, none separately chosen:

- D1: ONE exact-name list for security-unverifiable capabilities of any tier, `tibyan.security.exposeUnverifiable`,
  under Volume 7 section 10's five conditions. Volume 7 section 10 D5 is AMENDED accordingly before its build
  (recorded in Volume 7 section 10.10); the two builds share one list.
- D2: reviewed READ capabilities register by default (`tibyan.reviewed.expose` default true).
- D3: the prefix is `reviewed_`, reserved both ways (Volume 4 rule 13).
- D4: an approved UNKNOWN draft is admitted as kind ACTION behind the write gate.
- D5: an unresolvable output renders the permissive object of Volume 6 section 4.1.
- D6: collection inputs refuse with `INPUT_UNMAPPED` and are deferred to DL-2 by name, trigger recorded.

WHAT THE RATIFICATION ALSO DECIDES: the six constitution changes of section 4.17.9. CLAUDE.md rule 5, the refusal
bias and the fail-loud discipline are reworded in the same commit as this record, with the reviewed tier marked
as effective on the DL-1 build; docs/SECURITY.md's read-only boundary becomes the write gate IN THE BUILD, where
the gate exists to describe. Volume 4 gains rules 12 and 13 in its section 5 now, as ratified rule text awaiting
the build.

THE OPENING OF THE BUILD SESSION: re-read sections 4.17.3, 4.17.6 and this record; verify against the repository
that spike DL-0 is at commit fdd5ec3; run the reactor; then build in this order: core (Review, kinds, rules 12 and
13), provider intake (RV-A to RV-H, the merge and the stamp), backends (manifest and OpenAPI, golden files
re-baselined), runtime (the gate, the composition, the hints check), CLI (the survey counts), the example app's
write method and the end-to-end write behind the gate (RV-2), then the benchmark byte-identity check against a
worktree at the pre-build commit. The maintainer's reading sample of the drafts file is the first real measurement
and is recorded in as-backend-trial.md section 10 whenever it is done, before or after the build.


#### 4.17.13 DL-1 build record (BUILT 2026-09-23)

Built in the session that ran spike DL-0, designed and ratified this section, at the maintainer's explicit
instruction. THAT BREAKS THE ONE-PHASE-PER-SESSION RULE and is recorded here as a process exception, not a
precedent: the maintainer directed "start building DL-1" after ratifying it in the same session. Rule 12 held:
the frozen text (commit 89b3441) landed before any DL-1 code.

WHAT IS BUILT, by layer, in the order section 4.17.12 gives:

- CORE. `Review(label, reviewedBy, reviewedOn, descriptionOrigin, fingerprint)` and `ReviewLabel` (READ, WRITE,
  UNKNOWN, each mapping to a kind); `Capability` gains the optional `review` component, `hasReview()` and the
  `Capability.reviewed(...)` factory that sets kind from the label and readOnly only for READ. Validation rules 12
  and 13 as ratified, plus two guards rule 12 implies: a capability is in exactly one tier (never both a review
  and an attestation), and the kind must equal the label's kind. `tibyan-core` stays zero-dependency.
- PROVIDER. The drafts record of 4.17.2 (fingerprint stamped over the attestation slice, equal by test to
  `attestationFingerprint`), the merge, and `DraftIntake` with RV-A to RV-H, wired into `scan` after the attestation
  intake and before the section 4.14 security composition. `ScanReport` gains the draft refusals, the pending count
  and `reviewedCount()`.
- BACKENDS. Manifest `kind` (emitted only when not READ), `tier`, `review{}`, and the class as the first sentence;
  OpenAPI GET for reviewed reads and POST for WRITE and ACTION. Every existing golden file is byte-identical and
  new golden cases cover the reviewed shapes.
- RUNTIME. `tibyan.reviewed.expose`, `tibyan.writes.allow` and `tibyan.security.exposeUnverifiable`, both lists
  bound through the Spring `Binder` and checked at startup under section 10's X1 to X5, the gate with its five
  exclusion reason tokens, and startup honesty. THIS ALSO BUILDS VOLUME 7 SECTION 10 under its D5 amendment: the
  exact-name security list, RX-1 to RX-10, the WARN, and the registered-names fix.
- CLI. `tibyan draft` merges into an existing file; `tibyan scan` prints the reviewed tier, the draft refusals and
  the pending count, and the Totals line gains `reviewed N` and `review-drift 0` only where they apply.
- END TO END (RV-2, the Q3 the spike deferred). The example app gains one write, `ProductService#rename`. An
  integration test drafts it, approves it in the file, scans, generates, and starts the packaged app twice: without
  the allow list the tool is not advertised; with its exact name listed it runs against the real H2 store and the
  verified `getProduct` read observes the change.

BUILD DECISIONS, each a place where the ratified text did not decide and the build had to, recorded so the
text and the code do not diverge silently:

1. THE ATTESTED GLOBAL SWITCH IS ATTESTED-ONLY. `tibyan.attested.exposeSecurityUnverifiable` never opens a
   reviewed capability; a security-unverifiable reviewed capability opens only by exact name in
   `tibyan.security.exposeUnverifiable`. The switch's name says attested, and widening it to a tier that did not
   exist when it shipped in 0.3.0 would be the silent widening section 10 exists to remove.
2. AN APPROVED ENTRY KEEPS THE FINGERPRINT THE REVIEWER APPROVED. A re-run of `tibyan draft` re-pins pending and
   rejected entries only; an approved entry whose body changed is refused STALE_DRAFT at scan until a person
   re-approves it. Re-pinning an approval on a re-run would let a changed body ship under an old signature.
3. RV-6 FIRES ONLY WHERE THE SLICES DIFFER. Any edit inside the method body changes the fingerprint, so RV-B
   refuses first. LABEL_DRIFT therefore fires when the label moves through code the fingerprint slice does not
   cover, such as the one-hop private helper read-safety follows. That gap between the labeled slice and the
   fingerprinted slice is real and is exactly the case RV-C exists for; the test pins it.
4. NAMES. A reviewer may rename an entry, so intake refuses as REVIEW_INCOMPLETE an empty or non-identifier name,
   a name already carrying `reviewed_`, and a name colliding with an existing capability. A new candidate whose
   minted name collides with a reviewer's name takes a numeric suffix; a reviewer's name is never changed.
5. OUTPUTS. `void` and an undescribable return carry the placeholder output STRING with a `void` or
   `opaqueOutput` annotation, and both surfaces disclose it (manifest `void: true` or `opaqueOutput: <type>`;
   OpenAPI 204, or the permissive object of Volume 6 section 4.1). `List`, `Set` and `Collection` of an element
   set `outputMultiple`; `Page`, `Optional` and `Iterable` are opaque.
6. THE DESCRIPTION rides an annotation named `description` on the reviewed capability, because `Review` has no
   sentence slot and the backends already read annotations; the reviewer's words are carried verbatim.
7. OPENAPI PATHS for reviewed operations are `/reviewed/<name>` with every input a query parameter (GET) or a
   JSON body (POST), because nothing proves a reviewed input is an identifier, so no entity path is derived.
8. THE RUNTIME REFUSES A MANIFEST that carries a WRITE or ACTION without the reviewed tier, or an unknown kind, at
   startup: the runtime-side mirror of rule 12.
9. THE IR ACCESSOR is `Review.complete()`, not `isComplete()`: an is-prefixed method is serialized by the IR mapper
   and broke the round-trip, the trap `Capability.hasAttestation` already documents. Caught by the integration test.

RULE-13 AMENDMENT, flagged: the startup line "registered N MCP tool(s)" now names only the registered tools and each
excluded tool is logged with its reason. No registration outcome changed.

THE HINTS GAP, measured: the pinned MCP SDK 0.18.3 has `McpSchema.ToolAnnotations`, but the Spring AI 1.0.9 bridge
never sets it (`ToolDefinition` carries name, description and schema only; `McpToolUtils.toSyncToolSpecification`
never calls `annotations`). readOnlyHint and destructiveHint are NOT carried. The name prefix and the first sentence
carry the tier; a tripwire test fails when a stack bump adds the surface; registering `SyncToolSpecification` beans
directly is the recorded option for a Volume 16 session.

MEASURED (2026-09-23): `mvn clean verify` green across 12 modules; tests core 38, pipeline 10, provider 235,
backend-mcp 14, backend-openapi 13, runtime 68, cli 16, maven-plugin 2, integration 5 (3 existing, 2 new).
pms, erp and as-backend are BYTE-IDENTICAL across the serialized IR, the generated MCP output and the generated
OpenAPI document, by `diff -r` against a worktree at the pre-build commit 89b3441; none carries an approved draft.
All counters unmoved: discovered 5 / 37 / 5, attested 0 / 0 / 15, mis-bound 0, attestation-drift 0,
mis-constrained 0.

NOT DONE, named: the maintainer's reading sample of the as-backend drafts file (the first real measurement of
the READ label, still owed); the as-backend runtime check of section 10.10's exit criterion (the fifteen names in
`tibyan.security.exposeUnverifiable` with the global flag removed from the local profile, the same fifteen
registered), which needs the adopter's services running; dry run and in-runtime confirmation (not in scope by
name); collection inputs (DL-2).


#### 4.17.14 DL-1.1: the bean-call closure of the READ label (designed, RATIFIED and BUILT 2026-09-23; build record at 4.17.15)

STATUS: RATIFIED 2026-09-23 AS WRITTEN by the maintainer, so D1 to D3 stand at their recommendations: a hop bound of
four, UNKNOWN on an ambiguous C2 resolution, and READ for a remote GET with the call named in the reason. It changes
labels, which are matching outcomes, so under rule 12 this record is committed before any code.

THE EVIDENCE. The reading sample of docs/experiments/as-backend-trial.md section 10.2: 3 of 30 sampled READ labels
hide a write or an external effect, and all three are calls into another bean (a JMS publisher, a counter service
three hops away, a reflective condition dispatcher). A READ label drives `readOnly` true and default exposure of a
reviewed read, so a false READ is the reviewed tier's mis-safe. The reviewer is the gate, but a label that is wrong
one time in ten trains the reviewer to trust it wrongly.

THE RULE. The draft labeler's READ verdict becomes CLOSED over bean calls. For a candidate method M, read-safety
runs as today over M, its one-hop private helpers and the repository declarations they call; then every call in
that slice whose receiver is a FIELD of the declaring class is classified, and the most severe classification
wins (WRITE over UNKNOWN over READ):

- C1, REPOSITORY FIELD: as today (write-set names, `@Modifying`, a mutating `@Query`).
- C2, IN-TREE BEAN: the field's declared type resolves in the tree to exactly one concrete class carrying a Spring
  stereotype (`@Service`, `@Component`, `@Repository`) or is that class. The called method is resolved by name and
  arity on it and LABELED RECURSIVELY by these same rules. Its label propagates: WRITE gives WRITE, UNKNOWN gives
  UNKNOWN. Recursion is bounded at four bean hops and cycle-safe (a method already on the path contributes READ);
  a chain that exceeds the bound gives UNKNOWN, named.
- C3, EXTERNAL EFFECT: the field's type is a known messaging, mail or event type (`JmsTemplate`,
  `JmsMessagingTemplate`, `KafkaTemplate`, `RabbitTemplate`, `AmqpTemplate`, `StreamBridge`,
  `ApplicationEventPublisher`, `JavaMailSender`, `SimpMessagingTemplate`): WRITE, reason "external effect". The set
  is closed; extending it is a design change.
- C4, REMOTE CLIENT: an in-tree interface annotated `@FeignClient` gives READ for a method mapped with
  `@GetMapping` or `@RequestMapping(method = GET)` and WRITE for any other verb; a `RestTemplate` or `WebClient` call
  gives READ for `getFor*` and `get()`, and UNKNOWN otherwise.
- C5, REFLECTION: any call to `getBean`, `Method.invoke` or `BeanFactory` resolution in the slice gives UNKNOWN,
  reason "the executed code is chosen at run time".
- C6, EVERYTHING ELSE: a field whose type does not resolve in the tree and is not in C3, C4 or the closed PURE set
  gives UNKNOWN. The PURE set is closed: interfaces annotated MapStruct `@Mapper` (their generated implementations
  are not in the tree), `ObjectMapper`, `Clock`, the JDK and the logging facade.

THE LABELER ONLY. Read-safety for ATTESTATION intake is unchanged: this refines what a DRAFT's READ label claims,
not what an attestation's read-safety check proves. The two may converge later; that is its own decision.

WHAT IT FIXES, forecast for the build: `downloadFile` relabels WRITE through C2 then C3 (`MQClientServiceImpl`
holds a `JmsTemplate`); `generateGlobalKeyAttributeId` relabels WRITE through C2 (the counter chain ends in a
repository `save`); `getEligibleTransitions` relabels UNKNOWN through C2 then C5. THE COST, forecast honestly:
some of the 27 audit-confirmed pure reads will drop from READ to UNKNOWN where they call a bean the rule cannot
resolve; the build measures that number and it is a coverage cost, not a failure. Approved entries whose label
moves are caught by RV-C (LABEL_DRIFT) at the next scan.

FIXTURES (LC-1 to LC-7): LC-1 a read calling an in-tree read-only service stays READ; LC-2 a read calling an
in-tree service whose method saves is WRITE with the chain in the reason; LC-3 a read calling a `JmsTemplate`
through a service is WRITE; LC-4 a `@FeignClient` GET is READ and a POST is WRITE; LC-5 `getBean` plus `invoke` is
UNKNOWN; LC-6 a five-hop chain is UNKNOWN at the bound; LC-7 a MapStruct mapper call stays READ. Plus the three
as-backend methods of section 10.2 relabeled in the census, and every benchmark byte-identical across IR, MCP and
OpenAPI (the labeler never reaches the IR except through an approved draft, and none exists).

OPEN DECISIONS for the maintainer, each with a recommendation:

- D1, THE HOP BOUND. Recommendation: four. The counter chain needed three.
- D2, WHERE A C2 RESOLUTION IS AMBIGUOUS (two concrete implementations of the field's interface). Recommendation:
  UNKNOWN, named; the runtime picks one bean and the tool cannot say which.
- D3, WHETHER C4 GET IS READ. Recommendation: READ, with the reason naming the remote call, because a GET that
  mutates is the remote service's defect; the reason tells the reviewer a network call happens.


#### 4.17.15 DL-1.1 build record (BUILT 2026-09-23)

Built the same day it was ratified, in a session that also built DL-1: a second process exception at the
maintainer's instruction, recorded as such. Rule 12 held (record fd64243 before code). The labeler only: attestation
intake, discovery, the IR and every surface are untouched, and the three benchmarks are byte-identical.

WHAT IS BUILT: `BeanCallClosure` in the draft package, consulted by `DraftLabeler` after read-safety; the most
severe finding wins. It keeps its own simple-name index with interfaces included, because the shared
`TypeReferenceResolver` indexes classes and enums only (it keys entities) and a bean field is usually typed by
an interface. Fixtures LC-1 to LC-7 as tests (`BeanCallClosureTest`); the DL-0 fixture's `describe` case, the blind
spot DL-0 recorded, moves from READ to UNKNOWN and its test says why.

BUILD DECISIONS, where the ratified text did not decide:

1. SAME-CLASS CALLS ARE FOLLOWED WITHIN THE BEAN, no hop counted. A call to the class's own non-helper method
   (an overload, a public delegate) is stepped into; an ambiguous self overload of one arity is UNKNOWN. Without
   this the counter write of section 10.2 stays hidden behind `getNextValueAsString(type)` delegating to its
   two-argument overload.
2. AN ABSTRACT CLASS RESOLVES TO ITS CONCRETE SUBCLASSES, exactly as an interface resolves to its implementations
   (C2). One stereotyped subclass is the bean; two are UNKNOWN (D2); none is C6.
3. ANY `@Mapper` TYPE IS PURE, interface or abstract class: MapStruct generates the implementation outside the
   tree, and every mapper call in the reading sample was a pure mapping.
4. THE PATH IS AN IDENTITY SET. JavaParser nodes compare structurally, so a chain of look-alike methods (LC-6's
   `go()` delegates) read as a cycle until the path compared by identity.
5. `RestTemplate` GET-family calls and `WebClient.get` are READ with the call named; `RestClient` (Spring 6.1)
   is not in the ratified C4 set and falls to C6 UNKNOWN, recorded for the next amendment.

MEASURED ON as-backend (990 candidates, same tree):

| Label | DL-1 | DL-1.1 |
|---|---|---|
| READ | 401 | 271 |
| WRITE | 204 | 337 |
| UNKNOWN | 365 | 362 |

The three false READs of section 10.2 are caught: `downloadFile` is WRITE through C2 then C3 (the chain names
`MQClientServiceImpl#sendMessage` and `JmsTemplate`), `generateGlobalKeyAttributeId` is WRITE through C2 (four
steps to `CounterInternalServiceImpl#getNextValue` and its repository insert), `getEligibleTransitions` is UNKNOWN
through C5. The 20 audited WRITEs stay WRITE. Of the 27 audit-confirmed pure reads, 22 stay READ and 5 drop to
UNKNOWN: one D2 ambiguity (`SystemSettingsProxyService` has two implementations), three calls on an abstract
security config with no stereotyped subclass, one `RestClient`. That is the forecast coverage cost, measured: 5 of
27. Two audited UNKNOWNs became WRITE, correctly: void notification handlers that publish. THE LARGEST MOVEMENT is
the one nobody forecast: 214 of the 337 WRITEs are C3, a message publisher behind a bean, mostly void event
handlers; DL-1 had labeled them UNKNOWN, and a reviewer would have had to find the publish by hand.

THE REMAINING UNKNOWN (362): 135 void with nothing found, 123 C6 (54 `ObjectProvider`, 28 the abstract security
config, 19 `Gson`, 15 `RestClient`), 96 C2 (ambiguous implementations and unresolved methods), 5 C5, 3 read-safety.
The C6 list is the evidence for the next amendment of the closed sets.

`mvn clean verify` green across 12 modules (provider 242 tests). pms, erp and as-backend byte-identical across
IR, MCP and OpenAPI against a worktree at fd64243. The adopter's uncommitted `tibyan/drafts.json` was re-drafted
through the merge: 243 entries carry `previousLabel`, all 990 stay pending, the 11 prepared descriptions survive.


#### 4.17.16 DL-1.2: the closed-set amendment of the bean-call closure (designed 2026-09-23, RATIFIED and BUILT 2026-09-24; build record at 4.17.17)

STATUS: RATIFIED 2026-09-24 AS WRITTEN by the maintainer, committed before code (rule 12). It amends section 4.17.14's closed sets and one of its decisions; the mechanism is unchanged.

THE EVIDENCE, from the DL-1.1 census on as-backend (section 4.17.15; docs/experiments/as-backend-trial.md section
10.3). Of the 362 UNKNOWN labels, 123 are C6 (a field type the closure cannot place) and 96 are C2. Read by type:

| Cause | Count | What it is in the adopter |
|---|---|---|
| `ObjectProvider<T>` field, `getIfAvailable` | 54 | Spring's lazy bean provider; the call obtains a bean, it mutates nothing |
| an abstract config class with no stereotyped subclass | 31 | its one subclass is `@Configuration @ConfigurationProperties`; the called methods are property getters and a Base64 encoding of two of them |
| `Gson` | 19 | `toJson` 18, `fromJson` 1: pure serialization |
| `RestClient` (Spring 6.1) | 15 | `get` 14, `post` 1: the verb is explicit in the call |
| `PasswordEncoder` | 4 | `matches` and `encode`: pure computation |
| two implementations of one proxy interface (D2) | 77 | a local implementation and a Feign one, chosen by profile |
| no single method of that name and arity on the bean | 18 | same-arity overloads |

THE AMENDMENT, five clauses, each traceable to a row above:

- A1, CONFIGURATION IS A BEAN. `@Configuration` and `@ConfigurationProperties` join the C2 stereotype set, so an
  abstract config class resolves to its one configured subclass and its getters are followed. A getter with no
  in-tree body (a Lombok accessor) is READ, as any bodiless same-class method already is.
- A2, PROVIDERS ARE TRANSPARENT. A call to `getObject`, `getIfAvailable`, `getIfUnique` or `get` on a field typed
  `ObjectProvider<T>`, `Provider<T>` or `Supplier<T>` is READ in itself, and a call chained directly on its result
  (`provider.getIfAvailable().m()`) is classified as a call on a field of type T. A provider result stored in a
  local and called later is not followed, exactly as no local receiver is followed today; that residue is named.
- A3, THE PURE SET GAINS `Gson` and `PasswordEncoder`. Both are computations over their arguments. The set stays
  closed; `ObjectMapper` was already in it.
- A4, FLUENT HTTP CLIENTS GO BY THEIR VERB. On a field typed `RestClient` or `WebClient`, `get` is READ with the
  call named (as C4 already says for `WebClient`), and `post`, `put`, `patch` and `delete` are WRITE, because the
  verb is explicit in the call exactly as it is on a Feign method. `RestTemplate` keeps its C4 rule: its verb is an
  argument to `exchange`, so anything but `getFor*` stays UNKNOWN.
- A5, DECISION D2 AMENDED: AN AMBIGUOUS IMPLEMENTATION TAKES THE WORST LABEL ACROSS ALL OF THEM. The runtime picks
  one implementation, and the label is sound whichever it picks only if every implementation is labeled and the
  most severe wins: all READ gives READ, any WRITE gives WRITE, otherwise UNKNOWN, with the reason naming every
  implementation and its label. This is stricter than the ratified D2 in one direction (a WRITE in any
  implementation now surfaces) and looser in the other (77 profile-selected proxies whose implementations all
  read stop being UNKNOWN). The 18 same-arity overload cases stay UNKNOWN: nothing in the call decides which.

FORECAST, to be checked by the build: C6 falls from 123 to about 3 (the two static-call cases and `Files` stay);
C2 falls from 96 to about 19 (the overloads and any mixed-label pair); READ rises and WRITE rises by the split of
the 200 addressed entries, which the build reports exactly. The 22 audited pure reads that kept READ keep it; of
the 5 that dropped, the abstract-config three and the `RestClient` one return to READ under A1 and A4, and the D2
one returns to READ if both implementations read. The three false READs of section 10.2 stay caught: none of the
five clauses touches C3 or C5. Every benchmark byte-identical, by construction as in 4.17.15.

FIXTURES LA-1 to LA-6: LA-1 a `@ConfigurationProperties` subclass of an abstract config, its getter is READ; LA-2
`provider.getIfAvailable().save()` on an `ObjectProvider<Repo>` is WRITE, and `provider.getIfAvailable()` alone is
READ; LA-3 `gson.toJson` and `encoder.matches` are READ; LA-4 `restClient.get()` is READ with the call named and
`restClient.post()` is WRITE; LA-5 two implementations that both read give READ, one that writes gives WRITE, one
that is UNKNOWN gives UNKNOWN; LA-6 two same-arity overloads stay UNKNOWN. Plus the as-backend re-census against
the forecast above.

No open decisions: each clause is a closed-set change with one honest reading of its evidence.


#### 4.17.17 DL-1.2 build record (BUILT 2026-09-24)

Built the day after ratification (record 71e03f4 before code). Labeler only; IR, MCP and OpenAPI byte-identical on
pms, erp and as-backend against a worktree at 71e03f4. Fixtures LA-1 to LA-6 as `ClosureAmendmentTest`; provider
248 tests; `mvn clean verify` green across 12 modules.

MEASURED ON as-backend (990 candidates, same tree):

| Label | DL-1.1 | DL-1.2 | Forecast |
|---|---|---|---|
| READ | 271 | 346 | up |
| WRITE | 337 | 343 | up |
| UNKNOWN | 362 | 281 | down |
| of which C6 | 123 | 3 | about 3 |
| of which C2 | 96 | 67 | about 19 |

WHAT HELD: every one of the 27 audit-confirmed pure reads is READ again (the five DL-1.1 had dropped return under
A1, A4 and A5); 20 of 20 audited WRITEs hold; the three false READs of section 10.2 stay caught. C6 is exactly the
forecast three: two `ThreadContext` statics and one `Files.delete`, which are read-safety's own UNKNOWN.

WHAT THE FORECAST MISSED, an unforecast movement and therefore a finding under the process rule: C2 fell to 67,
not 19, and 55 of those are "no single method of that name and arity" on the bean. Before DL-1.2 the closure never
reached those beans (they sat behind a config, a provider or an ambiguous interface), so their overloads were
invisible; A1, A2 and A5 opened the door and the overloads are what was behind it. Same-arity overload
disambiguation by argument type is a rule of its own, not a closed-set change, and is recorded as the next
candidate with its count. The other 11 C2 are A5 pairs whose implementations disagree, as designed; 1 is a self
overload. The void UNKNOWN count rose from 135 to 206 for the same reason: void methods whose only field call was
C6 now resolve, find nothing, and fall through to the void rule, which is the honest label for them.

BUILD DECISIONS: none beyond the ratified text. One note for the reasons: a provider-chained write
(`provider.getIfAvailable().save(x)`) is caught by read-safety's own L1 clause before the closure runs, so its
reason is read-safety's, not A2's; the label is the same.


#### 4.17.18 DL-1.3: bodiless accessors and overload disambiguation in the closure (designed 2026-09-24, PENDING RATIFICATION)

STATUS: DESIGNED, NOT RATIFIED, NOT BUILT. It changes labels, so under rule 12 it takes its own ratification before
code. Two rules, each with its own count.

THE EVIDENCE, the unforecast movement of section 4.17.17: 55 UNKNOWN labels on as-backend read "no single method of
that name and arity on the bean". Read by target they are two different things:

| Case | Count | What it is |
|---|---|---|
| a getter on a `@Configuration` class with NO method in the file | 15 | Lombok `@Getter` accessors of declared fields (`PharmacyConfig#getRoleIdsHasAccessToPharmacyDetails` 9, `getPharmacyReqEmployeeRoleId` 2, `SecurityAdapterConfig#getInternalMS`, `getExpirationTimeInHours`, two more) |
| `RequestServiceImpl#processRequest(Long, Events \| SCEvent, Map, String)` | 25 | two overloads differing in the second parameter's type; every caller passes an enum constant |
| `JWTServiceImpl#extractClaim(String, String \| Function)` | 7 | a public String overload and a private generic one |
| `ProductAdapterServiceImpl#getProductDetails(ProductIdDTO \| Long)` | 4 | a DTO and a primitive overload |
| `cascadeActivateProduct` and three siblings, arity 1 | 4 | an id overload and an entity overload |

THE RULES:

- B1, A BODILESS ACCESSOR OF A DECLARED FIELD IS READ. When the bean declares no method of the called name but
  declares a field `x` such that the call is `getX()`, `isX()` or `x()` with no arguments, the call reads a field
  and is READ. Lombok generates exactly these; the same-class path already treats a bodiless self call as READ, so
  this makes the cross-bean path agree. Anything else with no declared method stays UNKNOWN.
- B2, OVERLOADS ARE DISAMBIGUATED BY THE STATIC TYPE OF EACH ARGUMENT, where the closure can read it: a parameter
  of the calling method (its declared type), a local declared with an explicit type (`var` does not count), a field
  of the calling class, a literal (String, integer, long, boolean, null), an enum constant written `Enum.CONST`
  where `Enum` resolves in the tree, or a `new T(...)` expression. Each overload's parameter simple types are
  compared position by position, with boxing equivalence (`long` and `Long`) and `null` matching any reference
  type. Exactly one overload matching gives that overload; zero or more than one, or an argument the closure cannot
  type (a method-call result, a lambda, a cast, an arithmetic expression), keeps UNKNOWN with the reason naming the
  untyped argument. Applies to the cross-bean path (C2) and the same-class path alike.

WHAT THIS DOES NOT DO: it does not resolve a method-call argument's type through its callee (that is type
inference, not a lookup), and it does not choose among overloads by subtype (an argument typed by an interface
against an overload typed by its implementation stays ambiguous).

FORECAST: B1 moves the 15 config getters to READ. B2 resolves the 25 `processRequest` calls whose second argument
is an enum constant, the 4 `getProductDetails` calls with a `Long` parameter or literal, and the 4 `cascade*` calls
with an id parameter; the 7 `extractClaim` calls resolve where the argument is a String literal or parameter and
stay UNKNOWN where it is a lambda. So at most 55 labels move, at least 15, and the build reports the number. The
27 audited pure reads, the 20 audited WRITEs and the three caught false READs are unaffected: none of them was a
"no single method" case. Every benchmark byte-identical, by construction.

FIXTURES LB-1 to LB-5: LB-1 a `@Getter`-style bodiless `getX()` on a field `x` is READ; LB-2 two overloads
distinguished by a parameter's declared type, one saving and one reading, label by the chosen one; LB-3 an
enum-constant argument picks the enum overload; LB-4 a null literal against two reference overloads stays UNKNOWN;
LB-5 a method-call argument stays UNKNOWN with the argument named.

No open decisions.

## 5. Future providers

| Provider | Source | Why it matters |
|---|---|---|
| Database provider | A database schema | Proves the IR accepts a non-code source (Volume 13, Tier 3) |
| OpenAPI provider | An OpenAPI document | Imports systems that already publish a contract |
| Manual builder | A human-authored definition | A fallback when no readable source exists |
| AI-assisted provider | Mixed or unclear sources, with model assistance | Helps where structure is implicit, with human review |
| Bytecode analyzer | Compiled classes | Reads systems whose source is unavailable |

Each new provider produces the same IR, so every existing backend and runtime works against it with no change. That is the entire point of the abstraction.

## 6. Rules

1. A provider feeds the IR and stops. It produces no output.
2. A provider never depends on a backend or a runtime.
3. A provider maps all source types into the closed primitive set; unknown types are a validation concern, not a silent passthrough.
