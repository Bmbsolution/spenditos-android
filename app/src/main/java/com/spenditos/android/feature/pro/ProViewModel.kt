package com.spenditos.android.feature.pro

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.data.entitlement.ProAccess
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
    private val proAccess: ProAccess,
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
                        error = if (list.isEmpty() && pro != true) Strings.get(R.string.pro_no_plans_are_available_right) else null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, isPro = pro == true, error = Strings.get(R.string.pro_couldn_t_load_the_plans)) }
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
            PurchaseResult.Pending -> _state.update { it.copy(isBusy = false, notice = Strings.get(R.string.common_your_purchase_is_pending_pro)) }
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
                _state.update { it.copy(isBusy = false, isPro = true, notice = Strings.get(R.string.pro_you_have_pro_thank_you)) }
                return
            }
            if (attempt < POLLS - 1) delay(POLL_MILLIS)
        }
        _state.update { it.copy(isBusy = false, notice = Strings.get(R.string.pro_your_purchase_is_processing_pro)) }
    }

    /**
     * Pro that needs no purchase (a subscription or an admin grant). Welcome Premium does not count:
     * that user can still buy, like iOS where the welcome row leads to the paywall.
     */
    private suspend fun currentlyPro(): Boolean? = try {
        settings.refresh() // applies the server's premium status to [proAccess]
        proAccess.current().hasLastingPro
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
