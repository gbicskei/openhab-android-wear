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
 * The DataClient listener is always active for the process lifetime (started eagerly
 * on singleton creation). This ensures watch log entries are captured even when the
 * Debug Log screen is not visible, avoiding gaps caused by the 5-minute buffer
 * retention window on the watch side.
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

    init {
        // Always listen for watch debug log changes — don't tie to screen lifecycle.
        // This ensures entries are captured even when the Debug Log screen is closed.
        dataClient.addListener(this)
        readCurrent()
    }

    /**
     * Trigger a fresh read of the current DataItem. Call when the Debug Log screen
     * opens to pick up any entries that arrived before the flow was collected.
     */
    fun refreshFromDataItem() {
        readCurrent()
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

    /**
     * Delete the watch debug log DataItem so stale entries don't re-sync after clear.
     */
    suspend fun clearWatchData() {
        try {
            val uri = android.net.Uri.Builder()
                .scheme("wear")
                .path(PATH_DEBUG_LOG)
                .authority("*")
                .build()
            dataClient.deleteDataItems(uri).await()
            _watchEntries.value = emptyList()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to clear watch debug DataItem", e)
        }
    }
}
