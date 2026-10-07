package com.spenditos.android.contract

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.model.AchievementDefinition
import com.spenditos.android.data.model.AchievementProgressDetail
import com.spenditos.android.data.model.GamificationChallenge
import com.spenditos.android.data.model.GamificationLevelInfo
import com.spenditos.android.data.model.GamificationPointsBalance
import com.spenditos.android.data.model.GamificationProgressSummary
import com.spenditos.android.data.model.PointsTransaction
import com.spenditos.android.data.model.StreakCalendarDay
import com.spenditos.android.data.model.StreakInfo
import com.spenditos.android.data.model.UpdateChallengeProgressRequest
import com.spenditos.android.data.model.UserAchievement
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Handwritten response fixtures use fake values and mirror the gamification handlers. */
class GamificationContractTest {
    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResource("contract/handwritten/$name.json")) { "missing $name fixture" }.readText()

    private fun <T> envelope(name: String, serializer: KSerializer<T>): T =
        SpenditosJson.decodeFromString(ApiEnvelope.serializer(serializer), fixture(name)).data!!

    @Test
    fun `streak and backend calendar array decode`() {
        val streak = envelope("gamification-streak", StreakInfo.serializer())
        assertEquals(4, streak.currentStreak)
        assertEquals("2026-10-06", streak.lastEntryDate.toString())

        val days = envelope("gamification-streak-calendar", kotlinx.serialization.builtins.ListSerializer(StreakCalendarDay.serializer()))
        assertTrue(days.first().hasEntry)
        assertTrue(days.last().freezeUsed)
    }

    @Test
    fun `points balance history and handler level shape decode`() {
        val balance = envelope("gamification-points", GamificationPointsBalance.serializer())
        assertEquals(230, balance.available)
        assertEquals(40.0, balance.progressToNextLevel, 0.0)

        val history = envelope("gamification-points-history", kotlinx.serialization.builtins.ListSerializer(PointsTransaction.serializer()))
        assertEquals("TRANSACTION_LOGGED", history.single().action)
        assertEquals(10.0, history.single().amount, 0.0)

        val level = envelope("gamification-level", GamificationLevelInfo.serializer())
        assertEquals(listOf("streak_freeze"), level.unlocks)
    }

    @Test
    fun `challenge and challenge history decode`() {
        val challenge = envelope("gamification-challenge", GamificationChallenge.serializer())
        assertEquals("fake-challenge-1", challenge.id)
        assertEquals("2026-10-05", challenge.date.toString())
        assertEquals("weekly", challenge.period)

        val history = envelope("gamification-challenge-history", kotlinx.serialization.builtins.ListSerializer(GamificationChallenge.serializer()))
        assertTrue(history.single().completed)

        val request = UpdateChallengeProgressRequest(2)
        assertEquals(request, SpenditosJson.decodeFromString(UpdateChallengeProgressRequest.serializer(), SpenditosJson.encodeToString(request)))
    }

    @Test
    fun `achievement definitions enriched user results and detail decode`() {
        val definitions = envelope("gamification-achievements", kotlinx.serialization.builtins.ListSerializer(AchievementDefinition.serializer()))
        assertEquals("transaction_count", definitions.single().requirement.type)

        val achievements = envelope("gamification-user-achievements", kotlinx.serialization.builtins.ListSerializer(UserAchievement.serializer()))
        assertEquals(2, achievements.size)
        assertTrue(achievements.first().unlockedAt != null)
        assertFalse(achievements.last().unlockedAt != null)

        val detail = envelope("gamification-achievement-progress", AchievementProgressDetail.serializer())
        assertFalse(detail.isUnlocked)
        assertEquals(2, detail.progress)
    }

    @Test
    fun `consolidated progress summary decodes`() {
        val summary = envelope("gamification-progress-summary", GamificationProgressSummary.serializer())
        assertEquals(4, summary.streak.currentStreak)
        assertEquals("fake-challenge-1", summary.challenge?.id)
        assertEquals("transaction_target_10", summary.goals.single().id)
    }
}
