# The adoption prompt: attest every capability, then ship the chatbot

This file is a **prompt**, not a tutorial. Hand the whole thing to an AI coding agent that has a
terminal and write access to your production application's repository. It takes the agent from a
plain Spring Boot system to a running, human-attested MCP surface and a modern chat assistant on top
of it, and it ends by making the agent report back with the feature and a verifiable record of what
it did.

**How to use it**

1. Fill in the placeholders in the INPUTS block below. Nothing else needs editing.
2. Paste the whole file (from `## THE PROMPT` down) into the agent, or tell the agent to read this
   file and follow it.
3. Stay available. The workflow stops and waits for you at exactly two points, by design: the plan
   approval at the end of Phase 3, and the per-entry confirmation in Phase 7. Those two stops are
   the whole trust model. An agent that does not stop there has broken the workflow, not sped it up.

Reference material the agent may need, all in this repository: [docs/COMMANDS.md](COMMANDS.md) for
every command, [docs/CLI.md](CLI.md) for the CLI in depth, [docs/MULTI-MODULE.md](MULTI-MODULE.md)
for reactor builds, [docs/SECURITY.md](SECURITY.md) before anything is exposed, and Volume 5,
sections 4.15 and 4.16 for the ratified rules behind every refusal quoted here.

---

## THE PROMPT

### INPUTS (fill these in before running)

| Placeholder | Meaning | Your value |
|---|---|---|
| `<APP_ROOT>` | Absolute path to the production application's source root (the reactor root for a multi-module build) | |
| `<APP_NAME>` | The application's name, used in report titles | |
| `<TIBYAN_VERSION>` | The Tibyaan version to adopt. Use `0.3.0` from Maven Central unless you have a reason not to | `0.3.0` |
| `<YOUR_NAME>` | The human who will own every attestation, exactly as it should appear in `assertedBy` | |
| `<SECURITY_OWNER>` | The human who owns security decisions, for boundary entries only. May be the same person, but is named separately on purpose | |
| `<CHAT_STACK>` | The stack for the chat assistant: `typescript` (recommended) or `java` | `typescript` |
| `<TODAY>` | Today's date, `yyyy-mm-dd` | |

---

### ROLE

You are adopting Tibyaan in a real production Spring Boot application, `<APP_NAME>`, at
`<APP_ROOT>`. Your job has three parts, in this order and no other:

1. Put the application on Tibyaan `<TIBYAN_VERSION>` so its build produces an MCP manifest and its
   runtime serves it.
2. Bring every read the tool refuses, but a human can honestly describe, onto the surface through
   the attestation workflow, up to and including the point where a human confirms each one.
3. Build a modern chat assistant that talks to that MCP surface, and that shows a user which of its
   answers rest on proven structure and which rest on a person's word.

Then report, with evidence.

### WHAT TIBYAAN IS, IN ONE PARAGRAPH, SO YOU DO NOT MISUSE IT

Tibyaan reads a legacy business system's source, proves what it can about it, and generates an
AI-operable layer (an MCP server, an OpenAPI document) without editing the original system. It never
guesses. When it cannot prove a binding is right, it refuses and says why, because a false refusal
is a coverage miss and a false pass is a safety breach. Attestation is the one door through that
wall: a **human** asserts the meaning the structure cannot carry, the tool keeps proving everything
around that assertion, and every surface labels the result as human-attested rather than
tool-verified. You are operating that door. You are not allowed to walk through it yourself.

### NON-NEGOTIABLE RULES

1. **You never author a human claim.** Every entry you write carries `"origin": "ai-draft"`. You
   never write `"human-authored"`, and you never write `<YOUR_NAME>` into `assertedBy` on an entry
   you drafted. A draft ships nothing: no capability, no manifest entry, no OpenAPI path, no tool.
   That is the point.
2. **You never batch-confirm.** Confirmation is one entry, one named person, one command. There is
   deliberately no confirm-all in the CLI or in the API beneath it. Do not write a loop, a script,
   or a `for` over targets that calls `--confirm`. If a human tells you to confirm ten drafts, ask
   them to name them one at a time, or run the ten commands only after they have said, per entry,
   that they have read it.
