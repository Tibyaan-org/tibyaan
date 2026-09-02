# Releasing Tibyan

This document records the release decisions for v1 and the exact steps to publish. It is written so
that the parts a machine cannot do (accounts, key ceremonies, external verification) are enumerated as
human steps, never faked. Everything preparable in the repository is prepared and verifiable locally.

Release history, all under groupId `io.github.tibyaan-org`: `0.1.0` was published to Maven Central on
2026-07-08 (tag `v0.1.0`, commit f01d390) and `0.2.0` on 2026-07-17 (tag `v0.2.0`, commit ff22554).
`0.3.0` is PREPARED BUT NOT PUBLISHED: the version is set across the reactor at commit 1528cab and both
release gates are green, but no artifacts were uploaded, `0.2.0` remains the latest version on Central,
and no `v0.3.0` tag exists. The tag goes on only after the deploy succeeds, per section 3 step 5.
Per-release contents are in [CHANGELOG.md](../CHANGELOG.md).

The publishing machinery is proven, not theoretical: the namespace is verified, the GPG key
(keyid E0AED2AC2B744C03) is reusable, the reactor carries full Central-required POM metadata and a
`release` profile, and `mvn -Prelease verify` is green locally without any signing keys. Only a fresh
Central token is needed per release. The steps a machine cannot do are section 4.

A note on the version literals below: this document writes `<release>` for the version being cut and
`<next>` for the development version restored after it, rather than naming concrete versions. That is
deliberate. An earlier revision hardcoded them, and the 0.2.0 release followed step 6 literally and
restored `0.2.0-SNAPSHOT`, a development version that sorts BEHIND the release which had just shipped.
Keep these as placeholders.

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

The latest PUBLISHED release is `0.2.0`; `0.3.0` is prepared but unpublished, and the development tree
is `0.4.0-SNAPSHOT`. The scheme is
semantic-versioning-shaped, with the pre-1.0 caveat stated
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

One version string is not carried by the POM: the CLI reports its version through a hardcoded
`TOOL_VERSION` constant (`tibyan-cli/src/main/java/com/tibyaan/cli/TibyanCli.java`), which drives
`tibyan version` and `tibyan --version`. Keep it in step with the POM version on every bump, the release
and the snapshot restore alike (section 3 step 3, and section 4).

Post-release, the reactor returns to a snapshot development version (section 4, step 6).

## 3. The release checklist

In order. Steps 1 to 3 are verifiable in this repository with no external accounts or keys; steps 4 and
on are the human steps in section 4.

1. Confirm the tree is clean and the full reactor is green: `mvn clean verify`. This runs the unit,
   golden, and end-to-end tiers.
2. Confirm the version is the intended release version (no `-SNAPSHOT`): the aggregator and every module
   read `<release>`, and the CLI's `TOOL_VERSION` constant reads `<release>` too.
3. Confirm the release artifacts build without keys: `mvn -Prelease verify`. This produces the main,
   `-sources`, and `-javadoc` jars for every published module (and no `-sources`/`-javadoc` jars for the
   two non-published modules, the example app and the integration-test harness) and runs the full test
   suite; GPG signing is skipped by default (`gpg.skip=true`) so this passes with no key material. The
   signing configuration itself is proven separately by running the same profile with `-Dgpg.skip=false`
   against a throwaway key, which produces real `.asc` detached signatures; the real release uses the
   publisher's key in exactly the same way.
4. Publish (human, section 4): sign and deploy to Maven Central, then release the staging deployment.
5. Tag the release: `git tag -a v<release> -m "Tibyan <release>"` and push the tag.
6. Verify the published artifacts resolve from Central in a scratch project (the quickstart in the root
   README is the acceptance check).
7. Restore the development version: `mvn versions:set -DnewVersion=<next>-SNAPSHOT -DgenerateBackupPoms=false`,
   bump the CLI `TOOL_VERSION` constant to match, and commit. `<next>` MUST SORT ABOVE `<release>`: after
   cutting `0.3.0` the development version is `0.4.0-SNAPSHOT`, or `0.3.1-SNAPSHOT` if a patch is genuinely
   the next thing intended, never `0.3.0-SNAPSHOT`. A snapshot equal to a released version sorts BELOW that
   release in Maven's ordering, so the tree would claim to be older than an artifact already on Central.

## 4. Human-required steps (accounts, keys, verification): what only a person can do

