package com.spenditos.android.data.transactions

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spenditos.android.core.auth.AuthTokens
import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.auth.TokenKey
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.db.FailureCode
import com.spenditos.android.core.db.MutationKind
import com.spenditos.android.core.db.MutationStatus
import com.spenditos.android.core.db.PendingMutationEntity
import com.spenditos.android.core.db.SpenditosDatabase
import com.spenditos.android.core.db.SyncStatus
import com.spenditos.android.core.db.TransactionEntity
import com.spenditos.android.core.network.AuthInterceptor
import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.core.sync.SyncRequester
import com.spenditos.android.data.model.BulkTransactionItem
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import com.spenditos.android.testing.TestJwt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class TransactionRepositoryTest {
    private val server = MockWebServer()
    private lateinit var db: SpenditosDatabase
    private val online = MutableStateFlow(true)
    private var syncRequests = 0
    private lateinit var repo: TransactionRepository

    private val draft = NewTransaction(12.5, "cat-food", "Lunch", LocalDate.of(2026, 10, 5))

    @Before
    fun setUp() {
        server.start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SpenditosDatabase::class.java)
            .allowMainThreadQueries().build()
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient())
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build().create(TransactionsApi::class.java)
        val session = SessionManager(FakeTokenStore.signedIn("user-1"), { RefreshedTokens("a", "b") }, EpochClock { NOW }, AppEnvironment())
        repo = TransactionRepository(
            api, db, session,
            object : ConnectivityStatus { override val isOnline = online },
            SyncRequester { syncRequests++ },
            FinanceInvalidationBus(),
        )
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private fun created(id: String = "srv-1") = MockResponse(
        code = 201,
        body = """{"data":{"results":[{"transaction":{"id":"$id","userId":"user-1","categoryId":"cat-food","description":"Lunch","amount":12.5,"date":"2026-10-05","createdAt":"2026-10-05T12:00:00Z","updatedAt":"2026-10-05T12:00:00Z"}}]}}""",
    )

    private suspend fun rows() = db.transactions().observeRange("user-1", "2026-10-01", "2026-10-31").first()

    private suspend fun queue() = db.mutations().observeAll("user-1").first()

    private fun requestJson() = SpenditosJson.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject

    @Test
    fun `online create goes through bulk with an idempotency key and replaces the local row`() = runTest {
        server.enqueue(created())
        assertEquals(CreateResult.Saved("srv-1"), repo.create(draft))

        val request = server.takeRequest()
        assertEquals("/transactions/bulk", request.url.encodedPath)
        val body = SpenditosJson.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("manual", body["source"]!!.jsonPrimitive.content)
        val item = body["transactions"]!!.jsonArray.single().jsonObject
        assertTrue(item["idempotencyKeyId"]!!.jsonPrimitive.content.isNotBlank())
        assertEquals("2026-10-05", item["date"]!!.jsonPrimitive.content)

        val cached = rows().single()
        assertEquals("srv-1", cached.id)
        assertEquals(SyncStatus.SYNCED, cached.syncStatus)
        assertTrue(queue().isEmpty())
    }

    @Test
    fun `offline create is stored and queued without a request`() = runTest {
        online.value = false
        assertEquals(CreateResult.SavedOffline, repo.create(draft))
        assertEquals(0, server.requestCount)
        assertEquals(SyncStatus.PENDING, rows().single().syncStatus)
        assertEquals(MutationStatus.PENDING, queue().single().status)
        assertEquals(1, syncRequests)
    }

    @Test
    fun `lost connection keeps the create queued for the worker`() = runTest {
        server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
        assertEquals(CreateResult.SavedOffline, repo.create(draft))
        val queued = queue().single()
        assertEquals(MutationStatus.PENDING, queued.status)
        assertEquals(FailureCode.TRANSPORT, queued.failureCode)
        assertEquals(1, syncRequests)
    }

    @Test
    fun `server rejection of an online save keeps nothing`() = runTest {
        server.enqueue(MockResponse(code = 400, body = """{"error":"Validation failed"}"""))
        val result = repo.create(draft)
        assertEquals(CreateResult.Rejected(FailureCode.VALIDATION, "Validation failed"), result)
        assertTrue(rows().isEmpty())
        assertTrue(queue().isEmpty())
    }

    @Test
    fun `a resend uses the same idempotency key and a duplicate reply settles the row`() = runTest {
        server.enqueue(MockResponse(code = 503, body = """{"error":"unavailable"}"""))
        assertEquals(CreateResult.SavedOffline, repo.create(draft))
        val firstKey = requestJson()["transactions"]!!.jsonArray.single().jsonObject["idempotencyKeyId"]!!.jsonPrimitive.content

        // The first request actually committed; the retry is reported as a duplicate.
        server.enqueue(MockResponse(code = 201, body = """{"data":{"results":[],"duplicates":1}}"""))
        assertEquals(DrainResult.DONE, repo.drainQueue("user-1"))
        val secondKey = requestJson()["transactions"]!!.jsonArray.single().jsonObject["idempotencyKeyId"]!!.jsonPrimitive.content

        assertEquals(firstKey, secondKey)
        assertTrue(rows().isEmpty()) // the server row arrives with the next refresh
        assertTrue(queue().isEmpty())
    }

    @Test
    fun `drain stops on a retryable failure and leaves the item pending`() = runTest {
        online.value = false
        repo.create(draft)
        server.enqueue(MockResponse(code = 503, body = "{}"))
        assertEquals(DrainResult.RETRY_LATER, repo.drainQueue("user-1"))
        assertEquals(MutationStatus.PENDING, queue().single().status)
        assertEquals(FailureCode.SERVER, queue().single().failureCode)
    }

    @Test
    fun `category conflict fails the item and the drain moves on`() = runTest {
        online.value = false
        repo.create(draft)
        repo.create(draft.copy(description = "Dinner"))
        server.enqueue(MockResponse(code = 201, body = """{"data":{"results":[],"failed":[{"index":0,"reason":"category cat-food not found"}]}}"""))
        server.enqueue(created("srv-2"))
        assertEquals(DrainResult.DONE, repo.drainQueue("user-1"))

        val remaining = queue().single()
        assertEquals(MutationStatus.FAILED, remaining.status)
        assertEquals(FailureCode.CATEGORY_CONFLICT, remaining.failureCode)
        assertEquals(setOf(SyncStatus.FAILED, SyncStatus.SYNCED), rows().map { it.syncStatus }.toSet())
    }

    @Test
    fun `another user's queued work is never replayed`() = runTest {
        val item = BulkTransactionItem(5.0, "2026-10-02", "cat-x", "Theirs", idempotencyKeyId = "m-other")
        db.mutations().insert(
            PendingMutationEntity(
                id = "m-other", userId = "user-2", kind = MutationKind.CREATE_TRANSACTION,
                payloadJson = SpenditosJson.encodeToString(BulkTransactionItem.serializer(), item),
                localTransactionId = "local-m-other", status = MutationStatus.PENDING, createdAt = 1,
            ),
        )
        assertEquals(DrainResult.DONE, repo.drainQueue("user-1"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `refresh replaces synced rows but keeps queued ones`() = runTest {
        db.transactions().upsert(
            listOf(TransactionEntity("old", "user-1", 1.0, "cat", "Deleted on web", "2026-10-03", null, null, null, null, null, SyncStatus.SYNCED)),
        )
        online.value = false
        repo.create(draft)
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"data":{"transactions":[{"id":"srv-9","userId":"user-1","categoryId":"cat","description":"Rent","amount":900,"date":"2026-10-01","createdAt":"x","updatedAt":"x"}],"pagination":{"hasMore":false}}}""",
            ),
        )
        repo.refreshRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        val ids = rows().map { it.id }.toSet()
        assertTrue("srv-9" in ids)
        assertTrue("old" !in ids)
        assertTrue(ids.any { it.startsWith("local-") })
    }

    @Test
    fun `sign-in requeues auth failures only`() = runTest {
        online.value = false
        repo.create(draft)
        val id = queue().single().id
        db.mutations().release(id, MutationStatus.FAILED, FailureCode.AUTHENTICATION, "expired")
        repo.requeueAuthFailures("user-1")
        assertEquals(MutationStatus.PENDING, queue().single().status)
    }

    @Test
    fun `deleting a queued create drops it locally without a request, unless it is being sent`() = runTest {
        online.value = false
        repo.create(draft)
        repo.create(draft.copy(description = "Dinner"))
        val (first, second) = queue().sortedBy { it.createdAt }.let { it[0] to it[1] }

        repo.delete(first.localTransactionId)
        assertEquals(listOf(second.id), queue().map { it.id })
        assertEquals(listOf(second.localTransactionId), rows().map { it.id })

        db.mutations().claim(second.id, System.currentTimeMillis())
        val busy = runCatching { repo.delete(second.localTransactionId) }.exceptionOrNull()
        assertTrue(busy is TransactionBusyException)
        assertEquals(1, rows().size)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `deleting a synced row calls the server and a 404 still removes it here`() = runTest {
        server.enqueue(created())
        repo.create(draft)
        server.takeRequest()
        server.enqueue(MockResponse(code = 404, body = """{"error":"Transaction not found"}"""))
        repo.delete("srv-1")
        assertEquals("DELETE", server.takeRequest().method)
        assertTrue(rows().isEmpty())
    }

    @Test
    fun `account deletion drops every queued create and its row`() = runTest {
        online.value = false
        repo.create(draft)
        repo.create(draft.copy(description = "Dinner"))
        repo.dropQueue()
        assertTrue(queue().isEmpty())
        assertTrue(rows().isEmpty())
    }

    @Test
    fun `bulk delete removes what the server deleted and keeps what it reported as failed`() = runTest {
        server.enqueue(created("srv-1"))
        repo.create(draft)
        server.enqueue(created("srv-2"))
        repo.create(draft.copy(description = "Dinner"))
        server.takeRequest(); server.takeRequest()

        server.enqueue(
            MockResponse(
                code = 207,
                body = """{"message":"Some transactions could not be deleted","error":"PartialFailure","details":{"failedTransactionIds":["srv-2"],"deletedCount":1}}""",
            ),
        )
        val result = repo.bulkDelete(listOf("srv-1", "srv-2"))
        assertEquals(BulkDeleteResult(deleted = 1, failed = 1), result)
        val request = server.takeRequest()
        assertEquals("/transactions/bulk-delete", request.url.encodedPath)
        assertEquals(listOf("srv-2"), rows().map { it.id })
    }

    @Test
    fun `bulk delete drops queued creates locally and refuses one being sent`() = runTest {
        online.value = false
        repo.create(draft)
        repo.create(draft.copy(description = "Dinner"))
        val (first, second) = queue().sortedBy { it.createdAt }.let { it[0] to it[1] }
        db.mutations().claim(second.id, System.currentTimeMillis())

        val result = repo.bulkDelete(listOf(first.localTransactionId, second.localTransactionId))
        assertEquals(BulkDeleteResult(deleted = 1, failed = 1), result)
        assertEquals(listOf(second.localTransactionId), rows().map { it.id })
        assertEquals(0, server.requestCount)
    }

    private fun repoWithSession(
        store: FakeTokenStore,
        refresher: com.spenditos.android.core.auth.TokenRefresher,
    ): Pair<TransactionRepository, SessionManager> {
        val session = SessionManager(store, refresher, EpochClock { NOW }, AppEnvironment())
        val client = okhttp3.OkHttpClient.Builder().addInterceptor(AuthInterceptor(session)).build()
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(client)
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build().create(TransactionsApi::class.java)
        return TransactionRepository(
            api, db, session,
            object : ConnectivityStatus { override val isOnline = online },
            SyncRequester { syncRequests++ },
            FinanceInvalidationBus(),
        ) to session
    }

    private fun signInAs(session: SessionManager, store: FakeTokenStore, userId: String, nonce: String) {
        session.signOut()
        store.values[TokenKey.ACCESS] = TestJwt.token(userId, NOW + 3600, nonce = "$nonce-access")
        store.values[TokenKey.REFRESH] = "refresh-$userId"
        store.values[TokenKey.ID] = TestJwt.token(userId, NOW + 3600, nonce = "$nonce-id")
        session.signIn(
            AuthTokens(
                TestJwt.token(userId, NOW + 3600, nonce = "$nonce-access"),
                "refresh-$userId",
                TestJwt.token(userId, NOW + 3600, nonce = "$nonce-id"),
            ),
        )
    }

    @Test
    fun `a queued create of a switched account is never sent with the new account's token and stays recoverable`() = runTest {
        val store = FakeTokenStore.signedIn("user-1")
        val (repo, session) = repoWithSession(store) {
            RefreshedTokens(TestJwt.token("user-1", NOW + 7200, nonce = "new"), TestJwt.token("user-1", NOW + 7200, nonce = "newid"))
        }
        online.value = false
        repo.create(draft)
        val queued = queue().single()

        // The user signs out and another account signs in while the worker still holds A's id.
        signInAs(session, store, "user-2", "b")
        assertEquals(DrainResult.RETRY_LATER, repo.drainQueue("user-1"))
        assertEquals(0, server.requestCount)
        assertEquals(MutationStatus.PENDING, queue().single().status)

        // Back as the owning account: the entry replays with that account's token and key.
        signInAs(session, store, "user-1", "c")
        server.enqueue(created())
        assertEquals(DrainResult.DONE, repo.drainQueue("user-1"))
        val request = server.takeRequest()
        assertEquals("Bearer ${store.values[TokenKey.ACCESS]}", request.headers["Authorization"])
        val key = SpenditosJson.parseToJsonElement(request.body!!.utf8()).jsonObject["transactions"]!!
            .jsonArray.single().jsonObject["idempotencyKeyId"]!!.jsonPrimitive.content
        assertEquals(queued.id, key)
        assertTrue(queue().isEmpty())
    }

    @Test
    fun `draining with no session defers the queue instead of failing it`() = runTest {
        val (repo, session) = repoWithSession(FakeTokenStore.signedIn("user-1")) { RefreshedTokens("a", "b") }
        online.value = false
        repo.create(draft)
        session.signOut()
        assertEquals(DrainResult.RETRY_LATER, repo.drainQueue("user-1"))
        assertEquals(0, server.requestCount)
        val row = queue().single()
        assertEquals(MutationStatus.PENDING, row.status)
        assertNull(row.failureCode)
    }

    @Test
    fun `a session switch during the 401 refresh keeps the create queued and never retries with the new token`() = runTest {
        val store = FakeTokenStore.signedIn("user-1")
        val gate = CompletableDeferred<Unit>()
        val (repo, session) = repoWithSession(store) {
            gate.await()
            RefreshedTokens(TestJwt.token("user-1", NOW + 7200, nonce = "new"), TestJwt.token("user-1", NOW + 7200, nonce = "newid"))
        }
        server.enqueue(MockResponse(code = 401, body = """{"error":"expired"}"""))

        val saving = async { repo.create(draft) }
        testScheduler.advanceUntilIdle()
        // Read (and receive) the first attempt off the test thread: while this suspends,
        // the scheduler advances the save coroutine through Room and the network call.
        val firstAttempt = withContext(Dispatchers.IO) { server.takeRequest(10, TimeUnit.SECONDS) }
        assertNotNull(firstAttempt) // the first attempt went out with user-1's bearer token
        signInAs(session, store, "user-2", "b")
        gate.complete(Unit)

        assertEquals(CreateResult.SavedOffline, saving.await())
        assertEquals(1, server.requestCount) // no retry was sent at all
        val row = queue().single()
        assertEquals(MutationStatus.PENDING, row.status)
        assertNull(row.failureCode)
    }
}
