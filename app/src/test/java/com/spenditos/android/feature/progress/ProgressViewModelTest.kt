package com.spenditos.android.feature.progress

import androidx.lifecycle.ViewModelStore
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.gamification.GamificationDataSource
import com.spenditos.android.data.model.AchievementDefinition
import com.spenditos.android.data.model.AchievementProgressDetail
import com.spenditos.android.data.model.AchievementRequirement
import com.spenditos.android.data.model.GamificationChallenge
import com.spenditos.android.data.model.GamificationLevelInfo
import com.spenditos.android.data.model.GamificationPointsBalance
import com.spenditos.android.data.model.GamificationProgressSummary
import com.spenditos.android.data.model.PointsTransaction
import com.spenditos.android.data.model.ProgressAchievementCounts
import com.spenditos.android.data.model.ProgressChallenge
import com.spenditos.android.data.model.ProgressGoal
import com.spenditos.android.data.model.ProgressPoints
import com.spenditos.android.data.model.ProgressStreak
import com.spenditos.android.data.model.StreakCalendarDay
import com.spenditos.android.data.model.StreakInfo
import com.spenditos.android.data.model.UserAchievement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {
    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `partial failures leave independent sections loaded`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeGamificationDataSource().apply {
            calendarFailure = ApiError.Network(IOException("offline"))
            achievementsFailure = ApiError.Server(503, "Unavailable")
        }
        val viewModel = ProgressViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.hasLoaded)
        assertFalse(state.isLoading)
        assertEquals(4, state.streak?.currentStreak)
        assertTrue(state.calendarFailed)
        assertEquals(230, state.points?.available)
        assertEquals("This week's challenge", state.challenge?.title)
        assertTrue(state.achievementsFailed)
        assertEquals(10, state.pointsHistory.size)
        assertEquals(1, state.challengeHistory.size)
    }

    @Test
    fun `calendar remains loaded when the streak endpoint fails`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeGamificationDataSource().apply {
            streakFailure = ApiError.Server(500, "Unavailable")
        }
        val viewModel = ProgressViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.streakFailed)
        assertTrue(viewModel.state.value.calendarLoaded)
        assertEquals(1, viewModel.state.value.calendar.size)
        assertEquals(230, viewModel.state.value.points?.available)
    }

    @Test
    fun `refresh reloads sections and keeps previous data when one endpoint fails`() = runTest {
        listOf(ApiError.Server(400, "Invalid response"), ApiError.Network(IOException("offline"))).forEach { failure ->
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeGamificationDataSource()
            val viewModel = ProgressViewModel(repository)
            viewModel.load()
            advanceUntilIdle()
            assertEquals(1, repository.calls["streak"])

            repository.streak = StreakInfo(currentStreak = 5, longestStreak = 12, streakFreezes = 0)
            repository.pointsFailure = failure
            viewModel.refresh()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse(state.isRefreshing)
            assertEquals(5, state.streak?.currentStreak)
            assertEquals(230, state.points?.available)
            assertTrue(state.pointsFailed)
            assertEquals(2, repository.calls["streak"])
            assertEquals(2, repository.calls["points"])
            ViewModelStore().apply { put("progress", viewModel); clear() }
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `eligible challenge can be marked complete and points refresh afterward`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeGamificationDataSource()
        val viewModel = ProgressViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.completeChallenge()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.challenge?.completed == true)
        assertFalse(viewModel.state.value.challengeActionFailed)
        assertEquals(1, repository.calls["completeChallenge"])
        assertEquals(2, repository.calls["points"])
        assertEquals(2, repository.calls["level"])
    }

    @Test
    fun `challenge completion handles a client error and a network failure quietly`() = runTest {
        listOf(ApiError.Server(400, "Not ready"), ApiError.Network(IOException("offline"))).forEach { failure ->
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeGamificationDataSource().apply { completionFailure = failure }
            val viewModel = ProgressViewModel(repository)
            viewModel.load()
            advanceUntilIdle()

            viewModel.completeChallenge()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.challengeActionFailed)
            assertFalse(viewModel.state.value.isCompletingChallenge)
            assertEquals(1, repository.calls["completeChallenge"])
            ViewModelStore().apply { put("progress", viewModel); clear() }
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `achievement details load and detail failures are independent`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeGamificationDataSource()
        val viewModel = ProgressViewModel(repository)
        viewModel.load()
        advanceUntilIdle()
        val achievement = viewModel.state.value.achievements.single()

        viewModel.openAchievement(achievement)
        advanceUntilIdle()
        assertEquals("week_1", viewModel.state.value.achievementDetail?.achievementId)
        viewModel.dismissAchievement()
        assertNull(viewModel.state.value.selectedAchievement)

        listOf(ApiError.Server(404, "Not found"), ApiError.Network(IOException("offline"))).forEach { failure ->
            repository.detailFailure = failure
            viewModel.openAchievement(achievement)
            advanceUntilIdle()
            assertTrue(viewModel.state.value.achievementDetailFailed)
            assertFalse(viewModel.state.value.isLoadingAchievementDetail)
            viewModel.dismissAchievement()
        }
        assertEquals(3, repository.calls["achievementDetail"])
    }

    @Test
    fun `streak view model loads once, keeps zero for the tile and hides failed streaks`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeGamificationDataSource()
        val chip = StreakChipViewModel(repository)
        advanceUntilIdle()
        assertEquals(4, chip.streak.value)
        assertEquals(1, repository.calls["streak"])
        ViewModelStore().apply { put("chip", chip); clear() }

        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val zero = StreakChipViewModel(FakeGamificationDataSource().apply { streak = StreakInfo() })
        advanceUntilIdle()
        // The tile shows 0 days; only the chip composable hides a zero streak.
        assertEquals(0, zero.streak.value)
        ViewModelStore().apply { put("chip", zero); clear() }

        listOf(ApiError.Server(400, "Invalid"), ApiError.Network(IOException("offline"))).forEach { failure ->
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val hidden = StreakChipViewModel(FakeGamificationDataSource().apply { streakFailure = failure })
            advanceUntilIdle()
            assertNull(hidden.streak.value)
            ViewModelStore().apply { put("chip", hidden); clear() }
            Dispatchers.resetMain()
        }
    }

    private class FakeGamificationDataSource : GamificationDataSource {
        val calls = mutableMapOf<String, Int>()
        var streak = StreakInfo(currentStreak = 4, longestStreak = 12, streakFreezes = 1)
        var calendar = listOf(StreakCalendarDay(LocalDate.parse("2026-10-06"), hasEntry = true, isStreakDay = true))
        var points = GamificationPointsBalance(240, 10, 230, 3, 60, 40.0)
        var level = GamificationLevelInfo(3, "Financial Explorer", 240, 200, 60, 40.0, listOf("streak_freeze"))
        var challenge = GamificationChallenge(
            id = "fake-challenge-1", type = "transaction_count", title = "This week's challenge",
            description = "Record three entries.", difficulty = "easy", target = 3, current = 3,
            completed = false, date = LocalDate.parse("2026-10-05"), reward = 25, period = "weekly",
        )
        var challengeHistory = listOf(challenge.copy(completed = true))
        var achievements = listOf(UserAchievement("week_1", "One Week Strong", "Track for seven days", "📅", "tracking", 7, 2, null))
        var pointsHistory = (1..12).map { PointsTransaction("fake-point-$it", 1.0, "TRANSACTION_LOGGED", it, "2026-10-06T12:00:00Z") }
        var streakFailure: Throwable? = null
        var calendarFailure: Throwable? = null
        var pointsFailure: Throwable? = null
        var achievementsFailure: Throwable? = null
        var completionFailure: Throwable? = null
        var detailFailure: Throwable? = null

        private fun hit(name: String) { calls[name] = (calls[name] ?: 0) + 1 }
        private fun fail(error: Throwable?) { if (error != null) throw error }

        override suspend fun getStreak(): StreakInfo { hit("streak"); fail(streakFailure); return streak }
        override suspend fun getStreakCalendar(): List<StreakCalendarDay> { hit("calendar"); fail(calendarFailure); return calendar }
        override suspend fun useStreakFreeze(): StreakInfo = streak
        override suspend fun getPoints(): GamificationPointsBalance { hit("points"); fail(pointsFailure); return points }
        override suspend fun getPointsHistory(limit: Int): List<PointsTransaction> { hit("pointsHistory"); return pointsHistory.take(limit) }
        override suspend fun getLevel(): GamificationLevelInfo { hit("level"); return level }
        override suspend fun getTodayChallenge(): GamificationChallenge { hit("challenge"); return challenge }
        override suspend fun getChallengeHistory(limit: Int): List<GamificationChallenge> { hit("challengeHistory"); return challengeHistory.take(limit) }
        override suspend fun updateChallengeProgress(challengeId: String, progress: Int): GamificationChallenge = challenge
        override suspend fun completeChallenge(challengeId: String): GamificationChallenge {
            hit("completeChallenge"); fail(completionFailure); return challenge.copy(completed = true)
        }
        override suspend fun completeCurrentChallenge(): GamificationChallenge = challenge
        override suspend fun getAchievementDefinitions(): List<AchievementDefinition> = listOf(
            AchievementDefinition("week_1", "One Week Strong", "Track for seven days", "📅", 100, "tracking", AchievementRequirement("consecutive_days", 7)),
        )
        override suspend fun getUserAchievements(): List<UserAchievement> { hit("achievements"); fail(achievementsFailure); return achievements }
        override suspend fun getAchievementProgress(achievementId: String): AchievementProgressDetail {
            hit("achievementDetail"); fail(detailFailure); return AchievementProgressDetail(achievementId, false, 2)
        }
        override suspend fun getProgressSummary() = GamificationProgressSummary(
            "fake-user", ProgressPoints(230, 240, 3, 60, 40.0), ProgressStreak(4, 12, true, 1),
            ProgressChallenge("fake-challenge-1", "Challenge", "Description", "transaction_count", "easy", 1, 3, false, 25, "2026-10-12T00:00:00Z"),
            listOf(ProgressGoal("goal", "Goal", "Description", 1, 3, 33, "transaction_target")),
            ProgressAchievementCounts(1, 20, listOf("first_transaction")), "2026-10-06T12:00:00Z",
        )
    }
}
