package org.openhab.habdroid.wear.data.api

import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.openhab.habdroid.wear.data.repository.CredentialStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SSE client that connects to the openHAB events stream and notifies
 * when wearTile item states change. Used to trigger real-time tile refreshes.
 *
 * Lifecycle: start() when tile becomes visible, stop() when tile leaves.
 */
@Singleton
class TileStateEventSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val credentialStore: CredentialStore
) {
    companion object {
        private const val TAG = "TileStateSSE"
        private const val EVENTS_PATH = "/rest/events"
        private const val TOPIC_FILTER = "openhab/items/*/statechanged"
    }

    private var eventSource: EventSource? = null
    private var onStateChanged: (() -> Unit)? = null

    /** Item names to watch for changes. Updated when tile items are loaded. */
    var watchedItems: Set<String> = emptySet()

    /**
     * Start listening for state change events.
     * @param onChanged callback invoked when a watched item's state changes
     */
    fun start(onChanged: () -> Unit) {
        if (eventSource != null) {
            Log.d(TAG, "Already connected, skipping start")
            return
        }

        onStateChanged = onChanged

        val credentials = runBlocking { credentialStore.credentials.first() } ?: run {
            Log.w(TAG, "No credentials, cannot connect SSE")
            return
        }

        val url = "${credentials.serverUrl.trimEnd('/')}$EVENTS_PATH?topics=$TOPIC_FILTER"
        Log.d(TAG, "Connecting to: $url")

        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .build()

        val factory = EventSources.createFactory(okHttpClient)
        eventSource = factory.newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.d(TAG, "SSE connected")
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                handleEvent(data)
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                Log.w(TAG, "SSE connection failed: ${t?.message ?: response?.code}")
                // Will auto-reconnect on next start() call from onTileEnterEvent
                this@TileStateEventSource.eventSource = null
            }

            override fun onClosed(eventSource: EventSource) {
                Log.d(TAG, "SSE connection closed")
                this@TileStateEventSource.eventSource = null
            }
        })
    }

    /**
     * Stop listening and close the SSE connection.
     */
    fun stop() {
        Log.d(TAG, "Stopping SSE")
        eventSource?.cancel()
        eventSource = null
        onStateChanged = null
    }

    /**
     * Parse SSE event data and trigger callback if it's a relevant item state change.
     *
     * Event format:
     * {"topic":"openhab/items/BDR_MainLight/statechanged","payload":"{\"type\":\"ItemStateChangedEvent\",...}","type":"ItemStateChangedEvent"}
     */
    private fun handleEvent(data: String) {
        try {
            // Quick string check — avoid full JSON parsing for performance
            val topicStart = data.indexOf("\"topic\":\"") + 9
            if (topicStart < 9) return
            val topicEnd = data.indexOf("\"", topicStart)
            if (topicEnd < 0) return
            val topic = data.substring(topicStart, topicEnd)

            // Topic format: openhab/items/{itemName}/statechanged
            val parts = topic.split("/")
            if (parts.size < 4 || parts[3] != "statechanged") return
            val itemName = parts[2]

            if (watchedItems.isEmpty() || watchedItems.contains(itemName)) {
                Log.d(TAG, "State changed: $itemName")
                onStateChanged?.invoke()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing SSE event", e)
        }
    }
}
