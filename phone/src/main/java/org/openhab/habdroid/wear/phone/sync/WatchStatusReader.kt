package org.openhab.habdroid.wear.phone.sync

import android.content.Context
import org.openhab.habdroid.wear.phone.util.AppLog
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import org.openhab.habdroid.wear.shared.sync.WatchSettingsPayload
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the watch status from the unified DataItem written by the watch via WatchSettingsDataStore.
 *
 * DataItem path: /openhab/watch-settings (shared bidirectional DataItem)
 * Status fields (watch writes, phone reads):
 *   - configTimestamp: latest config timestamp the watch has loaded
 *   - theme: current watch theme name
 *   - screenWidthDp: watch screen width in dp
 *   - appVersion: watch app version
 *
 * Used by:
 * - HomeScreen: compare configTimestamp with server's latest to show out-of-sync indicator
 * - TileDesignScreen: read current watch theme to set the theme selector
 * - DebugLogViewModel: read watch info for debug log export header
 */
@Singleton
class WatchStatusReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "WatchStatusReader"
    }

    private val dataClient: DataClient by lazy { Wearable.getDataClient(context) }

    /**
     * Read the watch status from the unified settings DataItem.
     * Returns null if no DataItem exists (watch hasn't synced yet).
     */
    suspend fun readStatus(): WatchStatus? {
        return try {
            val dataItems = dataClient.getDataItems(
                android.net.Uri.parse("wear://${WatchSettingsPayload.DATA_PATH}")
            ).await()

            val result = dataItems.firstOrNull()?.let { item ->
                val payload = WatchSettingsPayload.fromDataMap(
                    DataMapItem.fromDataItem(item).dataMap
                )
                WatchStatus(
                    configTimestamp = payload.configTimestamp.ifBlank { null },
                    theme = payload.theme.ifBlank { null },
                    screenWidthDp = payload.screenWidthDp.takeIf { it > 0 },
                    appVersion = payload.appVersion.ifBlank { null },
                    appVersionCode = payload.appVersionCode.takeIf { it > 0 }
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
     * Read the watch screen width in dp. Returns null if never synced.
     */
    suspend fun readScreenWidthDp(): Int? {
        return readStatus()?.screenWidthDp
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
    val theme: String?,
    val screenWidthDp: Int? = null,
    val appVersion: String? = null,
    val appVersionCode: Int? = null
)
