package org.openhab.habdroid.wear.sync

import android.content.Context
import org.openhab.habdroid.wear.util.AppLog
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes watch status to the Wearable DataClient so the phone can read it.
 *
 * DataItem path: /openhab/status
 * Keys:
 *   - configTimestamp: latest config timestamp from server (String)
 *   - theme: current watch theme name (String)
 *
 * Maintains an in-memory copy of both fields. Every write persists the full
 * status atomically — no field is ever lost due to partial writes.
 *
 * The phone reads this DataItem to:
 * 1. Show an out-of-sync indicator if configTimestamp doesn't match the server's latest
 * 2. Read the current watch theme on tile editor open
 */
@Singleton
class WatchStatusWriter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "WatchStatusWriter"
        private const val PATH_STATUS = "/openhab/status"
        private const val KEY_CONFIG_TIMESTAMP = "configTimestamp"
        private const val KEY_THEME = "theme"
    }

    private val dataClient by lazy { Wearable.getDataClient(context) }

    /** In-memory state — always written atomically */
    private var currentConfigTimestamp: String = ""
    private var currentTheme: String = ""

    /**
     * Write the config timestamp after a successful cold load.
     */
    suspend fun writeConfigTimestamp(timestamp: String) {
        currentConfigTimestamp = timestamp
        writeFullStatus()
    }

    /**
     * Write the current theme name.
     */
    suspend fun writeTheme(themeName: String) {
        currentTheme = themeName
        writeFullStatus()
    }

    /**
     * Write both config timestamp and theme together.
     * Used after cold load when both values are known.
     */
    suspend fun writeStatus(configTimestamp: String, themeName: String) {
        currentConfigTimestamp = configTimestamp
        currentTheme = themeName
        writeFullStatus()
    }

    /**
     * Persists the full in-memory status to the DataClient atomically.
     */
    private suspend fun writeFullStatus() {
        try {
            val request = PutDataMapRequest.create(PATH_STATUS).apply {
                dataMap.putString(KEY_CONFIG_TIMESTAMP, currentConfigTimestamp)
                dataMap.putString(KEY_THEME, currentTheme)
            }.asPutDataRequest().setUrgent()
            dataClient.putDataItem(request).await()
            AppLog.d(TAG, "Wrote status: configTimestamp=$currentConfigTimestamp, theme=$currentTheme")
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to write status", e)
        }
    }
}
