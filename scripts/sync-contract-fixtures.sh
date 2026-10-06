#!/usr/bin/env bash
# Exports the backend's canonical contract fixtures (spenditos/packages/types/src/__fixtures__)
# to JSON for Android contract tests (app/src/test/resources/contract/).
#
# Usage: scripts/sync-contract-fixtures.sh [--check] [path-to-spenditos-checkout]
#   The checkout's packages/types must be built first:  (cd <checkout>/packages/types && pnpm build)
#   --check: regenerate into a temp dir and fail if it differs from the committed files.
set -euo pipefail

check=0
if [[ "${1:-}" == "--check" ]]; then check=1; shift; fi
backend="${1:-$(cd "$(dirname "$0")/../.." && pwd)/spenditos}"
dist="$backend/packages/types/dist/__fixtures__/index.js"
[[ -f "$dist" ]] || { echo "missing $dist; build packages/types in $backend first" >&2; exit 2; }

repo="$(cd "$(dirname "$0")/.." && pwd)"
target="$repo/app/src/test/resources/contract"
out="$target"
[[ $check -eq 1 ]] && out="$(mktemp -d)"
mkdir -p "$out"

commit="$(git -C "$backend" rev-parse --short HEAD 2>/dev/null || echo unknown)"
node --input-type=module - "$dist" "$out" "$commit" <<'JS'
import { writeFileSync, readdirSync, unlinkSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
const [dist, out, commit] = process.argv.slice(2);
const fixtures = await import(pathToFileURL(dist).href);
for (const f of readdirSync(out)) if (f.endsWith('.json')) unlinkSync(join(out, f));
const names = [];
for (const [name, value] of Object.entries(fixtures).sort(([a], [b]) => a.localeCompare(b))) {
  if (value === null || typeof value !== 'object') continue; // skip id constants
  writeFileSync(join(out, `${name}.json`), JSON.stringify(value, null, 2) + '\n');
  names.push(name);
}
writeFileSync(join(out, 'MANIFEST.json'), JSON.stringify({ source: 'spenditos/packages/types/src/__fixtures__', backendCommit: commit, fixtures: names }, null, 2) + '\n');
console.log(`wrote ${names.length} fixtures from backend ${commit}`);
JS

if [[ $check -eq 1 ]]; then
  if diff -r -q "$out" "$target" >/dev/null; then echo "contract fixtures up to date"; else
    echo "contract fixtures drifted; run scripts/sync-contract-fixtures.sh" >&2; diff -r -q "$out" "$target" >&2 || true; exit 1; fi
fi
