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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.openhab.habdroid.wear.phone.data.PhoneCredentialStore
import org.openhab.habdroid.wear.phone.data.ServerBackupRepository
import org.openhab.habdroid.wear.phone.sync.PhoneDataLayerSender
import org.openhab.habdroid.wear.phone.sync.WatchSettingsDataItemClient
import org.openhab.habdroid.wear.phone.util.AppLog
import org.openhab.habdroid.wear.shared.sync.SyncConstants
import org.openhab.habdroid.wear.shared.sync.WatchSettingsPayload
import javax.inject.Inject

/**
 * UI state for the Watch Settings screen on the phone.
 */
data class WatchSettingsUiState(
    val loadState: LoadState = LoadState.Loading,
    val snapshot: WatchSettingsPayload = WatchSettingsPayload(),
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
    val watchHasSpeaker: Boolean = true,
    /** Outcome of the most recent server backup write. */
    val backupStatus: BackupStatus = BackupStatus.Idle
)

enum class LoadState { Loading, Loaded, Error }
enum class RestoreState { Idle, Restoring, Success, Error }

/**
 * Outcome of a server backup write attempt, surfaced to the UI so silent
 * failures (unconfigured config server, missing device name, HTTP errors)
 * become visible instead of being swallowed.
 */
sealed interface BackupStatus {
    data object Idle : BackupStatus
    data object Writing : BackupStatus
    data object Success : BackupStatus
    data class Skipped(val reason: String) : BackupStatus
    data class Failed(val reason: String) : BackupStatus
}

