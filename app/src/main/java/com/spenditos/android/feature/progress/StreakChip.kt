package com.spenditos.android.feature.progress

import com.spenditos.android.core.i18n.Strings
import androidx.compose.ui.res.stringResource
import com.spenditos.android.R
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.w
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
                _streak.value = repository.getStreak().currentStreak
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
    val days = streak?.takeIf { it > 0 } ?: return
    Surface(
        modifier = modifier.testTag("progress.streakChip"),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.progress_d, days), style = IosText.caption)
            Text("streak", style = IosText.caption2)
        }
    }
}

/** iOS Home streak tile: flame, days, and a calm hint. Hidden until the streak could be loaded. */
@Composable
fun StreakTile(modifier: Modifier = Modifier) {
    val viewModel: StreakChipViewModel = hiltViewModel()
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    val days = streak ?: return
    com.spenditos.android.ui.components.GlassCard(modifier.testTag("progress.streakTile")) {
        // iOS HomeBento.swift StreakTile: caption-semibold title with a flame, then flame 28 + count 34 heavy rounded + "days".
        androidx.compose.foundation.layout.Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(SfSymbols.icon("flame.fill"), contentDescription = null, tint = Color(0xFFF06A3B), modifier = Modifier.size(14.4.dp))
                Text(stringResource(R.string.progress_streak), style = IosText.caption.w(FontWeight.SemiBold), color = IosColors.textSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(SfSymbols.icon("flame.fill"), contentDescription = null, tint = Color(0xFFFF7A3D), modifier = Modifier.size(34.dp))
                Text("$days", style = IosText.rounded(34, FontWeight.ExtraBold), color = IosColors.textPrimary)
                Text(Strings.plural(R.plurals.streak_unit_days, days), style = IosText.subheadline.w(FontWeight.Medium), color = IosColors.textSecondary)
            }
            Text(stringResource(R.string.progress_keep_logging_to_build_it), style = IosText.caption.w(FontWeight.SemiBold), color = IosColors.textSecondary)
        }
    }
}
