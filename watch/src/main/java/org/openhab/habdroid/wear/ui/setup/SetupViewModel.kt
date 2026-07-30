package org.openhab.habdroid.wear.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.shared.model.ServerCredentials
import org.openhab.habdroid.wear.data.repository.CredentialStore
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import javax.inject.Inject

sealed interface SetupUiState {
    data class ManualEntry(
        val serverUrl: String = "https://myopenhab.org",
        val username: String = "",
        val password: String = ""
    ) : SetupUiState
    data object Success : SetupUiState
    data class Error(val message: String) : SetupUiState
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val credentialStore: CredentialStore,
    private val repository: OpenHabRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SetupUiState>(SetupUiState.ManualEntry())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    private var hasPrefilledFromStore = false

    init {
        // Load existing credentials if available (one-shot prefill only)
        viewModelScope.launch {
            credentialStore.credentials.collect { credentials ->
                if (!hasPrefilledFromStore && credentials != null) {
                    hasPrefilledFromStore = true
                    val current = _uiState.value
                    if (current is SetupUiState.ManualEntry && current.username.isEmpty()) {
                        _uiState.value = SetupUiState.ManualEntry(
                            serverUrl = credentials.serverUrl,
                            username = credentials.username,
                            password = credentials.password
                        )
                    }
                }
            }
        }
    }

    fun updateServerUrl(url: String) {
        val current = _uiState.value as? SetupUiState.ManualEntry ?: return
        _uiState.value = current.copy(serverUrl = url)
    }

    fun updateUsername(username: String) {
        val current = _uiState.value as? SetupUiState.ManualEntry ?: return
        _uiState.value = current.copy(username = username)
    }

    fun updatePassword(password: String) {
        val current = _uiState.value as? SetupUiState.ManualEntry ?: return
        _uiState.value = current.copy(password = password)
    }

    fun saveManualCredentials() {
        val state = _uiState.value as? SetupUiState.ManualEntry ?: return
        viewModelScope.launch {
            try {
                val credentials = ServerCredentials(
                    serverUrl = state.serverUrl.trim(),
                    username = state.username.trim(),
                    password = state.password
                )
                credentialStore.saveCredentials(credentials)

                // Verify connectivity by fetching items
                repository.getAllItems()
                    .onSuccess {
                        _uiState.value = SetupUiState.Success
                    }
                    .onFailure { e ->
                        _uiState.value = SetupUiState.Error(
                            "Connection failed: ${e.localizedMessage}"
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = SetupUiState.Error(
                    e.localizedMessage ?: "Setup failed"
                )
            }
        }
    }

    fun reset() {
        _uiState.value = SetupUiState.ManualEntry()
    }
}
