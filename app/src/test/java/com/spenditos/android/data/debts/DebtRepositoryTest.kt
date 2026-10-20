package com.spenditos.android.data.debts

import com.spenditos.android.core.auth.AuthTokens
import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import com.spenditos.android.testing.OfflineInterceptor
import com.spenditos.android.testing.TestJwt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.LocalDate

/** iOS DebtServiceTests ported: asOf, Idempotency-Key, 207, PATCH nulls, online-only writes, invalidation. */
class DebtRepositoryTest {
    private val server = MockWebServer()
    private val offline = OfflineInterceptor()
    private val online = MutableStateFlow(true)
    private var beforeSend: (() -> Unit)? = null
    private val invalidations = FinanceInvalidationBus()
    private lateinit var session: SessionManager
    private lateinit var repository: DebtRepository

    private val today = LocalDate.of(2026, 10, 20)
    private fun fixture(name: String) =
        requireNotNull(javaClass.classLoader!!.getResource("contract/handwritten/debts/$name.json")).readText()

    @Before
    fun setUp() {
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(
                OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(offline)
                    .addInterceptor { chain -> beforeSend?.invoke(); chain.proceed(chain.request()) }
                    .build(),
            )
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build().create(DebtApi::class.java)
        session = SessionManager(FakeTokenStore.signedIn("user-fake-1"), { RefreshedTokens("access", "id") }, EpochClock { NOW }, AppEnvironment())
        val connectivity = object : ConnectivityStatus { override val isOnline = online }
        repository = DebtRepository(api, session, connectivity, invalidations)
    }

    @After
    fun tearDown() = server.close()

    private fun ok(name: String, code: Int = 200) = MockResponse(code = code, body = fixture(name))
    private suspend fun failure(block: suspend () -> Any?): Throwable =
        requireNotNull(runCatching { block() }.exceptionOrNull()) { "expected failure" }

    private val newDebt = CreateDebtRequest(
        name = "Car loan", startingBalance = 10000.0, startDate = "2026-10-20", apr = 7.5,
        payment = CreateDebtPayment(amount = 320.0, frequency = "monthly", firstPaymentDate = "2026-11-01"),
    )

    // region Reads

    @Test
    fun `list sends the local asOf and unwraps the envelope without announcing a change`() = runTest {
        server.enqueue(ok("list"))
        val event = async(start = CoroutineStart.UNDISPATCHED) { invalidations.events.first() }

        val debts = repository.list(today)

        assertEquals(3, debts.size)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/debts", request.url.encodedPath)
        assertEquals("2026-10-20", request.url.queryParameter("asOf"))
        assertNull(request.headers["Idempotency-Key"])
        assertTrue("Reads never announce a change", event.isActive)
        event.cancel()
    }

    @Test
    fun `detail sends asOf`() = runTest {
        server.enqueue(ok("detail"))
        val detail = repository.detail("debt-car", today)
        assertEquals("debt-car", detail.debt.id)
        assertEquals(3, detail.history.size)
        val request = server.takeRequest()
        assertEquals("/debts/debt-car", request.url.encodedPath)
        assertEquals("2026-10-20", request.url.queryParameter("asOf"))
    }

    @Test
    fun `preview posts the request and decodes the plan`() = runTest {
        server.enqueue(ok("preview"))
        val plan = repository.preview(
            DebtPreviewRequest(
                balance = 6240.0, apr = 7.5, frequency = "monthly", repeatInterval = 1,
                firstPaymentDate = "2026-11-01", payment = 370.0,
            ),
        )
        assertEquals(20, plan.paymentsCount)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/debts/preview", request.url.encodedPath)
        assertNull(request.headers["Idempotency-Key"])
        val body = SpenditosJson.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("370.0", (body["payment"] as JsonPrimitive).content)
        assertEquals("2026-11-01", (body["firstPaymentDate"] as JsonPrimitive).content)
    }

    @Test
    fun `preview sends apr null for an interest free debt and works offline-checked as a read`() = runTest {
        server.enqueue(ok("preview"))
        repository.preview(
            DebtPreviewRequest(balance = 1200.0, apr = null, frequency = "monthly", firstPaymentDate = "2026-10-05", targetMonth = "2027-06"),
        )
        val body = SpenditosJson.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals(JsonNull, body["apr"])
        assertFalse(body.containsKey("payment"))
    }

