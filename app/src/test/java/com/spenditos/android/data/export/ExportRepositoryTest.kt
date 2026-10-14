package com.spenditos.android.data.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import android.net.Uri
import com.spenditos.android.core.auth.AuthTokens
import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionChangedException
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.auth.TokenKey
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.AuthInterceptor
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import com.spenditos.android.testing.OfflineInterceptor
import com.spenditos.android.testing.TestJwt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
class ExportRepositoryTest {
    private val server = MockWebServer()
    private val offline = OfflineInterceptor()
    private lateinit var context: Context
    private lateinit var repository: ExportRepository
    private lateinit var store: FakeTokenStore
    private lateinit var session: SessionManager
    private var afterResponse: () -> Unit = {}
    private val clock = Clock.fixed(Instant.parse("2026-10-14T15:00:00Z"), ZoneId.of("UTC"))

    @Before
    fun setUp() {
        server.start()
        context = ApplicationProvider.getApplicationContext()
        File(context.cacheDir, "exports").deleteRecursively()
        File(context.cacheDir, "full-copy").deleteRecursively()
        store = FakeTokenStore.signedIn()
        session = SessionManager(store, { RefreshedTokens(TestJwt.token("user-1", NOW + 7200, nonce = "new"), TestJwt.token("user-1", NOW + 7200, nonce = "id2")) }, EpochClock { NOW }, AppEnvironment())
        // No Accept-Encoding set by hand: OkHttp adds gzip and gunzips on its own.
        val client = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .addInterceptor { chain -> chain.proceed(chain.request()).also { afterResponse() } }
            .addInterceptor(AuthInterceptor(session))
            .addInterceptor(offline)
            .build()
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(client).build().create(ExportApi::class.java)
        repository = ExportRepository(context, api, session, Dispatchers.Unconfined, clock)
    }

    private fun switchToSecondAccount() {
        session.signOut()
        val access = TestJwt.token("user-2", NOW + 3600, nonce = "b-access")
        val id = TestJwt.token("user-2", NOW + 3600, nonce = "b-id")
        store.values[TokenKey.REFRESH] = "refresh-user-2"
        session.signIn(AuthTokens(access, "refresh-user-2", id))
    }

    @After
    fun tearDown() {
        server.close()
        File(context.cacheDir, "exports").deleteRecursively()
        File(context.cacheDir, "full-copy").deleteRecursively()
    }

    private val start = LocalDate.of(2026, 10, 1)
    private val end = LocalDate.of(2026, 10, 31)

