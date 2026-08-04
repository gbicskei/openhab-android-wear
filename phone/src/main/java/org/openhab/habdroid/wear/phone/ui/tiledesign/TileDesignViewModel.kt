package org.openhab.habdroid.wear.phone.ui.tiledesign

import org.openhab.habdroid.wear.phone.util.AppLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.openhab.habdroid.wear.phone.data.LocalServerConfig
import org.openhab.habdroid.wear.phone.data.PhoneCredentialStore
import org.openhab.habdroid.wear.phone.ui.tiledesign.data.ApiException
import org.openhab.habdroid.wear.phone.ui.tiledesign.data.TileApiService
import org.openhab.habdroid.wear.phone.ui.tiledesign.model.PhoneItem
import org.openhab.habdroid.wear.phone.ui.tiledesign.model.SlotAction
import org.openhab.habdroid.wear.phone.ui.tiledesign.model.StateDisplay
import org.openhab.habdroid.wear.phone.ui.tiledesign.model.TileEditorState
import org.openhab.habdroid.wear.phone.ui.tiledesign.model.TilePageState
import org.openhab.habdroid.wear.phone.ui.tiledesign.model.TileSlotState
import org.openhab.habdroid.wear.phone.ui.tiledesign.model.WearTilePageDto
import org.openhab.habdroid.wear.shared.sync.SyncConstants
import javax.inject.Inject

sealed interface TileDesignUiState {
    data object Loading : TileDesignUiState
    data class Success(
        val editor: TileEditorState,
        val isReadOnly: Boolean = false,
        val iconBaseUrl: String? = null,
        val iconAuthHeader: String? = null
    ) : TileDesignUiState
    data class Error(val message: String) : TileDesignUiState
}

/**
 * ViewModel for the tile design editor.
 * Reads tile config from wear:tile UI components, writes via local server.
 */
