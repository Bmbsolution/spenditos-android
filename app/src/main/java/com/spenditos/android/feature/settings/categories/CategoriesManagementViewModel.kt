package com.spenditos.android.feature.settings.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.categories.CategoryRepository
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.CategoryCreateRequest
import com.spenditos.android.data.model.CategoryUpdateRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

data class CategoryDraft(
    val name: String,
    val color: String,
    val icon: String?,
    val isIncome: Boolean,
)

data class CategoriesManagementUiState(
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val isOffline: Boolean = false,
)

sealed interface CategoriesManagementEvent {
    data object Saved : CategoriesManagementEvent
    data object Deleted : CategoriesManagementEvent
}

@HiltViewModel
@OptIn(FlowPreview::class)
class CategoriesManagementViewModel @Inject constructor(
    private val categories: CategoryRepository,
    invalidations: FinanceInvalidationBus,
) : ViewModel() {
    private val screen = MutableStateFlow(CategoriesManagementUiState())
    private val _events = Channel<CategoriesManagementEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<CategoriesManagementUiState> = combine(screen, categories.observe()) { ui, rows ->
        ui.copy(categories = rows)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesManagementUiState())

    init {
        refresh()
        invalidations.events
            .filter { Aggregate.CATEGORY in it && !screen.value.isSaving }
            .debounce(250)
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        screen.update { it.copy(isLoading = true, error = null, isOffline = false) }
        viewModelScope.launch {
            val result = runCatching { categories.refresh() }
            result.fold(
                onSuccess = { screen.update { it.copy(isLoading = false) } },
                onFailure = { error ->
                    screen.update {
                        it.copy(
                            isLoading = false,
                            isOffline = error is ApiError.Network,
                            error = when (error) {
                                is ApiError.Network -> "You're offline. Showing saved categories."
                                is ApiError.Server -> error.serverMessage
                                else -> "Couldn't load categories. Try again."
                            },
                        )
                    }
                },
            )
        }
    }

    fun saveCategory(id: String?, draft: CategoryDraft) {
        val name = draft.name.trim()
        if (name.isEmpty()) {
            screen.update { it.copy(error = "Enter a category name.") }
            return
        }
        if (name.length > 100) {
            screen.update { it.copy(error = "Name must be 100 characters or fewer.") }
            return
        }
        val duplicate = state.value.categories.firstOrNull { existing ->
            existing.id != id && existing.isIncome == draft.isIncome && normalizeCategoryName(existing.name) == normalizeCategoryName(name)
        }
        if (duplicate != null) {
            screen.update { it.copy(error = "\"${duplicate.name}\" already exists.") }
            return
        }

        screen.update { it.copy(isSaving = true, error = null, isOffline = false) }
        viewModelScope.launch {
            val result = runCatching {
                if (id == null) {
                    categories.create(CategoryCreateRequest(name, draft.color, draft.isIncome, draft.icon))
                } else {
                    categories.update(
                        id,
                        CategoryUpdateRequest(name, draft.color, draft.isIncome, draft.icon ?: ""),
                    )
                }
            }
            result.fold(
                onSuccess = {
                    screen.update { it.copy(isSaving = false) }
                    _events.send(CategoriesManagementEvent.Saved)
                },
                onFailure = { error ->
                    screen.update {
                        it.copy(
                            isSaving = false,
                            isOffline = error is ApiError.Network,
                            error = when (error) {
                                is ApiError.Network -> "You're offline. Try again when you're connected."
                                is ApiError.Server -> error.serverMessage
                                else -> "Couldn't save this category. Try again."
                            },
                        )
                    }
                },
            )
        }
    }

    fun deleteCategory(id: String) {
        screen.update { it.copy(isSaving = true, error = null, isOffline = false) }
        viewModelScope.launch {
            val result = runCatching { categories.delete(id) }
            result.fold(
                onSuccess = {
                    screen.update { it.copy(isSaving = false) }
                    _events.send(CategoriesManagementEvent.Deleted)
                },
                onFailure = { error ->
                    screen.update {
                        it.copy(
                            isSaving = false,
                            isOffline = error is ApiError.Network,
                            error = when (error) {
                                is ApiError.Network -> "You're offline. Try again when you're connected."
                                is ApiError.Server -> DELETE_ERROR_COPY[error.serverMessage] ?: error.serverMessage
                                else -> "Couldn't delete this category. Try again."
                            },
                        )
                    }
                },
            )
        }
    }
}

private fun normalizeCategoryName(value: String): String =
    Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.getDefault())

/** 409 codes of `DELETE /categories/{id}` (packages/functions/src/api/categories.ts:472, 564-576). */
private val DELETE_ERROR_COPY = mapOf(
    "CategoryHasReferences" to "This category is still used by transactions or income and bills. Move them to another category first.",
    "CategoryDeleteInProgress" to "This category is already being deleted. Try again in a moment.",
)
