package org.openhab.habdroid.wear.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.data.repository.CredentialStore
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import javax.inject.Inject

sealed interface ReloadState {
    data object Idle : ReloadState
    data object Loading : ReloadState
    data class Success(val count: Int) : ReloadState
    data class Error(val message: String) : ReloadState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    credentialStore: CredentialStore,
    private val repository: OpenHabRepository
) : ViewModel() {
    val isConfigured: Flow<Boolean> = credentialStore.isConfigured

    private val _reloadState = MutableStateFlow<ReloadState>(ReloadState.Idle)
    val reloadState: StateFlow<ReloadState> = _reloadState.asStateFlow()

    fun reloadItems() {
        viewModelScope.launch {
            _reloadState.value = ReloadState.Loading
            repository.clearAndReload()
                .onSuccess { count ->
                    _reloadState.value = ReloadState.Success(count)
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
