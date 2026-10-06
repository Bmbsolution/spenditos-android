# STATUS

Last updated: 2026-10-06 (usage limit hit). Waves A and B built AND Opus-reviewed (subagent, commits df556e5..465b893, 203 tests, lint 0 errors). Wave C workers were still running when work stopped.

## Where to resume
Read in order: `/Users/asaklex/dev/Spenditos-Project/CLAUDE.md`, `AGENTS.md`, then `docs/PLAN.md`, `docs/ARCHITECTURE.md`, `docs/PARITY.md`, `docs/DECISIONS.md`, `ANDROID_STYLE.md`, `README.md`, this file.

## Done
- **Phase 0** plan/parity/architecture/decisions (owner approved). **Phase 1** skeleton, shared layers, transactions slice, emulator verified (incl. real staging sign-in), baseline `8d26aba`.
- **Wave A** (4 codex workers, one worktree each, orchestrator compiled/tested/merged; commits `f2b44da`…`32135e4`):
  - A1 Categories management, Profile (name, currency), Change password; Settings rows.
  - A2 Reports: hub, daily aggregates, month comparison, paycheck periods (Pro 403 state).
  - A3 Onboarding quick wizard + launch gate. Gate follows iOS: per-user "completed on this device" flag (`OnboardingFlagStore`); any failed check, including 404 for an account with no settings yet, SHOWS onboarding. (The worker built "fail-open"; my spec had misread iOS; fixed in `32135e4`.)
  - A4 Register, Verify email, Forgot password screens + Login links; signed-out flow wired in `AppRoot`.
- **Prefill hook** (`7beb0de`): `AddTransactionPrefill` + `applyPrefill`, "Scan a receipt" button (needs `onScanReceipt`), FileProvider (`cache/receipts`) + IMAGE_CAPTURE query. For Wave B receipt scan.
- Verified: **139/139 unit tests**, lint clean, staging+harness build. Emulator (harness): Reports hub/daily/comparison numbers match the transactions tab; Settings/Profile/Categories render; fresh account shows the onboarding wizard.

## Wave B (done; commits `f1f9f5e`…`42dc0a8` + wiring)
- B1 Income & bills: list (Income/Bills), add/edit/delete, main-income star, Generate a month (client preview via `RecurrenceMath`, `POST /transactions/generate`), `amountVaries` follow-up PUT (server drops it on create), legacy `variable` rows delete-only. In-memory per-user cache.
- B2 Receipt scan: camera (system app + FileProvider) or gallery → resize/EXIF/JPEG 80 → `upload-url` → S3 multipart POST (separate client, no auth) → `analyze-image` → poll 2 s × 15 → `AddTransactionPrefill`. Quota exhausted = 403 `QuotaExhausted` → "used this month's free scans", no purchase button.
- Wiring (`MainScaffold`): Plan tab = `IncomeAndBillsScreen()`; add sheet "Scan a receipt" → `receipt-scan` route → sheet reopens with `receiptPrefill`.
- Orchestrator fixes: missing `dp` import; locale lint; B2 polling rewritten count-based (virtual-time safe); injected `@ReceiptIoDispatcher`; receipt screen insets.
- Verified: **190/190 unit tests**, lint clean. Emulator (harness): Plan tab shows seeded income/bills with next dates; add sheet shows Scan a receipt; scan screen renders (harness has no S3, so the real upload/analyze path is covered by MockWebServer tests only; real scan needs staging + a photo).
- Spec for Wave B: `$SCRATCHPAD/specs/waveB`.

## OPUS REVIEW BATCH (owner asked for switches only at review points; do ONE batch after Wave B)
Review for correctness against the server contract and iOS, security, and idempotency; fix findings, then switch back to Sonnet.
1. New wire models (contract review): `data/model/{Account,Onboarding,CategoryRequests,UserSettingsUpdate,Reports,Recurring,Receipt}.kt`: field names/nullability vs `packages/types` and handlers; `UserSettingsUpdate.displayName: JsonElement` (explicit null clears); Reports double envelope; Recurring `type` as String.
2. Auth-adjacent: `data/auth/Account*`, `feature/auth/{Login,Register,VerifyEmail,ForgotPassword}*` (passwords only in memory; no token access; no auto sign-in), `app/AppRoot.kt` signed-out flow, change password (`UserSettingsRepository.changePassword`, `ChangePasswordViewModel`).
3. Writes (Wave B items marked B): `CategoryRepository.create/update/delete`; `OnboardingRepository.complete` (seed only if categories empty; double PUT workaround for setupCompleted); B: `RecurringRepository` create/update/delete/setMainIncome and `generate(month)`: it sends `"$month-01"` while iOS sent day 15 (RecurringTemplateListView.swift:582-588): check the handler's date/timezone handling; `pausedAt = Instant.now()` on inactive create; user scoping (`activateUser/ensureCurrentUser`). B: receipt upload (separate client, no auth header, no logging), temp-file deletion, `ReceiptRepository` poll semantics vs iOS, 403 `QuotaExhausted` mapping.
4. Gate semantics: `OnboardingRepository.needsOnboarding` vs iOS `checkOnboardingStatus` (`SpenditosApp.swift:329-375`).
No queue/sync or token-storage code changed in Wave A (`core/**` untouched since Phase 1).

