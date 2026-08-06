package org.openhab.habdroid.wear.phone.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.phone.data.PhoneCredentialStore
import org.openhab.habdroid.wear.phone.util.AppLog
import javax.inject.Inject

data class SettingsUiState(
    val debugMode: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val needsSync: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val credentialStore: PhoneCredentialStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(debugMode = credentialStore.isDebugMode))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // Keep legacy flow for backward compat (HomeScreen reads debugMode to show/hide debug log card)
    private val _debugMode = MutableStateFlow(credentialStore.isDebugMode)
    val debugMode: StateFlow<Boolean> = _debugMode.asStateFlow()

    fun setDebugMode(enabled: Boolean) {
        _uiState.update { it.copy(debugMode = enabled, hasUnsavedChanges = true) }
    }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            credentialStore.setDebugMode(state.debugMode)
            credentialStore.markSettingsNeedSync()
            _debugMode.value = state.debugMode
            _uiState.update { it.copy(hasUnsavedChanges = false, needsSync = true) }
            AppLog.d("SettingsVM", "Settings saved (debugMode=${state.debugMode})")
        }
    }
}
