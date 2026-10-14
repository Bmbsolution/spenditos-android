package com.spenditos.android.data.merchants

import com.spenditos.android.core.auth.SessionChangedException
import com.spenditos.android.core.network.ApiError
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class RuleErrorsTest {
    private fun server(status: Int, code: String) = ApiError.Server(status, code)

    @Test
    fun `the backend's conflicts read as plain sentences`() {
        assertEquals(RuleErrors.RULE_EXISTS, RuleErrors.message(server(409, "RuleExists")))
        assertEquals(RuleErrors.CATEGORY_BEING_DELETED, RuleErrors.message(server(409, "CategoryBeingDeleted")))
        assertEquals(RuleErrors.CATEGORY_NOT_FOUND, RuleErrors.message(server(400, "CategoryNotFound")))
        assertEquals(RuleErrors.SKIP_CANNOT_APPLY, RuleErrors.message(server(409, "SkipRuleCannotApply")))
        assertEquals(RuleErrors.INVALID, RuleErrors.message(server(400, "ValidationError")))
        assertEquals(RuleErrors.RULE_NOT_FOUND, RuleErrors.message(server(404, "NotFound")))
    }

    @Test
    fun `generic failures use the iOS wording`() {
        assertEquals(RuleErrors.OFFLINE, RuleErrors.message(ApiError.Network(IOException("x"))))
        assertEquals(RuleErrors.ACCOUNT_CHANGED, RuleErrors.message(ApiError.Network(SessionChangedException())))
        assertEquals(RuleErrors.SESSION, RuleErrors.message(ApiError.Unauthorized))
        assertEquals(RuleErrors.SESSION, RuleErrors.message(server(401, "Unauthorized")))
        assertEquals(RuleErrors.SERVER, RuleErrors.message(server(503, "x")))
        assertEquals(RuleErrors.TOO_MANY, RuleErrors.message(server(429, "RateLimited")))
        assertEquals(RuleErrors.GENERIC, RuleErrors.message(server(418, "teapot")))
        assertEquals(RuleErrors.GENERIC, RuleErrors.message(IllegalStateException("x")))
    }

    @Test
    fun `server errors never leak the raw code to the user`() {
        for (code in listOf("RuleExists", "CategoryBeingDeleted", "CategoryNotFound", "SkipRuleCannotApply", "ValidationError", "NotFound")) {
            val text = RuleErrors.message(server(409, code))
            assert(!text.contains(code)) { text }
        }
    }
}
