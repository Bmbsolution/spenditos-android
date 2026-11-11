package com.spenditos.android.data.restore

import com.spenditos.android.data.debts.LenientEnumSerializer
import kotlinx.serialization.Serializable

/**
 * Backup restore wire models (spenditos `packages/types/src/api/account-backup.ts`, docs/features/backup-restore.md).
 * Server-owned enums decode leniently: a value this client does not know becomes `UNKNOWN`.
 */

/** `uploading -> validating -> restoring -> done | failed`; `expired` is an upload left unfinished for 24 h. */
@Serializable(with = RestoreSessionStatusSerializer::class)
enum class RestoreSessionStatus(val wire: String) {
    UPLOADING("uploading"),
    VALIDATING("validating"),
    RESTORING("restoring"),
    DONE("done"),
    FAILED("failed"),
    EXPIRED("expired"),
    UNKNOWN("unknown"),
}

object RestoreSessionStatusSerializer : LenientEnumSerializer<RestoreSessionStatus>(
    "RestoreSessionStatus", RestoreSessionStatus.entries, { it.wire }, RestoreSessionStatus.UNKNOWN,
)

/** Why a restore failed; unknown codes fall back to a generic message. */
@Serializable(with = RestoreErrorCodeSerializer::class)
enum class RestoreErrorCode(val wire: String) {
    CHECKSUM_MISMATCH("ChecksumMismatch"),
    INVALID_BACKUP_FILE("InvalidBackupFile"),
    UNSUPPORTED_BACKUP_VERSION("UnsupportedBackupVersion"),
    ACCOUNT_NOT_EMPTY("AccountNotEmpty"),
    RESTORE_FAILED("RestoreFailed"),
    UNKNOWN("unknown"),
}

object RestoreErrorCodeSerializer : LenientEnumSerializer<RestoreErrorCode>(
    "RestoreErrorCode", RestoreErrorCode.entries, { it.wire }, RestoreErrorCode.UNKNOWN,
)

/** The part of the restore that is running. */
@Serializable(with = RestoreProgressPhaseSerializer::class)
enum class RestoreProgressPhase(val wire: String) {
    SETTINGS("settings"),
    CATEGORIES("categories"),
    INCOME_AND_BILLS("incomeAndBills"),
    DEBTS("debts"),
    RULES("rules"),
    TRANSACTIONS("transactions"),
    MONTH_BALANCE_CHOICES("monthBalanceChoices"),
    UNKNOWN("unknown"),
}

object RestoreProgressPhaseSerializer : LenientEnumSerializer<RestoreProgressPhase>(
    "RestoreProgressPhase", RestoreProgressPhase.entries, { it.wire }, RestoreProgressPhase.UNKNOWN,
)

@Serializable
data class RestoreProgress(
    val phase: RestoreProgressPhase = RestoreProgressPhase.UNKNOWN,
    val done: Int = 0,
    val total: Int = 0,
)

/** What a finished restore wrote (or found already there on a re-run). */
@Serializable
data class RestoreSummary(
    val categories: Int = 0,
    val incomeAndBills: Int = 0,
    val debts: Int = 0,
    val rules: Int = 0,
    val transactions: Int = 0,
    val monthBalanceChoices: Int = 0,
    val categoriesMerged: Int = 0,
)

@Serializable
data class RestoreFailure(
    val code: RestoreErrorCode = RestoreErrorCode.UNKNOWN,
    val message: String = "",
)

@Serializable
data class RestoreSession(
    val id: String,
    val status: RestoreSessionStatus = RestoreSessionStatus.UNKNOWN,
    val totalBytes: Long = 0,
    val partSize: Int = 0,
    val partCount: Int = 0,
    /** Indexes (0-based) the server has stored, ascending. */
    val receivedParts: List<Int> = emptyList(),
    val createdAt: String = "",
    val updatedAt: String = "",
    val expiresAt: String = "",
    val progress: RestoreProgress? = null,
    val summary: RestoreSummary? = null,
    val error: RestoreFailure? = null,
)

/** `POST /user/restore` body. */
@Serializable
data class StartRestoreRequest(val totalBytes: Long, val sha256: String)
