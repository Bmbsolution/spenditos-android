# Opus review: Waves A and B

Date: 2026-10-06. Base: `44ec917`. Reviewer: Claude Opus 5.5.
References: iOS `spenditos-ios/Spenditos/Spenditos/` (read only); backend `origin/develop` `4acac33` (`packages/functions`, `packages/core`, `packages/types`, `packages/web`).
App paths below are relative to `app/src/main/java/com/spenditos/android/`. Line numbers are at the fixing commit.

Verification after the fixes: `./scripts/verify.sh` rc=0, **203 unit tests, 0 failures**; lint 0 errors, 8 warnings (all in code that predates this review: 4 UseKtx, 2 ExifInterface, 1 OldTargetApi, 1 Compose naming in `ProfileScreen.kt:191`); `assembleStagingDebug` OK.

## Findings

| ID | Severity | Area | File:line | What is wrong | Evidence (backend / iOS) | Status |
|----|----------|------|-----------|---------------|--------------------------|--------|
| R1 | MUST-FIX | Recurring generate | `data/recurring/RecurringRepository.kt:133,178` | `generate(month)` sent `"<month>-01"`. The handler does `new Date(targetDate)` (UTC midnight) and the generator reads `getFullYear()/getMonth()` in the Lambda's local zone, so day 1 generates the **previous month's** entries in any runtime behind UTC (the local e2e harness on this Mac; any stage that sets `TZ`). Deployed Lambdas run in UTC, so production is not hit today, but correctness of a financial write depended on server zone config. Now sends `<month>-15`. | `packages/functions/src/api/transactions.ts:1288-1291`, `jobs/utils.ts:225-252`; iOS `Features/Recurring/RecurringTemplateListView.swift:582-588` (day 15); web `packages/web/src/app/settings/recurring/PageContent.tsx:355` (`-15`) | FIXED `df556e5` |
| R2 | MUST-FIX | Recurring create | `data/recurring/RecurringRepository.kt:69-98` | After `POST /recurring` committed, a failed compatibility PUT (`amountVaries`, or `pausedAt` for an inactive create) made `create()` throw, the sheet showed "Couldn't save changes. Please try again.", and a second Save created a **duplicate template**, which doubles its generated entries every month (generation ids are per template). The PUTs are now best effort; the committed template is returned and the list is refreshed. `isActive=false` is already saved by the POST (`recurring-template.ts:100`), so only the cosmetic fields can be lost. | `packages/functions/src/api/recurring.ts:295-307` (create drops `amountVaries`), `packages/core/src/repositories/recurring-template.ts:80-119`, `jobs/generation-logic.ts` (id = template + date) | FIXED `df556e5` |
| A1 | SHOULD-FIX | Auth flow | `app/AppRoot.kt:45-73,121-176` | Signed-out screens used Activity-scoped ViewModels. After a password reset the Forgot screen reopened stuck on the finished step (`isDone`, both buttons disabled) until the app was killed; a second verification reopened as already verified; typed passwords stayed in memory for the Activity's life. Each step now gets a `ViewModelStore` held by `RootViewModel` (kept on rotation, cleared on step change and on sign-in). | n/a (Android lifecycle); iOS `AuthViewModel` resets per flow | FIXED `4d88a6f` |
| A2 | SHOULD-FIX | Onboarding gate | `app/AppRoot.kt:108-113` | `OnboardingViewModel` was Activity-scoped, so a second account signing in on the same device (same Activity) saw the first account's wizard state, including its display name, and could save it. Now `hiltViewModel(key = "onboarding:<userId>")` (keeps SavedStateHandle across process death). | iOS `SpenditosApp.swift:329-375` scopes onboarding by `userId` | FIXED `4d88a6f` |
| A3 | SHOULD-FIX | Error copy | `feature/auth/RegisterViewModel.kt:68-84`, `feature/settings/ChangePasswordViewModel.kt:67,79-83`, `feature/settings/categories/CategoriesManagementViewModel.kt:166,183-186` | `ApiError.Server.serverMessage` is the envelope `error` code when present, so users saw raw codes: "InvalidPassword" for a wrong current password, "UserAlreadyExists", "CodeMismatch", "ExpiredCode", "CategoryHasReferences". Known codes now map to plain copy; unknown codes still show the server text. (Same behaviour exists on iOS; see O4.) | `auth.ts:282-299,1028-1052,1093-1140`; `user.ts:797-812`; `categories.ts:472,564-576`; `core/network/ApiCall.kt:47`; iOS `APIClient.swift:420-423` | FIXED `0ab198f` |
| C1 | SHOULD-FIX | Receipt privacy | `feature/receipt/ReceiptImageProcessor.kt:33-50,125` | Startup cleanup removed only `receipt-scan-*`. A `receipt-camera-*` original (full resolution, with EXIF location) stayed in the cache indefinitely if the app was killed while the camera app was open. Camera files older than 1 h are now removed; recent ones stay because a camera result can still arrive after process death. | n/a (Android); iOS keeps the image in memory only | FIXED `73c2192` |
| P1 | SHOULD-FIX | Reports math | `feature/reports/MonthComparisonViewModel.kt:136-150` | The Net row divided by a negative previous month: net -200 to -100 showed "-50%" in the "better" colour. Now measured against `abs(previous)`; positive baselines unchanged (iOS rule); 0 to a deficit reads "-100%" instead of "0%". | iOS `MonthComparisonViewModel.swift:69-76` (`guard previous > 0`, also wrong for negative net) | FIXED `78d21b5` |
| O1 | SHOULD-FIX | Onboarding | `data/onboarding/OnboardingRepository.kt:87-95` | `complete()` rethrew a failed settings/categories cache refresh that runs after `setupCompleted` was saved and the local flag set, so a finished setup showed "Couldn't finish setup". Refresh is now best effort. | iOS `OnboardingView.swift:895-951` ignores all errors | FIXED `f451d29` |
| A4 | NIT | Auth | `feature/auth/RegisterViewModel.kt:52`, `ForgotPasswordViewModel.kt:52,67` | Register and forgot-password sent the email untrimmed (sign-in trims), so a trailing keyboard space failed `z.string().email()` with a 400. | `auth.ts:59-94` | FIXED `4d88a6f` |
| O2 | OPEN (backend) | Recurring create idempotency | `data/recurring/RecurringRepository.kt:69` | `POST /recurring` has no idempotency key; a response lost after commit (timeout) plus a retry creates a duplicate template. iOS has the same exposure. R2 removes the client-caused case only. **Backend change needed:** accept an idempotency key on `POST /recurring` (as `POST /transactions/bulk` does with `idempotencyKeyId`), or a client-supplied template id. | `recurring.ts:238-386`, `recurring-template.ts:85` (`randomUUID()`) | OPEN |
| O3 | OPEN (owner) | Recurring parity | `feature/recurring/IncomeAndBillsViewModel.kt:100-103` | iOS and web log the current month for a new item right after create: `POST /transactions/generate {targetDate: month start, templateIds: [id], fromDate: today}` (#336 Phase 1). Android does not, so a new bill/income has no entries this month until the monthly job or a manual Generate. It creates transactions, so it needs the owner's go-ahead. Note iOS sends the month start there (same zone caveat as R1); the robust fix is server side: parse `targetDate` as a calendar date (`transactions.ts:1289`). | iOS `AddRecurringTemplateSheet.swift:628-658`; web `settings/recurring/PageContent.tsx:411` | OPEN |
| O4 | OPEN (owner) | Error copy, both clients | `core/network/ApiCall.kt:47` | `serverMessage = error ?: message` (iOS parity) means any unmapped code is still shown verbatim. A3 maps the common ones per screen. A general fix (prefer `message` for display, keep `error` for classification) touches `core/**` and FailureClassifier, so it is left for a deliberate change on both platforms. | iOS `APIClient.swift:420-423` | OPEN |
| N1 | PLAUSIBLE | Nav lifecycle | `app/MainScaffold.kt:79` | `rememberNavController()` inside `key(userId)`: when a user signs out, the old controller's back-stack ViewModels are probably not cleared until the Activity finishes (Navigation keeps them in an Activity-scoped `NavControllerViewModel`). They hold no other account's data on screen and repositories scope by the current user, so the effect is wasted work and memory. Not verified at runtime; not changed. | n/a | OPEN |
| N2 | NIT | Recurring edit | `feature/recurring/IncomeAndBillsScreen.kt:288` | Editing any inactive item re-stamps `pausedAt = now`, so "paused since" moves on every edit, and a stopped item becomes paused. iOS never sends `pausedAt`. | `recurring.ts:545-551` | OPEN |
| N3 | NIT | Recurring form | `feature/recurring/AddEditRecurringSheet.kt:67` | "0 occurrences remaining" is sent as `null` (unlimited), so editing an exhausted item clears its cap. Same as iOS `occurrencesRemainingPayload` (`AddRecurringTemplateSheet.swift:17-23`). | iOS parity | OPEN |
| N4 | NIT | Recurring math | `feature/recurring/RecurrenceMath.kt:58-61` | Monthly steps accumulate clamping (Jan 31, Feb 28, Mar 28...). This matches the backend exactly (`jobs/utils.ts:126-140`, date-fns `addMonths` from the previous date), so the preview equals what is generated; the drift itself is a backend behaviour to decide on. The Generate preview also ignores `occurrencesRemaining` caps (display only). | `jobs/utils.ts:126-140` | OPEN |
| N5 | NIT | Memory | `data/recurring/RecurringRepository.kt:46` | `savedByUser` keeps previous accounts' templates in memory for the process lifetime (never shown: `observe()` filters by the signed-in user). | n/a | OPEN |
| N6 | NIT | Receipt | `app/MainScaffold.kt:85`; `feature/receipt/ReceiptImageProcessor.kt:7` | The receipt prefill is `remember` (not saved), so rotating the add sheet drops the scanned values (deliberately not put in SavedState). `android.media.ExifInterface` (lint warning) handles fewer formats than `androidx.exifinterface`. | n/a | OPEN |
| N7 | NIT | Coroutines | `feature/auth/*ViewModel.kt`, `feature/settings/*ViewModel.kt`, `app/SessionLifecycle.kt` | `runCatching { suspend call }` also catches `CancellationException`. Every case is a one-shot call whose failure branch only updates state, so nothing loops or hides work; noted for the style guide. | n/a | OPEN |

Counts: MUST-FIX 2 (fixed 2), SHOULD-FIX 6 (fixed 6), NIT 8 (fixed 1), OPEN for the owner/backend 3 (O2, O3, O4), PLAUSIBLE 1 (N1).

## Verified OK

1. **Wire models**
   - `Account.kt` matches `auth.ts:59-109` (register `timezone` optional, max 50; `RegisterResponse {userId, needsConfirmation}` in `{data}` at `auth.ts:376-383`); the account routes use the `@PublicApi` Retrofit (no bearer).
   - `Onboarding.kt`: the PUT body fits `UpdateUserSettingsSchema` (`core/schemas/user.ts:160-176`); `POST /categories/batch` takes a bare array (`categories.ts:237-256`).
   - `CategoryRequests.kt`: `icon` is optional, not nullable, server side, and a null is omitted (`explicitNulls = false`).
   - `UserSettingsUpdate.displayName` sends JSON `null`. The schema allows it (`DisplayNameSchema.nullable().optional()`) and the repository persists it (`user-settings.ts:175-204`).
   - `Reports.kt`: daily aggregates double envelope (`reports.ts:450`), dashboard and paycheck shapes, ISO dates.
   - `Recurring.kt`: `type`/`frequency` as String, nullable `occurrencesRemaining`; create/update fit `CreateRecurringTemplateSchema`/`UpdateRecurringTemplateSchema` (`core/schemas/transaction.ts:173-224`); `Instant.toString()` satisfies `z.string().datetime()`; the `207 EntriesNotSynced` body still decodes.
   - `Receipt.kt` matches `document-analysis.ts` upload, analyze (202) and poll (`pending|processing|failed|success`).
2. **Auth-adjacent code**
   - Passwords live only in ViewModel `StateFlow`s: no `SavedStateHandle`, no `rememberSaveable`, no logging. They are cleared after success.
   - `AccountRepository` never touches `SessionManager` or the token store. Register and verify return to sign-in; there is no automatic sign-in.
   - Forgot-password and resend-code enumeration protection is server side (`auth.ts:948-975,1212-1216`); Android does not reveal more than iOS.
   - Change password goes through the authed client. A wrong current password is a 400, not a 401, so it never triggers refresh or sign-out.
   - Only the verify step's email goes into SavedState.
3. **Onboarding**
   - `needsOnboarding` matches iOS `checkOnboardingStatus`: per-user local flag, the server is the source of truth, it fails open, and a null `userId` shows onboarding.
   - `complete()` seeds only when `GET /categories` is empty, and is safe to retry after a partial failure (test `retry checks categories before seeding again`).
   - The double PUT runs at most twice and then throws, so it cannot loop.
   - The flag is namespaced per user and survives sign-out.
4. **Category writes**: user scoped via `currentUserId`. The cache is updated only after a successful response. 4xx/409 leave the cache untouched. A follow-up refresh failure keeps the write successful, and `CATEGORY` is invalidated.
5. **Recurring**
   - Generation is idempotent server side (deterministic id `userId + templateId + date`, `generation-logic.ts`), so retrying a month is safe.
   - `activateUser`/`ensureCurrentUser` hide other accounts' rows and drop results that arrive after an account switch.
   - `RecurrenceMath` matches `jobs/utils.ts:69-222`: weekly, bi-weekly, daily, monthly, quarterly, semi-annual and yearly stepping with `repeatInterval`; semi-monthly alignment, clamping, dedupe, the default second day and the before-start filter; skipped dates.
   - Amounts are parsed as `BigDecimal` and dates stay `LocalDate`. The DatePicker uses UTC only at the picker boundary.
6. **Receipt scan**
   - S3 upload uses a separate `OkHttpClient` with no interceptors, so no `Authorization` header and no logging. Policy fields go before `file`, matching iOS.
   - The temp copy is deleted on success, failure and cancel, and at startup after process death. The re-encode strips EXIF. Nothing is persisted.
   - Polling matches iOS: 2 s interval, 30 s window, the count bounds the loop.
   - `403 QuotaExhausted` maps correctly (`core/services/quota.ts:112-123` sends `error: "QuotaExhausted"`).
7. **Reports**: sums use `BigDecimal`. `403 ProRequired` maps to the Pro state (`lib/require-pro.ts:110-114`). Reports are not cached.
8. **Prefill**: `applyPrefill` only fills the form (valid date, an existing category, positive amount), and saving still needs the user's tap. A scan never creates a transaction.
9. **Whole app**
   - No `Log`/`println`. OkHttp logging is BASIC, debug only, with `Authorization` redacted.
   - Cleartext is allowed only for `127.0.0.1` in the harness flavor.
   - Only the launcher Activity is exported (the FileProvider is not). Backup is off. No secrets in `config/`.
   - Every Room query takes `userId`. Explicit `catch (Exception)` blocks rethrow `CancellationException`.
