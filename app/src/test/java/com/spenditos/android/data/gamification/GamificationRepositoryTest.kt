package com.spenditos.android.data.gamification

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.testing.OfflineInterceptor
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class GamificationRepositoryTest {
    private val server = MockWebServer()
    private val offline = OfflineInterceptor()
    private lateinit var repository: GamificationRepository

    @Before
    fun setUp() {
        server.start()
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(offline).build()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GamificationApi::class.java)
        repository = GamificationRepository(api)
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `every endpoint decodes success and maps client server and network failures`() = runTest {
        val endpoints = listOf(
            Endpoint("GET", "/user/streak", "gamification-streak") { repository.getStreak() },
            Endpoint("GET", "/user/streak/calendar", "gamification-streak-calendar", query = "limit=30") { repository.getStreakCalendar() },
            Endpoint("POST", "/user/streak/freeze", "gamification-streak") { repository.useStreakFreeze() },
            Endpoint("GET", "/user/points", "gamification-points") { repository.getPoints() },
            Endpoint("GET", "/user/points/history", "gamification-points-history", query = "limit=10") { repository.getPointsHistory() },
            Endpoint("GET", "/user/level", "gamification-level") { repository.getLevel() },
            Endpoint("GET", "/user/challenges/today", "gamification-challenge") { repository.getTodayChallenge() },
            Endpoint("GET", "/user/challenges/history", "gamification-challenge-history", query = "limit=10") { repository.getChallengeHistory() },
            Endpoint("POST", "/user/challenges/fake-challenge-1/progress", "gamification-challenge", body = "{\"progress\":2}") {
                repository.updateChallengeProgress("fake-challenge-1", 2)
            },
            Endpoint("POST", "/user/challenges/fake-challenge-1/complete", "gamification-challenge") {
                repository.completeChallenge("fake-challenge-1")
            },
            Endpoint("POST", "/user/challenges/complete", "gamification-challenge") { repository.completeCurrentChallenge() },
            Endpoint("GET", "/achievements", "gamification-achievements") { repository.getAchievementDefinitions() },
            Endpoint("GET", "/user/achievements", "gamification-user-achievements") { repository.getUserAchievements() },
            Endpoint("GET", "/user/achievements/week_1", "gamification-achievement-progress") { repository.getAchievementProgress("week_1") },
            Endpoint("GET", "/user/progress-summary", "gamification-progress-summary") { repository.getProgressSummary() },
        )

        endpoints.forEach { endpoint ->
            server.enqueue(MockResponse(body = fixture(endpoint.fixture)))
            endpoint.call()
            val request = server.takeRequest()
            assertEquals(endpoint.method, request.method)
            assertEquals(endpoint.path, request.url.encodedPath)
            endpoint.query?.let { (key, value) -> assertEquals(value, request.url.queryParameter(key)) }
            endpoint.body?.let { assertEquals(it, request.body!!.utf8()) }

            server.enqueue(MockResponse(code = 400, body = "{\"error\":\"Invalid request\"}"))
            expectServerError(endpoint, 400)

            server.enqueue(MockResponse(code = 503, body = "{\"error\":\"Unavailable\"}"))
            expectServerError(endpoint, 503)

            val requestsBeforeOffline = server.requestCount
            offline.failNext = 1
            try {
                endpoint.call()
                throw AssertionError("expected a network error for ${endpoint.path}")
            } catch (error: ApiError.Network) {
                assertTrue(error.cause != null)
            }
            assertEquals(requestsBeforeOffline, server.requestCount)
        }

        server.enqueue(MockResponse(code = 409, body = "{\"error\":\"AutomaticOnly\"}"))
        expectServerError(Endpoint("POST", "/user/streak/freeze", "gamification-streak") { repository.useStreakFreeze() }, 409)
        server.enqueue(MockResponse(code = 403, body = "{\"error\":\"Forbidden\"}"))
        expectServerError(
            Endpoint("POST", "/user/challenges/fake-challenge-1/progress", "gamification-challenge") {
                repository.updateChallengeProgress("fake-challenge-1", 2)
            },
            403,
        )
    }

    private suspend fun expectServerError(endpoint: Endpoint, status: Int) {
        try {
            endpoint.call()
            throw AssertionError("expected HTTP $status for ${endpoint.path}")
        } catch (error: ApiError.Server) {
            assertEquals(status, error.status)
            val request = server.takeRequest()
            assertEquals(endpoint.method, request.method)
            assertEquals(endpoint.path, request.url.encodedPath)
        }
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResource("contract/handwritten/$name.json")) { "missing $name fixture" }.readText()

    private class Endpoint(
        val method: String,
        val path: String,
        val fixture: String,
        query: String? = null,
        val body: String? = null,
        val call: suspend () -> Any,
    ) {
        val query: Pair<String, String>? = query?.split("=", limit = 2)?.let { it[0] to it[1] }
    }
}
