# Experiment: as-backend, the fourth app and the mis-bind that came from it

as-backend is a large multi-module Spring application, an ERP with a pharmacy inventory system
alongside it, built on the author's private foundation libraries (the same family erp-backend and
pms-backend use). It was surveyed on 2026-08-31 at the maintainer's request, as an adopter question
("what can I build with this, and how do I get all my capabilities") rather than as a planned
benchmark run. It produced the project's second defect found by its own measurement, and the first
one that was a MIS-BIND.

It is NOT the private enterprise benchmark (docs/experiments/enterprise-benchmark.md), which is a
different application under permanent redaction. as-backend is one of the author's own systems and is
recorded here the way pms-backend and erp-backend are, by name.

---

## 1. Structural profile (2026-08-31)

- Java files 2844. `@Entity` files 186 (by grep) resolving to 159 entities the scan classifies.
  `@Service` files 314. Controller files 147. Parse failures 0: the tree parses 100 percent clean at
  the Java 17 language level.
- Entity distribution by area: 114 in the ERP services, 36 in the pharmacy inventory modules, and
  roughly 29 in the foundation libraries (id-counter, workflow-engine, sql-db-adapter,
  service-context, user-adapter). The foundation entities are plumbing and nobody would want them
  exposed; "all 159 capabilities" was never the goal, and saying so is part of reading the number
  honestly.

## 2. Survey result, pre-phase (2026-08-31, before Read-Identity)

```
discovered 6 (by-id 6, list 0), failed 153 (NO_BACKING_METHOD 153), list-refused 0,
skipped-by-parse 0, partial 15 (17 fields skipped), mis-bound 0
```

Deferred reads: 219, in four shapes.

| Count | Reason | Attestation category |
|---|---|---|
| 115 | returns a collection of a type that is not an in-tree `@Entity` (a projection list) | SHAPE (AT-2) |
| 86 | the wrapper return is built from more than one repository access (E6a, section 4.11.7) | SHAPE (AT-2) |
| 15 | `OPAQUE_WRAPPER` | SHAPE (AT-2) |
| 3 | takes domain parameters, a deferred filtered list read (section 4.10, condition 3) | PREDICATE (AT-1) |

The three discovered by the section 4.1 entity-returning path were `getSyncedUser`, `getOrder`, and
`getCPESCustomerProduct`; the other three came through the section 4.2 view path.

## 3. What this app says about coverage, and it is not a defect

The dominant read idiom here is the aggregate-enrichment read: a clean load by primary key, then one
or more secondary repository reads, then a multi-argument mapper. Structurally, for example, a load
of the entity, a filtered load of its roles, and `mapper.toUserVTO(user, roles)`. Section 4.2 permits
exactly one persistence access and a single-argument mapping step, so it defers. The list side is the
same story through `selectAllByFilters` plus a `*ResultSet` wrapper.

6 of 159 is the tool refusing where it cannot prove, not the tool failing. The 201 SHAPE-category
deferrals are precisely what AT-2 is designed for, and AT-1 as built unlocks essentially nothing
here: all three PREDICATE-shaped candidates take domain-typed parameters that fail intake's parameter
mapping. This app is therefore a strong second piece of evidence for AT-2's ranking, independent of
pms.

## 4. The mis-bind (2026-08-31), and the phase it forced

`getCPESCustomerProduct(id: LONG)` had bound to
`CustomerProductAdapterServiceImpl#validateCustomerProductExistence(Long id)`. That method satisfies
every one of section 4.1's four conditions, and does none of what the capability name promises:

- it treats `id` as a FOREIGN key (a product id), not the entity's primary key;
- it builds a `CustomerProductSearchFilter` and calls `selectAllByFilters`, a filter query;
- it returns element 0 of the filtered list;
- on a miss it calls a remote product adapter, and either returns `null` or throws.

An agent calling that tool with a CPESCustomerProduct primary key gets a different row or an
exception. This is exactly the `findByUserId` trap section 4.2.1 anchors its whole rule against,
admitted through the one path that never checked a body. The other two 4.1-path reads on this app
(`SyncedUserAdapterServiceImpl#getUserById`, `OrderServiceImpl#getOrderById`) are clean one-hop
`selectById` loads and were correct.

The phase is Volume 5, section 4.1.1, ratified and built the same day.

## 5. Post-phase measurement (2026-08-31, after Read-Identity)

```
discovered 5 (by-id 5, list 0), failed 154 (NO_BACKING_METHOD 154), list-refused 0,
skipped-by-parse 0, partial 15 (17 fields skipped), mis-bound 0
```

Forecast was 6 to 5 with `getCPESCustomerProduct` refused; that is exactly what happened. Verified by
generating both backends from a worktree at the pre-build commit and from the built tree and running
`diff -r`: across the serialized IR, the MCP manifest, the generated MCP tool sources, and the
OpenAPI document, the ONLY difference is the removal of `getCPESCustomerProduct` and its artifacts
(`GetCPESCustomerProductTool.java`, the manifest entry, the `/cPESCustomerProduct/{id}` path). Nothing
else moved.

The refusal is now reported as a near miss against condition 4, naming
`CustomerProductService#validateExistence`'s failure to prove a by-primary-key load, so the capability
is absent with a located reason rather than present and wrong.

## 6. What this app is now

A fourth named benchmark, and the one that owns two facts the other three do not:

- it is the only app that has produced a mis-bind, so it is the regression target for section 4.1.1;
- it is the largest SHAPE-category deferral set measured anywhere (201 of 219), so it is second
  evidence for AT-2 alongside pms.

Read its coverage the way the other ledgers are read: it shares a private foundation library with
erp-backend and pms-backend, so its idioms do not prove generality. What it proves is that the
by-primary-key discriminator has to be asked on every path, which is a structural claim and not an
idiom-bound one.

## 7. AT-2 (SHAPE) re-measurement (2026-09-01)

Attestation AT-2 built the SHAPE category, the one this app's deferral profile ranks first: 201 of its 219
deferred reads are SHAPE-category (115 projection collections, 86 multi-read assemblies, 15 opaque
wrappers). Re-measured after the build: discovered 5, failed 154, mis-bound 0, attested 0, and the
serialized IR, generated MCP output and generated OpenAPI document all BYTE-IDENTICAL to a worktree built
at the pre-build commit.

Byte-identical is the correct and expected result, and reading it as "AT-2 did nothing here" would be a
misreading. as-backend carries no `tibyan/attestations.json`, and it never will until a person who knows
the code writes one. AT-2 builds the mechanism that lets those 201 reads BE attested; it does not attest
them, and the tool authoring an assertion about code it does not know is the first thing section 4.15.9
forbids. The count that moves is `attested`, and it moves only when a named human moves it.

What this app can expect when someone does attest, stated honestly rather than as a ceiling: the 86
multi-read assemblies include the aggregate-enrichment by-id reads AT-2 covers directly, provided their
only inputs are the identifier and paging. Any read with a domain filter parameter needs the compound
category `both`, which is NOT built, so a large part of the 115 projection collections (the
`getAllXByFilters` family, which is most of them) stays refused after AT-2. The honest summary is that
AT-2 opens the by-id aggregates on this app and leaves the filtered surface for a later step.

## 8. EI-1 re-measurement (2026-09-01)

Step EI-1 built the input value constraint. Measured: discovered 5, failed 154, mis-bound 0, attested 0,
with serialized IR, generated MCP output and generated OpenAPI document all BYTE-IDENTICAL to a worktree
built at the pre-build commit, and an identical survey: as-backend produces no `enum-parsed-in-body` lines.

This app is unmoved by EI-1 for the same structural reason erp is, and the reason is worth stating rather
than leaving as a bare zero. No discovered capability can carry an enum input, because a by-id read takes
one identifier and a list-all read takes none. The constraint attaches only at attestation intake, and
as-backend has no record file. What EI-1 changes here is what would happen IF someone attested one of the
219 deferred reads whose filters are enum-typed: the closed set would now be proven and carried instead of
the entry refusing at the parameter gate.

