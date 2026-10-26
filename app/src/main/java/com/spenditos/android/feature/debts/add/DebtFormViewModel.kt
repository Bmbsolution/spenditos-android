package com.spenditos.android.feature.debts.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.data.debts.CreateDebtPayment
import com.spenditos.android.data.debts.CreateDebtRequest
import com.spenditos.android.data.debts.DebtActions
import com.spenditos.android.data.debts.DebtDetail
import com.spenditos.android.data.debts.DebtIdempotencyKey
import com.spenditos.android.data.debts.DebtPreviewRequest
import com.spenditos.android.data.debts.DebtPreviewResponse
import com.spenditos.android.data.transactions.RecurrencePreset
import com.spenditos.android.data.user.UserSettingsActions
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "Plan by" (iOS `DebtFormViewModel.PlanMode`). */
enum class DebtPlanMode(val label: String) {
    WHAT_I_PAY("What I pay"),
    WHEN_IM_DONE("When I'm done"),

    /** No payment plan: the debt is paid down as the user can (no bill). */
    WHEN_I_CAN("When I can"),
}

/**
 * The sheet's starting point for a month-start shortfall (docs/features/month-balance.md; iOS
 * `DebtFormViewModel.Prefill`): name, amount and "When I can". [shortfallMonth] (`YYYY-MM`) ties the debt to
 * the month that ended short. Nothing here reaches logs: it is user financial data.
 */
data class DebtPrefill(val name: String, val owed: Double, val shortfallMonth: String)

/** The plan the server worked out for exactly [request]. */
data class DebtPreviewResult(val request: DebtPreviewRequest, val response: DebtPreviewResponse)

/**
 * Everything the "Add a debt" sheet shows (iOS `DebtFormViewModel` state and derived values). Nothing here
 * reaches logs or telemetry: name, balance and payment are user financial data.
 */
