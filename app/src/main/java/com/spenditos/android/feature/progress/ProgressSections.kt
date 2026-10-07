package com.spenditos.android.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spenditos.android.data.model.GamificationChallenge
import com.spenditos.android.data.model.PointsTransaction
import com.spenditos.android.data.model.UserAchievement
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.components.SectionHeader
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun ProgressLoadingState() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text("Loading progress…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ProgressStreakSection(state: ProgressUiState, locale: Locale) {
    val finance = LocalFinanceColors.current
    val streak = state.streak
    if (streak != null) {
        GlassCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("progress.streak.card"),
        ) {
            Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text("🔥", fontSize = 36.sp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            "${streak.currentStreak} Day Streak",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (streak.currentStreak > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text("Longest: ${streak.longestStreak} days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (streak.streakFreezes > 0) {
                        Row(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .padding(horizontal = Spacing.s, vertical = Spacing.xs),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("❄", style = MaterialTheme.typography.labelMedium, color = Color(0xFF5FA9D9))
                            Text("${streak.streakFreezes}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                StreakCalendarContent(state, locale, insideStreakCard = true)
                if (state.streakFailed) InlineQuietLine("Couldn't load", "progress.streak.error")
            }
        }
    } else {
        when {
            !state.streakLoaded -> QuietLine("Loading…", "progress.streak.loading")
            state.streakFailed -> QuietLine("Couldn't load", "progress.streak.error")
        }
        ProgressCalendarSection(state, locale)
    }
}

@Composable
internal fun ProgressCalendarSection(state: ProgressUiState, locale: Locale) {
    SectionHeader("Streak calendar")
    when {
        state.calendarFailed && state.calendar.isEmpty() -> QuietLine("Couldn't load", "progress.calendar.error")
        !state.calendarLoaded && state.calendar.isEmpty() -> QuietLine("Loading…", "progress.calendar.loading")
        state.calendar.isEmpty() -> QuietLine("No active days this month", "progress.calendar.empty")
        else -> GlassCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("progress.calendar.card"),
        ) {
            StreakCalendarContent(state, locale, insideStreakCard = false)
        }
    }
}

@Composable
private fun StreakCalendarContent(state: ProgressUiState, locale: Locale, insideStreakCard: Boolean) {
    when {
        state.calendarFailed && state.calendar.isEmpty() -> InlineQuietLine("Couldn't load", "progress.calendar.error")
        !state.calendarLoaded && state.calendar.isEmpty() -> InlineQuietLine("Loading…", "progress.calendar.loading")
        state.calendar.isEmpty() -> InlineQuietLine("No active days this month", "progress.calendar.empty")
        else -> {
            Column(
                Modifier.fillMaxWidth().testTag(if (insideStreakCard) "progress.calendar.card" else "progress.calendar.content"),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Row(
                    Modifier.fillMaxWidth().testTag("progress.calendar.grid"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    state.calendar.takeLast(14).forEach { day ->
                        StreakDay(day.date, day.hasEntry && day.isStreakDay, day.freezeUsed, locale, Modifier.weight(1f))
                    }
                }
                if (state.calendarFailed) InlineQuietLine("Couldn't refresh", "progress.calendar.refreshError")
            }
        }
    }
}

@Composable
private fun StreakDay(
    date: LocalDate,
    active: Boolean,
    freezeUsed: Boolean,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val finance = LocalFinanceColors.current
    val fill = when {
        active -> finance.income
        freezeUsed -> Color(0xFF5FA9D9)
        else -> Color.Transparent
    }
    val outline = when {
        date == LocalDate.now() -> MaterialTheme.colorScheme.primary
        active || freezeUsed -> Color.Transparent
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(
            Modifier.size(18.dp).clip(CircleShape).background(fill).border(if (date == LocalDate.now()) 2.dp else 1.dp, outline, CircleShape)
                .testTag("progress.calendar.day.$date"),
            contentAlignment = Alignment.Center,
        ) {
            if (freezeUsed) Text("❄", fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Text(
            date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ProgressPointsSection(state: ProgressUiState) {
    SectionHeader("Level and points")
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
    val pointsToNextLevel = level?.pointsForNextLevel ?: points?.pointsToNextLevel ?: 0
    val brandGradient = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))
    GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("progress.points.card")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(brandGradient), contentAlignment = Alignment.Center) {
                    Text("$levelNumber", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(level?.title ?: "Level $levelNumber", style = MaterialTheme.typography.titleLarge)
                    Text("$total XP Total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                points?.let { PointsBadge("${it.available} XP") }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Box(
                    Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("progress.points.bar"),
                ) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(brandGradient))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$total / $pointsToNextLevel XP", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Next: Level ${levelNumber + 1}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.pointsFailed) InlineQuietLine("Couldn't load points", "progress.points.balanceError")
            if (state.levelFailed) InlineQuietLine("Couldn't load level", "progress.points.levelError")
        }
    }
}

@Composable
private fun PointsBadge(text: String) {
    Text(
        text,
        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = Spacing.s, vertical = Spacing.xs),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
internal fun ProgressChallengeSection(state: ProgressUiState, onComplete: () -> Unit) {
    SectionHeader(if (state.challenge?.period == "weekly") "This week's challenge" else "Today's challenge")
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
    val progress = if (challenge.target > 0) (challenge.current.toFloat() / challenge.target).coerceIn(0f, 1f) else 0f
    val challengeGradient = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))
    GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("progress.challenge.card")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (challenge.period == "weekly") "▦" else "☀", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(challenge.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                Text(challenge.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Box(
                    Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("progress.challenge.bar"),
                ) {
                    val barBrush = if (challenge.completed) Brush.linearGradient(listOf(LocalFinanceColors.current.income, LocalFinanceColors.current.income)) else challengeGradient
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(barBrush))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${challenge.current} / ${challenge.target}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    PointsBadge("${challenge.reward} points")
                }
            }
            if (challenge.completed) {
                Text("Completed", style = MaterialTheme.typography.labelLarge, color = LocalFinanceColors.current.income)
            } else if (challenge.current >= challenge.target) {
                PillButton(
                    if (isCompleting) "Saving…" else "Mark complete",
                    onComplete,
                    modifier = Modifier.fillMaxWidth().testTag("progress.challenge.complete"),
                    enabled = !isCompleting,
                )
            }
            if (actionFailed) InlineQuietLine("Couldn't complete", "progress.challenge.actionError")
            if (challengeFailed) InlineQuietLine("Couldn't refresh", "progress.challenge.refreshError")
        }
    }
}

@Composable
internal fun ProgressAchievementsSection(state: ProgressUiState, onClick: (UserAchievement) -> Unit) {
    SectionHeader("Achievements")
    when {
        !state.achievementsLoaded && state.achievements.isEmpty() && !state.achievementsFailed -> QuietLine("Loading…", "progress.achievements.loading")
        state.achievementsFailed && state.achievements.isEmpty() -> QuietLine("Couldn't load", "progress.achievements.error")
        state.achievements.isEmpty() -> QuietLine("No achievements yet", "progress.achievements.empty")
        else -> {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("progress.achievements.list"),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                state.achievements.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        row.forEach { achievement ->
                            GlassCard(
                                modifier = Modifier.weight(1f).testTag("progress.achievement.${achievement.id}"),
                                onClick = { onClick(achievement) },
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(Spacing.m),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                                ) {
                                    Text(achievement.icon, fontSize = 36.sp)
                                    Text(achievement.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
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
    SectionHeader("Recent points")
    when {
        !state.pointsHistoryLoaded && state.pointsHistory.isEmpty() && !state.pointsHistoryFailed -> QuietLine("Loading…", "progress.history.loading")
        state.pointsHistoryFailed && state.pointsHistory.isEmpty() -> QuietLine("Couldn't load", "progress.history.error")
        state.pointsHistory.isEmpty() -> QuietLine("No points history yet", "progress.history.empty")
        else -> {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("progress.history.list"),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                state.pointsHistory.take(10).forEach { entry -> PointsHistoryRow(entry) }
            }
            if (state.pointsHistoryFailed) QuietLine("Couldn't refresh", "progress.history.refreshError")
        }
    }
}

@Composable
private fun PointsHistoryRow(entry: PointsTransaction) {
    GlassCard(modifier = Modifier.fillMaxWidth().testTag("progress.history.${entry.id}")) {
        Row(Modifier.fillMaxWidth().padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(entry.action.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge)
                Text(entry.timestamp.take(10), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val amount = entry.amount.toInt()
            Text(
                "${if (amount >= 0) "+" else ""}$amount points",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
internal fun ProgressChallengeHistorySection(state: ProgressUiState) {
    SectionHeader("Challenge history")
    when {
        !state.challengeHistoryLoaded && state.challengeHistory.isEmpty() && !state.challengeHistoryFailed -> QuietLine("Loading…", "progress.challengeHistory.loading")
        state.challengeHistoryFailed && state.challengeHistory.isEmpty() -> QuietLine("Couldn't load", "progress.challengeHistory.error")
        state.challengeHistory.isEmpty() -> QuietLine("No challenge history yet", "progress.challengeHistory.empty")
        else -> {
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                state.challengeHistory.take(10).forEach { challenge ->
                    GlassCard(modifier = Modifier.fillMaxWidth().testTag("progress.challengeHistory.${challenge.id}")) {
                        Row(
                            Modifier.padding(Spacing.m),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(challenge.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (challenge.completed) "Completed" else "In progress",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (state.challengeHistoryFailed) QuietLine("Couldn't refresh", "progress.challengeHistory.refreshError")
        }
    }
}

@Composable
internal fun AchievementDetailSheet(state: ProgressUiState, onDismiss: () -> Unit) {
    val achievement = state.selectedAchievement ?: return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(bottom = Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(achievement.icon, fontSize = 64.sp)
            Text(achievement.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(achievement.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            when {
                state.isLoadingAchievementDetail -> QuietLine("Loading…", "progress.achievementDetail.loading")
                state.achievementDetailFailed -> QuietLine("Couldn't load", "progress.achievementDetail.error")
                state.achievementDetail != null -> {
                    val detail = requireNotNull(state.achievementDetail)
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            Text(if (detail.isUnlocked) "Unlocked" else "${detail.progress} / ${achievement.target}", style = MaterialTheme.typography.bodyLarge)
                            if (!detail.isUnlocked && achievement.target > 0) {
                                val progress = (detail.progress.toFloat() / achievement.target).coerceIn(0f, 1f)
                                Box(
                                    Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                                        .testTag("progress.achievementDetail.bar"),
                                ) {
                                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                }
                            }
                        }
                    }
                }
            }
            PillButton("Close", onDismiss, modifier = Modifier.fillMaxWidth().testTag("progress.achievementDetail.close"))
        }
    }
}

@Composable
private fun QuietLine(text: String, tag: String) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag(tag)) {
        Text(
            text,
            Modifier.fillMaxWidth().padding(Spacing.m),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InlineQuietLine(text: String, tag: String) {
    Text(text, Modifier.fillMaxWidth().testTag(tag), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