Read alongside section 7: AT-2 opened the by-id aggregates on this app, EI-1 removes the enum-parameter
obstacle from the filtered ones, and what still blocks most of the filtered surface is the compound
category `both`, which is not built.

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

### AT-3 member C1 (2026-09-01)

Member C1, the security contract, was built the session after C2. Re-measured: BYTE-IDENTICAL across the
serialized IR, the generated MCP output and the generated OpenAPI document, verified by `diff -r` against a
worktree at the pre-build commit. This app carries no record file, so nothing could move.

C1's evidence lives on the private enterprise benchmark, not here. What this ledger records is the same
negative as the C2 row: settling a targeting model, adding a core contradiction guard, and completing an
epistemic class moved not one byte of any existing output.

### The compound category `both` (2026-09-01)

The compound category was built, closing the two-hole target: a filtered read whose element type structure
cannot tie, which before this could not be attested by either narrow category because condition 1 forbids
two entries against one declaration.

Re-measured: BYTE-IDENTICAL across the serialized IR, the generated MCP output and the generated OpenAPI
document, verified by `diff -r` against a worktree at the pre-build commit. This app carries no record
file, so nothing could move.

This is the category that matters most to this ledger's own numbers, and the reason is worth stating: the
earlier rows here noted that a read with a domain filter AND an unresolvable element type needed `both`,
and that `both` was not built. That caveat is now discharged. What those reads still need is a human to
write the entry.

### AT-4, the draft lifecycle (2026-09-01)

The draft lifecycle was built: an `ai-draft` entry is queued for human review, counted apart from attested
and from discovered, and ships nothing until a named person confirms it one entry at a time.

Re-measured: BYTE-IDENTICAL across the serialized IR, the generated MCP output, the generated OpenAPI
document AND the survey text, verified by `diff -r` against a worktree at the pre-build commit. This app
carries no record file, so no draft queue can appear.

The assist that would PRODUCE drafts is not built, and that is a stack decision rather than a phase one
(Volume 5, section 4.15.5.1). What this changes for an adopter here is only this: if someone drafts entries
for this app by any means, the tool will hold them in a review queue rather than shipping them, and the
confirmation that releases one is a named human act.

---

## 9. THE FIRST REAL ATTESTATION RUN (2026-09-04 to 2026-09-08)

Every attestation phase from AT-1 onward closed with the same open verification, and STATUS.md carried it
forward each time in nearly the same words: no real attesting human had run this flow, and for AT-2 it was
recorded as "the deciding one". THAT VERIFICATION IS NOW DISCHARGED. Between 2026-09-04 and 2026-09-08 the
maintainer adopted published Tibyaan 0.3.0 on this application, drafted twenty entries, read and confirmed
them one at a time, generated the MCP surface, and called the resulting tools through a real MCP client.

This section is written under rule 14: a session's report is not evidence, the repository is. Every claim
below carries where it was checked, in one of three classes:

- **[tibyaan]** read from this repository's source at the cited file and line;
- **[adopter]** read from the as-backend repository, at the cited commit or file;
- **[reported]** stated by the trial and NOT re-verified here, recorded as testimony rather than as fact.

### 9.1 The record as committed

**[adopter]** `tibyan/attestations.json` on branch `Ahmed-0000-tibyan-mcp-trial`:

| Field | Value |
|---|---|
| Entries | 15 |
| Categories | `both` 13, `shape` 2, `predicate` 0, `boundary` 0 |
| Origin | `ai-drafted-human-confirmed`, all 15 |
| assertedBy | Ahmed El-Seginy, all 15 |
| Date | 2026-09-04, all 15 |
| Fingerprint | present, all 15 |
| securityOwner, assertedAuthorities | absent; no boundary entry exists in this application |

The funnel, verified by reading the file at each commit: twenty entries were committed as drafts
(a7a0091037, all twenty `origin: ai-draft`), one was struck by hand as a byte-for-byte duplicate of another
module's tool before confirmation, nineteen were confirmed individually (97fbd23587), and four were removed
afterwards when intake refused them on collection-typed parameters. Fifteen shipped.

### 9.2 The forecast that held, and it was this ledger's own

Section 7, written after AT-2 and before any human had authored an entry here, predicted that AT-2 alone
would open this app's by-id aggregates and leave most of its filtered surface refused, because a filtered
read whose element type does not tie needs the compound category `both`. THIRTEEN OF THE FIFTEEN CONFIRMED
ENTRIES ARE `both`. The prediction was structural, made from the deferral profile, and it landed exactly.
Read together with section 8: the two entries that are plain `shape` are the two zero-argument lookups, which
is the only shape the narrow category was ever going to cover here.

### 9.3 What the run proves about the mechanism

- **The per-entry confirmation gate held under real pressure.** **[reported]** the attester asked to confirm
  the queue in one act and was refused; **[tibyaan]** there is no `--confirm-all` and no multi-target form to
  refuse with, by construction (`AttestCommand.java:38-49`). Nineteen confirmations, nineteen named acts.
- **Fingerprinting survived contact.** All fifteen carry a `sha256:` stamp **[adopter]**.
- **Read-Identity was re-confirmed in the field.** **[reported]** `getCPESCustomerProduct` disappeared on the
  0.2.0 to 0.3.0 upgrade and the adopter recognised it as the correct removal rather than a regression. That
  is section 4 of this ledger, arriving a second time as an adopter's own experience.
- **The attested tools invoke and return the right rows.** **[reported]** eleven of thirteen tools tested
  through a real MCP client returned exactly the predicted result; the sharpest case is the five order-reason
  tools, which read ONE table and differ only by a hardcoded constant, so nothing structural distinguishes
  them and only the human sentences do. All five matched, and were reproduced a second time from a tracked
  Liquibase migration rather than a one-off insert.

That last point is the one worth keeping. Five tools that structure cannot tell apart were told apart
correctly by an agent reading only the attested sentences. That is the entire thesis of the attestation
design, tested where it could actually fail, on the first try.

### 9.4 Six findings

**F1. The safe default made attestation unusable, and the workaround disables the control globally.**
ALL FIFTEEN attested capabilities compiled to `securityUnverifiable(conflicting-declarations)`, so under the
ratified G8 default `tibyan.attested.exposeSecurityUnverifiable=false` NONE of them register and the adopter
gets the five verified capabilities they already had. **[adopter]** commit a7a17e0c64 sets the flag true, in
a local-only profile, with a written decision owner and a warning not to copy it anywhere else. The cause is
structural and not specific to this app: the authority is declared on the controller
(`CityControllerImpl#_getAllCitiesByFilters` is `@Secured(SC_GET_ALL_CITIES)`), Tibyaan binds its tool to the
service method beneath that check, and **[tibyaan]** `ControllerSecurity` refuses to compile a contract when
any associated route is not a strict anchor or when one route is undeclared alongside a declared one
(`ControllerSecurity.java:158`, `:167`, `:193`). The refusal is correct. The CONSEQUENCE is not: the flag is
capability-wide, so opening the fifteen reads a person actually reviewed also opens every other unverifiable
capability in that service, now and in future. A control whose only usable setting is off is not a control.

**F2. The draft lifecycle cannot express a draft.** **[tibyaan]** intake checks `provenance.assertedBy` for
blankness at `AttestationIntake.java:195` and only recognises the `ai-draft` origin at `:206`, eleven lines
later. A draft has no human asserter, which is precisely what makes it a draft, so the check that runs first
refuses the entry the check below it exists to queue. **[adopter]** all twenty drafts at a7a0091037 carry the
literal string `UNCONFIRMED-DRAFT` in `assertedBy`. AT-4 was built so that a drafted claim could never be
mistaken for a person's claim, and the ordering forces a fake person into the field whose only job is to name
a real one.

