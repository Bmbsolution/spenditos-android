package com.spenditos.android.data.monthbalance

import com.spenditos.android.data.debts.LenientEnumSerializer
import kotlinx.serialization.Serializable

/*
 * The month-start prompt wire contract (docs/features/month-balance.md in the spenditos repo; iOS twin:
 * Domain/Models/MonthBalance.swift). Responses use the standard `{ data }` envelope (unwrapped by
 * `apiCall`). The balance is computed by the server from last month's cash flow; it is never a
 * transaction. Unknown future `kind` / `choice` values decode as "nothing to show" instead of failing.
 */

/** The user's saved answer for the month the card is shown in. */
@Serializable(with = MonthBalanceChoiceSerializer::class)
enum class MonthBalanceChoice(val wire: String) {
    DISMISSED("dismissed"),
    CARRIED("carried"),

    /** An answer this client does not know yet; treated as unanswered (iOS decodes it as nil). */
    UNKNOWN("unknown"),
}

object MonthBalanceChoiceSerializer :
    LenientEnumSerializer<MonthBalanceChoice>("MonthBalanceChoice", MonthBalanceChoice.entries, { it.wire }, MonthBalanceChoice.UNKNOWN)

@Serializable(with = MonthBalanceKindSerializer::class)
enum class MonthBalanceKind(val wire: String) {
    SHORTFALL("shortfall"),
    SURPLUS("surplus"),
    NONE("none"),
}

/** Unknown kinds fall back to NONE (nothing to show). */
object MonthBalanceKindSerializer :
    LenientEnumSerializer<MonthBalanceKind>("MonthBalanceKind", MonthBalanceKind.entries, { it.wire }, MonthBalanceKind.NONE)

/** `MonthBalanceData` (packages/types). Call [normalized] on anything decoded from the wire. */
@Serializable
data class MonthBalanceData(
    /** The month the card is shown in, `YYYY-MM`. */
    val month: String,
    /** The month that ended, `YYYY-MM`. */
    val previousMonth: String,
    val received: Double,
    val spent: Double,
    /** What that month itself carried in through an explicit "Carry over" (0 otherwise). Absent on older servers. */
    val previousCarriedIn: Double = 0.0,
    /**
     * received - spent + previousCarriedIn, rounded to cents by the server. Negative means short. Balances
     * under the currency floor come back as [MonthBalanceKind.NONE]; the client never re-derives `kind`.
     */
    val balance: Double,
    val kind: MonthBalanceKind = MonthBalanceKind.NONE,
    /** The saved answer; null while unanswered. */
    val choice: MonthBalanceChoice? = null,
    /** The debt that tracks the shortfall, once one exists. */
    val shortfallDebtId: String? = null,
    /** Whether the card should show. */
    val showPrompt: Boolean = false,
    /** What is added to "left after bills": the surplus once the user chose "Carry over", otherwise 0. */
    val carriedIn: Double = 0.0,
) {
    /** iOS decoding rules: an unknown choice is unanswered, and there is no prompt for kind NONE. */
    fun normalized(): MonthBalanceData = copy(
        choice = choice?.takeIf { it != MonthBalanceChoice.UNKNOWN },
        showPrompt = showPrompt && kind != MonthBalanceKind.NONE,
    )
}

/** Body of `PUT /month-balance`. */
@Serializable
data class SetMonthBalanceChoiceRequest(
    val month: String,
    val choice: MonthBalanceChoice,
)
