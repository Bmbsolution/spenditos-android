package com.spenditos.android.data.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.testing.OfflineInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import okhttp3.OkHttpClient
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
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ExportRepositoryTest {
    private val server = MockWebServer()
    private val offline = OfflineInterceptor()
    private lateinit var context: Context
    private lateinit var repository: ExportRepository

    @Before
    fun setUp() {
        server.start()
        context = ApplicationProvider.getApplicationContext()
        File(context.cacheDir, "exports").deleteRecursively()
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(offline).build()
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(client).build().create(ExportApi::class.java)
        repository = ExportRepository(context, api, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        server.close()
        File(context.cacheDir, "exports").deleteRecursively()
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
        assertEquals("transactions-2026-10-01-to-2026-10-31.csv", repository.exportCsv(start, end).name)
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
        server.enqueue(MockResponse.Builder().code(401).body("""{"error":"Unauthorized"}""").build())
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
        ExportRepository(context, repository.let { ExportRetrofit.api(server, offline) }, Dispatchers.Unconfined)
        assertFalse(File(dir, "old.csv").exists())
        server.enqueue(MockResponse.Builder().body("x").build())
        val file = repository.exportCsv(start, end)
        assertTrue(file.exists())
        repository.deleteExports()
        assertFalse(file.exists())
    }

    private object ExportRetrofit {
        fun api(server: MockWebServer, offline: OfflineInterceptor): ExportApi =
            Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient.Builder().addInterceptor(offline).build()).build()
                .create(ExportApi::class.java)
    }
}
