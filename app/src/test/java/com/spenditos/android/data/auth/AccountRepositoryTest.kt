package com.spenditos.android.data.auth

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.model.RegisterAccountResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class AccountRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repository: AccountRepository

    @Before
    fun setUp() {
        server.start()
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient())
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build().create(AccountApi::class.java)
        repository = AccountRepository(api)
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `register decodes response and sends optional timezone`() = runTest {
        server.enqueue(MockResponse(code = 201, body = fixture()))
        assertEquals(RegisterAccountResponse("fake-user-001", true), repository.register("person@example.test", "Abcdef1!", "America/Toronto"))
        val request = server.takeRequest()
        assertEquals("/auth/register", request.url.encodedPath)
        val body = SpenditosJson.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("America/Toronto", body["timezone"]!!.jsonPrimitive.content)
        assertEquals("person@example.test", body["email"]!!.jsonPrimitive.content)
    }

    @Test
    fun `all public unit endpoints accept successful responses`() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"data":{"message":"Registration confirmed successfully"}}"""))
        repository.confirmRegistration("person@example.test", "123456")
        assertEquals("/auth/confirm-registration", server.takeRequest().url.encodedPath)

        server.enqueue(MockResponse(code = 200, body = """{"message":"A new verification code has been sent"}"""))
        repository.resendCode("person@example.test")
        assertEquals("/auth/resend-code", server.takeRequest().url.encodedPath)

        server.enqueue(MockResponse(code = 200, body = """{"data":{"message":"Reset code sent if email exists"}}"""))
        repository.forgotPassword("person@example.test")
        assertEquals("/auth/forgot-password", server.takeRequest().url.encodedPath)

        server.enqueue(MockResponse(code = 200, body = """{"data":{"message":"Password has been reset successfully"}}"""))
        repository.confirmPassword("person@example.test", "123456", "Newpass1!")
        assertEquals("/auth/confirm-password", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `confirmation already complete accepts a 200 body without data`() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"message":"Account already confirmed"}"""))
        repository.confirmRegistration("person@example.test", "123456")
        assertEquals("/auth/confirm-registration", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `all endpoints surface client errors`() = runTest {
        val calls: List<suspend () -> Unit> = listOf(
            { repository.register("person@example.test", "Abcdef1!", "UTC"); Unit },
            { repository.confirmRegistration("person@example.test", "123456") },
            { repository.resendCode("person@example.test") },
            { repository.forgotPassword("person@example.test") },
            { repository.confirmPassword("person@example.test", "123456", "Newpass1!") },
        )
        calls.forEach { call ->
            server.enqueue(MockResponse(code = 400, body = """{"message":"Invalid payload","error":"ValidationError"}"""))
            try {
                call()
                throw AssertionError("Expected a client error")
            } catch (error: ApiError.Server) {
                assertEquals(400, error.status)
            }
        }
    }

    @Test
    fun `all endpoints surface network failures`() = runTest {
        val calls: List<suspend () -> Unit> = listOf(
            { repository.register("person@example.test", "Abcdef1!", "UTC"); Unit },
            { repository.confirmRegistration("person@example.test", "123456") },
            { repository.resendCode("person@example.test") },
            { repository.forgotPassword("person@example.test") },
            { repository.confirmPassword("person@example.test", "123456", "Newpass1!") },
        )
        calls.forEach { call ->
            server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
            try {
                call()
                throw AssertionError("Expected a network error")
            } catch (error: ApiError.Network) {
                assertTrue(error.cause != null)
            }
        }
    }

    @Test
    fun `handwritten registration fixture decodes`() {
        val json = requireNotNull(javaClass.classLoader!!.getResource("contract/handwritten/account_register_response.json")).readText()
        val decoded = SpenditosJson.decodeFromString(ApiEnvelope.serializer(RegisterAccountResponse.serializer()), json).data
        assertEquals("fake-user-001", decoded!!.userId)
        assertTrue(decoded.needsConfirmation)
    }

    private fun fixture(): String =
        requireNotNull(javaClass.classLoader!!.getResource("contract/handwritten/account_register_response.json")).readText()
}
