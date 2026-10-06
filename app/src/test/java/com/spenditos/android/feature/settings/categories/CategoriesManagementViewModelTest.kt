package com.spenditos.android.feature.settings.categories

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.db.SpenditosDatabase
import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.categories.CategoriesApi
import com.spenditos.android.data.categories.CategoryRepository
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.CategoryCreateRequest
import com.spenditos.android.data.model.CategoryUpdateRequest
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class CategoriesManagementViewModelTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    class MainDispatcherRule : TestWatcher() {
        val dispatcher: TestDispatcher = StandardTestDispatcher()
        override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
        override fun finished(description: Description) = Dispatchers.resetMain()
    }

    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val graphs = mutableListOf<TestGraph>()

    @After
    fun tearDown() {
        graphs.forEach { it.database.close() }
    }

    private data class TestGraph(
        val database: SpenditosDatabase,
        val api: FakeCategoriesApi,
        val repository: CategoryRepository,
        val invalidations: FinanceInvalidationBus,
    )

    private fun graph(rows: List<Category> = emptyList()): TestGraph {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            SpenditosDatabase::class.java,
        ).allowMainThreadQueries().build()
        val api = FakeCategoriesApi(rows.toMutableList())
        val session = SessionManager(
            FakeTokenStore.signedIn("user-1"),
            { RefreshedTokens("access", "id") },
            EpochClock { NOW },
            AppEnvironment(),
        )
        val bus = FinanceInvalidationBus()
        return TestGraph(database, api, CategoryRepository(api, database.categories(), session, bus), bus).also(graphs::add)
    }

    private fun category(id: String = "c1", name: String = "Fake Food", isIncome: Boolean = false) =
        Category(id, "user-1", name, "#B85F42", "🍲", isIncome)

    private suspend fun loaded(vm: CategoriesManagementViewModel) = vm.state.first { !it.isLoading }

    private fun failures() = listOf(
        ApiError.Server(400, "Validation failed"),
        ApiError.Network(IOException("offline")),
    )

    @Test
    fun `initial refresh loads categories and reports server or network failure`() = runTest {
        val success = graph(listOf(category(), category("c2", "Fake Salary", true)))
        val vm = CategoriesManagementViewModel(success.repository, success.invalidations)
        val loaded = loaded(vm)
        assertEquals(2, loaded.categories.size)
        assertFalse(loaded.isOffline)

        failures().forEach { failure ->
            val failedGraph = graph().apply { api.failureFor = "list"; api.failure = failure }
            val failedVm = CategoriesManagementViewModel(failedGraph.repository, failedGraph.invalidations)
            val state = loaded(failedVm)
            assertTrue(state.error != null)
            assertEquals(failure is ApiError.Network, state.isOffline)
        }
    }

    @Test
    fun `duplicate check folds case and accents before create`() = runTest {
        val graph = graph(listOf(category(name = "Café")))
        val vm = CategoriesManagementViewModel(graph.repository, graph.invalidations)
        loaded(vm)
        vm.saveCategory(null, CategoryDraft("CAFE\u0301", "#B85F42", null, false))
        runCurrent()
        assertEquals(0, graph.api.createCalls)
        assertTrue(vm.state.value.error!!.contains("Café"))
    }

    @Test
    fun `create and edit succeed and update category cache`() = runTest {
        val graph = graph(listOf(category()))
        val vm = CategoriesManagementViewModel(graph.repository, graph.invalidations)
        loaded(vm)

        val saved = async { vm.events.first() }
        vm.saveCategory(null, CategoryDraft("Fake Salary", "#3F7D70", "💰", true))
        runCurrent()
        assertEquals(CategoriesManagementEvent.Saved, saved.await())
        assertTrue(vm.state.first { s -> s.categories.any { it.name == "Fake Salary" && it.isIncome } }.categories.isNotEmpty()) // Room emits on its own executor
        assertEquals(1, graph.api.createCalls)

        val updated = async { vm.events.first() }
        vm.saveCategory("c1", CategoryDraft("Fake Dining", "#B86A6A", null, true))
        runCurrent()
        assertEquals(CategoriesManagementEvent.Saved, updated.await())
        assertTrue(vm.state.first { s -> s.categories.any { it.id == "c1" && it.name == "Fake Dining" && it.isIncome } }.categories.isNotEmpty())
        assertEquals(1, graph.api.updateCalls)
    }

    @Test
    fun `create and edit show server and offline errors`() = runTest {
        failures().forEach { failure ->
            val graph = graph()
            val vm = CategoriesManagementViewModel(graph.repository, graph.invalidations)
            loaded(vm)
            graph.api.failureFor = "create"
            graph.api.failure = failure
            vm.saveCategory(null, CategoryDraft("Fake New", "#B85F42", null, false))
            runCurrent()
            assertTrue(vm.state.value.error != null)
            assertEquals(failure is ApiError.Network, vm.state.value.isOffline)
        }

        failures().forEach { failure ->
            val graph = graph(listOf(category()))
            val vm = CategoriesManagementViewModel(graph.repository, graph.invalidations)
            loaded(vm)
            graph.api.failureFor = "update"
            graph.api.failure = failure
            vm.saveCategory("c1", CategoryDraft("Fake Changed", "#B85F42", null, false))
            runCurrent()
            assertTrue(vm.state.value.error != null)
            assertEquals(failure is ApiError.Network, vm.state.value.isOffline)
        }
    }

    @Test
    fun `delete updates cache and shows server or offline errors`() = runTest {
        val success = graph(listOf(category()))
        val vm = CategoriesManagementViewModel(success.repository, success.invalidations)
        loaded(vm)
        val deleted = async { vm.events.first() }
        vm.deleteCategory("c1")
        runCurrent()
        assertEquals(CategoriesManagementEvent.Deleted, deleted.await())
        assertTrue(vm.state.first { it.categories.isEmpty() }.categories.isEmpty()) // Room emits on its own executor

        failures().forEach { failure ->
            val failedGraph = graph(listOf(category()))
            val failedVm = CategoriesManagementViewModel(failedGraph.repository, failedGraph.invalidations)
            loaded(failedVm)
            failedGraph.api.failureFor = "delete"
            failedGraph.api.failure = failure
            failedVm.deleteCategory("c1")
            runCurrent()
            assertTrue(failedVm.state.value.error != null)
            assertEquals(failure is ApiError.Network, failedVm.state.value.isOffline)
        }
    }

    @Test
    fun `delete of a category in use explains the conflict`() = runTest {
        val graph = graph(listOf(category()))
        val vm = CategoriesManagementViewModel(graph.repository, graph.invalidations)
        loaded(vm)
        graph.api.failureFor = "delete"
        graph.api.failure = ApiError.Server(409, "CategoryHasReferences")
        vm.deleteCategory("c1")
        runCurrent()
        assertTrue(vm.state.first { it.error != null }.error!!.startsWith("This category is still used"))
        assertEquals(1, vm.state.value.categories.size)
    }

    private inner class FakeCategoriesApi(var rows: MutableList<Category>) : CategoriesApi {
        var failureFor: String? = null
        var failure: Throwable? = null
        var createCalls = 0
        var updateCalls = 0
        var deleteCalls = 0

        private fun fail(method: String) {
            if (failureFor == method) throw requireNotNull(failure)
        }

        override suspend fun list(): ApiEnvelope<List<Category>> {
            fail("list")
            return ApiEnvelope(rows.toList())
        }

        override suspend fun create(body: CategoryCreateRequest): ApiEnvelope<Category> {
            fail("create")
            createCalls++
            val created = category("c${rows.size + 1}", body.name, body.isIncome).copy(color = body.color, icon = body.icon)
            rows.add(created)
            return ApiEnvelope(created)
        }

        override suspend fun update(id: String, body: CategoryUpdateRequest): ApiEnvelope<Category> {
            fail("update")
            updateCalls++
            val index = rows.indexOfFirst { it.id == id }
            val changed = rows[index].copy(
                name = body.name ?: rows[index].name,
                color = body.color ?: rows[index].color,
                isIncome = body.isIncome ?: rows[index].isIncome,
                icon = body.icon?.takeIf { it.isNotEmpty() } ?: if (body.icon == "") null else rows[index].icon,
            )
            rows[index] = changed
            return ApiEnvelope(changed)
        }

        override suspend fun delete(id: String) {
            fail("delete")
            deleteCalls++
            rows.removeAll { it.id == id }
        }
    }
}
