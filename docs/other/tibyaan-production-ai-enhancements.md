# Enhancing Tibyaan for Production AI: Agents, Workflows, RAG, and Beyond

---

## Where you are now

Tibyaan v1 reads a Spring Boot app, builds a Capability IR of entities and read-only capabilities, and generates an MCP server and OpenAPI doc that run inside the real application. The one-dependency promise works. The trust guarantee holds: `mis-bound: 0` across two real benchmark apps. The artifact is on Maven Central.

The honest limit: v1 discovers 25 of 25 capabilities on a structurally simple system, and 2 of 43 on a mature layered system. The structural ceiling is real and measured. The tool is correct, but narrow.

Below is the path from this foundation to a production-grade AI platform that supports agents, workflows, RAG, and the full AI ecosystem.

---

## 1. The immediate unlock: Attestation (cross the structural ceiling)

This is the single highest-leverage phase. Without it, Tibyaan cannot deliver enough coverage to be useful on real systems, and everything below this point is academic.

### What it is

A human asserts what the tool cannot structurally prove: a filter's meaning, an opaque wrapper's returned entity, the intent of a custom query, the business purpose of a capability. The tool records it as `HUMAN_ATTESTED`, explicitly distinct from `TOOL_VERIFIED`, and carries it through every surface: the IR, the MCP manifest, the OpenAPI doc, and every future output.

This is not a violation of "never guesses." The tool did not guess. A person asserted. The label is the guarantee.

### Why it unlocks everything else

| Without Attestation | With Attestation |
|---|---|
| PMS discovers 2 of 43 capabilities | PMS discovers 30+ of 43 (every read a human names) |
| Filtered reads are a permanent refusal | Filtered reads are attested and exposed as tools |
| Wrapped reads are invisible | Wrapped reads are made visible, then attested |
| Custom `@Query` methods are refused | Custom queries are attested with their semantics |
| No write capabilities (no policy gate yet, but the IR space is empty) | Write capabilities can be attested (policy-gated, off by default) |
| The IR is a structural skeleton | The IR becomes a semantic description of what the business does |

### How to build it

1. A new `Provenance` enum on capabilities: `TOOL_VERIFIED`, `HUMAN_ATTESTED`.
2. An attestation file format (YAML or JSON) where a human maps entity names to capabilities, describes parameters, and attests to semantics the tool cannot prove.
3. The provider reads the attestation file and merges attested capabilities into the IR, tagged with provenance.
4. The MCP backend surfaces the provenance in the tool description: "Attested: this tool reads products filtered by category. Verification: the binding to `ProductService#findByCategory` is structurally proven; the parameter meaning 'category' is human-attested."
5. The OpenAPI backend does the same.
6. The survey report (`tibyan scan --report`) distinguishes verified from attested in its ledger.

### Evidence trigger (this is the gate, per the project's discipline)

Build this only when a real adopter hits the ceiling and needs it. Publishing v1 and getting external signal is how that evidence arrives. If you are that adopter, this is your next build.

---

## 2. Write and action capabilities (let agents DO things)

An AI agent that can only read is a search engine. An AI agent that can act is a worker. Read-only is the right MVP posture, but production AI needs writes.

### What changes

The IR already has `CapabilityKind.WRITE` and `CapabilityKind.ACTION`. They exist but are never produced. The runtime already handles any capability kind through the same `ExecutionAdapter`. The gap is in the provider and in the governance layer.

### What to build

1. **Write discovery in the provider.** Detect `@Service` methods that take an entity or a DTO, return void or the saved entity, and delegate to `repository.save()` or equivalent. Same structural proof standard as reads: verify the save call, the parameter forwarding, and the absence of side effects the tool cannot see. Fail loud on ambiguity.

2. **A policy gate.** Before any write tool ships, it MUST be gated. The runtime needs a policy layer (the designed RFC-0002):
   - A `CapabilityPolicy` interface: `boolean allow(String capability, Map<String, Object> args, Authentication auth)`.
   - A default implementation that denies all writes and actions.
   - An opt-in configuration where the adopter explicitly enables specific write capabilities.
   - The runtime checks the policy before every non-read invocation.
   - The MCP tool description carries a `"writable": true` flag so the AI agent knows.

3. **The security model.** The legacy app's existing authentication and authorization MUST be respected. The runtime already runs in the app's own context, so Spring Security filters apply. But the tool layer needs to surface auth requirements to the AI agent: "this tool requires the caller to be authenticated as a user with the ADMIN role."

### The order

