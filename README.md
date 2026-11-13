# Pocket Nutrition Android

Native Android client for Pocket Nutrition. Kotlin + Jetpack Compose.

- API: <https://api.pocketnutrition.org>
- Organisation: <https://github.com/pocket-nutrition>

## Status

The meal-logging flow works end to end: search an ingredient, set a portion and cooking method, analyse,
save to a local journal. 73 unit tests, all 17 corpus suites asserted, lint clean, R8 exercised on every
push, and a signed APK plus AAB published on a tag.

`AboutScreen` carries the licence, the Open Food Facts attribution and the privacy summary, and the
token gallery is now behind `BuildConfig.DEBUG` — so R8 removes it, which was verified by grepping the
release `classes.dex` for strings only that screen uses rather than assumed.

Not yet done, and each is a store-submission item rather than a feature: a designed launcher icon (the
current mark is provisional, see `ic_launcher_foreground.xml`), and the privacy-policy URL actually
being served — the page exists in `pocket-nutrition/website` but that repo's deployed image predates
it, so the URL currently returns the home page with a 200. There are no instrumented or UI tests —
`app/src/` has only `main/` and `test/`.

What was built first is the part that is hard to add later: contract pinning, the vendored corpus, the
generated design tokens, and a signing path that fails loudly rather than silently shipping unsigned.

Since the first public release the app is also bilingual (French and English), has a Settings screen to
point it at your own backend server, and can capture a meal by voice: the clip is recorded only while you
hold Record, uploaded to a recognition server you configure in Settings, and kept on the phone just until
the result has come back. It is off until a server is set. Barcode scanning now decodes with ZXing instead
of Google ML Kit, so the whole dependency tree is free software.

## Why not cross-platform

Two native apps, deliberately. What this product shares between clients is its *data* and its
*behaviour*, not its widgets — and those are shared through `pocket-nutrition/contracts` as JSON, which
a Kotlin app and a Swift app can both assert against without a shared runtime. A cross-platform layer
would couple the two apps' release cycles and UI idioms to buy sharing we already have.

## Stack

| Concern | Choice |
|---|---|
| Language | Kotlin 2.4 |
| UI | Compose (BOM 2026.06) + Material 3 |
| Min / target SDK | 26 / 37 |
| Build | Gradle 9.6 + AGP 9.3, version catalogue in `gradle/libs.versions.toml` |
| CI | GitHub Actions — see "CI" below |

`minSdk 26` is chosen so `java.time` is available without desugaring: the diary is date-keyed, and date
arithmetic is not somewhere to accept a shim.

## Layout

```
.ci/Dockerfile            # the CI build image: JDK 21 + pinned Android SDK
contract/                 # VENDORED from pocket-nutrition/contracts — never edit by hand
  kotlin/Tokens.kt        #   generated design tokens, compiled into :app
  data/vectors/*.json     #   the shared behavioural corpus, a test resource
  data/strings/fr.json    #   the FR copy deck
CONTRACTS_VERSION         # the pinned contracts version
scripts/sync-contracts.sh # --update / --verify
app/                      # the single module, for now
```

## The contract layer

Three things come from `pocket-nutrition/contracts` rather than being written here, because writing them
here is how the Android and iOS apps drift apart:

**Design tokens** are generated Kotlin (`PnColor`, `PnRole`, `PnCooking`, `PnConfidence`). A stale or
wrong token is a compile error rather than a slightly-off colour.

**Behavioural vectors** are the same JSON the sandbox and iOS assert against. `roundHalfUp` is the first
function ported and is asserted against the `rounding` suite; `CorpusIntegrityTest` decodes every other
suite so a malformed or half-vendored corpus fails now rather than when someone finally ports it.

**The FR copy deck** is vendored but not yet wired into resources. That lands with the first real screen,
so the mapping is reviewed alongside the UI that uses it.

```bash
./scripts/sync-contracts.sh --verify   # CI runs this; fails on drift
./scripts/sync-contracts.sh --update   # after bumping CONTRACTS_VERSION
```

Vendored rather than a submodule on purpose: a stale submodule pointer is silent, whereas a vendored file
shows up in the diff and `--verify` turns going stale into a build failure.

### The one rule to read before writing any arithmetic

`roundHalfUp` — `floor(x + 0.5)`. **Not `kotlin.math.round`**, which breaks ties towards even and would
make this app disagree with the web client and iOS on values a user can produce by tapping "Demi".
`app/src/main/kotlin/org/pocketnutrition/core/Rounding.kt` explains it; the corpus enforces it.

## Building

```bash
./gradlew testDebugUnitTest   # unit tests, including the corpus
./gradlew assembleDebug       # debug APK
./gradlew lintDebug           # lint; warnings are errors
```

