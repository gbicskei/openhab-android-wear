package org.openhab.habdroid.wear.phone.sync

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import org.openhab.habdroid.wear.phone.util.AppLog
import org.openhab.habdroid.wear.shared.debug.DebugLog
import org.openhab.habdroid.wear.shared.debug.DebugLogEntry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads debug log entries published by the watch via the Wearable DataClient.
 * Listens for changes to the /openhab/debug_log DataItem and merges watch
 * entries into the shared [DebugLog] buffer.
 *
 * Exposes a [watchEntries] StateFlow for the UI to observe.
 */
@Singleton
class DebugLogReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) : DataClient.OnDataChangedListener {

    companion object {
        private const val TAG = "DebugLogReader"
        private const val PATH_DEBUG_LOG = "/openhab/debug_log"
        private const val KEY_ENTRIES = "entries"
    }

    private val dataClient: DataClient by lazy { Wearable.getDataClient(context) }

    private val _watchEntries = MutableStateFlow<List<DebugLogEntry>>(emptyList())
    /** Watch-side debug entries, updated live when DataClient changes. */
    val watchEntries: StateFlow<List<DebugLogEntry>> = _watchEntries.asStateFlow()

    private var listening = false

    /**
     * Start listening for DataClient changes. Call from Activity onResume or similar.
     */
    fun startListening() {
        if (!listening) {
            dataClient.addListener(this)
            listening = true
        }
        // Also do an initial read
        readCurrent()
    }

    /**
     * Stop listening. Call from Activity onPause or similar.
     */
    fun stopListening() {
        if (listening) {
            dataClient.removeListener(this)
            listening = false
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == PATH_DEBUG_LOG
            ) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val entriesJson = dataMap.getString(KEY_ENTRIES) ?: "[]"
                parseAndMerge(entriesJson)
            }
        }
    }

    /**
     * Read the current DataItem (for initial load without waiting for a change event).
     */
    private fun readCurrent() {
        try {
            val uri = android.net.Uri.Builder()
                .scheme("wear")
                .path(PATH_DEBUG_LOG)
                .build()
            dataClient.getDataItems(uri).addOnSuccessListener { items ->
                for (item in items) {
                    if (item.uri.path == PATH_DEBUG_LOG) {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        val entriesJson = dataMap.getString(KEY_ENTRIES) ?: "[]"
                        parseAndMerge(entriesJson)
                    }
                }
                items.release()
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to read current debug log DataItem", e)
        }
    }

    private fun parseAndMerge(entriesJson: String) {
        try {
            val entries = json.decodeFromString<List<DebugLogEntry>>(entriesJson)
            _watchEntries.value = entries
            DebugLog.addRemoteEntries(entries)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to parse watch debug log", e)
        }
    }
}