**F3. Confirmation erases the drafter.** **[tibyaan]** `Provenance` carries a single name field
(`AttestationRecord.java:41`), and `--confirm --by` writes the CONFIRMER into `assertedBy`
(`AttestCommand.java:83`, and its own comment says so). After confirmation the record can no longer say who
drafted the words and who signed them. This is the same separation AT-3 guard 1 treats as load bearing for a
security decision, that signing is a different act from knowing the code, dropped for the documentation case.
**[adopter]** all fifteen final entries name the confirmer and no entry names a drafter.

**F4. A collection-typed parameter refuses the capability, and it refuses it AFTER a human has signed.**
**[tibyaan]** `JavaTypeMapper.map` returns empty for any type containing `<` or `[`
(`JavaTypeMapper.java:53-56`), so `List<Long> ids` refuses at STRUCTURAL_FRAME. **[adopter]** four entries
were confirmed by name and then deleted. The ordering is the worst available: `tibyan attest` stamped them
without complaint, a person read and signed four claims, and only the later scan reported that the target was
never attestable. The structural refusal is knowable at stamp time.

**F5. The runtime does not derive the MCP base URL from the servlet context path.** **[tibyaan]**
`TibyanDefaultPropertiesEnvironmentPostProcessor` supplies exactly two defaults, `spring.ai.mcp.server.name`
and `.version` (lines 73-74). A host app with a context path therefore advertises an SSE message endpoint
without it, and a client opens the stream and then POSTs into a 404. **[adopter]** every service needed a
hand-written `spring.ai.mcp.server.base-url` (`/pharmacy-mgt`, `/user-mgt`). This is the same shape as the
version defect fixed on 2026-09-03, in the same class, and the `addLast` precedence that makes that fix safe
makes this one safe too.

**F6. Connection health is not capability health.** **[reported]** two service crashes were reported as
connected by the client's own health check right up until a real tool call failed, and the gap between those
two facts is where a user-visible failure lived. Not a Tibyaan defect, but the generated surface is what a
client probes, so the honest liveness check is calling a known tool, and that belongs in
`docs/CONNECTING-AN-MCP-CLIENT.md`.

### 9.5 Testing status

| What | Status | Class |
|---|---|---|
| Human authored, read and confirmed attestations on a real app | DONE, first time in the project | [adopter] |
| Per-entry confirmation gate, no confirm-all | HELD | [tibyaan] + [reported] |
| Compound category `both` used in earnest | 13 of 15 entries | [adopter] |
| `shape` category used in earnest | 2 of 15 entries | [adopter] |
| `predicate` (AT-1) used | NEVER, on this app | [adopter] |
| `boundary` (AT-3) used, guard 1 exercised | NEVER; no boundary entry exists here | [adopter] |
| Attested tools invoked through a real MCP client | 11 of 13 returned the predicted result | [reported] |
| Five same-table tools distinguished only by attested sentences | ALL FIVE CORRECT | [reported] |
| Result reproduced from a tracked migration, not a one-off insert | YES | [reported] |
| Draft lifecycle used end to end (20 drafted, 19 confirmed) | DONE, with the F2 placeholder workaround | [adopter] |
| `tibyan scan` re-run against the final 15-entry record | RE-RUN 2026-09-20, section 9.8 | [tibyaan] + [adopter] |
| attested / mis-bound / attestation-drift / mis-constrained counts | MEASURED 2026-09-20: 15 / 0 / 0 / 0, section 9.8 | [tibyaan] + [adopter] |

The last two rows WERE the honest gap when this section was written, and section 9.8 closes them; the rest of
this paragraph is kept as the record of why the re-run was owed. The fifteen entries were fingerprinted on 2026-09-04 and the app has
changed since (four new Liquibase changesets and three master-changelog edits are uncommitted at the time of
writing), so the drift status of this record is not known and must not be assumed. All of the change is XML
rather than Java, so the fingerprints are EXPECTED to hold, but expected is not measured. Re-running the scan
against this branch is the first thing any follow-up session should do.

### 9.6 What is NOT a Tibyaan defect, recorded so it is not miscounted

Three of the trial's four worst incidents belong to the host application and are recorded here only so the
ledger is not read as a defect list against the tool. **[reported]** a build fix let 107 `@SpringBootTest`
classes run their `@Sql(clear-schema)` setup against the developer's own database and emptied several tables,
which is a missing test-schema isolation in that repository; a `converters.clear()` in the app's web config
dropped `StringHttpMessageConverter` and broke every JSON-RPC body before it reached a handler; and
`attested_citiesByFilters` fails at invoke on a pre-existing sort against a `title` column that does not
exist on `ph_city`, reproduced against the plain REST endpoint too.

The converter case is worth one line of documentation even so. It is not Tibyaan's bug, but it is Tibyaan's
symptom: an app that customises its message converters can break the generated MCP endpoint in a way whose
error message points nowhere near the cause.

### 9.7 What this app is now, restated

It was the app that produced the project's only mis-bind. It is now also the app that ran the project's first
real attestation, and the two facts are the same fact seen twice: this is the tree where the tool's claims
get tested by someone who knows the code well enough to catch it being wrong.

Its idiom caveat is unchanged and matters more here than anywhere, because F1 is an idiom finding. This app
declares its authorities on controllers and binds its tools to services, and it shares a private foundation
library with pms-backend and erp-backend. Whether controller-declared authority is general or a house style
is not settled by one app, and F1's ranking should be read with that in mind. What is NOT idiom-bound is F2,
F3, F4 and F5: those are defects in the tool's own record shape, refusal ordering, type mapping, and runtime
defaults, and they would reproduce on any application.

### 9.8 The owed re-scan (2026-09-20), closing the measurement gap

Section 9.5 left two rows open and named the re-scan as the first thing a follow-up session should do. It ran
on 2026-09-20. No tool code changed and no adopter code changed.

| What | Value | Class |
|---|---|---|
| Tool | CLI 0.4.0-SNAPSHOT, jar rebuilt from the tree at commit 657700e (no Java or POM change since 2e8ab9f) | [tibyaan] |
| Target | branch Ahmed-0000-tibyan-mcp-trial at commit 23db1101cd, working tree clean | [adopter] |
| Record | tibyan/attestations.json, 15 entries, both 13 and shape 2, all ai-drafted-human-confirmed | [adopter] |
| Entities | 159 | [tibyaan] |
| Discovered | 5 (by-id 5, list 0), the same 5 | [tibyaan] |
| Attested | 15, every entry carried, none refused, none stale | [tibyaan] |
| Failed | 154, all NO_BACKING_METHOD | [tibyaan] |
| Deferred list reads | 219, of which 86 are multi-read assemblies (E6a, section 4.11.7) | [tibyaan] |
| mis-bound | 0 | [tibyaan] |
| attestation-drift | 0 | [tibyaan] |
| mis-constrained | 0 | [tibyaan] |
| Security verdict on the 15 | all 15 print unverifiable (conflicting-declarations), so F1 is confirmed by the tool's own output rather than by testimony | [tibyaan] |

The Liquibase changesets that were uncommitted on 2026-09-08 are committed now (23db1101cd) and are XML, so
the fingerprints were expected to hold, and they did: expected is now measured.

ONE HONEST LIMIT. The Totals line prints mis-bound, attestation-drift and mis-constrained. It does NOT print
mis-safe, so mis-safe is not a number this scan reports. What can be said is narrower and true: read-safety
runs at intake on every entry, a READ_SAFETY failure refuses the entry, and zero of fifteen were refused.
That is "no entry failed the check", which is what the invariant rests on, and it is not the same sentence as
a printed mis-safe 0. Whether the survey should print the fourth invariant beside the other three is a small
diagnostic question, recorded here and not acted on.

### 9.9 Census of the 86 multi-read deferrals (2026-09-08, re-derived 2026-09-20)