3. **You never modify the application's business source to make a capability appear.** Tibyaan reads
   the system; it does not rewrite it. Editing a service method to fit a discovery rule turns a
   measurement into a self-fulfilling prophecy. The only files you may add or edit in the
   application are: `pom.xml` (dependency and plugin coordinates), `tibyan/attestations.json`, the
   chat assistant's own new module, configuration, and documentation.
4. **Read-only stays read-only.** Never attest a write or an action. That is a standing policy gate,
   not an oversight. If a target mutates, the answer is "refused", and you say so in the report.
5. **You never invent a fingerprint.** Fingerprints come only from `tibyan attest`. A hand-written
   one is a lie the tool will catch, and catching it is the feature.
6. **A refusal is data, not an obstacle.** When the tool refuses something, the first question is
   always "is this refusal correct?" Sometimes the honest fix is in the application's source, not in
   an attestation. The section "WHEN NOT TO ATTEST" below is binding.
7. **Never report a step done that you have not verified against the repository.** A build is done
   when the tree proves it: a command's real output, a real commit hash, a real reactor result. Your
   final report ends with that evidence.
8. **House style:** no em dashes anywhere, in code, docs, commit messages, or output. Use commas or
   colons.
9. **Do not expose anything to a network without asking.** The generated server executes real
   methods against real data and ships no authentication of its own. Read
   [docs/SECURITY.md](SECURITY.md) and keep it on localhost until a human says otherwise.
10. **Stop and ask when a rule does not decide a case.** Do not fall back on a convention. The tool
    behaves that way, and so do you.

---

## PHASE 0. Ground truth

Before you change anything, establish what is actually there. Do not trust this prompt over the
repository.

```bash
java -version
mvn -v
cd <APP_ROOT> && git status --short && git log --oneline -5
```

Get the CLI, and prove it runs:

```bash
curl -O https://repo1.maven.org/maven2/io/github/tibyaan-org/tibyan-cli/<TIBYAN_VERSION>/tibyan-cli-<TIBYAN_VERSION>-jar-with-dependencies.jar
java -jar tibyan-cli-<TIBYAN_VERSION>-jar-with-dependencies.jar version
```

The version line it prints is the version you are working with. If it disagrees with
`<TIBYAN_VERSION>`, stop and say so. For the rest of this prompt, `$TIBYAN` is that jar's path.

**Exit criterion:** `tibyan version` prints, and you have recorded the application's current commit.

---

## PHASE 1. The baseline survey

This is the measurement everything later is compared against. Take it before you change a single
line, and keep the output.

```bash
java -jar $TIBYAN scan <APP_ROOT> --report baseline-report.md
java -jar $TIBYAN scan <APP_ROOT> --out baseline.ir.json
```

Record from the totals line, verbatim:

- `discovered N (by-id A, list B)`
- `failed` and its causes
- `list-refused`, `skipped-by-parse`, `partial`
- `mis-bound 0`

Read `baseline-report.md` properly. It contains the operable subset with its bindings, the refusals
grouped with located reasons, and the single most common blocker. That last line usually tells you
where most of the value is.

**Why this matters:** every later claim you make ("attestation added 14 tools") is only true against
a recorded before. Without this file you have an anecdote.

**Exit criterion:** the baseline report and IR exist, and the totals are written into your working
notes.

---

## PHASE 2. Put the application on Tibyaan

Two coordinates, both at the same version. Keep the runtime and the manifest that generated it at
the same version: they are not guaranteed compatible across a minor bump before 1.0.

In `<APP_ROOT>/pom.xml`:

```xml
<dependency>
  <groupId>io.github.tibyaan-org</groupId>
  <artifactId>tibyan-runtime-spring</artifactId>
  <version><TIBYAN_VERSION></version>
</dependency>
```

```xml
<plugin>
  <groupId>io.github.tibyaan-org</groupId>
  <artifactId>tibyan-maven-plugin</artifactId>
  <version><TIBYAN_VERSION></version>
  <executions>
    <execution>
      <goals><goal>generate</goal></goals>
    </execution>
  </executions>
</plugin>
```

**Multi-module reactor:** `sourceRoot` defaults to the module the plugin runs in, which is almost
never what you want. Point it at the reactor root so one manifest covers every module, and put the
plugin on the module that also carries the runtime dependency. Read
[docs/MULTI-MODULE.md](MULTI-MODULE.md) and follow it rather than guessing.

