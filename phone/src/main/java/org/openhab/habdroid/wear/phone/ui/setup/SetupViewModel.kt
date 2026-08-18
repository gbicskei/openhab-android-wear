package org.openhab.habdroid.wear.phone.ui.setup

import org.openhab.habdroid.wear.phone.util.AppLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.phone.data.ConnectionTester
import org.openhab.habdroid.wear.phone.data.InvalidCredentialsException
import org.openhab.habdroid.wear.phone.data.LocalServerConfig
import org.openhab.habdroid.wear.phone.data.PhoneCredentialStore
import org.openhab.habdroid.wear.phone.data.WriteNotAllowedException
import org.openhab.habdroid.wear.phone.sync.NoNetworkException
import org.openhab.habdroid.wear.phone.sync.NoWatchConnectedException
import org.openhab.habdroid.wear.phone.sync.PhoneDataLayerSender
import org.openhab.habdroid.wear.phone.sync.WatchVersionHolder
import org.openhab.habdroid.wear.shared.model.ServerCredentials
import org.openhab.habdroid.wear.shared.sync.VersionCompat
import javax.inject.Inject

/**
 * Orchestrates phone-side setup flow — manages server connections (main + config),
 * credential persistence, watch sync, and config version checking.
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val connectionTester: ConnectionTester,
    private val dataLayerSender: PhoneDataLayerSender,
    private val credentialStore: PhoneCredentialStore,
    private val watchStatusReader: org.openhab.habdroid.wear.phone.sync.WatchStatusReader
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        loadSavedCredentials()
        observeWatchConnection()
        observeWatchVersion()
        observeDebugMode()
        checkConfigSync()
    }

    fun loadSavedCredentials() {
        viewModelScope.launch {
            val saved = credentialStore.credentials.first()
            if (saved != null) {
                _uiState.update {
                    it.copy(
                        serverUrl = saved.serverUrl,
                        username = saved.username,
                        password = "",
                        hasStoredPassword = saved.password.isNotBlank(),
                        passwordModifiedThisSession = false
                    )
                }
            }
            val local = credentialStore.localConfig.first()
            if (local != null) {
                _uiState.update {
                    it.copy(
                        configServerUrl = local.serverUrl,
                        configUsername = local.username,
                        configPassword = "",
                        configApiToken = local.apiToken,
                        configUseApiToken = local.hasApiToken,
                        configHasStoredPassword = local.password.isNotBlank(),
                        configPasswordModifiedThisSession = false
                    )
                }
            }
            // Load user key and Google TTS API key
            val userKey = credentialStore.currentUserKey
            val hasGoogleTtsKey = credentialStore.hasGoogleTtsApiKey
            _uiState.update {
                it.copy(
                    userKey = userKey,
                    deviceName = credentialStore.deviceName,
                    hasStoredGoogleTtsApiKey = hasGoogleTtsKey,
                    debugMode = credentialStore.isDebugMode,
                    watchUseLocalServer = credentialStore.isWatchUseLocalServer,
                    hasUnsavedChanges = false
                )
            }
        }
    }

    private fun observeWatchConnection() {
        viewModelScope.launch {
            dataLayerSender.watchConnectionState.collect { info ->
                _uiState.update { state ->
                    val newStatus = when {
                        info == null -> WatchStatus.NotFound
                        !info.watchAppInstalled -> WatchStatus.AppNotInstalled
                        state.watchStatus == WatchStatus.Synced -> WatchStatus.Synced
                        else -> WatchStatus.Connected
                    }
                    // Reset version mismatch when watch disconnects
                    val versionMismatch = if (newStatus == WatchStatus.NotFound || newStatus == WatchStatus.AppNotInstalled) {
                        false
                    } else {
                        state.watchVersionMismatch
                    }
                    state.copy(
                        watchStatus = newStatus,
                        watchName = info?.displayName,
                        watchNearby = info?.isNearby ?: false,
                        watchVersionMismatch = versionMismatch
                    )
                }
                // When watch becomes connected, check its version
                if (info != null && info.watchAppInstalled) {
                    loadWatchVersionFromDataItem()
                }
            }
        }
    }

    private fun observeDebugMode() {
        viewModelScope.launch {
            credentialStore.debugModeFlow.collect { enabled ->
                _uiState.update { it.copy(debugMode = enabled) }
            }
        }
    }

    /**
     * Check if the watch's config is out of sync with the phone/server.
     * Compares both configVersion (tile layout) and theme.
     * Skips the check if the watch app is not installed or if we just synced successfully.
     */
    fun checkConfigSync() {
        viewModelScope.launch {
            // Don't check sync if the watch app isn't installed — there's nothing to sync with
            if (_uiState.value.watchStatus == WatchStatus.AppNotInstalled) {
                _uiState.update { it.copy(configOutOfSync = false) }
                return@launch
            }

            // Don't override a recent sync success — the watch may still be writing its DataItem
            // Always re-check if tile config may have changed (configVersion comparison is cheap)
            if (_uiState.value.watchStatus == WatchStatus.Synced && !_uiState.value.configOutOfSync && !credentialStore.settingsNeedSync) {
                // Still re-check configVersion — tile editor may have changed it
            }

            try {
                val watchStatus = watchStatusReader.readStatus()
                val watchVersion = watchStatus?.configTimestamp?.toIntOrNull()
                val watchTheme = watchStatus?.theme
                AppLog.d("SetupVM", "Sync check: watchVersion=$watchVersion, watchTheme=$watchTheme")

                if (watchVersion == null) {
                    // Watch hasn't synced yet — show as out of sync
                    _uiState.update { it.copy(configOutOfSync = true) }
                    return@launch
                }

                // Check theme mismatch
                val selectedTheme = credentialStore.getSelectedTheme()
                if (watchTheme != null && !watchTheme.equals(selectedTheme, ignoreCase = true)) {
                    AppLog.d("SetupVM", "Sync check: theme mismatch (watch=$watchTheme, phone=$selectedTheme)")
                    _uiState.update { it.copy(configOutOfSync = true) }
                    return@launch
                }

                // Fetch server's configVersion from main page
                val creds = credentialStore.credentials.first() ?: return@launch
                val local = credentialStore.localConfig.first()
                val serverUrl = local?.serverUrl?.takeIf { it.isNotBlank() } ?: creds.serverUrl
                val username = local?.username?.takeIf { it.isNotBlank() } ?: creds.username
                val password = local?.password?.takeIf { it.isNotBlank() } ?: creds.password

                val serverVersion = connectionTester.fetchConfigVersion(
                    serverUrl, username, password, credentialStore.tileNamespace
                )
                AppLog.d("SetupVM", "Sync check: serverVersion=$serverVersion")

                if (serverVersion == null) {
                    _uiState.update { it.copy(configOutOfSync = credentialStore.settingsNeedSync) }
                    return@launch
                }

                val outOfSync = watchVersion != serverVersion || credentialStore.settingsNeedSync
                AppLog.d("SetupVM", "Sync check: outOfSync=$outOfSync (configVersion: watch=$watchVersion server=$serverVersion, settingsNeedSync=${credentialStore.settingsNeedSync})")
                _uiState.update { it.copy(configOutOfSync = outOfSync) }
            } catch (e: Exception) {
                AppLog.w("SetupVM", "Config sync check failed", e)
            }
        }
    }

    fun checkWatchConnection() {
        viewModelScope.launch {
            val node = dataLayerSender.getConnectedWatch()
            _uiState.update {
                it.copy(
                    watchStatus = if (node != null) WatchStatus.Connected else WatchStatus.NotFound,
                    watchName = node?.displayName
                )
            }
        }
    }

    // ─── Version Compatibility Check ───

    /**
     * Loads the watch app version from the DataItem (written by the watch on startup).
     * Falls back to a one-shot message request if the DataItem doesn't contain a version yet.
     * Skipped entirely for dev builds (version mismatch never blocks in dev).
     */
    private fun loadWatchVersionFromDataItem() {
        val phoneVersionName = org.openhab.habdroid.wear.phone.BuildConfig.VERSION_NAME
        if (VersionCompat.isDevBuild(phoneVersionName)) {
            AppLog.d("SetupVM", "Dev build — skipping version check")
            return
        }

        viewModelScope.launch {
            // Always read the persistent DataItem for the freshest version
            val status = watchStatusReader.readStatus()
            val dataItemVersion = status?.appVersion
            if (!dataItemVersion.isNullOrBlank()) {
                WatchVersionHolder.update(dataItemVersion)
                AppLog.d("SetupVM", "Watch version from DataItem: $dataItemVersion")
                return@launch
            }

            // Fallback: one-shot message request (older watch app without DataItem version)
            dataLayerSender.requestWatchVersion()
                .onFailure { e ->
                    AppLog.w("SetupVM", "Failed to request watch version: ${e.message}")
                }
        }
    }

    /**
     * @deprecated Use [loadWatchVersionFromDataItem] instead. Kept for external callers.
     */
    fun checkWatchVersion() {
        loadWatchVersionFromDataItem()
    }

    /**
     * Observes the WatchVersionHolder flow. When the watch responds with its version,
     * this evaluates whether sync should be blocked.
     * No-op for dev builds (never blocks).
     */
    private fun observeWatchVersion() {
        val phoneVersionName = org.openhab.habdroid.wear.phone.BuildConfig.VERSION_NAME
        if (VersionCompat.isDevBuild(phoneVersionName)) return

        viewModelScope.launch {
            WatchVersionHolder.watchVersion.collect { watchVersionName ->
                if (watchVersionName != null) {
                    val blocked = VersionCompat.shouldBlockSync(phoneVersionName, watchVersionName)
                    AppLog.d("SetupVM", "Version check: phone=$phoneVersionName, watch=$watchVersionName, blocked=$blocked")
                    _uiState.update {
                        it.copy(
                            watchVersionName = watchVersionName,
                            watchVersionMismatch = blocked
                        )
                    }
                }
            }
        }
    }

    // ─── Main (Remote) Connection ───

    fun onServerUrlChanged(url: String) {
        _uiState.update { it.copy(serverUrl = url, connectionStatus = ConnectionStatus.Idle, hasUnsavedChanges = true) }
    }

    fun onUsernameChanged(username: String) {
        _uiState.update { it.copy(username = username, connectionStatus = ConnectionStatus.Idle, hasUnsavedChanges = true) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update {
            it.copy(password = password, connectionStatus = ConnectionStatus.Idle, passwordModifiedThisSession = true, hasUnsavedChanges = true)
        }
    }

    fun testConnection() {
        val state = _uiState.value
        if (state.serverUrl.isBlank()) return

        _uiState.update { it.copy(connectionStatus = ConnectionStatus.Testing, errorMessage = null) }

        viewModelScope.launch {
            val effectivePassword = getEffectivePassword()
            connectionTester.testConnection(
                serverUrl = state.serverUrl.trim(),
                username = state.username.trim(),
                password = effectivePassword
            ).onSuccess {
                _uiState.update { it.copy(connectionStatus = ConnectionStatus.Success) }
            }.onFailure { error ->
                val message = when (error) {
                    is InvalidCredentialsException -> "Invalid username or password"
                    else -> error.message ?: "Connection failed"
                }
                _uiState.update { it.copy(connectionStatus = ConnectionStatus.Failed, errorMessage = message) }
            }
        }
    }

    // ─── User Key (Multi-user namespace) ───

    fun onUserKeyChanged(key: String) {
        // Validate: only allow [a-z0-9_-]
        val sanitized = key.lowercase().filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        _uiState.update { it.copy(userKey = sanitized, hasUnsavedChanges = true) }
    }

    fun onDeviceNameChanged(name: String) {
        _uiState.update { it.copy(deviceName = name, hasUnsavedChanges = true) }
    }

    // ─── Config (Local) Connection ───

    fun onConfigServerUrlChanged(url: String) {
        _uiState.update { it.copy(configServerUrl = url, configConnectionStatus = ConnectionStatus.Idle, hasUnsavedChanges = true) }
    }

    fun onConfigUsernameChanged(username: String) {
        _uiState.update { it.copy(configUsername = username, configConnectionStatus = ConnectionStatus.Idle, hasUnsavedChanges = true) }
    }

    fun onConfigPasswordChanged(password: String) {
        _uiState.update {
            it.copy(configPassword = password, configConnectionStatus = ConnectionStatus.Idle, configPasswordModifiedThisSession = true, hasUnsavedChanges = true)
        }
    }

    fun onConfigApiTokenChanged(token: String) {
        _uiState.update { it.copy(configApiToken = token, configConnectionStatus = ConnectionStatus.Idle, hasUnsavedChanges = true) }
    }

    fun onConfigAuthModeChanged(useApiToken: Boolean) {
        _uiState.update { it.copy(configUseApiToken = useApiToken, configConnectionStatus = ConnectionStatus.Idle, hasUnsavedChanges = true) }
    }

    // ─── Other — Google Cloud TTS ───

    fun onGoogleTtsApiKeyChanged(key: String) {
        _uiState.update {
            it.copy(
                googleTtsApiKey = key,
                googleTtsApiKeyModifiedThisSession = true,
                googleTtsTestStatus = ConnectionStatus.Idle,
                hasUnsavedChanges = true
            )
        }
    }

    fun testGoogleTts() {
        val state = _uiState.value
        val key = if (state.googleTtsApiKeyModifiedThisSession) state.googleTtsApiKey.trim()
        else credentialStore.getGoogleTtsApiKey()

        if (key.isBlank()) return

        _uiState.update { it.copy(googleTtsTestStatus = ConnectionStatus.Testing, googleTtsTestError = null) }

        viewModelScope.launch {
            connectionTester.testGoogleTts(key)
                .onSuccess {
                    _uiState.update { it.copy(googleTtsTestStatus = ConnectionStatus.Success) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            googleTtsTestStatus = ConnectionStatus.Failed,
                            googleTtsTestError = error.message ?: "TTS test failed"
                        )
                    }
                }
        }
    }

    fun testConfigConnection() {
        val state = _uiState.value
        if (state.configServerUrl.isBlank()) return

        _uiState.update { it.copy(configConnectionStatus = ConnectionStatus.Testing, configErrorMessage = null) }

        viewModelScope.launch {
            val effectivePassword = getEffectiveConfigPassword()
            connectionTester.testConfigConnection(
                serverUrl = state.configServerUrl.trim(),
                username = state.configUsername.trim(),
                password = effectivePassword,
                apiToken = state.configApiToken.trim(),
                namespace = credentialStore.tileNamespace
            ).onSuccess {
                _uiState.update { it.copy(configConnectionStatus = ConnectionStatus.Success) }
            }.onFailure { error ->
                val message = when (error) {
                    is InvalidCredentialsException -> "Invalid username or password"
                    is WriteNotAllowedException -> "Write access denied — enable Basic Auth in Settings > API Security"
                    else -> error.message ?: "Connection failed"
                }
                _uiState.update { it.copy(configConnectionStatus = ConnectionStatus.Failed, configErrorMessage = message) }
            }
        }
    }

    // ─── Watch Local Server Toggle ───

    fun setWatchUseLocalServer(enabled: Boolean) {
        _uiState.update { it.copy(watchUseLocalServer = enabled, hasUnsavedChanges = true) }
        viewModelScope.launch {
            credentialStore.setWatchUseLocalServer(enabled)
        }
    }

    // ─── Save All ───

    fun saveAll() {
        val state = _uiState.value
        _uiState.update { it.copy(saveStatus = SaveStatus.Testing) }

        viewModelScope.launch {
            val errors = mutableListOf<String>()

            // Test main server connection if credentials are configured
            val effectivePassword = getEffectivePassword()
            if (state.serverUrl.isNotBlank()) {
                connectionTester.testConnection(
                    serverUrl = state.serverUrl.trim(),
                    username = state.username.trim(),
                    password = effectivePassword
                ).onSuccess {
                    _uiState.update { it.copy(connectionStatus = ConnectionStatus.Success, errorMessage = null) }
                }.onFailure { error ->
                    val message = when (error) {
                        is InvalidCredentialsException -> "Main Server: Invalid username or password"
                        else -> "Main Server: ${error.message ?: "Connection failed"}"
                    }
                    errors.add(message)
                    _uiState.update { it.copy(connectionStatus = ConnectionStatus.Failed, errorMessage = message) }
                }
            }

            // Test config server connection if configured
            val effectiveConfigPassword = getEffectiveConfigPassword()
            if (state.configServerUrl.isNotBlank()) {
                connectionTester.testConfigConnection(
                    serverUrl = state.configServerUrl.trim(),
                    username = state.configUsername.trim(),
                    password = effectiveConfigPassword,
                    apiToken = state.configApiToken.trim(),
                    namespace = credentialStore.tileNamespace
                ).onSuccess {
                    _uiState.update { it.copy(configConnectionStatus = ConnectionStatus.Success, configErrorMessage = null) }
                }.onFailure { error ->
                    val message = when (error) {
                        is InvalidCredentialsException -> "Config Server: Invalid username or password"
                        is WriteNotAllowedException -> "Config Server: Write access denied"
                        else -> "Config Server: ${error.message ?: "Connection failed"}"
                    }
                    errors.add(message)
                    _uiState.update { it.copy(configConnectionStatus = ConnectionStatus.Failed, configErrorMessage = message) }
                }
            }

            // Test Google TTS API key if configured
            val googleTtsKey = if (state.googleTtsApiKeyModifiedThisSession) state.googleTtsApiKey.trim()
            else credentialStore.getGoogleTtsApiKey()
            if (googleTtsKey.isNotBlank()) {
                connectionTester.testGoogleTts(googleTtsKey)
                    .onSuccess {
                        _uiState.update { it.copy(googleTtsTestStatus = ConnectionStatus.Success, googleTtsTestError = null) }
                    }
                    .onFailure { error ->
                        errors.add("Google TTS: ${error.message ?: "Test failed"}")
                        _uiState.update { it.copy(googleTtsTestStatus = ConnectionStatus.Failed, googleTtsTestError = error.message) }
                    }
            }

            // If any test failed, show warning and stop
            if (errors.isNotEmpty()) {
                _uiState.update { it.copy(saveStatus = SaveStatus.Warning(errors)) }
                return@launch
            }

            // All tests passed (or nothing to test) — proceed with saving
            performSave(state, effectivePassword, effectiveConfigPassword)
        }
    }

    /**
     * Force-save even when there are connection warnings.
     * Called from the UI when the user dismisses the warning and chooses to save anyway.
     */
    fun forceSave() {
        val state = _uiState.value
        viewModelScope.launch {
            val effectivePassword = getEffectivePassword()
            val effectiveConfigPassword = getEffectiveConfigPassword()
            performSave(state, effectivePassword, effectiveConfigPassword)
        }
    }

    fun dismissSaveWarning() {
        _uiState.update { it.copy(saveStatus = SaveStatus.Idle) }
    }

    private suspend fun performSave(state: SetupUiState, effectivePassword: String, effectiveConfigPassword: String) {
        _uiState.update { it.copy(saveStatus = SaveStatus.Saving) }

        // Save user key
        credentialStore.saveUserKey(state.userKey)

        // Save device name
        credentialStore.saveDeviceName(state.deviceName)

        // Save main server credentials
        if (state.serverUrl.isNotBlank()) {
            credentialStore.saveCredentials(
                ServerCredentials(
                    serverUrl = state.serverUrl.trim(),
                    username = state.username.trim(),
                    password = effectivePassword,
                    userKey = state.userKey
                )
            )
        }

        // Save config server credentials
        if (state.configServerUrl.isNotBlank()) {
            credentialStore.saveLocalConfig(
                LocalServerConfig(
                    serverUrl = state.configServerUrl.trim(),
                    username = state.configUsername.trim(),
                    password = effectiveConfigPassword,
                    apiToken = state.configApiToken.trim()
                )
            )
        }

        // Save Google Cloud TTS API key
        if (state.googleTtsApiKeyModifiedThisSession) {
            credentialStore.saveGoogleTtsApiKey(state.googleTtsApiKey.trim())
        }

        _uiState.update {
            it.copy(
                hasUnsavedChanges = false,
                hasStoredPassword = effectivePassword.isNotBlank(),
                configHasStoredPassword = effectiveConfigPassword.isNotBlank(),
                hasStoredGoogleTtsApiKey = credentialStore.hasGoogleTtsApiKey,
                saveStatus = SaveStatus.Success
            )
        }
        AppLog.d("SetupVM", "All settings saved")

        // Auto-sync credentials to watch if connected
        syncCredentialsToWatch(state)

        // Reset save status after a short delay
        kotlinx.coroutines.delay(2000)
        _uiState.update { it.copy(saveStatus = SaveStatus.Idle) }
    }

    /**
     * Pushes credentials to the watch silently after saving.
     * Non-blocking — if watch is not connected, this is a no-op.
     */
    private suspend fun syncCredentialsToWatch(state: SetupUiState) {
        try {
            val node = dataLayerSender.getConnectedWatch() ?: return
            val effectivePassword = getEffectivePassword()
            val credentials = ServerCredentials(
                serverUrl = state.serverUrl.trim(),
                username = state.username.trim(),
                password = effectivePassword,
                userKey = state.userKey,
                googleTtsApiKey = getEffectiveGoogleTtsApiKey()
            )
            val localConfig = if (credentialStore.isWatchUseLocalServer) {
                credentialStore.localConfig.first()
            } else {
                null
            }
            val localUrl = localConfig?.serverUrl ?: ""
            dataLayerSender.sendCredentials(
                credentials,
                debugMode = credentialStore.isDebugMode,
                localServerUrl = localUrl,
                localUsername = localConfig?.username ?: "",
                localPassword = localConfig?.password ?: "",
                localApiToken = localConfig?.apiToken ?: "",
                deviceName = credentialStore.deviceName,
                bindingInstalled = credentialStore.isBindingInstalled
            )
                .onSuccess {
                    AppLog.d("SetupVM", "Credentials auto-synced to watch on save")
                }
                .onFailure { e ->
                    AppLog.w("SetupVM", "Auto-sync to watch failed: ${e.message}")
                }
        } catch (e: Exception) {
            AppLog.w("SetupVM", "Auto-sync to watch failed: ${e.message}")
        }
    }

    // ─── Watch Sync ───

    fun sendToWatch() {
        val state = _uiState.value
        _uiState.update { it.copy(syncResult = SyncResult.Sending) }

        viewModelScope.launch {
            // Ensure settings are saved before syncing
            if (state.hasUnsavedChanges) {
                saveAll()
            }

            val effectivePassword = getEffectivePassword()
            val credentials = ServerCredentials(
                serverUrl = state.serverUrl.trim(),
                username = state.username.trim(),
                password = effectivePassword,
                userKey = state.userKey,
                googleTtsApiKey = getEffectiveGoogleTtsApiKey()
            )

            val localConfig = if (credentialStore.isWatchUseLocalServer) {
                credentialStore.localConfig.first()
            } else {
                null
            }
            val localUrl = localConfig?.serverUrl ?: ""
            dataLayerSender.sendCredentials(
                credentials,
                debugMode = credentialStore.isDebugMode,
                localServerUrl = localUrl,
                localUsername = localConfig?.username ?: "",
                localPassword = localConfig?.password ?: "",
                localApiToken = localConfig?.apiToken ?: "",
                deviceName = credentialStore.deviceName,
                bindingInstalled = credentialStore.isBindingInstalled,
                triggerReload = true
            )
                .onSuccess {
                    _uiState.update { it.copy(syncResult = SyncResult.Success, watchStatus = WatchStatus.Synced, configOutOfSync = false) }
                    // Re-check sync status after giving the watch time to reload config + write DataItem
                    kotlinx.coroutines.delay(10_000)
                    checkConfigSync()
                }
                .onFailure { error ->
                    val message = when (error) {
                        is NoWatchConnectedException -> "No watch connected"
                        is NoNetworkException -> "No network connection"
                        else -> error.message ?: "Failed to send"
                    }
                    _uiState.update { it.copy(syncResult = SyncResult.Error(message)) }
                }
        }
    }

    fun dismissSyncResult() {
        _uiState.update { it.copy(syncResult = null) }
    }

    private suspend fun getEffectivePassword(): String {
        val state = _uiState.value
        return if (state.passwordModifiedThisSession) state.password
        else credentialStore.credentials.first()?.password ?: ""
    }

    private suspend fun getEffectiveConfigPassword(): String {
        val state = _uiState.value
        return if (state.configPasswordModifiedThisSession) state.configPassword
        else credentialStore.localConfig.first()?.password ?: ""
    }

    private fun getEffectiveGoogleTtsApiKey(): String {
        val state = _uiState.value
        return if (state.googleTtsApiKeyModifiedThisSession) state.googleTtsApiKey.trim()
        else credentialStore.getGoogleTtsApiKey()
    }
}

