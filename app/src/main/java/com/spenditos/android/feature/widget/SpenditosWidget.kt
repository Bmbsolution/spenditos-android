package com.spenditos.android.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import android.content.Intent
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.spenditos.android.app.MainActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

class SpenditosWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SpenditosWidgetEntryPoint::class.java,
        )
        val snapshot = try {
            entryPoint.widgetStateProvider().snapshot()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            WidgetSnapshot.placeholder()
        }
        provideContent { WidgetContent(snapshot) }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SpenditosWidgetEntryPoint {
    fun widgetStateProvider(): WidgetStateProvider
}

@Composable
private fun WidgetContent(snapshot: WidgetSnapshot) {
    GlanceTheme {
        val foreground = GlanceTheme.colors.onSurface
        val secondary = GlanceTheme.colors.onSurfaceVariant
        val background = GlanceTheme.colors.surface
        val open = actionStartActivity(Intent(LocalContext.current, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        if (snapshot.isPlaceholder) {
            Column(
                modifier = GlanceModifier.fillMaxSize()
                    .background(background)
                    .clickable(open)
                    .padding(16.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            ) {
                Text("Open Spenditos", style = TextStyle(color = foreground, fontSize = 14.sp))
            }
        } else {
            Column(
                modifier = GlanceModifier.fillMaxSize()
                    .background(background)
                    .clickable(open)
                    .padding(8.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.Start,
            ) {
                Text(snapshot.stateLabel.orEmpty(), style = TextStyle(color = foreground, fontSize = 12.sp))
                Text("Left after bills", style = TextStyle(color = secondary, fontSize = 10.sp))
                Text(snapshot.formattedLeftAfterBills().orEmpty(), style = TextStyle(color = foreground, fontSize = 16.sp))
                snapshot.daysLeft?.let { days ->
                    Text(
                        "$days ${if (days == 1) "day" else "days"} left",
                        style = TextStyle(color = secondary, fontSize = 10.sp),
                    )
                }
            }
        }
    }
}
