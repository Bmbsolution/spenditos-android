package com.spenditos.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing

/*
 * The iOS look, as reusable pieces (iOS: Shared/Theme, Shared/Extensions/View+Glassmorphism.swift, MainTabView).
 * Screens compose these instead of raw Material parts so the whole app reads like the iOS app.
 */

/** Brand gradient of the add button (iOS gradientOrangeStart/End). */
val BrandGradient = Brush.linearGradient(listOf(Color(0xFFB85F42), Color(0xFF9A4B31)))

/** iOS `glassCard`: white on light, the secondary grouped surface on dark, hairline stroke, soft shadow. */
@Composable
fun glassCardModifier(shape: Shape = RoundedCornerShape(Radius.l)): Modifier {
    val dark = isSystemInDarkTheme()
    val stroke = if (dark) Color(0xFF38383A) else Color.Black.copy(alpha = 0.08f)
    return Modifier
        .shadow(
            elevation = if (dark) 0.dp else 8.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.06f),
            spotColor = Color.Black.copy(alpha = 0.06f),
        )
        .background(if (dark) Color(0xFF1C1C1E) else Color.White, shape)
        .border(1.dp, stroke, shape)
        .clip(shape)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.l),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().then(glassCardModifier(shape))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        content = content,
    )
}

/** Large bold screen title (iOS large navigation title) with optional trailing controls. */
@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(top = 28.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f).semantics { role = Role.Button; contentDescription = text }.testTag("title.$text"),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
}

/** Screen surface: the grouped background, status-bar aware, scrolling content supplied by the caller. */
@Composable
fun IosScreen(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) { content() }
}

/** Gray section label above a card ("Money", "Notifications"). */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = Spacing.m + 4.dp, top = Spacing.l, bottom = Spacing.s),
    )
}

/** Rounded card holding rows with inset dividers (iOS inset-grouped list). */
@Composable
fun GroupedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(modifier.padding(horizontal = Spacing.screen), shape = RoundedCornerShape(Radius.l), content = content)
}

/** Soft circular tint behind a glyph (iOS `IconChip`). */
@Composable
fun IconChip(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 36.dp, shape: Shape = CircleShape) {
    Box(
        modifier.size(size).clip(shape).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f)) }
}

/** One settings-style row: tinted chip, title (+ subtitle), trailing value or chevron, inset divider. */
@Composable
fun IosRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = onClick != null,
    divider: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = Spacing.m, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (icon != null) IconChip(icon, iconTint)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (value != null) Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            trailing?.invoke()
            if (chevron) Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
        if (divider) HorizontalDivider(Modifier.padding(start = if (icon != null) 66.dp else Spacing.m), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** The terracotta add button: 56dp gradient circle with a soft brand-colored shadow (iOS FAB). */
@Composable
fun GradientFab(onClick: () -> Unit, modifier: Modifier = Modifier, glyph: ImageVector, contentDescription: String) {
    Box(
        modifier.size(56.dp)
            .shadow(14.dp, CircleShape, ambientColor = Color(0xFFB85F42).copy(alpha = 0.45f), spotColor = Color(0xFFB85F42).copy(alpha = 0.45f))
            .clip(CircleShape)
            .background(BrandGradient)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(glyph, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(26.dp)) }
}

data class TabItem(val route: String, val label: String, val icon: ImageVector)

/** The floating capsule tab bar of iOS 26: translucent surface, selected tab in a soft pill, brand tint. */
@Composable
fun FloatingTabBar(
    items: List<TabItem>,
    selectedRoute: String?,
    onSelect: (TabItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(32.dp)
    Row(
        modifier.fillMaxWidth().padding(horizontal = 21.dp)
            .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.10f))
            .clip(shape)
            .background(if (dark) Color(0xFF1C1C1E).copy(alpha = 0.985f) else Color(0xFFFBFAFE).copy(alpha = 0.985f))
            .border(BorderStroke(1.dp, if (dark) Color(0xFF38383A) else Color.White.copy(alpha = 0.7f)), shape)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = item.route == selectedRoute
            val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            Column(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (selected) (if (dark) Color(0xFF3A3A3C) else Color(0xFFE8E8ED)) else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(item) }
                    .semantics { this.selected = selected }
                    .padding(vertical = 8.dp)
                    .testTag("tab.${item.route}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                Text(item.label, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** iOS segmented control: gray track, white sliding thumb. */
@Composable
fun IosSegmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, tagPrefix: String = "segment") {
    val dark = isSystemInDarkTheme()
    val track = if (dark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.screen).clip(RoundedCornerShape(Radius.m)).background(track).padding(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(13.dp))
                    .background(if (selected) (if (dark) Color(0xFF636366) else Color.White) else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .semantics { this.selected = selected }
                    .testTag("$tagPrefix.$index"),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium) }
        }
    }
}

/** Filled terracotta pill button (iOS primary action) and its gray secondary sibling. */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true, enabled: Boolean = true) {
    val dark = isSystemInDarkTheme()
    val bg = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        filled -> MaterialTheme.colorScheme.primary
        else -> if (dark) Color(0xFF2C2C2E) else Color(0xFFF1F1F4)
    }
    Box(
        modifier.height(52.dp).clip(RoundedCornerShape(26.dp)).background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = if (filled && enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}
