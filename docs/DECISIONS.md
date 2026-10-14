# Decisions

Format: ID · date · decision · why · who decided.

## Made (proposed defaults, owner may override at plan approval)

- D1 · 2026-10-06 · Kotlin + Compose + MVVM + Hilt + Room + Retrofit/OkHttp + kotlinx.serialization + WorkManager + Keystore-wrapped token store. · Owner's default stack. · owner
- D2 · 2026-10-06 · **Every** plain transaction create (online, queued, or replayed) goes through `POST /transactions/bulk` with one item, `source: "manual"`, and `idempotencyKeyId = clientMutationId` minted and persisted before the first attempt. · Deployed `POST /transactions` ignores idempotency keys, so a create that started there could not be safely replayed; bulk is idempotent today (no Pro gate, keeps `mood`/`templateId`); no server change. Trade-off: rows carry `source = manual`. If the server later honors `Idempotency-Key` on single create (PLAN §6.3), revisit. · Claude
- D3 · 2026-10-06 · Android caches transaction lists in Room (cache-first) although iOS is network-only for lists. · Cheap with Room, better offline UX, no server impact. · Claude (owner can veto)
- D4 · 2026-10-06 · Queue a create when offline at save time **or** when the send fails with a transport error (iOS queues only when offline at save time). Repeat / bill-link saves still require connectivity, like iOS. · Avoids losing a save on a flaky network; safe because replay is idempotent (D2). · Claude
- D5 · 2026-10-06 · No everyday-spending estimate in Month plan. · Removed server-side in #336 Phase 7. · owner (memory: planning-simplification-direction)
- D6 · 2026-10-06 · Don't port merchant mappings, DEBUG-only export/import, or dead iOS code. · Broken or not user-facing on iOS. · Claude
- D7 · 2026-10-06 · Flavors `staging` and `prod`; `prodDebug` removed; default run config `stagingDebug`; debug-only `harness` flavor for the local e2e harness. · "Never silently prod in debug." · owner rule
- D8 · 2026-10-06 · applicationId `com.asaklex.spenditos` (+ `.staging` suffix for staging). · Matches iOS bundle id `com.asaklex.Spenditos` owner prefix. · Claude
- D9 · 2026-10-06 · minSdk 28, compile/target 36, JDK 21 toolchain. · Keystore/biometric APIs, installed SDKs. · Claude
- D10 · 2026-10-06 · Contract fixtures are generated from the backend's built `packages/types` fixtures by a script in this repo; no backend change. · Keeps backend untouched. · Claude

## Owner answers (2026-10-06)

- D11 · Debug builds target **staging** (`stagingDebug` default); prod is release-only; personal stage only via uncommitted `config/dev.properties`. · owner
- D12 · **No purchases in v1.** Respect server gates (free receipt quota, paycheck-periods 403) with clear copy; RevenueCat + Play Billing later, after the additive `rc_android` webhook fix is deployed. · owner
- D13 · Voice/text AI capture is **later** (v1.x); v1 = manual entry + receipt scan. · owner
- D14 · Phase 1 verification: local e2e harness for data flows + the owner signs in to staging on the emulator once for the login path. · owner

- D15 · Import in v1 = statement import only (handles CSV + XLSX); the iOS `/import/csv` flow is skipped because it is broken against the current server. Pro check uses `userSettings.premium.active` since v1 has no purchases. · Claude (owner can veto)

## Phase 1 (2026-10-06)

- D16 · Toolchain: Gradle 9.8.0 (wrapper, checksum pinned), AGP 9.4.1 with built-in Kotlin, Kotlin 2.4.20, KSP 2.3.12, Compose BOM 2026.09.00, Hilt 2.60.1, Room 2.8.5, WorkManager 2.12.0, Retrofit 3.0.0, OkHttp 5.5.0, kotlinx.serialization 1.11.0. · Latest stable at build time. · Claude
- D17 · compileSdk 37 (required by current AndroidX/Compose/OkHttp AAR metadata), targetSdk 36, minSdk 28. Platform 37 installed via sdkmanager. · Claude
- D18 · Robolectric tests run at SDK 35 (`app/src/test/resources/robolectric.properties`): SDK 36 crashes in `ApplicationSharedMemory` under JDK 21. · Claude
- D19 · Sheets/dialogs outside the NavHost get their own ViewModelStore (`ui/components/ScopedViewModels`); the signed-in UI is keyed by userId. Found on the emulator: the add form reopened with stale input and was Activity-scoped across accounts. · Claude
- D20 · On a duplicate reply from `/transactions/bulk` (idempotent resend), the optimistic row is dropped and the next refresh brings the server row; the bulk response carries no id for duplicates and recomputing the server's deterministic id client-side is fragile (JS number formatting). · Claude
- D21 · `harnessDebug` flavor: base URL `http://127.0.0.1:4310` (via `adb reverse`), fixed synthetic token from the harness seed, cleartext allowed only for 127.0.0.1 in that flavor. · Claude

## Still open

- Approved 2026-10-06: plan, dependency downloads, local `git init`.
- Approved 2026-10-06: local commits (no remote, never push). Baseline `8d26aba`.
- Done 2026-10-06: owner signed in to staging on the emulator (D14).
- D15 superseded (iOS #259) · Statement import removed from Android; statements and CSVs come in through Scan. Settings has "Download my data" instead of Import/Export. · Claude (follows iOS)
