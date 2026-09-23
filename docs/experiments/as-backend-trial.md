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
