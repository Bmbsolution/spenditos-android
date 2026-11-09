package com.spenditos.android.feature.pro

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.data.entitlement.ProAccess
import com.paykit.core.Provider
import com.spenditos.android.data.purchases.ManageResult
import com.spenditos.android.data.purchases.PaymentsUnavailableException
import com.spenditos.android.data.purchases.ProPackage
import com.spenditos.android.data.purchases.PurchaseResult
import com.spenditos.android.data.purchases.PurchasesService
import com.spenditos.android.data.purchases.paidProvider
import com.spenditos.android.data.purchases.periodName
import com.spenditos.android.data.user.UserSettingsActions
import com.spenditos.android.feature.settings.SettingsPlanLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    /** Who bills this subscription; null for a grant or Free, which have nothing to manage. */
    val provider: Provider? = null,
    /** "Switches to yearly on Dec 1, 2026" while a change is scheduled. */
    val pendingChangeText: String? = null,
    /** Plans a Play subscriber can switch to; other stores change plans in their own place. */
    val changeOptions: List<ProPackage> = emptyList(),
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
                _state.update { it.copy(isLoading = false, isAvailable = false, isPro = pro == true).withAccess() }
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
                    ).withAccess()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: PaymentsUnavailableException) {
                _state.update { it.copy(isLoading = false, isAvailable = false, isPro = pro == true).withAccess() }
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, isPro = pro == true, error = if (pro == true) null else Strings.get(R.string.pro_couldn_t_load_the_plans)).withAccess() }
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

    fun changePlan(activity: Activity, packageId: String) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, error = null, notice = null) }
        viewModelScope.launch {
            val result = purchases.changePlan(activity, packageId, proAccess.current().premium?.plan?.period)
            if (result == PurchaseResult.Success) {
                _state.update { it.copy(isBusy = false, notice = Strings.get(R.string.pro_plan_changed)).withAccess() }
            } else {
                handle(result)
            }
        }
    }

    /** [open] gets the provider's page: a store's subscriptions, or Stripe's billing portal. */
    fun manage(open: (String) -> Unit) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, error = null, notice = null) }
        viewModelScope.launch {
            when (val result = purchases.manageUrl(proAccess.current().premium)) {
                is ManageResult.Open -> {
                    _state.update { it.copy(isBusy = false) }
                    open(result.url)
                }
                ManageResult.Unavailable -> _state.update { it.copy(isBusy = false, error = Strings.get(R.string.pro_subscriptions_aren_t_available_right_now)) }
                is ManageResult.Failed -> _state.update { it.copy(isBusy = false, error = result.message) }
            }
        }
    }

    /** The parts of the Pro card that come from the server's premium. */
    private fun ProUiState.withAccess(): ProUiState {
        val access = proAccess.current()
        val provider = paidProvider(access.premium)
        val current = periodName(access.premium?.plan?.period)
        val pending = periodName(access.premium?.pendingChange?.period)
        return copy(
            provider = provider,
            pendingChangeText = SettingsPlanLabel.pendingChangeText(access),
            changeOptions = if (provider == Provider.GooglePlay && current != null) packages.filter { it.period != null && it.period != current && it.period != pending } else emptyList(),
        )
    }

    private fun handle(result: PurchaseResult) {
        when (result) {
            // The service already synced, so this is the server's answer; it can still lag the store.
            PurchaseResult.Success -> if (proAccess.current().hasLastingPro) confirmed() else processing()
            PurchaseResult.Processing -> processing()
            PurchaseResult.Cancelled -> _state.update { it.copy(isBusy = false) }
            PurchaseResult.Pending -> _state.update { it.copy(isBusy = false, notice = Strings.get(R.string.common_your_purchase_is_pending_pro)) }
            PurchaseResult.NothingToRestore -> _state.update { it.copy(isBusy = false, notice = Strings.get(R.string.data_purchases_no_purchases_to_restore)) }
            PurchaseResult.Unavailable -> _state.update { it.copy(isBusy = false, isAvailable = false) }
            is PurchaseResult.Failed -> _state.update { it.copy(isBusy = false, error = result.message) }
        }
    }

    private fun confirmed() = _state.update { it.copy(isBusy = false, isPro = true, notice = Strings.get(R.string.pro_you_have_pro_thank_you)).withAccess() }

    /** Never leave the user wondering whether they paid for nothing. */
    private fun processing() = _state.update { it.copy(isBusy = false, notice = Strings.get(R.string.pro_your_purchase_is_processing_pro)) }

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
}
