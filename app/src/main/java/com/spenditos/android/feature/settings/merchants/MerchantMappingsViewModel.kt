package com.spenditos.android.feature.settings.merchants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.merchants.MerchantCategories
import com.spenditos.android.data.merchants.MerchantMappingActions
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.CreateMerchantMappingRequest
import com.spenditos.android.data.model.MerchantMapping
import com.spenditos.android.data.model.UpdateMerchantMappingRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.regex.PatternSyntaxException
import javax.inject.Inject

data class MerchantMappingsUiState(
    val items: List<MerchantMapping> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSaving: Boolean = false,
    val loadError: String? = null,
    val actionError: String? = null,
)

/** Validation of the editor, mirroring the server schema (`min(1)` strings) plus a regex sanity check. */
object MerchantMappingRules {
    fun validate(rawPattern: String, cleanName: String, isRegex: Boolean): String? = when {
        rawPattern.trim().isEmpty() -> "Enter the text as it appears on your statement."
        cleanName.trim().isEmpty() -> "Enter the name you want to see."
        isRegex && runCatching { Regex(rawPattern.trim()) }.exceptionOrNull() is PatternSyntaxException -> "That pattern isn't valid."
        else -> null
    }
}

@HiltViewModel
class MerchantMappingsViewModel @Inject constructor(
    private val mappings: MerchantMappingActions,
    categories: MerchantCategories,
) : ViewModel() {
    private val _state = MutableStateFlow(MerchantMappingsUiState())
    val state: StateFlow<MerchantMappingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { categories.observe().collect { rows -> _state.update { it.copy(categories = rows) } } }
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = initial && it.items.isEmpty(), isRefreshing = !initial, loadError = null) }
            try {
                val rows = mappings.list()
                _state.update { it.copy(items = rows, isLoading = false, isRefreshing = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(isLoading = false, isRefreshing = false, loadError = message(e, "Couldn't load your names.")) }
            }
        }
    }

    /** [id] null creates. Returns through [onDone] only when the server accepted it. */
    fun save(id: String?, rawPattern: String, cleanName: String, categoryId: String?, isRegex: Boolean, onDone: () -> Unit) {
        MerchantMappingRules.validate(rawPattern, cleanName, isRegex)?.let { error ->
            _state.update { it.copy(actionError = error) }
            return
        }
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true, actionError = null) }
        viewModelScope.launch {
            try {
                val raw = rawPattern.trim()
                val clean = cleanName.trim()
                if (id == null) mappings.create(CreateMerchantMappingRequest(raw, clean, categoryId, isRegex))
                else mappings.update(id, UpdateMerchantMappingRequest(raw, clean, categoryId, isRegex))
                _state.update { it.copy(isSaving = false) }
                load(initial = false)
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(isSaving = false, actionError = message(e, "Couldn't save that name.")) }
            }
        }
    }

    fun delete(id: String, onDone: () -> Unit) {
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true, actionError = null) }
        viewModelScope.launch {
            try {
                mappings.delete(id)
                _state.update { state -> state.copy(isSaving = false, items = state.items.filterNot { it.id == id }) }
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(isSaving = false, actionError = message(e, "Couldn't delete that name.")) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(actionError = null) }

    private fun message(error: Throwable, fallback: String): String = when (error) {
        is ApiError.Network -> "You're offline. Check your connection and try again."
        is ApiError.Server -> when {
            error.status == 400 || error.status == 409 || error.status == 422 ->
                error.serverMessage.takeIf { it.isNotBlank() && it != "Unknown error" } ?: fallback
            error.status == 404 -> "That name no longer exists. Refresh and try again."
            error.status >= 500 -> "Something went wrong on our side. Try again."
            else -> fallback
        }
        is ApiError.Unauthorized -> "Your session expired. Sign in and try again."
        else -> fallback
    }
}
