package com.bmbsolution.spenditos.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmbsolution.spenditos.data.model.DashboardSummary
import com.bmbsolution.spenditos.data.repository.GamificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val gamificationRepository: GamificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Load gamification data
            gamificationRepository.getLevel().onSuccess { level ->
                _uiState.update { state ->
                    state.copy(
                        level = level.level,
                        levelTitle = level.title,
                        currentPoints = level.currentPoints,
                        pointsToNextLevel = level.pointsToNextLevel
                    )
                }
            }

            gamificationRepository.getStreak().onSuccess { streak ->
                _uiState.update { state ->
                    state.copy(currentStreak = streak.currentStreak)
                }
            }

            // Load dashboard data
            gamificationRepository.getDashboard().onSuccess { dashboard ->
                _uiState.update { state ->
                    state.copy(
                        totalIncome = dashboard.summary.totalIncome,
                        totalExpenses = dashboard.summary.totalExpenses,
                        netAmount = dashboard.summary.netAmount,
                        spendingByCategory = dashboard.spendingByCategory.map { category ->
                            CategorySpendingUi(
                                id = category.categoryId,
                                name = category.categoryName,
                                amount = category.amount,
                                percentage = category.percentage
                            )
                        },
                        isLoading = false
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLoading = false, error = it.error) }
            }
        }
    }
}

data class DashboardUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    // Gamification
    val level: Int = 1,
    val levelTitle: String = "Beginner",
    val currentPoints: Int = 0,
    val pointsToNextLevel: Int = 100,
    val currentStreak: Int = 0,
    // Summary
    val totalIncome: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val netAmount: Double = 0.0,
    // Categories
    val spendingByCategory: List<CategorySpendingUi> = emptyList()
)
