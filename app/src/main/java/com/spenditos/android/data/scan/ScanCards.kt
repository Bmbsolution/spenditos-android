package com.spenditos.android.data.scan

import com.spenditos.android.data.model.ScanStatus
import com.spenditos.android.data.model.ScanSummary

/** "1 transaction" / "3 transactions" (iOS `Int.counted`). */
fun Int.counted(noun: String): String = if (this == 1) "$this $noun" else "$this ${noun}s"

/** What one Home card shows: a pure mapping from a [ScanSummary] so the copy and ordering are unit-testable (iOS `ScanCardState`). */
data class ScanCardState(
    val scanId: String,
    val phase: Phase,
    val title: String,
    val subtitle: String?,
) {
    enum class Phase { READING, READY }

    companion object {
        /** Most cards Home shows at once. */
        const val MAX_CARDS = 3

        /**
         * processing -> "Reading {sourceLabel}…"; ready -> "{n} transactions ready to review". Anything else (failed,
         * saved, discarded, unknown) and anything in [hidden] produces no card. Ready cards come first.
         */
        fun cards(scans: List<ScanSummary>, hidden: Set<String> = emptySet()): List<ScanCardState> {
            val ready = scans.filter { it.state == ScanStatus.READY && it.scanId !in hidden }.map(::card)
            val reading = scans.filter { it.state == ScanStatus.PROCESSING && it.scanId !in hidden }.map(::card)
            return (ready + reading).take(MAX_CARDS)
        }

        fun card(scan: ScanSummary): ScanCardState {
            val label = scan.sourceLabel.trim()
            return if (scan.state == ScanStatus.READY) {
                ScanCardState(
                    scanId = scan.scanId,
                    phase = Phase.READY,
                    title = "${scan.itemCount.counted("transaction")} ready to review",
                    subtitle = label.takeIf { it.isNotEmpty() }?.let { "From $it" },
                )
            } else {
                val progress = scan.progress.takeIf { it.total > 0 }?.let { "${minOf(it.done, it.total)} of ${it.total}" }
                ScanCardState(
                    scanId = scan.scanId,
                    phase = Phase.READING,
                    title = if (label.isEmpty()) "Reading your scan…" else "Reading $label…",
                    subtitle = progress,
                )
            }
        }
    }
}
