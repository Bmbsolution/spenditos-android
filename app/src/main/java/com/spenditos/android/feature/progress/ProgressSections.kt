package com.spenditos.android.feature.progress

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.model.GamificationChallenge
import com.spenditos.android.data.model.PointsTransaction
import com.spenditos.android.data.model.UserAchievement
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.coroutines.delay

private val XpGold = Color(0xFFFFD700)
private val FreezeBlue = Color(0xFF1565C0)

@Composable
internal fun ProgressLoadingState() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        CircularProgressIndicator(color = IosColors.spendOrange, modifier = Modifier.size(24.dp))
        Text("Loading progress...", style = IosText.body, color = IosColors.textTertiary)
    }
}

@Composable
internal fun ProgressStreakSection(state: ProgressUiState, locale: java.util.Locale) {
    val streak = state.streak
    if (streak != null) {
        val fireScale by rememberInfiniteTransition(label = "streak flame").animateFloat(
            initialValue = 1f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "fire scale",
        )
        GlassCard(modifier = Modifier.fillMaxWidth().testTag("progress.streak.card")) {
            Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔥", style = IosText.system(36), modifier = Modifier.graphicsLayer(scaleX = fireScale, scaleY = fireScale))
                    Column(
                        Modifier.weight(1f).padding(start = Spacing.s),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            "${streak.currentStreak} Day Streak",
                            style = IosText.title2.w(FontWeight.Bold),
                            color = streakColor(streak.currentStreak),
                        )
                        Text("Longest: ${streak.longestStreak} days", style = IosText.subheadline, color = IosColors.textSecondary)
                    }
                    FreezeBadge(streak.streakFreezes)
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
private fun FreezeBadge(freezes: Int) {
    Row(
        Modifier.then(subtleGlassModifier(RoundedCornerShape(Radius.s))).padding(horizontal = Spacing.s, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.AcUnit, contentDescription = null, tint = FreezeBlue, modifier = Modifier.size(14.4.dp))
        Text(freezes.toString(), style = IosText.subheadline.w(FontWeight.Bold), color = IosColors.textPrimary)
    }
}

@Composable
internal fun ProgressCalendarSection(state: ProgressUiState, locale: java.util.Locale) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text("Streak calendar", style = IosText.headline, color = IosColors.textSecondary)
        when {
            state.calendarFailed && state.calendar.isEmpty() -> QuietLine("Couldn't load", "progress.calendar.error")
            !state.calendarLoaded && state.calendar.isEmpty() -> QuietLine("Loading…", "progress.calendar.loading")
            state.calendar.isEmpty() -> QuietLine("No active days this month", "progress.calendar.empty")
            else -> GlassCard(modifier = Modifier.fillMaxWidth().testTag("progress.calendar.card")) {
                StreakCalendarContent(state, locale, insideStreakCard = false)
            }
        }
    }
}

@Composable
private fun StreakCalendarContent(state: ProgressUiState, locale: java.util.Locale, insideStreakCard: Boolean) {
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
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    state.calendar.takeLast(14).forEach { day ->
                        StreakDay(day.date, day.hasEntry && day.isStreakDay, day.freezeUsed, Modifier, locale)
                    }
                }
                if (state.calendarFailed) InlineQuietLine("Couldn't refresh", "progress.calendar.refreshError")
            }
        }
    }
}

@Composable
private fun StreakDay(
    date: java.time.LocalDate,
    active: Boolean,
    freezeUsed: Boolean,
    modifier: Modifier,
    locale: java.util.Locale,
) {
    val today = date == java.time.LocalDate.now()
    val fill = when {
        active -> IosColors.income
        freezeUsed -> FreezeBlue
        else -> Color.Transparent
    }
    val outline = when {
        today -> IosColors.spendOrange
        active || freezeUsed -> Color.Transparent
        else -> Color.Black.copy(alpha = 0.08f)
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(
            Modifier.size(18.dp).clip(CircleShape).background(fill)
                .border(if (today) 2.dp else if (active || freezeUsed) 0.dp else 1.dp, outline, CircleShape)
                .testTag("progress.calendar.day.$date"),
            contentAlignment = Alignment.Center,
        ) {
            if (freezeUsed) Icon(Icons.Outlined.AcUnit, contentDescription = null, tint = Color.White, modifier = Modifier.size(9.6.dp))
        }
        val weekday = listOf("S", "M", "T", "W", "T", "F", "S")[date.dayOfWeek.value % 7]
        Text(weekday, style = IosText.system(9, FontWeight.Medium), color = IosColors.textTertiary)
    }
}

