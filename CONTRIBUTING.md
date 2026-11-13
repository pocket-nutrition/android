# Contributing to Pocket Nutrition Android

Thanks for looking at this repo. It is the native Android client of the Pocket Nutrition platform —
see `README.md` for what it does and `AGENTS.md` for the rules an automated agent (or a human moving
fast) needs to know before touching it. This file is the human-facing "how do I build this and send a
change" companion to those two.

## Prerequisites

- JDK 17+ (the CI image uses JDK 21).
- Android SDK with `platforms;android-37` and a recent `build-tools` — Android Studio installs both,
  or use `sdkmanager` directly.
- No `ANDROID_HOME`/SDK on your machine? Use the pinned Docker image instead (see below) — it is the
  exact environment CI builds in, so a result there reproduces a CI result exactly.

There is no `setup.sh`: the Gradle wrapper (`./gradlew`) is the bootstrap. It downloads the pinned
Gradle version on first run; nothing else needs installing beyond a JDK and the Android SDK.

## Building and testing

```bash
./gradlew testDebugUnitTest   # unit tests, including the shared behavioural corpus
./gradlew assembleDebug       # debug APK
./gradlew lintDebug           # lint — warnings are errors, so a clean run means zero warnings
```

`allWarningsAsErrors` (Kotlin) and `warningsAsErrors` (lint) are both on. Don't relax either to get a
change to compile — see `AGENTS.md` for why that needs approval.

### Reproducing CI locally

```bash
docker build -f .ci/Dockerfile -t pn-ci-android:local .
docker run --rm -v "$PWD":/w -w /w pn-ci-android:local ./gradlew testDebugUnitTest
```

On Apple Silicon this needs an amd64-capable Docker (Rosetta or qemu) — Google only publishes Linux
Android build-tools for x86_64.

## The `contract/` directory

`contract/` is **vendored, generated content from `pocket-nutrition/contracts`** — the repository that
also feeds the iOS client and the sandbox. It contains the generated design-token Kotlin
(`contract/kotlin/Tokens.kt`) and the shared behavioural test corpus (`contract/data/vectors/*.json`,
`contract/data/strings/fr.json`), pinned to the version recorded in `CONTRACTS_VERSION`.

**Never hand-edit anything under `contract/`.** If a design token or a behavioural vector is wrong or
missing, fix it upstream in `pocket-nutrition/contracts`, tag a new contracts release, bump
`CONTRACTS_VERSION` here, and re-vendor:

```bash
./scripts/sync-contracts.sh --update    # refreshes contract/ from the pinned CONTRACTS_VERSION
./scripts/sync-contracts.sh --verify    # what CI runs — fails the build on drift
```

This exists as vendored files rather than a git submodule on purpose: a stale submodule pointer is
silent, while a vendored file shows up in the diff, and `--verify` turns "the contract quietly went
stale" into a build failure instead of a surprise later. See `README.md`'s "The contract layer" section
for the full rationale, and `AGENTS.md` for what counts as an additive vs. a breaking contract bump.

## Branching and commits

1. Fork the repo and create a branch off `main` named for the change (`fix/…`, `feat/…`, `chore/…`).
2. Keep commits focused — one logical change per commit — with a clear, imperative subject line
   (`Add barcode-scan permission rationale`, not `fixes`).
3. If you ported a function from the sandbox prototype, bring its vector-suite assertions with it in
   the same change — a port without the corpus assertion is how the apps silently drift, which is
   exactly what the corpus exists to catch.

## Pull requests

- Rebase on the latest `main` before opening the PR; keep the history readable (avoid unnecessary merge
  commits).
- Make sure `./gradlew testDebugUnitTest`, `./gradlew assembleDebug`, and `./gradlew lintDebug` all pass
  locally before pushing — CI runs the same gates (`contracts`, `test`, `build`) and will not merge a
  red run.
- Describe *why* the change is needed, not just what changed — the codebase leans on comments and PR
  descriptions to keep hard-won context (see `AGENTS.md`'s "Toolchain notes") from being re-learned the
  hard way.
- Changes that touch anything listed under "Requires approval" in `AGENTS.md` (new permission, `minSdk`,
  lint/warning relaxation, a major contracts bump, a new dependency or module) should say so explicitly
  in the PR description and expect a slower review.

## Licensing

This repository is MPL-2.0, the client tier of Pocket Nutrition's two-tier licensing — see
`README.md`'s "Licence" section for the full rationale (it is deliberate and accurate; please don't
propose relicensing it). Add `// SPDX-License-Identifier: MPL-2.0` to new files, using **Exhibit A only,
never Exhibit B** — Exhibit B would make this code incompatible with the AGPL backend repos it has to
be combinable with.

Do not copy code from any GPL-licensed reference implementation — see `AGENTS.md`'s "Do not copy
code from GPL-licensed reference implementations" section for why, and what is fine to do instead
(reading it, describing its behaviour in prose, porting public scientific formulas).

## Reporting issues

Use the issue templates under `.github/ISSUE_TEMPLATE/` — bug reports and feature requests both ask for
the device/OS/app-version details that make an Android bug reproducible. If you're unsure whether
something is a bug in this app or in the shared contract/backend, say so in the report; it usually saves
a round trip.

## CI and releases

- Every pull request runs `.github/workflows/ci.yml`. The `ci-success` check must be green before a pull
  request can be merged, the branch must be up to date with `main`, and pull requests are squash-merged
  so history stays linear.
- If you contribute from a fork, the workflow waits for a maintainer to approve it. It runs with a
  read-only token and no secrets.
- Every commit merged to `main` is released automatically with a CalVer version, `vYYYY.M.N`: the year, the
  month, then a counter that restarts at 1 each month (for example `v2026.11.1`). You never pick a version
  number or edit a changelog; release notes are generated from the merged pull requests.
- A release attaches a debug-signed APK. Signed store builds are not produced by CI.
