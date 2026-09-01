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