Then:

```bash
cd <APP_ROOT> && mvn package
ls target/classes/tibyan/capabilities.json
```

Run the application and confirm the endpoint is live:

```bash
curl -N http://localhost:8080/sse
```

**If the build warns that zero capabilities were discovered,** that is not a failure of the adoption
step, it is the structural ceiling, and Phase 3 is the answer to it. Do not "fix" it by loosening
anything.

**Exit criterion:** `target/classes/tibyan/capabilities.json` exists, the app boots, `/sse` responds,
and the tools listed there match the baseline survey's discovered set.

---

## PHASE 3. Build the worklist, and get it approved

Now read the refusals. For each refused read in `baseline-report.md`, decide one of four things, and
write the decision down:

| Decision | When |
|---|---|
| **Attest it** | A human can honestly state what it selects or returns, and the refusal is a genuine limit of structural proof |
| **Fix the source** | The refusal is pointing at a real defect or an accidental shape in the application |
| **Leave it refused** | Nobody can honestly describe it, it mutates, or it is not worth exposing to an assistant |
| **Out of scope** | It is a write, an action, or an admin operation. Refused by policy, permanently |

### Choosing the category

There are four, and the category is not a style choice: it names which hole in the proof a human is
filling. The tool checks that the category matches the holes the target actually has.

| Category | The hole it fills | Required extra fields | Use when |
|---|---|---|---|
| `predicate` | What the read **selects**. Structure ties the entity locally but cannot say what the filter means | none | A filtered or custom-query read that still touches an in-tree repository, so the entity tie is proven and only the meaning is missing |
| `shape` | What the read **returns**. Structure cannot resolve the element type to a nameable entity | `subjectEntity` | A read that returns a DTO, a projection, a view, or an opaque wrapper, while still having a provable local repository tie |
| `both` | Both holes at once, in **one** entry with **one** minted name | `subjectEntity` | A filtered read whose element type is also unresolvable. Never write two entries against one declaration: that is refused |
| `boundary` | A **remote delegation**, including read-only-ness itself, the one property the tool cannot check for that shape | `subjectEntity`, `securityOwner`, `readOnlyAffirmed: true`, optionally `assertedAuthorities` | The read delegates to something outside the tree (a client, a remote service) so there is no local persistence tie at all |

Two things the categories deliberately refuse, so do not attempt them:

- A `boundary` entry over a target that **does** have a local tie. That target is a `shape`, and the
  tool will say so. The two categories partition the space on purpose.
- A security contract (`assertedAuthorities`) over a capability the tool already **verified**. That
  case has a source fix, and an assertion would paper over it.

### WHEN NOT TO ATTEST

This is the part an eager agent gets wrong. Attestation is not a way to raise a coverage number.

- **Do not attest around a mis-bind.** If a read-by-id capability disappeared after upgrading, that
  is the by-primary-key proof correctly refusing a binding that filtered by a foreign key and
  returned the first hit. The previous binding was not safe. Fix or accept, do not attest over it.
- **Do not attest what you cannot read.** If you cannot follow the method body and its repository
  calls well enough to state, in one sentence, what comes back and what was excluded, you have no
  claim to draft. Say so in the worklist and move on.
- **Do not attest to hide a paging surprise.** If a read silently truncates at a default page size,
  the honest move is to say that in the sentence, in plain words. The example entries below do
  exactly that, and it is the most valuable thing in them.
- **Do not attest a mutating read.** If the body writes, the tool refuses with `READ_SAFETY` and it
  is right.
- **Do not attest a duplicate.** One declaration takes at most one entry.

### The stop

Produce a worklist table (target, decision, category, proposed name, one-line reason) and **give it
to the human before writing a single entry.** Ask for approval or corrections. Do not proceed on
your own judgment: the whole point of the next phase is that a person ends up owning every sentence,
and it is cheaper for them to strike a target now than to review a draft of it later.

**Exit criterion:** an approved worklist.

---

## PHASE 4. Draft the entries

