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

    /**
     * Write the config timestamp after a successful cold load.
     * Called from OpenHabRepository after fetching tile config.
     */
    suspend fun writeConfigTimestamp(timestamp: String) {
        try {
            val request = PutDataMapRequest.create(PATH_STATUS).apply {
                dataMap.putString(KEY_CONFIG_TIMESTAMP, timestamp)
                // Preserve existing theme if already set
            }.asPutDataRequest().setUrgent()
            dataClient.putDataItem(request).await()
            AppLog.d(TAG, "Wrote configTimestamp: $timestamp")
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to write configTimestamp", e)
        }
    }

    /**
     * Write the current theme name.
     * Called after theme change (bezel picker or phone message).
     */
    suspend fun writeTheme(themeName: String) {
        try {
            val request = PutDataMapRequest.create(PATH_STATUS).apply {
                dataMap.putString(KEY_THEME, themeName)
            }.asPutDataRequest().setUrgent()
            dataClient.putDataItem(request).await()
            AppLog.d(TAG, "Wrote theme: $themeName")
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to write theme", e)
        }
    }

    /**
     * Write both config timestamp and theme together.
     * Used after cold load when both values are known.
     */
    suspend fun writeStatus(configTimestamp: String, themeName: String) {
        try {
            val request = PutDataMapRequest.create(PATH_STATUS).apply {
                dataMap.putString(KEY_CONFIG_TIMESTAMP, configTimestamp)
                dataMap.putString(KEY_THEME, themeName)
            }.asPutDataRequest().setUrgent()
            dataClient.putDataItem(request).await()
            AppLog.d(TAG, "Wrote status: configTimestamp=$configTimestamp, theme=$themeName")
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to write status", e)
        }
    }
}
