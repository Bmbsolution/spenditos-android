package com.spenditos.android.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.IosText

@Composable
fun ProgressScreen(onBack: () -> Unit) {
    val viewModel: ProgressViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(viewModel) { viewModel.load() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(Modifier.fillMaxWidth().statusBarsPadding().height(44.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(44.dp).testTag("progress.back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Progress", style = IosText.headline, modifier = Modifier.align(Alignment.Center))
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding).testTag("progress.refresh"),
        ) {
            if (!state.hasLoaded) {
                Box(Modifier.fillMaxSize().testTag("progress.list"), contentAlignment = Alignment.Center) {
                    ProgressLoadingState()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("progress.list"),
                    contentPadding = PaddingValues(horizontal = Spacing.screen, top = Spacing.m, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.l),
                ) {
                    item { ProgressStreakSection(state, locale) }
                    item { ProgressPointsSection(state) }
                    item { ProgressChallengeSection(state, viewModel::completeChallenge) }
                    item { ProgressAchievementsSection(state, viewModel::openAchievement) }
                    item { ProgressPointsHistorySection(state, viewModel::refresh) }
                    item { ProgressChallengeHistorySection(state) }
                }
            }
        }
    }

    AchievementDetailSheet(state, viewModel::dismissAchievement)
}
