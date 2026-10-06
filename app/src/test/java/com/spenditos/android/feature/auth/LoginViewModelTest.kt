package com.spenditos.android.feature.auth

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.auth.LoginOperations
import kotlinx.coroutines.Dispatchers
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

class LoginViewModelTest {
    private lateinit var login: FakeLoginOperations
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        login = FakeLoginOperations()
        viewModel = LoginViewModel(login)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `login success clears the in-memory password`() = runTest {
        fillForm()
        viewModel.signIn()
        advanceUntilIdle()
        assertEquals("person@example.test", login.email)
        assertTrue(login.receivedExpectedPassword)
        assertEquals("", viewModel.state.value.password)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `wrong credentials expose the server 401 as a sign in error`() = runTest {
        login.failure = ApiError.Server(401, "Unauthorized")
        fillForm()
        viewModel.signIn()
        advanceUntilIdle()
        assertEquals("Email or password is incorrect.", viewModel.state.value.error)
        assertFalse(viewModel.state.value.needsVerification)
    }

    @Test
    fun `unconfirmed account exposes verification email`() = runTest {
        login.failure = ApiError.Server(403, "UserNotConfirmed")
        fillForm()
        viewModel.signIn()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.needsVerification)
        assertEquals("person@example.test", viewModel.state.value.verificationEmail)
        assertTrue(viewModel.state.value.error!!.contains("Confirm your email"))
    }

    @Test
    fun `network failure shows offline sign in copy`() = runTest {
        login.failure = ApiError.Network(IOException("offline"))
        fillForm()
        viewModel.signIn()
        advanceUntilIdle()
        assertEquals("Can't reach Spenditos. Check your connection and try again.", viewModel.state.value.error)
    }

    private fun fillForm() {
        viewModel.onEmailChange("person@example.test")
        viewModel.onPasswordChange("Abcdef1!")
    }

    private class FakeLoginOperations : LoginOperations {
        var failure: Throwable? = null
        var email: String? = null
        var receivedExpectedPassword = false

        override suspend fun login(email: String, password: String) {
            failure?.let { throw it }
            this.email = email
            receivedExpectedPassword = password == "Abcdef1!"
        }
    }
}
