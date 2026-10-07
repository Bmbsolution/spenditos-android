package com.spenditos.android.app

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.spenditos.android.ui.theme.SpenditosTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @javax.inject.Inject lateinit var deepLinks: com.spenditos.android.data.push.DeepLinkHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLinks.handle(intent)
        enableEdgeToEdge()
        setContent { SpenditosTheme { AppRoot() } }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        deepLinks.handle(intent)
    }
}
