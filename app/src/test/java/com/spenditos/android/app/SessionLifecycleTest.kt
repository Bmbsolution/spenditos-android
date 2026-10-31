package com.spenditos.android.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spenditos.android.core.auth.AuthTokens
import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionBinding
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.auth.SessionState
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.db.SpenditosDatabase
import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.core.sync.SyncRequester
import com.spenditos.android.data.categories.CategoryRepository
import com.spenditos.android.data.categories.CategoriesApi
import com.spenditos.android.data.model.BulkCreateRequest
import com.spenditos.android.data.model.BulkCreateResult
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.CategoryCreateRequest
import com.spenditos.android.data.model.CategoryUpdateRequest
import com.spenditos.android.data.model.ChangePasswordRequest
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.data.model.TransactionsPage
import com.spenditos.android.data.model.UpdateTransactionRequest
import com.spenditos.android.data.model.UserSettings
import com.spenditos.android.data.model.UserSettingsUpdate
import com.spenditos.android.data.push.AppVersion
import com.spenditos.android.data.push.DeviceTokenRequest
import com.spenditos.android.data.push.PushApi
import com.spenditos.android.data.push.PushRegistrar
import com.spenditos.android.data.push.PushSettings
import com.spenditos.android.data.push.UnregisterDeviceTokenRequest
import com.spenditos.android.data.purchases.PurchaseResult
import com.spenditos.android.data.purchases.PurchasesService
import com.spenditos.android.data.transactions.BulkDeleteRequest
import com.spenditos.android.data.transactions.TransactionRepository
import com.spenditos.android.data.transactions.TransactionsApi
import com.spenditos.android.data.user.UserApi
import com.spenditos.android.data.user.UserSettingsRepository
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import com.spenditos.android.testing.TestJwt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionLifecycleTest {
    private lateinit var db: SpenditosDatabase
    private val logIns = AtomicInteger()
    private val settingsRefreshes = AtomicInteger()
    private val categoriesRefreshes = AtomicInteger()

    private val transactionsApi = object : TransactionsApi {
        override suspend fun list(
            startDate: String,
            endDate: String,
            sortBy: String,
            sortOrder: String,
            limit: Int,
            cursor: String?,
        ): ApiEnvelope<TransactionsPage> = error("not used")

        override suspend fun createBulk(body: BulkCreateRequest, sessionBinding: SessionBinding?): ApiEnvelope<BulkCreateResult> =
            error("not used")

        override suspend fun createSingle(
            body: com.spenditos.android.data.transactions.CreateTransactionRequest,
            sessionBinding: SessionBinding?, idempotencyKey: String?,
        ): ApiEnvelope<Transaction> = error("not used")

        override suspend fun update(id: String, body: UpdateTransactionRequest): ApiEnvelope<Transaction> = error("not used")
        override suspend fun delete(id: String) = error("not used")
        override suspend fun bulkDelete(body: BulkDeleteRequest): retrofit2.Response<okhttp3.ResponseBody> = error("not used")
    }

    private val categoriesApi = object : CategoriesApi {
        override suspend fun list(): ApiEnvelope<List<Category>> {
            categoriesRefreshes.incrementAndGet()
            return ApiEnvelope(data = emptyList())
        }

        override suspend fun create(body: CategoryCreateRequest): ApiEnvelope<Category> = error("not used")
        override suspend fun update(id: String, body: CategoryUpdateRequest): ApiEnvelope<Category> = error("not used")
        override suspend fun delete(id: String, reassignTo: String?) = error("not used")
    }

    private val userApi = object : UserApi {
        override suspend fun settings(): ApiEnvelope<UserSettings> {
            settingsRefreshes.incrementAndGet()
            return ApiEnvelope(data = UserSettings("user-1", currency = "USD"))
        }

        override suspend fun updateSettings(body: UserSettingsUpdate): ApiEnvelope<UserSettings> = error("not used")
        override suspend fun changePassword(body: ChangePasswordRequest) = error("not used")
        override suspend fun deleteAccount() = error("not used")
    }

    private val purchases = object : PurchasesService {
        override val isAvailable: Boolean = false
        override suspend fun logIn(userId: String) {
            logIns.incrementAndGet()
        }

        override suspend fun logOut() {}
        override suspend fun packages(): List<com.spenditos.android.data.purchases.ProPackage> = emptyList()
        override suspend fun purchase(activity: android.app.Activity, packageId: String) = PurchaseResult.Failed("not used")
        override suspend fun restore() = PurchaseResult.Failed("not used")
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            SpenditosDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun session(): SessionManager =
        SessionManager(FakeTokenStore(), { RefreshedTokens("access", "id") }, EpochClock { NOW }, AppEnvironment())

    private fun signInAs(manager: SessionManager, userId: String) {
        manager.signIn(
            AuthTokens(
                TestJwt.token(userId, NOW + 3600, nonce = "access-$userId"),
                "refresh-$userId",
                TestJwt.token(userId, NOW + 3600, nonce = "id-$userId"),
            ),
        )
    }

    private fun lifecycleFor(session: SessionManager): SessionLifecycle {
        val pushSettings = object : PushSettings {
            override val enabled: Flow<Boolean> = flowOf(false)
            override suspend fun setEnabled(value: Boolean) {}
            override suspend fun lastRegistered(): String? = null
            override suspend fun setLastRegistered(value: String?) {}
        }
        val push = PushRegistrar(
            object : PushApi {
                override suspend fun register(body: DeviceTokenRequest) = error("not used")
                override suspend fun unregister(body: UnregisterDeviceTokenRequest) = error("not used")
            },
            session,
            { null },
            pushSettings,
            AppVersion("com.spenditos.android", "0.1.0"),
        )
        val transactions = TransactionRepository(
            transactionsApi, db, session,
            object : ConnectivityStatus { override val isOnline = MutableStateFlow(true) },
            SyncRequester {},
            FinanceInvalidationBus(),
        )
        val categories = CategoryRepository(categoriesApi, db.categories(), session, FinanceInvalidationBus())
        val settings = UserSettingsRepository(ApplicationProvider.getApplicationContext<Context>(), userApi, session, com.spenditos.android.testing.FakeProAccess())
        return SessionLifecycle(session, transactions, categories, settings, SyncRequester {}, push, purchases, com.spenditos.android.data.user.LanguageMirror(settings, session))
    }

    /** Waits until [counter] settles on exactly [expected] (all earlier emissions processed). */
    private fun awaitExactly(expected: Int, counter: AtomicInteger) {
        val deadline = System.currentTimeMillis() + 10_000
        var last = counter.get()
        var lastChange = System.currentTimeMillis()
        while (System.currentTimeMillis() < deadline) {
            val value = counter.get()
            if (value != last) {
                last = value
                lastChange = System.currentTimeMillis()
            }
            if (value == expected && System.currentTimeMillis() - lastChange >= 250) return
            Thread.sleep(20)
        }
        assertEquals(expected, counter.get())
    }

    @Test
    fun `the first emission runs the session start work`() {
        assertTrue(shouldRunStartWork(null, SessionState.SignedIn("user-1", "test@example.test")))
    }

    @Test
    fun `a signed-in state repeated for the same user does not rerun the session start work`() {
        val state = SessionState.SignedIn("user-1", "test@example.test")
        assertFalse(shouldRunStartWork(state, state))
    }

    @Test
    fun `signing in again after a sign-out reruns the session start work`() {
        assertTrue(
            shouldRunStartWork(SessionState.SignedOut, SessionState.SignedIn("user-1", "test@example.test")),
        )
    }

    @Test
    fun `a different user signing in reruns the session start work`() {
        assertTrue(
            shouldRunStartWork(
                SessionState.SignedIn("user-1", "a@example.test"),
                SessionState.SignedIn("user-2", "b@example.test"),
            ),
        )
    }

    @Test
    fun `a signed-out state never runs the session start work`() {
        assertFalse(shouldRunStartWork(null, SessionState.SignedOut))
        assertFalse(shouldRunStartWork(SessionState.SignedIn("user-1", "test@example.test"), SessionState.SignedOut))
    }

    @Test
    fun `a single sign-in runs the session start work exactly once`() {
        val session = session()
        lifecycleFor(session).start()

        signInAs(session, "user-1")
        awaitExactly(1, logIns)

        assertEquals(SessionState.SignedIn("user-1", "test@example.test"), session.state.value)
        assertEquals(1, settingsRefreshes.get())
        assertEquals(1, categoriesRefreshes.get())
    }
}
