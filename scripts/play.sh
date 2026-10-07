#!/usr/bin/env bash
# One command to play with the app on an emulator with test data (local flavor, nothing touches real services).
#   1. backend harness (real Lambda handlers on DynamoDB Local, seeded; needs Docker) on 127.0.0.1:4310
#   2. e2e sidecar (sign-in/sign-up, Pro account, S3 stand-in, import/capture stubs) on 127.0.0.1:4311
#   3. emulator window, adb reverse, install of the `local` flavor, launch
# Sign-in accounts for the sidecar are listed in tools/e2e-sidecar/README.md.
# Usage: scripts/play.sh [stop]
set -uo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
backend="$root/../spenditos"
sdk="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
adb="$sdk/platform-tools/adb"
avd="${AVD:-Pixel_3a_API_34_extension_level_7_arm64-v8a}"
log="${TMPDIR:-/tmp}/spenditos-play"; mkdir -p "$log"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"

if [[ "${1:-}" == "stop" ]]; then
  "$adb" emu kill >/dev/null 2>&1; pkill -f "tools/e2e-sidecar/server.mjs"; pkill -f "e2e/dist/standalone.js"; echo stopped; exit 0
fi

up() { curl -fs -m 2 "$1" >/dev/null 2>&1; }

if ! up http://127.0.0.1:4310/health; then
  echo "starting backend harness (Docker must be running)…"
  (cd "$backend" && nohup pnpm --filter @spenditos/functions run serve:e2e >"$log/harness.log" 2>&1 &)
  for _ in $(seq 1 90); do up http://127.0.0.1:4310/health && break; sleep 2; done
fi
up http://127.0.0.1:4310/health || { echo "harness did not start; see $log/harness.log"; exit 1; }

if ! up http://127.0.0.1:4311/__sidecar/health; then
  echo "starting sidecar…"
  (cd "$root" && nohup node tools/e2e-sidecar/server.mjs >"$log/sidecar.log" 2>&1 &)
  for _ in $(seq 1 15); do up http://127.0.0.1:4311/__sidecar/health && break; sleep 1; done
fi

if ! "$adb" get-state >/dev/null 2>&1; then
  echo "starting emulator window…"
  (nohup "$sdk/emulator/emulator" -avd "$avd" -gpu swiftshader_indirect -no-audio -no-boot-anim -no-snapshot-save >"$log/emulator.log" 2>&1 &)
  "$adb" wait-for-device
  until [[ "$("$adb" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do sleep 3; done
fi
"$adb" reverse tcp:4311 tcp:4311 >/dev/null
"$adb" shell svc power stayon true >/dev/null 2>&1
"$adb" shell input keyevent KEYCODE_WAKEUP

(cd "$root" && ./gradlew :app:installLocalDebug --console=plain -q) || exit 1
"$adb" shell am start -n com.asaklex.spenditos.local/com.spenditos.android.app.MainActivity >/dev/null
echo "ready: the app is open on the emulator (logs: $log). Stop everything with: scripts/play.sh stop"
