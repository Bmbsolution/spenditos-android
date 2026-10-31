package com.spenditos.android.feature.home.insights

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import com.spenditos.android.feature.home.CashFlowStatus
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.Transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Insights drawn from the same month of transactions as Home's cash-flow status. */
data class HomeInsights(
    val monthStart: LocalDate,
    val monthEnd: LocalDate,
    val today: LocalDate,
    val points: List<DayPoint>,
    val pins: List<Pin>,
    val week: List<WeekDay>,
    val topCategory: TopCategory?,
    val nextBill: Pin?,
) {
    /** A running total for one day. Actual values end today; projected values start today. */
    data class DayPoint(
        val date: LocalDate,
        val moneyIn: BigDecimal,
        val moneyOut: BigDecimal,
        val isProjected: Boolean,
    ) {
        val gap: BigDecimal get() = moneyIn - moneyOut
    }

    /** A bill or income entry pinned to its cumulative line total. */
    data class Pin(
        val id: String,
        val date: LocalDate,
        val name: String,
        val amount: BigDecimal,
        val isIncome: Boolean,
        val isPast: Boolean,
        val icon: String?,
        val categoryName: String?,
        val colorHex: String?,
        val level: BigDecimal,
    )

    data class Shortfall(val start: LocalDate, val end: LocalDate, val amount: BigDecimal)

    data class WeekDay(val date: LocalDate, val amount: BigDecimal, val isToday: Boolean)

    data class TopCategory(
        val name: String,
        val icon: String?,
        val colorHex: String?,
        val amount: BigDecimal,
        val share: BigDecimal,
    )

    val monthIn: BigDecimal get() = points.lastOrNull()?.moneyIn ?: BigDecimal.ZERO
    val monthOut: BigDecimal get() = points.lastOrNull()?.moneyOut ?: BigDecimal.ZERO
    val monthEndGap: BigDecimal get() = monthIn - monthOut
    val weekTotal: BigDecimal get() = week.fold(BigDecimal.ZERO) { total, day -> total + day.amount }

    /** The current or next continuous stretch where out is ahead of in. */
    val shortfall: Shortfall?
        get() {
            val days = points.filter { it.date >= today && (it.isProjected || it.date == today) }
            val first = days.indexOfFirst { it.gap < SHORTFALL_EPSILON.negate() }
            if (first < 0) return null
            var last = first
            while (last + 1 < days.size && days[last + 1].gap < SHORTFALL_EPSILON.negate()) last++
            val deepest = days.subList(first, last + 1).minOf { it.gap }
            return Shortfall(days[first].date, days[last].date, deepest.negate())
        }

    companion object {
        private val SHORTFALL_EPSILON = BigDecimal("0.005")
        private val SHARE_SCALE = 8

        /** Mirrors iOS `Features/Dashboard/HomeInsights.swift` using calendar dates and decimal sums. */
        fun make(transactions: List<Transaction>, categories: List<Category>, today: LocalDate): HomeInsights {
            val categoryById = categories.associateBy { it.id }
            fun isIncome(transaction: Transaction) = categoryById[transaction.categoryId]?.isIncome == true
            fun dayOf(transaction: Transaction) = LocalDate.parse(transaction.date)

            val monthStart = today.withDayOfMonth(1)
            val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
            val inMonth = transactions.filter { dayOf(it) in monthStart..monthEnd }
            val byDay = inMonth.groupBy(::dayOf)
            val inByDay = mutableMapOf<LocalDate, BigDecimal>()
            val outByDay = mutableMapOf<LocalDate, BigDecimal>()
            var moneyIn = BigDecimal.ZERO
            var moneyOut = BigDecimal.ZERO
            val points = mutableListOf<DayPoint>()
            var day = monthStart
            while (day <= monthEnd) {
                byDay[day].orEmpty().forEach { transaction ->
                    val amount = CashFlowStatus.cashAmount(transaction.amount)
                    if (isIncome(transaction)) moneyIn += amount else moneyOut += amount
                }
                inByDay[day] = moneyIn
                outByDay[day] = moneyOut
                if (day <= today) points += DayPoint(day, moneyIn, moneyOut, isProjected = false)
                if (day >= today) points += DayPoint(day, moneyIn, moneyOut, isProjected = true)
                day = day.plusDays(1)
            }

            val pins = inMonth.asSequence()
                .filter { it.templateId != null || dayOf(it) > today }
                .map { transaction ->
                    val transactionDay = dayOf(transaction)
                    val category = categoryById[transaction.categoryId]
                    val income = isIncome(transaction)
                    Pin(
                        id = transaction.id,
                        date = transactionDay,
                        name = transaction.description.ifEmpty { category?.name ?: Strings.get(R.string.common_bill) },
                        amount = CashFlowStatus.cashAmount(transaction.amount),
                        isIncome = income,
                        isPast = transactionDay <= today,
                        icon = category?.icon,
                        categoryName = category?.name,
                        colorHex = category?.color,
                        level = (if (income) inByDay else outByDay)[transactionDay] ?: BigDecimal.ZERO,
                    )
                }
                .sortedWith(compareBy<Pin> { it.date }.thenBy { it.id })
                .toList()

            // The iOS week tile uses the last seven calendar dates and everyday (unlinked) expenses.
            val everyday = transactions.filter { !isIncome(it) && it.templateId == null }
            val week = (6 downTo 0).map { back ->
                val date = today.minusDays(back.toLong())
                val amount = everyday.asSequence()
                    .filter { dayOf(it) == date }
                    .fold(BigDecimal.ZERO) { total, transaction -> total + CashFlowStatus.cashAmount(transaction.amount) }
                WeekDay(date, amount, isToday = back == 0)
            }

            val totalsByCategory = linkedMapOf<String, BigDecimal>()
            val sampleByCategory = mutableMapOf<String, Transaction>()
            var everydayTotal = BigDecimal.ZERO
            everyday.forEach { transaction ->
                val transactionDay = dayOf(transaction)
                if (transactionDay < monthStart || transactionDay > today) return@forEach
                val amount = CashFlowStatus.cashAmount(transaction.amount)
                totalsByCategory[transaction.categoryId] = (totalsByCategory[transaction.categoryId] ?: BigDecimal.ZERO) + amount
                sampleByCategory[transaction.categoryId] = transaction
                everydayTotal += amount
            }
            val largest = totalsByCategory.maxByOrNull { it.value }
            val topCategory = if (everydayTotal.signum() > 0 && largest != null) {
                val category = sampleByCategory[largest.key]?.let { categoryById[it.categoryId] }
                TopCategory(
                    name = category?.name ?: Strings.get(R.string.common_other),
                    icon = category?.icon,
                    colorHex = category?.color,
                    amount = largest.value,
                    share = largest.value.divide(everydayTotal, SHARE_SCALE, RoundingMode.HALF_UP),
                )
            } else {
                null
            }

            return HomeInsights(
                monthStart = monthStart,
                monthEnd = monthEnd,
                today = today,
                points = points,
                pins = pins,
                week = week,
                topCategory = topCategory,
                nextBill = pins.firstOrNull { !it.isIncome && it.date > today },
            )
        }

    }
}
