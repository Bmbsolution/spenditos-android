# STATUS

Last updated: 2026-10-06, Phase 2 Wave A done, Wave B running (Sonnet orchestrating).

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

## Wave B in progress (codex workers, worktrees under `$SCRATCHPAD/runs/*codex-76523|76525`)
- B1 Income & bills (recurring list/add/edit/delete, main income, generate month; `RecurrenceMath` port).
- B2 Receipt scan (camera/gallery → upload-url → S3 multipart → analyze-image → poll 2 s/30 s → prefill).
- Specs: `$SCRATCHPAD/specs/waveB/*.md`. After they finish: compile/test each worktree with `scripts/verify.sh <wt>`, merge, wire `MainScaffold` (Plan tab → `IncomeAndBillsScreen()`; sheet `onScanReceipt` → `ReceiptScanScreen` → reopen sheet with `prefill`), commit.

## OPUS REVIEW BATCH (owner asked for switches only at review points; do ONE batch after Wave B)
Review for correctness against the server contract and iOS, security, and idempotency; fix findings, then switch back to Sonnet.
1. New wire models (contract review): `data/model/{Account,Onboarding,CategoryRequests,UserSettingsUpdate,Reports,Recurring,Receipt}.kt`: field names/nullability vs `packages/types` and handlers; `UserSettingsUpdate.displayName: JsonElement` (explicit null clears); Reports double envelope; Recurring `type` as String.
2. Auth-adjacent: `data/auth/Account*`, `feature/auth/{Login,Register,VerifyEmail,ForgotPassword}*` (passwords only in memory; no token access; no auto sign-in), `app/AppRoot.kt` signed-out flow, change password (`UserSettingsRepository.changePassword`, `ChangePasswordViewModel`).
3. Writes: `CategoryRepository.create/update/delete`; `OnboardingRepository.complete` (seed only if categories empty; double PUT workaround for setupCompleted); Wave B recurring writes + `POST /transactions/generate` safety; receipt upload (separate client, no auth header, no logging) and temp-file deletion.
4. Gate semantics: `OnboardingRepository.needsOnboarding` vs iOS `checkOnboardingStatus` (`SpenditosApp.swift:329-375`).
No queue/sync or token-storage code changed in Wave A (`core/**` untouched since Phase 1).

## Waiting on the owner
- Nothing blocking. At the end of Wave B I will ask for the Opus switch for the batch above.

## Remaining plan
Wave C: Month plan draft, Home. Wave D: Statement import. Hardening: accessibility, dark mode, process-death tests, R8 release build, emulator smoke of every tab. Later (v1.x): voice/text capture, purchases, realtime, gamification, biometric lock, push, widgets.
Polish backlog (cosmetic): categories rows (color dot, inline Edit/Delete), month comparison "Net" figure crowding its percentage, FAB overlaps list tails, tab label contrast.

## Known harness limits
The backend local e2e harness does not serve `POST /categories` (404) or `PUT /user/settings` / country-detection for new users, so those writes are covered by MockWebServer tests, not on-device. A fresh user id (any `e2e-<uuid>` token) gets 404 on settings, which is how the onboarding wizard was exercised (temporary token edit, reverted).

## Repos, branches, worktrees, processes
- `spenditos-android/`: git repo, branch `main`, local commits only (no remote, never push). Head `7beb0de`.
- `spenditos`: `develop` @ `384235b` (4 behind origin), working tree clean. Scratch detached worktree `$SCRATCHPAD/audit/spenditos` (origin/develop `4acac33`).
- `spenditos-ios`: untouched.
- Running: backend e2e harness (`serve:e2e`, port in its log; `adb reverse tcp:4310 → port`), emulator `Pixel_3a_API_34_extension_level_7_arm64-v8a`. The installed harness APK currently uses a FRESH-USER token (onboarding test); reinstall `:app:installHarnessDebug` from `main` to restore the seeded Home user.
- Pending side task (owner's choice): "Fix iOS offline replay duplicate transactions".
- delegate.sh: the codex sandbox cannot run Gradle (blocks sockets); workers write code, the orchestrator verifies; `DELEGATE_BASE=<branch>` starts a worktree from a worker branch for second passes.
