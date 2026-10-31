package com.spenditos.android.core.i18n

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.util.ServiceLoader

/** Resolves a string resource to text in the current app language. */
interface StringResolver {
    fun string(@StringRes id: Int, args: Array<out Any?>): String
    fun plural(@PluralsRes id: Int, quantity: Int, args: Array<out Any?>): String
}

/** Production resolver: the application context's resources follow the (per-app) locale. */
private class ContextStringResolver(private val context: Context) : StringResolver {
    override fun string(id: Int, args: Array<out Any?>): String =
        if (args.isEmpty()) context.resources.getString(id) else context.resources.getString(id, *args)

    override fun plural(id: Int, quantity: Int, args: Array<out Any?>): String =
        context.resources.getQuantityString(id, quantity, *args)
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