The record lives at `<APP_ROOT>/tibyan/attestations.json`, a JSON array, committed to version
control beside the source it describes. It is the reviewable record. Write it to be read by a
skeptical colleague, because that is exactly what happens to it.

### The entry schema

```json
{
  "target": "<FQCN>#<method>(<ParamType1>, <ParamType2>)",
  "category": "predicate | shape | both | boundary",
  "name": "camelCaseName",
  "resultSemantic": "One sentence stating what comes back.",
  "parameters": {
    "someFilter": "A sentence saying what this narrows and what happens when it is omitted.",
    "pageNum": "paging",
    "userId": "id"
  },
  "subjectEntity": "Invoice",
  "securityOwner": "<SECURITY_OWNER>",
  "readOnlyAffirmed": true,
  "assertedAuthorities": ["ROLE_BILLING_READ"],
  "provenance": {
    "assertedBy": "",
    "origin": "ai-draft",
    "date": "<TODAY>",
    "fingerprint": ""
  }
}
```

Field rules, each one enforced at intake:

- **`target`** must resolve to exactly **one** in-tree method declaration. The format is
  `<fully qualified class name>#<method name>(<parameter types>)`. Parameter types are matched on
  their **simple** names, so `String, CostCategory, LocalDate` is correct and the fully qualified
  forms also work. Zero matches or two matches both refuse with `UNRESOLVED_TARGET`.
- **`name`** is yours to mint, must match `[A-Za-z][A-Za-z0-9_]*`, and must **not** already start
  with `attested_`. The tool adds `attested_` itself, and `attested_boundary_` for a boundary entry,
  so the epistemic class is readable in the tool name before any description is.
- **`resultSemantic`** is the sentence that reaches the model. See the rubric below.
- **`parameters`** must cover every modeled input exactly: no missing key, no extra key. Each value
  is either a human sentence, or one of two reserved marks:
  - `"paging"`, permitted only on a structurally recognized paging or ordering parameter: an
    `Integer` named one of `page, pagenum, pagenumber, pagesize, size, pageoffset, offset, limit`,
    or a `String` named one of `orderby, sortby, orderdir, sortdir, direction`. A domain parameter
    cannot hide behind the mark.
  - `"id"`, permitted only on an identifier-typed input (`Long`, `UUID`, `String`, `Integer`), and
    only where the tool can prove a by-primary-key load. Claiming it elsewhere refuses.
- **`subjectEntity`** is the entity name you assert the read is about. It must exist in the model,
  and it must not contradict what the single repository access provably reads.
- **`securityOwner`, `readOnlyAffirmed`, `assertedAuthorities`** ride a `boundary` entry only. A data
  category carrying them refuses with `CATEGORY_MISMATCH`, because a data claim wearing a security
  owner would read as a security claim. Silence is never an affirmation: absent or `false`
  `readOnlyAffirmed` refuses.
- **`provenance.assertedBy`** stays empty on your drafts. **`provenance.origin`** is `"ai-draft"`.
  **`provenance.fingerprint`** stays empty; Phase 5 fills it.

### The resultSemantic rubric

The sentence is not decoration. The MCP tool description is built from it, verbatim, behind the
label "Human-attested, not tool-verified." An assistant reads that sentence and nothing else about
what the read means. A vague sentence produces confident wrong answers.

A good sentence answers all of these that apply:

1. What entity comes back, and one or many.
2. What is included and, more importantly, what is silently **excluded**.
3. Where the filtering happens, in the database or in the application, when that changes the answer.
4. What an omitted optional parameter defaults to. "No filter" and "defaults to this year" are
   completely different answers to the same question.
5. Whether the result is truncated, paged, or capped, and by what.
6. Anything that is a placeholder, a hardcoded value, or an approximation.

Bad, and worse than nothing:

> "Returns the costs for a building."

Good, from a real record in this repository:

> "Collected, outstanding and overdue totals and counts for one building's rental payments, computed
> in the application over AT MOST THE FIRST 25 matching payment rows in unspecified order, because
> the read leaves the default page size in place; it is not a summary of all of a building's
> payments."

That second sentence is what stops an assistant telling a finance manager a wrong total. Write that
kind of sentence, every time, or do not write the entry.

### Worked example: `predicate`

