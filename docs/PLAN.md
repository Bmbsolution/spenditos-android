# Spenditos Android: plan

Status: **draft, waiting for owner approval** (2026-10-06). Read with `ARCHITECTURE.md`, `PARITY.md`, `DECISIONS.md`, `STATUS.md`.

## 1. MVP scope (Android v1, finance-first)

| Wave | Contents | Why here |
|---|---|---|
| Shell | auth (login, register, verify, forgot/reset, refresh, sign-out), launch gate, quick-mode onboarding, 5 tabs + FAB, env badge | everything depends on it |
| Core money | transactions list/filter/search/pagination, add/edit/delete, categories + picker, offline create queue + "Not synced" view | the product's primary loop |
| Plan | Income & bills (recurring CRUD, main income, generate month), Month plan draft (autosave, apply, discard; no everyday) | the owner's core value |
| Home | cash-flow status card + math sheet, upcoming bills, simple month chart, draft reminder | needs transactions + recurring |
| Reports | hub, daily aggregates, month comparison, paycheck periods (server Pro gate respected) | read-only, low risk |
| Capture | receipt scan (camera/photo → analysis → prefilled form) | highest-value AI capture, simple async |
| Import | statement import (accepts .csv and .xlsx); gated on `premium.active` like iOS | finance-first list; iOS's separate CSV flow is broken against the server, skipped |
| Settings | profile, currency, categories, change password, delete account, sign out | account hygiene |

**Later (v1.x):** voice/text AI capture with multi-item review, Pro purchases (RevenueCat + Play, needs a backend webhook change), WebSocket real-time, gamification/progress (visually secondary, best-effort), biometric lock, push (no backend sender exists), widgets, CSV export, Play Store release pipeline, config-sync workflow.

**Not porting:** merchant mappings and the `/import/csv` flow (both broken against the server on iOS), everyday estimate (removed server-side), DEBUG-only export/import screens, dead iOS code listed in PARITY.md.

## 2. Build order and model switches

| Step | Who | Model | Output / gate |
|---|---|---|---|
| P0 plan | Claude + GLM audits | Opus | this folder, owner approval |
| P1.1 skeleton | Claude | Opus | Gradle wrapper, version catalog, flavors (staging/prod, no prodDebug), host validation, Hilt, theme, nav shell, CI-less `./gradlew check` green |
| P1.2 shared layers | Claude | Opus | `core/network` (envelope, auth interceptor, single-flight refresh), `core/auth` (Keystore TokenStore, SessionManager), `core/db` (Room), `core/sync` (MutationQueue, SyncWorker, invalidation bus), contract fixture script + `ContractFixtureTest` |
| P1.3 vertical slice | Claude | Opus | login → transactions list (cache-first) → add (online + offline queue → replay via bulk) → "Not synced" sheet; unit tests for refresh, queue classification, replay idempotency |
| P1.4 verify | Claude | Opus | emulator screenshots against the local e2e harness (and staging login if the owner signs in), `ANDROID_STYLE.md`, `STATUS.md` |
| ⇄ **switch to Sonnet** | | | ask owner |
| P2 fan-out | codex workers (one feature per worktree), Sonnet reviews | Sonnet | waves below |
| ⇄ **switch to Opus** for batched reviews | | Opus | any diff touching auth/tokens, financial writes, queue/sync, contract models |

P2 waves (features in one wave share no files, so they run in parallel):

1. **Wave A:** Categories + Settings basics · Reports (all four screens) · Onboarding
2. **Wave B:** Income & bills (recurring) · Receipt scan
3. **Wave C:** Month plan draft (financial writes → Opus review) · Home (depends on recurring + transactions)
4. **Wave D:** Statement import (financial write → Opus review)
5. **Hardening:** accessibility pass, dark mode, process-death/rotation tests, release build with R8, emulator smoke of every tab

Opus review batches: end of Wave B (receipt confirm path), end of Wave C (plan apply), end of Wave D (imports), plus any contract-model change.

