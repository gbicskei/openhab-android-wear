package org.openhab.habdroid.wear.data.api

import org.openhab.habdroid.wear.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.openhab.habdroid.wear.data.repository.CredentialStore
import org.openhab.habdroid.wear.data.repository.ItemCache
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel

/**
 * Shared SSE client for real-time item state updates.
 *
 * This is the **single source** of server-sent events for the entire watch app.
 * All components (tile, complications, control activities) subscribe via [stateChanges].
 *
 * Strategy:
 * 1. Connect to SSE stream filtered to [watchedItems] state changes
 * 2. Use ALIVE heartbeat (every ~10s from server) as liveness signal
 * 3. If no event within 30s → assume dead, reconnect
 * 4. After 3 consecutive quick failures → fall back to polling (15s interval)
 * 5. Polling continues until all subscribers leave (ref count → 0)
 *
 * Lifecycle is reference-counted:
 * - [subscribe] increments the ref count and starts SSE if it was stopped
 * - [unsubscribe] decrements the ref count and stops SSE when it reaches 0
 * - Tile enter/leave and control activity start/finish use subscribe/unsubscribe
 */
@Singleton
class TileStateEventSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val credentialStore: CredentialStore,
    private val repository: OpenHabRepository,
    private val itemCache: ItemCache,
    private val serverSelector: ServerSelector
) {
    companion object {
        private const val TAG = "TileStateSSE"
        private const val EVENTS_PATH = "/rest/events"
        private const val TOPIC_WILDCARD_FILTER = "openhab/items/*/statechanged"

        /** Timeout waiting for any event (ALIVE or state change). Server sends ALIVE every ~10s. */
        private const val EVENT_TIMEOUT_MS = 30_000L

        /** Delay between reconnection attempts */
        private const val RECONNECT_DELAY_MS = 1_000L

        /** If connection fails this quickly, count it as a "quick failure" */
        private const val QUICK_FAILURE_THRESHOLD_MS = 10_000L

        /** After this many consecutive quick failures, fall back to polling */
        private const val MAX_QUICK_FAILURES = 3

        /** Polling interval when SSE is unavailable */
        private const val POLL_INTERVAL_MS = 15_000L

        /** Connection is considered "live" if an event was received within this window */
        private const val CONNECTION_ALIVE_THRESHOLD_MS = 60_000L
    }

    /** Item names to watch for changes. Updated when tile items are loaded.
     *  Setting a new value restarts the SSE connection to apply the updated topic filter. */
    var watchedItems: Set<String> = emptySet()
        set(value) {
            val changed = field != value
            field = value
            if (changed && connectionJob?.isActive == true) {
                AppLog.d(TAG, "Watched items changed (${value.size} items), restarting SSE for new topic filter")
                restartPending = true
                connectionJob?.cancel()
            }
        }

    /** Flag to distinguish a restart (items changed) from a stop (tile leave). */
    @Volatile
    private var restartPending = false

    /** Shared flow of state changes. All subscribers receive (itemName, newState) pairs. */
    private val _stateChanges = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 64)

    /** Flow of item state changes from the SSE connection. Collect this from any component. */
    val stateChanges: SharedFlow<Pair<String, String>> = _stateChanges.asSharedFlow()

    /** Reference count of active subscribers. SSE runs while > 0. */
    private val subscriberCount = AtomicInteger(0)

    /** Callback for tile refresh (set by tile service). */
    private var tileChangedCallback: (() -> Unit)? = null

    /** Timestamp of the last successful SSE event or poll. Used by the tile to show connection status. */
    @Volatile
    var lastSuccessMillis: Long = 0L

    /** Whether the connection is currently considered live (event received within threshold). */
    val isConnected: Boolean
        get() = lastSuccessMillis > 0L &&
            (System.currentTimeMillis() - lastSuccessMillis) < CONNECTION_ALIVE_THRESHOLD_MS

    /** Whether the tile is currently visible. Survives TileService instance recreation.
     *  Set to true by onTileEnterEvent, false by onTileLeaveEvent.
     *  On a fresh process (no enter/leave yet), defaults to true so that the first
     *  onTileRequest can start SSE. onTileLeaveEvent will clear it. */
    @Volatile
    var tileVisible: Boolean = true

    /**
     * Timestamp of the last onTileRequest start. Used to detect if a new onTileRequest
     * arrives long after the previous one (indicating the tile was off-screen and is now
     * freshly displayed). If gap > 5s, it's a fresh display → reset to main.
     */
    @Volatile
    var lastTileRequestMillis: Long = 0L

    private var connectionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Subscribe to the shared SSE connection. Increments the ref count and starts SSE if needed.
     * Call from tile enter, control activity start, etc.
     *
     * @param onChanged optional callback for tile refresh (only the tile service sets this)
     */
    fun subscribe(onChanged: (() -> Unit)? = null) {
        if (onChanged != null) tileChangedCallback = onChanged
        val count = subscriberCount.incrementAndGet()
        AppLog.d(TAG, "subscribe() → refCount=$count")
        startIfNeeded()
    }

    /**
     * Unsubscribe from the shared SSE connection. Decrements the ref count and stops SSE when 0.
     * Call from tile leave, control activity finish, etc.
     */
    fun unsubscribe() {
        val count = subscriberCount.decrementAndGet()
        AppLog.d(TAG, "unsubscribe() → refCount=$count")
        if (count <= 0) {
            subscriberCount.set(0) // floor at 0
            stopInternal()
        }
    }

    /**
     * Start the SSE connection loop. Handles reconnection and fallback to polling.
     * @param onChanged callback invoked when a watched item's state changes (triggers tile refresh)
     */
    fun start(onChanged: () -> Unit) {
        AppLog.d(TAG, "→ start()")
        tileChangedCallback = onChanged
        if (connectionJob?.isActive == true && !restartPending) {
            AppLog.d(TAG, "Already running, skipping start")
            return
        }
        restartPending = false
        startConnection()
    }

    /**
     * Stop the SSE connection and any polling. Called on tile leave.
     */
    fun stop() {
        AppLog.d(TAG, "Stopping")
        stopInternal()
    }

    private fun startIfNeeded() {
        if (connectionJob?.isActive == true && !restartPending) return
        restartPending = false
        startConnection()
    }

    private fun stopInternal() {
        connectionJob?.cancel()
        connectionJob = null
    }

    private fun startConnection() {

        connectionJob = scope.launch {
            var consecutiveQuickFailures = 0

            // Main loop: try SSE, fall back to polling if unstable
            while (isActive) {
                // Re-read credentials on each iteration (picks up server URL changes)
                val credentials = credentialStore.credentials.first() ?: run {
                    AppLog.w(TAG, "No credentials, cannot connect SSE")
                    return@launch
                }

                // Re-race local vs cloud on each reconnect attempt so the watch
                // adapts when entering/leaving the home network.
                serverSelector.reset()
                val baseUrl = (serverSelector.resolveUrl()).trimEnd('/')

                if (consecutiveQuickFailures >= MAX_QUICK_FAILURES) {
                    AppLog.d(TAG, "SSE unstable ($consecutiveQuickFailures quick failures), switching to polling")
                    val pollResult = pollLoop()
                    // Poll succeeded → network is back, reset and retry SSE
                    consecutiveQuickFailures = 0
                    if (pollResult == SseResult.CANCELLED) return@launch
                    AppLog.d(TAG, "Retrying SSE after successful poll")
                    delay(RECONNECT_DELAY_MS)
                    continue
                }

                val connectTime = System.currentTimeMillis()
                val sseResult = runSseSession(baseUrl)

                when (sseResult) {
                    SseResult.CANCELLED -> return@launch // stop() was called
                    SseResult.FAILURE -> {
                        val elapsed = System.currentTimeMillis() - connectTime
                        if (elapsed < QUICK_FAILURE_THRESHOLD_MS) {
                            consecutiveQuickFailures++
                            AppLog.d(TAG, "Quick failure #$consecutiveQuickFailures (${elapsed}ms)")
                        } else {
                            // Connection lasted a while then died — may have missed events
                            consecutiveQuickFailures = 0
                            AppLog.d(TAG, "SSE dropped after ${elapsed}ms, refreshing states")
                            refreshAndNotify()
                        }
                    }
                    SseResult.TIMEOUT -> {
                        // No events for 30s — reconnect (not a "quick" failure)
                        consecutiveQuickFailures = 0
                        AppLog.d(TAG, "Event timeout, refreshing states and reconnecting")
                        refreshAndNotify()
                    }
                }

                if (isActive) {
                    delay(RECONNECT_DELAY_MS)
                }
            }
        }
    }

    /** Notify all subscribers (shared flow + tile callback). */
    private fun notifyChanged() {
        tileChangedCallback?.invoke()
    }

    /**
     * Run a single SSE session. Returns when the connection fails, times out, or is cancelled.
     */
    private suspend fun runSseSession(baseUrl: String): SseResult {
        val _traceStart = System.currentTimeMillis()
        AppLog.d(TAG, "→ runSseSession()")
        val topicFilter = buildTopicFilter()
        val url = "$baseUrl$EVENTS_PATH?topics=$topicFilter"
        AppLog.d(TAG, "Connecting SSE: $url")

        // Resolve auth header upfront — don't use AuthInterceptor for SSE
        // (interceptors with runBlocking can interfere with OkHttp's SSE reader thread)
        val authHeader = serverSelector.resolveAuthHeader()

        val sseClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .apply {
                if (authHeader != null) {
                    addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("Authorization", authHeader)
                            .build()
                        chain.proceed(request)
                    }
                }
            }
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .header("Connection", "keep-alive")
            .header("X-Accel-Buffering", "no")
            .build()

        val channel = Channel<SseEvent>(Channel.BUFFERED)

        val factory = EventSources.createFactory(sseClient)
        val eventSource = factory.newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                AppLog.d(TAG, "SSE connected")
                lastSuccessMillis = System.currentTimeMillis()
                channel.trySend(SseEvent.Connected)
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                lastSuccessMillis = System.currentTimeMillis()
                channel.trySend(SseEvent.Data(data))
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                AppLog.w(TAG, "SSE onFailure: ${t?.javaClass?.simpleName}: ${t?.message ?: "status ${response?.code}"}")
                channel.trySend(SseEvent.Failed(t))
            }

            override fun onClosed(eventSource: EventSource) {
                AppLog.d(TAG, "SSE onClosed (server closed the connection)")
                channel.trySend(SseEvent.Closed)
            }
        })

        try {
            // Wait for connection or quick failure
            val firstEvent = withTimeoutOrNull(EVENT_TIMEOUT_MS) {
                channel.receive()
            } ?: run {
                eventSource.cancel()
                return SseResult.TIMEOUT
            }

            when (firstEvent) {
                is SseEvent.Failed -> {
                    eventSource.cancel()
                    return SseResult.FAILURE
                }
                is SseEvent.Closed -> {
                    eventSource.cancel()
                    return SseResult.FAILURE
                }
                else -> { /* Connected or Data — continue */ }
            }

            // Main event loop
            while (true) {
                val event = withTimeoutOrNull(EVENT_TIMEOUT_MS) {
                    channel.receive()
                } ?: run {
                    // No event within timeout — assume dead
                    AppLog.d(TAG, "No events for ${EVENT_TIMEOUT_MS}ms, connection presumed dead")
                    eventSource.cancel()
                    return SseResult.TIMEOUT
                }

                when (event) {
                    is SseEvent.Data -> handleEvent(event.data)
                    is SseEvent.Failed -> {
                        AppLog.d(TAG, "Event loop: received Failed signal")
                        eventSource.cancel()
                        return SseResult.FAILURE
                    }
                    is SseEvent.Closed -> {
                        AppLog.d(TAG, "Event loop: received Closed signal")
                        eventSource.cancel()
                        return SseResult.FAILURE
                    }
                    is SseEvent.Connected -> { /* Already connected, ignore duplicate */ }
                }
            }
        } catch (e: CancellationException) {
            eventSource.cancel()
            return SseResult.CANCELLED
        } finally {
            channel.close()
            AppLog.d(TAG, "← runSseSession() ${System.currentTimeMillis() - _traceStart}ms")
        }
    }

    /**
     * Quick state refresh to catch events missed during SSE gap.
     * Only fetches items currently on the visible tile page (lightweight).
     */
    private suspend fun refreshAndNotify() {
        try {
            // Only refresh the watched items (current page) — not all 14
            val itemsToRefresh = watchedItems.take(4) // max 4 items on a tile page
            if (itemsToRefresh.isEmpty()) {
                repository.refreshStates()
                    .onSuccess {
                        lastSuccessMillis = System.currentTimeMillis()
                        notifyChanged()
                    }
                    .onFailure { e -> AppLog.w(TAG, "Reconnect refresh failed: ${e.message}") }
                return
            }
            
            var anyChanged = false
            for (name in itemsToRefresh) {
                try {
                    val item = repository.fetchSingleItem(name)
                    if (item != null) {
                        val newState = item.state
                        if (newState != null) {
                            itemCache.updateItemState(name, newState)
                            _stateChanges.tryEmit(name to newState)
                            anyChanged = true
                        }
                    }
                } catch (e: Exception) {
                    AppLog.w(TAG, "Refresh item '$name' failed: ${e.message}")
                }
            }
            if (anyChanged) {
                lastSuccessMillis = System.currentTimeMillis()
                notifyChanged()
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Reconnect refresh error: ${e.message}")
        }
    }

    /**
     * Polling fallback: fetch states periodically when SSE is unstable.
     * After a successful poll (network is back), retries SSE.
     * Runs until SSE is re-established or the coroutine is cancelled (tile leave → stop()).
     */
    private suspend fun pollLoop(): SseResult {
        AppLog.d(TAG, "→ pollLoop()")
        AppLog.d(TAG, "Starting poll loop (${POLL_INTERVAL_MS}ms interval)")
        while (true) {
            delay(POLL_INTERVAL_MS)
            try {
                repository.refreshStates()
                    .onSuccess {
                        AppLog.d(TAG, "Poll: states refreshed — promoting back to SSE")
                        lastSuccessMillis = System.currentTimeMillis()
                        notifyChanged()
                        return SseResult.TIMEOUT // signals main loop to retry SSE
                    }
                    .onFailure { e ->
                        AppLog.w(TAG, "Poll: refresh failed: ${e.message}")
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(TAG, "Poll: unexpected error", e)
            }
        }
    }

    /**
     * Parse SSE event data and update cache + trigger callback if relevant.
     */
    private fun handleEvent(data: String) {
        val _traceStart = System.currentTimeMillis()
        AppLog.d(TAG, "→ handleEvent() dataLen=${data.length}")
        try {
            // Check for ALIVE event (server heartbeat)
            if (data.contains("\"type\":\"ALIVE\"") || data.contains("\"ALIVE\"")) {
                AppLog.d(TAG, "ALIVE heartbeat received")
                return
            }

            // Quick string parsing — avoid full JSON for performance
            val topicStart = data.indexOf("\"topic\":\"") + 9
            if (topicStart < 9) {
                AppLog.d(TAG, "No topic found in event, data=${data.take(200)}")
                return
            }
            val topicEnd = data.indexOf("\"", topicStart)
            if (topicEnd < 0) return
            val topic = data.substring(topicStart, topicEnd)

            // Topic format: openhab/items/{itemName}/statechanged
            val parts = topic.split("/")
            if (parts.size < 4 || parts[3] != "statechanged") {
                AppLog.d(TAG, "Non-statechanged topic: $topic")
                return
            }
            val itemName = parts[2]

            // Extract new state from payload
            val newState = extractNewState(data)
            AppLog.d(TAG, "Parsed: item=$itemName state=$newState watched=${watchedItems.contains(itemName)} watchedItems=${watchedItems.size}")

            if (newState != null) {
                // Always emit to shared flow (control activities may be listening for any item)
                _stateChanges.tryEmit(itemName to newState)
            }

            if (watchedItems.isEmpty() || watchedItems.contains(itemName)) {
                AppLog.d(TAG, "State changed: $itemName → $newState")
                if (newState != null) {
                    itemCache.updateItemState(itemName, newState)
                }
                notifyChanged()
            } else if (newState != null) {
                // Might be a Group member — update if it matches
                itemCache.updateItemState(itemName, newState)
                if (itemCache.get()?.any { it.item.isGroup && it.item.members?.any { m -> m.name == itemName } == true } == true) {
                    AppLog.d(TAG, "Group member state changed: $itemName → $newState")
                    notifyChanged()
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Error parsing SSE event", e)
        } finally {
            AppLog.d(TAG, "← handleEvent() ${System.currentTimeMillis() - _traceStart}ms")
        }
    }

    /**
     * Extract the new state value from SSE event payload.
     * The data format is: {"topic":"...","payload":"{\"type\":\"...\",\"value\":\"ON\",...}"}
     * The payload is a JSON-encoded string (escaped), so we need to look for escaped value key.
     */
    private fun extractNewState(data: String): String? {
        return try {
            // The payload is escaped JSON inside a string, so "value" appears as \"value\":\"
            val escapedValueKey = "\\\"value\\\":\\\""
            val valueStart = data.indexOf(escapedValueKey)
            if (valueStart < 0) {
                // Fallback: try unescaped (in case payload is not string-wrapped)
                val plainKey = "\"value\":\""
                val plainStart = data.indexOf(plainKey)
                if (plainStart < 0) return null
                val stateStart = plainStart + plainKey.length
                val stateEnd = data.indexOf("\"", stateStart)
                if (stateEnd < 0) return null
                return data.substring(stateStart, stateEnd)
            }
            val stateStart = valueStart + escapedValueKey.length
            // Find the closing escaped quote: \"
            val stateEnd = data.indexOf("\\\"", stateStart)
            if (stateEnd < 0) return null
            data.substring(stateStart, stateEnd)
        } catch (e: Exception) {
            null
        }
    }

    private enum class SseResult {
        FAILURE,
        TIMEOUT,
        CANCELLED
    }

    /**
     * Builds the SSE topic filter from [watchedItems].
     * If items are known, subscribes only to their state changes (reduces bandwidth).
     * Falls back to wildcard if no items are configured yet.
     */
    private fun buildTopicFilter(): String {
        val items = watchedItems
        if (items.isEmpty()) return TOPIC_WILDCARD_FILTER
        return items.joinToString(",") { "openhab/items/$it/statechanged" }
    }

    private sealed interface SseEvent {
        data object Connected : SseEvent
        data class Data(val data: String) : SseEvent
        data class Failed(val throwable: Throwable?) : SseEvent
        data object Closed : SseEvent
    }
}

