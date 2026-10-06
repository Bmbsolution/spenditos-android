# STATUS

Last updated: 2026-10-06, Phase 2 Waves A and B done; **waiting for the owner to switch to Opus for the review batch** (Sonnet orchestrated).

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

## Waiting on the owner
- Switch the session model to **Opus**, then run the review batch above. Afterwards switch back to Sonnet for Wave C (Month plan draft, Home) and Wave D (Statement import).

## Remaining plan
Wave C: Month plan draft, Home. Wave D: Statement import. Hardening: accessibility, dark mode, process-death tests, R8 release build, emulator smoke of every tab. Later (v1.x): voice/text capture, purchases, realtime, gamification, biometric lock, push, widgets.
Polish backlog (cosmetic): Income & bills rows (inline Edit/Delete, tighter layout), categories rows (color dot, inline Edit/Delete), month comparison "Net" figure crowding its percentage, FAB overlaps list tails, tab label contrast.

## Known harness limits
The backend local e2e harness does not serve `POST /categories` (404) or `PUT /user/settings` / country-detection for new users, so those writes are covered by MockWebServer tests, not on-device. A fresh user id (any `e2e-<uuid>` token) gets 404 on settings, which is how the onboarding wizard was exercised (temporary token edit, reverted).

## Repos, branches, worktrees, processes
- `spenditos-android/`: git repo, branch `main`, local commits only (no remote, never push). Head: see `git log`.
- `spenditos`: `develop` @ `384235b` (4 behind origin), working tree clean. Scratch detached worktree `$SCRATCHPAD/audit/spenditos` (origin/develop `4acac33`).
- `spenditos-ios`: untouched.
- Running: backend e2e harness (`serve:e2e`, port in its log; `adb reverse tcp:4310 → port`), emulator `Pixel_3a_API_34_extension_level_7_arm64-v8a`. The installed harness APK currently uses a FRESH-USER token (onboarding test); reinstall `:app:installHarnessDebug` from `main` to restore the seeded Home user.
- Pending side task (owner's choice): "Fix iOS offline replay duplicate transactions".
- delegate.sh: the codex sandbox cannot run Gradle (blocks sockets); workers write code, the orchestrator verifies; `DELEGATE_BASE=<branch>` starts a worktree from a worker branch for second passes.
