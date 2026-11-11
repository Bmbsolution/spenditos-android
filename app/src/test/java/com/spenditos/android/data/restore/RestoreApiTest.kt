package com.spenditos.android.data.restore

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.SpenditosJson
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** The restore routes over real HTTP: paths, bodies, the `{data}` envelope and lenient enum decoding. */
class RestoreApiTest {
    private val server = MockWebServer()
    private lateinit var api: RestoreApi

    @Before
    fun setUp() {
        server.start()
        api = Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient())
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build().create(RestoreApi::class.java)
    }

    @After
    fun tearDown() = server.close()

    private fun session(status: String, extra: String = "", parts: String = "[]") =
        """{"data":{"id":"r1","status":"$status","totalBytes":5,"partSize":3,"partCount":2,"receivedParts":$parts,""" +
            """"createdAt":"2026-11-11T10:00:00Z","updatedAt":"2026-11-11T10:00:00Z","expiresAt":"2026-11-12T10:00:00Z","progress":null,"summary":null,"error":null$extra}}"""

    @Test
    fun `uploads a file end to end`() = runTest {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        server.enqueue(MockResponse(code = 201, body = session("uploading")))
        server.enqueue(MockResponse(code = 200, body = session("uploading", parts = "[0]")))
        server.enqueue(MockResponse(code = 200, body = session("uploading", parts = "[0,1]")))
        server.enqueue(MockResponse(code = 202, body = session("validating", parts = "[0,1]")))
        server.enqueue(
            MockResponse(
                code = 200,
                body = session("done", parts = "[0,1]").replace(
                    """"summary":null""",
                    """"summary":{"categories":1,"incomeAndBills":2,"debts":3,"rules":4,"transactions":5,"monthBalanceChoices":0,"categoriesMerged":1}""",
                ),
            ),
        )

        val updates = RestoreUploader(api, null, pollIntervalMs = 1).run(bytes, "ab".repeat(32)).toList()

        val finished = updates.last() as RestoreUpdate.Finished
        assertEquals(RestoreSummary(1, 2, 3, 4, 5, 0, 1), finished.summary)

        val start = server.takeRequest()
        assertEquals("POST", start.method)
        assertEquals("/user/restore", start.url.encodedPath)
        assertEquals("""{"totalBytes":5,"sha256":"${"ab".repeat(32)}","mode":"fresh"}""", start.body!!.utf8())
        val part0 = server.takeRequest()
        assertEquals("PUT", part0.method)
        assertEquals("/user/restore/r1/parts/0", part0.url.encodedPath)
        assertEquals("application/octet-stream", part0.headers["Content-Type"])
        assertArrayEquals(byteArrayOf(1, 2, 3), part0.body!!.toByteArray())
        val part1 = server.takeRequest()
        assertEquals("/user/restore/r1/parts/1", part1.url.encodedPath)
        assertArrayEquals(byteArrayOf(4, 5), part1.body!!.toByteArray())
        val complete = server.takeRequest()
        assertEquals("POST", complete.method)
        assertEquals("/user/restore/r1/complete", complete.url.encodedPath)
        val poll = server.takeRequest()
        assertEquals("GET", poll.method)
        assertEquals("/user/restore/r1", poll.url.encodedPath)
    }

    @Test
    fun `unknown enum values decode to UNKNOWN`() = runTest {
        server.enqueue(
            MockResponse(
                body = session("paused", extra = ""","futureField":1""").replace(
                    """"progress":null,"summary":null,"error":null""",
                    """"progress":{"phase":"avatars","done":1,"total":2},"summary":null,"error":{"code":"BrandNewError","message":"x"}""",
                ),
            ),
        )
        val decoded = api.get("r1", null).data!!
        assertEquals(RestoreSessionStatus.UNKNOWN, decoded.status)
        assertEquals(RestoreProgressPhase.UNKNOWN, decoded.progress?.phase)
        assertEquals(RestoreErrorCode.UNKNOWN, decoded.error?.code)
        assertNull(decoded.summary)
    }

    @Test
    fun `error envelopes surface the code`() = runTest {
        server.enqueue(MockResponse(code = 413, body = """{"message":"The file is larger than 50 MB","error":"BackupTooLarge"}"""))
        try {
            RestoreUploader(api, null).run(ByteArray(5), "ab".repeat(32)).toList()
            fail("expected failure")
        } catch (e: ApiError.Server) {
            assertEquals(413, e.status)
            assertEquals("BackupTooLarge", e.serverMessage)
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `start sends the mode`() = runTest {
        server.enqueue(MockResponse(code = 201, body = session("uploading")))
        api.start(StartRestoreRequest(5, "ab".repeat(32), RestoreMode.REPLACE), null)
        assertEquals("""{"totalBytes":5,"sha256":"${"ab".repeat(32)}","mode":"replace"}""", server.takeRequest().body!!.utf8())
    }

    @Test
    fun `new fields decode`() = runTest {
        server.enqueue(
            MockResponse(
                body = """{"data":{"id":"r1","mode":"replace","status":"done","replaced":{"entries":1240,"incomeAndBills":12,"debts":2,"categories":18,"rules":4},""" +
                    """"undo":{"status":"available","availableUntil":"2026-11-18T10:00:00Z"}}}""",
            ),
        )
        val decoded = api.get("r1", null).data!!
        assertEquals(RestoreMode.REPLACE, decoded.mode)
        assertEquals(AccountDataCounts(1240, 12, 2, 18, 4), decoded.replaced)
        assertEquals(RestoreUndo(RestoreUndoStatus.AVAILABLE, "2026-11-18T10:00:00Z"), decoded.undo)
    }

    @Test
    fun `a session without mode or undo reads as fresh with no undo`() = runTest {
        server.enqueue(MockResponse(body = session("done")))
        val decoded = api.get("r1", null).data!!
        assertEquals(RestoreMode.FRESH, decoded.mode)
        assertNull(decoded.undo)
        assertNull(decoded.replaced)
    }

    @Test
    fun `unknown mode undo status and the new phases decode safely`() = runTest {
        server.enqueue(
            MockResponse(
                body = """{"data":{"id":"r1","mode":"merge","status":"restoring","progress":{"phase":"snapshot","done":1,"total":2},"undo":{"status":"frozen","availableUntil":"x"}}}""",
            ),
        )
        val decoded = api.get("r1", null).data!!
        assertEquals(RestoreMode.UNKNOWN, decoded.mode)
        assertEquals(RestoreUndoStatus.UNKNOWN, decoded.undo?.status)
        assertEquals(RestoreProgressPhase.SNAPSHOT, decoded.progress?.phase)
        server.enqueue(MockResponse(body = """{"data":{"id":"r1","progress":{"phase":"wipe","done":0,"total":0}}}"""))
        assertEquals(RestoreProgressPhase.WIPE, api.get("r1", null).data!!.progress?.phase)
    }

    @Test
    fun `summary, list and undo routes`() = runTest {
        server.enqueue(MockResponse(body = """{"data":{"entries":3,"incomeAndBills":2,"debts":1,"categories":4,"rules":5}}"""))
        assertEquals(AccountDataCounts(3, 2, 1, 4, 5), api.dataSummary(null).data)
        assertEquals("/user/data-summary", server.takeRequest().url.encodedPath)

        server.enqueue(MockResponse(body = """{"data":[{"id":"a","undo":{"status":"available","availableUntil":"t"}},{"id":"b"}]}"""))
        assertEquals(listOf("a", "b"), api.list(null).data!!.map { it.id })
        assertEquals("/user/restores", server.takeRequest().url.encodedPath)

        server.enqueue(MockResponse(code = 202, body = session("done")))
        api.undo("r1", null)
        val undo = server.takeRequest()
        assertEquals("POST", undo.method)
        assertEquals("/user/restore/r1/undo", undo.url.encodedPath)
    }
}
