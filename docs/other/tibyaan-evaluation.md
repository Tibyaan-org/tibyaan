# Evaluation: The Tibyaan Idea and Its Current Implementation

---

## Part 1: The Idea

### Is the problem real?

Yes, and it is everywhere. Most valuable business software is old. It runs operations, holds real data, and the business depends on it. But it was built years before MCP, before typed APIs, before anyone imagined an AI agent would need to call `getProduct` and get a real result. The gap between these systems and the AI era is real and growing. Rewriting them is the standard answer, and it is slow, expensive, and risky.

### Is the solution the right shape?

The core insight is two sentences: treat an existing system as raw material, and build a neutral model between the source and every output. This is the right architecture for the problem. The same insight powers compilers (source → IR → multiple targets) and it powers Tibyaan.

What makes it more than an obvious compiler analogy are three decisions that give it strategic depth:

1. **The Capability IR is framework-neutral by design.** It knows nothing about Spring, MCP, or any particular framework. This means the model outlives any single framework or protocol. When MCP evolves or a new protocol emerges, the model stays the same and a new backend is added. When a new framework is targeted, a new provider is added. The IR is the asset.

2. **The trust guarantee: never guess.** Tibyaan only exposes a capability when it can prove, structurally from the source, exactly which real method backs it. When it cannot prove that, it refuses and tells you plainly why. This is the right principle for a tool that puts real methods in front of AI agents. A wrong binding is worse than no binding at all.

3. **Single-dependency adoption.** The runtime is a Spring Boot starter that auto-configures. The build plugin generates the manifest at compile time onto the classpath. The adoptable application adds one dependency to its `pom.xml` and nothing else. This is the right adoption model.

### Where does the idea have tension?

The trust guarantee and broad coverage are in direct tension. The stricter the proof standard, the fewer capabilities are discovered. The project has chosen the strict standard, which is defensible for production systems, but it means the product's usefulness is directly bounded by how structurally discoverable the target system's code is.

The project acknowledges this tension honestly. It calls it "the structural ceiling" and has designed (but not built) an Attestation phase where humans can explicitly attest to capabilities the provider cannot prove. This is the right escape hatch. Without it, the product has a hard ceiling on value delivery.

The strategic bet is that structural discovery gets you far enough to be useful, and attestation fills the rest. The question is whether that bet holds for enough real systems to build a sustainable product.

---

## Part 2: The Implementation

### What was built well

**The core and IR.** `tibyan-core` is genuinely zero-dependency. The IR is 11 clean records and enums with immutability, defensive copying, and deterministic ordering. The sealed type system (8 primitives + entity references) is exactly the right size. Validation collects all errors in one pass rather than failing on the first. The code is clean, well-documented, and easy to read. This is the caliber of design that ages well.

**The provider.** The Spring provider is the most sophisticated piece. It does not rely on naming conventions. It reads method bodies structurally to verify that a service method actually calls `findById` on a Spring Data repository, that the id parameter is forwarded verbatim, and that a view method's mapping step takes the loaded entity as its sole argument. The three reading agents (by-id direct, DTO-aware view, list-all) are independent and composable. The test suite covers 7 classes with dozens of fixtures testing edge cases across association mapping, enum resolution, floating-point types, DTO matching, list reads, resilience, and partial-model integrity. Golden-file tests validate end-to-end output against the real example app.

**The runtime and binding.** The binding mechanism is clean. An opaque `Bean#method` string flows from provider to IR to manifest to runtime. The `SpringExecutionAdapter` resolves it to a live Spring bean via the ApplicationContext, coerces JSON types to the right Java types, and invokes via reflection. The one-dependency promise is kept. The end-to-end integration test (`EndToEndMcpIT`) connects a genuine MCP client, calls `getProduct(5)`, and asserts the real product comes back. This is the proof the entire architecture depends on, and it passes.

**The discipline.** Every hard rule from AGENTS.md is followed. Core has zero dependencies. No framework concept leaks into the IR. Providers depend only on plugin-api + core + their analysis library. Backends depend only on plugin-api + core. The runtime owns the execution adapter. Dependencies point inward. Package root is `com.tibyaan`. Output is deterministic. Tests confirm it.

**The published artifact.** Version 0.1.0 is on Maven Central. The CLI produces a reproducible survey against any Java source tree. The Maven plugin generates at build time. The documentation is thorough (17 volumes, with ADRs and status tracking). Two real-world third-party benchmarks produced `mis-bound: 0` on both.

### Where the implementation is limited

**Coverage is highly idiom-dependent.** The bottom line:

