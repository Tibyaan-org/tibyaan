# Tibyaan public website: plan

> Where the built site lives: this plan was implemented, and the site was then extracted into its
> own repository so it can deploy independently: **github.com/Tibyaan-org/tibyaan-site** (GitHub
> Pages at https://tibyaan-org.github.io/tibyaan-site/). This document remains here as the design
> record. The `website/` directory that once held the source has been removed from this repo.

Status: APPROVED WITH AMENDMENTS. Written 2026-07-17; amended the same day after owner review.
The owner approved the plan subject to five amendments and two notes; section 0.0 records each
one and where it landed in this revision. Still a planning artifact: no scaffold, no components,
no code exist yet. The build begins after the owner's final look at the amended hero (2.1) and
the regenerated claims sheet (section 8). This document is governed by the same honesty
discipline as the volumes: every factual claim the site will make is traced to a repository
document, and anything the docs do not support is flagged rather than written.

Absolute constraints carried through every section:

1. The site makes exactly the claims the docs support, and no more. The honesty IS the pitch.
2. The private enterprise benchmark is referred to only as "a real multi-module enterprise Spring
   application", by counts and shapes, never by name, domain, or any identifying detail.
3. No em dashes anywhere (CLAUDE.md rule 9).
4. No fabricated interactivity, no invented numbers, no testimonials (there are none), no adopter
   logos (there are none), no "any app" or "full coverage" language.
5. Anything on the site that looks like tool output IS tool output: verbatim from a recorded run
   or a really generated file, with a provenance caption. Elisions are marked in the text itself,
   and refusals are never elided.

---

## 0.0 Amendment record (owner review, 2026-07-17)

| # | Amendment | Where it landed |
|---|---|---|
| A1 | Kill or heavily rework the animated terminal; no simulated or abbreviated survey as a centerpiece, because abbreviation is how inflation sneaks in | Hero is now static with real numbers (2.1); the survey section is a verbatim, non-animated excerpt under the elision inversion rule (refusals shown in full, only discoveries may be elided, markers visible in the text); client JS at launch is zero (5.2); constraint 5 and copy rules 8 and 9 added |
| A2 | The hero headline must carry the differentiator, not just the category; the one property nobody else has belongs in the first screenful | H1 reworked to lead with proven-or-refused; the subhead carries the zero-wrong-bindings record with its scope attached; a static metric row puts the real numbers in the first screenful (2.1) |
| A3 | Add a real generated-output example: developers trust artifacts over adjectives | New landing section "What it generates" (2.1): verbatim generated MCP manifest and OpenAPI lines, in-repo example app or public-ledger-named capabilities only, provenance-captioned; new claim C-26 |
| A4 | Resolve the C-14 flag now; the site must not imply Central availability until it is true | Resolved by measurement 2026-07-17: all nine modules, including the self-contained CLI jar, are live on Maven Central at 0.1.0 (published 2026-07-08 per STATUS; re-verified against repo1.maven.org). The launch CTA is therefore real on day one. Station 1 says "download the CLI" (section 4), C-14 and C-19 upgraded (section 8), D-7 closed (section 9), and the site build gains a CI check that fetches every Central URL it prints and fails on any non-200 (5.2) |
| A5 | Trim the launch sitemap to three pages; every extra page is copy that can drift | Launch scope is /, /how-it-works, /docs/getting-started (section 2); the evidence section on the landing page carries the numbers at launch; security folds into getting-started as an on-page station; /evidence, /docs/security, /landscape, and the deep-docs hub move to the post-launch backlog |
| N1 | Keep "what it does not do" fold-adjacent; its placement is the credibility play | Landing order reworked: it sits directly after the survey and artifact pair, in the first half of the scroll, never at the page bottom (2.1) |
| N2 | Confirm the theme covers dark/light or pick one; do not improvise a second palette | Decision D-8, resolved: light-only at launch, `color-scheme: light`; the Ink sections are the design system's own dark moments; a dark mode waits until the design system defines one (5.1, section 9) |

Everything not named in an amendment stands as approved: the audience and message, the idea
copy, the getting-started stations, static-export Next.js, MDX docs, the claims-file mechanism,
the redaction handling, and the voice rules.

---

## 0. Ground truth: what was read, and what the theme assets give us

### 0.1 Truth sources read for this plan

| Source | What it contributes |
|---|---|
| docs/STATUS.md | Current state: v1 complete, 0.1.0 published to Maven Central 2026-07-08, Entity Identity and Discovery-Gap built, three-benchmark record |
| Volume 1 (vision) | Positioning, problem statement, target customers, the moat framing, the north star (used carefully: much of it is future vision, not present fact) |
| README.md | The adoption story, the honest-expectations framing, the real quickstart, requirements, "refusals are the product", the numbers with their qualifiers |
| docs/SECURITY.md | Read-only-by-design boundary, no-auth-in-v1, gate-the-endpoint responsibility |
| Volume 13 ("The structural ceiling", "Attestation") via STATUS and README | What is provable versus beyond proof, and why; the designed-and-waiting Attestation phase |
| docs/experiments/second-app-trial.md | pms benchmark: 43 entities, discovered 2, mis-bound 0, view-heavy idiom |
| docs/experiments/third-app-trial.md | erp benchmark: 25 entities, discovered 25 (by-id 16, list 9), mis-bound 0, the transfer verdict (coverage is idiom-bound) |
| docs/experiments/enterprise-benchmark.md | The redacted third benchmark: 169 modules, 111 entities, discovered 4, mis-bound 0, BLOCKED to EMITTED via Entity Identity |
| docs/competitive-landscape.md | The Rindler comparison and, more importantly, the honesty rules for writing about competitors |
| docs/OVERVIEW.md | An existing plain-language telling of the idea; the seed of the site's core copy |
| docs/CONNECTING-AN-MCP-CLIENT.md (via STATUS) | The verified out-of-process client connection, the SSE transport caveat, the bridge for stdio clients |

One stale fact found during the read and flagged for separate fixing: README's quickstart still
says the artifacts are "not yet on Maven Central". STATUS.md records 0.1.0 published 2026-07-08.
The site states the current truth (published), and the README fix is spun off as its own task.

### 0.2 The theme assets: where they are and what they give us

Found at `asstes/tibyan-platform/project/` (a Claude Design handoff bundle; note the folder is
spelled "asstes"). Contents:

- `Tibyan Brand Identity.dc.html`: the design system. This is the primary theme source.
- `Tibyan Website.dc.html` and `Tibyan Website v1.dc.html`: full landing-page prototypes.
- `Tibyan Compiler.dc.html`: a product-page prototype (the file the user had open at handoff).
- `screenshots/`: rendered captures of the above.
- `support.js`: the prototype runtime, not relevant to production.

What the brand identity file gives us, concretely:

**Palette ("Heritage Modern"): limestone, olive, sienna.**

| Token | Hex | Role |
|---|---|---|
| Ink | #211D17 | Primary text, dark surfaces, the mark |
| Limestone | #F4EEE3 | Page background |
| Surface | #FBF7EF | Cards, panels |
| Sienna | #A6492E | THE accent. Discipline: one emphasis per view |
| Olive | #5C5A3F | Secondary accent (rare) |
| Text muted | #756B5C | Secondary text, labels |
| Border | #E4DAC8 | Card borders, 1px grid gaps |
| Hairline | #D7CDBA | Rules, dividers |
| Sienna-on-dark | #D98A6A | Accent on Ink surfaces |
| Muted-on-dark | #B7AE9E / #9A8F7E | Secondary text on Ink |
| Code surface | #2C271F | Code blocks inside dark sections |
| Code text | #E9D9C8 | Terminal and code foreground |
| Success mark | #7FAF7A | The check in terminal output (used in prototypes) |

Contrast is pre-verified in the identity file: Ink on Limestone is 13.0:1, AAA.

**Type (IBM Plex, all free on Google Fonts).** Plex Serif Medium for display and headlines
(editorial gravitas, used sparingly), Plex Sans for headings (SemiBold) and body (Regular),
Plex Mono for code, terminal output, eyebrows, and metric labels, Plex Sans Arabic as the designed
equal for a future Arabic version. Type scale: H1 56/1.05 serif, H2 36/1.15 sans semibold,
H3 24/1.3, body 16/1.6, small 13/1.5.

**Other tokens.** 12-column grid, 24px gutter, generous margins, mirrors for RTL. Line icons at
1.75px stroke, round caps, 24px grid, occasional sienna dot. Motion: "things resolve, they don't
bounce": ease-out, 200 to 320ms, opacity and small translate only, one element at a time,
`prefers-reduced-motion` respected. The logo is the bilingual ligature mark (one bar serving as
the Latin T crossbar and the Arabic ت body, two sienna dots above), with horizontal, stacked, and
mark-only lockups, and it survives at 16px for the favicon.

The website prototypes additionally give us proven layout patterns: sticky blurred nav, eyebrow
plus status pill, large serif hero, terminal-style demo card with an Ink body, a four-item trust
band, mono-numbered sections (01, 02, 03), card grids with 1px border-color gaps, a two-column
"by hand versus with the tool" contrast (light card against Ink card), a specs table with mono
labels, a dark full-bleed CTA section, and a footer with the bilingual wordmark.

### 0.3 Three theme caveats the plan works around (not against)

1. **The prototypes' copy describes a different product and must be replaced wholesale.** Both
   website prototypes and the compiler page sell a URL-crawling "MCP compiler" ("A website goes
   in. An MCP server comes out.", "Infers the site's model", "Point it at a URL", fake compile
   animation with route counts derived from string length, "Private beta", waitlist form). That
   is an earlier product concept, it is close to Rindler's actual territory, and its language
   ("infers") is the exact opposite of Tibyaan's differentiator. The plan keeps the visual shell
   and discards every word. The fake interactive compile demo is dropped on principle and replaced
   with an honest artifact (section 2, "A real survey").
2. **The brand identity positions "Tibyan" as a consultancy** ("Clarity, engineered", mission and
   vision written for a services firm). The site borrows the visual system and the name story
   (tibyān: clear exposition, making something evident), which fits an honesty-first tool
   perfectly. It does not borrow the consultancy mission, vision, or service copy.
3. **Spelling: the assets say "Tibyan", the published project is "Tibyaan".** The GitHub org is
   Tibyaan-org, the groupId is io.github.tibyaan-org, the README's canonical project name is
   Tibyaan, and the artifacts and CLI keep the short "tibyan" (README, "A note on the name").
   Recommendation: the site's product name is **Tibyaan**, matching the published coordinates,
   with the short form appearing only in code snippets exactly as it really appears there. Owner
   decision recorded in section 9.

---

## 1. Audience and message

### Who the site is for

Primary: a developer or technical lead who owns a long-lived Spring Boot backend that runs the
business, cannot be rewritten, and is now expected to work with AI agents. They are skeptical of
AI tooling that overpromises, and they have been burned by "works on anything" claims before.

Secondary: the technical decision-maker (CTO, architect) above that person, who needs to judge
risk: what does this thing actually do to my system, what can go wrong, what does it refuse to do.

Tertiary: developers in the MCP and agent-infrastructure community evaluating approaches, who may
never adopt but who amplify and contribute.

### The single core message

> Tibyaan makes the Spring backend you already run operable by AI agents by exposing only what it
> can prove from your own source, and refusing, out loud, everything it cannot.

### What the site should make them feel and do

Feel: "I understand exactly what this does in thirty seconds, and I believe it, because the site
itself refuses to oversell." The honesty of the tool and the honesty of the site must be the same
experience. A visitor who notices the site saying "here is what we do not do" is a visitor who
trusts the headline numbers.

Do, in order of value: (1) run the read-only preview against their own app, because the preview
costs nothing and produces their real number; (2) star and follow the GitHub repository; (3)
write to the maintainer with their survey result, because a real external survey is exactly the
external signal the roadmap is gated on (STATUS.md: "What the tool needs next is external signal").

That last point is worth pausing on: the site's job is not only marketing. The project's own
roadmap needs real adopter surveys to rank what gets built next. So the primary call to action is
deliberately "preview your app and tell us what it found", which is both the honest first step of
adoption and the project's most valuable input.

---

## 2. Sitemap and sections

Launch scope (per A5): THREE pages. Every extra page is copy that can drift from the claims
sheet, and shipping is the point.

```
/                      Landing: the whole story in one scroll
/how-it-works          The idea and the proof model, one level deeper
/docs/getting-started  The real adoption path (preview, adopt, connect, secure)
```

Post-launch backlog, built only after the launch trio is live and true: `/evidence` (a dedicated
page; at launch the landing page's evidence section carries the load), `/docs/security` (a
dedicated page; at launch the essential security content is an on-page station inside
getting-started plus a link to the repository's SECURITY.md), `/landscape` (decision D-3 is
deferred until this page is scheduled), and the deep-docs hub. Sections 2.3, 2.5, and 2.6 below
describe backlog pages: they are planned now so their claims are reviewed now, and built later.

Every section below lists: its one job, the key message, and the honest claim it makes with its
source. No section without a true message.

### 2.1 Landing page (`/`)

Final section order (reworked per N1 so the boundary section is never buried; the build follows
this order exactly): 1 Hero, 2 Trust band, 3 The problem, 4 The idea, 5 A real survey, 6 What it
generates, 7 What it does not do (yet), 8 The evidence, 9 How it works, 10 Requirements strip,
11 Closing CTA, 12 Footer. The survey card is reachable within roughly one viewport of the fold,
and the boundary section sits directly behind it, well ahead of the page's midpoint by content
weight: the credibility pair (real output, stated limits) is the second thing a scrolling
visitor meets, not the last.

**Hero** (reworked per A1 and A2: static, differentiator first, real numbers in the first
screenful, no animation and no terminal of any kind).
- Job: the differentiator understood in ten seconds, the category in thirty, and nothing on the
  screen that is not literally true.
- Eyebrow: `Open source · Apache-2.0 · Maven Central` with a small status pill: `v0.1.0`.
  (Claim source: LICENSE, STATUS.md publish entry; Central presence re-verified 2026-07-17.)
- H1 (Plex Serif): **"AI agents for the backend you already run. Every tool proven, or
  refused."** The differentiator is the headline's second sentence, not a tagline further down:
  proven-or-refused is the one property nobody else can say, so it is in the first screenful.
- Subhead: "Tibyaan reads your Spring application's source and exposes only the operations it
  can prove: which real method backs each tool, on which entity, returning what. Everything it
  cannot prove it refuses, with the reason and the file. Zero wrong bindings, on every run, on
  every application it has been measured on."
- Under the subhead, a static metric row (Plex Mono, hairline-separated, no animation):
  `31 verified tools · 3 real applications · 0 wrong bindings`. The scope of the strong claim
  ("three real applications", idiom-bound qualifier one anchor away in the evidence section) is
  carried by the metric row itself and the trust band directly beneath it, so the claim and its
  scope share the first screenful (the X-6 discipline, applied to the hero).
- CTAs: primary "Preview your app" (to /docs/getting-started, the read-only step), secondary
  "See a real survey" (anchor to the survey section below), tertiary GitHub link.
- Honest claim: reads source, proves reads, serves MCP and OpenAPI, never modifies the original;
  zero wrong bindings scoped to the measured record. Sources: README "The idea", Volume 1
  mission commitment 2, STATUS.md, the three ledgers (C-4).

**Trust band** (four one-liners, every one literally true):
1. "Reads your source. Never modifies it." (Volume 1 mission; README)
2. "Read-only by design: writes are refused at discovery, not just disabled." (SECURITY.md)
3. "Everything it cannot prove is refused with a located reason." (README; CLAUDE.md fail-loud)
4. "Zero wrong bindings across every run on three real applications." (the three ledgers)

**The problem.**
- Job: recognition, in the visitor's own words.
- Key message: the systems that run the business predate the agent era; the three existing
  options (rewrite, hand-build every connection, let the AI improvise) are each bad in a
  different way.
- Honest claim: none beyond framing; this section is narrative. Source: Volume 1 section 3,
  OVERVIEW.md "The problem".

**The idea.** The heart of the site. Full drafted copy in section 3 of this plan.
- Job: a smart stranger understands what the tool actually does and why the honesty matters.
- Honest claim: the never-guess principle, stated as the defining property. Source: OVERVIEW.md,
  README "The idea".

**A real survey** (reworked per A1: static and verbatim, no animation, no replay).
- Job: show the product's voice, and make refusals concrete and likeable.
- Content: a terminal-styled card (Ink body, Plex Mono, the prototype's visual pattern) showing
  a VERBATIM excerpt of the recorded ERP benchmark survey output as static text. No typewriter,
  no replay, no client JS: the text is pasted from the ledger at authoring time and rendered at
  build time.
- The elision inversion rule (the heart of A1): if anything is elided for space, it is the
  discovered list, never the refusals. The card shows all 9 Failed lines in full, the Totals
  line in full (`discovered 25 (by-id 16, list 9), failed 9, list-refused 1, skipped-by-parse 0,
  partial 13, mis-bound 0`), and a handful of discovered lines with an explicit marker inside
  the output itself, for example `... (+21 more discovered; full per-entity ledger linked
  below)`. Abbreviation is how inflation sneaks in; this card abbreviates only in the direction
  that understates.
- Caption, verbatim provenance: "Unedited output of the ERP benchmark survey (2026-07-02,
  docs/experiments/third-app-trial.md); elisions marked in the text; the full per-entity ledger
  is public." A short annotation calls out the User line: two methods both fit the shape of a
  read-by-id, so the tool refused rather than pick one. "Refusals are the product working, not
  failing."
- Honest claim: the survey output shown is real, verbatim, and elided only against the tool's
  favor. Source: third-app-trial.md; enforced by constraint 5 and copy rule 8 (claims C-25).
  The prototype's URL-input demo, which invents route counts from string length, remains the
  named example of what this site must never do.

**What it generates** (new section per A3: the artifact does the convincing).
- Job: show actual generated output, because the product's honesty is legible in the artifact
  itself. Developers trust artifacts over adjectives.
- Content: one card, two verbatim snippets, both provenance-captioned:
  1. Lines from the generated MCP manifest of the in-repo example app (getProduct): fully
     reproducible by anyone with `mvn package` from a checkout, and the caption says so.
  2. Lines from a generated artifact for a view-returning capability already named in the public
     ledgers: pms getUnit, whose manifest carries `view: UnitVTO` with the backend's one-sentence
     description clause, or the erp OpenAPI document, where a partial entity's schema discloses
     its omitted fields (`x-tibyan-partial`, nothing listed as required, no schema claiming
     closedness). The honest description clause the backend writes is the visible star of the
     snippet: the tool's own output says what a view is and what was not extracted, which is the
     pitch in the tool's own voice.
- Sourcing rule: snippets are pasted from files the tool actually generated, never retyped and
  never trimmed inside a line; only the in-repo example app or capabilities already named in the
  public ledgers may appear; each snippet's caption names the generating command and date. The
  build session copies them from real generated output, not from memory.
- Honest claim: the generated output shown is real generated output. Sources: the generated MCP
  manifest and OpenAPI document; STATUS E2-view, E4, and OpenAPI entries name the exact fields
  shown (claims C-26).

**What it does not do (yet)** (placement per N1: this is the credibility play, so it sits
directly after the survey and artifact pair, in the first half of the scroll, never at the page
bottom).
- Job: the honesty differentiator made explicit; also a filter that saves unfit adopters time.
- Framing sentence: "Every one of these is a deliberate refusal-over-guessing boundary, not an
  oversight." (Verbatim framing from README.)
- Items: filtered reads (findByCategory, search, and similar: a parameter's meaning is a claim
  the tool cannot verify from structure, and a tool description an agent acts on must not carry
  unverified claims); writes and actions (refused at discovery until an explicit policy gate
  exists); custom result-set wrapper envelopes; non-Spring stacks (the model is neutral, only the
  Spring provider exists); Spring Boot 2.x / Java 8 hosts (the runtime needs Boot 3.x on Java 17,
  and saying so up front beats saying it after someone edits their pom).
- One forward-looking line, carefully bounded: "The path beyond this boundary is designed and
  waiting (explicitly human-attested capabilities, never guesses), and it will be built when real
  adopters hit the boundary, not before." (Source: Volume 13 Attestation; STATUS.)
- Honest claim: the boundary and its rationale. Source: README "What it does not do yet, and
  why", SECURITY.md, Volume 13.

**The evidence** (numbers block).
- Job: put the measured record in front of the visitor with its qualifiers attached, in the same
  breath.
- The numbers, exactly as the docs support them:
  - "31 verified capabilities across three real, unmodified applications. Zero wrong bindings, on
    every run." (erp 25, pms 2, private enterprise 4; sources: third-app-trial.md,
    second-app-trial.md, enterprise-benchmark.md, STATUS.md.)
  - Three benchmark rows: ERP app, 25 entities, 25 capabilities (16 by-id, 9 list). PMS app, 43
    entities, 2 capabilities (view-heavy idiom, most refused by name). "A real multi-module
    enterprise Spring application": 169 modules, 111 entities surveyed, 4 verified capabilities.
    Each row shows mis-bound 0.
  - The qualifier, verbatim in spirit from the README: "Read these numbers honestly. Coverage is
    idiom-bound: two of the three apps share an author, the apps discover through different
    rules, and your app's number depends on your idioms. It could land anywhere in that range.
    The one guarantee that transfers is zero wrong bindings. Preview your own app to see your
    real number before you decide."
