package com.spenditos.android.data.merchants

import com.spenditos.android.core.auth.SessionChangedException
import com.spenditos.android.core.network.ApiError

/**
 * Friendly copy for a failed rule request (iOS `APIError.errorDescription` wording for the generic cases). The
 * backend names its conflicts in the envelope's `error` field, which `ApiError.Server.serverMessage` carries:
 * `RuleExists`, `CategoryBeingDeleted`, `CategoryNotFound` (400), `SkipRuleCannotApply`, `NotFound`.
 */
object RuleErrors {
    const val RULE_EXISTS = "You already have a rule for that merchant."
    const val CATEGORY_BEING_DELETED = "That category is being deleted. Pick another one."
    const val CATEGORY_NOT_FOUND = "That category no longer exists. Pick another one."
    const val SKIP_CANNOT_APPLY = "Skip rules don't change past transactions."
    const val RULE_NOT_FOUND = "That rule no longer exists. Pull down to refresh."
    const val OFFLINE = "Unable to connect. Please check your internet connection and try again."
    const val SERVER = "We're having trouble reaching the server. Please try again later."
    const val SESSION = "Session expired. Please log in again."
    const val TOO_MANY = "Too many requests. Please wait a moment and try again."
    const val INVALID = "That doesn't look right. Check the merchant name and try again."
    const val GENERIC = "Something went wrong. Please try again."
    const val ACCOUNT_CHANGED = "The signed-in account changed, so nothing was saved."

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