THE QUESTION. After the run, the obvious objection to the whole attestation route was put directly: most of
the 86 multi-read deferrals are ONE idiom, rows from `selectAllByFilters` plus a total from
`countAllByFilters` over one repository with one filter, deferred only because section 4.11.7 counts the
count as a second access. If that idiom were safely provable, a discovery rule would recover most of the 86
with no human in the loop, and the coverage economics of this app would invert. The census was run to find
out, on 2026-09-08. It was never committed and lived only in a session, so under rule 14 it did not count. It
was RE-DERIVED from the 9.8 scan on 2026-09-20 by a script over the 86 target bodies, and the figures below
are the re-derived ones.

METHOD, stated so the numbers are not over-read. The 86 targets are taken from the scan's own deferral lines.
Each body is extracted from the file that actually declares the method (class names repeat across modules
here: `LookupServiceImpl` exists in four, and the first census mis-extracted 8 bodies for exactly that reason
before correcting it). Classification is LEXICAL: one pattern for a hardcoded predicate, one for an
ambient-context read, a count of distinct repository receivers. It is a census by pattern, not a proof, and
the survivors were therefore read by eye.

| Cut of the 86 | Count |
|---|---|
| select plus count idiom | 62 |
| of those, over a single repository receiver | 44 |
| select only | 15 |
| neither | 9 |
| carry a hardcoded predicate (`isDeleted(false)`, `isActive(true)`, a type constant, and so on) | 25 |
| read ambient request context, so the result depends on who is calling | 15 |
| zero-argument | 10 |
| survive every filter: zero-arg, select plus count, one repository, no hardcoded predicate, no ambient read | 6 |

Two figures differ from the 2026-09-08 session by small amounts (select only 15 against 14, ambient 15
against 13) because the patterns were rewritten rather than recovered; every other figure reproduced exactly,
including the 62, the 25 and the 6.

THE SIX, all `LookupServiceImpl` reads: `getAllConfigurationDomains`, `getAllDomains`, `getAllEvents`,
`getAllGovernorates`, `getAllSegments`, `getAllSuppliersLookups`. **[adopter]**

THE FINDING THE RE-DERIVATION ADDED, and it is the most important line in this section. Read by eye, the six
are not six. Two of them (`getAllSegments`, `getAllGovernorates`) build their filter with
`PaginationInfo.noPagination()` and genuinely return every row. The other FOUR build an empty filter,
`XSearchFilter.builder().build()`, and in this application an empty filter is not an unpaged one:
`SearchFilter` declares its `pagination` field with a builder default of `PaginationInfo.builder().build()`,
and `PaginationInfo` defaults `pageSize` to 25 (lib/sql-db-adapter, `SearchFilter.java:15-16` and
`PaginationInfo.java:17`). **[adopter]** So those four return AT MOST THE FIRST 25 ROWS, in unspecified order,
while their total is an unpaged count. A discovery rule that admitted them as list-all would describe a
25-row page as "every X". This is the same trap the attester caught by hand on
`attested_partiallyPackageReasonsLookup` (section 9.1) and that pms showed on `getPaymentsSummary`
(second-app-trial.md section 15), now found a third time, inside the set a lexical census had called clean.
THE HONEST SURVIVOR COUNT IS 2 OF 86, and it took reading a default on a foundation-library field, two files
away from the body, to see it.

THE CROSS-TABULATION. All 15 confirmed attestations target methods inside the 86. 9 of the 15 carry a
hardcoded predicate that exists in no signature and no type. 16 further predicate-bearing reads among the 86
are unattested. The survivors and the attested set are disjoint.

THE VERDICT, recorded as a decision input and not as a ratification. A list-plus-count discovery rule is NOT
a candidate. Its ceiling on this app is 2 reads against 159 entities, and the way the other 84 fail is the
project's worst failure mode rather than its mildest: the structure is provable and the MEANING is not, so a
loosened rule would ship confidently wrong descriptions, the mis-bind reproduced at the level of semantics,
25 times for hardcoded predicates, 15 times for caller-scoped results, and 4 more times for a silent page
cap. Section 4.11.7's deferral is doing its job. What this app's filtered surface needs is what section 7
forecast and section 9 measured: a human sentence per read, and the tool's effort is better spent making that
sentence cheap and safe to give (findings F1 to F4) than on admitting reads without one. The idiom caveat
applies as everywhere: the select-plus-count shape and the 25-row default both come from this author's
private foundation library, so the COUNTS are idiom-bound; the lesson, that a provable access pattern does
not carry its own semantics, is not.

## 10. SPIKE DL-0, draft and label (2026-09-23)

The first build of the v2 direction (Volume 13 section 3, "v2 direction"). Tool: CLI 0.4.0-SNAPSHOT at commit
fdd5ec3. Target: branch Ahmed-0000-tibyan-mcp-trial at 23db1101cd, working tree clean, the 15-entry record in
place. The drafts file was written to a scratch location, never into the adopter tree. **[tibyaan]** unless marked.

Q1, THE CENSUS. `tibyan draft` produced 990 candidates from 272 concrete service classes:

| Label | Count | Of which |
|---|---|---|
| VERIFIED | 5 | the same five as section 9.8: getSSConfig, getDepartment, getOrder, getWFRequest, getSyncedUser |
| ATTESTED | 15 | the fifteen confirmed entries, matched by target string |
| READ | 401 | 263 with every input mapped, 138 with at least one unmapped input, 94 zero-argument |
| WRITE | 204 | R1 write-set call on a repository field 159, setter on a loaded entity 35, R3 write-set-named call on another bean 9, L1/R9 1; no @Modifying and no mutating @Query hit |
| UNKNOWN | 365 | 362 return void with no visible mutation; 3 are static calls (ThreadContext.remove twice, Files.delete once) |

WRITE: 58 with every input mapped, 146 with an unmapped input. The unmapped input types among READ, most
frequent first: List<Long> 40, OrderDirections 33 (an in-tree enum; the draft mapper does not resolve enums,
attestation intake does), List<Integer> 26, WorkflowContext 20, RequestContextKeys 9. Names: 990 unique.

Q2, LABEL CORRECTNESS, the automatic half: every one of the 15 attested targets is present in the file and
labeled ATTESTED, none is labeled WRITE in either run, and with the record removed (a `git archive` copy of the
same commit without `tibyan/attestations.json`) all 15 label READ and the census reads VERIFIED 5, ATTESTED 0,
READ 416, WRITE 204, UNKNOWN 365. Every discovered binding labels VERIFIED. No spike-failure condition fired.
THE MAINTAINER'S READING SAMPLE OF READ AND WRITE ENTRIES IS OWED: it was not done in this session, so the
label correctness of the 401 READ entries beyond the 15 proven ones is UNMEASURED, and the known blind spot
below says some of them are wrong.

TWO AMENDMENTS MADE DURING THE SPIKE, recorded so the build and the design do not diverge silently:

1. THE VOID RULE. The first run labeled 763 candidates READ, of which 364 return `void`. A void method cannot be
   a read tool, and read-safety sees persistence only, so a void method with no visible mutation now labels
   UNKNOWN with the reason stated. That moved 362 entries out of READ; the two-entry difference from 364 is the
   two static-call UNKNOWNs that were already outside READ.
2. THE TARGET SPELLING. The first run wrote parameter types comma-separated without a space and matched 2 of
   the 15 attested targets by string, because the real record writes `(String, Integer)` with a space and
   intake accepts both. The draft target now uses the record's spelling, and 15 of 15 match by string.

Q3, SERVING AN APPROVED DRAFT: DEFERRED TO DL-1 BY NAME. Two reasons, both measured here rather than assumed:
the runtime binds only scalar, enum and java.time inputs (Volume 7 section 9), so 138 READ and 146 WRITE
drafts cannot be invoked today whatever serves them; and serving needs a manifest that carries the kind and
the label plus the write gate, which is the design the direction names as the phase after the spike, not a
spike question.

