package com.spenditos.android.feature.debts

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.debts.Debt
import com.spenditos.android.data.debts.DebtActions
import com.spenditos.android.data.debts.DebtDetail
import com.spenditos.android.data.debts.DebtHistoryKind
import com.spenditos.android.data.debts.DebtPatch
import com.spenditos.android.data.debts.DebtPreviewRequest
import com.spenditos.android.data.debts.DebtPreviewResponse
import com.spenditos.android.data.debts.DebtProjection
import com.spenditos.android.data.debts.DebtSummary
import com.spenditos.android.data.debts.DebtWithPlan
import com.spenditos.android.data.debts.UpdateDebtPayment
import com.spenditos.android.data.debts.UpdateDebtRequest
import com.spenditos.android.data.user.UserSettingsActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

internal const val DEBT_EXTRA_STEP = 25.0
internal const val DEBT_WHAT_IF_DEBOUNCE_MS = 300L

/** One row of the History card. */
data class DebtHistoryRow(
    val id: String,
    val kind: DebtHistoryKind,
    val title: String,
    val dateText: String,
    /** Payment amount, or "Set to $6,500" for a correction. */
    val amountText: String,
    /** "Includes $33.75 interest", or null. */
    val interestNote: String?,
    val balanceAfterText: String,
)

data class DebtWhatIfResult(
    /** "Jul 2028" */
    val doneBy: String?,
    val paymentsSooner: Int,
    val interestSaved: Double,
    val stillNeverPaysOff: Boolean,
)

/**
 * One debt (iOS `DebtDetailViewModel`; docs/features/debts.md "What the user gets"): where it stands,
 * what-if "pay more" planning, extra payments, balance corrections, history and delete. Every write is
 * online-only and followed by a refresh of the debt with the device's local date as `asOf`.
 */