- Redaction discipline: the third row never carries a name, domain, or industry. Counts and
  shapes only.
- Honest claim: all numbers as cited. Confidence: verified, ledger-backed.

**How it works** (four steps, replacing the prototype's Read/Map/Generate/Serve with honest
verbs; placed after the boundary and evidence sections per the N1 reordering):
1. **Read.** It parses your application's source, the same files your team maintains, and builds
   a neutral map of the entities and read operations it finds. Unreadable files are skipped and
   named, never silently ignored. (Source: Volume 5 scan behavior via STATUS E1 entry.)
2. **Prove.** For each candidate operation it demands structural evidence: which real service
   method backs it, on which entity, returning what. Names are never trusted; only structure
   counts. (Source: Volume 5 rules 4.1/4.2/4.10; README.)
3. **Refuse or emit.** Anything the evidence does not decide is refused with a located reason you
   can read. What survives becomes a verified capability. On real applications, most of a large
   codebase is refused, and that is the tool working. (Source: README "What you will actually
   see"; the ledgers.)
4. **Serve.** At build time your own build generates the capability manifest, and a small runtime
   inside your application serves the verified set to AI agents over MCP, and as an OpenAPI
   document, calling your real methods. Nothing in your code changes. (Source: README quickstart;
   STATUS Direction B entries; OpenAPI backend entry.)
- Honest claim per step as cited above.

**Requirements strip.**
- Job: prevent the wrong adopter from wasting an afternoon.
- Host app: Spring Boot 3.x on Java 17+. Build: Maven (no Gradle plugin). Preview CLI: needs only
  Java 17 and never runs your app. Transport: MCP over HTTP+SSE at /sse; stdio-only clients need
  a bridge. (Source: README "Requirements and compatibility".)

**Closing CTA** (dark Ink section, the prototype's access pattern, but honest):
- Job: convert understanding into the read-only preview, and surveys into contact.
- Copy direction: "Preview your app. It reads your source and changes nothing." plus "Ran the
  preview? Tell us your numbers: every real survey shapes what gets built next." with the
  maintainer contact and GitHub links. No waitlist, no "private beta": the tool is published and
  open source.
- Honest claim: the preview is read-only and writes nothing (README Step 1); external surveys
  genuinely drive the roadmap (STATUS "what the tool needs next is external signal").

**Footer.** Bilingual wordmark lockup, Apache-2.0, Maven Central namespace link, GitHub, security
policy link, "Report a vulnerability" mailto (SECURITY.md).

### 2.2 How it works (`/how-it-works`)

- Job: the concept one level deeper for the reader deciding whether to trust it, still without
  internal jargon. This page earns the trust the landing page asserts. Per A5 it also absorbs
  the full "what is the idea" explanation (section 3): the landing page carries the compact idea
  copy, this page carries the rest, so the launch trio covers the whole story.
- Sections:
  1. **The map.** What the tool extracts (business objects and the read operations over them) and
     what "neutral model" means in plain terms: one verified description that many outputs are
     generated from, today an MCP server and an OpenAPI document, byte-identical on every rebuild.
     (Source: Volume 4 concept via README; OpenAPI backend entry in STATUS; determinism rule 10.)
  2. **What proof means here.** Concrete, real examples, no abstraction: a read is exposed when
     the framework contract proves it (a Spring Data findById on the entity's repository is a
     by-primary-key load by contract, not by naming convention); a read is refused when two
     methods both fit (the real User example: refused as ambiguous rather than picking one); a
     read is refused when its meaning is not in the structure (a soft-delete guard between the
     load and the return could be an authorization rule; the tool cannot know, so it refuses).
     (Sources: Volume 5 rules narrated; README survey; enterprise-benchmark.md helper-hop refusal.)
  3. **The ceiling, stated proudly.** A structural tool tops out at what structure can prove:
     unfiltered reads by id and unfiltered lists today. The filtered majority of a real
     application's read API is beyond structural proof, and the tool says so instead of guessing.
     Where that boundary can move honestly (a human attesting a capability, labeled as such,
     never presented as verified), it is designed and trigger-gated. (Source: Volume 13 "The
     structural ceiling" and "Attestation"; Volume 5 4.11 via STATUS.)
  4. **In place, at build time.** What adoption physically is: one dependency plus one build
     plugin; the manifest is generated from source by your own build; the server runs inside your
     application's process and calls your real methods; delete the two pom entries and the layer
     is gone. Nothing is hosted by us, nothing metered. (Source: README quickstart; Volume 7 via
     STATUS; competitive-landscape.md 3.5 for the dependency-shaped framing.)
- Honest claims: as cited per section.

### 2.3 Evidence (`/evidence`), post-launch backlog (at launch the landing evidence section carries the load)

- Job: one page that survives a skeptical CTO reading it twice.
- Content: the three benchmark rows with dates; what mis-bound 0 means and does not mean (it is a
  correctness guarantee about each exposed tool's binding, not an access-control guarantee, per
  SECURITY.md); the idiom-bound coverage explanation with the ERP/PMS contrast; the redaction
  note for the enterprise benchmark ("private and redacted by agreement: counts and shapes only");
  links to the full public ledgers on GitHub, which name every refusal on the two public apps.
- The transparency move that IS the marketing: "Every number on this page links to the dated
  experiment record that produced it."
- Honest claims: the ledger numbers and the transfer verdict, as cited in 2.1's evidence block.

### 2.4 Getting started (`/docs/getting-started`)

Planned in full in section 4.

### 2.5 Security (`/docs/security`), post-launch backlog (at launch this content lives inside getting-started, station 5)

- Job: SECURITY.md, restructured for the web, with zero softening.
- Key messages kept verbatim in spirit: the generated layer executes real methods against real
  data; v1 is read-only by design (refused at discovery, not toggled off); the layer inherits
  your process and adds no auth of its own; gating the endpoint is your job; treat /sse like an
  unauthenticated admin console; fail-loud protects the binding's integrity, not access control.
- Honest claim: all of SECURITY.md. This page existing prominently is itself a trust signal.

### 2.6 Landscape (`/landscape`), post-launch backlog, owner decision D-3 deferred until scheduled

- Job: help the visitor place the approach among alternatives, under the same honesty rules as
  docs/competitive-landscape.md (their claims marked as vendor claims, ours cited, no axis
  presented as a sweep).
- Recommendation: publish at CATEGORY level ("outside-in: drive a browser against a system you
  cannot read, infer, then verify empirically" versus "inside-out: read the source you own and
  prove structurally"), without naming Rindler on the site itself, and link to the named analysis
  in the repository for readers who want it. Reason: the repo doc's careful sourcing discipline
  is hard to preserve in marketing-page format, and a category framing makes the same point
  without carrying another company's name on our homepage. The named doc remains public in the
  repo either way.
- The one line worth surfacing from that doc: independent convergence on "small, typed,
  pre-verified tools over MCP, not raw access" is evidence the shape of the idea is sound
  (competitive-landscape.md section 4), stated without claiming it validates market success.

### 2.7 Deliberately not on the site

- Testimonials, case-study logos, adopter counts: none exist (competitive-landscape.md section 4
  records zero external adopters by our own accounting). Nothing is faked.
- Pricing: it is Apache-2.0 open source; there is nothing to price.
- A blog: later, if ever; the ledgers are better content than posts.
- The Arabic version: phase 2. The design system is bilingual-native and the plan keeps the door
  open (section 5), but shipping EN excellently beats shipping two languages adequately.

---

## 3. The "what is the idea" explanation (drafted copy)

This is the heart of the site. Written for a smart developer who has read nothing, no internal
vocabulary (no "IR", no "provider", no "capability" as a term of art; "MCP" appears once,
defined by what it does). Target: understood in thirty seconds, trusted in sixty. Based on
OVERVIEW.md and README, tightened for the page.

### 3.1 The idea (landing page section, ~150 words)

> **It never guesses.**
>
> Your backend already knows how to look up a customer, an order, an invoice. That knowledge is
> real, tested, and locked inside a system that predates AI agents.
>
> Tibyaan reads that system's source code, the same files your team maintains, and works out
> which of its read operations it can prove safe to hand to an AI agent. Proof here is literal:
> an operation is exposed only when the code's own structure shows exactly which real method
> backs it and exactly what that method returns. Where the structure leaves any doubt, Tibyaan
> refuses, out loud, with the reason and the file.
>
> What survives becomes a set of tools that AI agents already know how to call, served from
> inside your own application, hitting your real logic and your real data. Your code is never
> modified. The set is smaller than your system, and every entry in it is right.

### 3.2 Why the refusals are the point (~90 words)

> A tool an agent uses is a promise: "getCustomer returns the customer." An agent will act on
> that promise without reading a disclaimer. So the only honest way to build this layer is to
> promise nothing that is not proven.
>
> That is why a Tibyaan survey of a real application reads as a short list of verified tools and
> a long, named list of refusals, each with its reason. The refusals are not failures. They are
> the exact edge of what can be guaranteed, drawn where it really is instead of where marketing
> would like it.

### 3.3 How it works (landing page, the four steps, condensed)

> **Read.** Tibyaan parses your application's source and maps the business objects and the read
> operations around them. Files it cannot parse are skipped and named, never silently dropped.
>
> **Prove.** Each candidate operation must show structural evidence: the real method that backs
> it, on the real repository of the real entity. Method names, parameter names, and comments
> carry no weight. Structure decides, or nothing does.
>
> **Refuse or emit.** Undecidable cases are refused with a located reason: this method, this
> file, this rule. Decided cases become verified tools. On a large layered application, expect
> most operations to be refused. That is the tool keeping its promise.
>
> **Serve.** Your own build generates the tool manifest from your source. A small runtime inside
> your application serves the verified tools to AI agents over MCP (the open protocol agents use
> to call tools), and the same verified model also produces an OpenAPI document. Rebuild, and
> the layer regenerates from whatever your source now says.

### 3.4 The thirty-second version (for the hero subhead and social cards)

> Tibyaan reads the Spring backend you already run, proves which read operations are safe to
> expose, and serves them to AI agents, in place, without changing your code. Everything it
> cannot prove, it refuses, and tells you why. Across three real applications: 31 verified
> tools, zero wrong bindings.

(Note: every sentence of 3.1 to 3.4 is traceable: OVERVIEW.md for the framing, README for the
adoption mechanics and the survey shape, SECURITY.md for in-process execution, the ledgers for
the closing numbers. The phrase "every entry in it is right" is backed specifically by the
mis-bound 0 record and should always sit within one screen of the qualifier that coverage varies
by idiom.)

---

## 4. Getting started: the docs path a real adopter follows

One page, `/docs/getting-started`, in the honest order (mirrors README, which is itself verified
against the example app). Six stations:

**Station 0: are you in range?** The requirements gate FIRST, before any excitement: host app
Spring Boot 3.x on Java 17+, Maven build, MCP client that speaks HTTP+SSE (or a bridge). A
Boot 2 / Java 8 backend cannot host the runtime as-is, and the page says so before the visitor
invests anything. The CLI preview, however, needs only Java 17 and reads any Java source tree.
(Source: README "Requirements and compatibility".)

**Station 1: preview, read-only, zero risk.** Download the self-contained CLI jar from Maven
Central and run `java -jar tibyan-cli-0.1.0-jar-with-dependencies.jar scan /path/to/your-app`
(building it from a checkout remains the documented alternative). The page states plainly: this
reads source, writes nothing, touches no build file, and cannot break anything. It prints
discovered capabilities, every refusal with its located reason, and `mis-bound 0`. "If the
preview discovers nothing useful for your app, stop here: you have spent no build changes to
learn it." (Verbatim honesty from README Step 1.)
- The former open item is RESOLVED by measurement (A4, checked 2026-07-17): the shaded CLI jar
  `tibyan-cli-0.1.0-jar-with-dependencies.jar` is live on Maven Central (repo1.maven.org under
  io/github/tibyaan-org/tibyan-cli/0.1.0/), alongside all nine published modules. Claims sheet
  C-14 is upgraded to VERIFIED; decision D-7 is closed.

**Station 2: read your survey.** The most important docs content on the site: how to interpret
the output. What each refusal cause means in plain words (no backing method found; ambiguous,
two candidates and the tool will not pick; no usable identifier; file skipped at parse). The
framing repeated: refusals are normal and good; the number to watch is mis-bound, and it should
be 0. Mention `scan --report` for a shareable Markdown survey. (Sources: README "What you will
actually see"; Volume 11 survey contract via STATUS; the report feature entry in STATUS.)

**Station 3: adopt.** The two real pom.xml additions, verbatim from the README: the
`tibyan-runtime-spring` dependency and the `tibyan-maven-plugin` build plugin, then `mvn package`.
State what physically happens: the plugin generates `tibyan/capabilities.json` onto the classpath
at build time; nothing is committed by hand; the plugin is resilient by default (skips and names
unparseable files at WARN) with a strict opt-in for CI. The honest adoption line, stated as such:
it is a dependency AND a build step, not "just add a dependency". (Source: README Step 2 and the
build-safety note; STATUS build-plugin resilience entry.)

**Station 4: connect an agent.** Run the app as usual; the MCP endpoint serves at /sse. The curl
smoke test, the scripted check, and the stdio-bridge configuration for clients like Claude
Desktop, all from docs/CONNECTING-AN-MCP-CLIENT.md. State what is verified (an out-of-process
client completed initialize, tools/list, and a real tools/call over the network against the
example app) and what is not (the GUI wiring of any one specific desktop client, a step on the
adopter's machine). (Source: CONNECTING-AN-MCP-CLIENT.md via STATUS 2026-07-07 entry.)

**Station 5: before you expose anything.** At launch this is an on-page section, not a pointer
(per A5 the dedicated /docs/security page is post-launch backlog): the endpoint executes real
methods against real data, v1 adds no auth of its own, gate it like an unauthenticated admin
console, with a link to the repository's SECURITY.md as the authoritative text. This station is
deliberately part of getting started, not an appendix. (Source: SECURITY.md.)

**Deep docs.** The 17 volumes and the dated experiment ledgers stay in the GitHub repository, and
the site links to them by name rather than mirroring them. Reason: the volumes are the living
design record, they change with the project, and a mirrored copy WILL drift into dishonesty.
The site carries only the stable adopter-facing layer (getting started, security, evidence).

---

## 5. Design and tech plan

### 5.1 How the theme maps to the site

The Heritage Modern system transfers almost one-to-one; the discipline is already the brand's.

| Site element | Theme decision |
|---|---|
| Page background | Limestone #F4EEE3; cards and panels on Surface #FBF7EF with 1px #E4DAC8 borders |
| Headlines | Plex Serif Medium, tight leading, the identity's H1/H2 scale |
| Body | Plex Sans 16/1.6, muted secondary text #756B5C |
| Eyebrows, labels, metrics | Plex Mono, 11 to 13px, letterspaced uppercase, the mono-number section pattern (01, 02, 03) |
| The accent | Sienna #A6492E, one emphasis per view (the identity's own rule). On the landing page the sienna emphasis budget goes to refusal-related highlights, because the refusals are the differentiator |
| Terminal and survey cards | Ink #211D17 body, #E9D9C8 text, #D98A6A accents, #7FAF7A success marks: exactly the prototype's demo-card pattern, now carrying real recorded output |
| Dark sections | The closing CTA and "what it does not do" can use the Ink full-bleed pattern from the prototypes' access section |
| Logo and favicon | The bilingual ligature mark; solid dots below 16px per the identity's guidance |
| Iconography | Line icons, 1.75px stroke, 24px grid, sparingly |
| Motion | CSS-only: fade plus small translate, ease-out 200 to 320ms, one element at a time, full prefers-reduced-motion support (already in the prototypes) |
| OG and social images | Reuse the identity's social template patterns (Ink or Surface cards with the mark and a serif claim) |
| RTL/Arabic | Not built in phase 1, but no decision may block it: logical CSS properties (margin-inline, padding-inline) from day one, and the type stack keeps Plex Sans Arabic reserved |
| Color scheme | LIGHT ONLY at launch (decision D-8, per owner note N2). The identity defines exactly one palette: limestone-based light, with Ink sections as its designed dark moments. No improvised dark mode: `color-scheme: light`, `theme-color` limestone, and `prefers-color-scheme` deliberately not honored until the design system defines a dark palette on purpose |

Layout patterns lifted from the prototypes as-is: sticky blurred nav, hero with eyebrow pill,
trust band, 1px-gap card grids, the two-column light-versus-Ink contrast block (repurposed
honestly: "expose by guessing" versus "expose by proof" or "by hand" versus "generated and
regenerated"), the specs table (repurposed for the requirements strip), the dark CTA, the
bilingual footer.

What is deliberately NOT lifted: the interactive URL demo (fabricates output), the waitlist form
("private beta" is false; the project is published), and every word of copy.

### 5.2 Tech stack

- **Next.js, current stable (15.x), App Router, TypeScript.** As requested. App Router because
  the site is content-first and server components render it to plain HTML with near-zero client
  JS.
- **Static export** (`output: 'export'`). This is a marketing-plus-docs site with no server-side
  behavior: no auth, no forms that need a backend (the CTA is mailto and GitHub), no
  personalization. A static export deploys anywhere (GitHub Pages under the Tibyaan-org account
  is the zero-cost default; Vercel or Netlify equally fine), cannot go down in interesting ways,
  and keeps hosting cost at zero. If a form or search ever becomes necessary, the export flag is
  the only thing that changes.
- **Content in MDX** for the docs pages (getting-started, security, evidence) via @next/mdx, so
  content edits are prose edits, reviewable in a diff, and never require touching components.
  Landing-page copy lives in typed content modules rather than being buried in JSX.
- **Styling: Tailwind CSS (v4)** with the Heritage Modern palette and type scale declared once as
  CSS custom properties / theme tokens (the table in 5.1 becomes the theme file). No component
  library: the design system is specific enough that shadcn or similar would fight it.
- **Fonts via next/font/google**: IBM Plex Serif, Plex Sans, Plex Mono (Plex Sans Arabic added in
  the AR phase), self-hosted at build time by next/font, no layout shift, no runtime Google
  request.
- **Code and terminal rendering: Shiki** at build time (static-export friendly, zero client JS)
  with a custom theme built from the Ink/code tokens.
- **Client JS budget: zero at launch** (per A1). The survey and artifact cards are static
  verbatim text rendered at build time: no replay, no typewriter, nothing animated beyond the
  CSS fade-and-settle the design system allows. If a later page genuinely needs a client
  component, it is added then, and this line is updated then.
- **CI honesty checks wired into the site build**: (1) a link check over every internal link and
  every cited ledger link; (2) a Central check that fetches every Maven coordinate URL the site
  prints (the runtime, the build plugin, the CLI jar) and fails the build on any non-200, so
  coordinates that 404 can never ship (A4); (3) the anti-claims grep from section 7; (4) an
  em-dash scan (CLAUDE.md rule 9 applies to the site).
- **Analytics: none by default.** If wanted later, a privacy-respecting counter (owner decision;
  the audience notices trackers and holds it against you).
- **Repository placement: owner decision** (section 9): a `website/` directory in the main repo
  (keeps claims and ledgers in the same tree the site cites, one PR can update both) versus a
  separate repo under Tibyaan-org. Recommendation: `website/` in-repo, excluded from the Maven
  reactor, so the honest-claims sheet and the ledgers it cites live and move together.

### 5.3 Maintainability rule for claims

Every number on the site (31, 25, 2, 4, 111, 169, mis-bound 0, version 0.1.0) appears in exactly
one place in the code: a single `claims.ts` (or claims.mdx data file) with a source-comment per
entry pointing at the ledger that proves it. Pages import from it. When a new benchmark run
changes a number, one file changes, and the honest-claims sheet (section 8) is the review
checklist for that file. This makes overselling a merge-visible diff instead of a drift.

The same mechanism extends to displayed output (constraint 5): the survey excerpt and the
generated snippets live as checked-in verbatim text files with a provenance header (source path,
generating command, date), and pages include those files; nobody ever retypes tool output into
JSX.

---

## 6. Voice and copy rules for the site

Derived from the brand identity's voice ("plain, exact, confident; explain before impress; never
inflate; short sentences; real nouns") plus the project's own documentation discipline:

1. Every claim carries its qualifier in the same breath, not in a footnote.
2. "Proven on three apps" is always written as what it is; never "battle-tested", never "any app".
3. Refusal language is positive and specific ("refused: two candidate methods; the tool will not
   pick") and never apologized for.
4. No em dashes (CLAUDE.md rule 9 applies to the site too). Commas and colons.
5. No superlatives without a measurement behind them. "The only", "the first", "the best" are
   banned outright: we have not verified any of them.
6. Future work is always labeled as direction, gated on evidence, never "coming soon".
7. The private benchmark is described only as "a real multi-module enterprise Spring application"
   with counts and shapes. This rule has no exceptions, including in alt text, OG descriptions,
   file names, and commit messages.
8. Elision in displayed output compresses only in the direction that understates: refusals are
   never elided, elision markers are visible in the text itself, and the Totals line is never
   cut (A1).
9. Nothing animates a fabrication: anything that looks like tool output is verbatim from a real
   recorded run or a really generated file, with a provenance caption (constraint 5).

---

## 7. What the site must never say (the anti-claims list)

Each of these is plausible marketing language that the docs do NOT support. Listed so review can
grep for the pattern, not just the sentence.

| Banned claim | Why |
|---|---|
| "Works on any app" / "any Spring app" | Coverage is idiom-bound; pms discovered 2 of 43. The docs actively disprove "any" |
| "Full coverage" / "your whole backend" | The structural ceiling is a first-class fact; the filtered majority is refused |
| "Just add a dependency" | The honest line is a dependency AND a build plugin AND a build. Volume 1's "one dependency" goal line is vision, not the present mechanic |
| "In minutes" as a promise | No timing was ever measured or claimed in the docs |
| "Secure by default" / "enterprise-ready security" | SECURITY.md says v1 has no auth surface and gating is the operator's job |
| "AI-operable" as a present-tense description of v1 | Volume 1 defines operable as read, act, and workflows; v1 is read-only. Present tense must say "readable/queryable by agents"; "operable" is the vision, labeled as such |
| "Battle-tested" / "production-proven" | Three benchmark apps, zero external adopters on record. Say "verified on three real applications" and no more |
| "Works with any MCP client" | Transport is HTTP+SSE; stdio clients need a bridge. Say "any client that speaks HTTP+SSE, or a bridge" |
| "Self-healing", "adapts automatically", "understands your business" | Other products' language; structurally false here and, worse, the exact opposite of the pitch |
| Unqualified "never wrong" | The proven statement is "zero wrong bindings across every run on three benchmarks"; keep the scope attached |

---

## 8. Honest-claims sheet (the review artifact)

Every factual claim the site will make. Confidence legend: VERIFIED (measured and recorded in a
dated doc), DOCUMENTED (stated in the docs as design fact), FLAGGED (compelling but needs owner
decision or verification before it may appear).

| # | Claim (site wording) | Source | Confidence |
|---|---|---|---|
| C-1 | Tibyaan reads a Spring application's source and exposes only read operations it can prove are backed by a specific real method | README "The idea"; Volume 5 rules via STATUS | DOCUMENTED |
| C-2 | Everything it cannot prove is refused with a located reason | README; CLAUDE.md fail-loud discipline; the ledgers' refusal lists | VERIFIED |
| C-3 | The original application is never modified; the tool only reads source | Volume 1 mission commitment 2; README | DOCUMENTED |
| C-4 | 31 verified capabilities across three real, unmodified applications; zero wrong bindings on every run | third-app-trial.md (25), second-app-trial.md (2), enterprise-benchmark.md (4); STATUS | VERIFIED |
| C-5 | ERP benchmark: 25 entities, 25 capabilities (16 by-id, 9 list), mis-bound 0 | third-app-trial.md totals row | VERIFIED |
| C-6 | PMS benchmark: 43 entities, 2 capabilities, most reads refused by name, mis-bound 0 | second-app-trial.md | VERIFIED |
| C-7 | Validated on a real multi-module enterprise Spring application: 169 modules, 111 entities surveyed, 4 verified capabilities, mis-bound 0 (never named) | enterprise-benchmark.md (redacted ledger) | VERIFIED, redaction absolute |
| C-8 | Coverage is idiom-bound and does not fully compound; your number depends on your idioms; the guarantee that transfers is zero wrong bindings | third-app-trial.md section 7 (transfer verdict); README evidence qualifier | VERIFIED |
| C-9 | Works on multi-module applications that legitimately reuse entity names across bounded contexts | STATUS Entity Identity entry (BLOCKED to EMITTED, 111 entities, 6 colliders disambiguated) | VERIFIED |
| C-10 | One verified model feeds two outputs today: an MCP server and an OpenAPI 3.0 document | STATUS OpenAPI backend entry (both benchmark IRs render valid OpenAPI) | VERIFIED |
| C-11 | Generation is deterministic: same source in, same files out, regenerated by your own build | CLAUDE.md rule 10; golden-file tests; STATUS build-time generation entry | DOCUMENTED |
| C-12 | v1 is read-only by design; writes are refused at discovery, not merely disabled | SECURITY.md | DOCUMENTED |
| C-13 | v1 adds no authentication of its own; gating the endpoint is the operator's responsibility | SECURITY.md | DOCUMENTED |
| C-14 | The preview (CLI scan) is read-only, requires only Java 17, changes nothing, and the self-contained CLI jar is downloadable from Maven Central | README Step 1 and requirements; repo1.maven.org listing of tibyan-cli/0.1.0 shows tibyan-cli-0.1.0-jar-with-dependencies.jar, checked 2026-07-17 | VERIFIED (A4) |
| C-15 | Adoption is two pom.xml additions (runtime dependency plus build plugin) and a build | README Step 2, verified against the example app | VERIFIED |
| C-16 | The build plugin is resilient by default (skips and names unparseable files) with a strict opt-in | STATUS build-plugin resilience entry | VERIFIED |
| C-17 | An out-of-process MCP client has been verified end to end over the network (initialize, tools/list, tools/call returning real data) | CONNECTING-AN-MCP-CLIENT.md; STATUS 2026-07-07 entry | VERIFIED |
| C-18 | Host requirements: Spring Boot 3.x on Java 17+, Maven; MCP transport is HTTP+SSE and stdio clients need a bridge | README "Requirements and compatibility" | DOCUMENTED |
| C-19 | Published: version 0.1.0 on Maven Central under io.github.tibyaan-org (all nine modules, including the runtime, the build plugin, and the CLI), Apache-2.0, CI green | STATUS publish entry 2026-07-08; LICENSE; re-verified against repo1.maven.org 2026-07-17; kept honest permanently by the site build's Central check (5.2) | VERIFIED |
| C-20 | Refused today, by design: filtered reads, writes and actions, custom result-set wrappers, non-Spring stacks | README "What it does not do yet"; Volume 13 ceiling | DOCUMENTED |
| C-21 | The path beyond the ceiling is designed and evidence-gated: human-attested capabilities, explicitly labeled, never guesses; built when real adopters hit the boundary | Volume 13 "Attestation" via STATUS | DOCUMENTED (must always be phrased as direction, not as "coming soon") |
| C-22 | Independent convergence (an unrelated company arrived at "small, typed, pre-verified tools over MCP" for the web) suggests the shape of the idea is sound | competitive-landscape.md section 4 | DOCUMENTED. Placement resolved for launch (A5): not on the launch site at all; the named analysis stays in the repo, and decision D-3 is deferred until the post-launch /landscape page is scheduled |
| C-23 | The name: tibyān, classical Arabic for clear exposition, making something evident | Brand identity cover; README name note | DOCUMENTED (brand fact, not a tech claim) |
| C-24 | "Every number on this page links to the dated experiment record that produced it" | The ledgers are public in the repo | VERIFIED once links are wired; the site build fails a link check otherwise (5.2) |
| C-25 | The survey shown on the site is the recorded ERP benchmark output, verbatim and static, with elisions marked inside the text, refusals shown in full, and the Totals line uncut | third-app-trial.md; constraint 5; copy rule 8 | VERIFIED at authoring (pasted from the ledger and reviewed against it) |
| C-26 | Generated-output snippets are pasted verbatim from files the tool really generated, only for the in-repo example app or capabilities already named in the public ledgers, each with a provenance caption naming the generating command and date | the generated MCP manifest and OpenAPI document; STATUS E2-view, E4, and OpenAPI entries name the fields shown (`view`, `outputMultiple`, `x-tibyan-partial`) | VERIFIED at build (copied from real output, never retyped) |

Flagged-and-cut claims (compelling, NOT usable):

| # | Tempting claim | Why it is cut or softened |
|---|---|---|
| X-1 | "Add one dependency and your system talks to AI" (Volume 1 value line) | Vision phrasing; the real mechanic includes the build plugin and a build step. Use the honest two-additions line (C-15) |
| X-2 | "AI-operable platform" for v1 | Operable includes actions and workflows (Volume 1 section 1); v1 is read-only. Reserve "operable" for the labeled vision section, use "agents can query it safely" for the present |
| X-3 | Any uptime, latency, or "minutes to running" figure | Never measured in the docs |
| X-4 | "Trusted by [enterprise]" or naming the private benchmark's industry | Redaction is absolute; even the industry is identifying detail |
| X-5 | "27 capabilities" (the README headline) | Stale by one benchmark: the current supported total is 31 across three apps (C-4). Site uses 31; README update is a separate task |
| X-6 | "Never wrong" unscoped | Always scope to bindings and to the measured record: "zero wrong bindings across every run on three real applications" |

---

## 9. Open decisions for the owner (recorded, not blocking the plan)

- **D-1, product name spelling on the site.** Recommendation: Tibyaan (matches org, groupId,
  README canon); "tibyan" appears only inside real code snippets. The brand assets' "Tibyan"
  wordmark would then need its site rendering updated to the long spelling (the ligature mark is
  unaffected; it is script, not spelling).
- **D-2, domain and contact address.** The prototypes use tibyan.dev and hello@tibyan.dev, which
  may not be owned and do not match the org spelling. Candidates: a tibyaan.* domain or the free
  tibyaan-org.github.io. Contact today is the maintainer address in SECURITY.md; a project
  address is nicer but must actually exist before it is printed.
- **D-3, competitor naming on the site.** DEFERRED (A5): the landscape page left the launch
  scope, so no decision is needed until it is scheduled. Standing recommendation unchanged:
  category-level on the site, the named analysis stays in the repo.
- **D-4, site placement.** Recommendation: `website/` directory in the main repo so claims and
  ledgers move together. Alternative: separate repo under Tibyaan-org.
- **D-5, Arabic version timing.** The design system is bilingual-native. Recommendation: EN now,
  AR as its own later phase with real translated copy (not machine-translated), keeping logical
  CSS properties from day one so RTL is a content job, not a refactor.
- **D-6, analytics.** Recommendation: none.
- **D-7, CLI distribution for Station 1.** RESOLVED 2026-07-17 (A4): the shaded CLI jar is live
  on Maven Central (io/github/tibyaan-org/tibyan-cli/0.1.0/, file
  tibyan-cli-0.1.0-jar-with-dependencies.jar, verified via repo1.maven.org). Station 1 says
  "download the CLI", and the site build's Central check (5.2) keeps that claim honest
  permanently.
- **D-8, color scheme.** RESOLVED (owner note N2): light-only at launch. The design system
  defines one palette; the Ink sections are its designed dark moments; no improvised dark mode.
  A dark theme happens only if the design system later defines one deliberately.

Launch defaults for the still-open decisions, applied unless the owner overrides before the
build: D-1, the site says Tibyaan, with "tibyan" only inside real code snippets; D-5, English
only; D-6, no analytics. D-2 (domain and contact) still needs an owner action, because an
address must exist before it is printed; the build can proceed on the GitHub Pages default and
swap in a domain at DNS time without content changes.

---

## 10. What happens after approval (the build, NOT this session)

Stage one, one later session, scoped like any phase: scaffold the Next.js app per section 5 and
implement the THREE launch pages (/, /how-it-works, /docs/getting-started) from the approved
copy, with the real theme tokens, the claims file (5.3) as the single source of every number,
the verbatim survey and artifact snippets copied from really generated output (constraint 5),
and the CI honesty checks (links, Central coordinates, anti-claims grep, em-dash scan) wired
into the site build. Review the built pages against section 7 and section 8 line by line before
anything ships.

Stage two, after the launch trio is live and true: the backlog pages (2.3 evidence, 2.5
security, 2.6 landscape pending D-3), each reviewed against the claims sheet the same way.

The plan is the ratified design; the build implements it, per the project's own rule 12 spirit.
