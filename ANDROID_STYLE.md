# ANDROID_STYLE.md: how to build a feature in spenditos-android

For workers and reviewers. Read `docs/ARCHITECTURE.md` once; this file is the checklist. The **reference slice** is Transactions (list + add + offline queue): copy its shape, not its contents.

## Hard rules (a diff that breaks one is rejected)

1. **Only touch the files your spec lists.** Never edit `core/**`, `data/model/**` shared models, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `AndroidManifest.xml`, or another feature's package unless the spec says so. Need a new dependency or a core change? Stop and say so in your summary.
2. **No business rules invented on the client.** Mirror the iOS file named in the spec and the server contract. If iOS and the server disagree, follow the server and say so.
3. **Money:** wire amounts are positive `Double`; income vs expense comes from the category's `isIncome`. Sum with `BigDecimal`. Format with `Money.format/signed`. Dates on the wire are `YYYY-MM-DD` strings → `LocalDate`; never go through UTC/`Instant` except at the DatePicker boundary (see `AddTransactionSheet`).
4. **Financial writes** (anything that creates/changes/deletes money data) go through a repository function and are reviewed by the owner's Opus pass. Plain transaction creates use `TransactionRepository.create` (bulk + idempotency key, DECISIONS D2), never a new `POST /transactions` call.
5. **User scoping:** every Room query takes `userId`; get it from `SessionManager.currentUserId` inside the repository, never from UI.
6. **No secrets, tokens, emails, amounts, descriptions, or URLs in logs.** No `Log.d` with model objects. No `println`.
7. **Gamification/telemetry are best-effort:** wrap in `runCatching`, never fail a financial operation because of them.
8. **Never prod in debug.** Don't add flavors or touch `config/*.properties`.

## Layers and where code goes

```
data/model/<Area>.kt            @Serializable wire models, KDoc citing packages/types/src/...:line or the handler
data/<feature>/<Feature>Api.kt  Retrofit interface; suspend funs returning ApiEnvelope<T> (or Unit for empty bodies)
data/<feature>/<Feature>Repository.kt   @Singleton; apiCall { } / apiCallUnit { }; Room for cache; exposes Flow + suspend funs
feature/<feature>/<Screen>ViewModel.kt  @HiltViewModel; StateFlow<UiState>; one-shot events via Channel
feature/<feature>/<Screen>Screen.kt     stateless-ish Composable taking the ViewModel via hiltViewModel()
app/MainScaffold.kt             nav wiring (only when the spec says so)
```

