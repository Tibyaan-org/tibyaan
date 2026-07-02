# Releasing Tibyan

This document records the release decisions for v1 and the exact steps to publish. It is written so
that the parts a machine cannot do (accounts, key ceremonies, external verification) are enumerated as
human steps, never faked. Everything preparable in the repository is prepared and verifiable locally.

Status at the time of writing: the reactor is at version `0.1.0` with full Central-required POM
metadata and a `release` profile, and `mvn -Prelease verify` is green locally without any signing keys.
The artifacts are NOT yet published. The remaining work is entirely the human steps in section 4.

## 1. License: Apache-2.0

The project is licensed under Apache-2.0 (see `LICENSE`, with a `NOTICE` file for attribution). The
choice is deliberate for a tool meant to be embedded in other people's builds: Apache-2.0 is permissive
enough that an adopter can add the starter and the Maven plugin to a proprietary application without
license friction, and it carries an explicit patent grant (section 3 of the license), which matters for
a platform that generates and executes code paths into a host system: both adopters and contributors get
a clear, irrevocable patent license, removing a class of legal ambiguity that a bare MIT license leaves
open. This posture fits the project's stance that MCP output is a commodity and the durable asset is the
semantic model: a permissive license invites the broad adoption that makes the model valuable, rather
than gating it.

Per-file source headers are not applied in this release. Apache-2.0 does not require them; the `LICENSE`
and `NOTICE` files at the repository root satisfy the license. Adding short per-file headers is a
recommended, purely cosmetic follow-up and is deliberately out of scope here to keep the release diff to
metadata and documentation.

## 2. Versioning discipline (pre-1.0)

The current version is `0.1.0`. The scheme is semantic-versioning-shaped, with the pre-1.0 caveat stated
plainly:

- Patch (`0.1.x`): documentation, build metadata, and bug fixes that change no discovery, backend,
  runtime, or CLI behavior. Safe to take.
- Minor (`0.x.0`): a new phase, a new discovery rule, IR growth, or any behavior change, including
  changes to what the survey discovers, the shape of the generated manifest or IR JSON, or the CLI
  output. PRE-1.0 MINOR BUMPS MAY BREAK. Read the changelog before upgrading; treat every minor bump as
  potentially breaking until 1.0.0.
- Major (`1.0.0`): the first stability commitment. It is not claimed yet. 1.0.0 will be cut only when the
  plugin API, the IR JSON schema, and the generated manifest format are ready to be held stable.

The compatibility boundary is the plugin API (`tibyan-plugin-api`, Volume 10 section 6). The IR JSON and
the runtime manifest formats are additive-where-possible but are not stable before 1.0.0.

Post-release, the reactor returns to a snapshot development version (section 3, step 7).

## 3. The release checklist

In order. Steps 1 to 3 are verifiable in this repository with no external accounts or keys; steps 4 and
on are the human steps in section 4.

1. Confirm the tree is clean and the full reactor is green: `mvn clean verify`. This runs the unit,
   golden, and end-to-end tiers.
2. Confirm the version is the intended release version (no `-SNAPSHOT`): the aggregator and every module
   read `0.1.0`.
3. Confirm the release artifacts build without keys: `mvn -Prelease verify`. This produces the main,
   `-sources`, and `-javadoc` jars for every published module and runs the full test suite; GPG signing
   is skipped by default (`gpg.skip=true`) so this passes with no key material.
4. Publish (human, section 4): sign and deploy to Maven Central, then release the staging repository.
5. Tag the release: `git tag -a v0.1.0 -m "Tibyan 0.1.0"` and push the tag.
6. Verify the published artifacts resolve from Central in a scratch project (the quickstart in the root
   README is the acceptance check).
7. Restore the development version: `mvn versions:set -DnewVersion=0.2.0-SNAPSHOT -DgenerateBackupPoms=false`
   and commit.

