package com.spenditos.android.feature.auth

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.auth.AccountOperations
import com.spenditos.android.data.model.RegisterAccountResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class RegisterViewModelTest {
    private lateinit var accounts: FakeAccountOperations
    private lateinit var viewModel: RegisterViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        accounts = FakeAccountOperations()
        viewModel = RegisterViewModel(accounts)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `registration success clears password and emits the email`() = runTest {
        val event = async { viewModel.events.first() }
        viewModel.onEmailChange("person@example.test")
        viewModel.onPasswordChange("Abcdef1!")
        viewModel.onConfirmPasswordChange("Abcdef1!")
        assertTrue(viewModel.state.value.canRegister)

        viewModel.register()
        advanceUntilIdle()

        assertEquals("person@example.test", accounts.registeredEmail)
        assertTrue(accounts.receivedExpectedPassword)
        assertEquals(RegisterEvent.Registered("person@example.test"), event.await())
        assertTrue(accounts.registeredTimezone!!.isNotBlank())
        assertEquals("", viewModel.state.value.password)
        assertEquals("", viewModel.state.value.confirmPasswordText)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `registration trims the email like sign in`() = runTest {
        val event = async { viewModel.events.first() }
        viewModel.onEmailChange(" person@example.test ")
        viewModel.onPasswordChange("Abcdef1!")
        viewModel.onConfirmPasswordChange("Abcdef1!")
        viewModel.register()
        advanceUntilIdle()
        assertEquals("person@example.test", accounts.registeredEmail)
        assertEquals(RegisterEvent.Registered("person@example.test"), event.await())
    }

    @Test
    fun `registration client error is shown`() = runTest {
        accounts.failure = ApiError.Server(400, "Invalid registration payload")
        fillForm()
        viewModel.register()
        advanceUntilIdle()
        assertEquals("Invalid registration payload", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `registration network error shows offline copy`() = runTest {
        accounts.failure = ApiError.Network(IOException("offline"))
        fillForm()
        viewModel.register()
        advanceUntilIdle()
        assertEquals("Can't reach Spenditos. Check your connection and try again.", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    private fun fillForm() {
        viewModel.onEmailChange("person@example.test")
        viewModel.onPasswordChange("Abcdef1!")
        viewModel.onConfirmPasswordChange("Abcdef1!")
    }

    private class FakeAccountOperations : AccountOperations {
        var failure: Throwable? = null
        var registeredEmail: String? = null
        var receivedExpectedPassword = false
        var registeredTimezone: String? = null

        override suspend fun register(email: String, password: String, timezone: String): RegisterAccountResponse {
            failure?.let { throw it }
            registeredEmail = email
            receivedExpectedPassword = password == "Abcdef1!"
            registeredTimezone = timezone
            return RegisterAccountResponse("fake-user-001", true)
        }

        override suspend fun confirmRegistration(email: String, code: String) = failure?.let { throw it } ?: Unit
        override suspend fun resendCode(email: String) = failure?.let { throw it } ?: Unit
        override suspend fun forgotPassword(email: String) = failure?.let { throw it } ?: Unit
        override suspend fun confirmPassword(email: String, code: String, password: String) = failure?.let { throw it } ?: Unit
    }
}
