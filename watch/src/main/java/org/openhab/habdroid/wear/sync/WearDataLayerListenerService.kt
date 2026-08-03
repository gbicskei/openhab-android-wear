package org.openhab.habdroid.wear.sync

import org.openhab.habdroid.wear.util.AppLog
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.openhab.habdroid.wear.data.repository.CredentialStore
import org.openhab.habdroid.wear.data.repository.ItemCache
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import org.openhab.habdroid.wear.shared.model.ServerCredentials
import org.openhab.habdroid.wear.shared.sync.SyncConfigPayload
import org.openhab.habdroid.wear.shared.sync.SyncConstants
import org.openhab.habdroid.wear.tile.OpenHabTileService
import androidx.wear.tiles.TileService
import javax.inject.Inject

/**
 * Listens for credential sync messages from the phone app via the Wear Data Layer API.
 *
 * The phone app sends a message to path "/openhab/config" containing a JSON payload
 * with server URL and credentials. This service receives it, parses it, and stores
 * the credentials locally so the watch can operate independently.
 */
@AndroidEntryPoint
class WearDataLayerListenerService : WearableListenerService() {

    @Inject
    lateinit var credentialStore: CredentialStore

    @Inject
    lateinit var json: Json

    @Inject
    lateinit var itemCache: ItemCache

    @Inject
    lateinit var themeStore: org.openhab.habdroid.wear.data.repository.ThemeStore

    @Inject
    lateinit var repository: org.openhab.habdroid.wear.data.repository.OpenHabRepository

    @Inject
    lateinit var watchStatusWriter: WatchStatusWriter

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        AppLog.d(TAG, "Message received on path: ${messageEvent.path}")

        when (messageEvent.path) {
            SyncConstants.PATH_CONFIG -> handleConfigMessage(messageEvent)
            SyncConstants.PATH_RELOAD -> handleReloadMessage()
            SyncConstants.PATH_THEME -> handleThemeMessage(messageEvent)
            else -> super.onMessageReceived(messageEvent)
        }
    }

    private fun handleConfigMessage(messageEvent: MessageEvent) {
        try {
            val payload = String(messageEvent.data, Charsets.UTF_8)
            AppLog.d(TAG, "Config payload received (${payload.length} chars)")

            val configData = json.decodeFromString<SyncConfigPayload>(payload)

            serviceScope.launch {
                val credentials = ServerCredentials(
                    serverUrl = configData.serverUrl,
                    username = configData.username,
                    password = configData.password
                )
                credentialStore.saveCredentials(credentials)
                AppLog.d(TAG, "Credentials saved from phone sync")
                // Also trigger tile refresh after credential update
                TileService.getUpdater(this@WearDataLayerListenerService)
                    .requestUpdate(OpenHabTileService::class.java)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to parse config message", e)
        }
    }

    private fun handleReloadMessage() {
        AppLog.d(TAG, "Reload message received — clearing cache and refreshing tile")
        serviceScope.launch {
            repository.clearAndReload()
                .onSuccess { count ->
                    AppLog.d(TAG, "Reload complete: $count items loaded")
                }
                .onFailure { e ->
                    AppLog.e(TAG, "Reload failed: ${e.message}")
                }
            TileService.getUpdater(this@WearDataLayerListenerService)
                .requestUpdate(OpenHabTileService::class.java)
        }
    }

    private fun handleThemeMessage(messageEvent: MessageEvent) {
        val themeName = String(messageEvent.data, Charsets.UTF_8).trim()
        AppLog.d(TAG, "Theme message received: $themeName")
        serviceScope.launch {
            val theme = org.openhab.habdroid.wear.data.repository.TileTheme.fromName(themeName)
            themeStore.setTheme(theme)
            // Write theme to DataClient so phone can read it
            watchStatusWriter.writeTheme(themeName)
            // Refresh tile to apply new theme
            TileService.getUpdater(this@WearDataLayerListenerService)
                .requestUpdate(OpenHabTileService::class.java)
        }
    }

    companion object {
        private const val TAG = "WearDataLayerListener"
    }
}
