package com.spenditos.android.feature.transactions

import android.content.Context
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.network.SpenditosJson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** iOS `DateRangeFilter` raw values are the chip labels. */
@Serializable
enum class DateRangeFilter {
    THIS_MONTH,
    LAST_MONTH,
    CUSTOM,
    ;

    val label: String
        get() = when (this) {
            THIS_MONTH -> "This Month"
            LAST_MONTH -> "Last Month"
            CUSTOM -> "Custom"
        }
}

/** iOS `FilterPreset`. Dates are ISO-8601 local dates; type is [EntryType.name]. */
@Serializable
data class FilterPreset(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val dateRange: DateRangeFilter,
    val customStartDate: String,
    val customEndDate: String,
    val selectedCategories: List<String> = emptyList(),
    val selectedType: String? = null,
    val minAmount: Double? = null,
    val maxAmount: Double? = null,
) {
    fun entryType(): EntryType? = selectedType?.let { runCatching { EntryType.valueOf(it) }.getOrNull() }

    fun startDate(): LocalDate? = runCatching { LocalDate.parse(customStartDate) }.getOrNull()

    fun endDate(): LocalDate? = runCatching { LocalDate.parse(customEndDate) }.getOrNull()
}

/** Saved filter presets for the signed-in user (iOS `UserDefaults` key `filterPresets`). */
@Singleton
class FilterPresetStore @Inject constructor(
    @ApplicationContext context: Context,
    private val session: SessionManager,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): List<FilterPreset> {
        val userId = session.currentUserId ?: return emptyList()
        val raw = prefs.getString(key(userId), null) ?: return emptyList()
        return runCatching { SpenditosJson.decodeFromString<List<FilterPreset>>(raw) }.getOrDefault(emptyList())
    }

    fun save(presets: List<FilterPreset>) {
        val userId = session.currentUserId ?: return
        prefs.edit().putString(key(userId), SpenditosJson.encodeToString(presets)).apply()
    }

    private fun key(userId: String) = "filterPresets.$userId"

    private companion object {
        const val PREFS = "transaction_filter_presets"
    }
}