## 3. Delegation plan

- Owned by Claude (never delegated): architecture, `core/*`, Kotlin contract model review, anything in auth/token storage, financial writes, queue/sync.
- Delegated to codex (Luna xhigh), one feature per worktree, spec template in `ANDROID_STYLE.md`: screens, ViewModels, repositories against the slice pattern, first-draft unit tests, Kotlin model drafts for a feature's handler-only shapes.
- GLM: read-only sweeps (parity audits, "find every iOS call site of X", fixture coverage gaps).
- grok `--effort low`: quick second opinion on a tricky diff.
- Every worker diff: Claude re-runs `./gradlew :app:testStagingDebugUnitTest lintStagingDebug` in the worktree and spot-checks before merging.
- `delegate.sh` needs Android support: Gradle runs in the worktree with the shared `~/.gradle` cache (default already shared), `local.properties` (sdk.dir) copied into the worktree, and staggered GLM starts (opencode's SQLite locks if several start at once). I'll make and report that change at the start of P2.

## 4. Verification strategy

- Unit: ViewModels with fake repositories; repositories with MockWebServer + in-memory Room; `TokenRefresher` concurrency tests; queue failure classification table test copied from iOS cases.
- Contract: every canonical fixture decodes; unknown fields and unknown enum values tolerated.
- Parity: Home `CashFlowStatus` and Month plan totals tested against the same seed numbers the iOS harness suites assert.
- Emulator: the backend's local e2e harness (`pnpm --filter @spenditos/functions run serve:e2e`, real handlers + DynamoDB Local, stub bearer `e2e-<userId>`), reached via `adb reverse tcp:4310 tcp:4310`, behind a debug-only `harness` flavor that allows cleartext to localhost and injects the test token. Staging login only if the owner signs in on the emulator themselves (Claude never types real credentials).
- Never against prod.

## 5. Risks

| Risk | Mitigation |
|---|---|
| Backend still moving (#336 just finished Phase 7; checkout was 4 commits behind) | pin a backend commit in `contract/MANIFEST.json`; re-run fixture sync before each contract-touching wave |
| Many shapes have no packages/types type (auth, quota, imports, generate, country) | handler-derived Kotlin models with hand fixtures; propose (not do) adding them to packages/types later |
| iOS has duplicated/buggy client logic (idempotency, everyday, merchant mappings, null-dropping settings) | PARITY.md marks each one; Android follows the server, not the bug |
| Offline queue duplicates | bulk + `idempotencyKeyId`, frozen payloads, tests |
| Keystore flakiness on some devices | distinct recoverable error; never clear the session on it |
| Workers drifting from the pattern | `ANDROID_STYLE.md` + slice as the reference file per layer; small specs; Claude re-runs verification |
| Dependency downloads | first build fetches Gradle 9.x + AGP + AndroidX/Compose/Hilt/Room/OkHttp from Google Maven and Maven Central (~1 GB); needs owner approval |
| No Play Billing in v1 | server gates still enforced; clear copy; billing is its own later task with a backend change |

## 6. Server changes (none required for MVP)

All additive, none needed to start; each needs the owner's go-ahead and lands/deploys before any Android build that depends on it:
1. RevenueCat webhook: tag Play Store purchases `rc_android` (`fn/webhooks/revenuecat-events.ts:62`). Needed before Android billing.
2. `POST /recurring` should persist `amountVaries` (`fn/api/recurring.ts:295-307`). Affects iOS too.
3. Optional: honor `Idempotency-Key` on `POST /transactions` (would also fix iOS without a client release).
4. Optional: export handler-only shapes (auth, quota, generate, imports) into `packages/types` with fixtures.
5. Optional: server-side Pro/quota gate on `/statements/*` (today only the iOS client enforces it, `fn/api/statement-import.ts` has no `requirePro`).
6. Later: Android config-sync dispatch in `deploy.yml` (needs a new PAT).