data class DebtFormUiState(
    val today: LocalDate,
    val name: String = "",
    val owedText: String = "",
    val mode: DebtPlanMode = DebtPlanMode.WHAT_I_PAY,
    val paymentText: String = "",
    /** The month the debt should be paid off by ("When I'm done"). */
    val targetMonth: YearMonth = YearMonth.from(today).plusMonths(1).plusYears(1),
    val frequency: String = "monthly",
    val firstPaymentDate: LocalDate = today.plusMonths(1),
    val chargesInterest: Boolean = false,
    val aprText: String = "",
    /** Set by the first Save tap; from then on every problem shows inline. */
    val showErrors: Boolean = false,
    val currency: String = "USD",
    val isPreviewing: Boolean = false,
    val previewError: String? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val previewResult: DebtPreviewResult? = null,
    /** The month (`YYYY-MM`) that ended short, when the sheet was opened from the month-start card. */
    val shortfallMonth: String? = null,
) {
    // region Parsed values

    val owed: Double? get() = DebtAmountParser.decimal(owedText)
    val payment: Double? get() = DebtAmountParser.decimal(paymentText)
    val aprValue: Double? get() = DebtAmountParser.decimal(aprText)

    /** The yearly rate to send: null when the switch is off. */
    val effectiveApr: Double? get() = if (chargesInterest) aprValue else null
    val trimmedName: String get() = name.trim()

    // endregion

    // region Validation

    val nameError: String?
        get() = when {
            trimmedName.isEmpty() -> if (showErrors) "Give this debt a name" else null
            trimmedName.length > MAX_NAME_LENGTH -> "Use 80 characters or fewer"
            else -> null
        }

    val owedError: String?
        get() {
            if (owedText.isBlank()) return if (showErrors) "Enter how much you owe" else null
            val value = owed
            return if (value == null || value <= 0) "Enter an amount above zero" else null
        }

    val paymentError: String?
        get() {
            if (mode != DebtPlanMode.WHAT_I_PAY) return null
            if (paymentText.isBlank()) return if (showErrors) "Enter how much you pay each time" else null
            val value = payment
            return if (value == null || value <= 0) "Enter an amount above zero" else null
        }

    val targetError: String?
        get() = if (mode == DebtPlanMode.WHEN_IM_DONE && targetMonth < YearMonth.from(firstPaymentDate)) {
            "Pick a month on or after your next payment"
        } else {
            null
        }

    val firstPaymentError: String?
        get() = if (mode != DebtPlanMode.WHEN_I_CAN && firstPaymentDate < today) "Pick today or a later date" else null

    private val aprInRange: Boolean get() = aprValue?.let { it > 0 && it < 100 } == true

    val aprError: String?
        get() {
            if (!chargesInterest) return null
            if (aprText.isBlank()) return if (showErrors) "Enter the yearly rate" else null
            return if (aprInRange) null else "Enter a rate above 0 and below 100"
        }

    /** Everything the Save button needs, ignoring [showErrors]. */
    val isValid: Boolean
        get() {
            if (trimmedName.isEmpty() || trimmedName.length > MAX_NAME_LENGTH) return false
            if ((owed ?: 0.0) <= 0) return false
            if (firstPaymentError != null) return false
            if (chargesInterest && !aprInRange) return false
            return when (mode) {
                DebtPlanMode.WHAT_I_PAY -> (payment ?: 0.0) > 0
                DebtPlanMode.WHEN_IM_DONE -> targetError == null
                DebtPlanMode.WHEN_I_CAN -> true
            }
        }

    /** Whether leaving the sheet would lose something the user typed (iOS `hasUnsavedChanges`). */
    val hasUnsavedChanges: Boolean get() = name.isNotEmpty() || owedText.isNotEmpty() || paymentText.isNotEmpty()

    // endregion

    // region Preview

    /** The preview request for what is typed so far, or null until it is complete enough to ask. */
    val previewRequest: DebtPreviewRequest?
        get() {
            if (mode == DebtPlanMode.WHEN_I_CAN) return null
            val balance = owed?.takeIf { it > 0 } ?: return null
            if (firstPaymentError != null) return null
            if (chargesInterest && !aprInRange) return null
            val first = firstPaymentDate.toString()
            return when (mode) {
                DebtPlanMode.WHAT_I_PAY -> {
                    val amount = payment?.takeIf { it > 0 } ?: return null
                    DebtPreviewRequest(
                        balance = balance, apr = effectiveApr, frequency = frequency,
                        firstPaymentDate = first, payment = amount,
                    )
                }
                DebtPlanMode.WHEN_IM_DONE -> {
                    if (targetError != null) return null
                    DebtPreviewRequest(
                        balance = balance, apr = effectiveApr, frequency = frequency,
                        firstPaymentDate = first, targetMonth = DebtAddText.monthKey(targetMonth),
                    )
                }
                DebtPlanMode.WHEN_I_CAN -> null
            }
        }

    /** The server's plan for the current inputs. A plan for older inputs is never returned. */
    val preview: DebtPreviewResponse?
        get() {
            val request = previewRequest ?: return null
            return previewResult?.takeIf { it.request == request }?.response
        }

    /**
     * First line of the live plan.
     * - What I pay: "Paid off by Jun 2028 · 20 payments, last one $180 · interest ≈ $612"
     * - When I'm done: "Pay $447 / month · done by Jun 2028"
     * - Too low: "Never paid off — pay more each time"
     */
    val previewHeadline: String?
        get() {
            val plan = preview ?: return null
            if (plan.neverPaysOff) return "Never paid off — pay more each time"
            val done = DebtAddText.monthYear(plan.payoffDate)
            return when (mode) {
                DebtPlanMode.WHAT_I_PAY -> buildList {
                    if (done != null) add("Paid off by $done")
                    add(paymentsPhrase(plan))
                    interestPhrase(plan)?.let(::add)
                }.joinToString(" · ")
                DebtPlanMode.WHEN_IM_DONE -> {
                    val pay = "Pay ${DebtAddText.paymentLine(plan.payment, frequency, currency)}"
                    if (done != null) "$pay · done by $done" else pay
                }
                DebtPlanMode.WHEN_I_CAN -> null
            }
        }

    /** Second line, only for "When I'm done" (the payments and the interest). */
    val previewDetail: String?
        get() {
            val plan = preview ?: return null
            if (mode != DebtPlanMode.WHEN_IM_DONE || plan.neverPaysOff) return null
            return buildList {
                add(paymentsPhrase(plan))
                interestPhrase(plan)?.let(::add)
            }.joinToString(" · ")
        }

    private fun paymentsPhrase(plan: DebtPreviewResponse): String {
        val count = plan.paymentsCount
        if (count <= 1) return "1 payment"
        val last = plan.lastPaymentAmount ?: return "$count payments"
        return "$count payments, last one ${DebtAddText.money(last, currency)}"
    }

    private fun interestPhrase(plan: DebtPreviewResponse): String? =
        if (effectiveApr == null) null else "interest ≈ ${DebtAddText.money(plan.totalInterest, currency)}"

    // endregion

    /**
     * The request Save sends for the current inputs. In "When I'm done" the payment is the one the
     * server worked out. Null when it is not ready.
     */
    fun createRequest(): CreateDebtRequest? {
        if (!isValid) return null
        val balance = owed ?: return null
        if (mode == DebtPlanMode.WHEN_I_CAN) {
            // No bill: the payments are recorded as they are made.
            return CreateDebtRequest(
                name = trimmedName,
                startingBalance = balance,
                startDate = today.toString(),
                apr = effectiveApr,
                payment = null,
                shortfallMonth = shortfallMonth,
            )
        }
        val amount = when (mode) {
            DebtPlanMode.WHAT_I_PAY -> payment ?: return null
            DebtPlanMode.WHEN_IM_DONE -> preview?.payment ?: return null
            DebtPlanMode.WHEN_I_CAN -> return null
        }
        return CreateDebtRequest(
            name = trimmedName,
            startingBalance = balance,
            startDate = today.toString(),
            apr = effectiveApr,
            payment = CreateDebtPayment(
                amount = amount,
                frequency = frequency,
                firstPaymentDate = firstPaymentDate.toString(),
                repeatInterval = null,
                secondOccurrenceDay = if (frequency == "semi-monthly") {
                    RecurrencePreset.secondDay(firstPaymentDate.dayOfMonth)
                } else {
                    null
                },
            ),
            shortfallMonth = shortfallMonth,
        )
    }

    companion object {
        const val MAX_NAME_LENGTH = 80
    }
}