data class SetupUiState(
    // Main (Remote) connection
    val serverUrl: String = "https://myopenhab.org",
    val username: String = "",
    val password: String = "",
    val hasStoredPassword: Boolean = false,
    val passwordModifiedThisSession: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Idle,
    val errorMessage: String? = null,
    // User key (namespace for multi-user config)
    val userKey: String = "",
    // Watch device name (stable identifier for audio sink binding)
    val deviceName: String = "",
    // Config (Local) connection
    val configServerUrl: String = "",
    val configUsername: String = "",
    val configPassword: String = "",
    val configApiToken: String = "",
    val configUseApiToken: Boolean = false,
    val configHasStoredPassword: Boolean = false,
    val configPasswordModifiedThisSession: Boolean = false,
    val configConnectionStatus: ConnectionStatus = ConnectionStatus.Idle,
    val configErrorMessage: String? = null,
    // Other — Google Cloud TTS
    val googleTtsApiKey: String = "",
    val hasStoredGoogleTtsApiKey: Boolean = false,
    val googleTtsApiKeyModifiedThisSession: Boolean = false,
    val googleTtsTestStatus: ConnectionStatus = ConnectionStatus.Idle,
    val googleTtsTestError: String? = null,
    // Watch
    val watchStatus: WatchStatus = WatchStatus.Unknown,
    val watchName: String? = null,
    val watchNearby: Boolean = false,
    val syncResult: SyncResult? = null,
    val configOutOfSync: Boolean = false,
    val watchVersionName: String? = null,
    val watchVersionMismatch: Boolean = false,
    // Unsaved changes tracking
    val hasUnsavedChanges: Boolean = false,
    // Watch use local server toggle
    val watchUseLocalServer: Boolean = false,
    // Save flow
    val saveStatus: SaveStatus = SaveStatus.Idle,
    // Debug
    val debugMode: Boolean = false
) {
    val canTest: Boolean get() = serverUrl.isNotBlank() && connectionStatus != ConnectionStatus.Testing
    val canTestConfig: Boolean get() = configServerUrl.isNotBlank() && configConnectionStatus != ConnectionStatus.Testing
    val canSave: Boolean get() = hasUnsavedChanges && saveStatus != SaveStatus.Testing && saveStatus != SaveStatus.Saving
    val canSendToWatch: Boolean get() = (connectionStatus == ConnectionStatus.Success || hasStoredPassword) &&
        watchStatus != WatchStatus.NotFound &&
        watchStatus != WatchStatus.AppNotInstalled &&
        watchStatus != WatchStatus.Unknown &&
        syncResult != SyncResult.Sending &&
        !watchVersionMismatch
    val connectionTypeLabel: String? get() = when {
        watchStatus == WatchStatus.NotFound || watchStatus == WatchStatus.Unknown || watchStatus == WatchStatus.AppNotInstalled -> null
        watchNearby -> "Bluetooth"
        else -> "Cloud"
    }
    val passwordPlaceholder: String get() = if (hasStoredPassword && !passwordModifiedThisSession) "••••••••" else ""
    val configPasswordPlaceholder: String get() = if (configHasStoredPassword && !configPasswordModifiedThisSession) "••••••••" else ""
    val googleTtsApiKeyPlaceholder: String get() = if (hasStoredGoogleTtsApiKey && !googleTtsApiKeyModifiedThisSession) "••••••••••••••••" else ""
}

enum class ConnectionStatus { Idle, Testing, Success, Failed }
enum class WatchStatus { Unknown, NotFound, AppNotInstalled, Connected, Synced }

sealed interface SyncResult {
    data object Sending : SyncResult
    data object Success : SyncResult
    data class Error(val message: String) : SyncResult
}

sealed interface SaveStatus {
    data object Idle : SaveStatus
    data object Testing : SaveStatus
    data object Saving : SaveStatus
    data object Success : SaveStatus
    data class Warning(val errors: List<String>) : SaveStatus
}
