package org.openhab.habdroid.wear.ui.tile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.data.model.TileItem
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import org.openhab.habdroid.wear.data.repository.TilePreferenceStore
import javax.inject.Inject

sealed interface TileConfigUiState {
    data object Loading : TileConfigUiState
    data class Success(
        /** Items currently selected for the tile (in display order) */
        val selectedItems: List<TileItem>,
        /** All wearTile items from server that are NOT yet selected */
        val availableItems: List<TileItem>,
        /** Whether the max of 6 items has been reached */
        val isFull: Boolean
    ) : TileConfigUiState

    data class Error(val message: String) : TileConfigUiState
}

/** Provides the list of configured tile items for the on-watch tile configuration screen. */
@HiltViewModel
class TileConfigViewModel @Inject constructor(
    private val repository: OpenHabRepository,
    private val tilePreferenceStore: TilePreferenceStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<TileConfigUiState>(TileConfigUiState.Loading)
    val uiState: StateFlow<TileConfigUiState> = _uiState.asStateFlow()

    /** Tracks whether changes were made (to know if tile needs refresh) */
    var hasChanges: Boolean = false
        private set

    init {
        loadItems()
    }

    fun loadItems() {
        viewModelScope.launch {
            _uiState.value = TileConfigUiState.Loading
            repository.getAvailableTileItems()
                .onSuccess { allItems ->
                    // Editor only manages main page items
                    val mainPageItems = allItems.filter { it.page == TileItem.PAGE_MAIN }
                    val selectedNames = tilePreferenceStore.selectedItemNames.first()
                    buildState(mainPageItems, selectedNames)
                }
                .onFailure { error ->
                    _uiState.value = TileConfigUiState.Error(
                        error.localizedMessage ?: "Failed to load items"
                    )
                }
        }
    }

    private fun buildState(allItems: List<TileItem>, selectedNames: List<String>) {
        val selected = if (selectedNames.isEmpty()) {
            // No local selection yet — default to all available items, capped at 6
            // Persist this default so subsequent add/remove works correctly
            val defaults = allItems.take(6)
            viewModelScope.launch {
                tilePreferenceStore.saveSelection(defaults.map { it.item.name })
            }
            defaults
        } else {
            selectedNames.mapNotNull { name ->
                allItems.find { it.item.name == name }
            }
        }
        val selectedNameSet = selected.map { it.item.name }.toSet()
        val available = allItems.filter { it.item.name !in selectedNameSet }

        _uiState.value = TileConfigUiState.Success(
            selectedItems = selected,
            availableItems = available,
            isFull = selected.size >= 6
        )
    }

    fun removeItem(itemName: String) {
        viewModelScope.launch {
            val state = _uiState.value as? TileConfigUiState.Success ?: return@launch
            val newSelected = state.selectedItems.filter { it.item.name != itemName }
            val removed = state.selectedItems.find { it.item.name == itemName }
            val newAvailable = if (removed != null) state.availableItems + removed else state.availableItems

            tilePreferenceStore.saveSelection(newSelected.map { it.item.name })
            hasChanges = true

            _uiState.value = state.copy(
                selectedItems = newSelected,
                availableItems = newAvailable,
                isFull = newSelected.size >= 6
            )
        }
    }

    fun addItem(itemName: String) {
        viewModelScope.launch {
            val state = _uiState.value as? TileConfigUiState.Success ?: return@launch
            if (state.isFull) return@launch

            val itemToAdd = state.availableItems.find { it.item.name == itemName } ?: return@launch
            val newSelected = state.selectedItems + itemToAdd
            val newAvailable = state.availableItems.filter { it.item.name != itemName }

            tilePreferenceStore.saveSelection(newSelected.map { it.item.name })
            hasChanges = true

            _uiState.value = state.copy(
                selectedItems = newSelected,
                availableItems = newAvailable,
                isFull = newSelected.size >= 6
            )
        }
    }
}
