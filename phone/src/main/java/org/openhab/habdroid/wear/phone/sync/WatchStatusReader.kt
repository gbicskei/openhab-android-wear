package org.openhab.habdroid.wear.phone.sync

import android.content.Context
import org.openhab.habdroid.wear.phone.util.AppLog
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the watch status DataItem written by the watch via WatchStatusWriter.
 *
 * DataItem path: /openhab/status
 * Keys:
 *   - configTimestamp: latest config timestamp the watch has loaded
 *   - theme: current watch theme name
 *
 * Used by:
 * - HomeScreen: compare configTimestamp with server's latest to show out-of-sync indicator
 * - TileDesignScreen: read current watch theme to set the theme selector
 */
@Singleton
class WatchStatusReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "WatchStatusReader"
        private const val PATH_STATUS = "/openhab/status"
        private const val KEY_CONFIG_TIMESTAMP = "configTimestamp"
        private const val KEY_THEME = "theme"
    }

    private val dataClient: DataClient by lazy { Wearable.getDataClient(context) }

    /**
     * Read the watch status DataItem.
     * Returns null if no DataItem exists (watch hasn't synced yet).
     */
    suspend fun readStatus(): WatchStatus? {
        return try {
            val dataItems = dataClient.getDataItems(
                android.net.Uri.parse("wear://$PATH_STATUS")
            ).await()

            val result = dataItems.firstOrNull()?.let { item ->
                val dataMap = DataMapItem.fromDataItem(item).dataMap
                WatchStatus(
                    configTimestamp = dataMap.getString(KEY_CONFIG_TIMESTAMP),
                    theme = dataMap.getString(KEY_THEME)
                )
            }

            dataItems.release()
            AppLog.d(TAG, "Read status: $result")
            result
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to read watch status", e)
            null
        }
    }

    /**
     * Read just the watch theme. Convenience method for the tile editor.
     */
    suspend fun readTheme(): String? {
        return readStatus()?.theme
    }

    /**
     * Read just the config timestamp. Convenience method for sync detection.
     */
    suspend fun readConfigTimestamp(): String? {
        return readStatus()?.configTimestamp
    }
}

/**
 * Watch status data read from the DataClient.
 */
data class WatchStatus(
    val configTimestamp: String?,
    val theme: String?
)
