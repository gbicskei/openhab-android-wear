package org.openhab.habdroid.wear.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.openhab.habdroid.wear.util.AppLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin facade that delegates all status writes to [WatchSettingsDataStore].
 *
 * Keeps existing callers (OpenHabRepository, OpenHabTileService, SettingsActivity, etc.)
 * working without changes. All data flows through the single DataItem at /openhab/watch-settings.
 */
@Singleton
class WatchStatusWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val watchSettingsDataStore: WatchSettingsDataStore
) {
    companion object {
        private const val TAG = "WatchStatusWriter"
    }

    suspend fun writeAppVersion(version: String) {
        // App version is set during WatchSettingsDataStore.initialize() — no-op here
        AppLog.d(TAG, "writeAppVersion: $version (handled by WatchSettingsDataStore.initialize)")
    }

    suspend fun writeConfigTimestamp(timestamp: String) {
        watchSettingsDataStore.writeConfigTimestamp(timestamp)
    }

    suspend fun writeTheme(themeName: String) {
        watchSettingsDataStore.writeTheme(themeName)
    }

    suspend fun writeScreenWidthDp(widthDp: Int) {
        watchSettingsDataStore.writeScreenWidthDp(widthDp)
    }

    suspend fun writeStatus(configTimestamp: String, themeName: String) {
        watchSettingsDataStore.writeConfigTimestamp(configTimestamp)
        watchSettingsDataStore.writeTheme(themeName)
    }

    suspend fun writeFullStatus() {
        watchSettingsDataStore.writeToDataItem()
        AppLog.d(TAG, "writeFullStatus delegated to WatchSettingsDataStore")
    }
}
