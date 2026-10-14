package com.spenditos.android.feature.transactions

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.db.CategoryEntity
import com.spenditos.android.core.db.SpenditosDatabase
import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.core.sync.SyncRequester
import com.spenditos.android.data.categories.CategoriesApi
import com.spenditos.android.data.categories.CategoryRepository
import com.spenditos.android.data.transactions.TransactionRepository
import com.spenditos.android.data.transactions.TransactionsApi
import com.spenditos.android.data.user.UserApi
import com.spenditos.android.data.user.UserSettingsRepository
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import java.lang.reflect.Proxy
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AddTransactionPrefillTest {
    private lateinit var db: SpenditosDatabase
    private lateinit var vm: AddTransactionViewModel

    /** Never called: these tests only exercise form state. */
    private inline fun <reified T : Any> unusedApi(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, _, _ -> error("unexpected network call") } as T

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SpenditosDatabase::class.java)
            .allowMainThreadQueries().build()
        val session = SessionManager(FakeTokenStore.signedIn("user-1"), { RefreshedTokens("a", "b") }, EpochClock { NOW }, AppEnvironment())
        val bus = FinanceInvalidationBus()
        val transactions = TransactionRepository(
            unusedApi<TransactionsApi>(), db, session,
            object : ConnectivityStatus { override val isOnline = MutableStateFlow(true) },
            SyncRequester { }, bus,
        )
        vm = AddTransactionViewModel(
            transactions,
            CategoryRepository(unusedApi<CategoriesApi>(), db.categories(), session, bus),
            UserSettingsRepository(ApplicationProvider.getApplicationContext(), unusedApi<UserApi>(), session, com.spenditos.android.testing.FakeProAccess()),
            com.spenditos.android.testing.FakeRecurring(),
            com.spenditos.android.testing.FakeMerchantMappings(),
            com.spenditos.android.testing.FakeLastUsed(),
            object : ConnectivityStatus { override val isOnline = MutableStateFlow(true) },
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private suspend fun seed() {
        db.categories().insert(
            listOf(
                CategoryEntity("food", "user-1", "Food & Drink", "#FF0000", null, false, 0, false),
                CategoryEntity("pay", "user-1", "Salary", "#00FF00", null, true, 1, false),
            ),
        )
        vm.state.first { it.categories.size == 2 }
    }

    @Test
    fun `prefill applies amount, description, date and the matching category`() = runTest {
        seed()
        vm.applyPrefill(AddTransactionPrefill(amount = 23.4, description = "Cafe", date = "2026-09-30", categoryId = "food"))
        val s = vm.state.first { it.categoryId == "food" }
        assertEquals("23.40", s.amountText)
        assertEquals("Cafe", s.description)
        assertEquals(LocalDate.of(2026, 9, 30), s.date)
        assertEquals(EntryType.EXPENSE, s.type)
    }

    @Test
    fun `an exhausted sentence fills the description and amount and flags the smart entries banner`() = runTest {
        seed()
        val fallback = com.spenditos.android.data.quota.SentenceManualFallback.from("  groceries 42.10 at Costco  ")
        vm.applyPrefill(
            AddTransactionPrefill(
                amount = fallback.amount?.toDouble(),
                description = fallback.description,
                smartEntriesUsed = true,
            ),
        )
        val s = vm.state.first { it.smartEntriesUsed }
        assertEquals("42.10", s.amountText)
        assertEquals("groceries 42.10 at Costco", s.description)
        assertNull("no 'Suggested from' banner for a typed sentence", s.assistedEntrySource)
        assertEquals("type and category are left alone", EntryType.EXPENSE, s.type)
        assertNull(s.categoryId)
    }

    @Test
    fun `a plain prefill does not flag the smart entries banner`() = runTest {
        seed()
        vm.applyPrefill(AddTransactionPrefill(amount = 5.0))
        assertEquals(false, vm.state.first { it.amountText == "5.00" }.smartEntriesUsed)
    }

    @Test
    fun `an unknown category id falls back to a case-insensitive name hint, and income flips the type`() = runTest {
        seed()
        vm.applyPrefill(AddTransactionPrefill(categoryId = "nope", categoryHint = " salary "))
        val s = vm.state.first { it.categoryId == "pay" }
        assertEquals(EntryType.INCOME, s.type)
    }

    @Test
    fun `invalid values are ignored and nothing is overwritten`() = runTest {
        seed()
        val before = vm.state.value
        vm.applyPrefill(AddTransactionPrefill(amount = -5.0, date = "not-a-date", categoryId = "nope", categoryHint = "zzz"))
        val after = vm.state.value
        assertEquals(before.amountText, after.amountText)
        assertEquals(before.date, after.date)
        assertNull(after.categoryId)
    }
}
