package org.openhab.habdroid.wear.phone.ui.complications

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.phone.data.LocalServerConfig
import org.openhab.habdroid.wear.phone.data.PhoneCredentialStore
import org.openhab.habdroid.wear.phone.ui.complications.model.ComplicationEditorState
import org.openhab.habdroid.wear.phone.ui.complications.model.ComplicationItem
import org.openhab.habdroid.wear.phone.ui.complications.model.ComplicationListDto
import org.openhab.habdroid.wear.phone.ui.complications.model.ComplicationState
import org.openhab.habdroid.wear.phone.ui.tiledesign.data.TileApiService
import javax.inject.Inject

sealed interface ComplicationUiState {
    data object Loading : ComplicationUiState
    data class Success(
        val editor: ComplicationEditorState,
        val isReadOnly: Boolean = false,
        val iconBaseUrl: String? = null,
        val iconAuthHeader: String? = null
    ) : ComplicationUiState
    data class Error(val message: String) : ComplicationUiState
}

@HiltViewModel
class ComplicationViewModel @Inject constructor(
    private val apiService: TileApiService,
    private val credentialStore: PhoneCredentialStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<ComplicationUiState>(ComplicationUiState.Loading)
    val uiState: StateFlow<ComplicationUiState> = _uiState.asStateFlow()

    private val _showItemPicker = MutableStateFlow(false)
    val showItemPicker: StateFlow<Boolean> = _showItemPicker.asStateFlow()

    private val _editingIndex = MutableStateFlow<Int?>(null)
    val editingIndex: StateFlow<Int?> = _editingIndex.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private var localConfig: LocalServerConfig? = null
    private var existsOnServer: Boolean = false

    init {
        loadComplications()
    }

    fun loadComplications() {
        viewModelScope.launch {
            _uiState.value = ComplicationUiState.Loading

            val remote = credentialStore.credentials.first()
            val local = credentialStore.localConfig.first()
            localConfig = local

            val serverUrl: String
            val username: String
            val password: String

            if (local != null && local.serverUrl.isNotBlank()) {
                serverUrl = local.serverUrl
                username = local.username
                password = local.password
            } else if (remote != null) {
                serverUrl = remote.serverUrl
                username = remote.username
                password = remote.password
            } else {
                _uiState.value = ComplicationUiState.Error("No server configured")
                return@launch
            }

            // Fetch complication list
            val complicationResult = apiService.getComplicationList(serverUrl, username, password)
            val dto = complicationResult.getOrElse { e ->
                _uiState.value = ComplicationUiState.Error("Failed to load: ${e.message}")
                return@launch
            }

            existsOnServer = dto != null

            // Fetch all items for the picker
            val itemsResult = apiService.getAllItems(serverUrl, username, password)
            val items = itemsResult.getOrDefault(emptyList()).map { item ->
                ComplicationItem(
                    name = item.name,
                    label = item.label,
                    type = item.type,
                    state = item.state,
                    category = item.category
                )
            }

            val editorState = if (dto != null) {
                ComplicationEditorState.fromDto(dto, items)
            } else {
                ComplicationEditorState(complications = emptyList(), allItems = items)
            }

            val isReadOnly = localConfig == null
            _uiState.value = ComplicationUiState.Success(
                editor = editorState,
                isReadOnly = isReadOnly,
                iconBaseUrl = serverUrl,
                iconAuthHeader = okhttp3.Credentials.basic(username, password)
            )
        }
    }

    fun showAddComplication() {
        _showItemPicker.value = true
    }

    fun dismissItemPicker() {
        _showItemPicker.value = false
    }

    fun addComplication(item: ComplicationItem) {
        _showItemPicker.value = false
        val state = (_uiState.value as? ComplicationUiState.Success) ?: return

        val newComplication = ComplicationState(
            item = item.name,
            label = item.displayLabel,
            icon = item.category ?: ""
        )

        val updated = state.editor.copy(
            complications = state.editor.complications + newComplication
        )
        _uiState.value = state.copy(editor = updated)
        save(updated)
    }

    fun editComplication(index: Int) {
        _editingIndex.value = index
    }

    fun dismissEditor() {
        _editingIndex.value = null
    }

    fun updateComplication(index: Int, complication: ComplicationState) {
        _editingIndex.value = null
        val state = (_uiState.value as? ComplicationUiState.Success) ?: return

        val updated = state.editor.copy(
            complications = state.editor.complications.toMutableList().also {
                it[index] = complication
            }
        )
        _uiState.value = state.copy(editor = updated)
        save(updated)
    }

    fun removeComplication(index: Int) {
        _editingIndex.value = null
        val state = (_uiState.value as? ComplicationUiState.Success) ?: return

        val updated = state.editor.copy(
            complications = state.editor.complications.toMutableList().also {
                it.removeAt(index)
            }
        )
        _uiState.value = state.copy(editor = updated)
        save(updated)
    }

    fun dismissSnackbar() {
        _snackbarMessage.value = null
    }

    /**
     * Import complication items from old wearTile metadata.
     * Maps: item name, icon (from config or item category), label (from item).
     * No per-type config exists in the old format — defaults are used.
     */
    fun importFromMetadata() {
        viewModelScope.launch {
            _isSaving.value = true
            _snackbarMessage.value = "Importing from metadata..."

            val config = localConfig
            if (config == null) {
                _snackbarMessage.value = "Local server not configured"
                _isSaving.value = false
                return@launch
            }

            // Fetch items with wearTile metadata
            val itemsResult = apiService.getItemsWithMetadata(
                config.serverUrl, config.username, config.password
            )
            if (itemsResult.isFailure) {
                _snackbarMessage.value = "Failed to fetch: ${itemsResult.exceptionOrNull()?.message}"
                _isSaving.value = false
                return@launch
            }

            val items = itemsResult.getOrDefault(emptyList())

            // Filter to complication items:
            // value == "complication" OR config.complication == "true"
            val complicationItems = items.filter { item ->
                val meta = item.metadata?.get("wearTile") ?: return@filter false
                meta.value == "complication" || meta.config?.get("complication") == "true"
            }

            if (complicationItems.isEmpty()) {
                _snackbarMessage.value = "No complication items found in metadata"
                _isSaving.value = false
                return@launch
            }

            // Convert to ComplicationState
            val imported = complicationItems.map { item ->
                val meta = item.metadata?.get("wearTile")
                val metaIcon = meta?.config?.get("icon")
                ComplicationState(
                    item = item.name,
                    label = item.label ?: item.name,
                    icon = metaIcon ?: item.category ?: ""
                )
            }

            // Merge with existing (don't duplicate items already configured)
            val state = (_uiState.value as? ComplicationUiState.Success) ?: return@launch
            val existingItemNames = state.editor.complications.map { it.item }.toSet()
            val newOnes = imported.filter { it.item !in existingItemNames }

            if (newOnes.isEmpty()) {
                _snackbarMessage.value = "All ${complicationItems.size} items already configured"
                _isSaving.value = false
                return@launch
            }

            val updated = state.editor.copy(
                complications = state.editor.complications + newOnes
            )
            _uiState.value = state.copy(editor = updated)
            save(updated)

            _snackbarMessage.value = "Imported ${newOnes.size} complications"
            _isSaving.value = false
        }
    }

    private fun save(editor: ComplicationEditorState) {
        val config = localConfig ?: run {
            _snackbarMessage.value = "Read-only: no local server configured"
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            val dto = editor.toDto()

            val result = if (existsOnServer) {
                apiService.updateComplicationList(config, dto)
            } else {
                apiService.createComplicationList(config, dto).also {
                    if (it.isSuccess) existsOnServer = true
                }
            }

            result
                .onSuccess {
                    Log.d(TAG, "Complications saved (${editor.complications.size} items)")
                }
                .onFailure { e ->
                    Log.w(TAG, "Failed to save complications", e)
                    _snackbarMessage.value = "Save failed: ${e.message}"
                }

            _isSaving.value = false
        }
    }

    companion object {
        private const val TAG = "ComplicationVM"
    }
}