@Composable
internal fun ProgressPointsSection(state: ProgressUiState) {
    var showBenefits by remember { mutableStateOf(false) }
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
    val (color1, color2) = levelGradient(levelNumber)
    val brandGradient = Brush.horizontalGradient(listOf(IosColors.spendOrange, XpGold))
    var levelVisible by remember(levelNumber) { mutableStateOf(false) }
    LaunchedEffect(levelNumber) {
        delay(200)
        levelVisible = true
    }
    val animatedProgress by animateFloatAsState(
        if (levelVisible) progress else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 70f),
        label = "level progress",
    )
    GlassCard(modifier = Modifier.fillMaxWidth().testTag("progress.points.card")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(color1, color2))))
                    Box(Modifier.size(48.dp).border(2.dp, color2.copy(alpha = 0.4f), CircleShape))
                    Text(levelNumber.toString(), style = IosText.title3.w(FontWeight.SemiBold), color = Color.White)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(level?.title ?: "Level $levelNumber", style = IosText.title3.w(FontWeight.SemiBold), color = IosColors.textPrimary)
                    Text("$total XP Total", style = IosText.subheadline, color = XpGold)
                }
                points?.let { PointsBadge(it.available) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Box(
                    Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.06f))
                        .testTag("progress.points.bar"),
                ) {
                    Box(Modifier.fillMaxWidth(animatedProgress).fillMaxHeight().clip(CircleShape).background(brandGradient))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$total / $pointsToNextLevel XP", style = IosText.subheadline, color = IosColors.textTertiary)
                    Text("Next: Level ${levelNumber + 1}", style = IosText.subheadline, color = IosColors.textTertiary)
                }
            }
            if (!level?.unlocks.isNullOrEmpty()) {
                Column(Modifier.padding(top = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { showBenefits = !showBenefits },
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(SfSymbols.icon("star.fill"), contentDescription = null, tint = IosColors.spendGold, modifier = Modifier.size(14.4.dp))
                        Text("Level Benefits", style = IosText.subheadline.w(FontWeight.Bold), color = IosColors.textSecondary)
                        Spacer(Modifier.weight(1f))
                        Icon(
                            if (showBenefits) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = IosColors.textTertiary,
                            modifier = Modifier.size(14.4.dp),
                        )
                    }
                    if (showBenefits) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = Spacing.s, top = Spacing.xs),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.LockOpen, contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(14.4.dp))
                            Text("Unlocked: ${level!!.unlocks.joinToString(", ")}", style = IosText.subheadline, color = IosColors.textSecondary)
                        }
                    }
                }
            }
            if (state.pointsFailed) InlineQuietLine("Couldn't load points", "progress.points.balanceError")
            if (state.levelFailed) InlineQuietLine("Couldn't load level", "progress.points.levelError")
        }
    }
}

@Composable
private fun PointsBadge(points: Int) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.background(Color(0x40FF9500), shape).clip(shape)
            .border(1.dp, Color(0x66FF9500), shape).padding(horizontal = Spacing.s, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SfSymbols.icon("star.fill"), contentDescription = null, tint = Color(0xFFFFCC00), modifier = Modifier.size(13.2.dp))
        Text(points.toString(), style = IosText.caption.w(FontWeight.Bold), color = IosColors.textPrimary, maxLines = 1, softWrap = false)
    }
}

