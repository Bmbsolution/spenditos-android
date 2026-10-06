#!/usr/bin/env bash
# Full local verification for a checkout/worktree: unit tests + lint + staging debug build.
# Usage: scripts/verify.sh [dir]   (default: repo root)  Prints a compact summary.
set -uo pipefail
dir="$(cd "${1:-$(dirname "$0")/..}" && pwd)"
cd "$dir"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
[[ -f local.properties ]] || cp "$(dirname "$0")/../local.properties" local.properties 2>/dev/null || true
log="$(mktemp)"
./gradlew :app:testStagingDebugUnitTest :app:lintStagingDebug :app:assembleStagingDebug --console=plain >"$log" 2>&1
rc=$?
echo "== verify rc=$rc ($dir)"
grep -E "^e: " "$log" | sed -E 's#file://[^ ]*/app/src/#app/src/#' | head -40
grep -E "tests completed|FAILED$" "$log" | head -20
python3 - <<'PY'
import glob,xml.etree.ElementTree as ET
t=f=e=0
for p in glob.glob("app/build/test-results/testStagingDebugUnitTest/*.xml"):
    r=ET.parse(p).getroot(); t+=int(r.get("tests"));f+=int(r.get("failures"));e+=int(r.get("errors"))
    for tc in r.iter("testcase"):
        for x in list(tc.findall("failure"))+list(tc.findall("error")):
            print("FAIL", r.get("name").split(".")[-1], tc.get("name"), "|", (x.get("message") or "")[:140])
print(f"tests={t} failures={f} errors={e}")
PY
grep -E "Lint found|Error:" app/build/reports/lint-results-stagingDebug.txt 2>/dev/null | head -10
rm -f "$log"
exit $rc
