# Contributing to Tibyan

Contributions are welcome. Tibyan is built under a small set of disciplines that are not negotiable,
because they are the reason the tool can claim zero wrong bindings. Read them before you open a pull
request.

## The disciplines

- **Design before code.** A new discovery rule, a change to the Capability model (the IR), or any change
  to what the tool exposes is designed and written into the relevant volume under `docs/`, and ratified,
  before it is implemented. The design commit precedes the build commit. This keeps the build implementing
  the ratified rule rather than the rule drifting to fit the build.
- **Fail loud, never guess.** The tool exposes a capability only when it can prove structurally which real
  method backs it. If a rule does not decide a case unambiguously, the tool refuses with a located error.
  It never falls back to a convention or emits a plausible binding. A change that would make the tool guess
  is rejected on principle, whatever its coverage benefit.
- **Benchmark discipline.** Behavior is measured against two real Spring applications, not asserted. The
  health metric is one line: discovered rising while `mis-bound` stays exactly 0. A change that produces a
  single wrong binding on either benchmark stops everything until the cause is found. The benchmark targets
  are private codebases; the published evidence is the dated ledgers in `docs/experiments/`.
- **Determinism.** Generation is deterministic and covered by golden-file tests: the same input yields the
  same output, byte for byte. A change to generated output is a visible, reviewed diff.
- **The core stays dependency-free.** `tibyan-core` (the Capability model and its validation) has zero
  external runtime dependencies. Not Spring, not JavaParser, not an MCP library, not Jackson.
- **No em dashes anywhere.** In code, comments, documentation, commit messages, and output. Use commas or
  colons.

## The architecture rules

The full set of rules that protect the architecture (the five abstractions, the dependency directions, the
one-plugin-per-module boundary, the package root) is in [CLAUDE.md](CLAUDE.md). It is the build brief the
project follows; read it before changing module structure or adding a dependency.

## Practical

- Build and test the whole reactor: `mvn clean verify`. It must be green, including the end-to-end test.
- Java 17, Maven, trunk-based (work commits to `main`, no per-phase branches).
- Keep commits small and buildable, with a clear message. End release-preparation and mechanics work with
  the same discipline as feature work.

The deep documentation, and the reasoning behind every decision, is the 17 volumes under
[docs/](docs/README.md), starting from the hub.
