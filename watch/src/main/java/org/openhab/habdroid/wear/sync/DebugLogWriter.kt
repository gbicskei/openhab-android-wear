package org.openhab.habdroid.wear.sync

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.openhab.habdroid.wear.shared.debug.DebugLog
import org.openhab.habdroid.wear.shared.debug.DebugLogEntry
import org.openhab.habdroid.wear.shared.debug.LogSource
import org.openhab.habdroid.wear.util.AppLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes the watch-side debug log buffer to the Wearable DataClient
 * so the phone companion can read and display it.
 *
 * DataItem path: /openhab/debug_log
 * Keys:
 *   - entries: JSON-serialized list of [DebugLogEntry]
 *   - updatedAt: timestamp of last write (forces DataClient change detection)
 */
@Singleton
class DebugLogWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    companion object {
        private const val TAG = "DebugLogWriter"
        const val PATH_DEBUG_LOG = "/openhab/debug_log"
        const val KEY_ENTRIES = "entries"
        const val KEY_UPDATED_AT = "updatedAt"
    }

    private val dataClient by lazy { Wearable.getDataClient(context) }

    /**
     * Publishes the current watch debug log entries to the DataClient.
     * Call this after errors occur or periodically.
     */
    suspend fun publish() {
        try {
            val watchEntries = DebugLog.entries().filter { it.source == LogSource.WATCH }
            val entriesJson = json.encodeToString<List<DebugLogEntry>>(watchEntries)

            val request = PutDataMapRequest.create(PATH_DEBUG_LOG).apply {
                dataMap.putString(KEY_ENTRIES, entriesJson)
                dataMap.putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            dataClient.putDataItem(request).await()
            AppLog.d(TAG, "Published ${watchEntries.size} debug log entries")
        } catch (e: Exception) {
            // Don't use AppLog.e here to avoid infinite recursion
            android.util.Log.w(TAG, "Failed to publish debug log", e)
        }
    }
}
