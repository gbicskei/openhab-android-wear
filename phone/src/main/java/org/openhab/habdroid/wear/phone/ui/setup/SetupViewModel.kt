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
import org.openhab.habdroid.wear.shared.model.ServerCredentials
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
            // Load user key
            val userKey = credentialStore.currentUserKey
            _uiState.update { it.copy(userKey = userKey, hasUnsavedChanges = false) }
        }
    }

    private fun observeWatchConnection() {
        viewModelScope.launch {
            dataLayerSender.watchConnectionState().collect { info ->
                _uiState.update { state ->
                    val newStatus = when {
                        info == null -> WatchStatus.NotFound
                        state.watchStatus == WatchStatus.Synced -> WatchStatus.Synced
                        else -> WatchStatus.Connected
                    }
                    state.copy(
                        watchStatus = newStatus,
                        watchName = info?.displayName,
                        watchNearby = info?.isNearby ?: false
                    )
                }
            }
        }
    }

    /**
     * Check if the watch's config is out of sync with the phone/server.
     * Compares both configVersion (tile layout) and theme.
     */
    fun checkConfigSync() {
        viewModelScope.launch {
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
                    _uiState.update { it.copy(configOutOfSync = false) }
                    return@launch
                }

                val outOfSync = watchVersion != serverVersion
                AppLog.d("SetupVM", "Sync check: outOfSync=$outOfSync")
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

    // ─── Save All ───

    fun saveAll() {
        val state = _uiState.value

        viewModelScope.launch {
            // Save user key
            credentialStore.saveUserKey(state.userKey)

            // Save main server credentials
            val effectivePassword = getEffectivePassword()
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
            val effectiveConfigPassword = getEffectiveConfigPassword()
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

            _uiState.update {
                it.copy(
                    hasUnsavedChanges = false,
                    hasStoredPassword = effectivePassword.isNotBlank(),
                    configHasStoredPassword = effectiveConfigPassword.isNotBlank()
                )
            }
            AppLog.d("SetupVM", "All settings saved")
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
                userKey = state.userKey
            )

            dataLayerSender.sendCredentials(credentials)
                .onSuccess {
                    // Also send reload signal so the watch refreshes tile config
                    try {
                        dataLayerSender.sendReload()
                    } catch (_: Exception) {}
                    // Send the selected theme to the watch
                    try {
                        val theme = credentialStore.getSelectedTheme()
                        dataLayerSender.sendTheme(theme)
                    } catch (_: Exception) {}
                    _uiState.update { it.copy(syncResult = SyncResult.Success, watchStatus = WatchStatus.Synced, configOutOfSync = false) }
                    // Re-check sync status after a short delay (watch needs time to reload + write DataItem)
                    kotlinx.coroutines.delay(3000)
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
    // Watch
    val watchStatus: WatchStatus = WatchStatus.Unknown,
    val watchName: String? = null,
    val watchNearby: Boolean = false,
    val syncResult: SyncResult? = null,
    val configOutOfSync: Boolean = false,
    // Unsaved changes tracking
    val hasUnsavedChanges: Boolean = false
) {
    val canTest: Boolean get() = serverUrl.isNotBlank() && connectionStatus != ConnectionStatus.Testing
    val canTestConfig: Boolean get() = configServerUrl.isNotBlank() && configConnectionStatus != ConnectionStatus.Testing
    val canSave: Boolean get() = serverUrl.isNotBlank() && hasUnsavedChanges
    val canSendToWatch: Boolean get() = (connectionStatus == ConnectionStatus.Success || hasStoredPassword) &&
        watchStatus != WatchStatus.NotFound &&
        watchStatus != WatchStatus.Unknown &&
        syncResult != SyncResult.Sending
    val connectionTypeLabel: String? get() = when {
        watchStatus == WatchStatus.NotFound || watchStatus == WatchStatus.Unknown -> null
        watchNearby -> "Bluetooth"
        else -> "Cloud"
    }
    val passwordPlaceholder: String get() = if (hasStoredPassword && !passwordModifiedThisSession) "••••••••" else ""
    val configPasswordPlaceholder: String get() = if (configHasStoredPassword && !configPasswordModifiedThisSession) "••••••••" else ""
}

enum class ConnectionStatus { Idle, Testing, Success, Failed }
enum class WatchStatus { Unknown, NotFound, Connected, Synced }

sealed interface SyncResult {
    data object Sending : SyncResult
    data object Success : SyncResult
    data class Error(val message: String) : SyncResult
}