| Benchmark | Entities | Discovered | Rate | System style |
|-----------|----------|------------|------|--------------|
| ERP backend | ~25 | 25 | ~100% | Entities returned from services; by-PK repos |
| PMS backend | 43 | 2 | ~5% | DTOs returned from services; custom repositories; unparseable modules |

On a system whose services return entities and whose repositories are standard Spring Data, Tibyaan shines. On a mature, layered system with DTOs, custom QueryBuilders, and multi-module complexity, it discovers almost nothing. Both numbers are honest, and the project does not hide the 2-of-43 result. But the honest truth is that if you are a CTO with a PMS-style system, Tibyaan v1 will show you almost nothing useful.

**The structural ceiling is real and low.** The following are all refused today:
- Filtered reads (`findByCategory`, `search`, any parameter that is not the primary key)
- Wrapped reads (custom paged result-set wrappers, `*ResultSet` types)
- Custom `@Query` methods (even if they are functionally by-primary-key)
- Writes and actions of any kind (policy-gated, not built)
- Any service method that loads an entity but transforms it through more than one mapping step
- Any method body shape beyond the three DTO patterns recognized by the matcher

These are not oversights. They are deliberate. Each requires either deeper structural analysis (which hits complexity limits quickly) or human knowledge (which the Attestation phase is designed for but not yet built).

**The provider is single-framework and single-version.** Only Spring Boot 3.x source is understood. Spring Boot 2.x, Quarkus, Micronaut, .NET, PHP: nothing. This is appropriate for a v1, but the promised multi-provider architecture is direction, not built.

**Tolerance has real gaps.** The provider resolves types by simple name only, without import resolution. Two classes named `Product` in different packages would collide. Fields with library types, byte arrays, and `@Embeddable` types are silently skipped with a note. These are reasonable MVP tradeoffs, but they contribute to the coverage gap.

**The runtime is reflection-only.** Private, protected, and package-private methods are not found. Overloaded methods are matched exactly by parameter type, which could fail if the provider's recorded types do not match precisely at runtime. No return-type unwrapping for page envelopes. These are edge cases today but become mainstream problems as scope expands.

**The OpenAPI backend exists but is shallow.** It generates an OpenAPI 3.0 document from the IR, but the IR carries no documentation, no descriptions, and no domain semantics. The output is structurally valid but semantically thin. This is a consequence of the MVP's entity-and-read-only IR scope, not a backend bug.

### What is missing that the architecture says should exist

The architecture describes a platform with many providers feeding one IR and many backends consuming it. What exists today is a single provider (Spring source) feeding two backends (MCP, OpenAPI) with a single runtime (Spring Boot). This is a valid v1, but it is a pipeline, not yet a platform.

The plugin system is architectural, not marketplace. There is no SPI loading, no plugin discovery, no version negotiation between plugins. The interface contracts exist (`CapabilityProvider`, `Backend`, `ExecutionAdapter`) and are clean, but plugins are wired manually by the pipeline and CLI. This is appropriate for v1 but should not be confused with an ecosystem.

The Attestation phase, which would unlock the structural ceiling by allowing humans to explicitly attest to capabilities, is designed but not started. Until it exists, the product's value proposition is limited to structurally simple systems.

---

## Part 3: Verdict

### On the idea

The idea is genuinely good. The architecture is sound. The trust guarantee is the right principle for the problem domain. The strategic bet on a neutral IR as the moat is well-reasoned. The market position (brownfield, shared-model, many-outputs) is genuinely underexplored and defensible.

The product is honest about its limits. The README tells you what to expect before you run it. The "What you will actually see" section warns that a real layered application will yield few discoveries. The `2-of-43` result is published. This honesty is uncommon and valuable.

### On the implementation

The implementation is well-executed for what it tries to do. The code is clean, well-tested, and architecturally disciplined. The one-dependency promise is kept. The end-to-end proof works. The artifact is published. The benchmarks are honest.

But the implementation has not yet crossed the value threshold for most real adopters. The structural ceiling means that the tool discovers a lot on simple systems (where modernization is less painful anyway) and very little on complex ones (where modernization is most needed). The ERP result (25 of 25) is the best case. The PMS result (2 of 43) is the honest norm for mature, layered systems. Until the Attestation phase or deeper structural analysis bridges this gap, Tibyaan is a well-built tool whose reach is narrower than its promise.

The recommendation is not to add more structural extraction rules. Each new rule adds complexity for diminishing returns. The next step is to build the Attestation phase: let humans mark what the provider cannot prove, record it as attested (not verified), and surface it through the same generation pipeline. That is the gateway between a tool that works for 5% of a real system and one that works for 50%+.

The foundation is strong. The core, the IR, the provider, the runtime, and the CLI are all well-built and correct. The path forward is clear. What remains is to make it useful for the systems that actually need it.