    // endregion

    // region Create

    @Test
    fun `create sends the Idempotency-Key header and announces transaction and recurring changes`() = runTest {
        server.enqueue(ok("detail", code = 201))
        val event = async(start = CoroutineStart.UNDISPATCHED) { invalidations.events.first() }

        val detail = repository.create(newDebt, "attempt-1")

        assertEquals("Car loan", detail.debt.name)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/debts", request.url.encodedPath)
        assertEquals("attempt-1", request.headers["Idempotency-Key"])
        assertEquals("Car loan", (SpenditosJson.parseToJsonElement(request.body!!.utf8()).jsonObject["name"] as JsonPrimitive).content)
        assertEquals(setOf(Aggregate.TRANSACTION, Aggregate.RECURRING), event.await())
    }

    @Test
    fun `a retry of the same attempt sends the same key and only the success announces a change`() = runTest {
        server.enqueue(MockResponse(code = 500, body = """{"message":"boom","error":"InternalError"}"""))
        server.enqueue(ok("detail", code = 201))
        val key = DebtIdempotencyKey.generate()

        assertTrue(failure { repository.create(newDebt, key) } is ApiError.Server)
        val event = async(start = CoroutineStart.UNDISPATCHED) { invalidations.events.first() }
        repository.create(newDebt, key)

        assertEquals(key, server.takeRequest().headers["Idempotency-Key"])
        assertEquals(key, server.takeRequest().headers["Idempotency-Key"])
        assertEquals(setOf(Aggregate.TRANSACTION, Aggregate.RECURRING), event.await())
    }

    @Test
    fun `generated keys are unique and within the server limit`() {
        val a = DebtIdempotencyKey.generate()
        assertTrue(a.length in 1..DebtIdempotencyKey.MAX_LENGTH)
        assertTrue(a != DebtIdempotencyKey.generate())
    }

