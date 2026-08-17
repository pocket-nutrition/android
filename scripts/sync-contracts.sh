#!/usr/bin/env bash
# SPDX-License-Identifier: MPL-2.0
#
# Vendors the pinned pocket-nutrition-contracts artefacts into contract/.
#
# WHY VENDOR RATHER THAN SUBMODULE
# A stale submodule pointer is silent: the build succeeds, the app compiles, and it is quietly using
# last month's schema. Vendored files put the contract in the diff, so a change is reviewed like any
# other change — and --verify turns going stale into a build failure instead of a surprise.
#
#   ./scripts/sync-contracts.sh --update    # refresh contract/ from the pinned version
#   ./scripts/sync-contracts.sh --verify    # CI: fail if contract/ differs from the pinned version
#
# The pin is CONTRACTS_VERSION. Source defaults to the sibling clone; override with PN_CONTRACTS_DIR.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACTS_DIR="${PN_CONTRACTS_DIR:-${REPO_ROOT}/../pocket-nutrition-contracts}"
PINNED="$(tr -d '[:space:]' < "${REPO_ROOT}/CONTRACTS_VERSION")"
DEST="${REPO_ROOT}/contract"

# source-relative-path : destination-relative-path
MAPPING=(
  "tokens/generated/Tokens.kt:kotlin/Tokens.kt"
  "openapi/openapi.patched.json:data/openapi.patched.json"
  "strings/fr.json:data/strings/fr.json"
)

usage() { echo "usage: $0 --update | --verify" >&2; exit 2; }
[[ $# -eq 1 ]] || usage
MODE="$1"
[[ "$MODE" == "--update" || "$MODE" == "--verify" ]] || usage

if [[ ! -d "$CONTRACTS_DIR" ]]; then
  echo "error: contracts repository not found at ${CONTRACTS_DIR}" >&2
  echo "       clone it beside this repo, or set PN_CONTRACTS_DIR." >&2
  exit 1
fi

ACTUAL="$(tr -d '[:space:]' < "${CONTRACTS_DIR}/VERSION")"
if [[ "$ACTUAL" != "$PINNED" ]]; then
  echo "error: version mismatch." >&2
  echo "       CONTRACTS_VERSION pins ${PINNED}, but ${CONTRACTS_DIR} is at ${ACTUAL}." >&2
  echo "       Check out contract-v${PINNED} there, or bump CONTRACTS_VERSION deliberately and" >&2
  echo "       re-run --update. Do not paper over it: a mismatch means the app is being built" >&2
  echo "       against a schema nobody reviewed it against." >&2
  exit 1
fi

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

# Build the expected tree in a staging directory first, so --verify never mutates anything and
# --update stays close to atomic.
for entry in "${MAPPING[@]}"; do
  src="${CONTRACTS_DIR}/${entry%%:*}"
  dst="${STAGE}/${entry##*:}"
  if [[ ! -f "$src" ]]; then
    echo "error: ${src} is missing from the contracts repository." >&2
    exit 1
  fi
  mkdir -p "$(dirname "$dst")"
  cp "$src" "$dst"
done

# The whole vector corpus, whatever it currently holds: hard-coding the suite list here would go
# stale the moment a suite is added or dropped, which has already happened once.
mkdir -p "${STAGE}/data/vectors"
shopt -s nullglob
vectors=("${CONTRACTS_DIR}"/vectors/*.json)
if [[ ${#vectors[@]} -eq 0 ]]; then
  echo "error: no vector files found in ${CONTRACTS_DIR}/vectors" >&2
  exit 1
fi
cp "${vectors[@]}" "${STAGE}/data/vectors/"

# index.json sits alongside the suites but is not one, so the count reported below excludes it.
# Counting files reported "13 vector suites" where index.json declares 12 — cosmetic, but a number that
# disagrees with the corpus is the sort of thing that wastes an hour during a real drift.
SUITE_COUNT=${#vectors[@]}
if [[ -f "${STAGE}/data/vectors/index.json" ]]; then
  SUITE_COUNT=$(( SUITE_COUNT - 1 ))
fi

# A stamp so a stray contract/ directory can be traced to its origin without git archaeology.
# Deliberately carries no timestamp, so --verify can compare bytes without flaking.
cat > "${STAGE}/VENDORED" <<STAMP
Vendored from pocket-nutrition-contracts ${PINNED}
by scripts/sync-contracts.sh — do not edit these files by hand.

They are generated or curated upstream. To change one: change it in the contracts
repository, tag a release, bump CONTRACTS_VERSION here, and run --update.
STAMP

if [[ "$MODE" == "--verify" ]]; then
  if diff -r --brief "$STAGE" "$DEST" > /dev/null 2>&1; then
    echo "OK: contract/ matches pocket-nutrition-contracts ${PINNED} (${SUITE_COUNT} vector suites)"
    exit 0
  fi
  echo "FAIL: contract/ has drifted from pocket-nutrition-contracts ${PINNED}." >&2
  diff -r "$STAGE" "$DEST" >&2 || true
  echo >&2
  echo "Run ./scripts/sync-contracts.sh --update and commit the result." >&2
  exit 1
fi

rm -rf "$DEST"
mkdir -p "$DEST"
cp -R "$STAGE"/. "$DEST"/
echo "updated contract/ from pocket-nutrition-contracts ${PINNED} (${SUITE_COUNT} vector suites)"
find "$DEST" -type f | sed "s|^${DEST}/|  |" | sort
