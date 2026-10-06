package com.spenditos.android.feature.home

import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.Transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** The Home card's monthly cash-flow meaning, ported from iOS `CashFlowStatus.swift`. */
data class CashFlowStatus(
    val received: BigDecimal,
    val expected: BigDecimal,
    val spent: BigDecimal,
    val everyday: BigDecimal,
    val billsLeft: BigDecimal,
    val billsLeftCount: Int,
    val daysElapsed: Int,
    val daysRemaining: Int,
    val today: LocalDate,
    val monthEnd: LocalDate,
) {
    val leftAfterBills: BigDecimal get() = received + expected - spent - billsLeft

    /** Average everyday spending over at least the first seven days of the month. */
    val pace: BigDecimal
        get() = everyday.divide(BigDecimal.valueOf(maxOf(daysElapsed, 7).toLong()), RATE_SCALE, RoundingMode.HALF_UP)

    /** Amount available per remaining day, counting today. */
    val affordablePace: BigDecimal
        get() = leftAfterBills.max(BigDecimal.ZERO)
            .divide(BigDecimal.valueOf(maxOf(daysRemaining, 1).toLong()), RATE_SCALE, RoundingMode.HALF_UP)

    val state: State
        get() = when {
            income.signum() <= 0 -> State.SETUP
            leftAfterBills.signum() < 0 -> State.OVER
            pace > affordablePace -> State.TIGHT
            pace > affordablePace.multiply(WATCH_THRESHOLD) -> State.WATCH
            else -> State.ON_TRACK
        }

    /** Date money runs out at the current pace, if that falls on or before month end. */
    fun runOutDate(from: LocalDate = today): LocalDate? {
        if (pace.signum() <= 0 || leftAfterBills.signum() < 0) return null
        val days = leftAfterBills.divideToIntegralValue(pace).toInt()
        return from.plusDays(days.toLong()).takeIf { it <= monthEnd }
    }

    val income: BigDecimal get() = received + expected

    /** Portion of this month's income left after spending and bills, from zero to one. */
    val incomeLeftShare: BigDecimal
        get() = if (income.signum() <= 0) {
            BigDecimal.ZERO
        } else {
            leftAfterBills.max(BigDecimal.ZERO).divide(income, RATE_SCALE, RoundingMode.HALF_UP).min(BigDecimal.ONE)
        }

    enum class State { ON_TRACK, WATCH, TIGHT, OVER, SETUP }

    enum class Bucket { RECEIVED, EXPECTED, BILL_PAID, EVERYDAY, BILL_LEFT }

    companion object {
        private const val RATE_SCALE = 12
        private val WATCH_THRESHOLD = BigDecimal("0.8")

        /** Groups a transaction using the same date, category, and bill rules as iOS. */
        fun bucket(
            transaction: Transaction,
            isIncome: Boolean,
            today: LocalDate,
            monthStart: LocalDate,
            monthEnd: LocalDate,
        ): Bucket? {
            val date = LocalDate.parse(transaction.date)
            if (date < monthStart || date > monthEnd) return null
            val isFuture = date > today
            if (isIncome) return if (isFuture) Bucket.EXPECTED else Bucket.RECEIVED
            if (isFuture) return Bucket.BILL_LEFT
            return if (transaction.templateId == null) Bucket.EVERYDAY else Bucket.BILL_PAID
        }

        /** Builds the monthly status. Income vs expense comes only from category `isIncome`. */
        fun make(transactions: List<Transaction>, categories: List<Category>, today: LocalDate): CashFlowStatus {
            val categoryIncome = categories.associate { it.id to it.isIncome }
            val monthStart = today.withDayOfMonth(1)
            val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
            var received = BigDecimal.ZERO
            var expected = BigDecimal.ZERO
            var spent = BigDecimal.ZERO
            var everyday = BigDecimal.ZERO
            var billsLeft = BigDecimal.ZERO
            var billsLeftCount = 0

            for (transaction in transactions) {
                val bucket = bucket(
                    transaction = transaction,
                    isIncome = categoryIncome[transaction.categoryId] == true,
                    today = today,
                    monthStart = monthStart,
                    monthEnd = monthEnd,
                ) ?: continue
                val amount = cashAmount(transaction.amount)
                when (bucket) {
                    Bucket.RECEIVED -> received += amount
                    Bucket.EXPECTED -> expected += amount
                    Bucket.BILL_PAID -> spent += amount
                    Bucket.EVERYDAY -> {
                        spent += amount
                        everyday += amount
                    }
                    Bucket.BILL_LEFT -> {
                        billsLeft += amount
                        billsLeftCount++
                    }
                }
            }

            return CashFlowStatus(
                received = received,
                expected = expected,
                spent = spent,
                everyday = everyday,
                billsLeft = billsLeft,
                billsLeftCount = billsLeftCount,
                daysElapsed = today.dayOfMonth,
                daysRemaining = today.lengthOfMonth() - today.dayOfMonth + 1,
                today = today,
                monthEnd = monthEnd,
            )
        }

        internal fun cashAmount(amount: Double): BigDecimal =
            BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)
    }
}
