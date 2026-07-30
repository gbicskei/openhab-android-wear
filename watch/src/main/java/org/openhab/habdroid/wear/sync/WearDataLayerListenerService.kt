package org.openhab.habdroid.wear.sync

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.openhab.habdroid.wear.data.repository.CredentialStore
import org.openhab.habdroid.wear.shared.model.ServerCredentials
import org.openhab.habdroid.wear.shared.sync.SyncConfigPayload
import org.openhab.habdroid.wear.shared.sync.SyncConstants
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Message received on path: ${messageEvent.path}")

        when (messageEvent.path) {
            SyncConstants.PATH_CONFIG -> handleConfigMessage(messageEvent)
            else -> super.onMessageReceived(messageEvent)
        }
    }

    private fun handleConfigMessage(messageEvent: MessageEvent) {
        try {
            val payload = String(messageEvent.data, Charsets.UTF_8)
            Log.d(TAG, "Config payload received (${payload.length} chars)")

            val configData = json.decodeFromString<SyncConfigPayload>(payload)

            serviceScope.launch {
                val credentials = ServerCredentials(
                    serverUrl = configData.serverUrl,
                    username = configData.username,
                    password = configData.password
                )
                credentialStore.saveCredentials(credentials)
                Log.d(TAG, "Credentials saved from phone sync")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse config message", e)
        }
    }

    companion object {
        private const val TAG = "WearDataLayerListener"
    }
}
