package com.spenditos.android.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.theme.Spacing

@Composable
fun ProgressScreen(onBack: () -> Unit) {
    val viewModel: ProgressViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(viewModel) { viewModel.load() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Progress") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("progress.back")) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding).testTag("progress.refresh"),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("progress.list"),
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                if (!state.hasLoaded) {
                    item { Text("Loading progress…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    item { ProgressStreakSection(state) }
                    item { ProgressCalendarSection(state, locale) }
                    item { ProgressPointsSection(state) }
                    item { ProgressChallengeSection(state, viewModel::completeChallenge) }
                    item { ProgressAchievementsSection(state, viewModel::openAchievement) }
                    item { ProgressPointsHistorySection(state) }
                    item { ProgressChallengeHistorySection(state) }
                }
            }
        }
    }

    AchievementDetailSheet(state, viewModel::dismissAchievement)
}
