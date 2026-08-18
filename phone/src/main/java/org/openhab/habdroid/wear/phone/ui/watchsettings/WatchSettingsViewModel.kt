package org.openhab.habdroid.wear.phone.ui.watchsettings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.openhab.habdroid.wear.phone.data.PhoneCredentialStore
import org.openhab.habdroid.wear.phone.data.ServerBackupRepository
import org.openhab.habdroid.wear.phone.sync.PhoneDataLayerSender
import org.openhab.habdroid.wear.phone.util.AppLog
import org.openhab.habdroid.wear.shared.sync.SyncConstants
import org.openhab.habdroid.wear.shared.sync.SyncNotificationSettingsPayload
import org.openhab.habdroid.wear.shared.sync.SyncVoiceSettingsPayload
import org.openhab.habdroid.wear.shared.sync.WatchSettingsSnapshot
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * UI state for the Watch Settings screen on the phone.
 */
data class WatchSettingsUiState(
    val loadState: LoadState = LoadState.Loading,
    val snapshot: WatchSettingsSnapshot = WatchSettingsSnapshot(),
    val watchConnected: Boolean = true,
    val backupEnabled: Boolean = false,
    val bindingInstalled: Boolean = false,
    val googleTtsAvailable: Boolean = false,
    val availableVoices: List<String> = emptyList(),
    val voicesLoading: Boolean = false,
    val testPlaying: Boolean = false,
    val restoreState: RestoreState = RestoreState.Idle,
    val errorMessage: String? = null
)

enum class LoadState { Loading, Loaded, Error }
enum class RestoreState { Idle, Restoring, Success, Error }

/**
 * ViewModel for phone-side Watch Settings.
 *
 * On screen open:
 * - Sends PATH_SETTINGS_REQUEST to watch
 * - Listens for PATH_SETTINGS_RESPONSE
 * - Populates UI with current values
 *
 * On user edit:
 * - Immediately sends the change to watch via existing paths (voice/notification settings)
 * - Updates local state
 * - Triggers async server backup if enabled
 *
 * No Save button — changes are instant (Samsung Galaxy Wearable style).
 */
