package com.spenditos.android.data.transactions

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spenditos.android.core.auth.AuthTokens
import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionBinding
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
import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.AuthInterceptor
import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.core.sync.SyncRequester
import com.spenditos.android.data.model.BulkCreateRequest
import com.spenditos.android.data.model.BulkCreateResult
import com.spenditos.android.data.model.BulkTransactionItem
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.data.model.TransactionsPage
import com.spenditos.android.data.model.TransactionsPagination
import com.spenditos.android.data.model.UpdateTransactionRequest
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import com.spenditos.android.testing.TestJwt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.LocalDate

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
    fun `matched debt payment preserves both links in the queued bulk request`() = runTest {
        online.value = false
        repo.create(draft.copy(templateId = "bill-debt", debtId = "debt-1"))
        val item = SpenditosJson.decodeFromString(BulkTransactionItem.serializer(), queue().single().payloadJson)
        assertEquals("bill-debt", item.templateId)
        assertEquals("debt-1", item.debtId)
        assertEquals("debt-1", rows().single().debtId)
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

    private fun transactionPage(ids: List<String>, nextCursor: String? = null) = MockResponse(
        code = 200,
        body = SpenditosJson.encodeToString(
            ApiEnvelope.serializer(TransactionsPage.serializer()),
            ApiEnvelope(
                data = TransactionsPage(
                    transactions = ids.map { id ->
                        Transaction(id, "user-1", "cat-food", amount = 12.5, date = "2026-10-05")
                    },
                    pagination = TransactionsPagination(nextCursor, nextCursor != null),
                ),
            ),
        ),
    )

    @Test
    fun `repeated refreshes include every page and keep queued rows`() = runTest {
        online.value = false
        repo.create(draft)
        val firstPage = (1..200).map { "server-$it" }
        repeat(2) {
            server.enqueue(transactionPage(firstPage, "page-2"))
            server.enqueue(transactionPage(listOf("nearer-entry")))

            repo.refreshRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))

            val ids = rows().map { it.id }.toSet()
            assertEquals(202, ids.size)
            assertTrue("nearer-entry" in ids)
            assertTrue(ids.any { id -> id.startsWith("local-") })
            assertEquals("200", server.takeRequest().url.queryParameter("limit"))
            assertEquals("page-2", server.takeRequest().url.queryParameter("cursor"))
        }
    }

    @Test
    fun `a failed later page keeps the entire previously cached range`() = runTest {
        server.enqueue(transactionPage(listOf("existing")))
        repo.refreshRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))

        server.enqueue(transactionPage(listOf("partial"), "page-2"))
        server.enqueue(MockResponse(code = 503, body = "{}"))
        val failure = runCatching {
            repo.refreshRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        }.exceptionOrNull()

        assertTrue(failure != null)
        assertEquals(listOf("existing"), rows().map { it.id })
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

    @Test
    fun `discard refuses a create being sent and keeps it until the send settles`() = runTest {
        online.value = false
        repo.create(draft)
        val row = queue().single()
        db.mutations().claim(row.id, System.currentTimeMillis())

        assertEquals(false, repo.discard(row.id))
        assertEquals(MutationStatus.IN_FLIGHT, queue().single().status)
        assertEquals(1, rows().size)

        // The send settles as a failure: an ordinary discard now works.
        db.mutations().release(row.id, MutationStatus.FAILED, FailureCode.VALIDATION, "rejected")
        assertEquals(true, repo.discard(row.id))
        assertTrue(queue().isEmpty())
        assertTrue(rows().isEmpty())
    }

    @Test
    fun `pending and failed entries discard normally`() = runTest {
        online.value = false
        repo.create(draft)
        repo.create(draft.copy(description = "Dinner"))
        val (first, second) = queue().sortedBy { it.createdAt }.let { it[0] to it[1] }

        assertEquals(true, repo.discard(first.id))
        db.mutations().release(second.id, MutationStatus.FAILED, FailureCode.VALIDATION, "rejected")
        assertEquals(true, repo.discard(second.id))
        assertTrue(queue().isEmpty())
        assertTrue(rows().isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a recent claim from a dead process schedules recovery and replays once stale`() = runTest {
        online.value = false
        repo.create(draft)
        val row = queue().single()

        // The process died mid-send: the claim is younger than the stale threshold, so the
        // queue is not empty — another worker pass must be arranged.
        db.mutations().claim(row.id, System.currentTimeMillis())
        assertEquals(DrainResult.RETRY_LATER, repo.drainQueue("user-1"))
        assertEquals(0, server.requestCount)
        assertEquals(MutationStatus.IN_FLIGHT, queue().single().status)

        // Past the stale threshold the claim is released and the create replays under its key.
        server.enqueue(created())
        db.mutations().release(row.id, MutationStatus.PENDING, null, null)
        db.mutations().claim(row.id, System.currentTimeMillis() - 3 * 60 * 1000)
        assertEquals(DrainResult.DONE, repo.drainQueue("user-1"))
        val key = requestJson()["transactions"]!!.jsonArray.single().jsonObject["idempotencyKeyId"]!!.jsonPrimitive.content
        assertEquals(row.id, key)
        assertTrue(queue().isEmpty())
    }

    @Test
    fun `an empty queue with no claims is done`() = runTest {
        assertEquals(DrainResult.DONE, repo.drainQueue("user-1"))
        assertEquals(0, server.requestCount)
    }

    /** Holds the bulk send in flight forever, so the test can cancel the caller mid-request. */
    private class SuspendingCreateApi : TransactionsApi {
        val entered = CompletableDeferred<Unit>()

        override suspend fun createBulk(
            body: BulkCreateRequest,
            sessionBinding: SessionBinding?,
        ): ApiEnvelope<BulkCreateResult> {
            entered.complete(Unit)
            CompletableDeferred<Unit>().await() // in flight until the test ends
            throw IllegalStateException("unreachable")
        }

        override suspend fun list(
            startDate: String,
            endDate: String,
            sortBy: String,
            sortOrder: String,
            limit: Int,
            cursor: String?,
        ): ApiEnvelope<TransactionsPage> = error("not used")

        override suspend fun createSingle(body: CreateTransactionRequest, sessionBinding: SessionBinding?, idempotencyKey: String?): ApiEnvelope<Transaction> =
            error("not used")

        override suspend fun update(id: String, body: UpdateTransactionRequest): ApiEnvelope<Transaction> = error("not used")
        override suspend fun delete(id: String) = error("not used")
        override suspend fun bulkDelete(body: BulkDeleteRequest): retrofit2.Response<okhttp3.ResponseBody> = error("not used")
    }

    @Test
    fun `cancelling the caller mid-send releases the claim as pending and requests a retry`() = runTest {
        val api = SuspendingCreateApi()
        val session = SessionManager(FakeTokenStore.signedIn("user-1"), { RefreshedTokens("a", "b") }, EpochClock { NOW }, AppEnvironment())
        val cancellingRepo = TransactionRepository(
            api, db, session,
            object : ConnectivityStatus { override val isOnline = online },
            SyncRequester { syncRequests++ },
            FinanceInvalidationBus(),
        )

        val job = launch { cancellingRepo.create(draft) }
        api.entered.await() // the claim was won and the request is in flight
        job.cancelAndJoin() // the sheet was dismissed mid-save; the ViewModel scope is gone

        val queued = queue().single()
        assertEquals(MutationStatus.PENDING, queued.status)
        assertNull(queued.failureCode)
        assertEquals(1, syncRequests)
    }

    @Test
    fun `a corrupt payload settles the claim as a non-retryable validation failure`() = runTest {
        db.transactions().upsert(
            listOf(
                TransactionEntity(
                    "local-m-bad", "user-1", 1.0, "cat-food", "Lunch", "2026-10-05",
                    null, null, null, null, null, SyncStatus.PENDING,
                ),
            ),
        )
        db.mutations().insert(
            PendingMutationEntity(
                id = "m-bad", userId = "user-1", kind = MutationKind.CREATE_TRANSACTION,
                payloadJson = "not-json", localTransactionId = "local-m-bad",
                status = MutationStatus.PENDING, createdAt = 1,
            ),
        )

        assertEquals(DrainResult.DONE, repo.drainQueue("user-1"))

        val queued = queue().single()
        assertEquals(MutationStatus.FAILED, queued.status)
        assertEquals(FailureCode.VALIDATION, queued.failureCode)
        assertEquals(SyncStatus.FAILED, rows().single().syncStatus)
        assertEquals(0, server.requestCount)
    }

    private fun repoWithSession(
        store: FakeTokenStore,
        onResponse: ((okhttp3.Response) -> Unit)? = null,
        refresher: com.spenditos.android.core.auth.TokenRefresher,
    ): Pair<TransactionRepository, SessionManager> {
        val session = SessionManager(store, refresher, EpochClock { NOW }, AppEnvironment())
        val builder = okhttp3.OkHttpClient.Builder().addInterceptor(AuthInterceptor(session))
        // Runs on OkHttp's thread once a response is read but before AuthInterceptor sees it, so a
        // test can land a session change at an exact point of the send.
        if (onResponse != null) {
            builder.addInterceptor { chain -> chain.proceed(chain.request()).also(onResponse) }
        }
        val client = builder.build()
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
        lateinit var session: SessionManager
        // The switch lands while the refresh request is in flight (after the 401 was handled).
        val (repo, created) = repoWithSession(store) {
            signInAs(session, store, "user-2", "b")
            RefreshedTokens(TestJwt.token("user-1", NOW + 7200, nonce = "new"), TestJwt.token("user-1", NOW + 7200, nonce = "newid"))
        }
        session = created
        server.enqueue(MockResponse(code = 401, body = """{"error":"expired"}"""))

        assertEquals(CreateResult.SavedOffline, repo.create(draft))
        assertEquals(1, server.requestCount) // no retry was sent at all
        assertQueuedForUser1()
    }

    @Test
    fun `a sign-out between the 401 and the refresh keeps the create queued instead of rejecting it`() = runTest {
        val store = FakeTokenStore.signedIn("user-1")
        lateinit var session: SessionManager
        // First half of an account switch: the tokens are gone and no one is signed in yet.
        val (repo, created) = repoWithSession(store, onResponse = { if (it.code == 401) session.signOut() }) {
            RefreshedTokens(TestJwt.token("user-1", NOW + 7200, nonce = "new"), TestJwt.token("user-1", NOW + 7200, nonce = "newid"))
        }
        session = created
        server.enqueue(MockResponse(code = 401, body = """{"error":"expired"}"""))

        assertEquals(CreateResult.SavedOffline, repo.create(draft))
        assertEquals(1, server.requestCount)
        assertQueuedForUser1()
    }

    @Test
    fun `a full account switch between the 401 and the refresh keeps the create queued`() = runTest {
        val store = FakeTokenStore.signedIn("user-1")
        lateinit var session: SessionManager
        val (repo, created) = repoWithSession(store, onResponse = { if (it.code == 401) signInAs(session, store, "user-2", "b") }) {
            RefreshedTokens(TestJwt.token("user-2", NOW + 7200, nonce = "new"), TestJwt.token("user-2", NOW + 7200, nonce = "newid"))
        }
        session = created
        server.enqueue(MockResponse(code = 401, body = """{"error":"expired"}"""))

        assertEquals(CreateResult.SavedOffline, repo.create(draft))
        assertEquals(1, server.requestCount) // user-2's token never carried user-1's payload
        assertQueuedForUser1()
    }

    private suspend fun assertQueuedForUser1() {
        val row = queue().single()
        assertEquals(MutationStatus.PENDING, row.status)
        assertEquals("user-1", row.userId)
        assertNull(row.failureCode)
        assertEquals(SyncStatus.PENDING, rows().single().syncStatus)
    }
}
