package org.openhab.habdroid.wear.phone.ui.setup

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
import org.openhab.habdroid.wear.phone.data.PhoneCredentialStore
import org.openhab.habdroid.wear.phone.sync.NoNetworkException
import org.openhab.habdroid.wear.phone.sync.NoWatchConnectedException
import org.openhab.habdroid.wear.phone.sync.PhoneDataLayerSender
import org.openhab.habdroid.wear.phone.sync.WatchConnectionInfo
import org.openhab.habdroid.wear.shared.model.ServerCredentials
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val connectionTester: ConnectionTester,
    private val dataLayerSender: PhoneDataLayerSender,
    private val credentialStore: PhoneCredentialStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        loadSavedCredentials()
        observeWatchConnection()
    }

    private fun loadSavedCredentials() {
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
        }
    }

    /**
     * Observes watch connectivity in real-time via polling NodeClient every 5s.
     * Updates UI state immediately when the watch connects or disconnects.
     */
    private fun observeWatchConnection() {
        viewModelScope.launch {
            dataLayerSender.watchConnectionState().collect { info ->
                _uiState.update { state ->
                    // Don't downgrade from Synced to Connected (sync status is sticky)
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

    fun onServerUrlChanged(url: String) {
        _uiState.update { it.copy(serverUrl = url, connectionStatus = ConnectionStatus.Idle) }
    }

    fun onUsernameChanged(username: String) {
        _uiState.update { it.copy(username = username, connectionStatus = ConnectionStatus.Idle) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update {
            it.copy(
                password = password,
                connectionStatus = ConnectionStatus.Idle,
                passwordModifiedThisSession = true
            )
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
                // Save credentials locally on successful test
                credentialStore.saveCredentials(
                    ServerCredentials(
                        serverUrl = state.serverUrl.trim(),
                        username = state.username.trim(),
                        password = effectivePassword
                    )
                )
            }.onFailure { error ->
                val message = when (error) {
                    is InvalidCredentialsException -> "Invalid username or password"
                    else -> error.message ?: "Connection failed"
                }
                _uiState.update {
                    it.copy(connectionStatus = ConnectionStatus.Failed, errorMessage = message)
                }
            }
        }
    }

    fun sendToWatch() {
        val state = _uiState.value
        if (state.connectionStatus != ConnectionStatus.Success) return

        _uiState.update { it.copy(syncResult = SyncResult.Sending) }

        viewModelScope.launch {
            val effectivePassword = getEffectivePassword()
            val credentials = ServerCredentials(
                serverUrl = state.serverUrl.trim(),
                username = state.username.trim(),
                password = effectivePassword
            )

            dataLayerSender.sendCredentials(credentials)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            syncResult = SyncResult.Success,
                            watchStatus = WatchStatus.Synced
                        )
                    }
                }
                .onFailure { error ->
                    val message = when (error) {
                        is NoWatchConnectedException -> "No watch connected"
                        is NoNetworkException -> "No network connection"
                        else -> error.message ?: "Failed to send"
                    }
                    _uiState.update {
                        it.copy(syncResult = SyncResult.Error(message))
                    }
                }
        }
    }

    fun dismissSyncResult() {
        _uiState.update { it.copy(syncResult = null) }
    }

    /**
     * Returns the password to use for operations.
     * If the user modified the password this session, use what they typed.
     * Otherwise, read the stored password directly from the encrypted store.
     */
    private suspend fun getEffectivePassword(): String {
        val state = _uiState.value
        return if (state.passwordModifiedThisSession) {
            state.password
        } else {
            credentialStore.credentials.first()?.password ?: ""
        }
    }
}

data class SetupUiState(
    val serverUrl: String = "https://myopenhab.org",
    val username: String = "",
    val password: String = "",
    val hasStoredPassword: Boolean = false,
    val passwordModifiedThisSession: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Idle,
    val errorMessage: String? = null,
    val watchStatus: WatchStatus = WatchStatus.Unknown,
    val watchName: String? = null,
    val watchNearby: Boolean = false,
    val syncResult: SyncResult? = null
) {
    val canTest: Boolean get() = serverUrl.isNotBlank() && connectionStatus != ConnectionStatus.Testing
    val canSendToWatch: Boolean get() = connectionStatus == ConnectionStatus.Success &&
        watchStatus != WatchStatus.NotFound &&
        syncResult != SyncResult.Sending
    val connectionTypeLabel: String? get() = when {
        watchStatus == WatchStatus.NotFound || watchStatus == WatchStatus.Unknown -> null
        watchNearby -> "Bluetooth"
        else -> "Cloud"
    }
    /** The placeholder to show in the password field when not modified this session */
    val passwordPlaceholder: String get() = if (hasStoredPassword && !passwordModifiedThisSession) "••••••••" else ""
}

enum class ConnectionStatus { Idle, Testing, Success, Failed }
enum class WatchStatus { Unknown, NotFound, Connected, Synced }

sealed interface SyncResult {
    data object Sending : SyncResult
    data object Success : SyncResult
    data class Error(val message: String) : SyncResult
}
