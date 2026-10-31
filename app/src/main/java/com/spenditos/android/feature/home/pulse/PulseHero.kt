package com.spenditos.android.feature.home.pulse

import com.spenditos.android.core.i18n.Strings
import androidx.compose.ui.res.stringResource
import com.spenditos.android.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Shadow as TextShadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spenditos.android.feature.home.CashFlowStatus
import com.spenditos.android.feature.home.monthbalance.MonthBalanceCopy
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@Composable
fun PulseHero(
    status: CashFlowStatus,
    history: List<DayFlow>,
    currency: String,
    locale: Locale,
    onSeeMath: () -> Unit,
    onOpenHealth: () -> Unit,
    onReviewPlan: () -> Unit,
    onAddIncome: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the upcoming expenses behind "Bills left". */
    onOpenBillsLeft: () -> Unit = {},
    /** "Remove" next to the Carried in line (month-start balance); null hides it. */
    onRemoveCarriedIn: (() -> Unit)? = null,
    removeEnabled: Boolean = true,
) {
    val weather = FinancialWeather.from(status.state)
    var expanded by rememberSaveable { mutableStateOf(false) }
    val money = { value: BigDecimal -> Money.format(value.toDouble(), currency, locale) }
    val shape = RoundedCornerShape(28.dp)
    val score = HealthScore.score(status)
    val short = status.leftAfterBills.signum() < 0
    val symbol = when (weather) {
        FinancialWeather.SUNNY -> "sun.max.fill"
        FinancialWeather.CLOUDY -> "cloud.sun.fill"
        FinancialWeather.RAINY -> "cloud.rain.fill"
        FinancialWeather.STORM -> "cloud.bolt.rain.fill"
        FinancialWeather.SETUP -> "sparkles"
    }

    Column(
        modifier = modifier.fillMaxWidth()
            .dropShadow(shape, Shadow(radius = 44.dp, color = weather.glow, offset = DpOffset(0.dp, 14.dp), alpha = 0.45f))
            .clip(shape)
            .background(Brush.linearGradient(weather.gradient))
            .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), center = Offset(900f, 250f), radius = 520f))
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.05f))), shape)
            .clickable(role = Role.Button, onClickLabel = if (expanded) stringResource(R.string.home_show_less) else stringResource(R.string.home_show_more)) { expanded = !expanded }
            .padding(18.dp)
            .testTag("home.statusCard"),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        if (expanded && score != null) {
            HealthBadge(score, onOpenHealth)
        }

        Row(Modifier.fillMaxWidth().testTag("home.status.header"), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))
                        .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(SfSymbols.icon(symbol), contentDescription = null, tint = Color.White, modifier = Modifier.size(21.6.dp))
                }
                Text(
                    weather.shortTitle,
                    style = IosText.callout.w(FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f).width(8.dp))
            Row(
                Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(SfSymbols.icon("hourglass"), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.4.dp))
                Text(stringResource(R.string.home_d_left, status.daysRemaining), style = IosText.caption.w(FontWeight.Bold), color = Color.White)
            }
        }

        if (status.state == CashFlowStatus.State.SETUP) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape))
                    Icon(SfSymbols.icon("banknote"), contentDescription = null, tint = Color.White, modifier = Modifier.size(31.2.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.home_let_s_set_up_your), style = IosText.rounded(22, FontWeight.Bold), color = Color.White)
                    Text(stringResource(R.string.home_two_steps_to_see_what), style = IosText.subheadline, color = Color.White.copy(alpha = 0.9f))
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (short) "SHORT AFTER BILLS" else "LEFT AFTER BILLS",
                        style = IosText.caption.w(FontWeight.Bold),
                        color = Color.White.copy(alpha = 0.85f),
                        letterSpacing = 0.8.sp,
                    )
                    val headline = money(status.leftAfterBills.abs())
                    val shadowBlur = with(LocalDensity.current) { 8.dp.toPx() }
                    val shadowY = with(LocalDensity.current) { 4.dp.toPx() }
                    Text(
                        if (short) "−$headline" else headline,
                        style = IosText.rounded(40, FontWeight.ExtraBold).copy(
                            shadow = TextShadow(Color.Black.copy(alpha = 0.18f), Offset(0f, shadowY), shadowBlur),
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val chip = if (short) {
                        stringResource(R.string.home_trim_a_day, money(status.leftAfterBills.negate().divide(BigDecimal(maxOf(status.daysRemaining, 1)), 2, RoundingMode.HALF_UP)))
                    } else {
                        "≈ ${money(status.affordablePace.setScale(2, RoundingMode.HALF_UP))} a day"
                    }
                    Row(
                        Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            if (short) SfSymbols.icon("scissors") else SfSymbols.icon("sparkles"),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.4.dp),
                        )
                        Text(chip, style = IosText.caption.w(FontWeight.Bold), color = Color.White)
                    }
                }
                LiquidOrb(
                    level = status.incomeLeftShare.toFloat(),
                    tint = weather.glow,
                    label = PulseCopy.percentLabel(status.incomeLeftShare),
                    caption = "of income left",
                    size = 124.dp,
                )
            }
        }

        Text(
            PulseCopy.reason(status, locale, money),
            style = IosText.subheadline.w(FontWeight.Medium),
            color = Color.White.copy(alpha = 0.95f),
        )

        StatsStrip(
            listOf(
                StripEntry("arrow.down.circle.fill", stringResource(R.string.common_income), whole(status.income, currency, locale)),
                StripEntry("cart.fill", stringResource(R.string.common_spent), whole(status.spent, currency, locale)),
                StripEntry("calendar.badge.clock", stringResource(R.string.home_bills_left), whole(status.billsLeft, currency, locale), onOpenBillsLeft, "home.billsLeft"),
            ),
        )

        if (status.carriedIn.signum() > 0) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(
                    stringResource(R.string.home_carried_in_from, money(status.carriedIn), MonthBalanceCopy.previousMonthName(status.today)),
                    style = IosText.subheadline.w(FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.95f),
                    modifier = Modifier.weight(1f).testTag("home.carriedIn"),
                )
                if (onRemoveCarriedIn != null) {
                    // A 48dp target (the text itself is small); applies at once, no confirmation.
                    Box(
                        Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable(enabled = removeEnabled, role = Role.Button, onClick = onRemoveCarriedIn)
                            .semantics { contentDescription = MonthBalanceCopy.REMOVE_DESCRIPTION }
                            .testTag("month-balance-remove"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            MonthBalanceCopy.REMOVE,
                            style = IosText.subheadline.w(FontWeight.Bold),
                            color = Color.White,
                            textDecoration = TextDecoration.Underline,
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onSeeMath).padding(vertical = 4.dp).testTag("home.seeMath"),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SfSymbols.icon("list.bullet.rectangle"), contentDescription = null, tint = Color.White.copy(alpha = 0.95f), modifier = Modifier.size(14.4.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.home_see_the_math), style = IosText.caption.w(FontWeight.Bold), color = Color.White.copy(alpha = 0.95f))
        }

        when (status.state) {
            CashFlowStatus.State.OVER -> HeroButton(stringResource(R.string.home_review_your_plan), SfSymbols.icon("slider.horizontal.3"), onReviewPlan, "home.reviewPlan", weather.gradient.last())
            CashFlowStatus.State.SETUP -> HeroButton(stringResource(R.string.common_add_income), SfSymbols.icon("plus"), onAddIncome, "home.addIncome", weather.gradient.last())
            else -> Unit
        }

        if (expanded) {
            if (status.state != CashFlowStatus.State.SETUP) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                    Icon(SfSymbols.icon("info.circle"), contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(13.2.dp))
                    Text(
                        stringResource(R.string.home_the_orb_is_the_share),
                        style = IosText.caption2,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }
            if (history.any { it.income.signum() != 0 || it.expenses.signum() != 0 }) {
                CashFlowSparkline(history, currency, locale)
            }
        }
    }
}

