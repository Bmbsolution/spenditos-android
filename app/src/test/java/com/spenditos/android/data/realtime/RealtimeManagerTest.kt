package com.spenditos.android.data.realtime

import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.auth.TokenRefresher
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.model.DataChangedEvent
import com.spenditos.android.data.model.GamificationUpdate
import com.spenditos.android.data.model.RealtimeEnvelope
import com.spenditos.android.data.model.WsTicketResponse
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RealtimeManagerTest {
    @Test
    fun `handwritten ticket and event fixtures decode`() {
        val loader = requireNotNull(javaClass.classLoader)
        val ticketJson = requireNotNull(loader.getResource("contract/handwritten/ws_ticket_response.json")).readText()
        val ticket = SpenditosJson.decodeFromString(
            ApiEnvelope.serializer(WsTicketResponse.serializer()),
            ticketJson,
        ).data
        assertEquals(WsTicketResponse("fake-ticket-123", 60), ticket)

        val eventJson = requireNotNull(loader.getResource("contract/handwritten/user_data_changed.json")).readText()
        val envelope = SpenditosJson.decodeFromString(RealtimeEnvelope.serializer(), eventJson)
        val event = SpenditosJson.decodeFromJsonElement(DataChangedEvent.serializer(), envelope.data)
        assertEquals("evt-fake-001", event.eventId)
        assertEquals("transaction", event.aggregateType)

        val gamificationJson = requireNotNull(loader.getResource("contract/handwritten/gamification_update.json")).readText()
        val gamificationEnvelope = SpenditosJson.decodeFromString(RealtimeEnvelope.serializer(), gamificationJson)
        val update = SpenditosJson.decodeFromJsonElement(GamificationUpdate.serializer(), gamificationEnvelope.data)
        assertEquals(123, update.points!!.total)
    }

    @Test
    fun `connects with a fresh ticket after an opened socket closes`() = runTest {
        val api = FakeRealtimeApi()
        val connector = FakeConnector()
        val clock = RecordingClock()
        val manager = manager(api, connector, clock, session(), FakeForeground(true), FinanceInvalidationBus(), backgroundScope, StandardTestDispatcher(testScheduler))

        manager.start()
        manager.start()
        runCurrent()

        assertEquals(1, api.calls)
        assertTrue(connector.sockets.single().url.contains("ticket=fake-ticket-1"))
        connector.sockets.single().listener.onOpen()
        connector.sockets.single().listener.onClosed()
        runCurrent()
        assertEquals(listOf(1_000L), clock.delays)

        clock.advance()
        runCurrent()
        assertEquals(2, api.calls)
        assertTrue(connector.sockets[1].url.contains("ticket=fake-ticket-2"))
    }

    @Test
    fun `user data changes invalidate each mapped aggregate only once per event id`() = runTest {
        val bus = FinanceInvalidationBus()
        val events = RealtimeEvents(bus)
        val received = mutableListOf<Set<Aggregate>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { bus.events.collect { received += it } }
        val expected = linkedMapOf(
            "transaction" to Aggregate.TRANSACTION,
            "category" to Aggregate.CATEGORY,
            "recurring" to Aggregate.RECURRING,
            "import" to Aggregate.IMPORT,
            "settings" to Aggregate.SETTINGS,
            "entitlement" to Aggregate.ENTITLEMENT,
            "account" to Aggregate.ACCOUNT,
        )

        expected.entries.forEachIndexed { index, (type, aggregate) ->
            val mutation = listOf("created", "updated", "deleted")[index % 3]
            val message = dataChangedMessage("evt-$index", type, mutation)
            events.accept(message)
            events.accept(message)
            assertEquals(setOf(aggregate), received.last())
        }

        assertEquals(expected.size, received.size)
    }

    @Test
    fun `event dedupe ring retains the most recent 512 ids`() = runTest {
        val bus = FinanceInvalidationBus()
        val events = RealtimeEvents(bus)
        val received = mutableListOf<Set<Aggregate>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { bus.events.collect { received += it } }

        events.accept(dataChangedMessage("evt-oldest", "transaction", "created"))
        repeat(512) { index -> events.accept(dataChangedMessage("evt-new-$index", "category", "updated")) }
        events.accept(dataChangedMessage("evt-oldest", "transaction", "created"))

        assertEquals(514, received.size)
        assertEquals(setOf(Aggregate.TRANSACTION), received.last())
    }

    @Test
    fun `unknown and malformed messages are ignored and gamification emits a unit`() = runTest {
        val bus = FinanceInvalidationBus()
        val events = RealtimeEvents(bus)
        val invalidations = mutableListOf<Set<Aggregate>>()
        var gamificationUpdates = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { bus.events.collect { invalidations += it } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { events.gamificationUpdates.collect { gamificationUpdates++ } }

        events.accept("not json")
        events.accept("{}")
        events.accept("""{"type":{},"data":{}}""")
        events.accept("""{"type":"other.event","data":{}}""")
        events.accept("""{"type":"user.data_changed","data":{"eventId":"bad"}}""")
        events.accept(dataChangedMessage("evt-unknown", "future_aggregate", "created"))
        events.accept("""{"type":"gamification.update","data":{"points":{"total":"bad","change":1}}}""")
        val gamificationJson = requireNotNull(javaClass.classLoader!!.getResource("contract/handwritten/gamification_update.json")).readText()
        events.accept(gamificationJson)

        assertTrue(invalidations.isEmpty())
        assertEquals(1, gamificationUpdates)
    }

    @Test
    fun `reconnect backoff is one two four eight sixteen then thirty seconds`() = runTest {
        val api = FakeRealtimeApi()
        val connector = FakeConnector()
        val clock = RecordingClock()
        val manager = manager(api, connector, clock, session(), FakeForeground(true), FinanceInvalidationBus(), backgroundScope, StandardTestDispatcher(testScheduler))
        val expected = listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L)

        manager.start()
        runCurrent()
        expected.forEachIndexed { index, delay ->
            connector.sockets[index].listener.onFailure()
            runCurrent()
            assertEquals(expected.take(index + 1), clock.delays)
            assertEquals(delay, clock.delays[index])
            if (index < expected.lastIndex) {
                clock.advance()
                runCurrent()
            }
        }
    }

    @Test
    fun `successful connection resets reconnect delay`() = runTest {
        val connector = FakeConnector()
        val clock = RecordingClock()
        val manager = manager(FakeRealtimeApi(), connector, clock, session(), FakeForeground(true), FinanceInvalidationBus(), backgroundScope, StandardTestDispatcher(testScheduler))

        manager.start()
        runCurrent()
        connector.sockets[0].listener.onFailure()
        runCurrent()
        clock.advance()
        runCurrent()

        connector.sockets[1].listener.onOpen()
        connector.sockets[1].listener.onFailure()
        runCurrent()
        assertEquals(listOf(1_000L, 1_000L), clock.delays)
    }

    @Test
    fun `ticket client errors server errors and network errors retry without throwing`() = runTest {
        val api = FakeRealtimeApi().apply {
            failures += ApiError.Server(400, "bad request")
            failures += ApiError.Server(503, "unavailable")
            failures += ApiError.Network(IOException("offline"))
        }
        val connector = FakeConnector()
        val clock = RecordingClock()
        val manager = manager(api, connector, clock, session(), FakeForeground(true), FinanceInvalidationBus(), backgroundScope, StandardTestDispatcher(testScheduler))

        manager.start()
        runCurrent()
        assertEquals(listOf(1_000L), clock.delays)
        clock.advance()
        runCurrent()
        assertEquals(listOf(1_000L, 2_000L), clock.delays)
        clock.advance()
        runCurrent()
        assertEquals(listOf(1_000L, 2_000L, 4_000L), clock.delays)
        clock.advance()
        runCurrent()

        assertEquals(4, api.calls)
        assertEquals(1, connector.sockets.size)
        assertTrue(connector.sockets.single().url.contains("ticket=fake-ticket-4"))
    }

    @Test
    fun `sign out closes the active socket`() = runTest {
        val connector = FakeConnector()
        val session = session()
        val manager = manager(FakeRealtimeApi(), connector, RecordingClock(), session, FakeForeground(true), FinanceInvalidationBus(), backgroundScope, StandardTestDispatcher(testScheduler))

        manager.start()
        runCurrent()
        session.signOut()
        runCurrent()

        assertTrue(connector.sockets.single().closed)
        assertEquals(1, connector.sockets.size)
    }

    @Test
    fun `backgrounding closes the active socket`() = runTest {
        val connector = FakeConnector()
        val foreground = FakeForeground(true)
        val manager = manager(FakeRealtimeApi(), connector, RecordingClock(), session(), foreground, FinanceInvalidationBus(), backgroundScope, StandardTestDispatcher(testScheduler))

        manager.start()
        runCurrent()
        foreground.isForeground.value = false
        runCurrent()

        assertTrue(connector.sockets.single().closed)
        assertEquals(1, connector.sockets.size)
    }

    @Test
    fun `empty websocket url stays inert`() = runTest {
        val api = FakeRealtimeApi()
        val connector = FakeConnector()
        val manager = manager(api, connector, RecordingClock(), session(), FakeForeground(true), FinanceInvalidationBus(), backgroundScope, StandardTestDispatcher(testScheduler), url = "")

        manager.start()
        runCurrent()

        assertEquals(0, api.calls)
        assertTrue(connector.sockets.isEmpty())
    }

    private fun manager(
        api: RealtimeApi,
        connector: WebSocketConnector,
        clock: RealtimeClock,
        session: SessionManager,
        foreground: ForegroundMonitor,
        bus: FinanceInvalidationBus,
        scope: CoroutineScope,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        url: String = "wss://socket.example.test",
    ) = RealtimeManager(api, session, foreground, connector, RealtimeEvents(bus), clock, scope, dispatcher, url)

    private fun session(): SessionManager = SessionManager(
        FakeTokenStore.signedIn(),
        TokenRefresher { RefreshedTokens("access", "id") },
        EpochClock { NOW },
        AppEnvironment(),
    )

    private fun dataChangedMessage(eventId: String, aggregateType: String, mutationType: String) =
        """{"type":"user.data_changed","data":{"eventId":"$eventId","aggregateType":"$aggregateType","mutationType":"$mutationType","occurredAt":"2026-10-07T12:00:00Z","version":1}}"""

    private class FakeRealtimeApi : RealtimeApi {
        var calls = 0
        val failures = mutableListOf<Exception>()

        override suspend fun createTicket(): ApiEnvelope<WsTicketResponse> {
            calls++
            if (failures.isNotEmpty()) throw failures.removeAt(0)
            return ApiEnvelope(data = WsTicketResponse("fake-ticket-$calls", 60))
        }
    }

    private class FakeForeground(initiallyForeground: Boolean) : ForegroundMonitor {
        override val isForeground = MutableStateFlow(initiallyForeground)
    }

    private class FakeConnector : WebSocketConnector {
        val sockets = mutableListOf<FakeSocket>()

        override fun connect(url: String, listener: WebSocketListener): WebSocketConnection {
            val socket = FakeSocket(url, listener)
            sockets += socket
            return WebSocketConnection { socket.closed = true }
        }
    }

    private class FakeSocket(
        val url: String,
        val listener: WebSocketListener,
        var closed: Boolean = false,
    )

    private class RecordingClock : RealtimeClock {
        val delays = mutableListOf<Long>()
        private val gates = mutableListOf<CompletableDeferred<Unit>>()

        override suspend fun sleep(milliseconds: Long) {
            delays += milliseconds
            CompletableDeferred<Unit>().also { gates += it }.await()
        }

        fun advance() {
            gates.removeAt(0).complete(Unit)
        }
    }
}