Attestation first (to get coverage), writes second (to get action), policy simultaneously (to keep it safe). Never ship writes without policy.

---

## 3. Workflows (let agents run multi-step business processes)

A single `getProduct` call is not a business operation. "Create an order, reserve inventory, charge the customer, confirm" is. Workflows chain capabilities into business processes.

### What changes

The IR gains a `Workflow` element (RFC-0001). A workflow is a named sequence of capability invocations with inputs flowing from one step to the next. It is framework-neutral, just like the rest of the IR.

### What to build

1. **IR growth: the Workflow element.**
   ```
   Workflow {
     name: "fulfillOrder"
     steps: [
       { capability: "getOrder", outputBinding: "order" },
       { capability: "reserveInventory", inputMapping: { "orderId": "$order.id" } },
       { capability: "chargeCustomer", inputMapping: { "orderId": "$order.id" } },
       { capability: "confirmOrder", inputMapping: { "orderId": "$order.id" } }
     ]
   }
   ```

2. **A workflow runtime.** The runtime needs a workflow engine that can:
   - Validate input/output type compatibility between steps.
   - Execute steps sequentially (MVP) or with parallelism (later).
   - Handle failures: rollback, retry, or halt.
   - Expose each workflow as a single MCP tool so the AI agent sees one "fulfillOrder" action, not four calls.

3. **Workflow attestation.** Workflows cannot be structurally discovered from source alone, at least not in the general case, and certainly not from the single-method analysis the provider does. They are prime attestation material. A human describes the workflow in the attestation file and the tool carries it.

4. **Workflow documentation.** If the tool is going to expose `fulfillOrder` to an AI agent, the agent needs to understand what it does and when to use it. The attestation file carries a plain-language description that becomes the tool description.

### The strategic insight

Workflows make Tibyaan the entry point for AI automation of the business. Instead of an agent trying to figure out "I need to call getOrder, then check inventory, then charge the customer," the agent sees one tool: `fulfillOrder`. The business logic lives in the model, not in the agent's prompt.

---

## 4. RAG integration (let AI understand what the system CAN do)

Right now, an AI agent connected to a Tibyaan-generated MCP server sees a list of tools: `getProduct`, `listProducts`. It does not know what a Product is, why it matters, what its fields mean, or how it relates to other entities. This is the RAG gap.

### The opportunity

The Capability IR is already a structured knowledge graph of the business system. It contains entities, fields, types, relationships, and capabilities. It is missing domain meaning: what an entity represents in business terms, what a field means, what a capability is for. Adding that meaning turns the IR from a structural model into a semantic one, which is exactly what RAG needs.

### What to build

1. **Enrich the IR with domain knowledge.** Add `description` fields to entities, capabilities, and fields. These are human-attested, not tool-verified. They carry plain-language business meaning: "A Product is an item sold to customers. The price field is in the customer's local currency. The getProduct capability looks up a single product by its identifier."

2. **Generate a semantic index.** At build time, produce a `tibyan/semantic-index.json` that contains embeddings-ready text for every entity and capability:
   ```json
   {
     "entities": [
       {
         "name": "Product",
         "description": "An item sold to customers. Products have a name, price, and belong to a category.",
         "relations": ["belongs to Category", "appears in Orders"],
         "capabilities": ["getProduct", "listProducts", "searchProducts"]
       }
     ]
   }
   ```

3. **Expose the index through MCP.** Add a `semanticSearch` tool to the MCP server that takes a natural-language query and returns matching entities and capabilities. The runtime embeds the index using a lightweight embedding model (or calls an external embedding service) and runs cosine similarity.

4. **Or, generate for external RAG systems.** The semantic index can be imported into any RAG pipeline (LangChain, LlamaIndex, your own vector store). Tibyaan does not need to own the RAG runtime. It owns the structured knowledge that feeds RAG. This is the better architecture: Tibyaan as the knowledge source, not the knowledge server.

### The strategic insight

RAG over business capabilities is a moat. Anyone can build a vector store over a database schema by reading column names. But knowing that "findByCategory" means "filter products by their product category, not by their sales category," or that "status" on a Lease is an enum with values that mean specific contractual states, requires understanding the business logic. That understanding lives in the source code and in the minds of the people who built the system. Tibyaan already reads the source. Attestation lets it carry the meaning. Together they produce a semantic model that no schema scraper can replicate.

---

## 5. Multi-agent orchestration (many agents, many MCP servers, one platform)