sealed interface DebtFormEvent {
    /** The debt was created (a 207 "entries not synced" answer counts); the sheet closes. */
    data class Created(val debtId: String) : DebtFormEvent
}

/**
 * The "Add a debt" sheet (docs/features/debts.md; iOS `DebtFormViewModel`): name, what I owe, how I want
 * to plan it (a payment I choose, or a month I want to be done by), how often, the next payment date and
 * optional interest. A live plan comes from `POST /debts/preview`, debounced. Saving is online-only; one
 * UUID `Idempotency-Key` covers all retries of the same attempt, so a request that went through but whose
 * answer was lost never makes a second debt.
 */
@HiltViewModel
class DebtFormViewModel internal constructor(
    private val actions: DebtActions,
    private val connectivity: ConnectivityStatus,
    settings: UserSettingsActions,
    private val todayProvider: () -> LocalDate,
    private val previewDebounceMs: Long,
) : ViewModel() {
    @Inject
    constructor(
        actions: DebtActions,
        connectivity: ConnectivityStatus,
        settings: UserSettingsActions,
    ) : this(actions, connectivity, settings, { LocalDate.now() }, PREVIEW_DEBOUNCE_MS)

    private val mutableState = MutableStateFlow(DebtFormUiState(today = todayProvider()))
    val state: StateFlow<DebtFormUiState> = mutableState.asStateFlow()

    private val mutableEvents = Channel<DebtFormEvent>(Channel.BUFFERED)
    val events = mutableEvents.receiveAsFlow()

    private var previewJob: Job? = null
    private var createAttempt: Pair<CreateDebtRequest, String>? = null
    private var prefillApplied = false

    init {
        viewModelScope.launch {
            settings.currency().collect { currency -> mutableState.update { it.copy(currency = currency) } }
        }
    }

    // region Inputs

    fun onNameChange(value: String) = mutableState.update { it.copy(name = value.take(MAX_NAME_INPUT)) }

    fun onOwedChange(value: String) = changeInput { it.copy(owedText = value) }

    fun onPaymentChange(value: String) = changeInput { it.copy(paymentText = value) }

    fun onAprChange(value: String) = changeInput { it.copy(aprText = value) }

    fun onFrequencyChange(value: String) = changeInput { it.copy(frequency = value) }

    fun onFirstPaymentDateChange(value: LocalDate) = changeInput { it.copy(firstPaymentDate = value) }

    fun onTargetMonthChange(value: YearMonth) = changeInput { it.copy(targetMonth = value) }

    fun onChargesInterestChange(value: Boolean) = changeInput { it.copy(chargesInterest = value) }

    /** Switching the plan mode. Going back to "What I pay" keeps the payment "When I'm done" worked out when nothing was typed yet. */
    fun setMode(newMode: DebtPlanMode) {
        val current = mutableState.value
        if (newMode == current.mode) return
        val computed = current.preview?.payment
        changeInput {
            val keepComputed = newMode == DebtPlanMode.WHAT_I_PAY && it.paymentText.isBlank() && computed != null
            it.copy(mode = newMode, paymentText = if (keepComputed) plainNumber(computed) else it.paymentText)
        }
    }

    /**
     * Starts the form from a month-start shortfall (name, amount, "When I can", no interest). Applies once per
     * ViewModel, so recomposition or a rotation never overwrites what the user has typed since.
     */
    fun applyPrefill(prefill: DebtPrefill?) {
        if (prefill == null || prefillApplied) return
        prefillApplied = true
        mutableState.update {
            it.copy(
                name = prefill.name.take(MAX_NAME_INPUT),
                owedText = plainNumber(prefill.owed),
                mode = DebtPlanMode.WHEN_I_CAN,
                shortfallMonth = prefill.shortfallMonth,
            )
        }
    }

    fun dismissError() = mutableState.update { it.copy(error = null) }

    private fun changeInput(transform: (DebtFormUiState) -> DebtFormUiState) {
        mutableState.update(transform)
        inputsChanged()
    }

    // endregion

    // region Preview

    /** Waits for a pause in typing, then asks the server for the plan. */
    private fun inputsChanged() {
        previewJob?.cancel()
        mutableState.update { it.copy(previewError = null) }
        val current = mutableState.value
        if (current.previewRequest == null || current.preview != null) {
            mutableState.update { it.copy(isPreviewing = false) }
            return
        }
        mutableState.update { it.copy(isPreviewing = true) }
        previewJob = viewModelScope.launch {
            delay(previewDebounceMs)
            runPreview()
        }
    }

    suspend fun runPreview() {
        val request = mutableState.value.previewRequest
        if (request == null) {
            mutableState.update { it.copy(isPreviewing = false) }
            return
        }
        mutableState.update { it.copy(isPreviewing = true) }
        try {
            val response = actions.preview(request)
            // Inputs moved on while the answer was on its way: drop it.
            if (request != mutableState.value.previewRequest) return
            mutableState.update { it.copy(previewResult = DebtPreviewResult(request, response), previewError = null) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (request != mutableState.value.previewRequest) return
            mutableState.update {
                it.copy(previewResult = null, previewError = DebtAddText.message(error, isWrite = false, online = isOnline()))
            }
        }
        if (request == mutableState.value.previewRequest) mutableState.update { it.copy(isPreviewing = false) }
    }

    // endregion

    // region Save

    /** The Save button: creates the debt and, on success, tells the sheet to close. */
    fun save() {
        viewModelScope.launch {
            val detail = saveNow() ?: return@launch
            mutableEvents.send(DebtFormEvent.Created(detail.id))
        }
    }

    /**
     * Creates the debt. Returns it on success (the 207 "entries not synced" answer counts), null with
     * `error` set otherwise.
     */
    suspend fun saveNow(): DebtDetail? {
        if (mutableState.value.isSaving) return null
        mutableState.update { it.copy(showErrors = true) }
        if (!mutableState.value.isValid) return null
        mutableState.update { it.copy(error = null, isSaving = true) }
        try {
            if (mutableState.value.mode == DebtPlanMode.WHEN_IM_DONE && mutableState.value.preview == null) {
                // The payment comes from the plan: make sure it is current.
                runPreview()
                if (mutableState.value.preview == null) {
                    val reason = mutableState.value.previewError ?: DebtAddText.INVALID_INPUT
                    mutableState.update { it.copy(error = reason) }
                    return null
                }
            }
            val request = mutableState.value.createRequest()
            if (request == null) {
                mutableState.update { it.copy(error = DebtAddText.INVALID_INPUT) }
                return null
            }
            val key = idempotencyKey(request)
            return try {
                // A write that already left the device is finished even when the sheet closes meanwhile:
                // the key makes a later retry safe, and the repository tells other screens to refresh.
                val detail = withContext(NonCancellable) { actions.create(request, key) }
                createAttempt = null
                detail
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = DebtAddText.message(error, isWrite = true, online = isOnline())
                mutableState.update { it.copy(error = message) }
                null
            }
        } finally {
            mutableState.update { it.copy(isSaving = false) }
        }
    }

    /**
     * The key for [request]: the same one while the same attempt is retried, a new one when anything
     * about the debt changed.
     */
    internal fun idempotencyKey(request: CreateDebtRequest): String {
        createAttempt?.let { (attempt, key) -> if (attempt == request) return key }
        val key = DebtIdempotencyKey.generate()
        createAttempt = request to key
        return key
    }

    // endregion

    private fun isOnline(): Boolean = connectivity.isOnline.value

    private fun plainNumber(value: Double): String =
        if (value == Math.rint(value)) value.toLong().toString() else String.format(Locale.US, "%.2f", value)

    companion object {
        const val PREVIEW_DEBOUNCE_MS = 300L
        private const val MAX_NAME_INPUT = 200
    }
}