@HiltViewModel
class TileDesignViewModel @Inject constructor(
    private val apiService: TileApiService,
    private val credentialStore: PhoneCredentialStore,
    private val watchStatusReader: org.openhab.habdroid.wear.phone.sync.WatchStatusReader,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<TileDesignUiState>(TileDesignUiState.Loading)
    val uiState: StateFlow<TileDesignUiState> = _uiState.asStateFlow()

    private val _showItemPicker = MutableStateFlow(false)
    val showItemPicker: StateFlow<Boolean> = _showItemPicker.asStateFlow()

    private val _editingSlot = MutableStateFlow<Pair<String, Int>?>(null)
    val editingSlot: StateFlow<Pair<String, Int>?> = _editingSlot.asStateFlow()

    private val _showConfigSheet = MutableStateFlow(false)
    val showConfigSheet: StateFlow<Boolean> = _showConfigSheet.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    /** Selected theme name. */
    private val _selectedTheme = MutableStateFlow(credentialStore.getSelectedTheme())
    val selectedTheme: StateFlow<String> = _selectedTheme.asStateFlow()

    /** Tracks if user manually changed the theme this session (prevents DataClient override) */
    private var themeModifiedByUser = false

    /** Cached local config for write operations. */
    private var localConfig: LocalServerConfig? = null

    init {
        loadTileConfig()
    }

    /**
     * Read the current watch theme from DataClient and update the selector.
     * Falls back to the locally stored theme if DataClient is unavailable.
     */
    private fun loadWatchTheme() {
        viewModelScope.launch {
            val watchTheme = watchStatusReader.readTheme()
            if (watchTheme != null && watchTheme.isNotBlank() && !themeModifiedByUser) {
                _selectedTheme.value = watchTheme
                credentialStore.saveSelectedTheme(watchTheme)
            }
        }
    }

    fun loadTileConfig() {
        viewModelScope.launch {
            _uiState.value = TileDesignUiState.Loading

            // Determine which server to use for reads
            val remote = credentialStore.credentials.first()
            val local = credentialStore.localConfig.first()
            localConfig = local

            val serverUrl: String
            val username: String
            val password: String

            // Prefer local server if configured (has all data + faster)
            if (local != null && local.isConfigured && local.hasAuth) {
                serverUrl = local.serverUrl
                username = local.username
                password = local.password
            } else if (remote != null && remote.isConfigured) {
                serverUrl = remote.serverUrl
                username = remote.username
                password = remote.password
            } else {
                _uiState.value = TileDesignUiState.Error("No server credentials configured")
                return@launch
            }

            // Load tile pages
            val namespace = credentialStore.tileNamespace
            val tilesResult = apiService.getAllTilePages(serverUrl, username, password, namespace)
            if (tilesResult.isFailure) {
                _uiState.value = TileDesignUiState.Error(
                    tilesResult.exceptionOrNull()?.message ?: "Failed to load tile config"
                )
                return@launch
            }

            // Load items for picker
            val allItemsResult = apiService.getAllItems(serverUrl, username, password)
            val allItems = allItemsResult.getOrDefault(emptyList())

            // Parse tile pages
            val tilePageDtos = tilesResult.getOrDefault(emptyList())
                .filter { it.isTilePage }

            val pages = if (tilePageDtos.isEmpty()) {
                // No config yet — create default main page
                val defaultMain = TilePageState(uid = "main", label = "Main", layout = 6)
                // Persist the default main page to the server so the watch can find it
                if (local != null && local.isConfigured && (local.hasAuth || local.hasApiToken)) {
                    val ns = credentialStore.tileNamespace
                    apiService.createTilePage(local, defaultMain.toDto(), ns)
                }
                listOf(defaultMain)
            } else {
                val parsed = tilePageDtos.map { TilePageState.fromDto(it) }
                // Ensure a main page exists even if only sub-pages were created
                if (parsed.none { it.uid == "main" }) {
                    val defaultMain = TilePageState(uid = "main", label = "Main", layout = 6)
                    if (local != null && local.isConfigured && (local.hasAuth || local.hasApiToken)) {
                        val ns = credentialStore.tileNamespace
                        apiService.createTilePage(local, defaultMain.toDto(), ns)
                    }
                    parsed + defaultMain
                } else {
                    parsed
                }
            }

            // Ensure main is first
            val sortedPages = pages.sortedWith(
                compareBy { if (it.uid == "main") 0 else 1 }
            )

            val isReadOnly = local == null || !local.isConfigured || (!local.hasAuth && !local.hasApiToken)

            _uiState.value = TileDesignUiState.Success(
                editor = TileEditorState(
                    pages = sortedPages,
                    currentPageIndex = 0,
                    allItems = allItems
                ),
                isReadOnly = isReadOnly,
                iconBaseUrl = serverUrl,
                iconAuthHeader = okhttp3.Credentials.basic(username, password)
            )
        }
    }

    fun selectPage(index: Int) {
        val state = (_uiState.value as? TileDesignUiState.Success) ?: return
        _uiState.value = state.copy(
            editor = state.editor.copy(
                currentPageIndex = index.coerceIn(0, state.editor.pages.lastIndex)
            )
        )
    }

    fun onLayoutChanged(newLayout: Int) {
        val state = (_uiState.value as? TileDesignUiState.Success) ?: return
        val currentPage = state.editor.currentPage

        // Keep ALL slot definitions — just change the layout count.
        // Slots beyond the layout count are preserved but not displayed.
        val updatedPage = currentPage.copy(layout = newLayout)

        updatePageInState(updatedPage)
        savePage(updatedPage)
    }

    fun onEmptySlotTapped(page: String, position: Int) {
        _editingSlot.value = page to position
        _showItemPicker.value = true
    }

    fun onFilledSlotTapped(page: String, position: Int) {
        _editingSlot.value = page to position
        _showConfigSheet.value = true
    }

    fun dismissItemPicker() {
        _showItemPicker.value = false
    }

    fun dismissConfigSheet() {
        _showConfigSheet.value = false
    }

    fun dismissSnackbar() {
        _snackbarMessage.value = null
    }

    fun onThemeSelected(themeName: String) {
        themeModifiedByUser = true
        _selectedTheme.value = themeName
        viewModelScope.launch {
            credentialStore.saveSelectedTheme(themeName)
        }
    }

    /** User selected an item from the picker. */
    fun assignItemToSlot(item: PhoneItem) {
        val (pageUid, position) = _editingSlot.value ?: return
        _showItemPicker.value = false

        val slot = TileSlotState(
            position = position,
            item = item.name,
            icon = item.category,
            label = null,
            stateDisplay = if (item.isToggleable) StateDisplay.COLOR else StateDisplay.VALUE,
            action = SlotAction.Toggle
        )

        addOrUpdateSlot(pageUid, slot)
        _showConfigSheet.value = true
    }

    /** User chose "Navigate to page" for a slot. */
    fun assignNavigationToSlot(targetPage: String, label: String?, icon: String?) {
        val (pageUid, position) = _editingSlot.value ?: return
        _showItemPicker.value = false

        val slot = TileSlotState(
            position = position,
            item = null,
            icon = icon,
            label = label ?: targetPage.replaceFirstChar { it.uppercase() },
            action = SlotAction.Navigate(targetPage),
            aggregateState = true
        )

        addOrUpdateSlot(pageUid, slot)
    }

    /** Update slot config from the config sheet. */
    fun updateSlotConfig(updatedSlot: TileSlotState) {
        val (pageUid, _) = _editingSlot.value ?: return
        addOrUpdateSlot(pageUid, updatedSlot)
        _showConfigSheet.value = false
    }

    /** Remove a slot from a page. */
    fun removeSlot(pageUid: String, position: Int) {
        val state = (_uiState.value as? TileDesignUiState.Success) ?: return
        val page = state.editor.pages.find { it.uid == pageUid } ?: return
        val updatedPage = page.copy(slots = page.slots.filter { it.position != position })
        updatePageInState(updatedPage)
        savePage(updatedPage)
        _showConfigSheet.value = false
    }

    /** Swap a slot's position with whatever is at the target position. */
    fun swapSlotPosition(pageUid: String, fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return
        val state = (_uiState.value as? TileDesignUiState.Success) ?: return
        val page = state.editor.pages.find { it.uid == pageUid } ?: return

        val updatedSlots = page.slots.map { slot ->
            when (slot.position) {
                fromPosition -> slot.copy(position = toPosition)
                toPosition -> slot.copy(position = fromPosition)
                else -> slot
            }
        }

        val updatedPage = page.copy(slots = updatedSlots)
        updatePageInState(updatedPage)
        savePage(updatedPage)

        // Update the editing slot reference to follow the moved item
        _editingSlot.value = pageUid to toPosition
    }

    /** Add a new page. */
    fun addPage(pageName: String) {
        val state = (_uiState.value as? TileDesignUiState.Success) ?: return
        val uid = pageName.trim().lowercase().replace(" ", "_")
        if (uid.isBlank() || state.editor.pageNames.contains(uid)) return

        val newPage = TilePageState(
            uid = uid,
            label = pageName.trim().replaceFirstChar { it.uppercase() },
            layout = 6
        )

        val newPages = state.editor.pages + newPage
        _uiState.value = state.copy(
            editor = state.editor.copy(pages = newPages, currentPageIndex = newPages.lastIndex)
        )

        // Create on server
        viewModelScope.launch {
            val config = localConfig ?: return@launch
            val namespace = credentialStore.tileNamespace
            apiService.createTilePage(config, newPage.toDto(), namespace)
                .onFailure { _snackbarMessage.value = "Failed to create page: ${it.message}" }
                .onSuccess { _snackbarMessage.value = "Page created" }
        }
    }

    /** Delete a non-main page. */
    fun deletePage(pageUid: String) {
        if (pageUid == "main") return
        val state = (_uiState.value as? TileDesignUiState.Success) ?: return

        val newPages = state.editor.pages.filter { it.uid != pageUid }
        val newIndex = state.editor.currentPageIndex.coerceAtMost(newPages.lastIndex)
        _uiState.value = state.copy(
            editor = state.editor.copy(pages = newPages, currentPageIndex = newIndex)
        )

        viewModelScope.launch {
            val config = localConfig ?: return@launch
            val namespace = credentialStore.tileNamespace
            apiService.deleteTilePage(config, pageUid, namespace)
                .onFailure { _snackbarMessage.value = "Failed to delete page: ${it.message}" }
                .onSuccess { _snackbarMessage.value = "Page deleted" }
        }
    }

    /** Rename a page's display label. The uid remains unchanged. */
    fun renamePage(pageUid: String, newLabel: String) {
        val state = (_uiState.value as? TileDesignUiState.Success) ?: return
        val page = state.editor.pages.find { it.uid == pageUid } ?: return

        val updatedPage = page.copy(label = newLabel)
        updatePageInState(updatedPage)
        savePage(updatedPage)
    }

    /**
     * Import tile config from existing wearTile item metadata (migration helper).
     * Reads items with wearTile metadata, converts to wear:tile-page documents, saves to server.
     */
    fun importFromMetadata() {
        viewModelScope.launch {
            _isSaving.value = true
            _snackbarMessage.value = "Importing from metadata..."

            val config = localConfig
            if (config == null || !config.isConfigured) {
                _snackbarMessage.value = "Local server not configured"
                _isSaving.value = false
                return@launch
            }

            // Fetch items with wearTile metadata
            val itemsResult = apiService.getItemsWithMetadata(
                config.serverUrl, config.username, config.password
            )
            if (itemsResult.isFailure) {
                _snackbarMessage.value = "Failed to fetch metadata: ${itemsResult.exceptionOrNull()?.message}"
                _isSaving.value = false
                return@launch
            }

            val items = itemsResult.getOrDefault(emptyList())
            if (items.isEmpty()) {
                _snackbarMessage.value = "No items with wearTile metadata found"
                _isSaving.value = false
                return@launch
            }

            // Group by page, build slots
            val slotsByPage = mutableMapOf<String, MutableList<TileSlotState>>()
            for (item in items) {
                val meta = item.metadata?.get("wearTile") ?: continue
                val cfg = meta.config ?: continue
                val rawPosition = cfg["position"] ?: continue

                val (page, slot) = parseMetadataPosition(rawPosition)
                val action = when (val a = cfg["action"]) {
                    null -> SlotAction.Toggle
                    "command" -> SlotAction.Command
                    else -> if (a.startsWith("page:")) SlotAction.Navigate(a.removePrefix("page:")) else SlotAction.Toggle
                }

                val slotState = TileSlotState(
                    position = slot,
                    item = item.name,
                    icon = cfg["icon"],
                    label = cfg["label"],
                    stateDisplay = StateDisplay.fromApi(cfg["valueDisplay"]),
                    action = action,
                    actionCommand = cfg["commandValue"],
                    actionItem = cfg["commandItem"],
                    stateItem = cfg["valueItem"],
                    invertState = cfg["invertValue"]?.toBooleanStrictOrNull() ?: false,
                    actionConfirmation = cfg["needsConfirmation"]?.toBooleanStrictOrNull() ?: false,
                    aggregateState = cfg["aggregateState"]?.toBooleanStrictOrNull() ?: false
                )

                slotsByPage.getOrPut(page) { mutableListOf() }.add(slotState)
            }

            // Create tile pages on server
            var created = 0
            val namespace = credentialStore.tileNamespace
            for ((pageName, slots) in slotsByPage) {
                // Deduplicate: if multiple items share a position, reassign incrementally
                val deduped = mutableListOf<TileSlotState>()
                val usedPositions = mutableSetOf<Int>()
                for (slot in slots.sortedBy { it.position }) {
                    if (slot.position in usedPositions) {
                        // Find next free position
                        var nextPos = slot.position + 1
                        while (nextPos in usedPositions) nextPos++
                        deduped.add(slot.copy(position = nextPos))
                        usedPositions.add(nextPos)
                    } else {
                        deduped.add(slot)
                        usedPositions.add(slot.position)
                    }
                }

                val layout = (deduped.maxOfOrNull { it.position } ?: 6).coerceIn(1, 7)
                val page = TilePageState(
                    uid = pageName,
                    label = pageName.replaceFirstChar { it.uppercase() },
                    layout = layout,
                    slots = deduped
                )
                val result = apiService.createTilePage(config, page.toDto(), namespace)
                if (result.isFailure) {
                    // Try update if it already exists
                    apiService.updateTilePage(config, page.toDto(), namespace)
                }
                created++
            }

            _snackbarMessage.value = "Imported $created page(s) with ${slotsByPage.values.sumOf { it.size }} slots"
            _isSaving.value = false

            // Reload to show imported data
            loadTileConfig()
        }
    }

    private fun parseMetadataPosition(raw: String): Pair<String, Int> {
        val parts = raw.split(":", limit = 2)
        return when (parts.size) {
            1 -> "main" to (parts[0].toDoubleOrNull()?.toInt() ?: 1)
            2 -> parts[0] to (parts[1].toDoubleOrNull()?.toInt() ?: 1)
            else -> "main" to 1
        }
    }

    /** Send reload signal to the watch. */
    fun sendReloadToWatch() {
        viewModelScope.launch {
            try {
                val messageClient = Wearable.getMessageClient(context)
                val nodeClient = Wearable.getNodeClient(context)
                val nodes = nodeClient.connectedNodes.await()
                val watchNode = nodes.firstOrNull()
                if (watchNode != null) {
                    messageClient.sendMessage(
                        watchNode.id,
                        SyncConstants.PATH_RELOAD,
                        ByteArray(0)
                    ).await()
                    _snackbarMessage.value = "Watch tile reloaded"
                } else {
                    _snackbarMessage.value = "No watch connected"
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to send reload to watch", e)
                _snackbarMessage.value = "Failed to reach watch: ${e.message}"
            }
        }
    }

    // ─── Private helpers ───

    private fun addOrUpdateSlot(pageUid: String, slot: TileSlotState) {
        val state = (_uiState.value as? TileDesignUiState.Success) ?: return
        val page = state.editor.pages.find { it.uid == pageUid } ?: return

        val existingSlots = page.slots.filter { it.position != slot.position }
        val updatedPage = page.copy(slots = existingSlots + slot)

        updatePageInState(updatedPage)
        savePage(updatedPage)
    }

    private fun updatePageInState(updatedPage: TilePageState) {
        val state = (_uiState.value as? TileDesignUiState.Success) ?: return
        val newPages = state.editor.pages.map { if (it.uid == updatedPage.uid) updatedPage else it }
        _uiState.value = state.copy(editor = state.editor.copy(pages = newPages))
    }

    private fun savePage(page: TilePageState) {
        viewModelScope.launch {
            val config = localConfig
            if (config == null || !config.isConfigured) {
                _snackbarMessage.value = "Local server not configured — cannot save"
                return@launch
            }

            // Increment configVersion on the main page to track changes
            val pageToSave = if (page.uid == "main") {
                page.copy(configVersion = page.configVersion + 1)
            } else page

            // Also update the state so the incremented version is reflected
            if (page.uid == "main") {
                updatePageInState(pageToSave)
            }

            val namespace = credentialStore.tileNamespace
            _isSaving.value = true
            apiService.updateTilePage(config, pageToSave.toDto(), namespace)
                .onFailure { e ->
                    // If 404, the page doesn't exist yet — create it
                    if (e is ApiException && e.code == 404) {
                        apiService.createTilePage(config, pageToSave.toDto(), namespace)
                            .onFailure { _snackbarMessage.value = "Save failed: ${it.message}" }
                    } else {
                        _snackbarMessage.value = "Save failed: ${e.message}"
                    }
                }
            _isSaving.value = false
        }
    }

    companion object {
        private const val TAG = "TileDesignVM"
    }
}
