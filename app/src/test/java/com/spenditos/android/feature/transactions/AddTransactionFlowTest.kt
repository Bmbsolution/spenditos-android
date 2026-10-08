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
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.core.sync.SyncRequester
import com.spenditos.android.data.categories.CategoriesApi
import com.spenditos.android.data.categories.CategoryRepository
import com.spenditos.android.data.model.MerchantMapping
import com.spenditos.android.data.model.TransactionMood
import com.spenditos.android.data.transactions.TransactionRepository
import com.spenditos.android.data.transactions.TransactionsApi
import com.spenditos.android.data.user.UserApi
import com.spenditos.android.data.user.UserSettingsRepository
import com.spenditos.android.testing.FakeLastUsed
import com.spenditos.android.testing.FakeMerchantMappings
import com.spenditos.android.testing.FakeRecurring
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** The add/edit form's save paths against a fake server (iOS QuickAddView + AddTransactionViewModel). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AddTransactionFlowTest {
    private val server = MockWebServer()
    private lateinit var db: SpenditosDatabase
    private val online = MutableStateFlow(true)
    private val recurring = FakeRecurring()
    private val lastUsed = FakeLastUsed()
    private var rules: List<MerchantMapping> = emptyList()
    private lateinit var transactions: TransactionRepository
    private lateinit var categories: CategoryRepository
    private lateinit var settings: UserSettingsRepository

    private inline fun <reified T : Any> unusedApi(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, _, _ -> error("unexpected network call") } as T

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        server.start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SpenditosDatabase::class.java)
            .allowMainThreadQueries().build()
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient())
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build().create(TransactionsApi::class.java)
        val session = SessionManager(FakeTokenStore.signedIn("user-1"), { RefreshedTokens("a", "b") }, EpochClock { NOW }, AppEnvironment())
        val bus = FinanceInvalidationBus()
        transactions = TransactionRepository(api, db, session, object : ConnectivityStatus { override val isOnline = online }, SyncRequester { }, bus)
        categories = CategoryRepository(unusedApi<CategoriesApi>(), db.categories(), session, bus)
        settings = UserSettingsRepository(ApplicationProvider.getApplicationContext(), unusedApi<UserApi>(), session)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.close()
        db.close()
    }

    private suspend fun newVm(): AddTransactionViewModel {
        db.categories().insert(
            listOf(
                CategoryEntity("food", "user-1", "Food & Drink", "#FF0000", null, false, 0, false),
                CategoryEntity("bills", "user-1", "Utilities", "#0000FF", null, false, 1, false),
                CategoryEntity("pay", "user-1", "Salary", "#00FF00", null, true, 2, false),
            ),
        )
        val vm = AddTransactionViewModel(
            transactions, categories, settings, recurring, FakeMerchantMappings(rules), lastUsed,
            object : ConnectivityStatus { override val isOnline = online },
        )
        vm.state.first { it.categories.size == 3 }
        return vm
    }

    private fun txn(id: String, amount: Double, date: String, category: String = "food", description: String = "Lunch", template: String? = null) =
        """{"id":"$id","userId":"user-1","categoryId":"$category","description":"$description","amount":$amount,"date":"$date"""" +
            (template?.let { ""","templateId":"$it"""" } ?: "") + "}"

    private fun list(vararg items: String) = MockResponse(code = 200, body = """{"data":{"transactions":[${items.joinToString(",")}],"pagination":{"hasMore":false}}}""")
    private fun bulkCreated(id: String) = MockResponse(code = 201, body = """{"data":{"results":[{"transaction":${txn(id, 12.5, LocalDate.now().toString())}}]}}""")
    private fun single(json: String) = MockResponse(code = 200, body = """{"data":$json}""")

    private fun take() = server.takeRequest(5, TimeUnit.SECONDS)!!

    @Test
    fun `save with nothing entered marks the missing fields and sends nothing`() = runTest {
        val vm = newVm()
        vm.save()
        val s = vm.state.first { it.showValidation }
        assertTrue(s.amountMissing)
        assertTrue(s.categoryMissing)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `last used category is suggested, a merchant rule replaces it, a hand pick is kept`() = runTest {
        lastUsed.expense = "food"
        rules = listOf(MerchantMapping("m1", "user-1", "hydro", "Hydro", categoryId = "bills"))
        val vm = newVm()
        var s = vm.state.first { it.categoryId == "food" }
        assertEquals(AddTransactionViewModel.LAST_USED_SOURCE, s.categorySuggestionSource)
        assertFalse(s.hasUnsavedChanges)

        vm.onDescriptionChange("Hydro Quebec")
        s = vm.state.first { it.categoryId == "bills" }
        assertEquals(AddTransactionViewModel.RULE_SOURCE, s.categorySuggestionSource)

        vm.onDescriptionChange("Corner store")
        vm.state.first { it.categoryId == "food" }

        vm.onCategorySelect("bills")
        vm.onDescriptionChange("Hydro")
        vm.onDescriptionChange("Something else")
        assertEquals("bills", vm.state.first { it.description == "Something else" }.categoryId)
    }

    @Test
    fun `save and add another keeps kind and date, offers undo, and undo deletes the payment`() = runTest {
        val vm = newVm()
        vm.onAmountChange("12.50")
        vm.onCategorySelect("food")
        vm.onDateChange(LocalDate.now().minusDays(1))
        server.enqueue(list()) // bill check: nothing generated nearby
        server.enqueue(bulkCreated("srv-1"))
        vm.save(andContinue = true)

        val s = vm.state.first { it.addedCount == 1 }
        assertEquals("", s.amountText)
        assertEquals(LocalDate.now().minusDays(1), s.date)
        assertEquals("srv-1", s.undo?.transactionId)
        assertEquals("food", lastUsed.expense)
        assertTrue(take().url.encodedPath.endsWith("/transactions"))
        assertTrue(take().url.encodedPath.endsWith("/transactions/bulk"))

        server.enqueue(MockResponse(code = 204))
        vm.undo()
        vm.state.first { it.addedCount == 0 }
        val delete = take()
        assertEquals("DELETE", delete.method)
        assertTrue(delete.url.encodedPath.endsWith("/transactions/srv-1"))
    }

    @Test
    fun `a failed repeat keeps the form, and Save then only retries the repeat`() = runTest {
        val vm = newVm()
        vm.onAmountChange("80")
        vm.onCategorySelect("bills")
        vm.onDescriptionChange("Internet")
        vm.onMakeRecurringChange(true)
        recurring.failCreate = true
        server.enqueue(bulkCreated("srv-2"))
        vm.save()

        val s = vm.state.first { it.error != null }
        assertEquals(AddTransactionViewModel.REPEAT_FAILED, s.error)
        assertTrue(s.paymentSaved)

        recurring.failCreate = false
        vm.save()
        assertEquals(AddTransactionEvent.Done("Saved"), vm.events.first())
        assertEquals(1, server.requestCount) // one create, never a second
        assertEquals("Internet", recurring.created.single().description)
        assertEquals("monthly", recurring.created.single().frequency)
    }

    @Test
    fun `offline with make it recurring refuses instead of dropping the repeat`() = runTest {
        online.value = false
        val vm = newVm()
        vm.onAmountChange("80")
        vm.onCategorySelect("bills")
        vm.onMakeRecurringChange(true)
        vm.save()
        assertEquals(AddTransactionViewModel.OFFLINE_REPEAT, vm.state.first { it.error != null }.error)
        assertEquals(0, server.requestCount)
        assertTrue(recurring.created.isEmpty())
    }

    @Test
    fun `a matching generated bill is offered and choosing it pays that entry`() = runTest {
        val vm = newVm()
        val today = LocalDate.now().toString()
        vm.onAmountChange("61")
        vm.onCategorySelect("bills")
        vm.onDescriptionChange("Internet")
        server.enqueue(list(txn("gen-1", 60.0, today, category = "bills", description = "Internet", template = "tpl-net")))
        vm.save()

        val candidate = vm.state.first { it.billChoices.isNotEmpty() }.billChoices.single()
        assertEquals("gen-1", candidate.entry.id)
        take()

        server.enqueue(single(txn("gen-1", 61.0, today, category = "bills", description = "Internet", template = "tpl-net")))
        vm.chooseBill(candidate)
        assertEquals(AddTransactionEvent.Done("Saved"), vm.events.first())
        val put = take()
        assertEquals("PUT", put.method)
        assertTrue(put.url.encodedPath.endsWith("/transactions/gen-1"))
        val body = SpenditosJson.parseToJsonElement(put.body!!.utf8()).jsonObject
        assertEquals("tpl-net", body["templateId"]?.jsonPrimitive?.content)
        assertEquals(61.0, body["amount"]?.jsonPrimitive?.content?.toDouble())
    }

    @Test
    fun `edit sends the whole form by PUT, clears a mood with null, and delete removes it`() = runTest {
        val vm = newVm()
        vm.startEdit(EditTarget("srv-9", 12.5, "food", "Lunch", LocalDate.of(2026, 10, 5), TransactionMood.REGRET))
        vm.state.first { it.isEditMode && it.amountText == "12.50" }
        assertFalse(vm.state.value.hasUnsavedChanges)

        vm.onMoodChange(null)
        vm.onAmountChange("14")
        assertTrue(vm.state.first { it.amountText == "14" }.hasUnsavedChanges)
        server.enqueue(single(txn("srv-9", 14.0, "2026-10-05")))
        vm.save()
        assertEquals(AddTransactionEvent.Done("Transaction updated"), vm.events.first())
        val put = take()
        assertEquals("PUT", put.method)
        val body = SpenditosJson.parseToJsonElement(put.body!!.utf8()).jsonObject
        assertTrue(body.containsKey("mood"))
        assertEquals("null", body["mood"].toString())
        assertNull(body["templateId"])

        server.enqueue(MockResponse(code = 204))
        vm.delete()
        assertEquals(AddTransactionEvent.Done("Transaction deleted"), vm.events.first())
        assertEquals("DELETE", take().method)
    }

    @Test
    fun `retry after a failed delete deletes again and never updates`() = runTest {
        val vm = newVm()
        vm.startEdit(EditTarget("srv-9", 12.5, "food", "Lunch", LocalDate.of(2026, 10, 5), null))
        vm.state.first { it.isEditMode }

        server.enqueue(MockResponse(code = 500))
        vm.delete()
        vm.state.first { it.error != null }
        assertEquals("DELETE", take().method)

        server.enqueue(MockResponse(code = 204))
        vm.retry()
        assertEquals(AddTransactionEvent.Done("Transaction deleted"), vm.events.first())
        val retried = take()
        assertEquals("DELETE", retried.method)
        assertTrue(retried.url.encodedPath.endsWith("/transactions/srv-9"))
        assertEquals(2, server.requestCount) // only the two deletes, never a PUT/POST
    }

    @Test
    fun `retry after a failed undo re-runs the undo and keeps it offered`() = runTest {
        val vm = newVm()
        vm.onAmountChange("12.50")
        vm.onCategorySelect("food")
        server.enqueue(list())
        server.enqueue(bulkCreated("srv-3"))
        vm.save(andContinue = true)
        vm.state.first { it.undo != null }
        take(); take()

        server.enqueue(MockResponse(code = 500))
        vm.undo()
        val failed = vm.state.first { it.error != null }
        assertEquals("srv-3", failed.undo?.transactionId) // the banner can run it again
        assertEquals("DELETE", take().method)

        server.enqueue(MockResponse(code = 204))
        vm.retry()
        vm.state.first { it.addedCount == 0 }
        val retried = take()
        assertEquals("DELETE", retried.method)
        assertTrue(retried.url.encodedPath.endsWith("/transactions/srv-3"))
        assertEquals(4, server.requestCount) // two saves, two deletes; retry never saves
    }
}
