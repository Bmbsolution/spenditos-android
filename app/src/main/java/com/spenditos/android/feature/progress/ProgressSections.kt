package com.spenditos.android.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.model.GamificationChallenge
import com.spenditos.android.data.model.PointsTransaction
import com.spenditos.android.data.model.StreakCalendarDay
import com.spenditos.android.data.model.UserAchievement
import com.spenditos.android.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun ProgressStreakSection(
    state: ProgressUiState,
) {
    SectionHeading("Streak")
    when {
        !state.streakLoaded && state.streak == null -> QuietLine("Loading…", "progress.streak.loading")
        state.streak == null && state.streakFailed -> QuietLine("Couldn't load", "progress.streak.error")
        state.streak != null -> {
            val streak = requireNotNull(state.streak)
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("progress.streak.card"),
            ) {
                Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${streak.currentStreak} day streak", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Longest: ${streak.longestStreak} days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (streak.streakFreezes > 0) {
                            Text(
                                if (streak.streakFreezes == 1) "1 freeze" else "${streak.streakFreezes} freezes",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (state.streakFailed) QuietLine("Couldn't load", "progress.streak.error")
                }
            }
        }
    }
}

@Composable
internal fun ProgressCalendarSection(state: ProgressUiState, locale: Locale) {
    SectionHeading("Streak calendar")
    when {
        state.calendarFailed && state.calendar.isEmpty() -> QuietLine("Couldn't load", "progress.calendar.error")
        !state.calendarLoaded && state.calendar.isEmpty() -> QuietLine("Loading…", "progress.calendar.loading")
        state.calendar.isEmpty() -> QuietLine("No active days this month", "progress.calendar.empty")
        else -> Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("progress.calendar.card")) {
            Column {
                StreakMonthGrid(state.calendar, locale)
                if (state.calendarFailed) QuietLine("Couldn't refresh", "progress.calendar.refreshError")
            }
        }
    }
}

@Composable
private fun StreakMonthGrid(days: List<StreakCalendarDay>, locale: Locale) {
    val month = LocalDate.now().withDayOfMonth(1)
    val title = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    val byDate = days.associateBy { it.date }
    val blanks = month.dayOfWeek.value - 1
    val cells = List(blanks) { null } + (1..month.lengthOfMonth()).map { month.withDayOfMonth(it) }
    Column(
        Modifier.fillMaxWidth().padding(Spacing.m).testTag("progress.calendar.grid"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        cells.chunked(7).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f).height(28.dp))
                    } else {
                        val day = byDate[date]
                        val active = day?.isStreakDay == true || day?.hasEntry == true || day?.freezeUsed == true
                        Box(
                            Modifier.weight(1f).height(28.dp)
                                .clip(CircleShape)
                                .background(if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .testTag("progress.calendar.day.$date"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                repeat(7 - row.size) { Spacer(Modifier.weight(1f).height(28.dp)) }
            }
        }
    }
}