The published coordinates are groupId `io.github.tibyaan-org`, version `<release>`. The Java package root
stays `com.tibyaan` regardless: that is a source convention (CLAUDE.md rule 8), independent of the Maven
coordinates, and it does not change. None of the steps below can be done inside an automated session;
each is written as an exact step, in order. They are the only work between the current commit and the
artifacts being publicly consumable. Steps marked ONE-TIME are done once for the project; steps marked
PER-RELEASE are repeated for every version.

What the preparation session already did, so it is not repeated below: set the groupId to
`io.github.tibyaan-org` across the reactor and confirmed the reactor builds green; confirmed
`mvn -Prelease verify` produces the main, `-sources`, and `-javadoc` jars for all nine published modules
and no `-sources`/`-javadoc` jars for the two non-published modules; and proved the GPG signing step
runs by signing a published module with a throwaway key (real `.asc` files were produced, then discarded
with the key). Only the real key, the accounts, and the external verification remain.

1. (ONE-TIME) Verify the namespace `io.github.tibyaan-org` on the Sonatype Central Portal (it currently
   shows Verification Pending). An `io.github` namespace is verified by proving control of the GitHub
   account named by the segment right after `io.github`, here the user account `tibyaan`, so no domain
   and no DNS record is needed. The code repository stays under the `Tibyaan-org` organization; Central
   does not require the publishing account and the code host to be the same account.
   a. Sign in to https://central.sonatype.com as the owner of the `tibyaan` GitHub account.
   b. Under Namespaces, open the pending `io.github.tibyaan-org` entry to see its verification code.
   c. Do exactly what the portal instructs for that code: create the public GitHub repository it names,
      named with the verification code, under the account it names (the `tibyaan` account), then click
      Verify.
   d. Once verified, the temporary repository can be deleted; the namespace stays verified permanently.

2. (ONE-TIME) Create the Central publishing token. At https://central.sonatype.com, under your account,
   generate a user token (a username and password pair). Add it to `~/.m2/settings.xml` under a server
   id of `central` (the id the `central-publishing-maven-plugin` references in the root POM). Do not
   commit `settings.xml`; it holds the token.

   ```xml
   <server>
     <id>central</id>
     <username>CENTRAL_TOKEN_USERNAME</username>
     <password>CENTRAL_TOKEN_PASSWORD</password>
   </server>
   ```

3. (ONE-TIME) Generate and distribute a GPG signing key.
   a. Generate a key pair: `gpg --full-generate-key` (or `gpg --gen-key`).
   b. Publish the public key to a keyserver Central checks, for example
      `gpg --keyserver keys.openpgp.org --send-keys <KEYID>`.
   c. Keep the private key and its passphrase secure. Provide the passphrase to `maven-gpg-plugin`
      through gpg-agent, or a `settings.xml` property, never a committed file. The signing wiring in the
      `release` profile is already proven to work (the preparation session signed a module with a
      throwaway key); this real key simply replaces it.

4. (PER-RELEASE) Deploy. From a clean checkout at the release version, run:

   ```
   mvn -Prelease -Dgpg.skip=false clean deploy
   ```

   This builds, attaches the sources and javadoc jars, signs every published artifact, and uploads the
   bundle to the Central staging area. `autoPublish` is `false` in the plugin (the current setting), so
   review the deployment and release it manually in the Central Portal UI; set `autoPublish` to `true`
   only if you want it to publish without that manual gate.

5. (PER-RELEASE) Tag and verify. `git tag -a v<release> -m "Tibyan <release>"` and push the tag. Then confirm
   the published artifacts resolve from Central in a scratch project (the quickstart in the root README
   is the acceptance check).

6. (PER-RELEASE) Restore the development version. Run
   `mvn versions:set -DnewVersion=<next>-SNAPSHOT -DgenerateBackupPoms=false`, bump the CLI's hardcoded
   `TOOL_VERSION` constant (`tibyan-cli/src/main/java/com/tibyaan/cli/TibyanCli.java`) to
   `<next>-SNAPSHOT` to match, and commit. The constant is hardcoded because the CLI reports it through
   picocli, so it does not move with the POM automatically; keep the two in step on every bump.

   `<next>` MUST SORT ABOVE the version just released. This step is where the 0.2.0 release went wrong: it
   restored `0.2.0-SNAPSHOT` after publishing `0.2.0`, so for 62 commits the development tree carried a
   version Maven orders BELOW an artifact that was already public. Pick the next minor (`0.4.0-SNAPSHOT`
   after `0.3.0`) unless a patch release is genuinely the next thing intended.

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
