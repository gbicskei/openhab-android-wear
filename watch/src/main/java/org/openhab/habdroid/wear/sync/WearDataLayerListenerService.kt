package org.openhab.habdroid.wear.sync

import org.openhab.habdroid.wear.util.AppLog
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import org.openhab.habdroid.wear.data.repository.CredentialStore
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import org.openhab.habdroid.wear.notification.FcmRegistrationWorker
import org.openhab.habdroid.wear.shared.model.ServerCredentials
import org.openhab.habdroid.wear.shared.sync.ConnectionPayload
import org.openhab.habdroid.wear.shared.sync.SyncConstants
import org.openhab.habdroid.wear.shared.sync.WatchSettingsPayload
import org.openhab.habdroid.wear.tile.OpenHabTileService
import androidx.wear.tiles.TileService
import javax.inject.Inject

/**
 * Listens for:
 * - MessageClient messages: connection credentials, reload, TTS test, assistant, version
 * - DataItem changes: watch settings written by phone at /openhab/watch-settings
 */
@AndroidEntryPoint
class WearDataLayerListenerService : WearableListenerService() {

    @Inject lateinit var credentialStore: CredentialStore
    @Inject lateinit var json: Json
    @Inject lateinit var repository: OpenHabRepository
    @Inject lateinit var watchStatusWriter: WatchStatusWriter
    @Inject lateinit var serverTtsPlayer: org.openhab.habdroid.wear.util.ServerTtsPlayer
    @Inject lateinit var voicePreferenceStore: org.openhab.habdroid.wear.data.repository.VoicePreferenceStore
    @Inject lateinit var assistantRegistrar: org.openhab.habdroid.wear.ui.voice.AssistantRegistrar
    @Inject lateinit var notificationPreferenceStore: org.openhab.habdroid.wear.data.repository.NotificationPreferenceStore
    @Inject lateinit var cachingDns: org.openhab.habdroid.wear.data.api.CachingDns
    @Inject lateinit var tileStateEventSource: org.openhab.habdroid.wear.data.api.TileStateEventSource
    @Inject lateinit var serverSelector: org.openhab.habdroid.wear.data.api.ServerSelector
    @Inject lateinit var themeStore: org.openhab.habdroid.wear.data.repository.ThemeStore
    @Inject lateinit var watchSettingsDataStore: WatchSettingsDataStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        AppLog.d(TAG, "Message received on path: ${messageEvent.path}")