@Composable
internal fun ProgressChallengeSection(state: ProgressUiState, onComplete: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text("Today's Challenge", style = IosText.headline, color = IosColors.textSecondary)
        val challenge = state.challenge
        if (challenge == null) {
            QuietLine(if (state.challengeFailed) "Couldn't load" else "Loading…", if (state.challengeFailed) "progress.challenge.error" else "progress.challenge.loading")
        } else {
            ChallengeCard(challenge, state.isCompletingChallenge, state.challengeActionFailed, state.challengeFailed, onComplete)
        }
    }
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
    val completed = challenge.completed
    val timeRemaining = rememberChallengeTime(challenge.expiresAt)
    var challengeVisible by remember(challenge.id) { mutableStateOf(false) }
    LaunchedEffect(challenge.id) {
        delay(300)
        challengeVisible = true
    }
    val animatedProgress by animateFloatAsState(
        if (challengeVisible) progress else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 70f),
        label = "challenge progress",
    )
    val challengeGradient = Brush.horizontalGradient(listOf(IosColors.spendOrange, XpGold))
    GlassCard(modifier = Modifier.fillMaxWidth().testTag("progress.challenge.card")) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            SfSymbols.icon(if (challenge.type == "daily") "sun.max.fill" else "calendar"),
                            contentDescription = null,
                            tint = IosColors.spendOrange,
                            modifier = Modifier.size(20.4.dp),
                        )
                        Text(challenge.title, style = IosText.title3.w(FontWeight.SemiBold), color = IosColors.textPrimary)
                    }
                    Text(challenge.description, style = IosText.callout, color = IosColors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Box(
                        Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.06f))
                            .testTag("progress.challenge.bar"),
                    ) {
                        val barBrush = if (completed) Brush.horizontalGradient(listOf(IosColors.income, IosColors.income)) else challengeGradient
                        Box(Modifier.fillMaxWidth(animatedProgress).fillMaxHeight().clip(CircleShape).background(barBrush))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${challenge.current} / ${challenge.target}", style = IosText.subheadline.w(FontWeight.Bold), color = IosColors.textSecondary)
                        Spacer(Modifier.weight(1f))
                        PointsBadge(challenge.reward)
                    }
                }
                if (!completed && challenge.current >= challenge.target) {
                    CompleteChallengeButton(isCompleting, onComplete)
                } else if (!completed && timeRemaining != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AccessTime, contentDescription = null, tint = IosColors.textTertiary, modifier = Modifier.size(14.4.dp))
                        Text(timeRemaining, style = IosText.subheadline, color = IosColors.textTertiary)
                    }
                }
                if (actionFailed) InlineQuietLine("Couldn't complete", "progress.challenge.actionError")
                if (challengeFailed) InlineQuietLine("Couldn't refresh", "progress.challenge.refreshError")
            }
            if (completed) {
                Column(
                    Modifier.matchParentSize().clip(RoundedCornerShape(Radius.l)).background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterVertically),
                ) {
                    Icon(SfSymbols.icon("checkmark.circle.fill"), contentDescription = null, tint = IosColors.income, modifier = Modifier.size(48.dp))
                    Text("Completed!", style = IosText.headline, color = IosColors.income)
                }
            }
        }
    }
}

@Composable
private fun CompleteChallengeButton(isCompleting: Boolean, onComplete: () -> Unit) {
    val shape = RoundedCornerShape(Radius.m)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.horizontalGradient(listOf(IosColors.spendOrange, IosColors.spendGold)))
            .clickable(enabled = !isCompleting, role = Role.Button, onClick = onComplete)
            .padding(vertical = Spacing.s)
            .testTag("progress.challenge.complete"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            if (isCompleting) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            else Icon(SfSymbols.icon("checkmark.circle.fill"), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.4.dp))
            Text("Complete Challenge", style = IosText.headline, color = Color.White)
        }
    }
}

@Composable
internal fun ProgressAchievementsSection(state: ProgressUiState, onClick: (UserAchievement) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text("Achievements", style = IosText.headline, color = IosColors.textSecondary)
        when {
            !state.achievementsLoaded && state.achievements.isEmpty() && !state.achievementsFailed -> QuietLine("Loading…", "progress.achievements.loading")
            state.achievementsFailed && state.achievements.isEmpty() -> QuietLine("Couldn't load", "progress.achievements.error")
            state.achievements.isEmpty() -> QuietLine("No achievements yet", "progress.achievements.empty")
            else -> {
                Column(Modifier.fillMaxWidth().testTag("progress.achievements.list"), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    state.achievements.sortedWith(
                        compareByDescending<UserAchievement> { it.unlockedAt != null }
                            .thenByDescending { if (it.target > 0) it.progress.toFloat() / it.target else 0f },
                    ).chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                            row.forEach { achievement ->
                                AchievementCard(achievement, Modifier.weight(1f), onClick)
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                if (state.achievementsFailed) QuietLine("Couldn't refresh", "progress.achievements.refreshError")
            }
        }
    }
}

@Composable
private fun AchievementCard(achievement: UserAchievement, modifier: Modifier, onClick: (UserAchievement) -> Unit) {
    val unlocked = achievement.unlockedAt != null
    val inProgress = !unlocked && achievement.progress > 0
    val shape = RoundedCornerShape(Radius.m)
    val glow by rememberInfiniteTransition(label = "achievement glow").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow opacity",
    )
    val borderColor = if (unlocked) XpGold.copy(alpha = glow) else Color.Transparent
    Column(
        modifier.fillMaxWidth().dropShadow(shape, Shadow(radius = 16.dp, color = XpGold, offset = DpOffset.Zero, alpha = if (unlocked) 0.15f else 0f))
            .then(subtleGlassModifier(shape)).border(1.5.dp, borderColor, shape)
            .clickable(role = Role.Button, onClick = { onClick(achievement) })
            .padding(Spacing.m).testTag("progress.achievement.${achievement.id}")
            .graphicsLayer(alpha = if (unlocked || inProgress) 1f else 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Text(achievement.icon, style = IosText.system(36), modifier = Modifier.align(Alignment.Center).graphicsLayer(alpha = if (unlocked) 1f else 0.4f))
            if (!unlocked && !inProgress) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = IosColors.textTertiary, modifier = Modifier.size(19.2.dp).align(Alignment.BottomEnd).offset(16.dp, 16.dp))
            }
        }
        Text(
            achievement.name,
            style = IosText.subheadline.w(FontWeight.Bold),
            color = IosColors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(32.dp),
        )
        when {
            unlocked -> PointsBadge(achievement.target)
            inProgress -> AchievementProgressBar(achievement)
            else -> Text("Locked", style = IosText.caption, color = IosColors.textTertiary)
        }
    }
}

