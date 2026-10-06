package com.spenditos.android.data.auth

import com.spenditos.android.core.network.apiCall
import com.spenditos.android.core.network.apiCallUnit
import com.spenditos.android.data.model.ConfirmPasswordRequest
import com.spenditos.android.data.model.ConfirmRegistrationRequest
import com.spenditos.android.data.model.ForgotPasswordRequest
import com.spenditos.android.data.model.RegisterAccountRequest
import com.spenditos.android.data.model.RegisterAccountResponse
import com.spenditos.android.data.model.ResendConfirmationRequest
import javax.inject.Inject
import javax.inject.Singleton

interface AccountOperations {
    suspend fun register(email: String, password: String, timezone: String): RegisterAccountResponse
    suspend fun confirmRegistration(email: String, code: String)
    suspend fun resendCode(email: String)
    suspend fun forgotPassword(email: String)
    suspend fun confirmPassword(email: String, code: String, password: String)
}

/** Existing sign-in action, exposed as an interface so its ViewModel can be tested with a fake. */
fun interface LoginOperations {
    suspend fun login(email: String, password: String)
}

/** Public register and recovery calls. This repository never reads or changes session tokens. */
@Singleton
class AccountRepository @Inject constructor(private val api: AccountApi) : AccountOperations {
    override suspend fun register(email: String, password: String, timezone: String): RegisterAccountResponse =
        apiCall { api.register(RegisterAccountRequest(email, password, timezone)) }

    override suspend fun confirmRegistration(email: String, code: String) {
        apiCallUnit { api.confirmRegistration(ConfirmRegistrationRequest(email, code)) }
    }

    override suspend fun resendCode(email: String) {
        apiCallUnit { api.resendCode(ResendConfirmationRequest(email)) }
    }

    override suspend fun forgotPassword(email: String) {
        apiCallUnit { api.forgotPassword(ForgotPasswordRequest(email)) }
    }

    override suspend fun confirmPassword(email: String, code: String, password: String) {
        apiCallUnit { api.confirmPassword(ConfirmPasswordRequest(email, code, password)) }
    }
}
