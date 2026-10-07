package com.spenditos.android.feature.pro

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.data.purchases.ProPackage
import com.spenditos.android.data.purchases.PurchaseResult
import com.spenditos.android.data.purchases.PurchasesService
import com.spenditos.android.data.user.UserSettingsActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProUiState(
    val isLoading: Boolean = true,
    val isAvailable: Boolean = true,
    val isPro: Boolean = false,
    val packages: List<ProPackage> = emptyList(),
    val selectedId: String? = null,
    val isBusy: Boolean = false,
    val error: String? = null,
    /** Calm status after a purchase or restore. */
    val notice: String? = null,
)

@HiltViewModel
class ProViewModel @Inject constructor(
    private val purchases: PurchasesService,
    private val settings: UserSettingsActions,
) : ViewModel() {
    private val _state = MutableStateFlow(ProUiState(isAvailable = purchases.isAvailable))
    val state: StateFlow<ProUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val pro = currentlyPro()
            if (!purchases.isAvailable) {
                _state.update { it.copy(isLoading = false, isAvailable = false, isPro = pro == true) }
                return@launch
            }
            try {
                val list = purchases.packages()
                _state.update {
                    it.copy(
                        isLoading = false,
                        isPro = pro == true,
                        packages = list,
                        selectedId = it.selectedId?.takeIf { id -> list.any { p -> p.id == id } }
                            ?: (list.firstOrNull { p -> p.isBestValue } ?: list.firstOrNull())?.id,
                        error = if (list.isEmpty() && pro != true) "No plans are available right now. Try again later." else null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, isPro = pro == true, error = "Couldn't load the plans. Check your connection and try again.") }
            }
        }
    }

    fun select(id: String) = _state.update { it.copy(selectedId = id) }

    fun buy(activity: Activity) {
        val id = _state.value.selectedId ?: return
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, error = null, notice = null) }
        viewModelScope.launch { handle(purchases.purchase(activity, id)) }
    }

    fun restore() {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, error = null, notice = null) }
        viewModelScope.launch { handle(purchases.restore()) }
    }

    private suspend fun handle(result: PurchaseResult) {
        when (result) {
            PurchaseResult.Success -> confirmEntitlement()
            PurchaseResult.Cancelled -> _state.update { it.copy(isBusy = false) }
            PurchaseResult.Pending -> _state.update { it.copy(isBusy = false, notice = "Your purchase is pending. Pro will turn on when it clears.") }
            is PurchaseResult.Failed -> _state.update { it.copy(isBusy = false, error = result.message) }
        }
    }

    /**
     * The store has the purchase, but Pro comes from our server (webhook), which can lag. Look a few times;
     * never leave the user wondering whether they paid for nothing.
     */
    private suspend fun confirmEntitlement() {
        repeat(POLLS) { attempt ->
            if (currentlyPro() == true) {
                _state.update { it.copy(isBusy = false, isPro = true, notice = "You have Pro. Thank you!") }
                return
            }
            if (attempt < POLLS - 1) delay(POLL_MILLIS)
        }
        _state.update { it.copy(isBusy = false, notice = "Your purchase is processing. Pro will turn on shortly.") }
    }

    private suspend fun currentlyPro(): Boolean? = try {
        settings.refresh().premium?.active == true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    companion object {
        const val POLLS = 10
        const val POLL_MILLIS = 2_000L
    }
}
