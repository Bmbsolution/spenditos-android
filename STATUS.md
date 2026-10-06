# STATUS

Last updated: 2026-10-06, end of Phase 1 (Opus). Next model: **Sonnet** for Phase 2 orchestration.

## Where to resume
Read in order: `/Users/asaklex/dev/Spenditos-Project/CLAUDE.md`, `AGENTS.md`, then `docs/PLAN.md`, `docs/ARCHITECTURE.md`, `docs/PARITY.md`, `docs/DECISIONS.md`, `ANDROID_STYLE.md`, `README.md`, this file.

## Done
- Phase 0: parity map, architecture, plan, decisions D1–D15 (owner approved).
- Phase 1 skeleton: Gradle wrapper 9.8.0, version catalog, flavors staging/prod/harness (no prodDebug, no harness release), build-time host validation, Hilt, theme, 5-tab shell + FAB.
- Phase 1 shared layers (Claude-owned, do not delegate changes): `core/network` (envelope, `apiCall`, AuthInterceptor refresh-once/retry-once, trace headers), `core/auth` (Keystore AES-GCM TokenStore, SessionManager single-flight refresh + generation guard, clear only on refresh 401), `core/db` (Room: transactions, categories, pending_mutations), `core/sync` (FailureClassifier = iOS table, SyncWorker, SyncScheduler, FinanceInvalidationBus), `app/SessionLifecycle`.
- Phase 1 slice: login, transactions month list (cache-first, pull to refresh), add sheet (online + offline queue), "Not synced" (retry/discard), settings subset (account, queue, environment, sign out with queue message).
- Contract fixtures: 78 exported from backend `4acac33` (`scripts/sync-contract-fixtures.sh`).
- Verified: 35/35 unit tests (contract 7, session 11, interceptor 4, classifier 3, repository 10); lint 0 errors (1 intentional targetSdk warning); `assembleStagingDebug`, `assembleHarnessDebug` OK.
- Emulator (Pixel 3a API 34) against the local e2e harness: list loads real handler data; online add → server row via `/transactions/bulk`; airplane-mode add → queued + banner + "Not synced"; reconnect → WorkManager replay in ~3 s, exactly one server row. Screenshots in `screenshots/`.
- `~/.claude/skills/delegate-workers/scripts/delegate.sh` extended: copies `local.properties` into Android worktrees, defaults JAVA_HOME, serializes GLM startups.

- Staging (2026-10-06): owner signed in on the emulator; `/user/settings`, `/categories`, `/transactions` all 200 through the auth interceptor; user currency applied; session survived a force-stop + relaunch (Keystore token store). Staging screenshots deliberately not kept (real account data).

## Waiting on the owner
- Permission to make local commits in `spenditos-android` (baseline commit is needed before Phase 2: worker worktrees branch from HEAD).
- Model switch to Sonnet for Phase 2.

## Next (Phase 2, Sonnet orchestrates, codex workers, one feature per worktree)
Wave A (parallel): Categories + Settings basics · Reports (4 screens) · Onboarding + register/verify/forgot screens (auth UI only; AuthRepository/SessionManager changes need Opus review).
Wave B: Income & bills · Receipt scan. Wave C: Month plan draft · Home. Wave D: Statement import.
Opus review batches: end of B (receipt confirm writes), C (plan apply), D (imports), and any `core/**` or `data/model/**` change.

Phase 2 TODOs carried from Phase 1:
- Onboarding gate in `AppRoot` (GET /user/settings `setupCompleted`).
- Transaction edit/delete (online only, like iOS) + filters/search (iOS FilterSheet).
- Transaction list: scroll to today / pagination beyond one month is out of scope until filters land.

## Repos, branches, worktrees, processes
- `spenditos-android/`: git repo, branch `main`, **no commits yet** (all files untracked). No remote.
- `spenditos`: `develop` @ `384235b` (4 behind origin), working tree clean; harness built gitignored output only. Scratch detached worktree at `$SCRATCHPAD/audit/spenditos` (origin/develop `4acac33`, types built, node_modules symlinked); remove with `git -C ../spenditos worktree remove --force <path>`.
- `spenditos-ios`: untouched.
- Running (started by Claude): backend e2e harness (`serve:e2e`, port printed in its log), Android emulator `Pixel_3a_API_34_extension_level_7_arm64-v8a`, `adb reverse tcp:4310 → harness port`.
- Pending side task (owner's choice): "Fix iOS offline replay duplicate transactions".
