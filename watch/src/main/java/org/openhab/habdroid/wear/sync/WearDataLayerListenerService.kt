package org.openhab.habdroid.wear.sync

import org.openhab.habdroid.wear.util.AppLog
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
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
import org.openhab.habdroid.wear.data.repository.ItemCache
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import org.openhab.habdroid.wear.notification.FcmRegistrationWorker
import org.openhab.habdroid.wear.shared.model.ServerCredentials
import org.openhab.habdroid.wear.shared.sync.SyncConfigPayload
import org.openhab.habdroid.wear.shared.sync.SyncConstants
import org.openhab.habdroid.wear.shared.sync.SyncNotificationSettingsPayload
import org.openhab.habdroid.wear.shared.sync.SyncVoiceSettingsPayload
import org.openhab.habdroid.wear.shared.sync.WatchSettingsSnapshot
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
    lateinit var repository: org.openhab.habdroid.wear.data.repository.OpenHabRepository

    @Inject
    lateinit var watchStatusWriter: WatchStatusWriter

    @Inject
    lateinit var serverTtsPlayer: org.openhab.habdroid.wear.util.ServerTtsPlayer

    @Inject
    lateinit var voicePreferenceStore: org.openhab.habdroid.wear.data.repository.VoicePreferenceStore

    @Inject
    lateinit var assistantRegistrar: org.openhab.habdroid.wear.ui.voice.AssistantRegistrar

    @Inject
    lateinit var notificationPreferenceStore: org.openhab.habdroid.wear.data.repository.NotificationPreferenceStore

    @Inject
    lateinit var cachingDns: org.openhab.habdroid.wear.data.api.CachingDns

    @Inject
    lateinit var tileStateEventSource: org.openhab.habdroid.wear.data.api.TileStateEventSource

    @Inject
    lateinit var serverSelector: org.openhab.habdroid.wear.data.api.ServerSelector

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        AppLog.d(TAG, "Message received on path: ${messageEvent.path}")

        when (messageEvent.path) {
            SyncConstants.PATH_CONFIG -> handleConfigMessage(messageEvent)
            SyncConstants.PATH_RELOAD -> handleReloadMessage()
            SyncConstants.PATH_VOICE_SETTINGS -> handleVoiceSettingsMessage(messageEvent)
            SyncConstants.PATH_NOTIFICATION_SETTINGS -> handleNotificationSettingsMessage(messageEvent)
            SyncConstants.PATH_ASSISTANT_STATUS_REQUEST -> handleAssistantStatusRequest(messageEvent)
            SyncConstants.PATH_ASSISTANT_REGISTER -> handleAssistantRegister()
            SyncConstants.PATH_SETTINGS_REQUEST -> handleSettingsRequest(messageEvent)
            SyncConstants.PATH_TTS_TEST -> handleTtsTest()
            SyncConstants.PATH_VERSION_REQUEST -> handleVersionRequest(messageEvent)
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
                    password = configData.password,
                    userKey = configData.userKey
                )
                credentialStore.saveCredentials(credentials)
                AppLog.d(TAG, "Credentials saved from phone sync (userKey=${configData.userKey.ifBlank { "<default>" }})")

                // Save device name for FCM registration
                credentialStore.saveDeviceName(configData.deviceName)

                // Save binding installed status
                credentialStore.saveBindingInstalled(configData.bindingInstalled)

                // Save local server URL and credentials for Happy Eyeballs racing
                credentialStore.saveLocalServerUrl(
                    url = configData.localServerUrl,
                    username = configData.localUsername,
                    password = configData.localPassword,
                    apiToken = configData.localApiToken
                )
                if (configData.localServerUrl.isNotBlank()) {
                    AppLog.d(TAG, "Local server URL saved: ${configData.localServerUrl}")
                }

                // Reset ServerSelector so next request re-races with new URLs
                serverSelector.reset()

                // Seed DNS cache with phone-resolved IPs
                if (configData.resolvedIps.isNotEmpty()) {
                    try {
                        val host = java.net.URI(configData.serverUrl).host
                        if (host != null) {
                            cachingDns.seedCache(host, configData.resolvedIps)
                            AppLog.d(TAG, "DNS cache seeded: $host → ${configData.resolvedIps.joinToString()}")
                        }
                    } catch (e: Exception) {
                        AppLog.w(TAG, "Failed to seed DNS cache: ${e.message}")
                    }
                }

                // Save Google TTS API key if provided
                if (configData.googleTtsApiKey.isNotBlank()) {
                    voicePreferenceStore.setServerTtsApiKey(configData.googleTtsApiKey)
                    voicePreferenceStore.setServerTtsEnabled(true)
                    voicePreferenceStore.setVoiceResponseSpoken(true)
                    AppLog.d(TAG, "Google TTS API key synced from phone")
                }

                // Update debug mode (persist to DataStore)
                AppLog.debugMode = configData.debugMode
                credentialStore.setDebugMode(configData.debugMode)

                // Register FCM token with cloud for push notifications
                FcmRegistrationWorker.schedule(this@WearDataLayerListenerService)

                // Restart SSE so it picks up the new server URL immediately
                tileStateEventSource.stop()

                // Perform reload if requested (config is fully saved at this point)
                if (configData.triggerReload) {
                    AppLog.d(TAG, "Config includes triggerReload — clearing cache and refreshing")
                    repository.clearAndReload()
                        .onSuccess { count ->
                            AppLog.d(TAG, "Reload complete: $count items loaded")
                        }
                        .onFailure { e ->
                            AppLog.e(TAG, "Reload failed: ${e.message}")
                        }

                    // Refresh complications
                    ComplicationDataSourceUpdateRequester.create(
                        this@WearDataLayerListenerService,
                        android.content.ComponentName(this@WearDataLayerListenerService, org.openhab.habdroid.wear.complication.OpenHabComplicationService::class.java)
                    ).requestUpdateAll()
                }

                // Trigger tile refresh after credential update
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

            // Also refresh complications
            ComplicationDataSourceUpdateRequester.create(
                this@WearDataLayerListenerService,
                android.content.ComponentName(this@WearDataLayerListenerService, org.openhab.habdroid.wear.complication.OpenHabComplicationService::class.java)
            ).requestUpdateAll()
        }
    }

    private fun handleVoiceSettingsMessage(messageEvent: MessageEvent) {
        try {
            val payload = String(messageEvent.data, Charsets.UTF_8)
            AppLog.d(TAG, "Voice settings received (${payload.length} chars)")

            val settings = json.decodeFromString<SyncVoiceSettingsPayload>(payload)

            serviceScope.launch {
                voicePreferenceStore.setVoiceCommandsEnabled(settings.voiceCommandsEnabled)
                voicePreferenceStore.setVoiceResponseSpoken(settings.readAloudEnabled)
                voicePreferenceStore.setServerTtsEnabled(settings.useServerTts)
                voicePreferenceStore.setServerTtsVoice(settings.serverTtsVoice)
                voicePreferenceStore.setTtsVolume(settings.volume)
                voicePreferenceStore.setTtsSpeechRate(settings.speechRate)
                voicePreferenceStore.setTtsPitch(settings.pitch)
                AppLog.d(TAG, "Voice settings saved from phone sync")
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to parse voice settings message", e)
        }
    }

    private fun handleNotificationSettingsMessage(messageEvent: MessageEvent) {
        try {
            val payload = String(messageEvent.data, Charsets.UTF_8)
            AppLog.d(TAG, "Notification settings received (${payload.length} chars)")

            val settings = json.decodeFromString<SyncNotificationSettingsPayload>(payload)

            serviceScope.launch {
                notificationPreferenceStore.setNotificationsEnabled(settings.notificationsEnabled)
                notificationPreferenceStore.setReadAloudEnabled(settings.readAloudEnabled)
                notificationPreferenceStore.setChimeEnabled(settings.chimeEnabled)
                notificationPreferenceStore.setChimeSound(settings.chimeSound)
                notificationPreferenceStore.setNotificationVolume(settings.notificationVolume)
                notificationPreferenceStore.setMinReadAloudPriority(settings.minReadAloudPriority)
                AppLog.d(TAG, "Notification settings saved from phone sync")
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to parse notification settings message", e)
        }
    }

    private fun handleTtsTest() {
        AppLog.d(TAG, "TTS test requested from phone")
        serviceScope.launch {
            try {
                val useServerTts = voicePreferenceStore.serverTtsEnabled.first()
                val volume = voicePreferenceStore.ttsVolume.first()
                if (useServerTts) {
                    val apiKey = voicePreferenceStore.serverTtsApiKey.first()
                    val voice = voicePreferenceStore.serverTtsVoice.first()
                    serverTtsPlayer.setApiKey(apiKey)
                    serverTtsPlayer.speakFromServer("This is a voice test.", voice = voice, volume = volume)
                } else {
                    // System TTS
                    val rate = voicePreferenceStore.ttsSpeechRate.first()
                    val pitch = voicePreferenceStore.ttsPitch.first()
                    val tts = android.speech.tts.TextToSpeech(this@WearDataLayerListenerService, null)
                    kotlinx.coroutines.delay(800)
                    tts.setSpeechRate(rate)
                    tts.setPitch(pitch)
                    val params = android.os.Bundle().apply {
                        putFloat(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
                    }
                    tts.speak("This is a voice test.", android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "test")
                    kotlinx.coroutines.delay(3000)
                    tts.shutdown()
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "TTS test failed", e)
            }
        }
    }

    private fun handleVersionRequest(messageEvent: MessageEvent) {
        AppLog.d(TAG, "Version request received from phone")
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

    companion object {
        private const val TAG = "WearDataLayerListener"
    }

    private fun handleSettingsRequest(messageEvent: MessageEvent) {
        AppLog.d(TAG, "Settings request received from phone")
        serviceScope.launch {
            try {
                val snapshot = WatchSettingsSnapshot(
                    debugMode = AppLog.debugMode,
                    voiceCommandsEnabled = voicePreferenceStore.voiceCommandsEnabled.first(),
                    readAloudEnabled = voicePreferenceStore.voiceResponseSpoken.first(),
                    useServerTts = voicePreferenceStore.serverTtsEnabled.first(),
                    serverTtsVoice = voicePreferenceStore.serverTtsVoice.first(),
                    ttsVolume = voicePreferenceStore.ttsVolume.first(),
                    ttsSpeechRate = voicePreferenceStore.ttsSpeechRate.first(),
                    ttsPitch = voicePreferenceStore.ttsPitch.first(),
                    notificationsEnabled = notificationPreferenceStore.notificationsEnabled.first(),
                    notificationReadAloud = notificationPreferenceStore.readAloudEnabled.first(),
                    chimeEnabled = notificationPreferenceStore.chimeEnabled.first(),
                    chimeSound = notificationPreferenceStore.chimeSound.first(),
                    notificationVolume = notificationPreferenceStore.notificationVolume.first(),
                    minReadAloudPriority = notificationPreferenceStore.minReadAloudPriority.first()
                )

                val responseJson = json.encodeToString(WatchSettingsSnapshot.serializer(), snapshot)
                val responseBytes = responseJson.toByteArray(Charsets.UTF_8)
                val messageClient = com.google.android.gms.wearable.Wearable.getMessageClient(this@WearDataLayerListenerService)
                messageClient.sendMessage(
                    messageEvent.sourceNodeId,
                    SyncConstants.PATH_SETTINGS_RESPONSE,
                    responseBytes
                ).await()
                AppLog.d(TAG, "Sent settings snapshot (${responseJson.length} chars, ${responseBytes.size} bytes)")
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to send settings response", e)
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
}
