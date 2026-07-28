package org.openhab.habdroid.wear.complication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import javax.inject.Inject

/**
 * ViewModel for the complication configuration activity.
 * Fetches items flagged for complications and exposes them for the picker UI.
 */
@HiltViewModel
class ComplicationConfigViewModel @Inject constructor(
    private val repository: OpenHabRepository,
    private val complicationPreferenceStore: ComplicationPreferenceStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<ComplicationConfigUiState>(ComplicationConfigUiState.Loading)
    val uiState: StateFlow<ComplicationConfigUiState> = _uiState.asStateFlow()

    init {
        loadItems()
    }

    private fun loadItems() {
        viewModelScope.launch {
            _uiState.value = ComplicationConfigUiState.Loading

            repository.getComplicationItems()
                .onSuccess { items ->
                    if (items.isEmpty()) {
                        _uiState.value = ComplicationConfigUiState.Empty
                    } else {
                        _uiState.value = ComplicationConfigUiState.Success(items)
                    }
                }
                .onFailure { error ->
                    _uiState.value = ComplicationConfigUiState.Error(
                        error.message ?: "Failed to load items"
                    )
                }
        }
    }

    /**
     * Save the selected item for the given complication slot.
     */
    suspend fun selectItem(complicationId: Int, itemName: String) {
        complicationPreferenceStore.setItemForSlot(complicationId, itemName)
    }

    fun retry() {
        loadItems()
    }
}

/**
 * UI state for the complication configuration screen.
 */
sealed interface ComplicationConfigUiState {
    data object Loading : ComplicationConfigUiState
    data class Success(val items: List<Item>) : ComplicationConfigUiState
    data object Empty : ComplicationConfigUiState
    data class Error(val message: String) : ComplicationConfigUiState
}