Kotlin `allWarningsAsErrors` and lint `warningsAsErrors` are both on. A warning nobody has to act on is a
warning everybody ignores.

`.ci/Dockerfile` pins a known-good JDK + Android SDK combination, useful for reproducing a build in a
clean environment regardless of what's installed locally:

```bash
docker build -f .ci/Dockerfile -t pn-ci-android:local .
docker run --rm -v "$PWD":/w -w /w pn-ci-android:local ./gradlew testDebugUnitTest
```

## Release signing

**The keystore is not in this repository and must never be.** `*.jks`, `*.keystore` and `key.properties`
are gitignored from the first commit.

Signing is opt-in: when `key.properties` is absent — every fresh clone, every CI run without the secret —
the release build produces an **unsigned** APK instead of failing. That keeps the build useful without the
secret; run `apksigner verify` yourself to confirm which one you got, so "signed" is never assumed.

To sign locally, create `key.properties` at the repo root (gitignored):

```properties
storeFile=release.jks
storePassword=…
keyAlias=pocket-nutrition
keyPassword=…
```

If you set up your own CI, the equivalent secrets to configure are:

| Secret | Contents |
|---|---|
| `ANDROID_KEYSTORE_B64` | the keystore, base64-encoded |
| `ANDROID_KEYSTORE_PASSWORD` | store password |
| `ANDROID_KEY_ALIAS` | key alias |
| `ANDROID_KEY_PASSWORD` | key password |

> **Generate the keystore yourself and keep the only copies.** Lose it and this app can never be updated
> under the same identity again — there is no recovery path, on any distribution channel. Take three
> independent backups before pushing a signed build.

## Distribution

MPL-2.0, the client tier — see [Licence](#licence). The licence no longer constrains any channel
below; what remains are ordinary packaging requirements.

| Channel | Status |
|---|---|
| GitHub Releases (signed APK) | recommended primary channel — attach the built APK plus a `.sha256` companion |
| F-Droid | MPL-2.0 is an accepted free licence, there is no Play Services, and the barcode scanner uses ZXing (Apache-2.0) rather than Google ML Kit, which F-Droid would have rejected. The release dependency tree contains no Google Play Services, Firebase or ML Kit artifact. A recipe still has to be submitted to `fdroiddata`. Fastlane metadata is in `fastlane/metadata/android/` |
| Google Play | workable, but requires an **AAB** — you'd need a `bundleRelease`-based build step, not just `assembleRelease` |
| Apple App Store | **viable** for the iOS client under MPL-2.0 — removing the AGPL §10/§6 conflict is why the client tier was relicensed |

## CI

`.github/workflows/ci.yml` runs on every pull request and every push to `main`:

- **Contract pin**: `./scripts/sync-contracts.sh --verify`
- **Unit tests**: `./gradlew testDebugUnitTest`
- **Build and lint**: `assembleDebug`, `lintDebug`, and `bundleRelease` (exercises R8, unsigned)
- **ci-success**: the single required status check; it fails if any gate above failed

Pull requests from forks run with a read-only token and no secrets, and wait for a maintainer to approve
the run. A push to `main` additionally creates a CalVer release `vYYYY.M.N` (for example `v2026.11.3`; N
restarts at 1 each month) with the **debug** APK and its `.sha256` attached. `versionName` is the tag
without the `v`; `versionCode` is `YYYYMMNN`. No signing key is involved, so these APKs are for testing,
not for installing as an update to a signed build.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the build/test commands, how `contract/` relates to
`pocket-nutrition/contracts`, and the branch/PR workflow. Bug reports and feature requests have
templates under `.github/ISSUE_TEMPLATE/`. `AGENTS.md` is the reference for anyone (human or agent)
making a change — read it before anything that touches signing, `contract/`, or the toolchain.

## Acknowledgments

This app was inspired by the excellent [OpenNutriTracker](https://github.com/simonoppowa/OpenNutriTracker).
Trying to improve on its interface and
support ingredients we curate ourselves is what led to building a dedicated API and backend for
this app, rather than tracking nutrition entirely on-device.

## Licence

MPL-2.0 — see `LICENSE`.

Pocket Nutrition uses two tiers: the backend (`api`, `ml`, `ingest`, `community`, `data`,
`knowledge`) is AGPL-3.0-or-later; the client chain (`contracts`, **this repo**, `ios`) is
MPL-2.0, because AGPL's terms conflict with the App Store's distribution terms and MPL-2.0's don't.

Use `SPDX-License-Identifier: MPL-2.0` (Exhibit A) on new files. **Never Exhibit B**
("Incompatible With Secondary Licenses") — it would make this code un-combinable with the AGPL
backend repos.
