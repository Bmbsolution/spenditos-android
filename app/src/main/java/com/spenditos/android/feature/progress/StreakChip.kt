package com.spenditos.android.feature.progress

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.data.gamification.GamificationDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StreakChipViewModel @Inject constructor(
    repository: GamificationDataSource,
) : ViewModel() {
    private val _streak = MutableStateFlow<Int?>(null)
    val streak: StateFlow<Int?> = _streak.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _streak.value = repository.getStreak().currentStreak.takeIf { it > 0 }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _streak.value = null
            }
        }
    }
}

@Composable
fun StreakChip(modifier: Modifier = Modifier) {
    val viewModel: StreakChipViewModel = hiltViewModel()
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    val days = streak ?: return
    Surface(
        modifier = modifier.testTag("progress.streakChip"),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("${days}d", style = MaterialTheme.typography.labelLarge)
            Text("streak", style = MaterialTheme.typography.labelMedium)
        }
    }
}