data class DebtDetailUiState(
    /** The debt with its history. Null until the first load finishes. */
    val detail: DebtDetail? = null,
    val currency: String = "USD",
    val isLoading: Boolean = false,
    /** A write is in flight (buttons disable, spinners show). */
    val isWorking: Boolean = false,
    val error: String? = null,
    val wasDeleted: Boolean = false,
    // What if I pay more?
    /** Extra money per payment, in steps of [DEBT_EXTRA_STEP]. 0 means "no change". */
    val extra: Double = 0.0,
    val whatIf: DebtPreviewResponse? = null,
    val isWhatIfLoading: Boolean = false,
    val whatIfError: String? = null,
) {
    // region Where it stands

    val plan: DebtWithPlan? get() = detail?.let { DebtWithPlan(it.debt, it.summary, it.projection) }
    val debt: Debt? get() = detail?.debt
    val summary: DebtSummary? get() = detail?.summary
    val projection: DebtProjection? get() = detail?.projection
    val isPaidOff: Boolean get() = summary?.isPaidOff ?: false

    private fun money(value: Double) = DebtFormat.money(value, currency)

    val balanceText: String get() = money(summary?.balance ?: 0.0)

    val progress: Double get() = summary?.progress ?: 0.0

    /** "$3,960 of $10,000 paid off" */
    val paidOffText: String?
        get() {
            val plan = plan ?: return null
            val paid = max(0.0, plan.debt.startingBalance - plan.summary.balance)
            return Strings.get(R.string.debts_paid_of_total, money(paid), money(plan.debt.startingBalance))
        }

    /** "40%". Rounds down so a nearly paid debt never reads 100%. */
    val percentText: String
        get() {
            val summary = summary ?: return "0%"
            if (summary.isPaidOff) return "100%"
            return "${(summary.progress * 100).toInt()}%"
        }

    /** "Paid off on Aug 15, 2026" */
    val paidOffOnText: String?
        get() {
            if (!isPaidOff) return null
            return DebtFormat.shortDate(summary?.paidOffDate)?.let { Strings.get(R.string.debts_paid_off_on, it) } ?: Strings.get(R.string.debts_paid_off)
        }

    /** A payment that is actually scheduled. */
    val hasPayment: Boolean
        get() {
            val summary = summary ?: return false
            val plan = plan ?: return false
            return summary.payment != null && summary.frequency != null && !summary.isPaidOff && !plan.paymentsStopped
        }

    /** "$320 / month", "$100 every 3 weeks". Null without a bill. */
    val paymentText: String?
        get() {
            val summary = summary ?: return null
            return if (hasPayment) DebtFormat.paymentLine(summary, currency) else null
        }

    val nextPaymentText: String? get() = DebtFormat.shortDate(summary?.nextPaymentDate)

    /** "Jul 2028", or null when the payment is too low or there is no payment. */
    val doneByText: String?
        get() {
            val projection = projection ?: return null
            if (projection.neverPaysOff || isPaidOff) return null
            return DebtFormat.monthYear(projection.payoffDate)
        }

    val neverPaysOff: Boolean
        get() {
            val projection = projection ?: return false
            return !isPaidOff && projection.neverPaysOff
        }

    /** Payments were stopped: the bill is kept for history but nothing is scheduled. */
    val paymentsStopped: Boolean get() = plan?.paymentsStopped ?: false

    /** "19 payments left, the last one $140" */
    val paymentsLeftText: String?
        get() {
            val projection = projection ?: return null
            if (isPaidOff || projection.neverPaysOff || projection.paymentsLeft <= 0) return null
            val count = projection.paymentsLeft
            val last = projection.lastPaymentAmount?.let { money(it) }
            if (count == 1) return if (last != null) Strings.get(R.string.debts_one_payment_left_last, last) else Strings.get(R.string.debts_one_payment_left)
            if (last == null) return Strings.get(R.string.debts_payments_left, count)
            return Strings.get(R.string.debts_payments_left_last, count, last)
        }

    // endregion

    // region Interest (only when the debt charges it)

    val showsInterest: Boolean get() = debt?.apr != null

    val aprText: String?
        get() {
            val apr = debt?.apr ?: return null
            val text = BigDecimal(apr).round(MathContext(6)).stripTrailingZeros().toPlainString()
            return Strings.get(R.string.debts_percent_a_year, text)
        }

    val interestSoFarText: String?
        get() {
            val summary = summary
            return if (showsInterest && summary != null) money(summary.interestTotal) else null
        }

    val interestToComeText: String?
        get() {
            val projection = projection ?: return null
            return if (showsInterest && !isPaidOff) money(projection.interestRemaining) else null
        }

    // endregion

    // region What if I pay more?

    /** Only a debt with a payment and a next payment date can be previewed. */
    val canWhatIf: Boolean get() = hasPayment && summary?.nextPaymentDate != null

    /** The slider's upper end: enough to matter, never more than finishes the debt. */
    val extraMax: Double
        get() {
            val summary = summary ?: return DEBT_EXTRA_STEP
            val payment = summary.payment ?: return DEBT_EXTRA_STEP
            fun roundedUp(value: Double) = ceil(value / DEBT_EXTRA_STEP) * DEBT_EXTRA_STEP
            val usual = max(500.0, roundedUp(payment * 2))
            val toFinish = max(DEBT_EXTRA_STEP, roundedUp(summary.balance - payment))
            return min(usual, toFinish)
        }

    val newPayment: Double? get() = summary?.payment?.let { it + extra }

    val whatIfResult: DebtWhatIfResult?
        get() {
            val whatIf = whatIf ?: return null
            val projection = projection ?: return null
            if (extra <= 0) return null
            val sooner = if (projection.neverPaysOff) 0 else max(0, projection.paymentsLeft - whatIf.paymentsCount)
            val saved = if (projection.neverPaysOff) 0.0 else max(0.0, projection.interestRemaining - whatIf.totalInterest)
            return DebtWhatIfResult(
                doneBy = DebtFormat.monthYear(whatIf.payoffDate),
                paymentsSooner = sooner,
                interestSaved = DebtFormat.cents(saved),
                stillNeverPaysOff = whatIf.neverPaysOff,
            )
        }

    /** "Pay $370 from now on" */
    val applyWhatIfTitle: String?
        get() {
            val newPayment = newPayment ?: return null
            return if (extra > 0) Strings.get(R.string.debts_pay_from_now_on, money(newPayment)) else null
        }

    /**
     * The preview request for the current slider position, per the spec:
     * `{ balance, apr, frequency, repeatInterval, firstPaymentDate, payment: payment + extra }`.
     */
    val whatIfRequest: DebtPreviewRequest?
        get() {
            if (!canWhatIf) return null
            val summary = summary ?: return null
            val debt = debt ?: return null
            val payment = summary.payment ?: return null
            val frequency = summary.frequency ?: return null
            val first = summary.nextPaymentDate ?: return null
            return DebtPreviewRequest(
                balance = summary.balance,
                apr = debt.apr,
                frequency = frequency,
                repeatInterval = summary.repeatInterval,
                firstPaymentDate = first,
                payment = payment + extra,
            )
        }

    // endregion

    // region History

    val historyRows: List<DebtHistoryRow>
        get() = (detail?.history ?: emptyList()).mapIndexed { index, entry ->
            val title: String
            val amountText: String
            when (entry.kind) {
                DebtHistoryKind.PAYMENT -> { title = Strings.get(R.string.debts_payment); amountText = money(entry.amount) }
                DebtHistoryKind.EXTRA_PAYMENT -> { title = Strings.get(R.string.debts_extra_payment); amountText = money(entry.amount) }
                DebtHistoryKind.ADJUSTMENT -> { title = Strings.get(R.string.debts_balance_corrected); amountText = Strings.get(R.string.debts_set_to, money(entry.amount)) }
                DebtHistoryKind.UNKNOWN -> { title = Strings.get(R.string.debts_update); amountText = money(entry.amount) }
            }
            DebtHistoryRow(
                id = "$index-${entry.kind.wire}-${entry.date}",
                kind = entry.kind,
                title = title,
                dateText = DebtFormat.shortDate(entry.date) ?: entry.date,
                amountText = amountText,
                interestNote = if (entry.interest > 0) Strings.get(R.string.debts_includes_interest, money(entry.interest)) else null,
                balanceAfterText = Strings.get(R.string.debts_balance, money(entry.balanceAfter)),
            )
        }

    // endregion
}