Register a new Retrofit API in `core/network/NetworkModule.kt` only when the spec allows it (it's a one-line `@Provides`); otherwise the reviewer adds it.

## Wire models

- `@Serializable data class`, property names exactly as the JSON (camelCase). Optional fields get defaults (`= null`, `= emptyList()`, `= false`).
- Enums: `@SerialName("server_value")` on each entry, and declare the property as `val x: MyEnum? = null` so an unknown server value decodes to `null` (Json uses `coerceInputValues`).
- Don't decode fields the screen doesn't need; `ignoreUnknownKeys` is on.
- A new model needs a decode test against its canonical fixture in `app/src/test/resources/contract/` (see `ContractFixtureTest`). If no fixture exists (handler-only shape), add a hand fixture under `contract/handwritten/` with obviously fake values.

## Repository pattern

- Reads: cache-first. Expose `Flow` from Room (or a `MutableStateFlow` for screens without a table yet) and a `suspend fun refresh(...)` that fetches and writes the cache.
- Errors: let `ApiError` propagate; the ViewModel turns it into copy. `ApiError.Network` → offline copy, never a scary error.
- After a write, call `FinanceInvalidationBus.invalidate(Aggregate.X)` so other screens refresh.

## ViewModel pattern (see `TransactionListViewModel`, `AddTransactionViewModel`)

- `data class XUiState(...)` with derived `val`s for things like `canSave`.
- `val state: StateFlow<XUiState> = combine(...).stateIn(viewModelScope, WhileSubscribed(5_000), XUiState())`.
- Inputs are plain functions (`onAmountChange`, `save`). Validation lives in the state class or a pure function you can unit-test.
- One-shot results (close sheet, snackbar text) go through `Channel<Event>` + `receiveAsFlow()`.
- Subscribe to `FinanceInvalidationBus.events` for the aggregates the screen shows, `debounce(250)`.

## Compose UI

- Material 3 with `SpenditosTheme`; finance colors from `LocalFinanceColors` (income/expense/warning). Brand terracotta = `colorScheme.primary`, for actions only, never to mean money in/out.
- Spacing from `Spacing` (`screen` = 20 dp side padding, cards 16 dp radius).
- Sheets and dialogs shown outside the NavHost **must** wrap content in `ScopedViewModels { ... }` (see `MainScaffold`), or their ViewModel leaks across openings and accounts.
- The FAB is the only global "add" entry point. Don't add another add button on Home.
- Every interactive element and every list/row gets `Modifier.testTag("<feature>.<thing>")`.
- Copy: plain, short, finance-first. Use "Income" (not paycheck/money in) and "bills"; never "rule", "template", "budget", or "variable" in UI text (owner decision, #336).
- Empty, loading, offline, and error states are required for every screen.

## Tests (required in the same diff)

- ViewModel: unit test with fakes or a repository on MockWebServer + in-memory Room (`TransactionRepositoryTest` shows the setup; Robolectric runs at SDK 35 via `app/src/test/resources/robolectric.properties`).
- Repository: MockWebServer for every endpoint you call: success, 4xx, 5xx, network failure.
- Pure rules (calculations, validation, occurrence math): table-driven tests with the iOS test cases ported as-is.
- Contract: decode test for every new wire model.

## Test patterns learned in Wave A (use these; they avoid flaky tests)

- **Network failure in tests:** use `testing/OfflineInterceptor` (`offline.failNext = 1`) on the test OkHttp client, built with `.retryOnConnectionFailure(false)`. Do NOT use MockWebServer `SocketEffect.CloseSocket`: on a reused connection OkHttp silently retries and the call "succeeds" with an empty body.
- **One request, one `takeRequest()`.** A refresh after a write is a second request; take each exactly once and assert on it.
- **State fed by Room is asynchronous** (Room emits on its own executor). Assert with `vm.state.first { predicate }`, never by reading `state.value` right after an event.
- Robolectric runs at SDK 35 (`robolectric.properties`); build Room in-memory with `allowMainThreadQueries()`.
- When you add a constructor parameter to an existing repository, grep the tests (`grep -rn "XRepository(" app/src/test`) and fix every call site.
- **Virtual time + real I/O don't mix.** Under `runTest`, MockWebServer calls take real time but the scheduler skips virtual time whenever the test is suspended, so `withTimeout`/`delay`-based deadlines fire instantly. Bound polling/retry loops by COUNT (testable) and add a `TimeSource.Monotonic` deadline only as a production backstop.
- **Inject dispatchers** (`@XyzIoDispatcher CoroutineDispatcher`) instead of calling `Dispatchers.IO` inside a ViewModel; tests pass `Dispatchers.Main` (the test scheduler) so work can be awaited.
- Screens drawn without a `Scaffold` need `Modifier.safeDrawingPadding()` or they sit under the status bar.
- Workers cannot run Gradle (sandbox blocks sockets); the orchestrator compiles and tests, and sends errors back. Write conservatively: copy imports and signatures from existing files.

## Verification (run it yourself; paste the summary lines)

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew :app:testStagingDebugUnitTest :app:lintStagingDebug :app:assembleStagingDebug
```

All must pass. No new lint errors. Don't use `--offline`, don't skip tests, don't weaken or delete existing assertions.

## Spec template (what the orchestrator gives a worker)

```
Feature: <name>   Worktree branch: delegate/<run>
Mirror iOS: <paths in spenditos-ios/...>
Server contract: <METHOD /path> → <TS type at packages/types/src/...:line or handler file:line>
Create: <exact file paths>   Modify: <exact file paths or "none">   Do not touch anything else.
Behavior: <bullets incl. empty/offline/error states and copy>
Tests: <cases>
Verify: the command above.
```
