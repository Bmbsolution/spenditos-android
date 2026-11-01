package com.spenditos.android.data.merchants

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import com.spenditos.android.core.auth.SessionChangedException
import com.spenditos.android.core.network.ApiError

/**
 * Friendly copy for a failed rule request (iOS `APIError.errorDescription` wording for the generic cases). The
 * backend names its conflicts in the envelope's `error` field, which `ApiError.Server.serverMessage` carries:
 * `RuleExists`, `CategoryBeingDeleted`, `CategoryNotFound` (400), `SkipRuleCannotApply`, `NotFound`.
 */
object RuleErrors {
    val RULE_EXISTS: String get() = Strings.get(R.string.data_merchants_you_already_have_a_rule)
    val CATEGORY_BEING_DELETED: String get() = Strings.get(R.string.data_merchants_that_category_is_being_deleted)
    val CATEGORY_NOT_FOUND: String get() = Strings.get(R.string.data_merchants_that_category_no_longer_exists)
    val SKIP_CANNOT_APPLY: String get() = Strings.get(R.string.data_merchants_skip_rules_don_t_change)
    val RULE_NOT_FOUND: String get() = Strings.get(R.string.data_merchants_that_rule_no_longer_exists)
    val OFFLINE: String get() = Strings.get(R.string.data_merchants_unable_to_connect_please_check)
    val SERVER: String get() = Strings.get(R.string.data_merchants_we_re_having_trouble_reaching)
    val SESSION: String get() = Strings.get(R.string.data_merchants_session_expired_please_log_in)
    val TOO_MANY: String get() = Strings.get(R.string.data_merchants_too_many_requests_please_wait)
    val INVALID: String get() = Strings.get(R.string.data_merchants_that_doesn_t_look_right)
    val GENERIC: String get() = Strings.get(R.string.auth_something_went_wrong_please_try)
    val ACCOUNT_CHANGED: String get() = Strings.get(R.string.data_merchants_the_signed_in_account_changed)

    fun message(error: Throwable): String = when (error) {
        is ApiError.Network -> if (error.cause is SessionChangedException) ACCOUNT_CHANGED else OFFLINE
        is ApiError.Unauthorized -> SESSION
        is ApiError.Server -> server(error)
        else -> GENERIC
    }

    private fun server(error: ApiError.Server): String {
        val code = error.serverMessage
        return when {
            error.status >= 500 -> SERVER
            error.status == 401 -> SESSION
            code == "RuleExists" -> RULE_EXISTS
            code == "CategoryBeingDeleted" -> CATEGORY_BEING_DELETED
            code == "CategoryNotFound" -> CATEGORY_NOT_FOUND
            code == "SkipRuleCannotApply" -> SKIP_CANNOT_APPLY
            code == "ValidationError" -> INVALID
            error.status == 404 -> RULE_NOT_FOUND
            error.status == 429 -> TOO_MANY
            else -> GENERIC
        }
    }
}
