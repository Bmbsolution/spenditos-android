package com.spenditos.android.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.categories.CategoryRepository
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.transactions.CreateResult
import com.spenditos.android.data.transactions.NewTransaction
import com.spenditos.android.data.transactions.TransactionRepository
import com.spenditos.android.data.user.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

enum class EntryType { EXPENSE, INCOME }

data class AddTransactionUiState(
    val type: EntryType = EntryType.EXPENSE,
    val amountText: String = "",
    val categoryId: String? = null,
    val description: String = "",
    val date: LocalDate = LocalDate.now(),
    val categories: List<Category> = emptyList(),
    val currency: String = "USD",
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    /** Income vs expense is the category's `isIncome` (iOS TransactionType is UI-only). */
    val visibleCategories: List<Category> get() = categories.filter { it.isIncome == (type == EntryType.INCOME) }
    val amount: BigDecimal? get() = parseAmount(amountText)
    val canSave: Boolean get() = !isSaving && (amount?.signum() ?: 0) > 0 && categoryId != null
}

/** Accepts "12.50" and "12,50"; at most two decimals; null when not a number. */
internal fun parseAmount(text: String): BigDecimal? {
    val normalized = text.trim().replace(',', '.')
    if (normalized.isEmpty() || normalized.count { it == '.' } > 1) return null
    return normalized.toBigDecimalOrNull()?.takeIf { it.scale() <= 2 }
}

/**
 * Values to start the form with, e.g. from a receipt scan. Everything is optional and is only
 * applied when valid; the user always reviews and saves (nothing is created by a scan itself).
 */
data class AddTransactionPrefill(
    val amount: Double? = null,
    val description: String? = null,
    /** `YYYY-MM-DD`; ignored when it doesn't parse. */
    val date: String? = null,
    /** A category id from the server; ignored when the user has no such category. */
    val categoryId: String? = null,
    /** A category name hint (case-insensitive match), used when [categoryId] doesn't resolve. */
    val categoryHint: String? = null,
)

sealed interface AddTransactionEvent {
    data class Done(val message: String) : AddTransactionEvent
}

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    categories: CategoryRepository,
    settings: UserSettingsRepository,
) : ViewModel() {
    private val form = MutableStateFlow(AddTransactionUiState())
    private val _events = Channel<AddTransactionEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<AddTransactionUiState> = combine(form, categories.observe(), settings.currency()) { f, cats, currency ->
        f.copy(categories = cats, currency = currency)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddTransactionUiState())

    /** Applies [prefill] once the user's categories are known; safe to call repeatedly for the same value. */
    fun applyPrefill(prefill: AddTransactionPrefill) {
        val categories = state.value.categories
        val category = categories.firstOrNull { it.id == prefill.categoryId }
            ?: prefill.categoryHint?.trim()?.takeIf { it.isNotEmpty() }?.let { hint ->
                categories.firstOrNull { it.name.equals(hint, ignoreCase = true) }
            }
        val parsedDate = prefill.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        form.update { f ->
            f.copy(
                type = category?.let { if (it.isIncome) EntryType.INCOME else EntryType.EXPENSE } ?: f.type,
                amountText = prefill.amount?.takeIf { it > 0 }?.let { BigDecimal.valueOf(it).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() } ?: f.amountText,
                categoryId = category?.id ?: f.categoryId,
                description = prefill.description?.take(200) ?: f.description,
                date = parsedDate ?: f.date,
                error = null,
            )
        }
    }

    fun onTypeChange(type: EntryType) = form.update { it.copy(type = type, categoryId = null, error = null) }

    fun onAmountChange(text: String) = form.update { it.copy(amountText = text.filter { c -> c.isDigit() || c == '.' || c == ',' }, error = null) }

    fun onCategorySelect(id: String) = form.update { it.copy(categoryId = id, error = null) }

    fun onDescriptionChange(text: String) = form.update { it.copy(description = text.take(200), error = null) }

    fun onDateChange(date: LocalDate) = form.update { it.copy(date = date) }

    fun save() {
        val current = state.value
        val amount = current.amount ?: return
        val categoryId = current.categoryId ?: return
        if (!current.canSave) return
        form.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = runCatching {
                transactions.create(NewTransaction(amount.toDouble(), categoryId, current.description, current.date))
            }
            form.update { it.copy(isSaving = false) }
            result.fold(
                onSuccess = { created ->
                    when (created) {
                        CreateResult.Saved -> _events.send(AddTransactionEvent.Done("Saved"))
                        CreateResult.SavedOffline -> _events.send(AddTransactionEvent.Done("Saved on this device. It will sync when you're back online."))
                        is CreateResult.Rejected -> form.update { it.copy(error = created.message) }
                    }
                },
                onFailure = { error ->
                    form.update { it.copy(error = if (error is ApiError.Unauthorized) "Sign in again to save." else "Couldn't save. Please try again.") }
                },
            )
        }
    }
}
