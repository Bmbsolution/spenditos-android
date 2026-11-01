package com.spenditos.android.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.spenditos.android.ui.components.dismissKeyboardOnTapOrScroll
import com.spenditos.android.ui.theme.SpenditosTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private companion object {
        /** The locale the process last drew the UI in; a change means view-model text is stale. */
        @Volatile var lastLocaleTag: String? = null
    }

    @javax.inject.Inject lateinit var deepLinks: com.spenditos.android.data.push.DeepLinkHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // View models survive the recreation a language change causes and hold text built in the old
        // language, so drop them (state in SavedStateHandle / rememberSaveable is kept) and let every
        // screen rebuild its view model in the new language.
        val locale = com.spenditos.android.core.i18n.AppLanguage.currentLocale(this)
        java.util.Locale.setDefault(locale)
        val tag = locale.toLanguageTag()
        if (lastLocaleTag != null && lastLocaleTag != tag) viewModelStore.clear()
        lastLocaleTag = tag
        // A recreation (rotation, process death) still delivers the original intent; offering it
        // again would re-fire the add sheet / camera. Later intents arrive through onNewIntent.
        if (savedInstanceState == null) {
            deepLinks.handle(intent)
            WidgetDeepLinks.offer(intent)
        }
        enableEdgeToEdge()
        // Tap-outside and scroll close the keyboard for everything in this window. Sheets and
        // dialogs are their own windows and install the same modifiers in their content.
        setContent { SpenditosTheme { Box(Modifier.fillMaxSize().dismissKeyboardOnTapOrScroll()) { AppRoot() } } }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        deepLinks.handle(intent)
        WidgetDeepLinks.offer(intent)
    }
}

/**
 * Pending spenditos:// link from a widget pill ("add-expense" or "scan-receipt"), offered in
 * onCreate/onNewIntent and consumed once by MainScaffold — the same single-pending-link shape
 * [com.spenditos.android.data.push.DeepLinkHandler] uses for notification links.
 */
internal object WidgetDeepLinks {
    const val SCHEME = "spenditos"
    const val ADD_EXPENSE = "add-expense"
    const val SCAN_RECEIPT = "scan-receipt"

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun offer(intent: android.content.Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != SCHEME) return
        when (data.host) {
            ADD_EXPENSE, SCAN_RECEIPT -> _pending.value = data.host
        }
    }

    fun consume() {
        _pending.value = null
    }

    /** Sign-out: a link tapped before signing out must not fire after the next sign-in. */
    fun clear() {
        _pending.value = null
    }
}
