package com.spenditos.android.feature.settings.rules

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.data.merchants.MerchantCategories
import com.spenditos.android.data.merchants.MerchantMappingActions
import com.spenditos.android.data.merchants.RuleErrors
import com.spenditos.android.data.merchants.normalizeMerchantKey
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.MerchantMapping
import com.spenditos.android.data.model.RuleAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RulesUiState(
    /** The user's own mappings (`source == "user"`), never AI or system ones. */
    val rules: List<MerchantMapping> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = false,
    val isDeleting: Boolean = false,
    /** Shown above the list and the edit screen (iOS `ErrorBanner`). */
    val error: String? = null,
    /** Shown in the "New Rule" sheet. */
    val addError: String? = null,
    val isAdding: Boolean = false,
) {
    fun rule(id: String): MerchantMapping? = rules.firstOrNull { it.id == id }
}

/**
 * Settings > Rules (iOS `RulesViewModel`): the user's own merchant mappings. AI and system mappings are never shown.
 * Edits apply to the list right away and roll back if the server refuses.
 */
@HiltViewModel
class RulesViewModel @Inject constructor(
    private val mappings: MerchantMappingActions,
    categories: MerchantCategories,
) : ViewModel() {
    private val _state = MutableStateFlow(RulesUiState(isLoading = true))
    val state: StateFlow<RulesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { categories.observe().collect { rows -> _state.update { it.copy(categories = rows) } } }
        load()
    }

    /** Only user rules are Rules, whatever else the server returned. */
    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val rows = userRules(mappings.rules())
                _state.update { it.copy(rules = rows, isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(isLoading = false, error = RuleErrors.message(e)) }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun dismissAddError() = _state.update { it.copy(addError = null) }

    /** [onDone] runs only when the server accepted the delete. */
    fun delete(id: String, onDone: () -> Unit) {
        if (_state.value.isDeleting) return
        _state.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            try {
                mappings.delete(id)
                _state.update { state -> state.copy(isDeleting = false, rules = state.rules.filterNot { it.id == id }) }
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(isDeleting = false, error = RuleErrors.message(e)) }
            }
        }
    }

    /** Change what a rule does. The list updates right away and rolls back if the server refuses. */
    fun update(id: String, action: RuleAction, categoryId: String?) {
        val previous = _state.value.rule(id) ?: return
        replace(previous.copy(action = action.wire, categoryId = categoryId.takeIf { action == RuleAction.CATEGORIZE }))
        viewModelScope.launch {
            try {
                val saved = mappings.update(id, action, categoryId)
                // The server's row wins, unless the rule was deleted meanwhile.
                if (_state.value.rule(id) != null) replace(saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (_state.value.rule(id) != null) replace(previous)
                _state.update { it.copy(error = RuleErrors.message(e)) }
            }
        }
    }

    /** A rule created from the add sheet (an upsert: it may replace one). [onDone] runs when the server accepted it. */
    fun add(merchant: String, skip: Boolean, categoryId: String?, onDone: () -> Unit) {
        val trimmed = merchant.trim()
        if (normalizeMerchantKey(trimmed).isEmpty() || (!skip && categoryId == null) || _state.value.isAdding) return
        _state.update { it.copy(isAdding = true, addError = null) }
        viewModelScope.launch {
            try {
                val saved = mappings.upsertRule(trimmed, if (skip) RuleAction.SKIP else RuleAction.CATEGORIZE, if (skip) null else categoryId)
                didSave(saved)
                _state.update { it.copy(isAdding = false) }
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(isAdding = false, addError = RuleErrors.message(e)) }
            }
        }
    }

    private fun didSave(rule: MerchantMapping) {
        if (!rule.isUserRule) return
        _state.update { state ->
            val index = state.rules.indexOfFirst { it.id == rule.id }
            state.copy(rules = if (index >= 0) state.rules.toMutableList().also { it[index] = rule } else state.rules + rule)
        }
    }

    private fun replace(rule: MerchantMapping) {
        _state.update { state -> state.copy(rules = state.rules.map { if (it.id == rule.id) rule else it }) }
    }

    companion object {
        fun userRules(rows: List<MerchantMapping>): List<MerchantMapping> = rows.filter { it.isUserRule }

        /** "→ Dining", "→ Unknown category" or "Skip — not spending". */
        fun actionText(rule: MerchantMapping, categories: List<Category>): String = when (rule.ruleAction) {
            RuleAction.SKIP -> Strings.get(R.string.settings_skip_not_spending)
            RuleAction.CATEGORIZE -> "→ ${categories.firstOrNull { it.id == rule.categoryId }?.name ?: Strings.get(R.string.settings_unknown_category)}"
        }
    }
}
