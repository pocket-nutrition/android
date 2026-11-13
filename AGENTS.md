# AGENTS.md — Pocket Nutrition Android

## What this repo is

The native Android client. Kotlin + Compose, one `:app` module, currently scaffolding: it builds, signs on
a tag, and renders a token gallery. No meal builder yet.

## The rules that matter most here

- **`roundHalfUp`, never `kotlin.math.round`.** `kotlin.math.round` breaks ties towards even, so
  `round(12.5)` is 12. Every weight and portion label in this app goes through this rule, and getting it
  wrong makes the app disagree with the web client and iOS on values a user reaches by tapping "Demi" —
  with no error anywhere. See `core/Rounding.kt` and the `rounding` vector suite.
- **Never invent food data.** No label→cooking-method table, no label→portion-weight table, no default
  weight when the server omits one. The sandbox had exactly those tables and they were deleted for this
  reason. When the API supplies no portion or no eating state, the correct behaviour is to **skip the item
  and say so**, not to substitute a plausible number. A missing food is a gap to fill in
  `pocket-nutrition/knowledge`.
- **`measuredState` is load-bearing.** It records whether a quantity is a raw or a cooked weight. Get it
  wrong and every nutrition number is off by the yield factor. A recipe's component weight is always a
  pre-cooking weight.
- **`contract/` is vendored. Never hand-edit anything in it.** Change it upstream in
  `pocket-nutrition/contracts`, tag, bump `CONTRACTS_VERSION`, run `./scripts/sync-contracts.sh --update`.
  CI verifies this and fails on a hand edit.

## Signing

- `*.jks`, `*.keystore` and `key.properties` are gitignored. **Never commit one, never paste one into a
  file, never echo one into a log.** If you find one committed, treat it as a compromised key: it has to be
  replaced, not merely removed from the tree.
- Do not make the release build fail when the keystore is absent. Unsigned-with-a-warning is the designed
  behaviour, and the release job verifies with `apksigner` and reports which artefact it produced.
- Only the maintainer generates the keystore. An agent must never create one, because the passwords would
  then exist in a transcript.

## Allowed without approval

- Adding screens, tests, or dependencies already in the version catalogue.
- Porting a function from the sandbox **together with** its vector-suite assertions.
- Bumping `CONTRACTS_VERSION` and re-running `--update` for an additive contracts release.

## Requires approval

- **Adding a `<uses-permission>`.** There are none today. Add one only in the same change as the code that
  needs it, with the reason stated.
- **Raising `minSdk`** (drops users) or **lowering it** (loses `java.time`).
- **Relaxing `allWarningsAsErrors` or lint `warningsAsErrors`**, or disabling a lint check.
- **A major `CONTRACTS_VERSION` bump**, which means a breaking contract change.
- Adding a dependency not in `gradle/libs.versions.toml`, or introducing a second module.

## Do not copy code from GPL-licensed reference implementations

If you consult a GPL-3.0 project implementing a similar feature set for inspiration, treat it as a
**behavioural specification only**.

Permitted: reading it, describing what it does in prose, and taking published scientific facts (BMR/TDEE
equations) and public-database nutrient lists.

Forbidden: copying or translating its source, strings, assets or schemas. Translating its code to
another language is a derivative work.

This repo is **MPL-2.0** (client tier — see README). GPL-3.0 cannot be relicensed as MPL-2.0, so
copied code would force the surrounding work to GPL-3.0, leave this repo carrying two incompatible
copyleft licences at once, and re-impose the GPL §10 terms that make App Store distribution
non-viable. Read it; do not copy it.

Same rule for the backend repos: they are AGPL-3.0-or-later, and AGPL code must not be copied
*into* this repo — it ships inside a binary. Copying the other direction is fine.

## Toolchain notes (read before bumping anything)

The repo is on AGP 9.3 / Gradle 9.6 / Kotlin 2.4 / compileSdk 37, which was reached by migration rather
than chosen freely. Four things bit, in this order, and they will bite again on the next major bump:

1. **AGP 9 provides Kotlin itself.** Applying `org.jetbrains.kotlin.android` alongside it fails with
   *"Cannot add extension with name 'kotlin'"*. That plugin is deliberately absent from both build
   scripts; `kotlin-compose` and `kotlin-serialization` are still applied normally.
2. **AGP 9 stopped compiling Kotlin found in a `java` source directory.** The vendored tokens must be
   registered on the `kotlin` container — `kotlin.directories`, not `java.srcDirs`. Under AGP 8 the java
   entry worked, so this looks redundant and is not: remove it and `PnColor` stops resolving.
3. **`srcDir`/`srcDirs` are deprecated** in favour of the `directories` mutable set. With
   `allWarningsAsErrors` that is a build failure, not a warning.
4. **Current AndroidX dictates `compileSdk`.** `androidx.core:core:1.19.0` requires 37; the failure
   arrives as `checkDebugAarMetadata`, which reads like a dependency problem and is really an SDK
   problem. `.ci/Dockerfile` must be bumped to match, or CI fails where local builds pass.

The three version-currency lint checks are disabled — see the comment in `app/build.gradle.kts`. They
are time- and network-dependent, so with `warningsAsErrors` they would fail CI on a day nobody committed
anything. **This is not licence to let dependencies rot:** every version was the newest available when
last verified, with lint reporting zero issues, and a bump should aim to restore that state rather than
settle for "still builds".

## CI notes

CI lives in `.github/workflows/ci.yml` and runs on GitHub-hosted Ubuntu runners: the SDK is installed by
`.github/actions/setup-android`, with the same pinned versions as `.ci/Dockerfile`, so the Docker image is
only for reproducing a build locally. If you point a container-based runner at this repo instead, note that
Node is required in the build image even though nothing here is JavaScript: JS-based actions
(`actions/checkout`, `actions/upload-artifact`) run *inside* the job container, and without a `node` binary
those steps fail at checkout with `exec: "node": executable file not found` (exit 127).

## Environment notes

The Android SDK is not required on the host: `.ci/Dockerfile` pins a known-good JDK + SDK image, so

```bash
docker build -f .ci/Dockerfile -t pn-ci-android:local .
docker run --rm -v "$PWD":/w -w /w pn-ci-android:local ./gradlew testDebugUnitTest
```

reproduces a clean-environment build without installing anything locally. On Apple Silicon this needs
an amd64-capable Docker (Rosetta or qemu), because Google publishes the Linux Android build-tools for
x86_64 only.
