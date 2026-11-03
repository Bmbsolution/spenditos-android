package com.spenditos.android.data.purchases

import com.spenditos.android.core.auth.AuthTokens
import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.model.PremiumAccess
import com.spenditos.android.data.model.PremiumStatus
import com.spenditos.android.testing.FakeProAccess
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import com.spenditos.android.testing.TestJwt
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Instant

class PaymentsRepositoryTest {
    private class Api : PaymentsApi {
        var tokenCalls = 0
        var syncCalls = 0
        var failure: Exception? = null
        var expiresAt: String? = Instant.ofEpochSecond(NOW + 600).toString()
        var synced = PaymentsSyncResponse()
        var beforeTokenReturns: () -> Unit = {}
        override suspend fun token(): ApiEnvelope<PaymentsToken> {
            tokenCalls++
            failure?.let { throw it }
            beforeTokenReturns()
            return ApiEnvelope(data = PaymentsToken("token-$tokenCalls", expiresAt))
        }
        override suspend fun sync(): ApiEnvelope<PaymentsSyncResponse> {
            syncCalls++
            failure?.let { throw it }
            return ApiEnvelope(data = synced)
        }
    }

    private fun http(status: Int) = HttpException(
        Response.error<Any>(status, """{"message":"Payments are not available","error":"PaymentsUnavailable"}""".toResponseBody("application/json".toMediaType())),
    )

    private var now = NOW
    private val api = Api()
    private val access = FakeProAccess()
    private val session = SessionManager(FakeTokenStore.signedIn("user-1"), { RefreshedTokens("a", "b") }, EpochClock { now }, AppEnvironment())
    private val repository = PaymentsRepository(api, session, access, EpochClock { now })

    private fun signInAs(userId: String) {
        session.signOut()
        session.signIn(AuthTokens(TestJwt.token(userId, NOW + 3600, nonce = "a-$userId"), "refresh-$userId", TestJwt.token(userId, NOW + 3600, nonce = "i-$userId")))
    }

    @Test
    fun `the token is cached until a minute before it expires`() = runTest {
        assertEquals("token-1", repository.token())
        now = NOW + 500
        assertEquals("token-1", repository.token())
        assertEquals(1, api.tokenCalls)
        now = NOW + 541 // 10-minute token, under a minute left
        assertEquals("token-2", repository.token())
    }

    @Test
    fun `an unreadable expiry is simply not cached`() = runTest {
        api.expiresAt = "soon"
        repository.token()
        repository.token()
        assertEquals(2, api.tokenCalls)
    }

    @Test
    fun `a token never crosses an account switch or sign-out`() = runTest {
        assertEquals("token-1", repository.token())
        signInAs("user-2")
        assertEquals("token-2", repository.token())
        repository.clear()
        assertEquals("token-3", repository.token())
    }

    @Test
    fun `a token minted while the account switched is not kept`() = runTest {
        api.beforeTokenReturns = { signInAs("user-2"); api.beforeTokenReturns = {} }
        repository.token()
        assertEquals("token-2", repository.token())
    }

    @Test
    fun `token 503 means payments are off`() = runTest {
        api.failure = http(503)
        assertTrue(runCatching { repository.token() }.exceptionOrNull() is PaymentsUnavailableException)
        api.failure = http(500)
        assertTrue(runCatching { repository.token() }.exceptionOrNull() is ApiError.Server)
    }

    @Test
    fun `sync applies the server's premium, revoked included`() = runTest {
        access.setPro(true)
        api.synced = PaymentsSyncResponse(
            applied = true,
            premium = PremiumAccess(tier = "pro", active = false, expiresAt = "2099-01-01T00:00:00.000Z", version = 7),
            premiumStatus = PremiumStatus(false, null, "none"),
        )
        assertEquals(PaymentsSyncResult.SYNCED, repository.sync())
        assertFalse(access.effectiveIsPro)
        assertEquals(7L, access.current().premium?.version)
    }

    @Test
    fun `sync reports payments off and other failures without throwing`() = runTest {
        api.failure = http(503)
        assertEquals(PaymentsSyncResult.UNAVAILABLE, repository.sync())
        api.failure = http(500)
        assertEquals(PaymentsSyncResult.FAILED, repository.sync())
        api.failure = IOException("offline")
        assertEquals(PaymentsSyncResult.FAILED, repository.sync())
    }

    @Test
    fun `sync needs a session`() = runTest {
        session.signOut()
        assertEquals(PaymentsSyncResult.FAILED, repository.sync())
        assertEquals(0, api.syncCalls)
    }

    @Test
    fun `syncIfBehind skips a version the server already reported`() = runTest {
        api.synced = PaymentsSyncResponse(premium = PremiumAccess(active = true, version = 5))
        repository.syncIfBehind(1)
        assertEquals("nothing held yet", 1, api.syncCalls)
        repository.syncIfBehind(5)
        assertEquals(1, api.syncCalls)
        repository.syncIfBehind(6)
        assertEquals(2, api.syncCalls)
    }

    @Test
    fun `sync and token responses decode from the backend's shape`() {
        val body = """{"data":{"applied":true,"premium":{"tier":"plus_family","active":true,"version":3,"future":1},"premiumStatus":{"isPremium":true,"expiresAt":null,"reason":"store"}}}"""
        val synced = SpenditosJson.decodeFromString(ApiEnvelope.serializer(PaymentsSyncResponse.serializer()), body).data!!
        assertTrue(synced.applied)
        assertEquals("plus_family", synced.premium?.tier)
        assertEquals("store", synced.premiumStatus?.reason)
        val empty = SpenditosJson.decodeFromString(ApiEnvelope.serializer(PaymentsSyncResponse.serializer()), """{"data":{"applied":false,"premium":null,"premiumStatus":{"isPremium":false,"expiresAt":null,"reason":"none"}}}""").data!!
        assertNull(empty.premium)
        val token = SpenditosJson.decodeFromString(ApiEnvelope.serializer(PaymentsToken.serializer()), """{"data":{"token":"t","expiresAt":"2026-11-03T10:10:00.000Z"}}""").data!!
        assertEquals("t", token.token)
    }
}