@Composable
private fun AchievementProgressBar(achievement: UserAchievement) {
    val ratio = if (achievement.target > 0) (achievement.progress.toFloat() / achievement.target).coerceIn(0f, 1f) else 0f
    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.06f))) {
        Box(Modifier.fillMaxWidth(ratio).fillMaxHeight().clip(CircleShape).background(IosColors.spendOrange))
    }
}

@Composable
internal fun ProgressPointsHistorySection(state: ProgressUiState, onRetry: () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text("Points History", style = IosText.headline, color = IosColors.textSecondary)
        when {
            !state.pointsHistoryLoaded && state.pointsHistory.isEmpty() && !state.pointsHistoryFailed -> {
                Column(
                    Modifier.fillMaxWidth().testTag("progress.history.loading"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    CircularProgressIndicator(color = IosColors.spendOrange, modifier = Modifier.size(24.dp))
                    Text("Loading history...", style = IosText.body, color = IosColors.textTertiary)
                }
            }
            state.pointsHistoryFailed && state.pointsHistory.isEmpty() -> {
                Column(
                    Modifier.fillMaxWidth().testTag("progress.history.error"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    Text("Couldn't load", style = IosText.body, color = IosColors.textTertiary)
                    TextButton(onClick = onRetry) { Text("Retry", style = IosText.headline, color = IosColors.spendOrange) }
                }
            }
            state.pointsHistory.isEmpty() -> {
                Column(
                    Modifier.fillMaxWidth().testTag("progress.history.empty"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    Icon(SfSymbols.icon("clock.arrow.circlepath"), contentDescription = null, tint = IosColors.textTertiary, modifier = Modifier.size(48.dp))
                    Text("No points history yet", style = IosText.body, color = IosColors.textTertiary)
                    Text("Earn XP to see your history here!", style = IosText.subheadline, color = IosColors.textTertiary)
                }
            }
            else -> {
                Column(Modifier.fillMaxWidth().testTag("progress.history.list")) {
                    state.pointsHistory.take(10).forEach { entry -> PointsHistoryRow(entry) }
                }
                if (state.pointsHistoryFailed) QuietLine("Couldn't refresh", "progress.history.refreshError")
            }
        }
    }
}

@Composable
private fun PointsHistoryRow(entry: PointsTransaction) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.action.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                style = IosText.body,
                color = IosColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val amount = entry.amount.toInt()
            Text("+${amount} XP", style = IosText.rounded(15), color = XpGold, maxLines = 1, softWrap = false)
        }
        Text(entry.timestamp.take(10), style = IosText.subheadline, color = IosColors.textTertiary)
    }
}

