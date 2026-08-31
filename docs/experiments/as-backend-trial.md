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