## Opus review of Waves A+B: DONE (see docs/REVIEW-WAVE-A-B.md)
2 must-fix + 6 should-fix fixed with tests. OPEN, needs the owner:
- O2 (backend): POST /recurring has no idempotency key; a lost response + retry duplicates a bill/income. Fix = accept `idempotencyKeyId`/client id like /transactions/bulk. iOS has the same flaw.
- O3 (Android behaviour): iOS and web log the current month right after creating a recurring item; Android does not (needs owner go-ahead, creates transactions; send day 15 not day 1).
- O4 (core + iOS): unmapped server error codes show verbatim; preferring the server's readable message touches core/network/ApiCall.kt serverMessage (FailureClassifier and receipt/quota mapping rely on codes, so change carefully).
- N1: previous account's screen state may linger in memory after sign-out until the app closes (no cross-account data shown).

## Wave C: DONE and merged (Home, Month plan, Plan hub)
- Home: `CashFlowStatus` port; parity verified: left after bills 1,838.50, 21% income remaining; per-day figure depends on today (pinned in unit tests to 2026-10-01 -> 59.31).
- Month plan draft (apply/discard): reviewed by codex + GLM read-only passes (no Opus). Fixes: skip regeneration when an override already moved an occurrence (server dedupes generate by scheduled date; iOS has the same duplicate flaw); freeze one-time entries after an unfinished Apply (server derives ids from entry details); rows/summary now computed from the composed state (fixed an empty-plan bug found on the emulator); locale lint.
- Plan tab = `PlanHubScreen` (segmented Income & bills / Month plan).
- O3 DONE (owner approved): a NEW recurring item is logged from today onward for this month (`POST /transactions/generate` with templateIds + fromDate, mid-month target), best effort.
- Owner decisions on review items: O2 ignore for now; O4 not requested (left open).
- 254 unit tests, lint clean. Emulator (harness): Home, Plan hub, Month plan checked.
- Review process going forward (owner asked to avoid Opus cost): codex (xhigh/high) + GLM read-only reviews of each diff, orchestrator verifies claims, fixes; Opus only if a question stays unresolved.

## Remaining: Wave D Statement import (financial write; spec per docs/PARITY.md 'Statement import'), then hardening. Polish backlog: month plan action buttons wrap, Home top inset, Income & bills row layout.

## Waiting on the owner
- Nothing blocking.

## Remaining plan
Wave C: Month plan draft, Home. Wave D: Statement import. Hardening: accessibility, dark mode, process-death tests, R8 release build, emulator smoke of every tab. Later (v1.x): voice/text capture, purchases, realtime, gamification, biometric lock, push, widgets.
Polish backlog (cosmetic): Income & bills rows (inline Edit/Delete, tighter layout), categories rows (color dot, inline Edit/Delete), month comparison "Net" figure crowding its percentage, FAB overlaps list tails, tab label contrast.

## Known harness limits
The backend local e2e harness does not serve `POST /categories` (404) or `PUT /user/settings` / country-detection for new users, so those writes are covered by MockWebServer tests, not on-device. A fresh user id (any `e2e-<uuid>` token) gets 404 on settings, which is how the onboarding wizard was exercised (temporary token edit, reverted).

## Repos, branches, worktrees, processes
- `spenditos-android/`: git repo, branch `main`, local commits only (no remote, never push). Head: see `git log`.
- `spenditos`: `develop` @ `384235b` (4 behind origin), working tree clean. Scratch detached worktree `$SCRATCHPAD/audit/spenditos` (origin/develop `4acac33`).
- `spenditos-ios`: untouched.
- Backend harness and emulator were STOPPED (2h background limit). Restart: harness `pnpm --filter @spenditos/functions run serve:e2e` (from ../spenditos, needs Docker), emulator `emulator -avd Pixel_3a_API_34_extension_level_7_arm64-v8a`, then `adb reverse tcp:4310 tcp:<harness port>`. Previously running: backend e2e harness (`serve:e2e`, port in its log; `adb reverse tcp:4310 → port`), emulator `Pixel_3a_API_34_extension_level_7_arm64-v8a`. The installed harness APK currently uses a FRESH-USER token (onboarding test); reinstall `:app:installHarnessDebug` from `main` to restore the seeded Home user.
- Pending side task (owner's choice): "Fix iOS offline replay duplicate transactions".
- delegate.sh: the codex sandbox cannot run Gradle (blocks sockets); workers write code, the orchestrator verifies; `DELEGATE_BASE=<branch>` starts a worktree from a worker branch for second passes.
