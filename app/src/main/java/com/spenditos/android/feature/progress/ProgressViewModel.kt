package com.spenditos.android.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.data.gamification.GamificationDataSource
import com.spenditos.android.data.model.AchievementProgressDetail
import com.spenditos.android.data.model.GamificationChallenge
import com.spenditos.android.data.model.GamificationLevelInfo
import com.spenditos.android.data.model.GamificationPointsBalance
import com.spenditos.android.data.model.PointsTransaction
import com.spenditos.android.data.model.StreakCalendarDay
import com.spenditos.android.data.model.StreakInfo
import com.spenditos.android.data.model.UserAchievement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.joinAll
import javax.inject.Inject

data class ProgressUiState(
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val streak: StreakInfo? = null,
    val streakLoaded: Boolean = false,
    val streakFailed: Boolean = false,
    val calendar: List<StreakCalendarDay> = emptyList(),
    val calendarLoaded: Boolean = false,
    val calendarFailed: Boolean = false,
    val points: GamificationPointsBalance? = null,
    val pointsLoaded: Boolean = false,
    val pointsFailed: Boolean = false,
    val level: GamificationLevelInfo? = null,
    val levelLoaded: Boolean = false,
    val levelFailed: Boolean = false,
    val challenge: GamificationChallenge? = null,
    val challengeLoaded: Boolean = false,
    val challengeFailed: Boolean = false,
    val challengeHistory: List<GamificationChallenge> = emptyList(),
    val challengeHistoryLoaded: Boolean = false,
    val challengeHistoryFailed: Boolean = false,
    val achievements: List<UserAchievement> = emptyList(),
    val achievementsLoaded: Boolean = false,
    val achievementsFailed: Boolean = false,
    val pointsHistory: List<PointsTransaction> = emptyList(),
    val pointsHistoryLoaded: Boolean = false,
    val pointsHistoryFailed: Boolean = false,
    val isCompletingChallenge: Boolean = false,
    val challengeActionFailed: Boolean = false,
    val isUsingStreakFreeze: Boolean = false,
    val streakFreezeUsed: Boolean = false,
    val streakFreezeFailed: Boolean = false,
    val selectedAchievement: UserAchievement? = null,
    val achievementDetail: AchievementProgressDetail? = null,
    val isLoadingAchievementDetail: Boolean = false,
    val achievementDetailFailed: Boolean = false,
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val repository: GamificationDataSource,
) : ViewModel() {
    private val _state = MutableStateFlow(ProgressUiState())
    val state: StateFlow<ProgressUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    fun load() = startLoad(refresh = false)

    fun refresh() = startLoad(refresh = true)

    private fun startLoad(refresh: Boolean) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = !it.hasLoaded,
                    isRefreshing = refresh,
                    streakFailed = false,
                    calendarFailed = false,
                    pointsFailed = false,
                    levelFailed = false,
                    challengeFailed = false,
                    challengeHistoryFailed = false,
                    achievementsFailed = false,
                    pointsHistoryFailed = false,
                )
            }
            supervisorScope {
                listOf(
                    launch { loadStreak() },
                    launch { loadCalendar() },
                    launch { loadPoints() },
                    launch { loadLevel() },
                    launch { loadChallenge() },
                    launch { loadAchievements() },
                    launch { loadPointsHistory() },
                    launch { loadChallengeHistory() },
                ).joinAll()
            }
            _state.update { it.copy(hasLoaded = true, isLoading = false, isRefreshing = false) }
        }
    }

    private suspend fun loadStreak() = section(
        success = { value -> _state.update { it.copy(streak = value, streakLoaded = true, streakFailed = false) } },
        failure = { _state.update { it.copy(streakLoaded = true, streakFailed = true) } },
    ) { repository.getStreak() }

    private suspend fun loadCalendar() = section(
        success = { value -> _state.update { it.copy(calendar = value, calendarLoaded = true, calendarFailed = false) } },
        failure = { _state.update { it.copy(calendarLoaded = true, calendarFailed = true) } },
    ) { repository.getStreakCalendar() }

    private suspend fun loadPoints() = section(
        success = { value -> _state.update { it.copy(points = value, pointsLoaded = true, pointsFailed = false) } },
        failure = { _state.update { it.copy(pointsLoaded = true, pointsFailed = true) } },
    ) { repository.getPoints() }

    private suspend fun loadLevel() = section(
        success = { value -> _state.update { it.copy(level = value, levelLoaded = true, levelFailed = false) } },
        failure = { _state.update { it.copy(levelLoaded = true, levelFailed = true) } },
    ) { repository.getLevel() }

    private suspend fun loadChallenge() = section(
        success = { value -> _state.update { it.copy(challenge = value, challengeLoaded = true, challengeFailed = false, challengeActionFailed = false) } },
        failure = { _state.update { it.copy(challengeLoaded = true, challengeFailed = true) } },
    ) { repository.getTodayChallenge() }

    private suspend fun loadAchievements() = section(
        success = { value -> _state.update { it.copy(achievements = value, achievementsLoaded = true, achievementsFailed = false) } },
        failure = { _state.update { it.copy(achievementsLoaded = true, achievementsFailed = true) } },
    ) { repository.getUserAchievements() }

    private suspend fun loadPointsHistory() = section(
        success = { value -> _state.update { it.copy(pointsHistory = value, pointsHistoryLoaded = true, pointsHistoryFailed = false) } },
        failure = { _state.update { it.copy(pointsHistoryLoaded = true, pointsHistoryFailed = true) } },
    ) { repository.getPointsHistory(10) }

    private suspend fun loadChallengeHistory() = section(
        success = { value -> _state.update { it.copy(challengeHistory = value, challengeHistoryLoaded = true, challengeHistoryFailed = false) } },
        failure = { _state.update { it.copy(challengeHistoryLoaded = true, challengeHistoryFailed = true) } },
    ) { repository.getChallengeHistory(10) }

    private suspend fun <T> section(
        success: (T) -> Unit,
        failure: () -> Unit,
        request: suspend () -> T,
    ) {
        try {
            success(request())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failure()
        }
    }

    fun useStreakFreeze() {
        val streak = state.value.streak ?: return
        if (streak.streakFreezes <= 0 || state.value.isUsingStreakFreeze || state.value.streakFreezeUsed) return
        // Mark busy before launching so a double tap cannot spend two freezes.
        _state.update { it.copy(isUsingStreakFreeze = true, streakFreezeFailed = false) }
        viewModelScope.launch {
            try {
                val updated = repository.useStreakFreeze()
                _state.update { it.copy(streak = updated, streakFreezeUsed = true, streakFailed = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Leave the current streak in place and say it did not work (iOS shows the error).
                _state.update { it.copy(streakFreezeFailed = true) }
            } finally {
                _state.update { it.copy(isUsingStreakFreeze = false) }
            }
        }
    }

    fun completeChallenge() {
        val challenge = state.value.challenge ?: return
        if (challenge.completed || challenge.current < challenge.target || state.value.isCompletingChallenge) return
        viewModelScope.launch {
            _state.update { it.copy(isCompletingChallenge = true, challengeActionFailed = false) }
            try {
                val completed = repository.completeChallenge(challenge.id)
                _state.update { it.copy(challenge = completed, challengeFailed = false) }
                loadPoints()
                loadLevel()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(challengeActionFailed = true) }
            } finally {
                _state.update { it.copy(isCompletingChallenge = false) }
            }
        }
    }

    fun openAchievement(achievement: UserAchievement) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    selectedAchievement = achievement,
                    achievementDetail = null,
                    isLoadingAchievementDetail = true,
                    achievementDetailFailed = false,
                )
            }
            try {
                val detail = repository.getAchievementProgress(achievement.id)
                _state.update { it.copy(achievementDetail = detail) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(achievementDetailFailed = true) }
            } finally {
                _state.update { it.copy(isLoadingAchievementDetail = false) }
            }
        }
    }

    fun dismissAchievement() {
        _state.update { it.copy(selectedAchievement = null, achievementDetail = null, achievementDetailFailed = false) }
    }
}
