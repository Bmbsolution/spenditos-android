package com.spenditos.android.data.push

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Where a tapped notification leads (iOS `DeepLink` and `NotificationService.handleNotificationAction`). */
enum class DeepLink {
    ADD_TRANSACTION,
    PROGRESS,
    ;

    companion object {
        const val EXTRA_TYPE = "type"

        /** iOS mapping: streak/add reminders -> add sheet, challenge and achievement events -> Progress. */
        fun fromType(type: String?): DeepLink? = when (type) {
            "streak_reminder", "add_transaction" -> ADD_TRANSACTION
            "challenge", "challenge_complete", "achievement", "level_up" -> PROGRESS
            else -> null
        }
    }
}

/** A single pending deep link, consumed once by the main screen. */
@Singleton
class DeepLinkHandler @Inject constructor() {
    private val _pending = MutableStateFlow<DeepLink?>(null)
    val pending: StateFlow<DeepLink?> = _pending.asStateFlow()

    fun handle(intent: Intent?) {
        DeepLink.fromType(intent?.getStringExtra(DeepLink.EXTRA_TYPE))?.let { _pending.value = it }
    }

    fun consume(link: DeepLink) {
        if (_pending.value == link) _pending.value = null
    }
}
