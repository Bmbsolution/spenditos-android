package com.spenditos.android.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.auth.TokenRefresher
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.db.SpenditosDatabase
import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.data.categories.CategoriesApi
import com.spenditos.android.data.categories.CategoryRepository
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.CategoryCreateRequest
import com.spenditos.android.data.model.CategoryUpdateRequest
import com.spenditos.android.data.realtime.ForegroundMonitor
import com.spenditos.android.data.realtime.ReconnectSignals
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class FinanceReconciliationCoordinatorTest {
    private lateinit var db: SpenditosDatabase
    private val api = FakeCategoriesApi()
    private val bus = FinanceInvalidationBus()
    private val invalidations = Channel<Set<Aggregate>>(Channel.UNLIMITED)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SpenditosDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `returning to the foreground refreshes categories and invalidates every aggregate`() = runTest {
        val foreground = FakeForeground(false)
        val coordinator = coordinator(session(), foreground, FakeReconnectSignals(), backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { bus.events.collect { invalidations.send(it) } }
        coordinator.start()

        foreground.isForeground.value = true
        advanceTimeBy(250)
        advanceUntilIdle()

        assertEquals(Aggregate.entries.toSet(), invalidations.receive())
        assertEquals(1, api.calls.get())
        assertTrue(invalidations.isEmpty)
    }

    @Test
    fun `a realtime reconnect without any foreground change reconciles too`() = runTest {
        val reconnects = FakeReconnectSignals()
        val foreground = FakeForeground(true)
        val coordinator = coordinator(session(), foreground, reconnects, backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { bus.events.collect { invalidations.send(it) } }
        coordinator.start()
        advanceTimeBy(250)
        advanceUntilIdle()

        assertEquals(Aggregate.entries.toSet(), invalidations.receive()) // the initial foreground emission
        val baseline = api.calls.get()
        assertTrue(baseline >= 1)

        reconnects.emit()

        // Waiting for the invalidation first lets auto-advance drive the debounced flush,
        // its categories call and the Room write before the counts are asserted.
        assertEquals(Aggregate.entries.toSet(), invalidations.receive())
        assertEquals(baseline + 1, api.calls.get())
        assertTrue(invalidations.isEmpty)
    }

    @Test
    fun `duplicate triggers coalesce into one reconciliation`() = runTest {
        val reconnects = FakeReconnectSignals()
        val foreground = FakeForeground(false)
        val coordinator = coordinator(session(), foreground, reconnects, backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { bus.events.collect { invalidations.send(it) } }
        coordinator.start()

        foreground.isForeground.value = true
        coordinator.schedule()
        reconnects.emit()
        advanceTimeBy(250)
        advanceUntilIdle()

        assertEquals(Aggregate.entries.toSet(), invalidations.receive())
        assertEquals(1, api.calls.get())
        assertTrue(invalidations.isEmpty)
    }

    @Test
    fun `a failing category refresh still invalidates and never throws`() = runTest {
        api.failure = RuntimeException("offline")
        val coordinator = coordinator(session(), FakeForeground(true), FakeReconnectSignals(), backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { bus.events.collect { invalidations.send(it) } }
        coordinator.start()
        advanceTimeBy(250)
        advanceUntilIdle()

        assertEquals(Aggregate.entries.toSet(), invalidations.receive())
        assertEquals(1, api.calls.get())
    }

    @Test
    fun `a signed-out session reconciles nothing`() = runTest {
        val session = session()
        session.signOut()
        val coordinator = coordinator(session, FakeForeground(true), FakeReconnectSignals(), backgroundScope)
        coordinator.start()
        advanceTimeBy(250)
        advanceUntilIdle()

        assertEquals(0, api.calls.get())
        assertTrue(invalidations.isEmpty)
    }

    private fun coordinator(
        session: SessionManager,
        foreground: ForegroundMonitor,
        reconnects: ReconnectSignals,
        scope: kotlinx.coroutines.CoroutineScope,
    ): FinanceReconciliationCoordinator {
        val categories = CategoryRepository(api, db.categories(), session, bus)
        return FinanceReconciliationCoordinator(session, foreground, reconnects, bus, categories, scope)
    }

    private fun session(): SessionManager = SessionManager(
        FakeTokenStore.signedIn(),
        TokenRefresher { RefreshedTokens("access", "id") },
        EpochClock { NOW },
        AppEnvironment(),
    )

    private class FakeForeground(initiallyForeground: Boolean) : ForegroundMonitor {
        override val isForeground = MutableStateFlow(initiallyForeground)
    }

    private class FakeReconnectSignals : ReconnectSignals {
        private val flow = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
        override val reconnects: kotlinx.coroutines.flow.SharedFlow<Unit> = flow
        fun emit() {
            flow.tryEmit(Unit)
        }
    }

    private class FakeCategoriesApi : CategoriesApi {
        val calls = AtomicInteger()
        var failure: Exception? = null

        override suspend fun list(): ApiEnvelope<List<Category>> {
            calls.incrementAndGet()
            failure?.let { throw it }
            return ApiEnvelope(data = listOf(Category("cat-1", "user-1", "Food", "#FF8844", isIncome = false)))
        }

        override suspend fun create(body: CategoryCreateRequest): ApiEnvelope<Category> = error("unused")
        override suspend fun update(id: String, body: CategoryUpdateRequest): ApiEnvelope<Category> = error("unused")
        override suspend fun delete(id: String) = error("unused")
    }
}