        when (messageEvent.path) {
            SyncConstants.PATH_CONNECTION -> handleConnectionMessage(messageEvent)
            SyncConstants.PATH_RELOAD -> handleReloadMessage()
            SyncConstants.PATH_ASSISTANT_STATUS_REQUEST -> handleAssistantStatusRequest(messageEvent)
            SyncConstants.PATH_ASSISTANT_REGISTER -> handleAssistantRegister()
            SyncConstants.PATH_TTS_TEST -> handleTtsTest()
            SyncConstants.PATH_VERSION_REQUEST -> handleVersionRequest(messageEvent)
            else -> super.onMessageReceived(messageEvent)
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == WatchSettingsPayload.DATA_PATH
            ) {
                AppLog.d(TAG, "DataItem changed: ${WatchSettingsPayload.DATA_PATH}")
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val incoming = WatchSettingsPayload.fromDataMap(dataMap)

                serviceScope.launch {
                    val current = watchSettingsDataStore.current
                    if (incoming.settingsEqual(current)) {
                        // Settings unchanged — this is likely our own write echoed back.
                        // But check if the phone's read-modify-write corrupted status fields
                        // (e.g., appVersion from a stale DataItem read). If so, re-assert.
                        if (incoming.appVersion != current.appVersion ||
                            incoming.appVersionCode != current.appVersionCode ||
                            incoming.configTimestamp != current.configTimestamp ||
                            incoming.screenWidthDp != current.screenWidthDp) {
                            AppLog.d(TAG, "DataItem has stale status fields — re-asserting " +
                                "(appVersion: ${incoming.appVersion}→${current.appVersion})")
                            watchSettingsDataStore.writeToDataItem()
                        } else {
                            AppLog.d(TAG, "DataItem change is our own write, ignoring")
                        }
                        return@launch
                    }

                    AppLog.d(TAG, "Phone wrote settings — applying: theme=${incoming.theme}, debug=${incoming.debugMode}")
                    watchSettingsDataStore.applySettingsFromPhone(incoming)

                    // Apply to preference stores
                    voicePreferenceStore.setVoiceCommandsEnabled(incoming.voiceCommandsEnabled)
                    voicePreferenceStore.setVoiceResponseSpoken(incoming.readAloudEnabled)
                    voicePreferenceStore.setServerTtsEnabled(incoming.useServerTts)
                    voicePreferenceStore.setServerTtsVoice(incoming.serverTtsVoice)
                    voicePreferenceStore.setTtsSpeechRate(incoming.speechRate)
                    voicePreferenceStore.setTtsPitch(incoming.pitch)

                    notificationPreferenceStore.setNotificationsEnabled(incoming.notificationsEnabled)
                    notificationPreferenceStore.setReadAloudEnabled(incoming.notificationReadAloudEnabled)
                    notificationPreferenceStore.setChimeEnabled(incoming.chimeEnabled)
                    notificationPreferenceStore.setChimeSound(incoming.chimeSound)
                    notificationPreferenceStore.setMinReadAloudPriority(incoming.minReadAloudPriority)

                    if (incoming.theme.isNotBlank()) {
                        val theme = org.openhab.habdroid.wear.data.repository.TileTheme.fromName(incoming.theme)
                        themeStore.setTheme(theme)
                    }

                    AppLog.debugMode = incoming.debugMode
                    credentialStore.setDebugMode(incoming.debugMode)

                    // Refresh tile
                    TileService.getUpdater(this@WearDataLayerListenerService)
                        .requestUpdate(OpenHabTileService::class.java)
                }
            }
        }
    }

    private fun handleConnectionMessage(messageEvent: MessageEvent) {
        try {
            val payload = String(messageEvent.data, Charsets.UTF_8)
            AppLog.d(TAG, "Connection payload received (${payload.length} chars)")

            val connectionData = json.decodeFromString<ConnectionPayload>(payload)

            serviceScope.launch {
                val credentials = ServerCredentials(
                    serverUrl = connectionData.serverUrl,
                    username = connectionData.username,
                    password = connectionData.password,
                    userKey = connectionData.userKey
                )
                credentialStore.saveCredentials(credentials)
                AppLog.d(TAG, "Credentials saved (userKey=${connectionData.userKey.ifBlank { "<default>" }})")

                credentialStore.saveDeviceName(connectionData.deviceName)
                credentialStore.saveBindingInstalled(connectionData.bindingInstalled)

                credentialStore.saveLocalServerUrl(
                    url = connectionData.localServerUrl,
                    username = connectionData.localUsername,
                    password = connectionData.localPassword,
                    apiToken = connectionData.localApiToken
                )
                if (connectionData.localServerUrl.isNotBlank()) {
                    AppLog.d(TAG, "Local server URL saved: ${connectionData.localServerUrl}")
                }

                serverSelector.reset()

                // Seed DNS cache with phone-resolved IPs
                if (connectionData.resolvedIps.isNotEmpty()) {
                    try {
                        val host = java.net.URI(connectionData.serverUrl).host
                        if (host != null) {
                            cachingDns.seedCache(host, connectionData.resolvedIps)
                            AppLog.d(TAG, "DNS cache seeded: $host → ${connectionData.resolvedIps.joinToString()}")
                        }
                    } catch (e: Exception) {
                        AppLog.w(TAG, "Failed to seed DNS cache: ${e.message}")
                    }
                }

                // Save Google TTS API key
                if (connectionData.googleTtsApiKey.isNotBlank()) {
                    voicePreferenceStore.setServerTtsApiKey(connectionData.googleTtsApiKey)
                }

                // Register FCM token
                FcmRegistrationWorker.schedule(this@WearDataLayerListenerService)

                // Restart SSE
                tileStateEventSource.stop()

                // Reload if requested
                if (connectionData.triggerReload) {
                    AppLog.d(TAG, "Connection includes triggerReload — clearing cache and refreshing")
                    repository.clearAndReload()
                        .onSuccess { count -> AppLog.d(TAG, "Reload complete: $count items loaded") }
                        .onFailure { e -> AppLog.e(TAG, "Reload failed: ${e.message}") }

                    ComplicationDataSourceUpdateRequester.create(
                        this@WearDataLayerListenerService,
                        android.content.ComponentName(this@WearDataLayerListenerService, org.openhab.habdroid.wear.complication.OpenHabComplicationService::class.java)
                    ).requestUpdateAll()
                }

                TileService.getUpdater(this@WearDataLayerListenerService)
                    .requestUpdate(OpenHabTileService::class.java)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to parse connection message", e)
        }
    }

    private fun handleReloadMessage() {
        AppLog.d(TAG, "Reload message received")
        serviceScope.launch {
            repository.clearAndReload()
                .onSuccess { count -> AppLog.d(TAG, "Reload complete: $count items loaded") }
                .onFailure { e -> AppLog.e(TAG, "Reload failed: ${e.message}") }
            TileService.getUpdater(this@WearDataLayerListenerService)
                .requestUpdate(OpenHabTileService::class.java)
            ComplicationDataSourceUpdateRequester.create(
                this@WearDataLayerListenerService,
                android.content.ComponentName(this@WearDataLayerListenerService, org.openhab.habdroid.wear.complication.OpenHabComplicationService::class.java)
            ).requestUpdateAll()
        }
    }

    private fun handleTtsTest() {
        AppLog.d(TAG, "TTS test requested from phone")
        serviceScope.launch {
            try {
                val useServerTts = voicePreferenceStore.serverTtsEnabled.first()
                if (useServerTts) {
                    val apiKey = voicePreferenceStore.serverTtsApiKey.first()
                    val voice = voicePreferenceStore.serverTtsVoice.first()
                    serverTtsPlayer.setApiKey(apiKey)
                    serverTtsPlayer.speakFromServer("This is a voice test.", voice = voice)
                } else {
                    val rate = voicePreferenceStore.ttsSpeechRate.first()
                    val pitch = voicePreferenceStore.ttsPitch.first()
                    val tts = android.speech.tts.TextToSpeech(this@WearDataLayerListenerService, null)
                    kotlinx.coroutines.delay(800)
                    tts.setSpeechRate(rate)
                    tts.setPitch(pitch)
                    tts.speak("This is a voice test.", android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "test")
                    kotlinx.coroutines.delay(3000)
                    tts.shutdown()
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "TTS test failed", e)
            }
        }
    }

    private fun handleVersionRequest(messageEvent: MessageEvent) {
        AppLog.d(TAG, "Version request received")
        serviceScope.launch {
            try {
                val versionName = org.openhab.habdroid.wear.BuildConfig.VERSION_NAME
                val messageClient = com.google.android.gms.wearable.Wearable.getMessageClient(this@WearDataLayerListenerService)
                messageClient.sendMessage(
                    messageEvent.sourceNodeId,
                    SyncConstants.PATH_VERSION_RESPONSE,
                    versionName.toByteArray(Charsets.UTF_8)
                ).await()
                AppLog.d(TAG, "Sent version response: $versionName")
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to send version response", e)
            }
        }
    }

    private fun handleAssistantStatusRequest(messageEvent: MessageEvent) {
        AppLog.d(TAG, "Assistant status request received")
        val status = assistantRegistrar.getStatus(this)
        val response = "${status.hasPermission}|${status.isRegistered}"
        serviceScope.launch {
            try {
                val messageClient = com.google.android.gms.wearable.Wearable.getMessageClient(this@WearDataLayerListenerService)
                messageClient.sendMessage(
                    messageEvent.sourceNodeId,
                    SyncConstants.PATH_ASSISTANT_STATUS_RESPONSE,
                    response.toByteArray(Charsets.UTF_8)
                ).await()
                AppLog.d(TAG, "Sent assistant status: $response")
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to send assistant status", e)
            }
        }
    }

    private fun handleAssistantRegister() {
        AppLog.d(TAG, "Assistant register command received")
        val success = assistantRegistrar.register(this)
        AppLog.d(TAG, "Assistant register result: $success")
    }

    companion object {
        private const val TAG = "WearDataLayerListener"
    }
}