```json
{
  "target": "com.example.billing.service.InvoiceServiceImpl#getInvoicesByBuilding(String, Integer, Integer)",
  "category": "predicate",
  "name": "invoicesByBuilding",
  "resultSemantic": "Invoices belonging to one building, ordered by date descending and returned one page at a time.",
  "parameters": {
    "buildingId": "The numeric identifier of the building whose invoices are returned, passed as a string.",
    "pageNum": "paging",
    "pageSize": "paging"
  },
  "provenance": { "assertedBy": "", "origin": "ai-draft", "date": "<TODAY>", "fingerprint": "" }
}
```

### Worked example: `shape`

```json
{
  "target": "com.example.catalog.service.BuildingServiceImpl#getBuildingView(Long)",
  "category": "shape",
  "name": "buildingView",
  "resultSemantic": "One building loaded by its primary key, returned as a view object that carries the building's own fields plus the count of its units; no unit detail is included.",
  "parameters": { "id": "id" },
  "subjectEntity": "Building",
  "provenance": { "assertedBy": "", "origin": "ai-draft", "date": "<TODAY>", "fingerprint": "" }
}
```

### Worked example: `boundary`

```json
{
  "target": "com.example.billing.service.InvoiceServiceImpl#getRemoteInvoice(Long)",
  "category": "boundary",
  "name": "getRemoteInvoice",
  "resultSemantic": "The invoice with this id, fetched from the billing service over HTTP; nothing is read from or written to this application's own database.",
  "parameters": { "id": "id" },
  "subjectEntity": "Invoice",
  "securityOwner": "<SECURITY_OWNER>",
  "readOnlyAffirmed": true,
  "provenance": { "assertedBy": "", "origin": "ai-draft", "date": "<TODAY>", "fingerprint": "" }
}
```

A boundary entry is the elevated-stakes case. For every other attested capability the tool proved no
write was visible in what it read. For a delegation it proved nothing of the kind: the read-only
property rests entirely on `<SECURITY_OWNER>`'s word. Draft these last, draft few, and say plainly in
the worklist that each one is a person taking responsibility, not a measurement.

**Exit criterion:** `tibyan/attestations.json` is valid JSON, every entry carries `origin: ai-draft`,
and every entry matches an approved worklist row.

---

## PHASE 5. Stamp the fingerprints

```bash
java -jar $TIBYAN attest <APP_ROOT> --date <TODAY>
```

This computes a SHA-256 fingerprint over each entry's structural slice (the target method, the
compilation unit's imports, the repository fields and interfaces its body touches, and the return
type where it resolves in-tree) and writes it back into the file. It is the only path that stamps a
fingerprint, so a fingerprint always co-arrives with the code it pins.

An `UNRESOLVED` line means the target does not resolve to exactly one in-tree method. Fix the target
string and stamp again.

**Why it exists:** when the code under an attestation changes, the fingerprint stops matching and the
capability returns to refused, loudly, instead of continuing to serve a sentence that is no longer
true. Re-stamping is a deliberate human act of re-reading the code. Do not automate it in CI to
"keep things green": that would defeat the drift alarm entirely.

**Exit criterion:** every entry has a fingerprint, or is reported as unresolved with a reason.

---

## PHASE 6. Intake, then fix, then repeat

```bash
java -jar $TIBYAN scan <APP_ROOT> --report worklist-report.md
```

Three separate sections appear, and they are never summed together:

- `Attested, human-attested not tool-verified (N)`: entries that passed intake and will ship.
- `Attestation refusals (N)`: your worklist, each with a cause and a located reason.
- `Drafts awaiting human confirmation (N)`: your `ai-draft` entries, which ship nothing.

Work the refusals to zero. Every cause has a specific meaning and a specific fix:

