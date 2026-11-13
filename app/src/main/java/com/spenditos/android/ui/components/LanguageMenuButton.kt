package com.spenditos.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.spenditos.android.R
import com.spenditos.android.core.i18n.AppLanguage
import com.spenditos.android.core.i18n.LanguageChoice
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText

/**
 * Compact language picker for the screens that come before Settings: sign in, create account and the
 * first-run setup. Same three choices (Device / English / Français) and the same [AppLanguage.apply]
 * as Settings > Language, which switches the UI at once. Signed out there is no account to save to;
 * sign-in and setup then send the resolved language themselves.
 */
@Composable
fun LanguageMenuButton(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val choice = AppLanguage.currentChoice()
    Box(modifier) {
        Row(
            Modifier
                .padding(end = 8.dp, top = 8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.06f))
                .clickable(role = Role.Button) { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("language.menu"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(SfSymbols.icon("globe"), contentDescription = null, tint = IosColors.textPrimary, modifier = Modifier.size(16.dp))
            Text(
                choice.nativeName ?: stringResource(R.string.settings_language_device),
                style = IosText.caption,
                color = IosColors.textPrimary,
                maxLines = 1,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LanguageChoice.entries.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            (item.nativeName ?: stringResource(R.string.settings_language_device)) + if (item == choice) "  ✓" else "",
                            style = IosText.body,
                        )
                    },
                    onClick = {
                        expanded = false
                        if (item != choice) AppLanguage.apply(item)
                    },
                    modifier = Modifier.testTag("language.menu.${item.name.lowercase()}"),
                )
            }
        }
    }
}
