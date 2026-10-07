package com.spenditos.android.feature.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

object WidgetUpdater {
    suspend fun refresh(context: Context) {
        SpenditosWidget().updateAll(context.applicationContext)
    }
}
