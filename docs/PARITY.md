# iOS → Android feature parity map

Sources: iOS `spenditos-ios` @ `c35c60f`, backend `spenditos` @ `4acac33` (origin/develop, includes #336 Phase 7).
Audit: GLM-5.3 read-only sweeps (Auth/Onboarding/Settings/Pro/shell; Plan/Recurring/Reports; Import/Gamification/extras), Claude's own read for Home/Transactions/capture/offline. Claude spot-checked 5 GLM citations; all 5 held.
`ios/` = `spenditos-ios/Spenditos/Spenditos/`, `fn/` = `spenditos/packages/functions/src/`, `types/` = `spenditos/packages/types/src/`.

Legend: **MVP** = Android v1 · **Later** = after v1 · **Skip** = don't port (dead, broken, or debug-only on iOS).

## App shell

| Item | iOS | Android | Scope |
|---|---|---|---|
| Launch gate | `App/SpenditosApp.swift:270-321`: authed → GET `/user/settings` `setupCompleted` → Onboarding or tabs; unauthed → login stack | same | MVP |
| Tabs | `App/MainTabView.swift:20-58`: Home, Transactions, Plan (badge = next-month draft change count), Reports, Settings; QuickAdd sheet = FAB | bottom bar + FAB | MVP |
| Deep links | notification payloads only (`App/DeepLinkHandler.swift:8-26`) | none until push exists | Later |
| Config | `AppRuntime.swift:160-220`: `API_HOST`, `WEB_SOCKET_HOST`, `DEPLOYMENT_STAGE`, `APP_ENV_NAME`, `CLIENT_CONFIG_VERSION`, `REVENUECAT_API_KEY` (+ unused Cognito ids) | `config/<env>.properties` → BuildConfig | MVP |

## Auth (MVP)

- Screens: Login (`Features/Auth/LoginView.swift`), Register (+ strength meter), VerifyEmail (6-digit code), ForgotPassword (2 steps). Biometric lock overlay is Later.
- Endpoints (all public, handler-local zod, **no packages/types type**): `POST /auth/register`, `/auth/confirm-registration`, `/auth/resend-code`, `/auth/login` → `{accessToken, refreshToken, idToken}`, `/auth/logout {refreshToken}`, `/auth/refresh {refreshToken}` → `{accessToken, idToken}` (refresh token **not rotated**), `/auth/forgot-password`, `/auth/confirm-password`. Shapes: `fn/auth.ts:59-144, 764-780`.
- Rules: 5-rule password validation (`AuthViewModel.swift:91-99`); login 403 = UserNotConfirmed → verify screen; 401 = wrong credentials. After login: replay offline queue.
- Token rules: see ARCHITECTURE.md §4 (`TokenManager.swift` single-flight refresh, generation guard, clear only on refresh 401).

## Onboarding (MVP, quick mode only)

- One paged wizard (`Features/Onboarding/OnboardingView.swift:161-205`): welcome → name → currency/country/language → setup mode → [categories, advanced only] → ready.
- Writes: `PUT /user/settings` (`setupCompleted`, currency, …), `POST /categories/batch` (19-category seed, `OnboardingView.swift:19-52`). Reads `GET /user/country-detection` (handler-only shape, `fn/user.ts:482`).
- iOS swallows errors and sets a local done flag; the launch gate re-checks the server. Android: same gate, but surface save errors.
- Skip: `NotificationPermissionView` (dead code).

## Home (MVP, reduced)

- iOS is 18 files / 5.6k lines (`Features/Dashboard`). Data: `GET /reports/dashboard` (`DashboardData`, `types/api/index.ts:741`), `GET /recurring`, `GET /transactions` (month), `GET /reports/daily-aggregates`, `GET /reports/paycheck-periods`, `GET /planning/draft` (reminder), gamification calls.
- Core number: **`CashFlowStatus`** (`Features/Dashboard/CashFlowStatus.swift`), computed client-side: left after bills = received + expected − spent − billsLeft; pace = everyday / max(daysElapsed, 7); states onTrack/watch/tight/over/setup. Parity target: iOS `HomeHarnessE2EUITests` seed numbers (Left after bills 1,838.50 · 21% of income left · next bill "Dentist") from `e2e/src/seed-home.ts`.
- MVP: status card + "See the math" sheet, upcoming bills, month chart (simple), plan-draft reminder. Later: bento visuals (`HomeBento`, `LiquidOrb`, `MonthRiverCard`), insights, streak/challenge cards.

## Transactions (MVP)

- List (`TransactionListView/ViewModel`): `GET /transactions` with `startDate,endDate` (required), `categoryIds` (comma list), `type`, `search`, `minAmount`, `maxAmount`, `sortBy=date`, `sortOrder=desc`, `limit=20`, `cursor` → `{transactions, pagination{nextCursor, hasMore}}` (`types/api/index.ts:316-383`). Filter sheet: this month / last month / custom range, categories, type, amount range, search.
- Add/edit (`AddTransactionViewModel.swift`, `QuickAddView.swift` 1.6k lines): manual form; `POST /transactions`, `PUT /transactions/{id}`, `DELETE /transactions/{id}`, `POST /transactions/bulk-delete`. Extras: mark as repeating (creates `POST /recurring`), link to a bill / pay a generated bill entry (PUT on the entry with real amount + `templateId`), bill matching suggestions (`Domain/Services/BillMatching.swift`: date window 4 d, name tolerance 20 %, category tolerance 5 %, top 3).
- Categories: `GET/POST /categories`, `PUT/DELETE /categories/{id}`, `POST /categories/batch`; picker with inline create. Category cache in SwiftData (`CategoryCacheStore`) for offline pickers.
- Offline (iOS truth): the list is **network-only**. SwiftData `CachedTransaction` holds queued/failed creates only; `LocalCache` is an empty stub. Queue only when offline **at save time** and only plain creates (repeat/bill-link refused offline, `AddTransactionViewModel.swift:596-600`). Pending rows are shown in Settings → "Not synced" (`OfflineQueueView`), not in the list. Replay on reconnect / login.
- **Bug found:** replay posts `POST /transactions` with `Idempotency-Key`, which the server ignores (`CreateTransactionSchema`, `spenditos/packages/core/src/schemas/transaction.ts:235`). Android replays through `POST /transactions/bulk` + `idempotencyKeyId`. iOS fix offered as a separate task.

## Receipt scan (MVP)

- `ReceiptScanService.swift`: `POST /transactions/upload-url {mimeType:"image/jpeg", documentType:"receipt"}` → `{uploadUrl, fields, documentKey}` → **multipart POST** of `fields` + `file` to S3 (presigned POST policy, no auth header) → `POST /transactions/analyze-image {documentKey}` → `{analysisId}` → poll `GET /transactions/analysis/{id}` every **2 s**, give up after **30 s** → `{status: success|failed|…, result{amount, description, date, merchantName, currency, categoryId, categoryHint}}`. JPEG quality 0.8. Fallback: the user finishes the form manually.
- Gating: Pro or free quota (3/month, server-authoritative `fn/lib/require-pro-or-quota.ts`), `GET /user/quota` for display.

## Voice / text AI capture (Later)

- `AudioEntryService.swift` (900 lines): record with VAD auto-stop (hard stop 60 s), `POST /transactions/analyze-audio`, poll up to 300 s with 1.5× backoff capped at 1 s, capture sessions (`captureSessionId`, `parentAnalysisId`), text parse through the same analyzer (`QuickAddView.swift:836`), multi-item review (`TransactionReviewListView.swift`) confirmed via `POST /transactions/bulk` with `analysisId` + per-item `idempotencyKeyId`.

## Plan: Income & bills (MVP)

- `Features/Plan/PlanHubView.swift` segments "Income & bills" / "Month plan"; list = `Features/Recurring/RecurringTemplateListView.swift`, form = `AddRecurringTemplateSheet.swift`.
- Endpoints: `GET/POST /recurring`, `PUT/DELETE /recurring/{id}`, `POST /recurring/{id}/skip`, `PATCH /user/settings/main-income {mainIncomeTemplateId}`, `POST /transactions/generate` (handler-only, returns `{generated, skipped, errors}`), `GET /categories`, `GET /user/settings`.
- Rules: income vs bill from **category** `isIncome`; frequencies daily…yearly incl. semi-monthly second day (1–31 clamp) and `repeatInterval`; `amountVaries` shows "≈"; "Never" = `occurrencesRemaining: null`. Generate-month preview is client-computed (`RecurringTemplateListViewModel.swift:184-320`, mirrors `fn/jobs/utils.ts:69-222`).
- Legacy `type: "variable"` rows still decode and are delete-only.
- Backend quirk: `POST /recurring` drops `amountVaries` (`fn/api/recurring.ts:295-307`); only PUT persists it. Android: send it on create anyway and PUT if it didn't stick? → open question for the backend (additive fix).
- No offline cache on iOS. Android: read cache (response_cache) so the screen renders offline; writes online-only.

## Plan: Month plan draft (MVP)

- `Features/Planning/MonthlyPlanningView.swift` + `MonthlyPlanViewModel.swift`: months current … +3, default next month, past months discard-only. Autosave debounce 900 ms with in-flight dedupe; empty draft → `DELETE /planning/draft?month=`; month switch flushes first.
- Endpoints: `GET /planning/month?month=`, `POST /planning/checkup`, `GET/PUT/DELETE /planning/draft`, then apply = client-orchestrated idempotent writes: skips (`POST /recurring/{id}/skip`) → logs (`POST /transactions/generate`) → edits (`PUT /transactions/{id}`) → deletes → one-time entries (`POST /transactions` with `idempotencyKeyId = "planning-<uuid>"`); partial failure → Retry.
- Numbers (client-computed): "Left at month end" = income − bills − one-time; "In your records now" vs "Draft ±delta"; "Draft · N changes" pill. **Everyday spending is gone** (backend Phase 7 removed GET fields and `PUT /planning/month/{m}/everyday`); iOS still calls the dead PUT. Android does not port everyday.
- Draft constraints (`fn/core/schemas/monthly-plan-draft.ts:84-240`): ≤100 entries, unique ids, in-month dates, one edit per transaction, one log per template/month. Do not send `budgetDelta`.
- Parity target: iOS `PlanningHarnessE2EUITests` seed numbers.

## Reports (MVP)

- `ReportsHubView` (monthly review + "Where money went" top-3), `DailyAggregatesView` (bars, 7-day moving average, high/low), `MonthComparisonView` (two daily-aggregate calls, weekly buckets, +100 % when previous = 0), `PaycheckPeriodsView` (Covered/Gap/Exact).
- Endpoints: `GET /reports/dashboard?startDate&endDate`, `GET /reports/daily-aggregates` (**double envelope** `{data:{data:[…]}}`, `fn/api/reports.ts:450`), `GET /reports/paycheck-periods` (**server Pro gate**, 403 `ProRequired`).
- Skip: export card (DEBUG-only on iOS), `hasProjected` badge (no TS field), unused models.

## Settings (MVP subset)

- MVP: profile (name, country/state/city, timezone), currency, categories management, change password (`POST /user/change-password`), delete account (`DELETE /user/account`, soft delete), sign out (keeps queue per account, states count), "Not synced" queue view.
- Later: notifications (no backend sender exists yet; `POST/DELETE /user/device-token` already accepts `platform: "android"`), biometric lock, Pro rows.
- Skip: merchant mappings (**contract broken on iOS**: Swift `merchantPattern` vs server `rawPattern` + `cleanMerchantName`, list decode fails), export/import data (DEBUG-only on iOS), `UserService.updateMainIncomeTemplate` (dead, wrong key).
- iOS quirk not to copy: `UserService.updateSettings` drops nulls, so a field can't be cleared.

## Pro (Later)

- iOS: RevenueCat, entitlement `pro`, `offerings.current`, product ids are iOS SKUs. `GET /user/quota` (not in packages/types; `core/services/quota.ts:71-92`). Server truth = `userSettings.premium.active` (webhook-driven).
- **Backend gap:** `fn/webhooks/revenuecat-events.ts:62` tags every non-Stripe store as `rc_ios`, so Play purchases would overwrite the iOS provider snapshot. Needs an additive server change (`rc_android`) before Android billing ships.
- Android v1 without billing still respects server gates: free quota for receipt scan, 403 on paycheck periods → explain Pro, no purchase button (pending owner decision).

## Import (MVP tail)

Entry on iOS: Settings → Import data sheet (`ImportDataView.swift`), both options client-gated on RevenueCat `isPro`.

### Statement import (MVP; covers .csv and .xlsx)
- Wizard (`Features/Import/StatementImportView.swift:30-37`): select file → analyzing → map columns → preview → importing → complete. `ColumnMappingView` seeds from the AI mapping; amount XOR debit/credit.
- Flow (all synchronous, LLM work inside the request; **no polling**):
  1. `POST /transactions/upload-url {mimeType, documentType:"bank_statement"}` → presigned **POST** (same as receipt). iOS forces the xlsx mime for any non-`.csv` file.
  2. `POST /statements/analyze-spreadsheet` → `{mapping, headers, preview}`.
  3. `POST /statements/preview-spreadsheet` → `{transactions, categories}`. **`categoryId` in preview rows carries a category NAME**; resolve case-insensitively against `categories`; unresolved → send none (server falls back to Uncategorized).
  4. `POST /statements/import-spreadsheet` + `Idempotency-Key` header (one UUID per file, reused across retries → deterministic ids `spreadsheet:<key>`) → `{message, count, stats{created, updated, skipped}}`.
- Rules: processable = date + description + (amount | debit + credit) (`StatementImportService.swift:61-65`); description = merchant ?? description; backend stores `abs(amount)`, the category decides the sign.
- Shapes are handler-only zod (`fn/api/statement-import.ts:23-55, 127, 215, 455-500`).
- Gating: **server has no Pro/quota gate on `/statements/*`**; iOS gates client-side. Android v1 (no purchases) gates on `userSettings.premium.active`, same user-visible rule as iOS.
- Android additions: explicit client timeout (e.g. 120 s) on analyze/preview; keep the wizard state in the ViewModel (SavedStateHandle) so rotation/process death doesn't lose it.
- Skip: `POST /statements/detect-recurring` (no iOS caller), `/statements/analyze-image` (unused).

### CSV import (`/import/csv` + `/imports/*`): Skip in v1
- **The iOS flow cannot work against the current backend** (spot-checked): iOS sends `fileName`/`mimeType` (`Domain/Services/ImportService.swift:96-102`) but the server requires `filename`/`contentType` (`fn/api/import-csv.ts:22-37`) → 400; iOS status enums `PENDING/REVIEW/MAPPED…` vs server `waiting_review`, `matched/unmatched/ignored` (`packages/core/src/schemas/import.ts`); Import/Skip buttons are no-ops. No tests.
- Statement import already accepts CSV files, so v1 covers the user need without this path. If the owner wants the reviewed-item CSV flow later, port it from the **server** contract (`fn/api/imports.ts:183-478`, finalize lease + quota reservation), not from iOS. iOS bug worth a separate fix or removal.

## Gamification / real-time / widgets / telemetry (Later, best-effort)

- Real-time: `POST /ws-ticket` → `wss://<WEB_SOCKET_HOST>?ticket=…`; events `user.data_changed` (aggregate invalidation) and `gamification.update`. Android v1: foreground + post-write refresh; WebSocket in a later wave.
- WebSocket details (`Domain/Services/WebSocketManager.swift`): dedupe `user.data_changed` by `eventId` (ring buffer 512); unknown event types ignored; reconnect 1/2/4/8/16 s then 30 s; heartbeat ping every 300 s; envelope parsed in two passes (raw JSON, then typed).
- Gamification (Later, sheet not tab; `Features/Gamification/GamificationHubView.swift`): streak, points, level, challenges, achievements via `GET /user/streak`, `/user/streak/calendar`, `/user/points`, `/user/points/history`, `/user/level`, `/user/challenges/today|history`, `POST /user/challenges/{id}/progress`, `POST /user/streak/freeze`, `GET /achievements`, `/user/achievements[/{id}]`. Every load fails independently and silently. Swift and TS shapes differ for achievements and the streak calendar → mirror the server when ported. No Pro gate.
- Widgets (Later): iOS widget reads numbers the app writes to an App Group; no networking. The streak Live Activity is **never started** on iOS (dead code). Android equivalent later: a Glance widget fed from Room.
- Telemetry (MVP-light): spans `http.client` with method, redacted `url.path`, status, `spenditos.outcome`, `spenditos.error_category`; OTLP/JSON to `POST /telemetry/traces`, bounded in-memory queue (128, flush at 50, no retry). **The allowlist is server-side** (`fn/api/telemetry.ts:89`), so non-allowlisted keys are stripped anyway; Android reuses the same keys.