@HiltViewModel
class WatchSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataLayerSender: PhoneDataLayerSender,
    private val credentialStore: PhoneCredentialStore,
    private val backupRepository: ServerBackupRepository,
    private val connectionTester: org.openhab.habdroid.wear.phone.data.ConnectionTester,
    private val json: Json
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchSettingsUiState())
    val uiState: StateFlow<WatchSettingsUiState> = _uiState.asStateFlow()

    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }

    /** Monitor watch connectivity and auto-navigate away on disconnect. */
    private val _watchDisconnected = MutableStateFlow(false)
    val watchDisconnected: StateFlow<Boolean> = _watchDisconnected.asStateFlow()

    /** Debounce job for server backup writes. */
    private var backupJob: Job? = null

    companion object {
        private const val TAG = "WatchSettingsVM"
        private const val SETTINGS_TIMEOUT_MS = 5000L
        private const val BACKUP_DEBOUNCE_MS = 2000L
    }

    init {
        loadSettingsFromWatch()
        observeWatchConnection()
        checkGoogleTtsAvailability()
        checkBindingInstalled()
        _uiState.update { it.copy(backupEnabled = credentialStore.isBackupEnabled) }
    }

    /**
     * Check if the Mobile Audio binding is installed on the config server.
     * Updates UI state and persists the result for sync to watch.
     */
    private fun checkBindingInstalled() {
        viewModelScope.launch {
            val localConfig = credentialStore.localConfig.first()
            if (localConfig == null || !localConfig.isConfigured) {
                _uiState.update { it.copy(bindingInstalled = false) }
                return@launch
            }
            val installed = connectionTester.checkBindingInstalled(
                serverUrl = localConfig.serverUrl,
                username = localConfig.username,
                password = localConfig.password,
                apiToken = localConfig.apiToken
            )
            credentialStore.saveBindingInstalled(installed)
            _uiState.update { it.copy(bindingInstalled = installed) }
            AppLog.d(TAG, "Binding installed check: $installed")
        }
    }

    /**
     * Check if Google TTS is available: API key configured + server reachable.
     */
    private fun checkGoogleTtsAvailability() {
        viewModelScope.launch {
            val hasKey = credentialStore.hasGoogleTtsApiKey
            if (!hasKey) {
                _uiState.update { it.copy(googleTtsAvailable = false) }
                return@launch
            }
            // Test server reachability using the watch connection (it already validated)
            // For the phone side, if we got this far with a connected watch, the server is reachable.
            // But let's be explicit: check if config server credentials are set.
            val localConfig = credentialStore.localConfig.first()
            val available = hasKey && localConfig != null && localConfig.isConfigured
            _uiState.update { it.copy(googleTtsAvailable = available) }
        }
    }

    /**
     * Request current settings from the watch via MessageClient.
     */
    fun loadSettingsFromWatch() {
        _uiState.update { it.copy(loadState = LoadState.Loading, errorMessage = null) }

        viewModelScope.launch {
            try {
                val node = dataLayerSender.getConnectedWatch()
                if (node == null) {
                    _uiState.update { it.copy(loadState = LoadState.Error, errorMessage = "Watch not connected") }
                    _watchDisconnected.value = true
                    return@launch
                }

                val response = withTimeoutOrNull(SETTINGS_TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        val listener = MessageClient.OnMessageReceivedListener { event: MessageEvent ->
                            if (event.path == SyncConstants.PATH_SETTINGS_RESPONSE) {
                                try {
                                    val snapshot = json.decodeFromString<WatchSettingsSnapshot>(
                                        String(event.data, Charsets.UTF_8)
                                    )
                                    if (cont.isActive) {
                                        cont.resume(snapshot)
                                    }
                                } catch (e: Exception) {
                                    AppLog.e(TAG, "Failed to parse settings response", e)
                                }
                            }
                        }
                        messageClient.addListener(listener)
                        cont.invokeOnCancellation { messageClient.removeListener(listener) }

                        viewModelScope.launch {
                            messageClient.sendMessage(
                                node.id,
                                SyncConstants.PATH_SETTINGS_REQUEST,
                                ByteArray(0)
                            ).await()
                        }
                    }
                }

                if (response != null) {
                    _uiState.update { it.copy(loadState = LoadState.Loaded, snapshot = response) }
                    AppLog.d(TAG, "Settings loaded from watch")
                } else {
                    _uiState.update { it.copy(loadState = LoadState.Error, errorMessage = "Watch did not respond") }
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to load settings from watch", e)
                _uiState.update { it.copy(loadState = LoadState.Error, errorMessage = e.message) }
            }
        }
    }

    private fun observeWatchConnection() {
        viewModelScope.launch {
            dataLayerSender.watchConnectionState(intervalMs = 3_000L).collect { info ->
                if (info == null && _uiState.value.loadState == LoadState.Loaded) {
                    _watchDisconnected.value = true
                }
                _uiState.update { it.copy(watchConnected = info != null) }
            }
        }
    }

    // ─── Voice settings ───

    fun setVoiceCommandsEnabled(enabled: Boolean) {
        updateAndSyncVoice { it.copy(voiceCommandsEnabled = enabled) }
    }

    fun setReadAloudEnabled(enabled: Boolean) {
        updateAndSyncVoice { it.copy(readAloudEnabled = enabled) }
    }

    fun setUseServerTts(enabled: Boolean) {
        updateAndSyncVoice { it.copy(useServerTts = enabled) }
        if (enabled && _uiState.value.availableVoices.isEmpty()) {
            loadVoices()
        }
    }

    fun loadVoices() {
        _uiState.update { it.copy(voicesLoading = true) }
        viewModelScope.launch {
            try {
                val apiKey = credentialStore.getGoogleTtsApiKey()
                if (apiKey.isBlank()) {
                    _uiState.update { it.copy(voicesLoading = false, availableVoices = emptyList()) }
                    return@launch
                }
                val lang = java.util.Locale.getDefault().toLanguageTag()
                val voices = connectionTester.fetchGoogleVoices(apiKey, lang)
                _uiState.update { it.copy(voicesLoading = false, availableVoices = voices.map { v -> v.id }) }
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to load voices: ${e.message}")
                _uiState.update { it.copy(voicesLoading = false, availableVoices = emptyList()) }
            }
        }
    }

    fun selectVoice(voice: String) {
        updateAndSyncVoice { it.copy(serverTtsVoice = voice) }
    }

    fun testVoice() {
        _uiState.update { it.copy(testPlaying = true) }
        viewModelScope.launch {
            try {
                // Sync current voice settings to watch first, then trigger test
                val snapshot = _uiState.value.snapshot
                sendVoiceSettingsToWatch(snapshot)
                kotlinx.coroutines.delay(500) // let settings apply

                val node = dataLayerSender.getConnectedWatch()
                if (node != null) {
                    val messageClient = com.google.android.gms.wearable.Wearable.getMessageClient(context)
                    messageClient.sendMessage(
                        node.id,
                        SyncConstants.PATH_TTS_TEST,
                        ByteArray(0)
                    ).await()
                    AppLog.d(TAG, "TTS test sent to watch")
                    // Give the watch time to play audio
                    kotlinx.coroutines.delay(4000)
                } else {
                    AppLog.w(TAG, "TTS test: watch not connected")
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "Test voice failed: ${e.message}")
            } finally {
                _uiState.update { it.copy(testPlaying = false) }
            }
        }
    }

    fun setServerTtsVoice(voice: String) {
        updateAndSyncVoice { it.copy(serverTtsVoice = voice) }
    }

    fun setTtsVolume(volume: Float) {
        updateAndSyncVoice { it.copy(ttsVolume = volume) }
    }

    fun setTtsSpeechRate(rate: Float) {
        updateAndSyncVoice { it.copy(ttsSpeechRate = rate) }
    }

    fun setTtsPitch(pitch: Float) {
        updateAndSyncVoice { it.copy(ttsPitch = pitch) }
    }

    private fun updateAndSyncVoice(transform: (WatchSettingsSnapshot) -> WatchSettingsSnapshot) {
        val newSnapshot = transform(_uiState.value.snapshot)
        _uiState.update { it.copy(snapshot = newSnapshot) }
        sendVoiceSettingsToWatch(newSnapshot)
        scheduleBackupWrite()
    }

    private fun sendVoiceSettingsToWatch(snapshot: WatchSettingsSnapshot) {
        viewModelScope.launch {
            val payload = SyncVoiceSettingsPayload(
                voiceCommandsEnabled = snapshot.voiceCommandsEnabled,
                readAloudEnabled = snapshot.readAloudEnabled,
                useServerTts = snapshot.useServerTts,
                serverTtsVoice = snapshot.serverTtsVoice,
                volume = snapshot.ttsVolume,
                speechRate = snapshot.ttsSpeechRate,
                pitch = snapshot.ttsPitch
            )
            dataLayerSender.sendVoiceSettings(json.encodeToString(payload))
                .onFailure { AppLog.w(TAG, "Failed to send voice settings: ${it.message}") }
        }
    }

    // ─── Notification settings ───

    fun setNotificationsEnabled(enabled: Boolean) {
        updateAndSyncNotifications { it.copy(notificationsEnabled = enabled) }
    }

    fun setNotificationReadAloud(enabled: Boolean) {
        updateAndSyncNotifications { it.copy(notificationReadAloud = enabled) }
    }

    fun setChimeEnabled(enabled: Boolean) {
        updateAndSyncNotifications { it.copy(chimeEnabled = enabled) }
    }

    fun setChimeSound(sound: String) {
        updateAndSyncNotifications { it.copy(chimeSound = sound) }
    }

    fun setNotificationVolume(volume: Float) {
        updateAndSyncNotifications { it.copy(notificationVolume = volume) }
    }

    fun setMinReadAloudPriority(priority: String) {
        updateAndSyncNotifications { it.copy(minReadAloudPriority = priority) }
    }

    private fun updateAndSyncNotifications(transform: (WatchSettingsSnapshot) -> WatchSettingsSnapshot) {
        val newSnapshot = transform(_uiState.value.snapshot)
        _uiState.update { it.copy(snapshot = newSnapshot) }
        sendNotificationSettingsToWatch(newSnapshot)
        scheduleBackupWrite()
    }

    private fun sendNotificationSettingsToWatch(snapshot: WatchSettingsSnapshot) {
        viewModelScope.launch {
            val payload = SyncNotificationSettingsPayload(
                notificationsEnabled = snapshot.notificationsEnabled,
                readAloudEnabled = snapshot.notificationReadAloud,
                chimeEnabled = snapshot.chimeEnabled,
                chimeSound = snapshot.chimeSound,
                notificationVolume = snapshot.notificationVolume,
                minReadAloudPriority = snapshot.minReadAloudPriority
            )
            dataLayerSender.sendNotificationSettings(json.encodeToString(payload))
                .onFailure { AppLog.w(TAG, "Failed to send notification settings: ${it.message}") }
        }
    }

    // ─── Debug mode ───

    fun setDebugMode(enabled: Boolean) {
        val newSnapshot = _uiState.value.snapshot.copy(debugMode = enabled)
        _uiState.update { it.copy(snapshot = newSnapshot) }
        // Update phone-side store so HomeScreen debug log card visibility is in sync
        viewModelScope.launch {
            credentialStore.setDebugMode(enabled)
            // Push to watch via credentials path (PATH_CONFIG carries debugMode)
            val creds = credentialStore.credentials.first() ?: return@launch
            dataLayerSender.sendCredentials(creds, debugMode = enabled)
                .onFailure { AppLog.w(TAG, "Failed to sync debug mode to watch: ${it.message}") }
        }
        scheduleBackupWrite()
        AppLog.d(TAG, "Debug mode set to $enabled")
    }

    // ─── Backup toggle ───

    fun setBackupEnabled(enabled: Boolean) {
        _uiState.update { it.copy(backupEnabled = enabled) }
        viewModelScope.launch { credentialStore.setBackupEnabled(enabled) }
        if (enabled) {
            // Ensure the backup item exists on the server and do an initial write
            scheduleBackupWrite()
        }
    }

    /**
     * Debounced write of current settings to server backup.
     * Called after every setting change when backup is enabled.
     */
    private fun scheduleBackupWrite() {
        if (!_uiState.value.backupEnabled) return
        backupJob?.cancel()
        backupJob = viewModelScope.launch {
            delay(BACKUP_DEBOUNCE_MS)
            writeBackupToServer()
        }
    }

    private suspend fun writeBackupToServer() {
        val localConfig = credentialStore.localConfig.first() ?: return
        if (!localConfig.isConfigured) return

        val deviceName = credentialStore.currentUserKey.ifBlank { return }
        val snapshot = _uiState.value.snapshot

        backupRepository.ensureBackupItemExists(localConfig, deviceName)
            .onFailure { AppLog.w(TAG, "Failed to create backup item: ${it.message}") }

        backupRepository.writeBackup(localConfig, deviceName, snapshot)
            .onSuccess { AppLog.d(TAG, "Backup written to server") }
            .onFailure { AppLog.w(TAG, "Failed to write backup: ${it.message}") }
    }

    // ─── Restore from backup ───

    /**
     * Reads backup from server and pushes all settings to the watch.
     */
    fun restoreFromBackup() {
        _uiState.update { it.copy(restoreState = RestoreState.Restoring) }

        viewModelScope.launch {
            val localConfig = credentialStore.localConfig.first()
            if (localConfig == null || !localConfig.isConfigured) {
                _uiState.update { it.copy(restoreState = RestoreState.Error, errorMessage = "Config server not configured") }
                return@launch
            }

            val deviceName = credentialStore.currentUserKey
            if (deviceName.isBlank()) {
                _uiState.update { it.copy(restoreState = RestoreState.Error, errorMessage = "Device name not configured") }
                return@launch
            }

            val result = backupRepository.readBackup(localConfig, deviceName)
            result
                .onSuccess { snapshot ->
                    if (snapshot == null) {
                        _uiState.update { it.copy(restoreState = RestoreState.Error, errorMessage = "No backup found for '$deviceName'") }
                        return@launch
                    }
                    // Push restored settings to watch
                    _uiState.update { it.copy(snapshot = snapshot) }
                    sendVoiceSettingsToWatch(snapshot)
                    sendNotificationSettingsToWatch(snapshot)
                    _uiState.update { it.copy(restoreState = RestoreState.Success) }
                    AppLog.d(TAG, "Settings restored from server backup")
                }
                .onFailure { e ->
                    _uiState.update { it.copy(restoreState = RestoreState.Error, errorMessage = e.message) }
                    AppLog.w(TAG, "Restore failed: ${e.message}")
                }
        }
    }

    fun restoreFromBackup(snapshot: WatchSettingsSnapshot) {
        _uiState.update { it.copy(restoreState = RestoreState.Restoring, snapshot = snapshot) }

        viewModelScope.launch {
            sendVoiceSettingsToWatch(snapshot)
            sendNotificationSettingsToWatch(snapshot)
            _uiState.update { it.copy(restoreState = RestoreState.Success) }
            AppLog.d(TAG, "Settings restored from provided snapshot and pushed to watch")
        }
    }

    fun clearRestoreState() {
        _uiState.update { it.copy(restoreState = RestoreState.Idle) }
    }
}
