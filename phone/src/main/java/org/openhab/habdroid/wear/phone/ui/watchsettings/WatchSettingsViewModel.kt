package org.openhab.habdroid.wear.phone.ui.watchsettings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.tasks.await
import org.openhab.habdroid.wear.phone.data.PhoneCredentialStore
import org.openhab.habdroid.wear.phone.data.ServerBackupRepository
import org.openhab.habdroid.wear.phone.sync.PhoneDataLayerSender
import org.openhab.habdroid.wear.phone.sync.WatchSettingsDataItemClient
import org.openhab.habdroid.wear.phone.util.AppLog
import org.openhab.habdroid.wear.shared.sync.SyncConstants
import org.openhab.habdroid.wear.shared.sync.WatchSettingsSnapshot
import org.openhab.habdroid.wear.shared.sync.WatchSettingsPayload
import javax.inject.Inject

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
    val errorMessage: String? = null,
    val selectedTheme: String = "AMBER",
    /** Whether the watch has a speaker. Defaults to true until synced. */
    val watchHasSpeaker: Boolean = true
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
    private val watchSettingsClient: WatchSettingsDataItemClient
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
        _uiState.update { it.copy(
            backupEnabled = credentialStore.isBackupEnabled,
            selectedTheme = credentialStore.getSelectedTheme()
        ) }
    }

    // ─── Unified settings sync ───

    /**
     * Build the complete [WatchSettingsPayload] from current UI state.
     * Only includes non-credential preferences — no secrets.
     */
    private fun buildSettingsPayload(
        snapshot: WatchSettingsSnapshot = _uiState.value.snapshot,
        theme: String = _uiState.value.selectedTheme
    ): WatchSettingsPayload {
        return WatchSettingsPayload(
            voiceCommandsEnabled = snapshot.voiceCommandsEnabled,
            readAloudEnabled = snapshot.readAloudEnabled,
            useServerTts = snapshot.useServerTts,
            serverTtsVoice = snapshot.serverTtsVoice,
            speechRate = snapshot.ttsSpeechRate,
            pitch = snapshot.ttsPitch,
            notificationsEnabled = snapshot.notificationsEnabled,
            notificationReadAloudEnabled = snapshot.notificationReadAloud,
            chimeEnabled = snapshot.chimeEnabled,
            chimeSound = snapshot.chimeSound,
            minReadAloudPriority = snapshot.minReadAloudPriority,
            theme = theme,
            debugMode = snapshot.debugMode
        )
    }

    /**
     * Send the full settings payload to the watch via DataItem write.
     * The watch receives onDataChanged and applies atomically.
     */
    private fun syncToWatch(
        snapshot: WatchSettingsSnapshot = _uiState.value.snapshot,
        theme: String = _uiState.value.selectedTheme
    ) {
        viewModelScope.launch {
            val payload = buildSettingsPayload(snapshot, theme)
            watchSettingsClient.writeSettings(payload)
                .onFailure { AppLog.w(TAG, "Failed to write settings DataItem: ${it.message}") }
        }
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
                val payload = watchSettingsClient.read()
                if (payload != null) {
                    val snapshot = WatchSettingsSnapshot(
                        debugMode = payload.debugMode,
                        voiceCommandsEnabled = payload.voiceCommandsEnabled,
                        readAloudEnabled = payload.readAloudEnabled,
                        useServerTts = payload.useServerTts,
                        serverTtsVoice = payload.serverTtsVoice,
                        ttsSpeechRate = payload.speechRate,
                        ttsPitch = payload.pitch,
                        notificationsEnabled = payload.notificationsEnabled,
                        notificationReadAloud = payload.notificationReadAloudEnabled,
                        chimeEnabled = payload.chimeEnabled,
                        chimeSound = payload.chimeSound,
                        minReadAloudPriority = payload.minReadAloudPriority
                    )
                    _uiState.update { it.copy(
                        loadState = LoadState.Loaded,
                        snapshot = snapshot,
                        selectedTheme = payload.theme.ifBlank { it.selectedTheme },
                        watchHasSpeaker = payload.hasSpeaker
                    ) }
                    AppLog.d(TAG, "Settings loaded from DataItem")
                    if (snapshot.useServerTts) {
                        loadVoices()
                    }
                } else {
                    // No DataItem yet — watch hasn't synced. Check if watch is connected.
                    val node = dataLayerSender.getConnectedWatch()
                    if (node == null) {
                        _uiState.update { it.copy(loadState = LoadState.Error, errorMessage = "Watch not connected") }
                        _watchDisconnected.value = true
                    } else {
                        // Watch connected but no DataItem — use defaults (first-time setup)
                        _uiState.update { it.copy(loadState = LoadState.Loaded) }
                        AppLog.d(TAG, "No DataItem yet — using defaults (watch first-time setup)")
                    }
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to load settings", e)
                _uiState.update { it.copy(loadState = LoadState.Error, errorMessage = e.message) }
            }
        }
    }

    private fun observeWatchConnection() {
        viewModelScope.launch {
            dataLayerSender.watchConnectionState.collect { info ->
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

    fun setTtsSpeechRate(rate: Float) {
        updateAndSyncVoice { it.copy(ttsSpeechRate = rate) }
    }

    fun setTtsPitch(pitch: Float) {
        updateAndSyncVoice { it.copy(ttsPitch = pitch) }
    }

    private fun updateAndSyncVoice(transform: (WatchSettingsSnapshot) -> WatchSettingsSnapshot) {
        val newSnapshot = transform(_uiState.value.snapshot)
        _uiState.update { it.copy(snapshot = newSnapshot) }
        syncToWatch(snapshot = newSnapshot)
        scheduleBackupWrite()
    }

    private fun sendVoiceSettingsToWatch(snapshot: WatchSettingsSnapshot) {
        syncToWatch(snapshot = snapshot)
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

    fun setMinReadAloudPriority(priority: String) {
        updateAndSyncNotifications { it.copy(minReadAloudPriority = priority) }
    }

    private fun updateAndSyncNotifications(transform: (WatchSettingsSnapshot) -> WatchSettingsSnapshot) {
        val newSnapshot = transform(_uiState.value.snapshot)
        _uiState.update { it.copy(snapshot = newSnapshot) }
        syncToWatch(snapshot = newSnapshot)
        scheduleBackupWrite()
    }

    private fun sendNotificationSettingsToWatch(snapshot: WatchSettingsSnapshot) {
        syncToWatch(snapshot = snapshot)
    }

    // ─── Debug mode ───

    fun setDebugMode(enabled: Boolean) {
        val newSnapshot = _uiState.value.snapshot.copy(debugMode = enabled)
        _uiState.update { it.copy(snapshot = newSnapshot) }
        viewModelScope.launch {
            credentialStore.setDebugMode(enabled)
        }
        syncToWatch(snapshot = newSnapshot)
        scheduleBackupWrite()
        AppLog.d(TAG, "Debug mode set to $enabled")
    }

    // ─── Theme ───

    fun setTheme(themeName: String) {
        _uiState.update { it.copy(selectedTheme = themeName) }
        viewModelScope.launch {
            credentialStore.saveSelectedTheme(themeName)
        }
        syncToWatch(theme = themeName)
        AppLog.d(TAG, "Theme set to $themeName")
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
        val payload = buildSettingsPayload()

        backupRepository.ensureBackupItemExists(localConfig, deviceName)
            .onFailure { AppLog.w(TAG, "Failed to create backup item: ${it.message}") }

        backupRepository.writeBackup(localConfig, deviceName, payload)
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
                .onSuccess { restoredSettings ->
                    if (restoredSettings == null) {
                        _uiState.update { it.copy(restoreState = RestoreState.Error, errorMessage = "No backup found for '$deviceName'") }
                        return@launch
                    }
                    // Map restored payload back to UI state
                    val restoredSnapshot = WatchSettingsSnapshot(
                        debugMode = restoredSettings.debugMode,
                        voiceCommandsEnabled = restoredSettings.voiceCommandsEnabled,
                        readAloudEnabled = restoredSettings.readAloudEnabled,
                        useServerTts = restoredSettings.useServerTts,
                        serverTtsVoice = restoredSettings.serverTtsVoice,
                        ttsSpeechRate = restoredSettings.speechRate,
                        ttsPitch = restoredSettings.pitch,
                        notificationsEnabled = restoredSettings.notificationsEnabled,
                        notificationReadAloud = restoredSettings.notificationReadAloudEnabled,
                        chimeEnabled = restoredSettings.chimeEnabled,
                        chimeSound = restoredSettings.chimeSound,
                        minReadAloudPriority = restoredSettings.minReadAloudPriority
                    )
                    val restoredTheme = restoredSettings.theme.ifBlank { _uiState.value.selectedTheme }
                    _uiState.update { it.copy(snapshot = restoredSnapshot, selectedTheme = restoredTheme) }
                    // Push restored settings to watch
                    syncToWatch(snapshot = restoredSnapshot, theme = restoredTheme)
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
            syncToWatch(snapshot = snapshot)
            _uiState.update { it.copy(restoreState = RestoreState.Success) }
            AppLog.d(TAG, "Settings restored from provided snapshot and pushed to watch")
        }
    }

    fun clearRestoreState() {
        _uiState.update { it.copy(restoreState = RestoreState.Idle) }
    }
}
