package com.spenditos.android.feature.settings.merchants

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.merchants.MerchantCategories
import com.spenditos.android.data.merchants.MerchantMappingActions
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.CreateMerchantMappingRequest
import com.spenditos.android.data.model.MerchantMapping
import com.spenditos.android.data.model.UpdateMerchantMappingRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MerchantMappingsViewModelTest {
    private class Fake : MerchantMappingActions {
        var rows = mutableListOf(MerchantMapping("a", "u", "SQ *COFFEE", "Coffee"))
        var failure: Throwable? = null
        val created = mutableListOf<CreateMerchantMappingRequest>()
        val updated = mutableListOf<Pair<String, UpdateMerchantMappingRequest>>()
        override suspend fun list(): List<MerchantMapping> { failure?.let { throw it }; return rows.toList() }
        override suspend fun create(request: CreateMerchantMappingRequest): MerchantMapping {
            failure?.let { throw it }
            created += request
            return MerchantMapping("new", "u", request.rawPattern, request.cleanMerchantName).also { rows += it }
        }
        override suspend fun update(id: String, request: UpdateMerchantMappingRequest): MerchantMapping {
            failure?.let { throw it }
            updated += id to request
            return MerchantMapping(id, "u", request.rawPattern, request.cleanMerchantName)
        }
        override suspend fun delete(id: String) { failure?.let { throw it }; rows.removeAll { it.id == id } }
    }

    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun categories() = MerchantCategories { flowOf(listOf(Category("c", "u", "Food", "#fff", isIncome = false))) }

    @Test
    fun `loads names and categories`() = runTest {
        val vm = MerchantMappingsViewModel(Fake(), categories())
        advanceUntilIdle()
        assertEquals(1, vm.state.value.items.size)
        assertEquals(1, vm.state.value.categories.size)
        assertEquals(false, vm.state.value.isLoading)
    }

    @Test
    fun `a failed first load shows an error and refresh recovers`() = runTest {
        val fake = Fake().apply { failure = ApiError.Network(IOException()) }
        val vm = MerchantMappingsViewModel(fake, categories())
        advanceUntilIdle()
        assertEquals("You're offline. Check your connection and try again.", vm.state.value.loadError)
        fake.failure = null
        vm.refresh(); advanceUntilIdle()
        assertNull(vm.state.value.loadError)
        assertEquals(1, vm.state.value.items.size)
    }

    @Test
    fun `validation blocks empty and broken input before any request`() = runTest {
        val fake = Fake()
        val vm = MerchantMappingsViewModel(fake, categories())
        advanceUntilIdle()
        vm.save(null, "  ", "Clean", null, false) {}
        assertEquals("Enter the text as it appears on your statement.", vm.state.value.actionError)
        vm.save(null, "RAW", " ", null, false) {}
        assertEquals("Enter the name you want to see.", vm.state.value.actionError)
        vm.save(null, "(unclosed", "Clean", null, true) {}
        assertEquals("That pattern isn't valid.", vm.state.value.actionError)
        advanceUntilIdle()
        assertTrue(fake.created.isEmpty())
    }

    @Test
    fun `create trims, edit updates, both reload and close`() = runTest {
        val fake = Fake()
        val vm = MerchantMappingsViewModel(fake, categories())
        advanceUntilIdle()
        var closed = 0
        vm.save(null, "  AMZN  ", " Amazon ", "c", false) { closed++ }
        advanceUntilIdle()
        assertEquals(CreateMerchantMappingRequest("AMZN", "Amazon", "c", false), fake.created.single())
        vm.save("a", "SQ *COFFEE", "Coffee Shop", null, false) { closed++ }
        advanceUntilIdle()
        assertEquals("a", fake.updated.single().first)
        assertEquals(2, closed)
        assertEquals(false, vm.state.value.isSaving)
    }

    @Test
    fun `server rejections keep the editor open with the server message`() = runTest {
        val fake = Fake()
        val vm = MerchantMappingsViewModel(fake, categories())
        advanceUntilIdle()
        fake.failure = ApiError.Server(400, "cleanMerchantName is too long")
        var closed = false
        vm.save(null, "RAW", "Clean", null, false) { closed = true }
        advanceUntilIdle()
        assertEquals("cleanMerchantName is too long", vm.state.value.actionError)
        assertEquals(false, closed)
        vm.clearError()
        assertNull(vm.state.value.actionError)
    }

    @Test
    fun `delete removes the row and failures keep it`() = runTest {
        val fake = Fake()
        val vm = MerchantMappingsViewModel(fake, categories())
        advanceUntilIdle()
        fake.failure = ApiError.Server(404, "x")
        vm.delete("a") {}
        advanceUntilIdle()
        assertEquals("That name no longer exists. Refresh and try again.", vm.state.value.actionError)
        assertEquals(1, vm.state.value.items.size)
        fake.failure = null
        var done = false
        vm.delete("a") { done = true }
        advanceUntilIdle()
        assertTrue(done)
        assertTrue(vm.state.value.items.isEmpty())
        assertNotNull(vm.state.value)
    }
}
