package com.spenditos.android.data.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.spenditos.android.R
import com.spenditos.android.core.i18n.Strings

/**
 * The single notification channel Spenditos posts to. Creating a channel again with the same id only
 * updates its name and description, so [register] runs at every app start and whenever the app locale
 * changes: the name in system settings always follows the language the app is shown in.
 */
object PushNotificationChannel {
    const val ID = "general"

    fun register(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(ID, Strings.get(R.string.data_push_reminders_and_progress), NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = Strings.get(R.string.data_push_channel_description)
        manager.createNotificationChannel(channel)
    }
}
