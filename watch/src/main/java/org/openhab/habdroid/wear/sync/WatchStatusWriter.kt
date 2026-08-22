package org.openhab.habdroid.wear.sync

import android.content.Context
import android.content.pm.PackageManager
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
 * Now delegates to [WatchSettingsDataStore] for the primary DataItem at /openhab/watch-settings.
 * Also writes to the legacy /openhab/status path for backward compatibility with
 * older phone app versions that haven't been updated yet.
 */
@Singleton
class WatchStatusWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val watchSettingsDataStore: WatchSettingsDataStore
) {
    companion object {
        private const val TAG = "WatchStatusWriter"
        private const val LEGACY_PATH_STATUS = "/openhab/status"
        private const val KEY_CONFIG_TIMESTAMP = "configTimestamp"
        private const val KEY_THEME = "theme"
        private const val KEY_SCREEN_WIDTH_DP = "screenWidthDp"
        private const val KEY_APP_VERSION = "appVersion"
        private const val KEY_HAS_SPEAKER = "hasSpeaker"
    }

    private val dataClient by lazy { Wearable.getDataClient(context) }

    /** Whether this device has a speaker (cached at startup) */
    private val hasSpeaker: Boolean by lazy {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)
    }

    /** In-memory state for legacy path writes */
    private var currentConfigTimestamp: String = ""
    private var currentTheme: String = ""
    private var currentScreenWidthDp: Int = 0
    private var currentAppVersion: String = ""

    /**
     * Write the app version. Called once on app startup.
     */
    suspend fun writeAppVersion(version: String) {
        currentAppVersion = version
        writeLegacyStatus()
    }

    /**
     * Write the config timestamp after a successful cold load.
     */
    suspend fun writeConfigTimestamp(timestamp: String) {
        currentConfigTimestamp = timestamp
        watchSettingsDataStore.writeConfigTimestamp(timestamp)
        writeLegacyStatus()
    }

    /**
     * Write the current theme name.
     */
    suspend fun writeTheme(themeName: String) {
        currentTheme = themeName
        watchSettingsDataStore.writeTheme(themeName)
        writeLegacyStatus()
    }

    /**
     * Write the watch screen width in dp.
     */
    suspend fun writeScreenWidthDp(widthDp: Int) {
        if (widthDp > 0 && widthDp != currentScreenWidthDp) {
            currentScreenWidthDp = widthDp
            watchSettingsDataStore.writeScreenWidthDp(widthDp)
            writeLegacyStatus()
        }
    }

    /**
     * Write both config timestamp and theme together.
     */
    suspend fun writeStatus(configTimestamp: String, themeName: String) {
        currentConfigTimestamp = configTimestamp
        currentTheme = themeName
        watchSettingsDataStore.writeConfigTimestamp(configTimestamp)
        watchSettingsDataStore.writeTheme(themeName)
        writeLegacyStatus()
    }

    /**
     * Write full status — called internally to trigger DataItem init on app start.
     */
    suspend fun writeFullStatus() {
        val _traceStart = System.currentTimeMillis()
        AppLog.d(TAG, "→ writeFullStatus()")
        writeLegacyStatus()
        AppLog.d(TAG, "← writeFullStatus() ${System.currentTimeMillis() - _traceStart}ms")
    }

    /**
     * Write to the legacy /openhab/status path for backward compatibility.
     */
    private suspend fun writeLegacyStatus() {
        try {
            val request = PutDataMapRequest.create(LEGACY_PATH_STATUS).apply {
                dataMap.putString(KEY_CONFIG_TIMESTAMP, currentConfigTimestamp)
                dataMap.putString(KEY_THEME, currentTheme)
                if (currentScreenWidthDp > 0) {
                    dataMap.putInt(KEY_SCREEN_WIDTH_DP, currentScreenWidthDp)
                }
                if (currentAppVersion.isNotBlank()) {
                    dataMap.putString(KEY_APP_VERSION, currentAppVersion)
                }
                dataMap.putBoolean(KEY_HAS_SPEAKER, hasSpeaker)
            }.asPutDataRequest().setUrgent()
            dataClient.putDataItem(request).await()
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to write legacy status", e)
        }
    }
}
