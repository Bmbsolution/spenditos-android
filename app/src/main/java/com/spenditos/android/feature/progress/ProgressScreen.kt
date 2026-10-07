package com.spenditos.android.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Progress", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("progress.back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
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
                    contentPadding = PaddingValues(top = Spacing.m, bottom = Spacing.l),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    item { ProgressStreakSection(state, locale) }
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
