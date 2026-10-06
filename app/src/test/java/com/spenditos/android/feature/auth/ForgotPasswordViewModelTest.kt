package com.spenditos.android.feature.auth

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.auth.AccountOperations
import com.spenditos.android.data.model.RegisterAccountResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class ForgotPasswordViewModelTest {
    private lateinit var accounts: FakeAccountOperations
    private lateinit var viewModel: ForgotPasswordViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        accounts = FakeAccountOperations()
        viewModel = ForgotPasswordViewModel(accounts)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `send code success advances to reset step`() = runTest {
        viewModel.onEmailChange("person@example.test")
        viewModel.sendCode()
        advanceUntilIdle()
        assertEquals("person@example.test", accounts.forgotEmail)
        assertEquals(ForgotPasswordStep.RESET, viewModel.state.value.step)
        assertTrue(viewModel.state.value.notice!!.contains("If an account exists"))
    }

    @Test
    fun `send code client error is shown`() = runTest {
        accounts.forgotFailure = ApiError.Server(400, "Invalid email")
        viewModel.onEmailChange("person@example.test")
        viewModel.sendCode()
        advanceUntilIdle()
        assertEquals("Invalid email", viewModel.state.value.error)
        assertEquals(ForgotPasswordStep.EMAIL, viewModel.state.value.step)
    }

    @Test
    fun `send code network error shows offline copy`() = runTest {
        accounts.forgotFailure = ApiError.Network(IOException("offline"))
        viewModel.onEmailChange("person@example.test")
        viewModel.sendCode()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.error!!.contains("connection"))
    }

    @Test
    fun `reset success clears password and confirms completion`() = runTest {
        fillResetForm()
        runCurrent()
        viewModel.resetPassword()
        advanceUntilIdle()
        assertEquals("123456", accounts.confirmedCode)
        assertTrue(accounts.receivedExpectedPassword)
        assertTrue(viewModel.state.value.isDone)
        assertEquals("", viewModel.state.value.newPassword)
        assertFalse(viewModel.state.value.canReset)
    }

    @Test
    fun `reset client error is shown`() = runTest {
        accounts.confirmFailure = ApiError.Server(400, "Invalid confirmation code")
        fillResetForm()
        runCurrent()
        viewModel.resetPassword()
        advanceUntilIdle()
        assertEquals("Invalid confirmation code", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isDone)
    }

    @Test
    fun `reset network error shows offline copy`() = runTest {
        accounts.confirmFailure = ApiError.Network(IOException("offline"))
        fillResetForm()
        runCurrent()
        viewModel.resetPassword()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.error!!.contains("connection"))
    }

    private fun fillResetForm() {
        viewModel.onEmailChange("person@example.test")
        viewModel.sendCode()
        viewModel.onCodeChange("123456")
        viewModel.onNewPasswordChange("Newpass1!")
    }

    private class FakeAccountOperations : AccountOperations {
        var forgotFailure: Throwable? = null
        var confirmFailure: Throwable? = null
        var forgotEmail: String? = null
        var confirmedCode: String? = null
        var receivedExpectedPassword = false
        override suspend fun register(email: String, password: String, timezone: String) = fail<RegisterAccountResponse>()
        override suspend fun confirmRegistration(email: String, code: String) = Unit
        override suspend fun resendCode(email: String) = Unit
        override suspend fun forgotPassword(email: String) {
            forgotFailure?.let { throw it }
            forgotEmail = email
        }
        override suspend fun confirmPassword(email: String, code: String, password: String) {
            confirmFailure?.let { throw it }
            confirmedCode = code
            receivedExpectedPassword = password == "Newpass1!"
        }
        private fun <T> fail(): T = throw UnsupportedOperationException()
    }
}
