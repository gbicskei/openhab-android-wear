package org.openhab.habdroid.wear.phone.sync

import android.content.Intent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.openhab.habdroid.wear.phone.ui.MainActivity
import org.openhab.habdroid.wear.shared.sync.SyncConstants

/**
 * Listens for messages and data changes from the watch via Data Layer.
 * Handles the "open app" request from the watch's "Setup on Phone" button,
 * version response for the version compatibility handshake,
 * and DataItem changes (watch status including app version).
 */
class PhoneWearListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            PATH_OPEN_APP -> {
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
            }
            PATH_OPEN_TILE_EDITOR -> {
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(EXTRA_NAVIGATE_TO, "tile_design")
                }
                startActivity(intent)
            }
            SyncConstants.PATH_VERSION_RESPONSE -> {
                val watchVersionName = String(messageEvent.data, Charsets.UTF_8)
                WatchVersionHolder.update(watchVersionName)
            }
            else -> super.onMessageReceived(messageEvent)
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == PATH_STATUS
            ) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val appVersion = dataMap.getString(KEY_APP_VERSION)
                if (!appVersion.isNullOrBlank()) {
                    WatchVersionHolder.update(appVersion)
                }
                // Adopt theme change from watch (watch is source of truth)
                val theme = dataMap.getString(KEY_THEME)
                if (!theme.isNullOrBlank()) {
                    ThemeHolder.update(theme, applicationContext)
                }
            }
        }
    }

    companion object {
        const val PATH_OPEN_APP = "/openhab/open-app"
        const val PATH_OPEN_TILE_EDITOR = "/openhab/open-tile-editor"
        const val EXTRA_NAVIGATE_TO = "navigate_to"
        private const val PATH_STATUS = "/openhab/status"
        private const val KEY_APP_VERSION = "appVersion"
        private const val KEY_THEME = "theme"
    }
}