A real business runs multiple systems. An inventory system, a billing system, a CRM, a shipping system. A production AI deployment will have multiple AI agents, each with different capabilities and permissions, operating across multiple systems. Tibyaan's architecture is designed for this, but the multi-agent layer is not built.

### What to build

1. **An aggregate MCP server.** A single Tibyaan deployment can generate MCP servers for multiple legacy apps. An aggregate server fronts them all, presenting a unified set of tools to the AI agent with source attribution: `getProduct [inventory-system]`, `getCustomer [crm-system]`.

2. **Agent-specific capability sets.** The policy layer (RFC-0002) controls which capabilities each agent can access. A billing agent sees billing tools. A shipping agent sees shipping tools. A supervisor agent sees everything. The same IR feeds different capability subsets for different agents.

3. **Cross-system workflows.** The workflow engine (from section 3) chains capabilities across systems: `getCustomer [CRM] -> createInvoice [Billing] -> reserveInventory [Inventory] -> createShipment [Shipping]`. The agent sees one tool. Tibyaan orchestrates across four systems.

4. **An agent registry.** A runtime component that knows which agents exist, what capabilities they have, and what they are currently doing. This enables:
   - Agent-to-agent handoff: "I found the customer, now I delegate to the billing agent."
   - Conflict detection: two agents trying to modify the same order.
   - Audit trail: every capability invocation is logged with the calling agent, timestamp, arguments, and result.

### The architectural principle

Tibyaan is NOT an agent framework. It does not build agents, schedule them, or manage their state. It is the layer that makes business systems callable by agents. The agent framework (LangChain, AutoGen, your own orchestrator) owns the agent logic. Tibyaan owns the business surface they operate on.

---

## 6. The IR as the RAG knowledge graph (the semantic business capability model)

This is the culmination of sections 1 through 5. The Capability IR starts as a structural model (entities, fields, types, capabilities) and grows into a Semantic Business Capability Model, enriched through attestation, that represents the full business domain.

### What the enriched IR contains

| Element | What it captures | How it feeds AI |
|---|---|---|
| Entities | Business objects with names, descriptions, fields, types, and domain meaning | The AI knows what a Product IS, not just that it has id, name, price |
| Capabilities | What the system can do, with input/output types, binding, and business intent | The AI knows what getProduct DOES, not just what parameters it takes |
| Relations | How entities relate (Product belongs to Category, Order contains LineItems) | The AI understands the business domain structure |
| Workflows | Multi-step business processes (fulfillOrder, onboardCustomer) | The AI sees business operations, not individual API calls |
| Policies | Who can do what, under what conditions | The AI operates within governance boundaries |
| Annotations | Domain labels: enum values, view types, partiality disclosures | The AI understands value constraints and data shapes |
| Descriptions | Human-attested plain-language explanations of everything | This is the RAG corpus: every entity, capability, and workflow has a description an embedding model can index |

### How this changes the product

Today: Tibyaan is an MCP generator. You point it at a Spring app, it gives you MCP tools for the structurally provable reads.

Tomorrow: Tibyaan is a business knowledge platform. You point it at your systems, enrich the model through attestation, and it produces:

- **MCP tools** for AI agents to call the system.
- **A semantic index** for RAG systems to search and understand the business.
- **An OpenAPI document** for any HTTP client.
- **Typed SDKs** for programmatic access.
- **Workflow definitions** for multi-step automation.
- **A policy layer** for governed AI access.
- **Documentation** derived from the same model.

One model. Many surfaces. The model is the asset.

---

## 7. Production hardening (the things that make it run in anger)

### Observability

Every tool invocation should produce structured logs, metrics, and traces:
- Log: which tool, which arguments, which binding, how long it took, what it returned.
- Metrics: invocation count, error rate, latency percentiles, per-tool and per-agent.
- Traces: correlate tool invocations with the calling agent's trace context so a multi-step workflow is traceable end to end.
- The runtime already runs inside the Spring Boot app, so it inherits Micrometer, actuator, and any existing observability stack. The gap is that tool calls are not instrumented.

### Security

- Transport: the MCP endpoint (`/sse`) must support TLS in production. The host app handles this; Tibyaan inherits it. The documentation must state this explicitly.
- Authentication: the MCP endpoint must participate in the host app's auth. The runtime runs in-context, so Spring Security filters apply. But MCP clients need to know how to authenticate, and the generated tools need to carry auth metadata.
- Authorization: the policy layer (RFC-0002) is the gate. Until it exists, writes and actions are off.
- Secrets: the binding string `ProductService#findById` is a reflection target, not a credential. This is safe by design. But if attestation lets a human name arbitrary methods, the attestation file becomes a security surface and needs its own access control.

