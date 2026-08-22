package org.openhab.habdroid.wear.phone.sync

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import org.openhab.habdroid.wear.phone.util.AppLog
import org.openhab.habdroid.wear.shared.sync.WatchSettingsPayload
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phone-side client for the shared watch settings DataItem.
 *
 * Reads: settings + status (instant, offline-capable — no round-trip to watch needed).
 * Writes: settings fields only (phone never writes status fields — those are watch-owned).
 *
 * After writing, the watch receives an onDataChanged callback and applies the new settings.
 */
@Singleton
class WatchSettingsDataItemClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "WatchSettingsDI"
    }

    private val dataClient: DataClient by lazy { Wearable.getDataClient(context) }

    /**
     * Read the current watch settings from the DataItem.
     * Returns null if no DataItem exists yet (watch has never synced).
     */
    suspend fun read(): WatchSettingsPayload? {
        return try {
            val dataItems = dataClient.getDataItems(
                android.net.Uri.parse("wear://${WatchSettingsPayload.DATA_PATH}")
            ).await()
            val result = dataItems.firstOrNull()?.let { item ->
                WatchSettingsPayload.fromDataMap(DataMapItem.fromDataItem(item).dataMap)
            }
            dataItems.release()
            result
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to read watch settings DataItem", e)
            null
        }
    }

    /**
     * Write settings to the DataItem. The watch will receive onDataChanged and apply them.
     *
     * IMPORTANT: This preserves the watch-owned status fields by reading the current DataItem
     * first, then merging in the new settings. If no DataItem exists, status fields default.
     */
    suspend fun writeSettings(settings: WatchSettingsPayload): Result<Unit> = runCatching {
        // Read current to preserve watch-owned status fields
        val current = read()
        val merged = if (current != null) {
            settings.copy(
                configTimestamp = current.configTimestamp,
                screenWidthDp = current.screenWidthDp,
                appVersion = current.appVersion,
                hasSpeaker = current.hasSpeaker
            )
        } else {
            settings // No existing DataItem — status fields will be defaults until watch writes
        }

        val request = PutDataMapRequest.create(WatchSettingsPayload.DATA_PATH).apply {
            merged.toDataMap(dataMap)
        }.asPutDataRequest().setUrgent()

        dataClient.putDataItem(request).await()
        AppLog.d(TAG, "Settings written to DataItem: theme=${merged.theme}, debug=${merged.debugMode}")
    }

    /**
     * Read just the config timestamp. Convenience for sync detection.
     */
    suspend fun readConfigTimestamp(): String? = read()?.configTimestamp

    /**
     * Read just the theme. Convenience for tile editor.
     */
    suspend fun readTheme(): String? = read()?.theme

    /**
     * Read the watch screen width in dp.
     */
    suspend fun readScreenWidthDp(): Int? = read()?.screenWidthDp?.takeIf { it > 0 }

    /**
     * Read the watch app version.
     */
    suspend fun readAppVersion(): String? = read()?.appVersion?.takeIf { it.isNotBlank() }

    /**
     * Read whether the watch has a speaker.
     */
    suspend fun readHasSpeaker(): Boolean = read()?.hasSpeaker ?: true
}
