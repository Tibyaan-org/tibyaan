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
| `tibyan scan` re-run against the final 15-entry record | NOT RE-RUN IN THIS SESSION | open |
| attested / mis-bound / attestation-drift / mis-safe / mis-constrained counts | UNMEASURED since 2026-09-04 | open |

The last two rows are the honest gap. The fifteen entries were fingerprinted on 2026-09-04 and the app has
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
