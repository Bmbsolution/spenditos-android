package com.spenditos.android.feature.settings

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.auth.SessionState
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.db.MutationKind
import com.spenditos.android.core.db.MutationStatus
import com.spenditos.android.core.db.PendingMutationEntity
import com.spenditos.android.core.db.SpenditosDatabase
import com.spenditos.android.core.db.SyncStatus
import com.spenditos.android.core.db.TransactionEntity
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.core.sync.SyncRequester
import com.spenditos.android.data.auth.AccountDeletion
import com.spenditos.android.data.auth.AuthApi
import com.spenditos.android.data.auth.AuthRepository
import com.spenditos.android.data.model.ChangePasswordRequest
import com.spenditos.android.data.model.LoginRequest
import com.spenditos.android.data.model.LogoutRequest
import com.spenditos.android.data.model.RefreshRequest
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
import com.spenditos.android.data.transactions.TransactionRepository
import com.spenditos.android.data.transactions.TransactionsApi
import com.spenditos.android.data.user.UserApi
import com.spenditos.android.data.user.UserSettingsActions
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class ProfileViewModelTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    class MainDispatcherRule : TestWatcher() {
        val dispatcher: TestDispatcher = StandardTestDispatcher()
        override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
        override fun finished(description: Description) = Dispatchers.resetMain()
    }

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun failures() = listOf(
        ApiError.Server(400, "Validation failed"),
        ApiError.Network(IOException("offline")),
    )

    @Test
    fun `profile loads then saves currency and explicit display name clear`() = runTest {
        val actions = FakeUserSettingsActions()
        val vm = ProfileViewModel(actions, com.spenditos.android.data.auth.AccountDeletion { })
        advanceUntilIdle()
        assertEquals("Fake User", vm.state.value.displayName)
        assertEquals("USD", vm.state.value.currency)

        vm.onDisplayNameChange("  ")
        vm.onCurrencyChange("CAD")
        vm.save()
        advanceUntilIdle()
        assertEquals(JsonNull, actions.lastSettingsUpdate!!.displayName)
        assertEquals("CAD", actions.lastSettingsUpdate!!.currency)
        assertEquals("CAD", vm.state.value.currency)
        assertTrue(vm.state.value.success)
        assertFalse(vm.state.value.isSaving)
    }

    @Test
    fun `profile load reports server and offline failures`() = runTest {
        failures().forEach { failure ->
            val actions = FakeUserSettingsActions().apply { failureFor = "refresh"; this.failure = failure }
            val vm = ProfileViewModel(actions, com.spenditos.android.data.auth.AccountDeletion { })
            advanceUntilIdle()
            assertTrue(vm.state.value.error != null)
            assertEquals(failure is ApiError.Network, vm.state.value.isOffline)
        }
    }

    @Test
    fun `profile save reports server and offline failures`() = runTest {
        failures().forEach { failure ->
            val actions = FakeUserSettingsActions()
            val vm = ProfileViewModel(actions, com.spenditos.android.data.auth.AccountDeletion { })
            advanceUntilIdle()
            actions.failureFor = "updateSettings"
            actions.failure = failure
            vm.onCurrencyChange("CAD")
            vm.save()
            advanceUntilIdle()
            assertTrue(vm.state.value.error != null)
            assertEquals(failure is ApiError.Network, vm.state.value.isOffline)
        }
    }

    @Test
    fun `change password sends current and new passwords and clears them on success`() = runTest {
        val actions = FakeUserSettingsActions()
        val vm = ChangePasswordViewModel(actions)
        vm.onCurrentPasswordChange("Fake-Current-123!")
        vm.onNewPasswordChange("Abcdef1!")
        vm.onConfirmPasswordChange("Abcdef1!")
        assertTrue(vm.state.value.canSave)
        vm.changePassword()
        advanceUntilIdle()

        assertEquals("Fake-Current-123!", actions.lastPasswordRequest!!.currentPassword)
        assertEquals("Abcdef1!", actions.lastPasswordRequest!!.newPassword)
        assertTrue(vm.state.value.success)
        assertEquals("", vm.state.value.currentPassword)
        assertEquals("", vm.state.value.newPassword)
    }

    @Test
    fun `change password reports server and offline failures`() = runTest {
        failures().forEach { failure ->
            val actions = FakeUserSettingsActions().apply { failureFor = "changePassword"; this.failure = failure }
            val vm = ChangePasswordViewModel(actions)
            vm.onCurrentPasswordChange("Fake-Current-123!")
            vm.onNewPasswordChange("Abcdef1!")
            vm.onConfirmPasswordChange("Abcdef1!")
            vm.changePassword()
            advanceUntilIdle()
            assertTrue(vm.state.value.error != null)
            assertEquals(failure is ApiError.Network, vm.state.value.isOffline)
        }
    }

    @Test
    fun `change password explains a wrong current password instead of showing the code`() = runTest {
        val actions = FakeUserSettingsActions().apply {
            failureFor = "changePassword"
            failure = ApiError.Server(400, "InvalidPassword")
        }
        val vm = ChangePasswordViewModel(actions)
        vm.onCurrentPasswordChange("Fake-Current-123!")
        vm.onNewPasswordChange("Abcdef1!")
        vm.onConfirmPasswordChange("Abcdef1!")
        vm.changePassword()
        advanceUntilIdle()
        assertEquals("Your current password is incorrect.", vm.state.value.error)
    }

    private class FakeUserSettingsActions : UserSettingsActions {
        var failureFor: String? = null
        var failure: Throwable? = null
        var settings = UserSettings("user-1", displayName = "Fake User", currency = "USD", setupCompleted = true)
        var lastSettingsUpdate: UserSettingsUpdate? = null
        var lastPasswordRequest: ChangePasswordRequest? = null

        private fun fail(method: String) {
            if (failureFor == method) throw requireNotNull(failure)
        }

        override fun currency(): Flow<String> = flowOf(settings.currency)

        override suspend fun refresh(): UserSettings {
            fail("refresh")
            return settings
        }

        override suspend fun updateSettings(update: UserSettingsUpdate): UserSettings {
            fail("updateSettings")
            lastSettingsUpdate = update
            settings = settings.copy(
                currency = update.currency,
                displayName = update.displayName.takeUnless { it == JsonNull }?.let { (it as JsonPrimitive).content },
            )
            return settings
        }

        override suspend fun changePassword(currentPassword: String, newPassword: String) {
            fail("changePassword")
            lastPasswordRequest = ChangePasswordRequest(currentPassword, newPassword)
        }
    }

    @Test
    fun `a failed account deletion keeps the user here with a message, and a retry can succeed`() = runTest {
        var calls = 0
        val vm = ProfileViewModel(FakeUserSettingsActions(), com.spenditos.android.data.auth.AccountDeletion {
            calls++
            if (calls == 1) throw ApiError.Network(IOException("offline"))
        })
        vm.deleteAccount()
        advanceUntilIdle()
        assertEquals("You're offline. Check your connection and try again.", vm.state.value.deleteError)
        assertFalse(vm.state.value.isDeletingAccount)

        vm.dismissDeleteError()
        vm.deleteAccount()
        advanceUntilIdle()
        assertEquals(2, calls)
        assertEquals(null, vm.state.value.deleteError)
    }

    @Test
    fun `leaving the screen after the server deleted the account still signs out and drops the queue`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            SpenditosDatabase::class.java,
        ).allowMainThreadQueries().build()
        val session = SessionManager(
            FakeTokenStore.signedIn("user-1"),
            { RefreshedTokens("access", "id") },
            EpochClock { NOW },
            AppEnvironment(),
        )
        var accountDeletedOnServer = 0
        val userApi = object : UserApi {
            override suspend fun settings(): ApiEnvelope<UserSettings> = error("not used")
            override suspend fun updateSettings(body: UserSettingsUpdate): ApiEnvelope<UserSettings> = error("not used")
            override suspend fun changePassword(body: ChangePasswordRequest) = error("not used")
            override suspend fun deleteAccount() {
                accountDeletedOnServer++
            }
        }
        // The cleanup parks here (after the server's yes), so the test can cancel the scope first.
        val pastServerCall = CompletableDeferred<Unit>()
        val purchases = object : PurchasesService {
            override val isAvailable: Boolean = false
            override suspend fun logIn(userId: String) {}
            override suspend fun logOut() = pastServerCall.await()
            override suspend fun packages(): List<com.spenditos.android.data.purchases.ProPackage> = emptyList()
            override suspend fun purchase(activity: android.app.Activity, packageId: String) = PurchaseResult.Failed("not used")
            override suspend fun restore() = PurchaseResult.Failed("not used")
        }
        val unusedTransactionsApi = object : TransactionsApi {
            override suspend fun list(
                startDate: String,
                endDate: String,
                sortBy: String,
                sortOrder: String,
                limit: Int,
                cursor: String?,
            ): ApiEnvelope<com.spenditos.android.data.model.TransactionsPage> = error("not used")

            override suspend fun createBulk(
                body: com.spenditos.android.data.model.BulkCreateRequest,
                sessionBinding: com.spenditos.android.core.auth.SessionBinding?,
            ): ApiEnvelope<com.spenditos.android.data.model.BulkCreateResult> = error("not used")

            override suspend fun createSingle(
                body: com.spenditos.android.data.transactions.CreateTransactionRequest,
                sessionBinding: com.spenditos.android.core.auth.SessionBinding?,
            ): ApiEnvelope<com.spenditos.android.data.model.Transaction> = error("not used")

            override suspend fun update(
                id: String,
                body: com.spenditos.android.data.model.UpdateTransactionRequest,
            ): ApiEnvelope<com.spenditos.android.data.model.Transaction> = error("not used")

            override suspend fun delete(id: String) = error("not used")
            override suspend fun bulkDelete(body: com.spenditos.android.data.transactions.BulkDeleteRequest): retrofit2.Response<okhttp3.ResponseBody> =
                error("not used")
        }
        val repository = AuthRepository(
            api = object : AuthApi {
                override suspend fun login(body: LoginRequest): ApiEnvelope<com.spenditos.android.data.model.LoginResponse> = error("not used")
                override suspend fun refresh(body: RefreshRequest): ApiEnvelope<com.spenditos.android.data.model.RefreshResponse> = error("not used")
                override suspend fun logout(body: LogoutRequest) = error("not used")
            },
            session = session,
            push = PushRegistrar(
                object : PushApi {
                    override suspend fun register(body: DeviceTokenRequest) = error("not used")
                    override suspend fun unregister(body: UnregisterDeviceTokenRequest) = error("not used")
                },
                session,
                { null },
                object : PushSettings {
                    override val enabled: Flow<Boolean> = flowOf(false)
                    override suspend fun setEnabled(value: Boolean) {}
                    override suspend fun lastRegistered(): String? = null
                    override suspend fun setLastRegistered(value: String?) {}
                },
                AppVersion("com.spenditos.android", "0.1.0"),
            ),
            purchases = purchases,
            userApi = userApi,
            transactions = TransactionRepository(
                unusedTransactionsApi, db, session,
                object : ConnectivityStatus { override val isOnline = MutableStateFlow(true) },
                SyncRequester {},
                FinanceInvalidationBus(),
            ),
        )
        db.transactions().upsert(
            listOf(
                TransactionEntity(
                    "local-m-1", "user-1", 1.0, "cat-food", "Lunch", "2026-10-05",
                    null, null, null, null, null, SyncStatus.PENDING,
                ),
            ),
        )
        db.mutations().insert(
            PendingMutationEntity(
                id = "m-1", userId = "user-1", kind = MutationKind.CREATE_TRANSACTION,
                payloadJson = "{}", localTransactionId = "local-m-1", status = MutationStatus.PENDING, createdAt = 1,
            ),
        )

        val vm = ProfileViewModel(FakeUserSettingsActions(), AccountDeletion { repository.deleteAccount() })
        advanceUntilIdle()
        vm.deleteAccount()
        advanceUntilIdle() // the server deleted the account; the cleanup is parked in logOut
        assertEquals(1, accountDeletedOnServer)

        vm.viewModelScope.cancel() // the user navigated back before sign-out
        pastServerCall.complete(Unit)
        advanceUntilIdle()
        awaitTrue { session.state.value == SessionState.SignedOut }

        assertEquals(SessionState.SignedOut, session.state.value)
        assertTrue(db.mutations().observeAll("user-1").first().isEmpty())
        assertTrue(db.transactions().observeRange("user-1", "2026-10-01", "2026-10-31").first().isEmpty())
        db.close()
    }

    /** Keeps servicing the test scheduler while Room work on real threads completes. */
    private fun TestScope.awaitTrue(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            testScheduler.advanceUntilIdle()
            if (!condition()) Thread.sleep(20)
        }
        assertTrue(condition())
    }
}