@Composable
internal fun ProgressChallengeHistorySection(state: ProgressUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text("Challenge history", style = IosText.headline, color = IosColors.textSecondary)
        when {
            !state.challengeHistoryLoaded && state.challengeHistory.isEmpty() && !state.challengeHistoryFailed -> QuietLine("Loading…", "progress.challengeHistory.loading")
            state.challengeHistoryFailed && state.challengeHistory.isEmpty() -> QuietLine("Couldn't load", "progress.challengeHistory.error")
            state.challengeHistory.isEmpty() -> QuietLine("No challenge history yet", "progress.challengeHistory.empty")
            else -> {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    state.challengeHistory.take(10).forEach { challenge ->
                        GlassCard(modifier = Modifier.fillMaxWidth().testTag("progress.challengeHistory.${challenge.id}")) {
                            Row(
                                Modifier.padding(Spacing.m),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(challenge.title, modifier = Modifier.weight(1f), style = IosText.body)
                                Text(if (challenge.completed) "Completed" else "In progress", style = IosText.subheadline, color = IosColors.textSecondary)
                            }
                        }
                    }
                }
                if (state.challengeHistoryFailed) QuietLine("Couldn't refresh", "progress.challengeHistory.refreshError")
            }
        }
    }
}

@Composable
internal fun AchievementDetailSheet(state: ProgressUiState, onDismiss: () -> Unit) {
    val achievement = state.selectedAchievement ?: return
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(bottom = Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(achievement.icon, style = IosText.system(64))
            Text(achievement.name, style = IosText.title2.w(FontWeight.Bold), textAlign = TextAlign.Center)
            Text(achievement.description, style = IosText.body, color = IosColors.textSecondary, textAlign = TextAlign.Center)
            when {
                state.isLoadingAchievementDetail -> QuietLine("Loading…", "progress.achievementDetail.loading")
                state.achievementDetailFailed -> QuietLine("Couldn't load", "progress.achievementDetail.error")
                state.achievementDetail != null -> {
                    val detail = requireNotNull(state.achievementDetail)
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            Text(if (detail.isUnlocked) "Unlocked" else "${detail.progress} / ${achievement.target}", style = IosText.body)
                            if (!detail.isUnlocked && achievement.target > 0) {
                                val progress = (detail.progress.toFloat() / achievement.target).coerceIn(0f, 1f)
                                Box(
                                    Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.06f))
                                        .testTag("progress.achievementDetail.bar"),
                                ) {
                                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(IosColors.spendOrange))
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
    GlassCard(modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Text(text, Modifier.fillMaxWidth().padding(Spacing.m), style = IosText.callout, color = IosColors.textSecondary)
    }
}

@Composable
private fun InlineQuietLine(text: String, tag: String) {
    Text(text, Modifier.fillMaxWidth().testTag(tag), style = IosText.subheadline, color = IosColors.textSecondary)
}

@Composable
private fun rememberChallengeTime(expiresAt: String?): String? {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expiresAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000)
        }
    }
    val expires = try {
        expiresAt?.let { Instant.parse(it).toEpochMilli() }
    } catch (_: DateTimeParseException) {
        null
    } ?: return null
    val interval = (expires - now) / 1000
    if (interval <= 0) return "Expired"
    val hours = interval / 3600
    val minutes = interval % 3600 / 60
    val days = hours / 24
    return when {
        days > 30 -> "No time limit"
        days >= 2 -> "$days days remaining"
        hours > 0 -> "${hours}h ${minutes}m remaining"
        else -> "${minutes}m remaining"
    }
}

private fun streakColor(days: Int): Color = when (days) {
    in 0..2 -> Color(0xFF94A3B8)
    in 3..6 -> Color(0xFFEAB308)
    in 7..13 -> Color(0xFFF59E0B)
    in 14..29 -> Color(0xFFF97316)
    else -> Color(0xFF8B5CF6)
}

private fun levelGradient(level: Int): Pair<Color, Color> = when (level) {
    1 -> Color(0xFF64748B) to Color(0xFF94A3B8)
    2 -> Color(0xFF10B981) to Color(0xFF34D399)
    3 -> Color(0xFF3B82F6) to Color(0xFF60A5FA)
    4 -> Color(0xFF8B5CF6) to Color(0xFFA78BFA)
    5 -> Color(0xFFEC4899) to Color(0xFFF472B6)
    in 6..9 -> Color(0xFFF59E0B) to Color(0xFFFCD34D)
    in 10..14 -> Color(0xFFF97316) to Color(0xFFFB923C)
    in 15..19 -> Color(0xFFEF4444) to Color(0xFFF87171)
    in 20..24 -> Color(0xFFF43F5E) to Color(0xFFFB7185)
    else -> Color(0xFF06B6D4) to Color(0xFF22D3EE)
}
