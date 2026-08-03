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
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.data.repository.CredentialStore
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import org.openhab.habdroid.wear.tile.OpenHabTileService
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
    val isConfigured: Flow<Boolean> = credentialStore.isConfigured

    /** Current config version — updates after cold load */
    private val _configVersion = MutableStateFlow(repository.lastConfigVersion)
    val configVersion: StateFlow<Int> = _configVersion.asStateFlow()

    private val _reloadState = MutableStateFlow<ReloadState>(ReloadState.Idle)
    val reloadState: StateFlow<ReloadState> = _reloadState.asStateFlow()

    fun reloadItems() {
        viewModelScope.launch {
            _reloadState.value = ReloadState.Loading
            repository.clearAndReload()
                .onSuccess { count ->
                    _reloadState.value = ReloadState.Success(count)
                    _configVersion.value = repository.lastConfigVersion
                    // Trigger tile refresh so it picks up new config
                    TileService.getUpdater(context)
                        .requestUpdate(OpenHabTileService::class.java)
                }
                .onFailure { e ->
                    _reloadState.value = ReloadState.Error(
                        e.localizedMessage ?: "Reload failed"
                    )
                }
        }
    }

    fun clearReloadState() {
        _reloadState.value = ReloadState.Idle
    }
}
