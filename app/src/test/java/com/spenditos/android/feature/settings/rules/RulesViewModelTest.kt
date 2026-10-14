package com.spenditos.android.feature.settings.rules

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.merchants.MerchantCategories
import com.spenditos.android.data.merchants.RuleErrors
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.MerchantMapping
import com.spenditos.android.data.model.RuleAction
import com.spenditos.android.testing.FakeMerchantMappings
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RulesViewModelTest {
    private val dining = Category("cat_dining", "u", "Dining", "#FF0000", isIncome = false)
    private val groceries = Category("cat_groc", "u", "Groceries", "#00FF00", isIncome = false)
    private val salary = Category("cat_salary", "u", "Salary", "#0000FF", isIncome = true)

    private fun rule(id: String, pattern: String, category: String? = "cat_dining", action: RuleAction = RuleAction.CATEGORIZE, source: String = "user") =
        MerchantMapping(id, "u", pattern, pattern, category.takeIf { action == RuleAction.CATEGORIZE }, source = source, action = action.wire)

    private lateinit var fake: FakeMerchantMappings

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        fake = FakeMerchantMappings(
            listOf(
                rule("r1", "Tim Hortons"),
                rule("r2", "Payment Thank You", action = RuleAction.SKIP),
                rule("a1", "AMZN", source = "ai"),
                rule("s1", "Netflix", source = "system"),
            ),
        )
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = RulesViewModel(fake, MerchantCategories { flowOf(listOf(dining, groceries, salary)) })

    @Test
    fun `lists only the user's own rules and the categories`() = runTest {
        val vm = vm()
        advanceUntilIdle()
        val state = vm.state.value
        assertEquals(listOf("r1", "r2"), state.rules.map { it.id })
        assertEquals(3, state.categories.size)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `userRules filters by source`() {
        val all = listOf(rule("1", "a"), rule("2", "b", source = "ai"), rule("3", "c", source = "system"))
        assertEquals(listOf("1"), RulesViewModel.userRules(all).map { it.id })
    }

    @Test
    fun `row text names the category, an unknown category or the skip`() {
        val cats = listOf(dining)
        assertEquals("→ Dining", RulesViewModel.actionText(rule("1", "x"), cats))
        assertEquals("→ Unknown category", RulesViewModel.actionText(rule("1", "x", category = "gone"), cats))
        assertEquals("Skip — not spending", RulesViewModel.actionText(rule("1", "x", action = RuleAction.SKIP), cats))
    }

    @Test
    fun `a failed load says so in plain words`() = runTest {
        fake.failure = ApiError.Network(IOException("offline"))
        val vm = vm()
        advanceUntilIdle()
        assertEquals(RuleErrors.OFFLINE, vm.state.value.error)
        assertTrue(vm.state.value.rules.isEmpty())
        vm.dismissError()
        assertNull(vm.state.value.error)
    }

    @Test
    fun `changing the category updates the list and the server`() = runTest {
        val vm = vm()
        advanceUntilIdle()
        vm.update("r1", RuleAction.CATEGORIZE, "cat_groc")
        // Right away, before the server answers.
        assertEquals("cat_groc", vm.state.value.rule("r1")?.categoryId)
        advanceUntilIdle()
        assertEquals(listOf(Triple("r1", RuleAction.CATEGORIZE, "cat_groc")), fake.updates)
        assertEquals("cat_groc", vm.state.value.rule("r1")?.categoryId)
    }

    @Test
    fun `skip clears the category and sends no category`() = runTest {
        val vm = vm()
        advanceUntilIdle()
        vm.update("r1", RuleAction.SKIP, null)
        assertEquals(RuleAction.SKIP, vm.state.value.rule("r1")?.ruleAction)
        assertNull(vm.state.value.rule("r1")?.categoryId)
        advanceUntilIdle()
        assertEquals(listOf(Triple("r1", RuleAction.SKIP, null)), fake.updates)
    }

    @Test
    fun `a refused edit rolls back and shows the friendly conflict text`() = runTest {
        val vm = vm()
        advanceUntilIdle()
        fake.failure = ApiError.Server(409, "CategoryBeingDeleted")
        vm.update("r1", RuleAction.CATEGORIZE, "cat_groc")
        advanceUntilIdle()
        assertEquals("cat_dining", vm.state.value.rule("r1")?.categoryId)
        assertEquals(RuleErrors.CATEGORY_BEING_DELETED, vm.state.value.error)
    }

    @Test
    fun `a missing category and a clashing rule read as sentences`() = runTest {
        val vm = vm()
        advanceUntilIdle()
        fake.failure = ApiError.Server(400, "CategoryNotFound")
        vm.update("r1", RuleAction.CATEGORIZE, "cat_gone")
        advanceUntilIdle()
        assertEquals(RuleErrors.CATEGORY_NOT_FOUND, vm.state.value.error)

        fake.failure = ApiError.Server(409, "RuleExists")
        vm.update("r1", RuleAction.CATEGORIZE, "cat_groc")
        advanceUntilIdle()
        assertEquals(RuleErrors.RULE_EXISTS, vm.state.value.error)
    }

    @Test
    fun `delete removes the rule only after the server agrees`() = runTest {
        val vm = vm()
        advanceUntilIdle()
        var closed = 0
        fake.failure = ApiError.Network(IOException("offline"))
        vm.delete("r1") { closed++ }
        advanceUntilIdle()
        assertEquals(0, closed)
        assertEquals(listOf("r1", "r2"), vm.state.value.rules.map { it.id })
        assertEquals(RuleErrors.OFFLINE, vm.state.value.error)
        assertFalse(vm.state.value.isDeleting)

        fake.failure = null
        vm.delete("r1") { closed++ }
        advanceUntilIdle()
        assertEquals(1, closed)
        assertEquals(listOf("r2"), vm.state.value.rules.map { it.id })
        assertEquals(listOf("r1"), fake.deletes)
    }

    @Test
    fun `adding a rule upserts by merchant and appends or replaces it`() = runTest {
        val vm = vm()
        advanceUntilIdle()
        var done = 0
        vm.add("  Uber  ", skip = false, categoryId = "cat_dining") { done++ }
        advanceUntilIdle()
        assertEquals(1, done)
        assertEquals(listOf(Triple("Uber", RuleAction.CATEGORIZE, "cat_dining")), fake.upserts)
        assertEquals(listOf("r1", "r2", "rule-5"), vm.state.value.rules.map { it.id })

        // The same merchant again updates that rule instead of adding a second one.
        vm.add("uber", skip = true, categoryId = null) { done++ }
        advanceUntilIdle()
        assertEquals(3, vm.state.value.rules.size)
        assertEquals(RuleAction.SKIP, vm.state.value.rules.last().ruleAction)
    }

    @Test
    fun `an invalid add sends nothing, and a failed add keeps the sheet open with a message`() = runTest {
        val vm = vm()
        advanceUntilIdle()
        var done = 0
        vm.add("###", skip = false, categoryId = "cat_dining") { done++ }
        vm.add("Uber", skip = false, categoryId = null) { done++ }
        advanceUntilIdle()
        assertTrue(fake.upserts.isEmpty())

        fake.failUpsert = ApiError.Server(409, "CategoryBeingDeleted")
        vm.add("Uber", skip = false, categoryId = "cat_dining") { done++ }
        advanceUntilIdle()
        assertEquals(0, done)
        assertEquals(RuleErrors.CATEGORY_BEING_DELETED, vm.state.value.addError)
        assertFalse(vm.state.value.isAdding)
        vm.dismissAddError()
        assertNull(vm.state.value.addError)
    }
}