    @Test
    fun `create rejects a blank or over long key before any request`() = runTest {
        assertTrue(failure { repository.create(newDebt, "") } is IllegalArgumentException)
        assertTrue(failure { repository.create(newDebt, "x".repeat(129)) } is IllegalArgumentException)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `create treats 207 EntriesNotSynced as success`() = runTest {
        val body = fixture("detail").replaceFirst(
            "\"data\":",
            "\"error\": \"EntriesNotSynced\", \"message\": \"The debt is saved, but its payments couldn't be updated.\", \"data\":",
        )
        server.enqueue(MockResponse(code = 207, body = body))
        val event = async(start = CoroutineStart.UNDISPATCHED) { invalidations.events.first() }

        val detail = repository.create(newDebt, "attempt-1")

        assertEquals("debt-car", detail.debt.id)
        assertEquals(setOf(Aggregate.TRANSACTION, Aggregate.RECURRING), event.await())
    }

    // endregion

    // region Update, delete, adjustment

    @Test
    fun `update treats 207 as success and sends the partial payment`() = runTest {
        server.enqueue(MockResponse(code = 207, body = fixture("detail")))
        val detail = repository.update("debt-car", UpdateDebtRequest(payment = DebtPatch.Value(UpdateDebtPayment(amount = 370.0))))
        assertEquals("debt-car", detail.debt.id)
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/debts/debt-car", request.url.encodedPath)
        assertNull(request.headers["Idempotency-Key"])
        val payment = SpenditosJson.parseToJsonElement(request.body!!.utf8()).jsonObject["payment"]!!.jsonObject
        assertEquals("370.0", (payment["amount"] as JsonPrimitive).content)
    }

    @Test
    fun `stopping payments sends an explicit null on the wire`() = runTest {
        server.enqueue(ok("detail"))
        repository.update("debt-car", UpdateDebtRequest(payment = DebtPatch.Null))
        assertEquals("""{"payment":null}""", server.takeRequest().body!!.utf8())
    }

    @Test
    fun `delete uses the DELETE route on a 204 and announces the change`() = runTest {
        server.enqueue(MockResponse(code = 204))
        val event = async(start = CoroutineStart.UNDISPATCHED) { invalidations.events.first() }

        repository.delete("debt-car")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/debts/debt-car", request.url.encodedPath)
        assertNull(request.headers["Idempotency-Key"])
        assertEquals(setOf(Aggregate.TRANSACTION, Aggregate.RECURRING), event.await())
    }

    @Test
    fun `adjustment posts date and balance`() = runTest {
        server.enqueue(ok("detail", code = 201))
        val event = async(start = CoroutineStart.UNDISPATCHED) { invalidations.events.first() }

        repository.addAdjustment("debt-car", today, 5900.0)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/debts/debt-car/adjustments", request.url.encodedPath)
        assertNull(request.headers["Idempotency-Key"])
        val body = SpenditosJson.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("2026-10-20", (body["date"] as JsonPrimitive).content)
        assertEquals("5900.0", (body["balance"] as JsonPrimitive).content)
        assertEquals(setOf(Aggregate.TRANSACTION, Aggregate.RECURRING), event.await())
    }

    // endregion

    // region Errors and offline

    @Test
    fun `server errors surface as ApiError Server with the code`() = runTest {
        server.enqueue(MockResponse(code = 404, body = fixture("error")))
        val e = failure { repository.detail("missing", today) } as ApiError.Server
        assertEquals(404, e.status)
        assertEquals("NOT_FOUND", e.serverMessage)

        server.enqueue(MockResponse(code = 400, body = """{"message":"Too soon","error":"TARGET_TOO_SOON"}"""))
        val p = failure {
            repository.preview(DebtPreviewRequest(100.0, null, "monthly", firstPaymentDate = "2026-10-30", targetMonth = "2026-10"))
        } as ApiError.Server
        assertEquals("TARGET_TOO_SOON", p.serverMessage)
    }

    @Test
    fun `a failed write does not announce a change`() = runTest {
        server.enqueue(MockResponse(code = 400, body = """{"message":"bad","error":"ValidationError"}"""))
        val event = async(start = CoroutineStart.UNDISPATCHED) { invalidations.events.first() }
        assertTrue(failure { repository.update("debt-car", UpdateDebtRequest(name = "x")) } is ApiError.Server)
        assertTrue(event.isActive)
        event.cancel()
    }

    @Test
    fun `transport failure maps to ApiError Network for reads and writes`() = runTest {
        offline.failNext = 1
        assertTrue(failure { repository.list(today) } is ApiError.Network)
        offline.failNext = 1
        assertTrue(failure { repository.create(newDebt, "k") } is ApiError.Network)
    }

    @Test
    fun `writes fail with Network before any request while offline and nothing is announced`() = runTest {
        online.value = false
        val event = async(start = CoroutineStart.UNDISPATCHED) { invalidations.events.first() }

        assertTrue(failure { repository.create(newDebt, "k") } is ApiError.Network)
        assertTrue(failure { repository.update("debt-car", UpdateDebtRequest(name = "x")) } is ApiError.Network)
        assertTrue(failure { repository.delete("debt-car") } is ApiError.Network)
        assertTrue(failure { repository.addAdjustment("debt-car", today, 1.0) } is ApiError.Network)

        assertEquals("Nothing is sent, and nothing is queued, while offline", 0, server.requestCount)
        assertTrue(event.isActive)
        event.cancel()
    }

    @Test
    fun `calls without a session fail as Unauthorized before any request`() = runTest {
        session.signOut()
        assertTrue(failure { repository.list(today) } === ApiError.Unauthorized)
        assertTrue(failure { repository.create(newDebt, "k") } === ApiError.Unauthorized)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a read that finishes after an account switch is discarded`() = runTest {
        server.enqueue(ok("list"))
        // Switch accounts while the (already enqueued) response is being read.
        beforeSend = {
            session.signIn(
                AuthTokens(
                    TestJwt.token("user-fake-2", NOW + 3600, nonce = "access-2"),
                    "refresh-user-fake-2",
                    TestJwt.token("user-fake-2", NOW + 3600, nonce = "id-2"),
                ),
            )
        }
        assertTrue(failure { repository.list(today) } === ApiError.Unauthorized)
    }

    // endregion
}