@Composable
internal fun ProgressPointsSection(state: ProgressUiState) {
    SectionHeading("Level and points")
    val points = state.points
    val level = state.level
    if (points == null && level == null) {
        when {
            state.pointsFailed || state.levelFailed -> QuietLine("Couldn't load", "progress.points.error")
            else -> QuietLine("Loading…", "progress.points.loading")
        }
        return
    }
    val levelNumber = level?.level ?: points?.level ?: 1
    val total = level?.totalPoints ?: points?.totalEarned ?: 0
    val progress = ((level?.progressPercentage ?: points?.progressToNextLevel ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    val progressPercent = (progress * 100).toInt()
    val pointsToNextLevel = level?.pointsForNextLevel ?: points?.pointsToNextLevel ?: 0
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("progress.points.card")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Level $levelNumber", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        level?.title?.let { "$it · $total points earned" } ?: "$total points earned",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                points?.let { Text("${it.available} points available", style = MaterialTheme.typography.labelLarge) }
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().testTag("progress.points.bar"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$progressPercent% complete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$pointsToNextLevel points to next level", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.pointsFailed) QuietLine("Couldn't load points", "progress.points.balanceError")
            if (state.levelFailed) QuietLine("Couldn't load level", "progress.points.levelError")
        }
    }
}

@Composable
internal fun ProgressChallengeSection(
    state: ProgressUiState,
    onComplete: () -> Unit,
) {
    SectionHeading(if (state.challenge?.period == "weekly") "This week's challenge" else "Today's challenge")
    val challenge = state.challenge
    if (challenge == null) {
        QuietLine(if (state.challengeFailed) "Couldn't load" else "Loading…", if (state.challengeFailed) "progress.challenge.error" else "progress.challenge.loading")
        return
    }
    ChallengeCard(challenge, state.isCompletingChallenge, state.challengeActionFailed, state.challengeFailed, onComplete)
}

@Composable
private fun ChallengeCard(
    challenge: GamificationChallenge,
    isCompleting: Boolean,
    actionFailed: Boolean,
    challengeFailed: Boolean,
    onComplete: () -> Unit,
) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("progress.challenge.card")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(challenge.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(challenge.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { if (challenge.target > 0) (challenge.current.toFloat() / challenge.target).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().testTag("progress.challenge.bar"),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${challenge.current} / ${challenge.target}", style = MaterialTheme.typography.labelMedium)
                Text("${challenge.reward} points", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (challenge.completed) {
                Text("Completed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (challenge.current >= challenge.target) {
                Button(onClick = onComplete, enabled = !isCompleting, modifier = Modifier.fillMaxWidth().testTag("progress.challenge.complete")) {
                    Text(if (isCompleting) "Saving…" else "Mark complete")
                }
            }
            if (actionFailed) QuietLine("Couldn't complete", "progress.challenge.actionError")
            if (challengeFailed) QuietLine("Couldn't refresh", "progress.challenge.refreshError")
        }
    }
}

@Composable
internal fun ProgressAchievementsSection(
    state: ProgressUiState,
    onClick: (UserAchievement) -> Unit,
) {
    SectionHeading("Achievements")
    when {
        !state.achievementsLoaded && state.achievements.isEmpty() && !state.achievementsFailed -> QuietLine("Loading…", "progress.achievements.loading")
        state.achievementsFailed && state.achievements.isEmpty() -> QuietLine("Couldn't load", "progress.achievements.error")
        state.achievements.isEmpty() -> QuietLine("No achievements yet", "progress.achievements.empty")
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.testTag("progress.achievements.list")) {
                state.achievements.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        row.forEach { achievement ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).clickable(role = Role.Button) { onClick(achievement) }
                                    .testTag("progress.achievement.${achievement.id}"),
                            ) {
                                Column(Modifier.padding(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Text(achievement.icon, style = MaterialTheme.typography.titleMedium)
                                    Text(achievement.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                                    Text(
                                        if (achievement.unlockedAt != null) "Unlocked" else "${achievement.progress} / ${achievement.target}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (state.achievementsFailed) QuietLine("Couldn't refresh", "progress.achievements.refreshError")
        }
    }
}

@Composable
internal fun ProgressPointsHistorySection(state: ProgressUiState) {
    SectionHeading("Recent points")
    when {
        !state.pointsHistoryLoaded && state.pointsHistory.isEmpty() && !state.pointsHistoryFailed -> QuietLine("Loading…", "progress.history.loading")
        state.pointsHistoryFailed && state.pointsHistory.isEmpty() -> QuietLine("Couldn't load", "progress.history.error")
        state.pointsHistory.isEmpty() -> QuietLine("No points history yet", "progress.history.empty")
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.testTag("progress.history.list")) {
                state.pointsHistory.take(10).forEach { entry -> PointsHistoryRow(entry) }
            }
            if (state.pointsHistoryFailed) QuietLine("Couldn't refresh", "progress.history.refreshError")
        }
    }
}

@Composable
private fun PointsHistoryRow(entry: PointsTransaction) {
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("progress.history.${entry.id}")) {
        Row(Modifier.fillMaxWidth().padding(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.action.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
                Text(entry.timestamp.take(10), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val amount = entry.amount.toInt()
            Text("${if (amount >= 0) "+" else ""}$amount points", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun ProgressChallengeHistorySection(state: ProgressUiState) {
    SectionHeading("Challenge history")
    when {
        !state.challengeHistoryLoaded && state.challengeHistory.isEmpty() && !state.challengeHistoryFailed -> QuietLine("Loading…", "progress.challengeHistory.loading")
        state.challengeHistoryFailed && state.challengeHistory.isEmpty() -> QuietLine("Couldn't load", "progress.challengeHistory.error")
        state.challengeHistory.isEmpty() -> QuietLine("No challenge history yet", "progress.challengeHistory.empty")
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                state.challengeHistory.take(10).forEach { challenge ->
                    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("progress.challengeHistory.${challenge.id}")) {
                        Row(Modifier.padding(Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                            Text(challenge.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(if (challenge.completed) "Completed" else "In progress", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (state.challengeHistoryFailed) QuietLine("Couldn't refresh", "progress.challengeHistory.refreshError")
        }
    }
}

@Composable
internal fun AchievementDetailSheet(
    state: ProgressUiState,
    onDismiss: () -> Unit,
) {
    val achievement = state.selectedAchievement ?: return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(achievement.icon, style = MaterialTheme.typography.headlineMedium)
            Text(achievement.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(achievement.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                state.isLoadingAchievementDetail -> QuietLine("Loading…", "progress.achievementDetail.loading")
                state.achievementDetailFailed -> QuietLine("Couldn't load", "progress.achievementDetail.error")
                state.achievementDetail != null -> {
                    val detail = requireNotNull(state.achievementDetail)
                    Text(if (detail.isUnlocked) "Unlocked" else "${detail.progress} / ${achievement.target}", style = MaterialTheme.typography.bodyMedium)
                    if (!detail.isUnlocked && achievement.target > 0) {
                        LinearProgressIndicator(
                            progress = { (detail.progress.toFloat() / achievement.target).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().testTag("progress.achievementDetail.bar"),
                        )
                    }
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().testTag("progress.achievementDetail.close")) { Text("Close") }
        }
    }
}

@Composable
private fun SectionHeading(label: String) {
    Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun QuietLine(text: String, tag: String) {
    Text(text, Modifier.fillMaxWidth().testTag(tag), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
