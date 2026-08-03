package org.openhab.habdroid.wear.data.api

import org.openhab.habdroid.wear.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel

/**
 * SSE client for real-time item state updates on the watch tile.
 *
 * Strategy (matching the phone app pattern):
 * 1. Connect to SSE stream for item state changes
 * 2. Use ALIVE heartbeat (every ~10s from server) as liveness signal
 * 3. If no event within 30s → assume dead, reconnect
 * 4. After 3 consecutive quick failures → fall back to polling (15s interval)
 * 5. Polling continues until tile leaves (stop() called)
 *
 * Lifecycle: start() on tile enter, stop() on tile leave.
 */
@Singleton
class TileStateEventSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val credentialStore: CredentialStore,
    private val repository: OpenHabRepository,
    private val itemCache: ItemCache
) {
    companion object {
        private const val TAG = "TileStateSSE"
        private const val EVENTS_PATH = "/rest/events"
        private const val TOPIC_FILTER = "openhab/items/*/statechanged"

        /** Timeout waiting for any event (ALIVE or state change). Server sends ALIVE every ~10s. */
        private const val EVENT_TIMEOUT_MS = 30_000L

        /** Delay between reconnection attempts */
        private const val RECONNECT_DELAY_MS = 5_000L

        /** If connection fails this quickly, count it as a "quick failure" */
        private const val QUICK_FAILURE_THRESHOLD_MS = 10_000L

        /** After this many consecutive quick failures, fall back to polling */
        private const val MAX_QUICK_FAILURES = 3

        /** Polling interval when SSE is unavailable */
        private const val POLL_INTERVAL_MS = 15_000L
    }

    /** Item names to watch for changes. Updated when tile items are loaded. */
    var watchedItems: Set<String> = emptySet()

    private var connectionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Start the SSE connection loop. Handles reconnection and fallback to polling.
     * @param onChanged callback invoked when a watched item's state changes (triggers tile refresh)
     */
    fun start(onChanged: () -> Unit) {
        if (connectionJob?.isActive == true) {
            AppLog.d(TAG, "Already running, skipping start")
            return
        }

        connectionJob = scope.launch {
            val credentials = credentialStore.credentials.first() ?: run {
                AppLog.w(TAG, "No credentials, cannot connect SSE")
                return@launch
            }

            val baseUrl = credentials.serverUrl.trimEnd('/')
            var consecutiveQuickFailures = 0

            // Main loop: try SSE, fall back to polling if unstable
            while (isActive) {
                if (consecutiveQuickFailures >= MAX_QUICK_FAILURES) {
                    AppLog.d(TAG, "SSE unstable ($consecutiveQuickFailures quick failures), switching to polling")
                    pollLoop(onChanged)
                    return@launch // pollLoop runs until cancelled (tile leave)
                }

                val connectTime = System.currentTimeMillis()
                val sseResult = runSseSession(baseUrl, onChanged)

                when (sseResult) {
                    SseResult.CANCELLED -> return@launch // stop() was called
                    SseResult.FAILURE -> {
                        val elapsed = System.currentTimeMillis() - connectTime
                        if (elapsed < QUICK_FAILURE_THRESHOLD_MS) {
                            consecutiveQuickFailures++
                            AppLog.d(TAG, "Quick failure #$consecutiveQuickFailures (${elapsed}ms)")
                        } else {
                            // Connection lasted a while — reset counter
                            consecutiveQuickFailures = 0
                        }
                    }
                    SseResult.TIMEOUT -> {
                        // No events for 30s — reconnect (not a "quick" failure)
                        consecutiveQuickFailures = 0
                        AppLog.d(TAG, "Event timeout, reconnecting")
                    }
                }

                if (isActive) {
                    delay(RECONNECT_DELAY_MS)
                }
            }
        }
    }

    /**
     * Stop the SSE connection and any polling. Called on tile leave.
     */
    fun stop() {
        AppLog.d(TAG, "Stopping")
        connectionJob?.cancel()
        connectionJob = null
    }

    /**
     * Run a single SSE session. Returns when the connection fails, times out, or is cancelled.
     */
    private suspend fun runSseSession(baseUrl: String, onChanged: () -> Unit): SseResult {
        val url = "$baseUrl$EVENTS_PATH?topics=$TOPIC_FILTER"
        AppLog.d(TAG, "Connecting SSE: $url")

        val sseClient = okHttpClient.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .build()

        val channel = Channel<SseEvent>(Channel.BUFFERED)

        val factory = EventSources.createFactory(sseClient)
        val eventSource = factory.newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                AppLog.d(TAG, "SSE connected")
                channel.trySend(SseEvent.Connected)
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                channel.trySend(SseEvent.Data(data))
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                AppLog.w(TAG, "SSE failed: ${t?.message ?: "status ${response?.code}"}")
                channel.trySend(SseEvent.Failed(t))
            }

            override fun onClosed(eventSource: EventSource) {
                AppLog.d(TAG, "SSE closed by server")
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
                    is SseEvent.Data -> handleEvent(event.data, onChanged)
                    is SseEvent.Failed -> {
                        eventSource.cancel()
                        return SseResult.FAILURE
                    }
                    is SseEvent.Closed -> {
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
        }
    }

    /**
     * Polling fallback: fetch states periodically when SSE is unstable.
     * Runs until the coroutine is cancelled (tile leave → stop()).
     */
    private suspend fun pollLoop(onChanged: () -> Unit) {
        AppLog.d(TAG, "Starting poll loop (${POLL_INTERVAL_MS}ms interval)")
        while (true) {
            delay(POLL_INTERVAL_MS)
            try {
                repository.refreshStates()
                    .onSuccess {
                        AppLog.d(TAG, "Poll: states refreshed")
                        onChanged()
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
    private fun handleEvent(data: String, onChanged: () -> Unit) {
        try {
            // Check for ALIVE event (server heartbeat)
            if (data.contains("\"type\":\"ALIVE\"") || data.contains("\"ALIVE\"")) {
                AppLog.d(TAG, "ALIVE heartbeat received")
                return
            }

            // Quick string parsing — avoid full JSON for performance
            val topicStart = data.indexOf("\"topic\":\"") + 9
            if (topicStart < 9) return
            val topicEnd = data.indexOf("\"", topicStart)
            if (topicEnd < 0) return
            val topic = data.substring(topicStart, topicEnd)

            // Topic format: openhab/items/{itemName}/statechanged
            val parts = topic.split("/")
            if (parts.size < 4 || parts[3] != "statechanged") return
            val itemName = parts[2]

            // Extract new state from payload
            val newState = extractNewState(data)

            if (watchedItems.isEmpty() || watchedItems.contains(itemName)) {
                AppLog.d(TAG, "State changed: $itemName → $newState")
                // Update cache directly from SSE event (avoids full refresh)
                if (newState != null) {
                    itemCache.updateItemState(itemName, newState)
                }
                onChanged()
            } else if (newState != null) {
                // Might be a Group member — update if it matches
                itemCache.updateItemState(itemName, newState)
                // Check if this member update changed any visible tile item's state
                if (itemCache.get()?.any { it.item.isGroup && it.item.members?.any { m -> m.name == itemName } == true } == true) {
                    AppLog.d(TAG, "Group member state changed: $itemName → $newState")
                    onChanged()
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Error parsing SSE event", e)
        }
    }

    /**
     * Extract the new state value from SSE event payload.
     * Payload is nested JSON: {"payload":"{\"type\":\"...\",\"value\":\"ON\",...}"}
     */
    private fun extractNewState(data: String): String? {
        return try {
            // Find "value":" in the payload
            val valueKey = "\"value\":\""
            val valueStart = data.indexOf(valueKey)
            if (valueStart < 0) return null
            val stateStart = valueStart + valueKey.length
            val stateEnd = data.indexOf("\"", stateStart)
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

    private sealed interface SseEvent {
        data object Connected : SseEvent
        data class Data(val data: String) : SseEvent
        data class Failed(val throwable: Throwable?) : SseEvent
        data object Closed : SseEvent
    }
}