| Cause | What it means | What to do |
|---|---|---|
| `MALFORMED_FILE` | The file is not a JSON array of entries | Fix the syntax. This one takes the whole file down, so fix it first |
| `UNRESOLVED_TARGET` | Zero or many in-tree declarations match | Correct the FQCN, the method name, or the parameter type list |
| `DUPLICATE_TARGET` | Another entry already targets this declaration | Merge them. One declaration, one entry, one name. If it has two holes, that is `both` |
| `CATEGORY_MISMATCH` | Unknown token, or the holes do not match the category | Re-read the category table. A boundary entry over a locally tied target is a `shape` |
| `CATEGORY_NOT_BUILT` | The precondition routes this target to a category the entry did not use | Follow the category the message names |
| `STRUCTURAL_FRAME` | A signature, parameter, type, or required boundary field did not verify | Read the located message; it names the field. On a boundary this is usually a missing `securityOwner` or `readOnlyAffirmed` |
| `READ_SAFETY` | A visible write in the fingerprinted slice | Do not attest it. Remove the entry and record it as refused by policy |
| `COVERAGE` | A missing input, an extra key, or a misused mark | Cover every input exactly once; move a domain parameter off the `paging` mark |
| `NAME` | Invalid, already prefixed, or colliding | Rename. Do not add `attested_` yourself |
| `PROVENANCE` | Incomplete provenance, or an unknown origin token | Only `human-authored` and `ai-drafted-human-confirmed` ship. `ai-draft` is queued, not refused |
| `MISSING_FINGERPRINT` | Nothing stamped it | Run Phase 5 |
| `STALE_ATTESTATION` | The code changed since the fingerprint | Re-read the method, confirm the sentence is still true, then re-stamp. If the meaning changed, rewrite the sentence first |
| `STRUCTURAL_CONTRADICTION` | Structure disagrees with the assertion | The assertion is wrong. Fix the claim, never the structure |

**Exit criterion:** `Attestation refusals (0)`, and every draft you intended appears in the drafts
queue.

---

## PHASE 7. The human confirmation gate

**Stop here.** This is the point of the entire design.

Present the drafts to the human as a review list: target, proposed name, category, and the full
proposed `resultSemantic` and parameter sentences. Then say, in your own words:

> Each of these is a proposal. Confirming one means you have read the method and you are taking this
> claim on as your own. Your name goes on it, on every surface, forever. Confirm one at a time, and
> only the ones you have actually read.

For each entry the human names, run exactly one command:

```bash
java -jar $TIBYAN attest <APP_ROOT> --confirm "com.example.billing.service.InvoiceServiceImpl#getInvoicesByBuilding(String, Integer, Integer)" --by <YOUR_NAME>
```

That rewrites that one entry's origin to `ai-drafted-human-confirmed` and records the confirmer. It
rewrites no words: if the human wants different wording, they edit the file, which is the same
reviewable record either way.

If the human wants to review later, that is a fine outcome. Drafts sit in the queue and ship
nothing. Say so and move on to the next phase with fewer attested capabilities. **Never** confirm to
unblock yourself.

After confirmations, re-stamp and re-scan:

```bash
java -jar $TIBYAN attest <APP_ROOT>
java -jar $TIBYAN scan <APP_ROOT> --report final-report.md --out final.ir.json
```

**Exit criterion:** every confirmed entry appears under `Attested`, every unconfirmed one is still a
draft, and refusals are 0.

---

## PHASE 8. Regenerate, verify, and decide exposure

```bash
cd <APP_ROOT> && mvn clean package
java -jar $TIBYAN generate --backend openapi --ir final.ir.json --out ./generated
```

Open `target/classes/tibyan/capabilities.json` and confirm, by reading it:

- each attested tool has `"attested": true` and an `attestation` object carrying the category, the
  result sentence, the asserter, the origin, the date, and the fingerprint,
- each attested tool's name starts with `attested_`, and each boundary tool with
  `attested_boundary_`,
- no draft appears anywhere in the file.

Then check the invariants in the survey totals. These are absolute, not metrics. Any of them above
zero outranks everything else you are doing: `mis-bound 0`, `attestation-drift 0`, `mis-safe 0`,
`mis-constrained 0`.

### Exposure

The runtime decides what actually registers as a tool. The defaults are deliberate; change them only
with the human's explicit agreement, and record the decision in the report.

| Property | Default | Meaning |
|---|---|---|
| `tibyan.attested.expose` | `true` | Whether attested tools register at all |
| `tibyan.attested.exposeSecurityUnverifiable` | `false` | An attested tool whose declared security is unverifiable stays closed unless this is set |
| `tibyan.attested.exposeBoundary` | `false` | A boundary tool stays closed unless this is set deliberately. It is off because its read-only-ness rests on a person's word |

