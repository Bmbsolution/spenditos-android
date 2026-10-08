package com.spenditos.android.app

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.spenditos.android.ui.theme.SpenditosTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @javax.inject.Inject lateinit var deepLinks: com.spenditos.android.data.push.DeepLinkHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLinks.handle(intent)
        WidgetDeepLinks.offer(intent)
        enableEdgeToEdge()
        setContent { SpenditosTheme { AppRoot() } }
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
}