    @Test
    fun `writes the csv under the server filename and asks for the right range`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .headers(Headers.headersOf("Content-Type", "text/csv", "Content-Disposition", "attachment; filename=\"transactions-2026-10-01-to-2026-10-31.csv\""))
                .body("Date,Description,Amount\n2026-10-01,Coffee,4.5\n").build(),
        )
        val file = repository.exportCsv(start, end)
        assertEquals("transactions-2026-10-01-to-2026-10-31.csv", file.name)
        assertEquals("Date,Description,Amount\n2026-10-01,Coffee,4.5\n", file.readText())
        val request = server.takeRequest()
        assertEquals("/transactions/export", request.url.encodedPath)
        assertEquals("2026-10-01", request.url.queryParameter("startDate"))
        assertEquals("2026-10-31", request.url.queryParameter("endDate"))
        assertEquals("csv", request.url.queryParameter("format"))
        assertEquals(null, request.url.queryParameter("categoryIds"))
    }

    @Test
    fun `a hostile filename can not escape the export folder`() {
        assertEquals("transactions.csv", ExportRepository.sanitizeFilename("../../etc/transactions", "fallback.csv"))
        assertEquals("fallback.csv", ExportRepository.sanitizeFilename("..", "fallback.csv"))
        assertEquals("fallback.csv", ExportRepository.sanitizeFilename(null, "fallback.csv"))
        assertEquals("a_b.csv", ExportRepository.sanitizeFilename("a b", "fallback.csv"))
        assertEquals("x.csv", ExportRepository.sanitizeFilename("C:\\temp\\x.csv", "fallback.csv"))
        assertEquals("transactions-a.csv", ExportRepository.filenameFrom("attachment; filename=\"transactions-a.csv\""))
    }

    @Test
    fun `missing header falls back to a generated name`() = runTest {
        server.enqueue(MockResponse.Builder().body("x").build())
        assertEquals("spenditos-transactions-2026-10-01-to-2026-10-31.csv", repository.exportCsv(start, end).name)
    }

    @Test
    fun `a 400 shows the server's readable message`() = runTest {
        server.enqueue(MockResponse.Builder().code(400).body("""{"error":"InvalidQuery","message":"Date range must not exceed 2 years."}""").build())
        try { repository.exportCsv(start, end); fail() } catch (e: ApiError.Server) {
            assertEquals(400, e.status)
            assertEquals("Date range must not exceed 2 years.", e.serverMessage)
        }
    }

    @Test
    fun `401 5xx and network failures are typed`() = runTest {
        // The auth interceptor refreshes once and retries; a second 401 reaches the caller.
        repeat(2) { server.enqueue(MockResponse.Builder().code(401).body("""{"error":"Unauthorized"}""").build()) }
        try { repository.exportCsv(start, end); fail() } catch (e: ApiError.Server) { assertEquals(401, e.status) }
        server.enqueue(MockResponse.Builder().code(503).body("""{"error":"Unavailable"}""").build())
        try { repository.exportCsv(start, end); fail() } catch (e: ApiError.Server) { assertEquals(503, e.status) }
        offline.failNext = 1
        try { repository.exportCsv(start, end); fail() } catch (e: ApiError.Network) { /* expected */ }
    }

    @Test
    fun `old exports are removed at launch and on request`() = runTest {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        File(dir, "old.csv").writeText("private")
        ExportRepository(context, ExportRetrofit.api(server, offline), session, Dispatchers.Unconfined, clock)
        assertFalse(File(dir, "old.csv").exists())
        server.enqueue(MockResponse.Builder().body("x").build())
        val file = repository.exportCsv(start, end)
        assertTrue(file.exists())
        repository.deleteExports()
        assertFalse(file.exists())
    }

    private fun gzip(bytes: ByteArray): Buffer {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return Buffer().write(out.toByteArray())
    }

    private val payload = """{"format":"spenditos-export","version":1,"transactions":[]}""".toByteArray()

    private fun fullCopyResponse(body: ByteArray = payload, disposition: String? = "attachment; filename=\"spenditos-data-2026-10-13.json\"") =
        MockResponse.Builder()
            .apply { if (disposition != null) addHeader("Content-Disposition", disposition) }
            .addHeader("Content-Type", "application/json")
            .addHeader("Content-Encoding", "gzip")
            .body(gzip(body))
            .build()

    @Test
    fun `full copy is gunzipped transparently, named by the server and streamed to the chosen document`() = runTest {
        server.enqueue(fullCopyResponse())
        val copy = repository.prepareFullCopy()
        val request = server.takeRequest()
        assertEquals("/user/export", request.url.encodedPath)
        assertEquals("gzip", request.headers["Accept-Encoding"])
        assertEquals("spenditos-data-2026-10-13.json", copy.suggestedName)

        val target = File(context.cacheDir, "picked.json")
        repository.saveFullCopy(copy, Uri.fromFile(target))
        assertEquals(String(payload), target.readText())
        assertFalse("the temporary copy is removed", copy.file.exists())
    }

    @Test
    fun `a large full copy streams byte for byte`() = runTest {
        val big = ByteArray(3 * 1024 * 1024) { (it % 251).toByte() }
        server.enqueue(fullCopyResponse(big))
        val copy = repository.prepareFullCopy()
        val out = ByteArrayOutputStream()
        repository.saveTo(copy, out)
        assertTrue(big.contentEquals(out.toByteArray()))
    }

    @Test
    fun `full copy without a filename header falls back to today's date`() = runTest {
        server.enqueue(fullCopyResponse(disposition = null))
        assertEquals("spenditos-data-2026-10-14.json", repository.prepareFullCopy().suggestedName)
    }

    @Test
    fun `server filenames for the json file are sanitized`() {
        assertEquals("spenditos-data-2026-10-13.json", ExportRepository.filenameFrom("attachment; filename=\"spenditos-data-2026-10-13.json\""))
        assertEquals("x.json", ExportRepository.sanitizeFilename("../../x", "fallback.json", ".json"))
        assertEquals("fallback.json", ExportRepository.sanitizeFilename("..", "fallback.json", ".json"))
    }

    @Test
    fun `a 500 ExportFailed surfaces the server message and leaves no file`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(500)
                .body("""{"error":"ExportFailed","message":"Could not build your data file. Please try again."}""").build(),
        )
        try { repository.prepareFullCopy(); fail() } catch (e: ApiError.Server) {
            assertEquals(500, e.status)
            assertEquals("Could not build your data file. Please try again.", e.serverMessage)
        }
        assertTrue(File(context.cacheDir, "full-copy").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `full copy offline is a network error and signed out is unauthorized`() = runTest {
        offline.failNext = 1
        try { repository.prepareFullCopy(); fail() } catch (e: ApiError.Network) { /* expected */ }
        session.signOut()
        try { repository.prepareFullCopy(); fail() } catch (e: ApiError.Unauthorized) { /* expected */ }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an account switch while the file downloads discards it`() = runTest {
        server.enqueue(fullCopyResponse())
        afterResponse = { switchToSecondAccount() }
        try { repository.prepareFullCopy(); fail() } catch (_: SessionChangedException) { /* expected */ }
        assertTrue(File(context.cacheDir, "full-copy").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `an account switch while the user picks a location writes nothing and removes the copy`() = runTest {
        server.enqueue(fullCopyResponse())
        val copy = repository.prepareFullCopy()
        switchToSecondAccount()
        val target = File(context.cacheDir, "picked-after-switch.json")
        try { repository.saveFullCopy(copy, Uri.fromFile(target)); fail() } catch (_: SessionChangedException) { /* expected */ }
        assertEquals(0L, target.length())
        assertFalse(copy.file.exists())
    }

    @Test
    fun `signing out mid-copy stops the write at the next chunk`() = runTest {
        val big = ByteArray(1024 * 1024) { 7 }
        server.enqueue(fullCopyResponse(big))
        val copy = repository.prepareFullCopy()
        val out = object : ByteArrayOutputStream() {
            override fun write(b: ByteArray, off: Int, len: Int) {
                super.write(b, off, len)
                session.signOut()
            }
        }
        try { repository.saveTo(copy, out); fail() } catch (_: SessionChangedException) { /* expected */ }
        assertTrue("only the first chunk was written", out.size() <= 64 * 1024)
    }

    @Test
    fun `dismissing the picker removes the temporary copy`() = runTest {
        server.enqueue(fullCopyResponse())
        val copy = repository.prepareFullCopy()
        assertTrue(copy.file.exists())
        repository.discardFullCopy(copy)
        assertFalse(copy.file.exists())
    }

    private object ExportRetrofit {
        fun api(server: MockWebServer, offline: OfflineInterceptor): ExportApi =
            Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient.Builder().addInterceptor(offline).build()).build()
                .create(ExportApi::class.java)
    }
}