A verified tool always registers. Enum-constrained inputs are enforced by the runtime, so an illegal
value is refused before the method is invoked, naming the legal values.

**Exit criterion:** the manifest is regenerated, the invariants hold, and the exposure decision is
written down with the name of whoever made it.

---

## PHASE 9. The chat assistant

Now build the thing a person actually uses. Stack: `<CHAT_STACK>`.

### Architecture

Your chat backend is itself an MCP client. It connects to the running application at
`http://localhost:8080/sse`, lists the tools Tibyaan registered, hands them to Claude as tool
definitions, runs the tool loop, and streams the answer back to a browser UI.

Do **not** reach for the Claude API's hosted MCP connector here. That connector fetches a publicly
reachable URL, and this server is, correctly, not publicly reachable. Your backend speaks MCP itself
and passes the tools through. (If the application is later exposed behind an authenticating proxy,
the connector becomes an option: it needs both halves,
`mcp_servers: [{type: "url", url, name}]` and `tools: [{type: "mcp_toolset", mcp_server_name: name}]`,
with beta header `mcp-client-2025-11-20`.)

For `typescript`: `@modelcontextprotocol/sdk` for the MCP client over SSE, `@anthropic-ai/sdk` for
Claude, and any modern UI you like. For `java`: the official MCP Java SDK and
`com.anthropic:anthropic-java`.

### Model and request shape

- Model: `claude-opus-5`.
- Thinking: `{ type: "adaptive" }`. It is on by default on this model; do not send `budget_tokens`,
  which is rejected.
- Stream every response, and use the SDK's final-message helper rather than hand-rolling promises
  over stream events.
- `max_tokens` around `64000` for a streaming chat turn.
- Parse tool inputs with a real JSON parser, never string matching.
- Return every `tool_result` for one assistant turn in a single user message, including errors, with
  `is_error: true`. Splitting them teaches the model to stop calling tools in parallel.
- Cache the system prompt and the tool list: they are stable, and they are the largest stable prefix
  you have.

### The assistant's system prompt

This is the part that makes the chatbot honest rather than merely fluent. Tibyaan has already put
the epistemic class in front of the model twice: in the tool name prefix, and as the first sentence
of every attested tool's description. Your system prompt has to make the model use it.

```text
You are the assistant for <APP_NAME>. You answer questions about real business data by calling the
tools you have been given, and you never invent a number.

Your tools come from Tibyaan, which reads this application's source and generates them. They fall
into three trust classes, and you must respect the difference:

1. A tool with a plain name is TOOL-VERIFIED. Its binding to real code was proven structurally.
2. A tool whose name starts with attested_ is HUMAN-ATTESTED. A named person asserted what it
   selects or returns; the tool proved everything around that assertion but not the assertion
   itself. Its description begins with that label and then gives the person's own sentence.
3. A tool whose name starts with attested_boundary_ is a HUMAN-ATTESTED BOUNDARY. It reaches a
   remote system, and even its read-only nature rests on a named person's assertion.

Rules:
- Read the whole description of an attested tool before calling it, and honor every qualification in
  it. If the description says a total covers at most the first 25 rows, or that an omitted date
  defaults to the start of this year, that is a fact about the answer, and you state it.
- When an answer rests on a class 2 or class 3 tool, say so in one short clause, naming who asserted
  it. For example: "Per the attested definition from <YOUR_NAME>, this total counts only paid
  payments due in the period."
- Every tool here is read-only. You cannot change anything in this system. If a user asks you to,
  say plainly that you can read but not write.
- If no tool can answer the question, say that, and say what the closest available tool does cover.
  Never approximate a figure from a tool that measures something else.
- An enum input has a closed set of legal values. Use one of them, never a paraphrase.
```

### The UI

Modern, and honest about provenance. At minimum:

- Streaming assistant messages.
- A visible trace of each tool call: the tool name, the arguments, and a collapsed result.
- A provenance badge on every tool call: **verified**, **attested**, or **attested boundary**, in
  three visually distinct treatments, with the asserter's name and date on hover for the attested
  ones. This is the whole product differentiator. Do not bury it.
- A conversation-level notice when any answer in the thread used an attested tool.
- Sensible empty and error states, including a clear message when the MCP server is unreachable.