BLIND SPOTS CONFIRMED, carried from read-safety and stated in the labeler: a call into another bean or a
remote client whose name is not in the write set passes as READ; only one hop of private helpers is read; the
check returns its first reason only, so an UNKNOWN can hide a later write.

WHAT THE SPIKE ANSWERS FOR THE DIRECTION. On this app the draft-first surface is 990 candidates against the 20
the refusal-first tool serves today, and 263 READ candidates are invocable by the current runtime as they
stand, before any human writes a sentence. The labels held on every proven target, in both directions. What it
does NOT answer: whether the READ label is right in the maintainer's reading (the owed sample), and whether the
description sentence can be produced cheaply, which is the assist question the direction leaves to DL-1.

### 10.1 DL-1 built (2026-09-23): the re-measurement

Phase DL-1 (Volume 5 section 4.17, build record 4.17.13) was built the same day. The same scan against the same
adopter commit (23db1101cd), built from the DL-1 tree and diffed with `diff -r` against a worktree at the
pre-build commit 89b3441, is BYTE-IDENTICAL across the serialized IR, the generated MCP output and the generated
OpenAPI document. Totals unchanged: discovered 5, attested 15, failed 154, mis-bound 0, attestation-drift 0,
mis-constrained 0. **[tibyaan]** This is forecast, not a finding: the adopter tree carries no `tibyan/drafts.json`,
so nothing is reviewed and nothing new is served.

What changes for this app from here is a human act, not a tool run: the drafts file of section 10 (990 entries,
written to a scratch location, never into the adopter tree) becomes the record once it is committed into the
adopter repository, and every entry the maintainer approves there is admitted at the next scan as
`reviewed_<name>`. The first approvals are the owed reading sample of section 10. Two things to know before
approving on this app: the 138 READ drafts with an unmapped input include every `List<Long>` and `List<Integer>`
parameter, which intake refuses as INPUT_UNMAPPED until DL-2; and a reviewed capability behind a secured
controller is security-unverifiable, as all fifteen attested ones are, so it opens only by exact name in
`tibyan.security.exposeUnverifiable`.

### 10.2 The reading sample (2026-09-23): 3 of 30 READ labels hide a write

The owed reading sample of section 10, done as an AGENT READING, not the maintainer's: an evenly spaced sample
of 60 entries (30 READ, 20 WRITE, 7 void UNKNOWN, the 3 non-void UNKNOWN) was read against the adopter source
by independent readers that followed calls at least two hops, two readers per READ entry and one per other entry.
The two READ readers agreed on every entry's verdict, so no tie-break was needed. **[adopter]** for every file
reference below. The maintainer's spot check of the three failures is still owed and is the human half.

| Label | Sampled | Correct | Wrong | Tool-worthy |
|---|---|---|---|---|
| READ | 30 | 27 | 3, all SAFETY | 11 |
| WRITE | 20 | 20 | 0 | 16 |
| UNKNOWN | 10 | 9 | 1, coverage (a void validation that only reads) | 0 |

THE THREE FALSE READS, each a write or an external effect the label missed:

1. `FileServiceImpl#downloadFile(String)` publishes a JMS message on every call: `mqClientService.sendMessage`
   reaches `MQClientServiceImpl` and `jms.convertAndSend` to the file topic. No DB write, but an external effect.
2. `ProductAttributeIdServiceImpl#generateGlobalKeyAttributeId()` allocates the next value of a shared counter:
   `CounterService` to `CounterInternalServiceImpl`, which inserts or updates a `CounterInstance` through a
   repository `save`, three bean hops from the method.
3. `TransitionConditionsServiceImpl#getEligibleTransitions(WFTransition, WorkflowContext)` runs workflow conditions
   by reflection (`getBean` plus `Method.invoke`, chosen by database rows), and one wired condition sets fields on
   a managed `Order` inside the caller's transaction.

ONE CAUSE: every failure is a call into ANOTHER BEAN. Read-safety reads the method body, one hop of private
helpers and the repository declarations it calls; a publisher, a counter service or a reflective dispatcher
behind an injected field is invisible to it. This is the blind spot DL-0 recorded in section 10, now measured:
on this sample the READ label is wrong 10 percent of the time, and every wrong case is a safety error, not a
coverage miss. It is the finding that ranks next, ahead of every coverage item: Volume 5 section 4.17.14 designs
the fix.

WHAT THE SAMPLE ALSO SHOWS, recorded for ranking and not acted on:

- CANDIDATE HYGIENE. 19 of 30 READ, 4 of 20 WRITE and 10 of 10 UNKNOWN entries are not tool-worthy: MQ
  subscription hooks (`onEvents()` returning a constant), `@Service`-annotated repository implementations,
  workflow conditions and activities taking a `WorkflowContext`, lifecycle `init()` methods, ID generators, and
  cross-service proxies that duplicate a real service in another module. A reviewer can reject them, but 990
  entries is a long file; a noise filter is a coverage-of-attention item for later, ranked below the safety fix.
- PROXIES THAT BORROW A SERVICE CREDENTIAL. The Feign and REST-client proxy reads call another service with this
  service's own internal Basic credential, not the caller's token. Approving one exposes the remote read with the
  service's privileges; a reviewer should treat it as a security decision, the way a security-unverifiable
  capability is treated.
- HIDDEN SEMANTICS, again. The readers' descriptions name what no signature shows: `getAllSuppliersLookups` and
  `getProductCurrentStocks` return at most 25 rows with an unpaged total (the same trap as section 9.9),
  `getApprovedPharmacies` narrows by the caller's role, and `getAllScopes` ignores its `noPagination` flag.

PREPARED, NOT APPROVED: the 11 entries the readers confirmed as pure reads with a servable signature now carry the
readers' sentence as `description` with `descriptionOrigin: ai-draft` in the adopter's uncommitted
`tibyan/drafts.json`, every one still `pending`. Nothing ships until the maintainer approves an entry by name;
the survey still reads reviewed 0.

### 10.3 DL-1.1 built (2026-09-23): the three false READs are caught

The bean-call closure (Volume 5 section 4.17.14, build record 4.17.15) re-labeled the same 990 candidates:
READ 401 to 271, WRITE 204 to 337, UNKNOWN 365 to 362. `downloadFile` and `generateGlobalKeyAttributeId` are
WRITE with the chain named; `getEligibleTransitions` is UNKNOWN (reflection). The 20 audited WRITEs hold; 22 of the
27 audit-confirmed pure reads stay READ and 5 are UNKNOWN (one ambiguous implementation, three abstract config
calls, one `RestClient`). 214 WRITEs come from a message publisher behind a bean, mostly void event handlers DL-1
had called UNKNOWN. IR, MCP and OpenAPI output unchanged **[tibyaan]**; the drafts file re-drafted through the merge,
243 entries carry `previousLabel`, all pending, the 11 prepared descriptions intact.

### 10.4 DL-1.2 built (2026-09-24): every audited pure read is READ again

The closed-set amendment (Volume 5 section 4.17.16, build record 4.17.17) re-labeled the 990 candidates: READ 271
to 346, WRITE 337 to 343, UNKNOWN 362 to 281. All 27 audit-confirmed pure reads are READ, 20 of 20 audited
WRITEs hold, the three false READs stay caught. C6 fell from 123 to the forecast 3. C2 fell to 67, not the
forecast 19: 55 are same-arity overloads on beans the closure could not reach before, the next candidate by
count. IR, MCP and OpenAPI unchanged **[tibyaan]**. The drafts file was re-drafted through the merge; all entries
pending, the 11 prepared descriptions intact.

### 10.5 DL-1.3 built (2026-09-24): 42 of the 55 overload cases decided

