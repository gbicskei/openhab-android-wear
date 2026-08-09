package org.openhab.habdroid.wear.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.tiles.TileService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.data.repository.CredentialStore
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import org.openhab.habdroid.wear.tile.OpenHabTileService
import org.openhab.habdroid.wear.util.AppLog
import javax.inject.Inject

sealed interface ReloadState {
    data object Idle : ReloadState
    data object Loading : ReloadState
    data class Success(val count: Int) : ReloadState
    data class Error(val message: String) : ReloadState
}

/** Handles main activity state — reload items from server, check configuration status. */
@HiltViewModel
class MainViewModel @Inject constructor(
    credentialStore: CredentialStore,
    private val repository: OpenHabRepository,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    val isConfigured: Flow<Boolean> = credentialStore.isConfigured

    /** Current user key from credentials (empty = default namespace) */
    val userKey: Flow<String> = credentialStore.credentials.map { it?.userKey ?: "" }

    /** Current config version — updates after cold load */
    private val _configVersion = MutableStateFlow(repository.lastConfigVersion)
    val configVersion: StateFlow<Int> = _configVersion.asStateFlow()

    private val _reloadState = MutableStateFlow<ReloadState>(ReloadState.Idle)
    val reloadState: StateFlow<ReloadState> = _reloadState.asStateFlow()

    /** Server connection status: null = unknown/checking, true = reachable, false = unreachable */
    private val _serverOnline = MutableStateFlow<Boolean?>(null)
    val serverOnline: StateFlow<Boolean?> = _serverOnline.asStateFlow()

    init {
        checkServerConnection()
    }

    /** Lightweight check if the openHAB server is reachable. */
    fun checkServerConnection() {
        viewModelScope.launch {
            _serverOnline.value = null
            AppLog.d(TAG, "→ checkServerConnection()")
            val result = repository.ping()
            _serverOnline.value = result.isSuccess
            AppLog.d(TAG, "← checkServerConnection() online=${result.isSuccess}")
        }
    }

    fun reloadItems() {
        viewModelScope.launch {
            AppLog.d(TAG, "→ reloadItems()")
            _reloadState.value = ReloadState.Loading
            repository.clearAndReload()
                .onSuccess { count ->
                    _reloadState.value = ReloadState.Success(count)
                    _configVersion.value = repository.lastConfigVersion
                    AppLog.d(TAG, "← reloadItems() success: $count items, configVersion=${repository.lastConfigVersion}")
                    // Trigger tile refresh so it picks up new config
                    TileService.getUpdater(context)
                        .requestUpdate(OpenHabTileService::class.java)
                }
                .onFailure { e ->
                    _reloadState.value = ReloadState.Error(
                        e.localizedMessage ?: "Reload failed"
                    )
                    AppLog.d(TAG, "← reloadItems() failed: ${e.message}")
                }
        }
    }

    fun clearReloadState() {
        _reloadState.value = ReloadState.Idle
    }
}
