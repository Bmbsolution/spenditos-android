package com.spenditos.android.data.restore

import com.spenditos.android.core.auth.SessionBinding
import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.ApiError
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import okhttp3.RequestBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class RestoreUploaderTest {
    private class FakeRestoreApi(
        val partSize: Int,
        val total: Int,
        var received: List<Int> = emptyList(),
    ) : RestoreApi {
        val partCount = (total + partSize - 1) / partSize
        val sent = mutableMapOf<Int, ByteArray>()
        val partTypes = mutableListOf<String?>()
        var startBody: StartRestoreRequest? = null
        var partCalls = 0
        var completeCalls = 0
        var partFailures = mutableListOf<Throwable>()
        var completeFailure: Throwable? = null
        var statusQueue = ArrayDeque<RestoreSession>()
        private val statuses = ArrayList<RestoreSession>()

        fun session(status: RestoreSessionStatus, parts: List<Int> = received) = RestoreSession(
            id = "r1", status = status, totalBytes = total.toLong(), partSize = partSize, partCount = partCount, receivedParts = parts,
        )

        override suspend fun start(body: StartRestoreRequest, binding: SessionBinding?): ApiEnvelope<RestoreSession> {
            startBody = body
            return ApiEnvelope(session(RestoreSessionStatus.UPLOADING))
        }

        override suspend fun putPart(id: String, index: Int, bytes: RequestBody, binding: SessionBinding?): ApiEnvelope<RestoreSession> {
            partCalls++
            partFailures.removeFirstOrNull()?.let { throw it }
            partTypes += bytes.contentType()?.toString()
            sent[index] = Buffer().also { bytes.writeTo(it) }.readByteArray()
            received = (received + index).sorted()
            return ApiEnvelope(session(RestoreSessionStatus.UPLOADING))
        }

        override suspend fun get(id: String, binding: SessionBinding?): ApiEnvelope<RestoreSession> {
            val next = statusQueue.removeFirstOrNull() ?: session(RestoreSessionStatus.DONE)
            statuses += next
            return ApiEnvelope(next)
        }

        override suspend fun complete(id: String, binding: SessionBinding?): ApiEnvelope<RestoreSession> {
            completeCalls++
            completeFailure?.let { throw it }
            return ApiEnvelope(session(RestoreSessionStatus.VALIDATING))
        }
    }

    private val bytes = ByteArray(10) { it.toByte() }
    private fun uploader(api: RestoreApi) = RestoreUploader(api, SessionBinding("u", 1), pollIntervalMs = 2_000, backoffMs = { 100L })
    private fun network() = ApiError.Network(IOException("down"))

    @Test
    fun `slices by the server part size and sends octet-stream`() = runTest {
        val api = FakeRestoreApi(partSize = 4, total = 10)
        val updates = uploader(api).run(bytes, "abc").toList()
        assertEquals(StartRestoreRequest(10, "abc"), api.startBody)
        assertArrayEquals(bytes.copyOfRange(0, 4), api.sent[0])
        assertArrayEquals(bytes.copyOfRange(4, 8), api.sent[1])
        assertArrayEquals(bytes.copyOfRange(8, 10), api.sent[2])
        assertTrue(api.partTypes.all { it == "application/octet-stream" })
        assertEquals(
            listOf(
                RestoreUpdate.Uploading(0, 3), RestoreUpdate.Uploading(1, 3), RestoreUpdate.Uploading(2, 3), RestoreUpdate.Uploading(3, 3),
                RestoreUpdate.Checking, RestoreUpdate.Finished(null),
            ),
            updates,
        )
        assertEquals(1, api.completeCalls)
    }

    @Test
    fun `skips parts the server already has`() = runTest {
        val api = FakeRestoreApi(partSize = 4, total = 10, received = listOf(0, 2))
        val updates = uploader(api).run(bytes, "abc").toList()
        assertEquals(setOf(1), api.sent.keys)
        assertEquals(RestoreUpdate.Uploading(2, 3), updates.first())
    }

    @Test
    fun `retries a part on network and 5xx errors then succeeds`() = runTest {
        val api = FakeRestoreApi(partSize = 10, total = 10)
        api.partFailures = mutableListOf(network(), ApiError.Server(503, "x"), network())
        val updates = uploader(api).run(bytes, "abc").toList()
        assertEquals(4, api.partCalls)
        assertTrue(updates.last() is RestoreUpdate.Finished)
        assertEquals(100L + 100L + 100L, currentTime - 2_000)
    }

    @Test
    fun `gives up after three retries`() = runTest {
        val api = FakeRestoreApi(partSize = 10, total = 10)
        api.partFailures = MutableList(10) { ApiError.Server(500, "x") }
        try {
            uploader(api).run(bytes, "abc").toList()
            fail("expected failure")
        } catch (e: ApiError.Server) {
            assertEquals(500, e.status)
        }
        assertEquals(4, api.partCalls)
    }

    @Test
    fun `does not retry a client error`() = runTest {
        val api = FakeRestoreApi(partSize = 10, total = 10)
        api.partFailures = mutableListOf(ApiError.Server(400, "PartSizeMismatch"))
        try {
            uploader(api).run(bytes, "abc").toList()
            fail("expected failure")
        } catch (e: ApiError.Server) {
            assertEquals("PartSizeMismatch", e.serverMessage)
        }
        assertEquals(1, api.partCalls)
    }

    @Test
    fun `http 410 is an expired restore`() = runTest {
        val api = FakeRestoreApi(partSize = 10, total = 10)
        api.partFailures = mutableListOf(ApiError.Server(410, "RestoreExpired"))
        try {
            uploader(api).run(bytes, "abc").toList()
            fail("expected failure")
        } catch (_: RestoreExpiredException) {
        }
    }

    @Test
    fun `polls every two seconds until done with the summary`() = runTest {
        val api = FakeRestoreApi(partSize = 10, total = 10)
        val summary = RestoreSummary(transactions = 5, incomeAndBills = 2)
        api.statusQueue = ArrayDeque(
            listOf(
                api.session(RestoreSessionStatus.VALIDATING),
                api.session(RestoreSessionStatus.RESTORING).copy(progress = RestoreProgress(RestoreProgressPhase.TRANSACTIONS, 3, 5)),
                api.session(RestoreSessionStatus.DONE).copy(summary = summary),
            ),
        )
        val updates = uploader(api).run(bytes, "abc").toList()
        assertEquals(
            listOf(
                RestoreUpdate.Checking,
                RestoreUpdate.Checking,
                RestoreUpdate.Restoring(RestoreProgress(RestoreProgressPhase.TRANSACTIONS, 3, 5)),
                RestoreUpdate.Finished(summary),
            ),
            updates.drop(updates.indexOfFirst { it is RestoreUpdate.Checking }),
        )
        assertEquals(3 * 2_000L, currentTime)
    }

    @Test
    fun `failed job throws its error code`() = runTest {
        val api = FakeRestoreApi(partSize = 10, total = 10)
        api.statusQueue = ArrayDeque(
            listOf(api.session(RestoreSessionStatus.FAILED).copy(error = RestoreFailure(RestoreErrorCode.ACCOUNT_NOT_EMPTY, "not empty"))),
        )
        try {
            uploader(api).run(bytes, "abc").toList()
            fail("expected failure")
        } catch (e: RestoreFailedException) {
            assertEquals(RestoreErrorCode.ACCOUNT_NOT_EMPTY, e.code)
        }
    }

    @Test
    fun `expired status throws`() = runTest {
        val api = FakeRestoreApi(partSize = 10, total = 10)
        api.statusQueue = ArrayDeque(listOf(api.session(RestoreSessionStatus.EXPIRED)))
        try {
            uploader(api).run(bytes, "abc").toList()
            fail("expected failure")
        } catch (_: RestoreExpiredException) {
        }
    }

    @Test
    fun `a session handed back to uploading is completed again`() = runTest {
        val api = FakeRestoreApi(partSize = 10, total = 10)
        api.statusQueue = ArrayDeque(listOf(api.session(RestoreSessionStatus.UPLOADING)))
        val updates = uploader(api).run(bytes, "abc").toList()
        assertEquals(2, api.completeCalls)
        assertTrue(updates.last() is RestoreUpdate.Finished)
    }
}
