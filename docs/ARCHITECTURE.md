# Spenditos Android: architecture

Status: **proposal, waiting for the owner's approval** (Phase 0, 2026-10-06).
Product reference: `spenditos-ios` @ `c35c60f` (develop). Backend reference: `spenditos` @ `4acac33` (origin/develop).

## 1. Stack

| Concern | Choice | Mirrors on iOS |
|---|---|---|
| Language / UI | Kotlin 2.x, Jetpack Compose, Material 3 | SwiftUI |
| Presentation | MVVM: `ViewModel` + `StateFlow<UiState>` + one-shot `events` channel | `@Observable` view models |
| DI | Hilt | singletons (`.shared`) |
| HTTP | Retrofit + OkHttp + kotlinx.serialization | `APIClient` + `JSONDecoder` |
| Local cache + queue | Room (one database per environment) | SwiftData |
| Background sync | WorkManager (unique work, network constraint, exponential backoff) | `BackgroundSyncManager` + `NWPathMonitor` |
| Token storage | Android Keystore-wrapped AES-GCM key encrypting a small DataStore file | Keychain, `AfterFirstUnlockThisDeviceOnly` |
| Preferences | Jetpack DataStore | `UserDefaults` / `UserPreferences` |
| Real-time | OkHttp WebSocket + `POST /ws-ticket` | `WebSocketManager` |
| Images | CameraX + Photo Picker; Coil for display | `CameraPreviewView`, PhotosPicker |
| Navigation | Navigation Compose, bottom bar: Home, Transactions, Plan, Reports, Settings; global FAB owns "add" | `MainTabView` + FAB |
| Tests | JUnit5-style JUnit4 + Turbine + MockWebServer + Room in-memory; Compose UI tests; Roborazzi screenshots later | XCTest |

minSdk 28, target/compile 36, JDK 21 toolchain, Gradle wrapper, version catalog (`gradle/libs.versions.toml`).
Not using: Amplify, Firebase (no push sender exists in the backend yet), RxJava, Moshi.

## 2. Module layout

Start as a single `:app` module with strict packages; split into Gradle modules only when build times demand it. Packages mirror iOS folders so workers can map files 1:1:

```
app/src/main/java/com/spenditos/android/
  app/          MainActivity, nav graph, bottom bar, FAB, deep links, launch/auth gate   (iOS App/)
  core/config/  AppEnvironment (BuildConfig-backed), host validation                   (AppRuntime)
  core/network/ ApiClient (Retrofit), Envelope<T>, ApiError, AuthInterceptor, TokenRefresher,
                TraceHeadersInterceptor, NetworkMonitor                                  (Core/Network)
  core/auth/    TokenStore (Keystore), SessionManager, BiometricGate                    (Core/Auth, TokenManager)
  core/db/      SpenditosDatabase, entities, DAOs, migrations                          (Core/Storage/SwiftDataModels)
  core/sync/    MutationQueue, SyncWorker, SyncScheduler, FinanceInvalidationBus,
                RealtimeClient (WebSocket)                                             (OfflineQueueManager, BackgroundSyncManager,
                                                                                        FinanceDataReconciliationCoordinator, WebSocketManager)
  core/telemetry/ allowlisted spans only                                               (TelemetryTracing)
  data/model/   Kotlin wire models mirrored from packages/types (+ handler-only shapes)  (Domain/Models)
  data/<feature>/ Repository per feature (network + Room)                              (Domain/Services)
  feature/<name>/ Screen.kt, ViewModel.kt, components                                  (Features/<Name>)
  ui/theme, ui/components                                                              (Shared/Theme, Shared/Components)
```

Rule: `feature/*` may depend on `data/*` and `ui/*`; never on `core/network` directly. Only `data/*` talks to Retrofit and Room.

## 3. Environments and config (never silently prod)

