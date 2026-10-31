package com.spenditos.android.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import kotlinx.coroutines.launch
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SpenditosApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var sessionLifecycle: SessionLifecycle
    @Inject lateinit var languageMirror: com.spenditos.android.data.user.LanguageMirror
    @Inject lateinit var appLock: com.spenditos.android.core.lock.AppLockController
    @Inject lateinit var reconciliation: com.spenditos.android.core.sync.FinanceReconciliationCoordinator
    @Inject lateinit var realtime: com.spenditos.android.data.realtime.RealtimeManager
    @Inject lateinit var proAccess: com.spenditos.android.data.entitlement.ProAccessRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // A per-app language change arrives here (the process is not restarted): keep the default locale
        // that formatters read in step with it.
        java.util.Locale.setDefault(com.spenditos.android.core.i18n.AppLanguage.currentLocale(this))
    }

    override fun onCreate() {
        super.onCreate()
        com.spenditos.android.core.i18n.Strings.install(this)
        java.util.Locale.setDefault(com.spenditos.android.core.i18n.AppLanguage.currentLocale(this))
        sessionLifecycle.start()
        // Foreground refresh (throttled) of server Premium: welcome expiry, admin grants.
        proAccess.start()
        appLock.start()
        // Before realtime.start() so the socket's reconnect signal is observed; runs with no
        // WebSocket configuration too (foreground reconciliation is independent of the socket).
        reconciliation.start()
        realtime.start()
        com.spenditos.android.feature.widget.WidgetRefreshWorker.schedule(this)
        // Leaving the foreground is when the cache is freshest: push it to the widget.
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                // Mirror the app language (per-app locale may have changed) into the server settings.
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { languageMirror.sync() }
            }

            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                    runCatching { com.spenditos.android.feature.widget.WidgetUpdater.refresh(this@SpenditosApplication) }
                }
            }
        })
    }
}
