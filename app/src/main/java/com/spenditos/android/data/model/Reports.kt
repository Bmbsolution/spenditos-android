@file:kotlinx.serialization.UseSerializers(com.spenditos.android.data.model.LocalDateIsoSerializer::class)

package com.spenditos.android.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate

/** `DashboardData` packages/types/src/api/index.ts:741. Only Reports screen fields are decoded. */
@Serializable
data class DashboardData(
    val dateRange: ReportDateRange? = null,
    val summary: DashboardSummary? = null,
    val categoryBreakdown: List<CategorySummary> = emptyList(),
    val totalTransactionCount: Int = 0,
)

/** `DashboardData.summary` packages/types/src/api/index.ts:752. */
@Serializable
data class DashboardSummary(
    val totalIncome: Double,
    val totalExpenses: Double,
    val netAmount: Double,
)

/** `CategorySummary` packages/types/src/api/index.ts:725. Legacy budget fields are not decoded. */
@Serializable
data class CategorySummary(
    val categoryId: String,
    val categoryName: String,
    val categoryColor: String? = null,
    val categoryIcon: String? = null,
    val totalAmount: Double,
    val transactionCount: Int,
    val isIncome: Boolean,
)

/** Inclusive date range used by the Reports endpoints, packages/types/src/api/index.ts:745. */
@Serializable
data class ReportDateRange(
    val startDate: LocalDate,
    val endDate: LocalDate,
)

/** `DailyAggregate` packages/types/src/api/index.ts:891. */
@Serializable
data class DailyAggregate(
    val date: LocalDate,
    val income: Double,
    val expenses: Double,
    val transactionCount: Int,
)

/** Inner `{ data: [...] }` returned by the daily handler at packages/functions/src/api/reports.ts:450. */
@Serializable
data class DailyAggregatesPayload(val data: List<DailyAggregate> = emptyList())

/** `PaycheckPeriodsData` packages/types/src/api/index.ts:869. */
@Serializable
data class PaycheckPeriodsData(
    val dateRange: ReportDateRange,
    val periods: List<PaycheckPeriod> = emptyList(),
)

/** `PaycheckPeriod` packages/types/src/api/index.ts:832. */
@Serializable
data class PaycheckPeriod(
    val id: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val label: String,
    val income: PaycheckPeriodTotal,
    val expenses: PaycheckPeriodTotal,
    val coverageStatus: CoverageStatus? = null,
)

/** `PeriodIncome` / `PeriodExpenses` packages/types/src/api/index.ts:805-823. */
@Serializable
data class PaycheckPeriodTotal(val total: Double)

/** `CoverageStatus` packages/types/src/api/index.ts:827. */
@Serializable
enum class CoverageStatus {
    @SerialName("covered") COVERED,
    @SerialName("gap") GAP,
    @SerialName("exact") EXACT,
}

/** ISO date strings are calendar dates; encode and decode without a time zone conversion. */
object LocalDateIsoSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}