## 4. Human-required steps (accounts, keys, verification): what only a person can do

None of the following can be done inside an automated session; each is written as an exact step. They
are the only work between the current commit and the artifacts being publicly consumable.

1. Namespace verification on Sonatype Central. The groupId `com.tibyaan` must be verified before Central
   will accept it. Central verifies a reversed-domain namespace by proof of domain ownership: add the
   DNS TXT record Central provides to the `tibyaan.com` domain and complete verification at
   https://central.sonatype.com. IF the `tibyaan.com` domain is not owned, do not fake it: either
   acquire the domain, or switch the published groupId to `io.github.tibyaan-org` (which Central verifies
   through the GitHub organization of the same name) and update `<groupId>` in the root POM accordingly.
   The Java package root stays `com.tibyaan` regardless (that is a source convention, CLAUDE.md rule 8,
   independent of the Maven coordinates).
2. Create a Central publishing account and a user token at https://central.sonatype.com, and add the
   token to `~/.m2/settings.xml` under a server id of `central` (the id the `central-publishing-maven-plugin`
   references). Do not commit `settings.xml`; it holds the token.
3. Generate a GPG key pair for signing (`gpg --gen-key`), and publish the public key to a keyserver
   (for example `gpg --keyserver keys.openpgp.org --send-keys <KEYID>`). Keep the private key and its
   passphrase secure. Configure the passphrase for `maven-gpg-plugin` through gpg-agent or a
   `settings.xml` server/property; never place it in a committed file.
4. Run the release: `mvn -Prelease -Dgpg.skip=false clean deploy`. This builds and signs the jars and
   uploads them to the Central staging area. Then release the deployment (either automatically, if
   `autoPublish` is enabled in the plugin, or manually in the Central UI).
5. Complete steps 5 to 7 of the checklist (tag, verify from Central, restore the snapshot version).

Interim option, if Central onboarding is not yet complete and a consumable artifact is needed sooner:
publish to GitHub Packages from the `Tibyaan-org/tibyaan` repository. This is an EXPLICITLY INTERIM
target, not the canonical home: GitHub Packages requires every consumer to authenticate to GitHub even
for public packages, so it is a poor default for a tool meant to be added by strangers. Use it only as a
labeled stopgap and move to Central as soon as the namespace is verified.

## 5. Continuous integration

A GitHub Actions workflow (`.github/workflows/ci.yml`) runs `mvn -B clean verify` on push and pull
request against `main`, on Ubuntu with Temurin JDK 17. It runs the full reactor including the
end-to-end test (`tibyan-it`, `EndToEndMcpIT`), which boots a real Spring context and a genuine MCP
server on localhost.

Honest scope of what was verified: `mvn clean verify` is green locally this session, including the
end-to-end tier, and the workflow runs exactly that command in a standard runner. The workflow itself has
NOT been executed on GitHub Actions from this environment, so its first push is the human verification
step. The end-to-end test binds localhost only and needs no external services, so there is no reason to
expect it to behave differently on a clean runner; but if it proves environment-sensitive in CI, the
documented fallback is to scope the workflow to the unit and golden modules (`mvn -B -pl '!tibyan-it'
clean verify`) and keep the end-to-end test as a local-only gate. This is recorded rather than hidden: a
documented gap is preferred over a green-looking claim about a run that did not happen here.

On its first run, this workflow immediately earned its place by catching a real fresh-checkout defect that
every local build had masked: an unanchored `.gitignore` rule (`generated/`) had excluded a committed
golden fixture (`tibyan-backend-mcp/.../com/tibyaan/generated/mcp/GetProductTool.java`) from git, because
its path contains a `generated` segment. Local builds passed because the file was present on disk; a clean
clone failed because it was never tracked. The fix anchored the rule to the repository root (`/generated/`)
and committed the missing golden. This is exactly the value of running the build from a clean checkout,
and the reason CI is part of release engineering rather than an afterthought.