- Product flavors on one `env` dimension: `staging`, `prod`. Optional `dev` flavor for a personal SST stage, values from an uncommitted `config/dev.properties` (the iOS Debug build targets the personal `asaklex` stage).
- Values live in `config/<env>.properties` (committed, non-secret: `API_HOST`, `WEB_SOCKET_HOST`, `DEPLOYMENT_STAGE`, `APP_ENV_NAME`, `CLIENT_CONFIG_VERSION`, `REVENUECAT_API_KEY` public key) and are injected into `BuildConfig`.
- `API_HOST` is **host-only**. A Gradle check fails the build if it contains a scheme, a path, or any forbidden token from `packages/core/src/config/client-config.ts` (`localhost`, `placeholder`, …). The client always builds `https://<host>`.
- **Debug builds are never prod.** `prodDebug` is removed with `variantFilter` / `beforeVariants`. The default run configuration is `stagingDebug`. Release builds of `prod` are the only prod variant.
- Room database name and token store file are namespaced per environment (`spenditos-<env>.db`), the same idea as the iOS Keychain namespace.
- An environment badge shows on non-prod builds (iOS `SHOW_ENV_BADGE`).
- Config sync: iOS gets stage config through a `repository_dispatch` from the backend deploy workflow. Android starts with hand-maintained `config/*.properties`; adding a matching `sync-config` workflow is a later, separate task (needs a new PAT and a backend workflow edit, so it needs the owner's authorization).

## 4. Networking and auth (mirrors `APIClient` + `TokenManager`)

- Every response is the envelope `{ data, message?, error?, details? }` (`ApiResponse` in `packages/types/src/api/index.ts:15`). `Envelope<T>` decodes it; `details` stays a `JsonObject` (it carries gamification extras).
- `Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }`. Enums decode with an `UNKNOWN` fallback so new server values never crash old clients (the enum-evolution rule in `packages/types/test/contract/enum-evolution.test.ts`).
- Headers on every call: `Authorization: Bearer` (except `/auth/*`), `X-Spenditos-Trace-Id`, `X-Spenditos-Session-Id`, W3C `traceparent`.
- Token lifecycle, same rules as iOS:
  - Proactive refresh when the access token expires within 300 s (JWT `exp`).
  - Single-flight refresh (one `Mutex`-guarded refresh shared by all callers) via `POST /auth/refresh {refreshToken}` → `{accessToken, idToken}`.
  - A session generation counter drops a refresh result that finished after sign-out or re-login.
  - On a 401 from a normal call: refresh once, retry once; a second 401 drops only the access token and surfaces "unauthorized".
  - Local auth state is cleared **only** when `/auth/refresh` itself returns 401. Network errors, 5xx, and Keystore unavailability keep the refresh token.
  - `userId` = `sub` from the id token; every Room row and queued mutation is stamped with it.
- Token storage: AES-256-GCM key in Android Keystore (`setUserAuthenticationRequired(false)` so WorkManager can sync after reboot, like iOS `AfterFirstUnlock`); ciphertext in a private DataStore file; excluded from backup (`dataExtractionRules`). `KeystoreUnavailable` is a distinct, recoverable error.
- Biometric lock (later phase): `BiometricPrompt` gates the UI on resume when enabled; it never deletes tokens. Explicit sign-out still requires a password.
- Certificate pinning: iOS has the hook but no real hashes yet and skips it in DEBUG. Android: same, an OkHttp `CertificatePinner` behind a flag, off until real hashes exist.
- Logging: OkHttp logging only in debug, headers redacted (`Authorization`, presigned URLs), bodies never logged.

## 5. Offline cache, queue, and sync: one system

These three are designed and changed together (`core/db` + `core/sync` + `data/transactions`). A worker never edits one without the others.

### Cache
- iOS caches only categories plus queued creates; its transaction list is network-only. Android deliberately goes one step further (cache-first lists) because Room makes it cheap; this changes no server behavior.
- Room tables: `transactions`, `categories`, `dashboard_summary`, plus a generic `response_cache(key, userId, json, fetchedAt)` for read-mostly screens (plan, reports, recurring) so they render offline.
- Every row carries `userId`; every DAO query filters by the signed-in `userId`.
- Reads are cache-first: the screen observes a Room `Flow`; a refresh fetches from the API and upserts. Server rows replace cached rows by `id`; rows with `syncStatus != SYNCED` are never overwritten by a refresh.

### Queue (pending mutations)
- Table `pending_mutations(id, userId, kind, payloadJson, clientMutationId, payloadVersion, status, failureCode, failureMessage, attemptCount, createdAt, lastAttemptAt)`. MVP kind: `CREATE_TRANSACTION` (the only queued kind on iOS). Edits and deletes need connectivity, as on iOS.
- The optimistic transaction row is inserted with `id = "local-<uuid>"`, `syncStatus = PENDING` in the same Room transaction as the mutation row.
- **All plain creates (first attempt and replay) use `POST /transactions/bulk` with one item, `source: "manual"`, and `idempotencyKeyId = clientMutationId`, not `POST /transactions`** (DECISIONS D2). The key is persisted before the first attempt, so a create whose response was lost can be queued and replayed safely. Reason: the deployed single-create handler ignores both the `Idempotency-Key` header and the body `idempotencyKey` that iOS sends (`CreateTransactionSchema` has no such field; `packages/core/src/schemas/transaction.ts:235`), so a lost response creates a duplicate. The bulk path derives a deterministic id from `userId + date + description + amount + categoryId + idempotencyKeyId` and reports a duplicate instead of writing twice (`packages/core/src/repositories/transaction.ts:457`). No server change is needed.
- The payload is frozen after the first send attempt (`payloadVersion`), because editing a sent-but-unacknowledged item would change the deterministic id.
- Failure classes copied from iOS `OfflineQueueManager.FailureCode`: `transport` and `server` (5xx/408/429) retry automatically; `authentication`, `validation`, `categoryConflict`, `permanent`, `unknown` wait for the user (retry / edit / discard in a "Not synced" sheet).
- Sign-out keeps the queue per account (iOS #227) and states the count in the confirmation; rows of other users are never replayed (the queue query filters by the current `userId`).

### Sync
- `SyncWorker` (WorkManager unique work `sync-mutations`, `NetworkType.CONNECTED`, exponential backoff from 30 s) drains pending rows oldest first, one at a time.
- Triggers: enqueue, app start, connectivity regained, `ON_START` of the process, and pull-to-refresh. That replaces iOS's NWPathMonitor observer and also survives process death, which iOS does not.
- After a successful drain the worker emits a `transaction` invalidation and refreshes the first page.

### Reconciliation (mirrors `FinanceDataReconciliationCoordinator`)
- `FinanceInvalidationBus` (a `SharedFlow<Set<Aggregate>>`) coalesces events for 250 ms. Sources: WebSocket `user.data_changed`, socket reconnect (all aggregates), app foreground (all aggregates), local successful writes.
- On socket loss: bounded fallback of 5 refreshes, 30 s apart, then stop.
- Screens subscribe to the aggregates they display (`transaction`, `category`, `recurring`, `import`, `settings`, `entitlement`, `account`).
- WebSocket connect: `POST /ws-ticket` → single-use ticket → `wss://<WEB_SOCKET_HOST>?ticket=…`; reconnect with 1, 2, 4, 8, 16 s backoff, a new ticket every time.

## 6. Async analysis (receipt / image / audio)

Mirror iOS semantics exactly (see PARITY.md "Receipt scan"): `POST /transactions/upload-url` → multipart **POST** of the returned `fields` + `file` to the presigned S3 policy URL (separate OkHttp client, no auth header, URL never logged) → `POST /transactions/analyze-image {documentKey}` → poll `GET /transactions/analysis/{id}` every 2 s for at most 30 s (audio: up to 300 s, 1.5× backoff capped at 1 s) → on timeout or `failed`, fall back to the manual form. The resulting items are confirmed through `POST /transactions/bulk` with `analysisId` and per-item `idempotencyKeyId`. Uploads that must outlive the screen run as a foreground-visible WorkManager job; the analysis result is never cached beyond the review screen (receipt contents are sensitive).

## 7. Money, dates, and domain rules

- Wire money is a JSON number (the server is TypeScript). Decode to `Double` at the wire boundary, convert to `BigDecimal` for any client-side sum, and format with the user's currency through `NumberFormat`.
- Dates are `YYYY-MM-DD` strings on the wire; parse to `LocalDate` in the user's time zone. Never convert a date-only value through UTC.
- Income vs expense comes from the category's `isIncome`, not from the amount sign (iOS `TransactionType` is UI-only).
- Do not port server business rules. Where iOS computes something client-side (bill matching, plan totals), port it only if the parity map says the screen needs it offline, and test it against the same fixtures as iOS.

## 8. Contract strategy

- Kotlin wire models live in `data/model`, one file per area, named exactly like the TypeScript types (`ListTransactionsResponse`, `MonthlyPlanDraftData`, …), each with a KDoc link to `packages/types/src/...:line` or to the handler for handler-only shapes.
- Fixtures: `scripts/sync-contract-fixtures.sh` runs Node against the backend's built `packages/types/dist/__fixtures__` and writes one JSON file per fixture to `app/src/test/resources/contract/`, plus `MANIFEST.json` with the backend commit. No backend change needed.
- `ContractFixtureTest` decodes every fixture with the production `Json` instance and asserts key fields; `UnknownFieldsTest` and `EnumEvolutionTest` mirror the backend's own contract suites.
- Drift check: the script has `--check` mode (regenerate to a temp dir, diff). It runs in CI later; for now, before each contract-touching change.
- Shapes with no TS type (auth, reports, imports, gamification, statements) are mirrored from the handler's response builder and the iOS Swift model, and get a hand-written JSON fixture copied from a real staging response with all personal values replaced.

## 9. Gamification, telemetry, and other best-effort side effects

Gamification UI reads `details` from write responses and `/gamification/*`; any failure there is swallowed and logged, never shown as a failed save. Telemetry spans use the same attribute allowlist as iOS `TelemetryTracing`; no descriptions, amounts, emails, tokens, or URLs.
