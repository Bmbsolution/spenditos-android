package com.spenditos.android.core.i18n

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.util.ServiceLoader

/** Resolves a string resource to text in the current app language. */
interface StringResolver {
    fun string(@StringRes id: Int, args: Array<out Any?>): String
    fun plural(@PluralsRes id: Int, quantity: Int, args: Array<out Any?>): String
}

/**
 * Production resolver. The resources are rebuilt whenever the app locale changes (per-app language,
 * `adb shell cmd locale set-app-locales`), so nothing is cached in the language it was first read in.
 */
private class ContextStringResolver(private val context: Context) : StringResolver {
    private var cached: Pair<String, Resources>? = null

    private fun resources(): Resources {
        val locale = AppLanguage.currentLocale(context)
        val tag = locale.toLanguageTag()
        cached?.takeIf { it.first == tag }?.let { return it.second }
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config).resources.also { cached = tag to it }
    }

    @Synchronized
    override fun string(id: Int, args: Array<out Any?>): String {
        val res = resources()
        return if (args.isEmpty()) res.getString(id) else res.getString(id, *args)
    }

    @Synchronized
    override fun plural(id: Int, quantity: Int, args: Array<out Any?>): String =
        resources().getQuantityString(id, quantity, *args)
}

/**
 * Localized copy for code that is not Compose (view models, formatters, notifications, the widget).
 * No Context is held by view models: they call [get] / [plural] with a resource id when they build
 * a message. Composables use `stringResource` / `pluralStringResource` instead.
 *
 * Installed once from `SpenditosApplication.onCreate`; unit tests get a resolver that reads
 * `values/strings.xml` from a `ServiceLoader` entry in the test source set.
 */
object Strings {
    @Volatile
    private var resolver: StringResolver? = null

    fun install(context: Context) {
        resolver = ContextStringResolver(context.applicationContext)
    }

    fun install(custom: StringResolver) {
        resolver = custom
    }

    private fun current(): StringResolver =
        resolver ?: ServiceLoader.load(StringResolver::class.java).iterator().let { if (it.hasNext()) it.next() else null }
            ?.also { resolver = it }
            ?: error("Strings.install(context) was not called")

    fun get(@StringRes id: Int, vararg args: Any?): String = current().string(id, args)

    fun plural(@PluralsRes id: Int, quantity: Int, vararg args: Any?): String =
        current().plural(id, quantity, if (args.isEmpty()) arrayOf(quantity) else args)
}
