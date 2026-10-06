package com.spenditos.android.feature.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.CreateRecurringTemplateRequest
import com.spenditos.android.data.model.GenerateTransactionsResult
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.model.UpdateRecurringTemplateRequest
import com.spenditos.android.data.recurring.RecurringActions
import com.spenditos.android.data.recurring.RecurringCategorySource
import com.spenditos.android.data.user.UserSettingsActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.YearMonth
import javax.inject.Inject

data class IncomeAndBillsUiState(
    val templates: List<RecurringTemplate> = emptyList(),
    val categories: List<Category> = emptyList(),
    val mainIncomeTemplateId: String? = null,
    val currency: String = "USD",
    val isLoading: Boolean = true,
    val isWriting: Boolean = false,
    val isOffline: Boolean = false,
    val error: String? = null,
) {
    private val categoryById get() = categories.associateBy { it.id }
    val incomeTemplates: List<RecurringTemplate>
        get() = templates.filter { categoryById[it.categoryId]?.isIncome == true }
    val billTemplates: List<RecurringTemplate>
        get() = templates.filter { categoryById[it.categoryId]?.isIncome != true }
    val isEmpty: Boolean get() = !isLoading && error == null && templates.isEmpty()
}

sealed interface IncomeAndBillsEvent {
    data object Saved : IncomeAndBillsEvent
    data object Deleted : IncomeAndBillsEvent
    data class Generated(val result: GenerateTransactionsResult) : IncomeAndBillsEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IncomeAndBillsViewModel @Inject constructor(
    private val recurring: RecurringActions,
    private val categories: RecurringCategorySource,
    settings: UserSettingsActions,
    invalidations: FinanceInvalidationBus,
) : ViewModel() {
    private val mutableState = MutableStateFlow(IncomeAndBillsUiState())
    val state: StateFlow<IncomeAndBillsUiState> = mutableState.asStateFlow()
    private val _events = Channel<IncomeAndBillsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(recurring.observe(), recurring.observeMainIncomeId(), categories.observe(), settings.currency()) { templates, mainIncomeId, categoryRows, currency ->
                mutableState.update { it.copy(templates = templates, mainIncomeTemplateId = mainIncomeId, categories = categoryRows, currency = currency) }
            }.collect {}
        }
        viewModelScope.launch {
            invalidations.events.debounce(250).collect { changed ->
                if (Aggregate.RECURRING in changed) {
                    refreshFromInvalidation()
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, error = null, isOffline = false) }
            try {
                recurring.refresh()
                categories.refresh()
                mutableState.update { it.copy(isLoading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showLoadFailure(error)
            }
        }
    }

    fun create(request: CreateRecurringTemplateRequest) = write {
        recurring.create(request)
        _events.send(IncomeAndBillsEvent.Saved)
    }

    fun update(id: String, request: UpdateRecurringTemplateRequest) = write {
        recurring.update(id, request)
        _events.send(IncomeAndBillsEvent.Saved)
    }

    fun delete(id: String) = write {
        recurring.delete(id)
        _events.send(IncomeAndBillsEvent.Deleted)
    }

    fun setMainIncome(id: String?) {
        if (id != null) {
            val template = state.value.templates.firstOrNull { it.id == id } ?: return
            if (state.value.categories.firstOrNull { it.id == template.categoryId }?.isIncome != true || !template.isActive) return
        }
        write { recurring.setMainIncome(id); _events.send(IncomeAndBillsEvent.Saved) }
    }

    fun generate(month: YearMonth) = write {
        val result = recurring.generate(month)
        _events.send(IncomeAndBillsEvent.Generated(result))
    }

    private fun write(operation: suspend () -> Unit) {
        if (mutableState.value.isWriting) return
        mutableState.update { it.copy(isWriting = true, error = null, isOffline = false) }
        viewModelScope.launch {
            try {
                operation()
                mutableState.update { it.copy(isWriting = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(isWriting = false, isOffline = error is ApiError.Network, error = writeMessage(error))
                }
            }
        }
    }

    private suspend fun refreshFromInvalidation() {
        try {
            recurring.refresh()
            mutableState.update { it.copy(error = null, isOffline = false) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            showLoadFailure(error)
        }
    }

    private fun showLoadFailure(error: Exception) {
        mutableState.update {
            it.copy(
                isLoading = false,
                isOffline = error is ApiError.Network || error is IOException,
                error = if (error is ApiError.Network || error is IOException) "You're offline. Check your connection and try again." else "Couldn't load income and bills. Please try again.",
            )
        }
    }

    private fun writeMessage(error: Throwable): String =
        if (error is ApiError.Network || error is IOException) "You're offline. Reconnect to save changes."
        else "Couldn't save changes. Please try again."
}
