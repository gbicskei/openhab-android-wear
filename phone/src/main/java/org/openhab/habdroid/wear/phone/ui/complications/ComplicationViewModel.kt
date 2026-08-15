package org.openhab.habdroid.wear.phone.ui.complications

import org.openhab.habdroid.wear.phone.util.AppLog
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
import org.openhab.habdroid.wear.phone.ui.complications.model.ComplicationType
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

/**
 * Manages the complication editor with fixed 10-slot model.
 * Each slot (1–10) maps to a dedicated ComplicationDataSourceService on the watch.
 * Loads, saves, and manages slot configurations via the wear:complication-list REST endpoint.
 */
@HiltViewModel
class ComplicationViewModel @Inject constructor(
    private val apiService: TileApiService,
    private val credentialStore: PhoneCredentialStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<ComplicationUiState>(ComplicationUiState.Loading)
    val uiState: StateFlow<ComplicationUiState> = _uiState.asStateFlow()

    /** Slot number being assigned an item (shows item picker). Null when picker is closed. */
    private val _assigningSlot = MutableStateFlow<Int?>(null)
    val assigningSlot: StateFlow<Int?> = _assigningSlot.asStateFlow()

    /** Slot number being edited (shows config sheet). Null when editor is closed. */
    private val _editingSlot = MutableStateFlow<Int?>(null)
    val editingSlot: StateFlow<Int?> = _editingSlot.asStateFlow()

    /** Slot number pending deletion confirmation. Null when no delete pending. */
    private val _confirmingDelete = MutableStateFlow<Int?>(null)
    val confirmingDelete: StateFlow<Int?> = _confirmingDelete.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private var localConfig: LocalServerConfig? = null
    private var remoteCredentials: org.openhab.habdroid.wear.shared.model.ServerCredentials? = null
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
            remoteCredentials = remote

            val serverUrl: String
            val authHeader: String?

            if (local != null && local.serverUrl.isNotBlank()) {
                serverUrl = local.serverUrl
                authHeader = local.resolveAuthHeader()
            } else if (remote != null) {
                serverUrl = remote.serverUrl
                authHeader = if (remote.hasAuth) okhttp3.Credentials.basic(remote.username, remote.password) else null
            } else {
                _uiState.value = ComplicationUiState.Error("No server configured")
                return@launch
            }

            // Fetch complication list
            val namespace = credentialStore.tileNamespace
            val complicationResult = if (local != null && local.serverUrl.isNotBlank()) {
                apiService.getComplicationList(local, namespace)
            } else {
                apiService.getComplicationList(serverUrl, remote!!.username, remote.password, namespace)
            }
            val dto = complicationResult.getOrElse { e ->
                _uiState.value = ComplicationUiState.Error("Failed to load: ${e.message}")
                return@launch
            }

            existsOnServer = dto != null

            // Fetch all items for the picker
            val itemsResult = if (local != null && local.serverUrl.isNotBlank()) {
                apiService.getAllItems(local)
            } else {
                apiService.getAllItems(serverUrl, remote!!.username, remote.password)
            }
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
                ComplicationEditorState(allItems = items)
            }

            val isReadOnly = false
            _uiState.value = ComplicationUiState.Success(
                editor = editorState,
                isReadOnly = isReadOnly,
                iconBaseUrl = serverUrl,
                iconAuthHeader = authHeader
            )
        }
    }

    // ─── Slot Assignment (empty slot tapped → pick item) ───

    /** Open the item picker to assign an item to the next available empty slot. */
    fun addComplication() {
        val state = (_uiState.value as? ComplicationUiState.Success) ?: return
        val firstEmpty = (1..ComplicationListDto.MAX_SLOTS).firstOrNull { state.editor.slots[it] == null }
        if (firstEmpty != null) {
            _assigningSlot.value = firstEmpty
        }
    }

    /** Open the item picker to assign an item to the given slot number. */
    fun assignSlot(slotNumber: Int) {
        _assigningSlot.value = slotNumber
    }

    fun dismissItemPicker() {
        _assigningSlot.value = null
    }

    /** Item selected from picker — assign it to the slot and open editor immediately. */
    fun confirmAssignment(item: ComplicationItem) {
        val slotNumber = _assigningSlot.value ?: return
        _assigningSlot.value = null

        val state = (_uiState.value as? ComplicationUiState.Success) ?: return

        val newComplication = ComplicationState(
            item = item.name,
            label = item.displayLabel,
            icon = item.category ?: "",
            supportedTypes = ComplicationType.defaultsForItemType(item.type)
        )

        val updatedSlots = state.editor.slots.toMutableMap()
        updatedSlots[slotNumber] = newComplication

        val updated = state.editor.copy(slots = updatedSlots)
        _uiState.value = state.copy(editor = updated)
        save(updated)

        // Open the config sheet for the newly assigned slot
        _editingSlot.value = slotNumber
    }

    // ─── Slot Editing (configured slot tapped → edit config) ───

    /** Open the config sheet for an already-configured slot. */
    fun editSlot(slotNumber: Int) {
        _editingSlot.value = slotNumber
    }

    fun dismissEditor() {
        _editingSlot.value = null
    }

    /** Save updated configuration for the slot being edited. */
    fun updateSlot(slotNumber: Int, complication: ComplicationState) {
        _editingSlot.value = null
        val state = (_uiState.value as? ComplicationUiState.Success) ?: return

        val updatedSlots = state.editor.slots.toMutableMap()
        updatedSlots[slotNumber] = complication

        val updated = state.editor.copy(slots = updatedSlots)
        _uiState.value = state.copy(editor = updated)
        save(updated)
    }

    /** Request deletion of a slot — shows confirmation dialog. */
    fun clearSlot(slotNumber: Int) {
        _editingSlot.value = null
        _confirmingDelete.value = slotNumber
    }

    /** Cancel the pending deletion. */
    fun cancelDelete() {
        _confirmingDelete.value = null
    }

    /** Confirm deletion: remove the slot and renumber remaining slots to close the gap. */
    fun confirmDelete() {
        val slotNumber = _confirmingDelete.value ?: return
        _confirmingDelete.value = null

        val state = (_uiState.value as? ComplicationUiState.Success) ?: return

        // Get all configured slots sorted by number, remove the target
        val configuredSlots = state.editor.slots.entries
            .filter { it.value != null }
            .sortedBy { it.key }
            .map { it.value!! }
            .toMutableList()

        // Find and remove the slot by its current number
        val indexToRemove = state.editor.slots.entries
            .filter { it.value != null }
            .sortedBy { it.key }
            .indexOfFirst { it.key == slotNumber }

        if (indexToRemove >= 0) {
            configuredSlots.removeAt(indexToRemove)
        }

        // Renumber: assign sequential slot numbers 1, 2, 3...
        val renumbered = (1..ComplicationListDto.MAX_SLOTS).associateWith { idx ->
            configuredSlots.getOrNull(idx - 1)
        }

        val updated = state.editor.copy(slots = renumbered)
        _uiState.value = state.copy(editor = updated)
        save(updated)
    }

    // ─── Snackbar ───

    fun dismissSnackbar() {
        _snackbarMessage.value = null
    }

    // ─── Import from legacy metadata ───

    /**
     * Import complication items from old wearTile metadata.
     * Assigns imported items to the first available empty slots.
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
            val itemsResult = apiService.getItemsWithMetadata(config)
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

            val state = (_uiState.value as? ComplicationUiState.Success) ?: return@launch

            // Find already-configured item names
            val existingItemNames = state.editor.slots.values
                .filterNotNull()
                .map { it.item }
                .toSet()

            // Filter out items already configured
            val newItems = complicationItems.filter { it.name !in existingItemNames }

            if (newItems.isEmpty()) {
                _snackbarMessage.value = "All ${complicationItems.size} items already configured"
                _isSaving.value = false
                return@launch
            }

            // Find empty slots and assign items
            val emptySlots = (1..ComplicationListDto.MAX_SLOTS)
                .filter { state.editor.slots[it] == null }
                .iterator()

            val updatedSlots = state.editor.slots.toMutableMap()
            var assignedCount = 0

            for (item in newItems) {
                if (!emptySlots.hasNext()) break
                val slotNumber = emptySlots.next()
                val meta = item.metadata?.get("wearTile")
                val metaIcon = meta?.config?.get("icon")
                updatedSlots[slotNumber] = ComplicationState(
                    item = item.name,
                    label = item.label ?: item.name,
                    icon = metaIcon ?: item.category ?: ""
                )
                assignedCount++
            }

            val skipped = newItems.size - assignedCount
            val updated = state.editor.copy(slots = updatedSlots)
            _uiState.value = state.copy(editor = updated)
            save(updated)

            val message = buildString {
                append("Imported $assignedCount complications")
                if (skipped > 0) append(" ($skipped skipped — no empty slots)")
            }
            _snackbarMessage.value = message
            _isSaving.value = false
        }
    }

    // ─── Persistence ───

    private fun save(editor: ComplicationEditorState) {
        viewModelScope.launch {
            _isSaving.value = true
            val dto = editor.toDto()
            val namespace = credentialStore.tileNamespace

            val local = localConfig
            val remote = remoteCredentials

            val result = if (existsOnServer) {
                if (local != null && local.serverUrl.isNotBlank()) {
                    apiService.updateComplicationList(local, dto, namespace)
                } else if (remote != null) {
                    apiService.updateComplicationList(
                        LocalServerConfig(serverUrl = remote.serverUrl, username = remote.username, password = remote.password),
                        dto, namespace
                    )
                } else {
                    Result.failure(Exception("No server configured"))
                }
            } else {
                // Try PUT first (document may exist from previous version), then POST
                val writeConfig = if (local != null && local.serverUrl.isNotBlank()) {
                    local
                } else if (remote != null) {
                    LocalServerConfig(serverUrl = remote.serverUrl, username = remote.username, password = remote.password)
                } else {
                    _snackbarMessage.value = "No server configured"
                    _isSaving.value = false
                    return@launch
                }
                val putResult = apiService.updateComplicationList(writeConfig, dto, namespace)
                if (putResult.isSuccess) {
                    existsOnServer = true
                    putResult
                } else {
                    apiService.createComplicationList(writeConfig, dto, namespace).also {
                        if (it.isSuccess) existsOnServer = true
                    }
                }
            }

            result
                .onSuccess {
                    AppLog.d(TAG, "Complications saved (${editor.configuredCount}/${ComplicationListDto.MAX_SLOTS} slots)")
                }
                .onFailure { e ->
                    AppLog.w(TAG, "Failed to save complications", e)
                    _snackbarMessage.value = "Save failed: ${e.message}"
                }

            _isSaving.value = false
        }
    }

    companion object {
        private const val TAG = "ComplicationVM"
    }
}
