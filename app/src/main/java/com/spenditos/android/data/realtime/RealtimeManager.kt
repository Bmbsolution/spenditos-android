package com.spenditos.android.data.realtime

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.auth.SessionState
import com.spenditos.android.core.network.apiCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener as OkHttpWebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

interface WebSocketConnector {
    fun connect(url: String, listener: WebSocketListener): WebSocketConnection
}

interface WebSocketListener {
    fun onOpen()
    fun onMessage(message: String)
    fun onClosed()
    fun onFailure()
}

fun interface WebSocketConnection {
    fun close()
}

interface ForegroundMonitor {
    val isForeground: kotlinx.coroutines.flow.StateFlow<Boolean>
}

/** Something that can report successful reconnects (the realtime socket). */
interface ReconnectSignals {
    /** Emits when a socket opens again after a previous open, not on the first connection. */
    val reconnects: SharedFlow<Unit>
}

/** Injectable wait seam keeps reconnect scheduling deterministic in tests. */
fun interface RealtimeClock {
    suspend fun sleep(milliseconds: Long)
}

class SystemRealtimeClock @Inject constructor() : RealtimeClock {
    override suspend fun sleep(milliseconds: Long) = delay(milliseconds)
}

@Singleton
class RealtimeManager @Inject constructor(
    private val api: RealtimeApi,
    private val session: SessionManager,
    private val foreground: ForegroundMonitor,
    private val connector: WebSocketConnector,
    private val events: RealtimeEvents,
    private val clock: RealtimeClock,
    @RealtimeScope private val scope: CoroutineScope,
    @RealtimeIoDispatcher private val dispatcher: CoroutineDispatcher,
    @RealtimeUrl private val webSocketUrl: String,
) : ReconnectSignals {
    private var started = false

    @Volatile
    private var hasConnectedOnce = false

    private val _reconnects = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    /** iOS posts `webSocketReconnected` only when a socket opens again, never on first connect. */
    override val reconnects: SharedFlow<Unit> = _reconnects.asSharedFlow()

    val gamificationUpdates: SharedFlow<Unit> = events.gamificationUpdates

    /** Starts one process-lifetime observer; the socket is active only while signed in and foregrounded. */
    @Synchronized
    fun start() {
        if (started || webSocketUrl.isBlank()) return
        started = true
        scope.launch(dispatcher) {
            combine(foreground.isForeground, session.state) { isForeground, state ->
                isForeground && state is SessionState.SignedIn
            }.distinctUntilChanged().collectLatest { shouldConnect ->
                if (shouldConnect) maintainConnection()
            }
        }
    }

    private suspend fun maintainConnection() {
        var failedConnections = 0
        while (currentCoroutineContext().isActive) {
            val connected = try {
                val ticket = apiCall { api.createTicket() }
                require(ticket.ticket.isNotBlank())
                val url = webSocketUrl.withTicket(ticket.ticket) ?: throw IllegalArgumentException("Invalid websocket URL")
                connectUntilClosed(url)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }

            if (connected) failedConnections = 0
            clock.sleep(reconnectDelay(failedConnections))
            if (failedConnections < FAST_RETRY_COUNT) failedConnections++
        }
    }

    private suspend fun connectUntilClosed(url: String): Boolean = suspendCancellableCoroutine { continuation ->
        val opened = AtomicBoolean(false)
        val connection = connector.connect(url, object : WebSocketListener {
            override fun onOpen() {
                opened.set(true)
                if (hasConnectedOnce) _reconnects.tryEmit(Unit)
                hasConnectedOnce = true
            }

            override fun onMessage(message: String) {
                events.accept(message)
            }

            override fun onClosed() = finish()
            override fun onFailure() = finish()

            private fun finish() {
                if (continuation.isActive) continuation.resume(opened.get())
            }
        })
        continuation.invokeOnCancellation { connection.close() }
    }

    /** OkHttp's [okhttp3.HttpUrl] only parses http(s), so build on the https form and restore wss. */
    private fun String.withTicket(ticket: String): String? {
        if (!startsWith("wss://")) return null
        val base = ("https://" + removePrefix("wss://")).toHttpUrlOrNull() ?: return null
        val built = base.newBuilder()
            .removeAllQueryParameters("ticket")
            .addQueryParameter("ticket", ticket)
            .build()
            .toString()
        return "wss://" + built.removePrefix("https://")
    }

    private companion object {
        const val FAST_RETRY_COUNT = 5

        fun reconnectDelay(failures: Int): Long = when (failures) {
            0 -> 1_000L
            1 -> 2_000L
            2 -> 4_000L
            3 -> 8_000L
            4 -> 16_000L
            else -> 30_000L
        }
    }
}

/** ProcessLifecycleOwner maps ON_START / ON_STOP to app foreground state. */
class ProcessForegroundMonitor : ForegroundMonitor {
    private val lifecycle = ProcessLifecycleOwner.get().lifecycle
    private val mutableForeground = MutableStateFlow(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))

    override val isForeground: kotlinx.coroutines.flow.StateFlow<Boolean> = mutableForeground

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                mutableForeground.value = true
            }

            override fun onStop(owner: LifecycleOwner) {
                mutableForeground.value = false
            }
        })
    }
}

/** OkHttp websocket adapter. The client has no auth interceptor and pings every five minutes. */
class OkHttpWebSocketConnector(client: OkHttpClient) : WebSocketConnector {
    private val client = client.newBuilder()
        .pingInterval(300, TimeUnit.SECONDS)
        .build()

    override fun connect(url: String, listener: WebSocketListener): WebSocketConnection {
        val socket = client.newWebSocket(Request.Builder().url(url).build(), object : OkHttpWebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = listener.onOpen()
            override fun onMessage(webSocket: WebSocket, text: String) = listener.onMessage(text)
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = listener.onClosed()
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = listener.onFailure()
        })
        return WebSocketConnection { socket.cancel() }
    }
}