Bodiless accessors and overload disambiguation (Volume 5 section 4.17.18, build record 4.17.19): READ 346 to 358,
WRITE 343 to 372, UNKNOWN 281 to 240. The 55 "no single method" cases are gone; 13 return as a named untyped
argument (a method-call result), the case the rule excludes. Audited sets unchanged. IR, MCP and OpenAPI unchanged
**[tibyaan]**; the drafts file re-drafted through the merge, all pending, the 11 prepared descriptions intact.

### 10.6 The first real approvals (2026-09-24): reviewed 9, refused 0

THE ACT. The maintainer, Ahmed El-Seginy, approved 9 of the 11 prepared entries of section 10.2 by name, in one
edit of `as-backend/tibyan/drafts.json` **[adopter]**, and declined the two Feign proxies (`getOfferDetails`,
`getInternalUserById`) because they call another service with this service's own credential. `descriptionOrigin`
stays `ai-draft` on all nine: the words are the readers' and the approval is the maintainer's, which is exactly the
separation the record was built to keep.

THE INTAKE. `tibyan scan` on the same adopter commit (23db1101cd) admitted all nine and refused none: every target
resolved, every fingerprint held, no label drifted, none was superseded, every input bound (including the in-tree
enum `OrderDirections` on `getAllSubProductsByFilters`, carried as a closed set `[ASC, DESC]` through the EI-1
path), every review complete. Totals: `discovered 5, attested 15, reviewed 9, ..., mis-bound 0, attestation-drift 0,
review-drift 0, mis-constrained 0`. Drafts pending 981. **[tibyaan]**

THE SURFACES. Both backends generated the nine as `reviewed_<name>` with the class as the first sentence. SEVEN of
the nine are `securityUnverifiable(conflicting-declarations)`, the same verdict as all fifteen attested
capabilities (section 9.4, F1): their fronting controllers declare authority the tool cannot compile. Under the
ratified defaults those seven do NOT register until each exact name is listed in
`tibyan.security.exposeUnverifiable`; the two local proxy reads (`getApprovedPharmacies`,
`getProductCurrentStocks`) carry no declared contract and register by default. So the first real use of the
security list is nine minus two names, per hosting service, which is what Volume 7 section 10 was designed for.

NOT DONE: starting the adopter's services with the manifest and the list, and calling a reviewed tool through a
real MCP client. That needs the adopter's databases running and is the next thing to do on this app.

### 10.7 The first reviewed capability executed end to end (2026-09-24)

THE RUN. **[adopter]** `zpi-pharmacy-mgt` at commit 23db1101cd, repackaged as a boot jar against Tibyaan
0.4.0-SNAPSHOT (the adopter's `package` profile deactivates the `activeByDefault` module list, so the
working invocation is `mvn -o -Ppackage,default -pl z-pharma-inv/ms/pharmacy/zpi-pharmacy-mgt package`), started
with `spring.profiles.active=local` against a throwaway local MySQL 8.4 with `spring.jpa.hibernate.ddl-auto=update`
(70 tables created, all empty), the existing Artemis container and the remote Redis. Two command-line overrides,
neither written to any adopter file: `tibyan.security.exposeUnverifiable` naming the seven unverifiable reviewed
tools of section 10.6, and the adopter's `zag.lib.security.public-paths` extended by `/sse,/mcp/message`, because
the MCP endpoint sits behind the adopter's JWT filter and answers 403 without a bearer token (the section 9 run
had the same shape). The client is a 50-line program on the MCP Java SDK 0.18.3 (`HttpClientSseClientTransport`,
`McpSyncClient`), the same SDK the server uses.

THE SURFACE. **[reported]** `tools/list` returned 29 tools: 5 verified, 15 attested, 9 reviewed. All nine reviewed
names registered: the seven because they were named, the two local proxies by default. The startup honesty line
fired as designed: 14 attested tools "registered ONLY because tibyan.attested.exposeSecurityUnverifiable=true;
nobody named them", which is F1 of section 9.4 still open on this app.

THE CALLS. **[reported]** eleven calls against nine tools:

| Tool, arguments | Result |
|---|---|
| `reviewed_LocalPharmacyProxyService_getApprovedPharmacies {}` | `isError=false`, `{"total":0,"list":[]}` |
| `reviewed_LocalPharmacyProxyService_getApprovedPharmacies {pharmacyId=1}` | `isError=false`, `{"total":0,"list":[]}` |
| `attested_cancellationReasons {}` (control) | `isError=false`, `{"total":0,"list":[]}` |
| `reviewed_CartServiceImpl_getExpectedDeliveryTime {cartId=1}` | `isError=true`, `failed to invoke binding 'CartServiceImpl#getExpectedDeliveryTime'` |
| `reviewed_LookupServiceImpl_getAllSuppliersLookups {}` | `isError=true`, `no method 'getAllSuppliersLookups' taking 0 argument(s) on zag.zpi.pharmacy.core.service.LookupServiceImpl$$SpringCGLIB$$0` |
| the other six reviewed tools, any arguments | `isError=true`, `no bean found for binding hint '<Class>'` |

