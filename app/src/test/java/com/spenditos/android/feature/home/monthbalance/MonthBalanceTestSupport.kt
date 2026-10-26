package com.spenditos.android.feature.home.monthbalance

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.monthbalance.MonthBalanceActions
import com.spenditos.android.data.monthbalance.MonthBalanceChoice
import com.spenditos.android.data.monthbalance.MonthBalanceData
import com.spenditos.android.data.monthbalance.MonthBalanceKind

/** In-memory [MonthBalanceActions] (iOS `MockMonthBalanceService`): stub the results, then read back what was asked. */
class FakeMonthBalanceActions : MonthBalanceActions {
    var getResult: Result<MonthBalanceData> = Result.success(shortfall)
    var setResult: Result<MonthBalanceData>? = null

    /** Runs inside [get] before it answers, to hold a request "in flight". */
    var getGate: (suspend () -> Unit)? = null
    var setGate: (suspend () -> Unit)? = null

    val getMonths = mutableListOf<String>()
    val setCalls = mutableListOf<Pair<String, MonthBalanceChoice>>()

    override suspend fun get(month: String): MonthBalanceData {
        getMonths += month
        getGate?.invoke()
        return getResult.getOrThrow()
    }

    override suspend fun set(month: String, choice: MonthBalanceChoice): MonthBalanceData {
        setCalls += month to choice
        setGate?.invoke()
        return (setResult ?: Result.failure(ApiError.NoData)).getOrThrow()
    }

    companion object {
        /** September ended $200 short, asked in October. */
        val shortfall = MonthBalanceData(
            month = "2026-10", previousMonth = "2026-09", received = 3000.0, spent = 3200.0, balance = -200.0,
            kind = MonthBalanceKind.SHORTFALL, showPrompt = true,
        )

        /** September ended $150 ahead, asked in October. */
        val surplus = MonthBalanceData(
            month = "2026-10", previousMonth = "2026-09", received = 3000.0, spent = 2850.0, balance = 150.0,
            kind = MonthBalanceKind.SURPLUS, showPrompt = true,
        )

        /** October ended $250 ahead: its own +$100 plus the +$150 it carried in. */
        val chained = MonthBalanceData(
            month = "2026-11", previousMonth = "2026-10", received = 3000.0, spent = 2900.0, previousCarriedIn = 150.0,
            balance = 250.0, kind = MonthBalanceKind.SURPLUS, showPrompt = true,
        )

        val carried = MonthBalanceData(
            month = "2026-10", previousMonth = "2026-09", received = 3000.0, spent = 2850.0, balance = 150.0,
            kind = MonthBalanceKind.SURPLUS, choice = MonthBalanceChoice.CARRIED, showPrompt = false, carriedIn = 150.0,
        )

        val dismissedShortfall = shortfall.copy(choice = MonthBalanceChoice.DISMISSED, showPrompt = false)
    }
}
