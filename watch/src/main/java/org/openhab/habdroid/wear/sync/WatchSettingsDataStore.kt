package org.openhab.habdroid.wear.sync

import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import org.openhab.habdroid.wear.BuildConfig
import org.openhab.habdroid.wear.shared.sync.WatchSettingsPayload
import org.openhab.habdroid.wear.util.AppLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bidirectional DataItem store for watch settings + status.
 *
 * Path: /openhab/watch-settings
 *
 * The watch owns the full state in memory. Every mutation writes the complete DataItem
 * atomically — no partial updates. The phone reads it instantly (offline-capable) and
 * writes settings fields back when the user changes them (watch applies via onDataChanged).
 *
 * Field ownership:
 * - Settings (voice, notifications, theme, debug): phone writes → watch applies
 * - Status (configTimestamp, screenWidthDp, appVersion, hasSpeaker): watch writes → phone reads
 */
@Singleton
class WatchSettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "WatchSettingsDS"
    }

    private val dataClient: DataClient by lazy { Wearable.getDataClient(context) }

    /** Whether this device has a speaker (cached at startup). */
    private val hasSpeaker: Boolean by lazy {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)
    }

    /** In-memory current state. Initialized from DataItem on first read, or defaults. */
    @Volatile
    var current: WatchSettingsPayload = WatchSettingsPayload(
        appVersion = BuildConfig.VERSION_NAME,
        hasSpeaker = true // will be updated in init
    )
        private set

    /**
     * Initialize from the existing DataItem (if any). Call once on app startup.
     * Merges persisted settings with fresh status values (version, speaker).
     */
    suspend fun initialize() {
        val existing = readFromDataItem()
        current = if (existing != null) {
            // Preserve settings from DataItem, refresh status fields
            existing.copy(
                appVersion = BuildConfig.VERSION_NAME,
                hasSpeaker = hasSpeaker
            )
        } else {
            WatchSettingsPayload(
                appVersion = BuildConfig.VERSION_NAME,
                hasSpeaker = hasSpeaker
            )
        }
        // Write back to ensure fresh status is published
        writeToDataItem()
        AppLog.d(TAG, "Initialized: theme=${current.theme}, debug=${current.debugMode}, configTs=${current.configTimestamp}")
    }

    // ─── Settings mutations (applied from phone via onDataChanged) ───

    /**
     * Apply settings received from the phone (via onDataChanged).
     * Only updates settings fields — preserves watch-owned status fields.
     */
    suspend fun applySettingsFromPhone(incoming: WatchSettingsPayload) {
        current = current.copy(
            voiceCommandsEnabled = incoming.voiceCommandsEnabled,
            readAloudEnabled = incoming.readAloudEnabled,
            useServerTts = incoming.useServerTts,
            serverTtsVoice = incoming.serverTtsVoice,
            speechRate = incoming.speechRate,
            pitch = incoming.pitch,
            notificationsEnabled = incoming.notificationsEnabled,
            notificationReadAloudEnabled = incoming.notificationReadAloudEnabled,
            chimeEnabled = incoming.chimeEnabled,
            chimeSound = incoming.chimeSound,
            minReadAloudPriority = incoming.minReadAloudPriority,
            theme = incoming.theme,
            debugMode = incoming.debugMode
        )
        // Don't write back — phone just wrote this. Avoid infinite loop.
        AppLog.d(TAG, "Applied settings from phone: theme=${current.theme}, debug=${current.debugMode}")
    }

    // ─── Status mutations (watch-owned, triggers DataItem write) ───

    /**
     * Update config timestamp after a successful tile config load.
     */
    suspend fun writeConfigTimestamp(timestamp: String) {
        current = current.copy(configTimestamp = timestamp)
        writeToDataItem()
    }

    /**
     * Update screen width after first tile request.
     */
    suspend fun writeScreenWidthDp(widthDp: Int) {
        if (widthDp > 0 && widthDp != current.screenWidthDp) {
            current = current.copy(screenWidthDp = widthDp)
            writeToDataItem()
        }
    }

    /**
     * Update theme (watch-side change, e.g. from settings PATH_SETTINGS legacy handler).
     */
    suspend fun writeTheme(theme: String) {
        if (theme.isNotBlank() && theme != current.theme) {
            current = current.copy(theme = theme)
            writeToDataItem()
        }
    }

    /**
     * Update debug mode on the watch side.
     */
    suspend fun writeDebugMode(enabled: Boolean) {
        current = current.copy(debugMode = enabled)
        writeToDataItem()
    }

    /**
     * Write the full current state to the DataItem. Call after any status change.
     */
    suspend fun writeToDataItem() {
        try {
            val request = PutDataMapRequest.create(WatchSettingsPayload.DATA_PATH).apply {
                current.toDataMap(dataMap)
            }.asPutDataRequest().setUrgent()
            dataClient.putDataItem(request).await()
            AppLog.d(TAG, "DataItem written: configTs=${current.configTimestamp}, theme=${current.theme}")
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to write DataItem", e)
        }
    }

    // ─── Read ───

    /**
     * Read the current DataItem from the Wearable DataClient.
     * Returns null if no DataItem exists yet.
     */
    private suspend fun readFromDataItem(): WatchSettingsPayload? {
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
            AppLog.w(TAG, "Failed to read DataItem", e)
            null
        }
    }
}
