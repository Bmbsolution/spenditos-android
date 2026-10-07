# STATUS

Last updated: 2026-10-07. Wave K (exact iOS visual parity) merged. Waves A-D built, verified and merged (288 unit tests, lint clean). Hardening pass DONE. Wave E (voice + typed capture) DONE. Release builds are unsigned (signing/publishing is the owner's step).

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

## Wave D: DONE and merged (Statement import)
- Flow: Settings > Import statement; pick CSV/XLSX (25 MB cap, streamed copy) -> `upload-url` -> S3 POST (unauthenticated client, fields first) -> analyze -> map columns -> preview -> import with `Idempotency-Key` (one UUID per picked file, reused on retries; new key after a completed import). Gated on `premium.active`, no purchase button. Temp file always deleted. Saved flow is bound to the user id (another account starts clean).
- Review: codex (xhigh) read-only; GLM returned nothing usable. Fixed: account switch with a saved flow, temp-file leak on a cancellation race. Verified in code: server derives per-row ids from key + row index, so a same-key replay skips rows already created.
- Open, shared with iOS (server behavior, not changed): with a key the server skips its fuzzy duplicate reconciliation, so importing an overlapping statement as a NEW import can create duplicates; the key store keeps no body fingerprint; the Pro gate is client-side only (server has no gate on /statements/*); dates go through server-local JS Date parsing; amounts are Doubles end to end.
- Not on-device: the harness user is not Pro and has no S3, so only the Pro-gate screen was checked on the emulator; analyze/preview/import are covered by MockWebServer + ViewModel tests (incl. process-death restore).

## Wave E: DONE and merged (Voice & typed capture)
- Add sheet > "Type or speak" > Describe it: Type (sentence) or Speak (MediaRecorder AAC/MP4, 60 s cap, mic permission with settings fallback, no-speech check). Typed text goes up as a tiny text/plain doc; voice is transcribed first, the transcript is editable, then parsed (parentAnalysisId). Review cards (include, amount, description, date, category) -> ONE `POST /transactions/bulk` with a persisted `idempotencyKeyId` per item + source voice/text + analysisId. Items freeze once a save was sent; retries reuse the same keys; online-only (no offline queue).
- Review (codex, read-only) fixes applied: capture bound to the signed-in user, category revalidated at save, parsed amounts no longer silently rounded, MediaRecorder released on start failure + recorder-level 60 s cap, recording stops when the screen backgrounds.
- 312 unit tests, lint clean. Emulator (harness): screen, permission denied path, recording timer, "No speech detected" path, calm error when the harness has no analysis endpoints. Not tested end to end: real analysis (needs staging/real audio), quota-exhausted copy on device.
- Emulator note: it only boots here headless: `emulator -avd <avd> -no-window -gpu swiftshader_indirect -no-audio -no-boot-anim -no-snapshot-save` (the windowed launch hangs); then `adb reverse tcp:4310 tcp:<harness port>`.

## Wave F: DONE (app lock, Progress, realtime, widget)
- **App lock** (core/lock + feature/lock): device-level switch in Settings ("App lock", shown only when a fingerprint/face/screen lock exists; switching on first proves you can pass it). Locks on cold launch of a restored session and after 30 s in the background (grace so camera/picker/permission trips don't relock; iOS has no grace), BiometricPrompt with BIOMETRIC_WEAK|DEVICE_CREDENTIAL (the combination valid on API 28-29). Content under the lock is hidden from accessibility services too. If the device lock is later removed the setting turns itself off. MainActivity is now a FragmentActivity (BiometricPrompt needs it). Verified on device with a PIN.
- **Progress** (feature/progress, data/gamification): Settings > Progress (streak, calendar, level/points, challenge, achievements, history; each section fails independently) + a StreakChip on Home (hidden at zero). Codex wrote it. Verified on device against the harness (level/achievements/history 404 there = quiet "Couldn't load").
- **Realtime** (data/realtime): foreground-only WebSocket: ticket, dedupe by eventId, backoff, two-pass parse; inert when BuildConfig.WEB_SOCKET_URL is empty (harness/local flavors) so NOT exercised on device. Codex wrote it; its tests found a real bug (OkHttp HttpUrl rejects wss://) which I fixed.
- **Widget** (feature/widget, Glance): "this month at a glance" from the Room cache; refresh on leaving the foreground + a 30-min WorkManager job. Verified on device: shows the same state/amount/days as Home.
- **Core bug found on device and fixed: auth refresh deadlock.** The refresh client shared OkHttp's dispatcher with the authed client (5 calls per host); 5+ simultaneous requests with a stale token blocked every slot waiting for a refresh that could not start (first hit by the Progress screen's 8 parallel loads at a 20 s token TTL). The refresh client now has its own dispatcher.
- 352 unit tests, lint clean, staging+prod R8 release builds succeed (unsigned).
- Still needs the owner (code is built, inert): Firebase `google-services.json` + a backend push sender; RevenueCat Android key + Play products + a backend webhook source for Play; signing keys / Play Console. CSV import via `/imports/*` (reviewed-item flow, server contract) is the one iOS-adjacent feature not built.

## Wave G: DONE (Home insights, release signing)
- **Home insights** (feature/home/insights): Month River (money in/out through the month, actual to today then dashed scheduled bills/paychecks, pins, shortfall note), Next bill, Last 7 days, Where everyday money went. Port of iOS HomeInsights (all iOS test cases ported). Codex wrote it; verified on device on the harness data. The streak tile of iOS is the Home StreakChip. Note the River card's own "Coming up" list sits above the older "Coming up" bills card on Home; harmless duplication, remove one if it feels busy.
- **Release signing**: `signingConfigs.release` from env only (SPENDITOS_KEYSTORE_PATH/_PASSWORD/_KEY_ALIAS/_KEY_PASSWORD); without it the AAB is unsigned. `docs/RELEASE.md` has the steps and what is still the owner's (Play Console, Firebase/push, billing, CI). `bundleProdRelease` builds an unsigned 8.2 MB AAB.

## Wave H: DONE (Financial Pulse hero, spending orb, health score)
- The Home status card is now the iOS "Financial Pulse" hero (feature/home/pulse): weather-coloured gradient (On track / Watch your pace / Getting tight / Overspending / Add your income), the animated LiquidOrb (income left after bills; still when system animations are off), "About $X a day" or "Trim ≈ $X a day" chip, the iOS reason sentence, income/spent/bills strip, See the math, Review-your-plan / Add-income buttons (go to the Plan tab), and, when tapped open, a health score badge (ring + savings rate + spending trend + tips, sheet) and a 7-day cash flow sparkline (from the Room cache, spanning the month boundary). Written directly (Codex was rate-limited until 11:00) and verified on device. Health score/trend/copy are exact ports of the iOS logic with tests (PulseLogicTest).
- 370 unit tests, lint clean.

## Wave I: DONE (push registration, export, merchant names, Pro purchases)
- **Push** (data/push): FCM token registration to `POST /user/device-token` (platform android) behind Settings > Notifications (asks POST_NOTIFICATIONS, registers on switch-on / session start / token rotation, unregisters on switch-off and sign-out), a messaging service that shows a calm notification, and deep links (iOS type mapping: streak/add -> add sheet, challenge/achievement/level -> Progress). INERT without `app/google-services.json` (plugin applied only if the file exists; Firebase logs one harmless warning). No backend sender exists yet. 7 tests.
- **Export** (feature/export): Settings > Export transactions: presets + custom range (max 2 years like the server), `GET /transactions/export` (raw CSV), saved to cache/exports with a sanitized server filename and shared via the system share sheet; files deleted at launch and after sharing. Verified on device through the sidecar.
- **Merchant names** (feature/settings/merchants): ported from the SERVER contract (rawPattern/cleanMerchantName/categoryId/isRegex; iOS' version is broken). List, add/edit sheet, delete, validation incl. regex sanity, online only. Verified on device through the sidecar.
- **Pro purchases** (data/purchases, feature/pro): RevenueCat/Play flow behind a `PurchasesService` port; Pro screen (plans, best value, restore, manage, Play disclosures), after a purchase it polls `premium.active` (10 x 2 s) before declaring success, "processing" message if the webhook lags; upsell buttons on the locked Reports and Statement import cards via `LocalOpenPro` (only when a key is configured). INERT without `REVENUECAT_API_KEY` ("Subscriptions aren't available in this build"). Not exercised against the real store. 10 tests.
- Also: CI workflow template (.github/workflows/android.yml), orb text ignores font scale, `docs/RELEASE.md` lists the owner inputs.
- 408 unit tests, lint clean, staging+prod R8 builds and a 9 MB unsigned AAB succeed.

## Wave J: iOS look and feel (DONE for all screens; see limits)
- Reference: the REAL iOS app was built and run on a simulator against the harness (`docs/ios-reference/*.png`; build with `xcodebuild ... -scheme "Spenditos Debug"`, launch with `--uitesting --uitesting-local-harness --uitesting-today 2026-10-01` and `SIMCTL_CHILD_E2E_API_BASE_URL/E2E_AUTH_TOKEN`).
- Design kit (`ui/theme/Theme.kt`, `ui/components/IosKit.kt`): iOS radii 10/16/24/32, iOS type scale (large title black 34, title2 22 bold, headline 17 semibold, body 17, callout 16), iOS grouped backgrounds, `GlassCard`, `GroupedCard`/`SectionHeader`/`IosRow`/`IconChip`, `LargeTitle`, `IosSegmented`, `PillButton`, the floating capsule `FloatingTabBar` (selected pill, brand tint), the 56 dp gradient `GradientFab` (hidden on Settings like iOS), app now starts on Home like iOS.
- Screens restyled to the SwiftUI layouts: Home (greeting, ambient weather wash, hero, River, 2x2 tiles, Recent activity), Transactions (+ add sheet, sync, receipt, capture), Plan (segmented, action card, rows, Month plan), Reports + Progress, Settings (profile card + inset-grouped sections) and its sub-screens (profile, categories, merchant names, export, Pro, statement import), sign-in/register/verify/forgot, onboarding, app lock. Codex wrote the screen restyles from the SwiftUI sources; I wrote the kit, Home and the shell and fixed their compile errors.
- Font: Inter (SIL OFL, `docs/licenses/Inter-OFL.txt`, one variable TTF in `res/font/inter.ttf`, 0.9 MB) is the app-wide font as the closest free match to SF Pro, which cannot be redistributed.
- Known remaining differences: SF Symbols vs Material icons (closest matches used), iOS 26 "liquid glass" blur (approximated with an opaque capsule), per-category icons (iOS maps the server icon name to an SF Symbol; Android shows a letter/generic glyph when the category has no icon), exact vertical rhythm on a few secondary screens (not pixel-compared).

## Wave K: exact iOS visual parity (2026-10-07, merged)
- Owner said the Wave J look was "far away from iOS". Every screen was re-ported from its SwiftUI source value by value (fonts, paddings, radii, colors, icons, copy). Rules for any future port: `docs/IOS-PORTING.md` (SwiftUI → Compose mapping table).
- Shared kit (mine): `IosText` (SwiftUI text styles; Inter scaled ×0.94 to SF Pro's apparent size), `IosColors` (iOS label colors), exact `glassCardModifier` (dropShadow r24 y4 6%), iOS `IconChip`, `CategorySymbolBadge`, `CategoryIcons.sfSymbolName` (port of IconMapper: categories show the monochrome symbol, not the emoji), `SfSymbols` (every SF Symbol the iOS app uses → closest Material icon), iOS settings rows, iOS 26 section headers, large titles under a 44dp nav strip with toolbar items in a glass capsule, `ErrorBanner`, tab bar at iOS 26 metrics, add button only on Home/Transactions (Plan has its own add menu, like iOS).
- Pipeline (owner-set): Codex + Grok wrote the screen ports (Codex hit its usage limit; Grok finished from Codex's checkpoints; Grok vs Codex head-to-head on Home and Transactions), GLM reviewed every port against the Swift, the lead curated findings, verified side by side on emulator vs the iOS simulator, merged; one fresh Opus agent reviewed the whole diff at the end.
- Bug found on the way: Reports takeaway was hard-coded "Recurring obligations are not mapped"; it now counts active non-legacy items like iOS (`activeRecurringCount`, unit-tested).
- Product gaps listed here were built in Wave L (below), except: Month plan "More tools" (plan checkup), and iOS hides Import/Export behind DEBUG while Android ships them.
- Icons: SF Symbols can't ship outside Apple platforms, so every iOS symbol name draws a Phosphor icon (MIT, `docs/licenses/Phosphor-MIT.txt`; generated paths in `ui/components/PhosphorPaths.kt` from `@phosphor-icons/core` 2.1.1). Default weight is Phosphor bold (matches SF at body sizes), `.fill` names draw the fill weight. No screen uses Material icons directly any more. The Your month chart draws iOS PinBadges.
- Final Opus review (fresh agent) found real regressions introduced by the ports; fixed: legacy estimates undeletable (rows now have a visible actions button), Month plan income lost Skip/Change/Log and hid draft changes, capture item Cancel saved edits, parsed currency shown as the saved one, new bills preselected a category, Reports could strand the user on an empty/failed month and never showed the empty-month card, closing search left a hidden filter, unreadable wheel pickers in dark mode, Reset dropped a "Log as paid", statement import lost its hint/result counts/preview category, theme-unsafe colors, unlabeled controls, swallowed streak-freeze error, Reports waited on the recurring refresh, past-month add opened later, receipt "Fill Manually" only went back. Not done (low): add-category from the add sheet when there are no categories (iOS opens an add-category sheet), some removed test tags, 24dp main-income star target.
- Emulator: run it with `-cores 4 -memory 4096` (`scripts/play.sh` does); the AVD's 1 core / 1.5 GB froze system_server.

## Wave L: product gaps closed (2026-10-07, merged)
- Add/edit form = iOS QuickAddView (one form for add and edit): hero amount, method bar Form/Voice/Sentence/Scan, Save and add another with 6 s Undo, last-used and merchant-rule category suggestions, Make it recurring (creates the bill from Next due), mood, bill duplicate questions (port of `BillMatching`, unit-tested), validation on Save, discard confirm, Add a category from the form. VM + data layer are mine (`PUT/DELETE /transactions/{id}`, pay a generated bill entry, bulk delete with 207 handling, device-only rows drop their queued create unless a send holds them). UI: Codex vs Grok head-to-head (Codex picked), GLM review, fixes applied.
- Transactions: tap edits, long press = Edit / Duplicate / Delete, iOS Edit mode (multi-select, Delete (n)), iOS filters (Grok: sheet, presets, pills, This/Last month/Custom); the Android-only month switcher is gone like iOS.
- Settings: Delete Account (two iOS confirmations, drops the unsynced queue, signs out) and Restore Purchases.
- Capture: Expense/Income picker in the item editor; iOS "Monthly Limit Reached" screen on a voice quota 403.
- Receipt: live CameraX viewfinder like iOS ScanReceiptView (Grok), permission screen, Retake / Use Photo.
- Month plan: iOS #336 Phase 7 copy (origin/develop): "After bills", no everyday wording. Note: local iOS `develop` is 2 commits behind origin (Phase 7) and 2 ahead (offline replay fix, unpushed).
- Brand: launcher icon, login mark and notification icon are the iOS AppIcon/BrandMark artwork.
- Widget data: iOS values (spent today, month income/expenses booked so far, 7-day series, top categories, streak cache). iOS bug noticed: its widget's "Spent Today" is fed the month's total expenses (`DashboardViewModel.updateWidgetData`); Android shows today's.

## Play with it
`scripts/play.sh` starts the backend harness (needs Docker), the sidecar, an emulator window, installs the `local` flavor and opens it; `scripts/play.sh stop` shuts them down. Accounts: `tools/e2e-sidecar/README.md`. Data resets when the harness/sidecar restart.

## Device e2e backend: DONE (tools/e2e-sidecar + `local` flavor)
- `local` flavor (debug only, `config/local.properties`, port 4311) = the REAL sign-in/refresh/Keystore path against `tools/e2e-sidecar`, a zero-dependency Node server in front of the backend harness (auth, S3 stand-in, statement import, capture/receipt stubs, Pro/non-Pro accounts: demo@spenditos.test / Passw0rd!demo, pro@spenditos.test / Passw0rd!pro1). Run: `pnpm --filter @spenditos/functions run serve:e2e` (backend repo), then `HARNESS_URL=http://127.0.0.1:<harness port> node tools/e2e-sidecar/server.mjs`, `adb reverse tcp:4311 tcp:4311`, `./gradlew :app:installLocalDebug`. `TOKEN_TTL_SECONDS=20` exercises refresh; `QUOTA_EXHAUSTED=1` the voice quota message. Self-test: `node --test tools/e2e-sidecar/test.mjs`.
- Verified ON DEVICE with it: register -> verify (123456) -> login -> onboarding -> empty Home; forgot-password (old password rejected, new accepted); sign out/in; token refresh at 20 s TTL (no spurious sign-out); Pro statement import (system file picker -> upload-url -> S3 multipart -> analyze -> map -> preview -> confirm -> import; transactions appear); typed capture (parse -> review -> bulk save; a failed save kept its keys and the retry saved once); receipt scan (photo picker -> upload -> analyze -> prefilled add sheet).
- Bugs found by it and fixed: (1) receipt scan failed for EVERY real image: `BitmapFactory.decodeStream(..., inJustDecodeBounds)` returns null, and the code treated that as "could not open" (Robolectric masked it); (2) statement preview showed raw amounts ("-64.2"), now signed currency. Sidecar fix: it strips the fake analysisId before forwarding a bulk save to the harness (the harness has no analysis log).
- Still not on device: real voice recording -> transcription (sidecar returns a canned transcript; the emulator mic crashed the windowed emulator once), real AI quality, S3 with a real presigned POST, XLSX import (sidecar rejects XLSX).

## Hardening: DONE
- R8: `assembleStagingRelease` + `assembleProdRelease` succeed (unsigned, ~4.5 MB). `-Pspenditos.minifyDebug` runs R8 on a debug-signed harness build; every tab, sub-screen, an add-transaction write (bulk) and a process kill/restore were exercised on the emulator without crashes (serializer rules hold; `StatementCellValueSerializer` made public to avoid a serializer-not-found warning).
- Dark mode checked on all five tabs. Font scale 1.6: money amounts no longer clipped (Reports review/Paycheck periods wrap with FlowRow; Home/Comparison unclipped), tab labels capped at 1.0 scale, Income & bills star moved into the action row ("Set as main"), bottom padding so the FAB never covers list tails.
- Accessibility: clickable rows have `Role.Button`; lint clean.
- Process death: Profile screen restored after `am kill`; statement import restore covered by unit tests.
- Not tested: sign-up/verify/forgot flows on device (emulator is signed in to the owner's real staging account), real S3 upload / Pro statement import, TalkBack by hand.

## Waiting on the owner
- Nothing blocking.

## Remaining plan
Wave C: Month plan draft, Home. Wave D: Statement import. Hardening: accessibility, dark mode, process-death tests, R8 release build, emulator smoke of every tab. Later (v1.x): voice/text capture, purchases, realtime, gamification, biometric lock, push, widgets.
Polish backlog: done (Income & bills, categories color dot + inline actions, comparison Net row, FAB insets). Only tab-label contrast not reviewed.

## Known harness limits
The backend local e2e harness does not serve `POST /categories` (404) or `PUT /user/settings` / country-detection for new users, so those writes are covered by MockWebServer tests, not on-device. A fresh user id (any `e2e-<uuid>` token) gets 404 on settings, which is how the onboarding wizard was exercised (temporary token edit, reverted).

## Repos, branches, worktrees, processes
- `spenditos-android/`: git repo, branch `main`, local commits only (no remote, never push). Head: see `git log`.
- `spenditos`: `develop` @ `384235b` (4 behind origin), working tree clean. Scratch detached worktree `$SCRATCHPAD/audit/spenditos` (origin/develop `4acac33`).
- `spenditos-ios`: untouched.
- Backend harness and emulator were STOPPED (2h background limit). Restart: harness `pnpm --filter @spenditos/functions run serve:e2e` (from ../spenditos, needs Docker), emulator `emulator -avd Pixel_3a_API_34_extension_level_7_arm64-v8a`, then `adb reverse tcp:4310 tcp:<harness port>`. Previously running: backend e2e harness (`serve:e2e`, port in its log; `adb reverse tcp:4310 → port`), emulator `Pixel_3a_API_34_extension_level_7_arm64-v8a`. The installed harness APK currently uses a FRESH-USER token (onboarding test); reinstall `:app:installHarnessDebug` from `main` to restore the seeded Home user.
- Pending side task (owner's choice): "Fix iOS offline replay duplicate transactions".
- delegate.sh: the codex sandbox cannot run Gradle (blocks sockets); workers write code, the orchestrator verifies; `DELEGATE_BASE=<branch>` starts a worktree from a worker branch for second passes.
