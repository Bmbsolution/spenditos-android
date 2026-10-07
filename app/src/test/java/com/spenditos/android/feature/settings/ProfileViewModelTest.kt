package com.spenditos.android.feature.settings

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.model.ChangePasswordRequest
import com.spenditos.android.data.model.UserSettings
import com.spenditos.android.data.model.UserSettingsUpdate
import com.spenditos.android.data.user.UserSettingsActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
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
import java.io.IOException

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
}
