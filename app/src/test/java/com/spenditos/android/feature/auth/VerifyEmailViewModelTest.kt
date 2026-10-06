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

class VerifyEmailViewModelTest {
    private lateinit var accounts: FakeAccountOperations
    private lateinit var viewModel: VerifyEmailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        accounts = FakeAccountOperations()
        viewModel = VerifyEmailViewModel(accounts)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `verification success marks confirmed`() = runTest {
        viewModel.onCodeChange("12x34567")
        assertEquals("123456", viewModel.state.value.code)
        viewModel.verify("person@example.test")
        advanceUntilIdle()
        assertEquals("123456", accounts.confirmedCode)
        assertTrue(viewModel.state.value.isVerified)
        assertEquals("Email confirmed, sign in", viewModel.state.value.notice)
    }

    @Test
    fun `verification client error is shown`() = runTest {
        accounts.failure = ApiError.Server(400, "Invalid confirmation code")
        fillCode()
        viewModel.verify("person@example.test")
        advanceUntilIdle()
        assertEquals("Invalid confirmation code", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isVerified)
    }

    @Test
    fun `verification network error shows offline copy`() = runTest {
        accounts.failure = ApiError.Network(IOException("offline"))
        fillCode()
        viewModel.verify("person@example.test")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.error!!.contains("connection"))
    }

    @Test
    fun `resend success starts cooldown`() = runTest {
        viewModel.resend("person@example.test")
        runCurrent()
        assertEquals("person@example.test", accounts.resentEmail)
        assertEquals(30, viewModel.state.value.resendSecondsRemaining)
        assertFalse(viewModel.state.value.canResend)
    }

    @Test
    fun `resend client error is shown`() = runTest {
        accounts.failure = ApiError.Server(429, "Too many requests")
        viewModel.resend("person@example.test")
        advanceUntilIdle()
        assertEquals("Too many requests", viewModel.state.value.error)
    }

    @Test
    fun `resend network error shows offline copy`() = runTest {
        accounts.failure = ApiError.Network(IOException("offline"))
        viewModel.resend("person@example.test")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.error!!.contains("connection"))
    }

    private fun fillCode() = viewModel.onCodeChange("123456")

    private class FakeAccountOperations : AccountOperations {
        var failure: Throwable? = null
        var confirmedCode: String? = null
        var resentEmail: String? = null
        override suspend fun register(email: String, password: String, timezone: String) = fail<RegisterAccountResponse>()
        override suspend fun confirmRegistration(email: String, code: String) {
            failure?.let { throw it }
            confirmedCode = code
        }
        override suspend fun resendCode(email: String) {
            failure?.let { throw it }
            resentEmail = email
        }
        override suspend fun forgotPassword(email: String) = failure?.let { throw it } ?: Unit
        override suspend fun confirmPassword(email: String, code: String, password: String) = failure?.let { throw it } ?: Unit
        private fun <T> fail(): T = throw UnsupportedOperationException()
    }
}