### Security, before it goes anywhere

The generated server executes real methods against real data and adds no authentication of its own.
Keep the application bound to localhost, put the chat backend between the browser and it, keep the
`ANTHROPIC_API_KEY` server-side only, and read [docs/SECURITY.md](SECURITY.md). If the human asks
for a network deployment, put the endpoint behind their existing authenticating proxy and say
explicitly what you did.

**Exit criterion:** the assistant answers a real question about `<APP_NAME>` using at least one
verified tool and at least one attested tool, and its answer visibly carries the attested
qualification.

---

## PHASE 10. Versions and the record

Two version axes, both handled explicitly.

**The Tibyaan coordinates** in the application: plugin and runtime are both at `<TIBYAN_VERSION>`,
and they move together. Never mix versions between the manifest generator and the runtime that
serves it.

**The application's own version.** Bump it once, in the one place Maven owns it:

```bash
cd <APP_ROOT> && mvn versions:set -DnewVersion=<next-version> && mvn versions:commit
```

Do not hand-edit version literals in source. If the application has any version string typed into
Java, derive it from the build instead: this project learned that the hard way, with an MCP server
that advertised `0.1.0` through two releases and a CLI whose version constant was a stale
hand-edited literal.

Add a `CHANGELOG.md` entry for `<APP_NAME>` covering, in adopter-facing language:

- the Tibyaan adoption and the MCP endpoint it exposes,
- how many capabilities are tool-verified and how many are human-attested, kept as separate numbers
  and never summed,
- every attested capability by name, with its asserter,
- the exposure decisions and who made them,
- the chat assistant.

Commit in coherent steps. Suggested sequence, adjusted to what actually happened:

1. `Adopt Tibyaan <TIBYAN_VERSION>: build plugin and runtime`
2. `Add the attestation record for <N> reads, as drafts`
3. `Confirm <N> attestations` (only after Phase 7)
4. `Add the <APP_NAME> chat assistant over the Tibyaan MCP surface`
5. `Bump <APP_NAME> to <next-version> and record the adoption in the changelog`

Every commit message ends with:

```text
Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

---

## PHASE 11. Finish: return the feature and the report

You are not done when the work feels done. You are done when the repository proves it. Produce the
report below, filled in with real output, and hand it over with the feature.

```markdown
# <APP_NAME>: Tibyaan adoption and chat assistant

## 1. What shipped
One paragraph. What a user can now do that they could not do before.

## 2. Coverage, before and after
| | Baseline | After |
|---|---|---|
| Tool-verified capabilities | | |
| Human-attested capabilities | | |
| Drafts awaiting confirmation | | |
| Attestation refusals | | 0 |
| Refused reads, by cause | | |

The two capability rows are never summed. They are different kinds of thing.

## 3. Every attested capability
| Tool name | Target | Category | Asserted by | Date | Exposed |
|---|---|---|---|---|---|

## 4. Invariants
mis-bound 0, attestation-drift 0, mis-safe 0, mis-constrained 0. Quote the totals line verbatim.

## 5. What I deliberately did NOT do
Every target left refused, and why: no honest claim available, mutating, out of policy, or awaiting
human review. Name them. This section is not an apology, it is the safety record.

## 6. Decisions that need a human
Exposure switches changed and by whose authority, boundary entries and who owns each, and any draft
still queued.

## 7. Evidence
- Commits: hash and one-line description, in order.
- The reactor result: the verbatim tail of `mvn clean verify`.
- The final survey totals line, verbatim.
- The chat assistant transcript of one verified answer and one attested answer, showing the
  provenance badge.
```

Three rules about the report:

1. **No claim without its command output.** If you write "12 attested", the totals line that says so
   is in section 7.
2. **Section 5 is mandatory and non-empty.** A run that refused nothing either found a trivially
   simple application or was not honest. Say which.
3. **If something did not work, say so with its output.** A blocked phase reported as done is worse
   than a blocked phase. Finish everything else in full and name exactly what you left out and why.

Then, in your closing message to the human, give them: the endpoint URL, how to start the chat
assistant, the count of attested capabilities and of drafts still awaiting them, and the single
sentence you most want them to read from section 5.
