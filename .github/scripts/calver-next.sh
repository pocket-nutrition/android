#!/usr/bin/env bash
# SPDX-License-Identifier: MPL-2.0
#
# Prints the next CalVer version (YYYY.M.N, without the leading "v") and its versionCode (YYYYMMNN),
# separated by a space. N restarts at 1 every month. Creates nothing.
#
# Reads the tags of the local clone, so the checkout must include them (fetch-depth: 0). The Release
# workflow runs one release at a time, and its `git push --atomic` refuses to create a tag that already
# exists, so a stale read fails the push instead of producing two commits with the same version.
set -euo pipefail

prefix="$(date -u +%Y.%-m)"

# Highest N already used this month. The trailing dot in the pattern stops "2026.1." from matching
# "2026.11.". Non-numeric suffixes (e.g. a manual "v2026.11.1-rc1") are ignored.
last="$(
  git tag --list "v${prefix}.*" \
    | sed -E "s|^v[0-9]+\.[0-9]+\.||" \
    | grep -E '^[0-9]+$' \
    | sort -n \
    | tail -n 1 || true
)"
version="${prefix}.$(( ${last:-0} + 1 ))"

IFS=. read -r year month patch <<< "${version}"
if [ "${patch}" -gt 99 ]; then
  echo "more than 99 releases this month: versionCode (YYYYMMNN) cannot represent ${version}" >&2
  exit 1
fi

printf '%s %d%02d%02d\n' "${version}" "${year}" "${month}" "${patch}"