/** One-shot results the screen turns into a snackbar. */
sealed interface DebtDetailEvent {
    data object ExtraPaymentAdded : DebtDetailEvent
    data object BalanceCorrected : DebtDetailEvent
    data object PaymentUpdated : DebtDetailEvent
    data object Deleted : DebtDetailEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DebtDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val debts: DebtActions,
    settings: UserSettingsActions,
    private val connectivity: ConnectivityStatus,
    invalidations: FinanceInvalidationBus,
    private val clock: Clock,
) : ViewModel() {
    val debtId: String = checkNotNull(savedStateHandle.get<String>("debtId")) { "debtId is required" }

    private val mutableState = MutableStateFlow(DebtDetailUiState())
    val state: StateFlow<DebtDetailUiState> = mutableState.asStateFlow()
    private val _events = Channel<DebtDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var whatIfJob: Job? = null
    private var extraPaymentAttempt: Triple<Double, LocalDate, String>? = null

    init {
        viewModelScope.launch {
            settings.currency().collect { currency -> mutableState.update { it.copy(currency = currency) } }
        }
        viewModelScope.launch {
            // Bill entries and transactions move the balance (also from another screen or device).
            invalidations.events.debounce(250).collect { changed ->
                if ((Aggregate.TRANSACTION in changed || Aggregate.RECURRING in changed) && !mutableState.value.wasDeleted) refresh()
            }
        }
        viewModelScope.launch { load() }
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    // region What if I pay more?

    /**
     * Moves the slider. Snaps to [DEBT_EXTRA_STEP], stays inside `0...extraMax`, and asks the preview
     * endpoint after a short pause so dragging stays smooth.
     */
    fun setExtra(value: Double) {
        val current = mutableState.value
        val snapped = roundHalfAwayFromZero(value / DEBT_EXTRA_STEP) * DEBT_EXTRA_STEP
        val clamped = min(max(0.0, snapped), current.extraMax)
        if (clamped == current.extra) return
        whatIfJob?.cancel()
        if (clamped <= 0) {
            mutableState.update { it.copy(extra = clamped, whatIfError = null, whatIf = null, isWhatIfLoading = false) }
            return
        }
        mutableState.update { it.copy(extra = clamped, whatIfError = null, isWhatIfLoading = true) }
        whatIfJob = viewModelScope.launch {
            delay(DEBT_WHAT_IF_DEBOUNCE_MS)
            runWhatIf()
        }
    }

    suspend fun runWhatIf() {
        val start = mutableState.value
        val request = start.whatIfRequest
        if (start.extra <= 0 || request == null) {
            mutableState.update { it.copy(isWhatIfLoading = false) }
            return
        }
        val requestedExtra = start.extra
        try {
            val response = debts.preview(request)
            // A newer slider position owns the result now.
            if (requestedExtra != mutableState.value.extra) return
            mutableState.update { it.copy(whatIf = response, whatIfError = null) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (requestedExtra != mutableState.value.extra) return
            mutableState.update { it.copy(whatIf = null, whatIfError = DebtErrorMessage.message(error)) }
        }
        if (requestedExtra == mutableState.value.extra) mutableState.update { it.copy(isWhatIfLoading = false) }
    }

    fun resetWhatIf() {
        whatIfJob?.cancel()
        mutableState.update { it.copy(extra = 0.0, whatIf = null, whatIfError = null, isWhatIfLoading = false) }
    }

    // endregion

    // region Loading

    suspend fun load() {
        mutableState.update { it.copy(isLoading = true) }
        try {
            fetch(reportErrors = true)
        } finally {
            mutableState.update { it.copy(isLoading = false) }
        }
    }

    suspend fun refresh() {
        fetch(reportErrors = true)
    }

    fun retry() {
        viewModelScope.launch { load() }
    }

    fun dismissError() {
        mutableState.update { it.copy(error = null) }
    }

    private suspend fun fetch(reportErrors: Boolean): Boolean = try {
        val detail = debts.detail(debtId, today())
        mutableState.update { it.copy(detail = detail, error = if (reportErrors) null else it.error) }
        true
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        if (reportErrors) mutableState.update { it.copy(error = DebtErrorMessage.message(error)) }
        false
    }

    // endregion

    // region Writes (online only)

    /**
     * Runs one write, then refreshes. The write's own success is what counts: a failed refresh afterwards
     * keeps the old numbers and shows nothing, the next refresh fixes it.
     */
    private suspend fun write(refresh: Boolean = true, work: suspend () -> DebtDetail?): Boolean {
        if (mutableState.value.isWorking) return false
        mutableState.update { it.copy(isWorking = true, error = null) }
        try {
            val returned = work()
            if (refresh && !fetch(reportErrors = false) && returned != null) {
                mutableState.update { it.copy(detail = returned) }
            }
            return true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val offline = error is ApiError.Network && !connectivity.isOnline.value
            mutableState.update { it.copy(error = DebtErrorMessage.message(error, offline)) }
            return false
        } finally {
            mutableState.update { it.copy(isWorking = false) }
        }
    }

    /**
     * One key survives retries of the same extra payment after an uncertain response.
     */
    suspend fun addExtraPayment(amount: Double, date: LocalDate): Boolean {
        val debt = mutableState.value.debt ?: return false
        val attempt = extraPaymentAttempt?.takeIf { it.first == amount && it.second == date }
            ?: Triple(amount, date, UUID.randomUUID().toString()).also { extraPaymentAttempt = it }
        val succeeded = write {
            debts.recordExtraPayment(debt, amount, date, attempt.third)
            null
        }
        if (succeeded) {
            extraPaymentAttempt = null
            resetWhatIf()
        }
        return succeeded
    }

    /** "Correct balance": what you owe on [date] (never later than tomorrow). */
    suspend fun correctBalance(balance: Double, date: LocalDate): Boolean {
        val succeeded = write { debts.addAdjustment(debtId, date, balance) }
        if (succeeded) resetWhatIf()
        return succeeded
    }

    /** "Pay $X from now on": makes the what-if payment the real one. */
    suspend fun applyWhatIf(): Boolean {
        val current = mutableState.value
        val newPayment = current.newPayment
        if (current.extra <= 0 || newPayment == null) return false
        val request = UpdateDebtRequest(payment = DebtPatch.Value(UpdateDebtPayment(amount = newPayment)))
        val succeeded = write { debts.update(debtId, request) }
        if (succeeded) resetWhatIf()
        return succeeded
    }

    suspend fun delete(): Boolean {
        // The debt is gone: there is nothing to refresh.
        val succeeded = write(refresh = false) {
            debts.delete(debtId)
            null
        }
        if (succeeded) mutableState.update { it.copy(wasDeleted = true) }
        return succeeded
    }

    // The screen calls these; they run on the ViewModel's scope so a sheet closing mid-request cannot cancel a write.

    fun submitExtraPayment(amount: Double, date: LocalDate) {
        viewModelScope.launch { if (addExtraPayment(amount, date)) _events.send(DebtDetailEvent.ExtraPaymentAdded) }
    }

    fun submitCorrectBalance(balance: Double, date: LocalDate) {
        viewModelScope.launch { if (correctBalance(balance, date)) _events.send(DebtDetailEvent.BalanceCorrected) }
    }

    fun submitApplyWhatIf() {
        viewModelScope.launch { if (applyWhatIf()) _events.send(DebtDetailEvent.PaymentUpdated) }
    }

    fun submitDelete() {
        viewModelScope.launch { if (delete()) _events.send(DebtDetailEvent.Deleted) }
    }

    // endregion
}
