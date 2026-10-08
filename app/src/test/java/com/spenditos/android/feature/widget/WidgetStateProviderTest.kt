package com.spenditos.android.feature.widget

import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.db.SyncStatus
import com.spenditos.android.core.db.TransactionEntity
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.UserSettings
import com.spenditos.android.data.model.UserSettingsUpdate
import com.spenditos.android.data.user.UserSettingsActions
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import com.spenditos.android.feature.home.CashFlowStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WidgetStateProviderTest {
    @Test
    fun `reads the signed in user's current month from cache and ignores other accounts`() = runTest {
        val transactions = FakeTransactions(
            listOf(
                row("income", "user-1", "income", "2026-10-01", 2000.0),
                row("food", "user-1", "food", "2026-10-05", 1000.0),
                row("other-income", "user-2", "other-income", "2026-10-01", 100000.0),
            ),
        )
        val categories = FakeCategories(
            listOf(
                Category("income", "user-1", "Income", "#445566", isIncome = true),
                Category("food", "user-1", "Food", "#445566", isIncome = false),
                Category("other-income", "user-2", "Income", "#445566", isIncome = true),
            ),
        )
        val provider = provider(transactions, categories, signedIn = true)

        val snapshot = provider.snapshot()

        assertEquals(LocalDate.parse("2026-10-01"), transactions.start)
        assertEquals(LocalDate.parse("2026-10-31"), transactions.end)
        assertEquals("user-1", snapshot.userId)
        assertEquals("CAD", snapshot.currency)
        assertEquals(CashFlowStatus.State.TIGHT, snapshot.state)
        assertEquals("1000.00", snapshot.leftAfterBills?.toPlainString())
        assertEquals(22, snapshot.daysLeft)
    }

    @Test
    fun `no cached transactions show the placeholder`() = runTest {
        val snapshot = provider(FakeTransactions(emptyList()), FakeCategories(emptyList()), signedIn = true).snapshot()

        assertTrue(snapshot.isPlaceholder)
        assertEquals("user-1", snapshot.userId)
    }

    @Test
    fun `cached transactions before the category fetch lands show the placeholder`() = runTest {
        // A fresh sign-in: the transaction cache is repopulated but the categories flow's
        // first emission is still empty; nothing may be counted as an expense.
        val transactions = FakeTransactions(
            listOf(
                row("income", "user-1", "income", "2026-10-01", 2000.0),
                row("food", "user-1", "food", "2026-10-05", 1000.0),
            ),
        )

        val snapshot = provider(transactions, FakeCategories(emptyList()), signedIn = true).snapshot()

        assertTrue(snapshot.isPlaceholder)
        assertEquals(0.0, snapshot.monthIncome, 0.001)
        assertEquals(0.0, snapshot.monthExpenses, 0.001)
    }

    @Test
    fun `signed out provider never reads repositories`() = runTest {
        val transactions = FakeTransactions(emptyList())
        val categories = FakeCategories(emptyList())

        val snapshot = provider(transactions, categories, signedIn = false).snapshot()

        assertTrue(snapshot.isPlaceholder)
        assertNull(snapshot.userId)
        assertEquals(0, transactions.reads)
        assertEquals(0, categories.reads)
    }

    private fun provider(
        transactions: FakeTransactions,
        categories: FakeCategories,
        signedIn: Boolean,
    ) = WidgetStateProvider(
        transactions = transactions,
        categories = categories,
        settings = FakeSettings(),
        session = SessionManager(
            tokenStore = if (signedIn) FakeTokenStore.signedIn("user-1") else FakeTokenStore(),
            refresher = { RefreshedTokens("access", "id") },
            clock = EpochClock { NOW },
            environment = AppEnvironment(),
        ),
        clock = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneId.of("America/Toronto")),
    )

    private fun row(id: String, userId: String, categoryId: String, date: String, amount: Double) =
        TransactionEntity(
            id = id,
            userId = userId,
            amount = amount,
            categoryId = categoryId,
            description = id,
            date = date,
            mood = null,
            templateId = null,
            source = "manual",
            createdAt = null,
            updatedAt = null,
            syncStatus = SyncStatus.SYNCED,
        )

    private class FakeTransactions(private val rows: List<TransactionEntity>) : WidgetTransactions {
        var start: LocalDate? = null
        var end: LocalDate? = null
        var reads = 0
        val ranges = mutableListOf<Pair<LocalDate, LocalDate>>()

        override fun observeRange(start: LocalDate, end: LocalDate): Flow<List<TransactionEntity>> {
            // The month read comes first; the widget's last-7-days read follows it.
            if (ranges.isEmpty()) {
                this.start = start
                this.end = end
            }
            ranges += start to end
            reads++
            return flowOf(rows)
        }
    }

    private class FakeCategories(private val rows: List<Category>) : WidgetCategories {
        var reads = 0

        override fun observe(): Flow<List<Category>> {
            reads++
            return flowOf(rows)
        }
    }

    private class FakeSettings : UserSettingsActions {
        override fun currency(): Flow<String> = flowOf("CAD")
        override suspend fun refresh(): UserSettings = error("not used")
        override suspend fun updateSettings(update: UserSettingsUpdate): UserSettings = error("not used")
        override suspend fun changePassword(currentPassword: String, newPassword: String) = error("not used")
    }
}