### Reliability

- The runtime should fail fast on startup if the manifest is missing or corrupt.
- The execution adapter should handle bean lifecycle: if a bean is not yet initialized, wait rather than fail.
- Tool registration should be atomic: all tools registered or none, so a partial manifest does not expose half the capabilities.
- The MCP server should survive a failed tool call without crashing: catch, log, return an error to the client.

### Scaling

- The MCP server shares the host app's thread pool and connection pool. For a read-heavy agent workload, this matters. The runtime should support configurable timeouts and concurrency limits per tool.
- The manifest is loaded once at startup and cached. Tool resolution and bean resolution should also be cached to avoid reflective lookups on every call.

---

## 8. Ecosystem growth (the platform, not the pipeline)

Today Tibyaan is a pipeline: one provider, one backend, one runtime, wired manually. The architecture is a platform but the implementation is not.

### What to build

1. **True plugin discovery.** An SPI layer where a plugin declares "I am a CapabilityProvider for Spring source" and the pipeline discovers it via `ServiceLoader` or Spring's `spring.factories` mechanism. Today, the `pom.xml` of the CLI hardcodes which provider to use.

2. **Second provider.** A database provider (RFC-0004) that reads a database schema and produces an IR of tables, columns, and inferred entity relationships. Not as rich as source analysis, but it works when source is unavailable and it proves the IR accepts a non-code source.

3. **Third backend.** An SDK backend that generates typed client libraries (Java, TypeScript, Python) from the same IR. Proves the model feeds code generation, not just configuration.

4. **Second runtime.** A Quarkus or Micronaut runtime. Proves the runtime abstraction is real. The IR and backends stay unchanged; only the ExecutionAdapter and auto-configuration differ.

5. **A plugin marketplace.** GitHub-based, with a registry of known plugins. Not a store, not a payment system. Just a place where a stranger can find "Spring Boot 3.x Provider" or "MCP Backend for Claude Desktop."

### The strategic arc

Pipeline → Platform → Ecosystem. You are at Pipeline. The path to Platform is SPI + second provider + third backend. The path to Ecosystem is marketplace + community. Each step compounds: more providers make the IR richer, richer IR makes backends more valuable, more backends attract more providers.

---

## 9. The build sequence (what to do, in what order)

This is the sequence, ordered by leverage. Each phase builds on the last.

| # | Phase | What it unlocks | Prerequisite |
|---|---|---|---|
| 1 | **Attestation** | Coverage on real systems. Without this, nothing else matters. | External adopter signal |
| 2 | **IR enrichment (descriptions, domain knowledge)** | RAG-ready semantic model. Agents understand the business. | Attestation (descriptions are attested) |
| 3 | **Write + policy** | Agents can act, not just read. Policy gates keep it safe. | Attestation (writes are attested), IR enrichment (writes need descriptions) |
| 4 | **RAG integration** | Semantic index generation. External RAG import. | IR enrichment (the index needs descriptions) |
| 5 | **Workflows** | Multi-step business processes. Agents see operations, not API calls. | Attestation (workflows are attested), write + policy (workflows include writes) |
| 6 | **Multi-agent orchestration** | Aggregate MCP server. Cross-system workflows. Agent registry. | Workflows, policy |
| 7 | **Production hardening** | Observability, security hardening, reliability, caching. | Everything above (hardening applies to all of it) |
| 8 | **Ecosystem growth** | SPI, second provider, third backend, second runtime, marketplace. | Production hardening (ecosystem without reliability is noise) |

### The rule

Do not build phase N+1 until phase N delivers measurable value on a real system. The project's "evidence before elaboration" discipline is the right one and it must hold through the entire sequence.

---

## 10. The one-page vision

If you are a CTO and you adopt Tibyaan today, you get MCP tools for the structurally provable reads in your Spring apps. Honest coverage, zero wrong bindings.

If you follow the sequence above, Tibyaan becomes:

> **A single model of what your business systems can do, enriched with domain meaning, that generates every AI surface you need.** MCP tools for agents. A semantic index for RAG. Workflow definitions for automation. Typed APIs for clients. A policy layer for governance. All from the systems you already run, without rewriting them.

The Capability IR is the moat. The IR is what agents, RAG systems, workflow engines, and client SDKs all read. The IR is what outlives any AI protocol or framework. Build the IR. Enrich it. Everything else is an output.
