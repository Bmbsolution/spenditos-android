package com.spenditos.android.ui.components

import android.os.Build
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.spenditos.android.ui.theme.IosColors

/**
 * iOS wheel picker (`.pickerStyle(.wheel)`) via the platform NumberPicker, themed for light or dark mode so
 * its labels stay readable (the app's XML theme is light-only).
 */
@Composable
fun WheelPicker(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    wrap: Boolean = false,
) {
    val dark = isSystemInDarkTheme()
    val textColor = IosColors.textPrimary.toArgb()
    AndroidView(
        factory = { context ->
            val themed = ContextThemeWrapper(context, if (dark) android.R.style.Theme_Material else android.R.style.Theme_Material_Light)
            NumberPicker(themed).apply {
                minValue = 0
                maxValue = labels.lastIndex
                displayedValues = labels.toTypedArray()
                value = selectedIndex.coerceIn(0, labels.lastIndex)
                wrapSelectorWheel = wrap
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setTextColor(textColor)
                setOnValueChangedListener { _, _, newValue -> onSelect(newValue) }
            }
        },
        update = { picker ->
            val index = selectedIndex.coerceIn(0, labels.lastIndex)
            if (picker.value != index) picker.value = index
        },
        modifier = modifier,
    )
}