`getApprovedPharmacies` is THE FIRST REVIEWED CAPABILITY EXECUTED END TO END on a real application: the adopter's
log shows `PharmacyServiceImpl.getAllApprovedPharmacies` entered with the reviewed argument, the repository's
`selectAllByFilters` and `countAllByFilters` run against MySQL, and the empty result set serialized back through
the MCP transport. The description the maintainer approved says it "narrows to the caller's role" and the call ran
with the adopter's own `no token put in the Context`, so the narrowing did not apply: the anonymous caller saw the
unnarrowed (empty) set, which is the exposure the tool's security verdict exists to name, here on one of the two
tools that carry NO declared contract and therefore register by default. `getExpectedDeliveryTime` reached the
bound method (the adopter's aspect logged `Starts with Input: [1]` and the cart select ran) and failed inside it
on the empty table; the measurement proves the path up to the method, not the method. Every empty result is a
property of the throwaway database, not of the tool: this run proves transport, registration, the write gate's
read-only surface, the security list, bean resolution, argument coercion and invocation. It proves nothing about
data.

THE FINDINGS, numbered on from section 9.4.

**F7. The binding is a simple class name, and the runtime resolves it by simple name.** **[tibyaan]** The
manifest carries `binding: "LookupServiceImpl#getAllSuppliersLookups"`; the reviewed target in the drafts file is
`zag.service.stock.core.service.LookupServiceImpl#getAllSuppliersLookups()`. `SpringExecutionAdapter.resolveBean`
(lines 84 to 97) tries the default bean name `lookupServiceImpl`, then the first bean whose SIMPLE class name
matches. **[adopter]** the reactor declares four classes named `LookupServiceImpl` (`zag.service.activity.core`,
`zag.service.stock.core`, `zag.service.system.settings.core`, `zag.zpi.pharmacy.core`). The pharmacy service
hosts the fourth, not the reviewed one, and the runtime resolved to it. The call failed only because that class
has no zero-argument `getAllSuppliersLookups`; had it had one, the reviewed tool would have invoked a method the
reviewer never read. That is a mis-bind at the moment of action, on the exact shape Entity Identity (Volume 5
section 4.12) closed for entities on this same benchmark class: simple-name keying collapses legitimately reused
names across bounded contexts. The verified and attested bindings have the same form (`binding` is
`<SimpleName>#<method>` on every tier), so the exposure is runtime-wide and not a reviewed-tier property. The
drafts file already carries the qualified target; the manifest drops it. This is the finding of the run, and the
next design: carry the declaring class qualified through the manifest and have the runtime verify that the
resolved bean IS that class (or refuse loudly), never resolve by simple name. It is a binding-integrity item, and
under the standing rule that a mis-bind outranks every coverage item, it outranks DL-2 and candidate hygiene.
PENDING RATIFICATION; nothing was changed in this session.

**F8. An invocation failure hides its cause.** **[tibyaan]** `SpringExecutionAdapter.invoke` (line 80) wraps
`InvocationTargetException` in `IllegalStateException("failed to invoke binding '...'", e)`, the tool callback
lets it propagate, and the client text carries only the outer message; the adopter's log has no exception line
at all for the cart call. A reviewer diagnosing a reviewed tool has neither the business exception nor its
message. Diagnostic-only: surface the cause's class and message in the returned error and log it at WARN.

**F9. Registration is manifest-wide, hosting is per service.** **[adopter]** six of the nine reviewed beans
live in services this one does not host (`RoleServiceImpl` and `ScopeServiceImpl` in user-core, hosted by
zpi-user-mgt and zpi-backend-mgt; `CurrentStockServiceImpl`, `SubProductServiceImpl` and the reviewed
`LookupServiceImpl` in stock-core and product-core, hosted by zpi-customer-product-mgt and zpi-backend-mgt;
`PharmacyUserServiceImpl` in zpi-user-core, hosted by zpi-user-mgt; `LocalStockProxyService` is on the pharmacy
classpath but not a bean here, the adopter loads proxies per service). **[tibyaan]** the runtime registers all
nine and discovers the absence only at call time. Section 9 recorded the same fact for attested tools (13 of 15
hosted) without a number. A startup check that a binding's bean exists would move this to startup honesty, next
to the X1 to X5 checks; it is a candidate, not ratified.

**F10. The order of checks at invocation.** **[tibyaan]** bean resolution runs before argument coercion, so the
illegal enum probe (`orderDir=UP` on `getAllSubProductsByFilters`) answered `no bean found` and EI-R's refusal was
not reached on this service. A fact, not a defect; the EI-R refusal is covered by its own fixtures.

WHAT THIS CHANGES. The reviewed tier is now measured on the same footing as the attested one: a named human's
decision, carried through scan, manifest, gate and runtime, executed against the real application through a real
client, with the tool's own honesty lines firing where they should. It also found the first runtime defect the
reviewed tier's shape exposes, F7, which section 9's thirteen hosted attested tools could not have found because
none of their simple names is reused in this reactor.

NEXT: ratify, amend or refuse the F7 design (qualified binding, verified at resolution). Real approvals continue
in parallel; they are safe on this service today only for bindings whose simple name is unique in the reactor,
which the maintainer can check with the drafts file's `target` field.

### 10.8 Binding Identity built (2026-09-24): the section 10.7 call repeated

THE BUILD. Volume 7 section 12, build record 12.10. Every binding on every tier is now
`<canonical qualified class name>#<method>`, and the runtime resolves it by class identity only.

THE RE-SCAN. **[tibyaan]** against a worktree at c3f4109: `Totals: discovered 5, attested 15, reviewed 9,
failed 154, partial 15, mis-bound 0, attestation-drift 0, review-drift 0, mis-constrained 0` on both sides;
29 of 29 manifest bindings changed and no other line of the IR, the MCP manifest or the OpenAPI document
moved. The draft census is `990 (VERIFIED 5, ATTESTED 15, READ 358, WRITE 372, UNKNOWN 240)` on both sides.

THE DRAFTS FILE. **[adopter]** `tibyan draft` re-qualified all 990 entries of `tibyan/drafts.json` in place;
the nine approved entries kept name, review, reviewer, description and approved fingerprint; zero diff lines
outside `binding`. The attestation record needed no edit. The file is still uncommitted in the adopter repo.

THE CALL. **[reported]** the pharmacy service repackaged on this build, started as in section 10.7, 29 tools:

| Tool, arguments | Section 10.7 | Now |
|---|---|---|
| `reviewed_LocalPharmacyProxyService_getApprovedPharmacies {}` | `{"total":0,"list":[]}` | `{"total":0,"list":[]}` |
| `reviewed_LookupServiceImpl_getAllSuppliersLookups {}` | `no method 'getAllSuppliersLookups' taking 0 argument(s) on zag.zpi.pharmacy.core.service.LookupServiceImpl$$SpringCGLIB$$0` (the WRONG class, F7) | `no bean of class 'zag.service.stock.core.service.LookupServiceImpl' is hosted by this application (binding '...', capability 'reviewed_LookupServiceImpl_getAllSuppliersLookups')` |
| `reviewed_CartServiceImpl_getExpectedDeliveryTime {cartId=1}` | `failed to invoke binding ...` (cause hidden, F8) | `failed to invoke binding '...': zag.lib.common.model.exception.BusinessException: [CART-0001] [CART_NOT_FOUND]: cart not found 1`, and the same at WARN in the log |
| `reviewed_RoleServiceImpl_getAllRoles {}` | `no bean found for binding hint 'RoleServiceImpl'` | `no bean of class 'zag.service.user.core.service.RoleServiceImpl' is hosted by this application` (F9, unchanged by design) |
| `attested_cancellationReasons {}` (control) | `{"total":0,"list":[]}` | `{"total":0,"list":[]}` |

F7 and F8 are closed on the app that found them. F9 stays a candidate: the message now names the class and the
capability, and a startup check would move it earlier, but that is a registration policy with its own
ratification. NEXT: the maintainer's call on F9, and real approvals continue; a reviewed binding is now safe on
any hosting service, whether or not its simple name is reused in the reactor.

### 10.9 DL-2a built (2026-09-24): collection inputs bind, 473 to 532

THE RE-CENSUS. **[tibyaan]** `tibyan draft` on the same commit, against a worktree at c6bf4ed: the census is
unchanged (990: VERIFIED 5, ATTESTED 15, READ 358, WRITE 372, UNKNOWN 240); 95 inputs gained a type and
`"multiple": true`; entries the tool can bind on every input went 473 to 532 (READ 261 to 313, WRITE 78 to 82,
UNKNOWN 114 to 117). The forecast said 533; the one entry is `List<Object>`, which the forecast script counted
as a collection of a primitive and the tool rightly does not. Discovered, attested and reviewed output on this
app is byte-identical: none of the nine approved entries has a collection input.

WHAT A REVIEWER SEES NOW. **[adopter]** an entry such as `LookupServiceImpl_getAllCities` reads
`{"name": "governorateIds", "javaType": "List<Integer>", "type": "INTEGER", "multiple": true}`; a `List` of a
DTO (for example `List<RecordAttribute>`, 8 inputs) reads `"multiple": true` with a null type, which is DL-2b's
by name.

NOT DONE: the real approval. Two READ candidates are hosted by the pharmacy service this ledger runs:
`zag.zpi.pharmacy.core.service.LookupServiceImpl#getAllCities(List<Integer> governorateIds)` and
`#getAllRegions(List<Integer> cityIds)`, both returning `LookupResultSet`. Approving one by name, with a read
description, and calling it through the MCP client of section 10.7 with a JSON array is the last clause of
DL-2a's exit criterion, and it is the maintainer's act.

THE APPROVAL AND THE CALL (later on 2026-09-24). **[adopter]** the maintainer approved
`LookupServiceImpl_getAllCities` by name in `tibyan/drafts.json` (reviewer Ahmed El-Seginy, origin ai-draft; the
description, read from the code by the tool's operator, says the governorate list narrows the cities, an
absent or empty list returns every city, and no soft-delete filter is applied). **[tibyaan]** `tibyan scan`:
`reviewed 10, refused 0, review-drift 0`; the capability is `securityUnverifiable(conflicting-declarations)`
like the other pharmacy reads, so it was named in `tibyan.security.exposeUnverifiable`. **[reported]** the
pharmacy service registered 30 tools and the MCP client called it four ways:

| Arguments | Result |
|---|---|
| `{"governorateIds": [1, 2]}` | `isError=false`, `{"total":0,"list":[]}`; the log shows the city select and count with `governorate_id in (...)` |
| `{"governorateIds": []}` | `isError=false`, `{"total":0,"list":[]}`; no governorate condition in the query, as the description says |
| `{"governorateIds": ["x"]}` | `isError=true`, `input 'governorateIds[0]' has value 'x', which is not a valid 'Integer'` |
| `{"governorateIds": 7}` | `isError=true`, `input 'governorateIds' is multiple and must be a JSON array, but the value was Integer` |

THE DEFECT THE CALL FOUND. The first attempt answered `input 'governorateIds' is multiple but the bound
parameter 'java.util.List' names no single element class`: the adapter read the generic signature from the
CGLIB subclass Spring wraps the bean in, whose override carries none. No fixture had a proxied bean with a
collection parameter. Fixed the same session (the method is now resolved on the user class; fixture CI-8) and
the call repeated with the results above. That is the second defect found by a real call in one day, after F7,
and the reason the exit criterion insists on one.

DL-2a's exit criterion is met in full. NEXT: DL-2b (shapes), in its own session; F9 still awaits the
maintainer.

### 10.10 DL-2b built (2026-09-24): structured inputs bind, 532 to 778

THE RE-CENSUS. **[tibyaan]** `tibyan draft` against a worktree at d45a131: the census unchanged (990: 5 / 15 /
358 / 372 / 240); entries the tool binds on every input 532 to 778 (READ 313 to 326, WRITE 82 to 252, UNKNOWN
117 to 180). The forecast said 852; the 74 are 59 `WorkflowContext` inputs whose every field is an entity or
a `Map` (refused `SHAPE_EMPTY`, exactly as D8 and R3 require) and 15 generated DTOs whose only field is a list
of a polymorphic interface (R2 never guesses an implementation). The tool read 129 distinct shapes, 119
complete and 10 partial, every skipped field disclosed with its reason. Discovered, attested and reviewed
output on this app is byte-identical in the IR and the MCP manifest; the OpenAPI document moved on one
operation, `reviewed_LookupServiceImpl_getAllCities` from GET to POST, the D6 correction of the DL-2a
rendering (Volume 5 section 4.19.8).

WHAT A REVIEWER SEES NOW. **[adopter]** the file re-run in place: 297 entries show their object inline, for
example `CityServiceImpl_createCity` reads `cityDTO: SHAPE_REF(CityDTO)` with fields `titleAr: STRING,
titleEn: STRING, governorateId: INTEGER` and no skipped field; 120 entries name why a class is not a shape
(47 an entity, 77 empty); the ten approvals and their fingerprints are unchanged, `reviewed 10,
review-drift 0`.

NOT DONE: the real approval. The pharmacy service hosts one READ with a shape input and it is partial
(`CurrentCartServiceImpl#getCartVTO(List<RecordAttribute>)`, `attributeValue` an `Object`), so the honest
first shape call on this app is a WRITE, named in `tibyan.writes.allow` and in the security list, against the
throwaway database. Fully mapped candidates the pharmacy service hosts: `CityServiceImpl#createCity(CityDTO)`,
`GovernorateServiceImpl#createGovernorate(GovernorateDTO)`,
`CancellationReasonServiceImpl#createCancellationReason(CancellationReasonDTO)`, among 54. Approving one by
name, with a write description, and calling it with a JSON object through the MCP client of section 10.7 is
the last clause of DL-2b's exit criterion, and the first reviewed WRITE ever executed on a real app. It is
the maintainer's act.

### 10.11 The first reviewed WRITE on a real app (2026-09-24): createGovernorate

THE APPROVAL. **[adopter]** the maintainer's instruction to finish the recorded next action was applied to
the first candidate section 10.10 named that needs no prerequisite row:
`GovernorateServiceImpl_createGovernorate` (reviewer Ahmed El-Seginy, origin ai-draft; the description, read
from the code, names the two duplicate-title refusals, the CREATE_GOVERNORATE event and the DTO's declared
constraints the tool does not enforce). **[tibyaan]** `tibyan scan`: `reviewed 11, refused 0, review-drift 0`;
the capability is `kind WRITE`, its shape `GovernorateDTO{titleAr, titleEn}` complete, and it carries NO
declared security contract, so the write gate alone decides it.

THE GATE, FIRST. **[reported]** the pharmacy service started without `tibyan.writes.allow`: 30 tools, and the
startup line `excluded MCP tool 'reviewed_GovernorateServiceImpl_createGovernorate' (write-not-allowed)`.
The tool was not in `tools/list`.

THE CALL. **[reported]** started again with
`tibyan.writes.allow=reviewed_GovernorateServiceImpl_createGovernorate`: 31 tools. `ph_governorate` held 0 rows.

| Arguments | Result |
|---|---|
| `{"titleEn": "Cairo", "titleAr": "Al Qahira"}` | `{"id": 1}`; row 1 in `ph_governorate`; `Event CREATE_GOVERNORATE is published on Topic zag.zpi.pharmacy.governorate` in the log |
| the same again | `BusinessException: [GOVERNORATE-0001] [GOVERNORATE_ENGLISH_TITLE_ALREADY_EXISTS]`, the app's own refusal, surfaced by name (F8) |
| `{"titleEn": "Giza", "code": "GZ"}` | refused before the call: `carries 'code', which is not a field of GovernorateDTO (fields: titleAr, titleEn); the adapter never drops a property silently` |
| `7` | refused: `is a GovernorateDTO object and must be a JSON object, but the value was Integer` |
| `{"titleEn": "Giza"}` | `{"id": 2}`; row 2 with `title_ar` NULL, the optional field never set (D4) |

DL-2b's exit criterion is met in full, and this is the first reviewed WRITE ever executed on a real
application: gated by name, bound from a JSON object through the shape the reviewer approved, refused on
anything outside it, and reaching the real repository and the real message queue. Two rows exist in the
throwaway database and nowhere else.

NEXT: F9 is designed pending ratification (Volume 7 section 14): the pharmacy service still registers seven
reviewed tools whose beans it does not host and finds out at call time.

### 10.12 F9 built (2026-09-24): the pharmacy service registers what it can execute

**[reported]** the pharmacy service repackaged on the F9 runtime, same lists as section 10.11: the registered
line went from 31 tools to 20, and one new line named the 11 the service cannot execute, each with its class:
three verified reads of other services (`getWFRequest`, `getSSConfig`, `getDepartment`), one attested
(`attested_currentStockAutoComplete`) and the seven reviewed tools of sections 10.7 and 10.9
(`CurrentStockServiceImpl`, `LocalStockProxyService`, the stock `LookupServiceImpl`, `PharmacyUserServiceImpl`,
`RoleServiceImpl`, `ScopeServiceImpl`, `SubProductServiceImpl`). `tools/list` returned 20;
`reviewed_LocalPharmacyProxyService_getApprovedPharmacies` still answered. The 13-tool WARN of section 10.6
stands: those attested tools register only because the global switch is on in the local profile.

What this closes: F9. An agent reading this service's tool list no longer learns, one failed call at a time,
which tools belong to another service. What it does not close: the adopter still ships one reactor-wide
manifest into every service; that is the adopter's practice and F9 makes it honest rather than forbidding it.

The reviewed tier on this app now stands at 11 approvals (10 reads, 1 write), all executed or refused as their
descriptions say, on a throwaway database.

### 10.13 Describe built (2026-09-24): the prompts, dry

**[tibyaan]** `tibyan describe . --dry-run --limit 2 --only CityServiceImpl_createCity,GovernorateServiceImpl_getAllGovernoratesByFilters`
on this app printed two prompts built from the real slices (75 members across them: each method, its
imports, the repository declarations it calls, the return type declaration) and wrote nothing; the drafts
file is byte-equal before and after. Without a key, `tibyan describe .` exited 3 naming `ANTHROPIC_API_KEY`
and wrote nothing. The 981 pending entries are describable; the first real run, over the 54 fully mapped write
candidates of section 10.10, waits for a key on the machine that runs it, and its sentences are read and
approved or corrected by the maintainer, by name.