@Composable
private fun HealthBadge(score: Int, onOpen: () -> Unit) {
    val status = when (score) {
        in 80..100 -> stringResource(R.string.home_excellent)
        in 60..79 -> stringResource(R.string.common_good)
        in 40..59 -> stringResource(R.string.home_needs_attention_2)
        else -> stringResource(R.string.home_critical)
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 10.dp, vertical = 6.dp).testTag("home.healthBadge"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(28.dp)) {
                drawArc(Color.White.copy(alpha = 0.2f), 0f, 360f, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                drawArc(Color.White, -90f, 360f * score / 100f, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
            Text("$score", style = IosText.caption2.w(FontWeight.Bold), color = Color.White)
        }
        Text(stringResource(R.string.home_health_100, score, status), style = IosText.caption.w(FontWeight.SemiBold), color = Color.White.copy(alpha = 0.9f), modifier = Modifier.weight(1f))
        Icon(SfSymbols.icon("chevron.right"), contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.4.dp))
    }
}

private data class StripEntry(
    val icon: String,
    val title: String,
    val value: String,
    val onClick: (() -> Unit)? = null,
    val tag: String? = null,
)

@Composable
private fun StatsStrip(items: List<StripEntry>) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp)).padding(vertical = 10.dp)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, (icon, title, value, onClick, tag) ->
            if (index > 0) Box(Modifier.width(1.dp).height(30.dp).background(Color.White.copy(alpha = 0.2f)))
            Column(
                Modifier.weight(1f)
                    .then(if (onClick != null) Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick) else Modifier)
                    .then(if (tag != null) Modifier.testTag(tag) else Modifier)
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(SfSymbols.icon(icon), contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(13.2.dp))
                    Text(title, color = Color.White.copy(alpha = 0.85f), style = IosText.caption2.w(FontWeight.SemiBold))
                }
                Text(value, color = Color.White, style = IosText.rounded(15, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun HeroButton(label: String, icon: ImageVector, onClick: () -> Unit, tag: String, ink: Color) {
    Box(
        // 40dp visual like iOS; the 48dp touch target overflows it without changing the layout.
        Modifier.height(40.dp).wrapContentSize(Alignment.Center, unbounded = true).requiredHeight(48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.clip(CircleShape).background(Color.White).padding(horizontal = Spacing.m).height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(19.2.dp))
            Text(label, style = IosText.callout.w(FontWeight.SemiBold), color = ink)
        }
    }
}

@Composable
private fun CashFlowSparkline(history: List<DayFlow>, currency: String, locale: Locale) {
    val points = CashFlowHistory.sparkline(history.map { it.net })
    val avg = history.fold(BigDecimal.ZERO) { sum, day -> sum + day.expenses }
        .divide(BigDecimal(maxOf(history.size, 1)), 2, RoundingMode.HALF_UP)
    Column(Modifier.fillMaxWidth().padding(top = 6.dp).testTag("home.sparkline"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.home_7_day_cash_flow), color = Color.White.copy(alpha = 0.8f), style = IosText.caption2.w(FontWeight.Medium))
            Text("${Money.format(avg.toDouble(), currency, locale)} avg/day", color = Color.White.copy(alpha = 0.9f), style = IosText.caption2.w(FontWeight.SemiBold))
        }
        Canvas(Modifier.fillMaxWidth().height(40.dp).clearAndSetSemantics { contentDescription = Strings.get(R.string.home_net_cash_flow_for_the) }) {
            if (points.isEmpty()) return@Canvas
            val xy = points.map { Offset(it.first * size.width, it.second * size.height) }
            val fill = Path().apply {
                moveTo(xy.first().x, xy.first().y)
                xy.drop(1).forEach { lineTo(it.x, it.y) }
                lineTo(size.width, size.height); lineTo(0f, size.height); close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.02f))))
            val line = Path().apply { moveTo(xy.first().x, xy.first().y); xy.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(line, Color.White, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            xy.forEachIndexed { index, p -> drawCircle(Color.White, radius = if (index == xy.lastIndex) 2.5.dp.toPx() else 1.5.dp.toPx(), center = p) }
        }
    }
}

private fun whole(value: BigDecimal, currency: String, locale: Locale): String {
    val format = NumberFormat.getCurrencyInstance(locale)
    runCatching { format.currency = Currency.getInstance(currency) }
    format.maximumFractionDigits = 0
    format.minimumFractionDigits = 0
    return format.format(value)
}
