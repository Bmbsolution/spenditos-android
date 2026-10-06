# Spenditos Android

Native Android client for Spenditos (Kotlin, Jetpack Compose, Hilt, Room, Retrofit/OkHttp, WorkManager).
Product reference: `../spenditos-ios`. Server and contracts: `../spenditos` (`packages/types`).

Start here: `STATUS.md` → `docs/PLAN.md` → `docs/ARCHITECTURE.md` → `ANDROID_STYLE.md`.

## Build

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew :app:assembleStagingDebug          # default dev build, talks to staging
./gradlew :app:testStagingDebugUnitTest :app:lintStagingDebug
```

Needs Android SDK platform 37 and build-tools 37 (`local.properties` → `sdk.dir`).

| Variant | Backend | Notes |
|---|---|---|
| `stagingDebug` | `https://api.staging.spenditos.com` | default for development |
| `stagingRelease` | staging | R8-minified |
| `prodRelease` | production | the only prod variant; `prodDebug` does not exist |
| `harnessDebug` | local e2e harness via `adb reverse` | cleartext to 127.0.0.1 only; fixed synthetic token, no login |

Hosts live in `config/<env>.properties` (host-only, validated at build time). An optional, uncommitted `config/dev.properties` is reserved for a personal SST stage.

## Run against the local e2e harness (no account needed)

```bash
# backend repo: real Lambda handlers + DynamoDB Local (Docker or JAR); prints {"baseUrl": "http://127.0.0.1:<port>", ...}
pnpm --filter @spenditos/functions run serve:e2e
~/Library/Android/sdk/platform-tools/adb reverse tcp:4310 tcp:<port>
./gradlew :app:installHarnessDebug
```

The harness data is synthetic and thrown away when it stops.

## Contract fixtures

`scripts/sync-contract-fixtures.sh [--check] [path-to-spenditos]` exports `packages/types` canonical fixtures (build that package first) to `app/src/test/resources/contract/`; `ContractFixtureTest` decodes them.