/**
 * ViewModel for phone-side Watch Settings.
 *
 * On screen open:
 * - Reads settings instantly from DataItem (offline-capable)
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
        observeSettingsForBackup()
    }

    /**
     * Persistence follows the settings DTO, not individual setters.
     *
     * The complete [WatchSettingsPayload] is assembled from UI state (including the
     * separately held theme) and any change to it triggers a debounced server backup.
     * This guarantees every current and future settings field is persisted without
     * each setter having to remember to request a write.
     *
     * The initial value emitted right after load is dropped so that merely opening the
     * screen does not write a backup.
     */
    private fun observeSettingsForBackup() {
        viewModelScope.launch {
            _uiState
                .map { buildSettingsPayload(it.snapshot, it.selectedTheme) }
                .distinctUntilChanged()
                .drop(1)
                .collect { scheduleBackupWrite() }
        }
    }

    // ─── Unified settings sync ───

    /**
     * Build the complete [WatchSettingsPayload] from current UI state.
     * Only includes non-credential preferences — no secrets.
     */
    private fun buildSettingsPayload(
        snapshot: WatchSettingsPayload = _uiState.value.snapshot,
        theme: String = _uiState.value.selectedTheme
    ): WatchSettingsPayload {
        return snapshot.copy(theme = theme)
    }

    /**
     * Send the full settings payload to the watch via DataItem write.
     * The watch receives onDataChanged and applies atomically.
     */
    private fun syncToWatch(
        snapshot: WatchSettingsPayload = _uiState.value.snapshot,
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
                    _uiState.update { it.copy(
                        loadState = LoadState.Loaded,
                        snapshot = payload,
                        selectedTheme = payload.theme.ifBlank { it.selectedTheme },
                        watchHasSpeaker = payload.hasSpeaker
                    ) }
                    AppLog.d(TAG, "Settings loaded from DataItem")
                    if (payload.useServerTts) {
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
                val lang = java.util.Locale.getDefault().language
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
        updateAndSyncVoice { it.copy(speechRate = rate) }
    }

    fun setTtsPitch(pitch: Float) {
        updateAndSyncVoice { it.copy(pitch = pitch) }
    }

    private fun updateAndSyncVoice(transform: (WatchSettingsPayload) -> WatchSettingsPayload) {
        val newSnapshot = transform(_uiState.value.snapshot)
        _uiState.update { it.copy(snapshot = newSnapshot) }
        syncToWatch(snapshot = newSnapshot)
        // Server backup is driven by observeSettingsForBackup() off the state change.
    }

    private fun sendVoiceSettingsToWatch(snapshot: WatchSettingsPayload) {
        syncToWatch(snapshot = snapshot)
    }

    // ─── Notification settings ───

    fun setNotificationsEnabled(enabled: Boolean) {
        updateAndSyncNotifications { it.copy(notificationsEnabled = enabled) }
    }

    fun setNotificationReadAloud(enabled: Boolean) {
        updateAndSyncNotifications { it.copy(notificationReadAloudEnabled = enabled) }
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

    private fun updateAndSyncNotifications(transform: (WatchSettingsPayload) -> WatchSettingsPayload) {
        val newSnapshot = transform(_uiState.value.snapshot)
        _uiState.update { it.copy(snapshot = newSnapshot) }
        syncToWatch(snapshot = newSnapshot)
        // Server backup is driven by observeSettingsForBackup() off the state change.
    }

    private fun sendNotificationSettingsToWatch(snapshot: WatchSettingsPayload) {
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
        // Server backup is driven by observeSettingsForBackup() off the state change.
        AppLog.d(TAG, "Debug mode set to $enabled")
    }

    // ─── Theme ───

    fun setTheme(themeName: String) {
        _uiState.update { it.copy(selectedTheme = themeName) }
        viewModelScope.launch {
            credentialStore.saveSelectedTheme(themeName)
        }
        syncToWatch(theme = themeName)
        // theme is part of the settings DTO, so observeSettingsForBackup() persists it.
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
     * Debounced write of the current settings DTO to server backup.
     * Triggered by [observeSettingsForBackup] on any settings change, and once
     * when backup is first enabled. No-op (with a visible reason) when backup is off.
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
        val localConfig = credentialStore.localConfig.first()
        if (localConfig == null || !localConfig.isConfigured) {
            AppLog.w(TAG, "Backup skipped: config server not configured")
            _uiState.update { it.copy(backupStatus = BackupStatus.Skipped("Config server not configured")) }
            return
        }

        // Item name matches the MobileAudio binding Thing id (mobileaudio:device:{deviceName}),
        // so watch settings and the audio-sink Thing share the same device identity.
        val deviceName = credentialStore.deviceName
        if (deviceName.isBlank()) {
            AppLog.w(TAG, "Backup skipped: device name not set")
            _uiState.update { it.copy(backupStatus = BackupStatus.Skipped("Set a watch device name to enable backup")) }
            return
        }

        val payload = buildSettingsPayload()
        _uiState.update { it.copy(backupStatus = BackupStatus.Writing) }

        val itemName = ServerBackupRepository.backupItemName(deviceName)

        backupRepository.ensureBackupItemExists(localConfig, deviceName)
            .onFailure { AppLog.w(TAG, "Failed to create backup item '$itemName': ${it.message}") }

        backupRepository.writeBackup(localConfig, deviceName, payload)
            .onSuccess {
                AppLog.d(TAG, "Backup written to server for '$itemName'")
                _uiState.update { it.copy(backupStatus = BackupStatus.Success) }
            }
            .onFailure { e ->
                AppLog.w(TAG, "Failed to write backup: ${e.message}")
                _uiState.update { it.copy(backupStatus = BackupStatus.Failed(e.message ?: "Backup write failed")) }
            }
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

            // Must match the backup item name used by writeBackupToServer() — the
            // device name (also the MobileAudio Thing id), not the user key.
            val deviceName = credentialStore.deviceName
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
                    val restoredTheme = restoredSettings.theme.ifBlank { _uiState.value.selectedTheme }
                    _uiState.update { it.copy(snapshot = restoredSettings, selectedTheme = restoredTheme) }
                    // Push restored settings to watch
                    syncToWatch(snapshot = restoredSettings, theme = restoredTheme)
                    _uiState.update { it.copy(restoreState = RestoreState.Success) }
                    AppLog.d(TAG, "Settings restored from server backup")
                }
                .onFailure { e ->
                    _uiState.update { it.copy(restoreState = RestoreState.Error, errorMessage = e.message) }
                    AppLog.w(TAG, "Restore failed: ${e.message}")
                }
        }
    }

    fun restoreFromBackup(snapshot: WatchSettingsPayload) {
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
