package com.spenditos.android.data.user

import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.i18n.AppLanguage
import com.spenditos.android.data.model.UserSettingsUpdate
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors the resolved app language (device / per-app locale) into the server `settings.language`
 * when it differs, on sign-in and on app foreground. Best-effort: never throws, never blocks UI.
 */
@Singleton
class LanguageMirror @Inject constructor(
    private val settings: UserSettingsActions,
    private val session: SessionManager,
) {
    @Volatile
    private var lastMirrored: Pair<String, String>? = null

    /** [known] is a settings read the caller just made (sign-in), to skip a second GET. */
    suspend fun sync(known: com.spenditos.android.data.model.UserSettings? = null, language: String = AppLanguage.resolved()) {
        val userId = session.currentUserId ?: return
        if (lastMirrored == userId to language) return
        runCatching {
            val current = known ?: settings.refresh()
            if (current.language != language) {
                settings.updateSettings(
                    UserSettingsUpdate(
                        currency = current.currency,
                        displayName = current.displayName?.let { JsonPrimitive(it) } ?: JsonNull,
                        language = language,
                    ),
                )
            }
            lastMirrored = userId to language
        }
    }
}
