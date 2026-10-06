package com.spenditos.android.core.config

import com.spenditos.android.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Build-time environment, mirroring iOS `AppRuntime`. Values come from
 * `config/<env>.properties` through BuildConfig; `API_HOST` is validated as
 * host-only at build time and turned into `https://<host>` here.
 */
@Singleton
class AppEnvironment @Inject constructor() {
    val apiBaseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/') + "/"
    val webSocketUrl: String = BuildConfig.WEB_SOCKET_URL
    val stage: String = BuildConfig.DEPLOYMENT_STAGE
    val displayName: String = BuildConfig.APP_ENV_NAME
    val clientConfigVersion: String = BuildConfig.CLIENT_CONFIG_VERSION
    val showBadge: Boolean = BuildConfig.SHOW_ENV_BADGE
    val isHarness: Boolean = BuildConfig.IS_HARNESS
    val harnessToken: String = BuildConfig.HARNESS_AUTH_TOKEN
    val isDebug: Boolean = BuildConfig.DEBUG

    /** Namespaces local storage per environment, like the iOS Keychain service suffix. */
    val storageNamespace: String = stage.lowercase().replace(Regex("[^a-z0-9]"), "_")
}
