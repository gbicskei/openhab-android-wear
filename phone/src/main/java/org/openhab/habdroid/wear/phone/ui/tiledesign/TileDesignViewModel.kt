package org.openhab.habdroid.wear.phone.ui.tiledesign

import org.openhab.habdroid.wear.phone.util.AppLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed interface TileDesignUiState {
    data object Loading : TileDesignUiState
    data class Success(
        val editor: TileEditorState,
        val isReadOnly: Boolean = false,
        val iconBaseUrl: String? = null,
        val iconAuthHeader: String? = null,
        val watchScreenWidthDp: Int? = null
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
    private val okHttpClient: OkHttpClient,
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

    /** Watch screen width in dp, read from DataClient */
    private var watchScreenWidthDp: Int? = null

    /** Cached local config for write operations. */
    private var localConfig: LocalServerConfig? = null

    /** Live item states: item name → current state string */
    private val _itemStates = MutableStateFlow<Map<String, String>>(emptyMap())
    val itemStates: StateFlow<Map<String, String>> = _itemStates.asStateFlow()

    /** SSE connection job for live state updates */
    private var sseJob: Job? = null

    init {
        loadTileConfig()
        loadWatchStatus()
    }

    /**
     * Read the current watch status from DataClient (theme + screen size).
     * The locally persisted theme is authoritative — the watch theme is only used
     * as the initial value when no explicit selection has been saved yet.
     */
    private fun loadWatchStatus() {
        viewModelScope.launch {
            val status = watchStatusReader.readStatus()
            if (status != null) {
                status.screenWidthDp?.let { widthDp ->
                    watchScreenWidthDp = widthDp
                    // Update the UI state if already loaded
                    val current = _uiState.value
                    if (current is TileDesignUiState.Success) {
                        _uiState.value = current.copy(watchScreenWidthDp = widthDp)
                    }
                }
            }
        }
    }

    fun loadTileConfig() {
        viewModelScope.launch {
            val _traceStart = System.currentTimeMillis()
            AppLog.d(TAG, "→ loadTileConfig()")
            try {
            _uiState.value = TileDesignUiState.Loading

            // Determine which server to use — tile designer always uses local (direct) server.
            // Cloud relay doesn't support SSE and config writes require direct access.
            val remote = credentialStore.credentials.first()
            val local = credentialStore.localConfig.first()
            localConfig = local

            if (local == null || !local.isConfigured || (!local.hasAuth && !local.hasApiToken)) {
                _uiState.value = TileDesignUiState.Error("Local server not configured — required for tile designer")
                return@launch
            }

            val serverUrl = local.serverUrl

            // Load tile pages
            val namespace = credentialStore.tileNamespace
            val tilesResult = apiService.getAllTilePages(local, namespace)
            if (tilesResult.isFailure) {
                _uiState.value = TileDesignUiState.Error(
                    tilesResult.exceptionOrNull()?.message ?: "Failed to load tile config"
                )
                return@launch
            }

            // Load items for picker
            val allItemsResult = apiService.getAllItems(local)
            val allItems = allItemsResult.getOrDefault(emptyList())

            // Parse tile pages
            val tilePageDtos = tilesResult.getOrDefault(emptyList())
                .filter { it.isTilePage }

            val pages = if (tilePageDtos.isEmpty()) {
                // No config yet — create default main page
                val defaultMain = TilePageState(uid = "main", label = "Main", layout = 6)
                // Persist the default main page to the server so the watch can find it
                val ns = credentialStore.tileNamespace
                apiService.createTilePage(local, defaultMain.toDto(), ns)
                listOf(defaultMain)
            } else {
                val parsed = tilePageDtos.map { TilePageState.fromDto(it) }
                // Ensure a main page exists even if only sub-pages were created
                if (parsed.none { it.uid == "main" }) {
                    val defaultMain = TilePageState(uid = "main", label = "Main", layout = 6)
                    val ns = credentialStore.tileNamespace
                    apiService.createTilePage(local, defaultMain.toDto(), ns)
                    parsed + defaultMain
                } else {
                    parsed
                }
            }

            // Sort pages: use pageOrder from main if available, else main-first stable order
            val mainPage = pages.find { it.uid == "main" }
            val orderList = mainPage?.pageOrder ?: emptyList()
            val sortedPages = if (orderList.isNotEmpty()) {
                // Explicit order from main page's config
                val orderMap = orderList.withIndex().associate { (i, uid) -> uid to i }
                pages.sortedWith(compareBy { orderMap[it.uid] ?: Int.MAX_VALUE })
            } else {
                // Default: main first, rest in API order
                pages.sortedWith(compareBy { if (it.uid == "main") 0 else 1 })
            }

            val isReadOnly = false // local server always allows writes

            _uiState.value = TileDesignUiState.Success(
                editor = TileEditorState(
                    pages = sortedPages,
                    currentPageIndex = 0,
                    allItems = allItems
                ),
                isReadOnly = isReadOnly,
                iconBaseUrl = serverUrl,
                iconAuthHeader = local.resolveAuthHeader(),
                watchScreenWidthDp = watchScreenWidthDp
            )

            // Fetch initial item states and start SSE for live updates
            fetchItemStates(local, sortedPages)
            startSse(local)
            } finally {
                AppLog.d(TAG, "← loadTileConfig() ${System.currentTimeMillis() - _traceStart}ms")
            }
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

    /** Reorder pages by moving a page from [fromIndex] to [toIndex]. Main always stays first. */
    fun reorderPages(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val state = (_uiState.value as? TileDesignUiState.Success) ?: return
        val pages = state.editor.pages.toMutableList()

        // Never move the main page (index 0)
        if (fromIndex == 0 || toIndex == 0) return
        if (fromIndex !in pages.indices || toIndex !in pages.indices) return

        val moved = pages.removeAt(fromIndex)
        pages.add(toIndex, moved)

        // Persist the new order on the main page
        val pageOrder = pages.map { it.uid }
        val mainPage = pages.find { it.uid == "main" } ?: return
        val updatedMain = mainPage.copy(pageOrder = pageOrder)
        val finalPages = pages.map { if (it.uid == "main") updatedMain else it }

        _uiState.value = state.copy(
            editor = state.editor.copy(
                pages = finalPages,
                currentPageIndex = state.editor.currentPageIndex
            )
        )

        savePage(updatedMain)
    }

    fun onLayoutChanged(newLayout: Int) {
        val state = (_uiState.value as? TileDesignUiState.Success) ?: return
        val currentPage = state.editor.currentPage

        // Hidden-slot warning is shown as a persistent banner in the editor
        // (TilePageState.hasHiddenSlots), so no transient snackbar here.

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
        // Preview only — updates the tile designer preview without persisting.
        // The actual theme is saved from Watch Settings → Theme section.
        _selectedTheme.value = themeName
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

    /** Add a new page. The [label] is the user-visible display name; uid is auto-generated. */
    fun addPage(label: String) {
        val state = (_uiState.value as? TileDesignUiState.Success) ?: return
        val baseUid = label.trim().lowercase().replace(" ", "_").replace(Regex("[^a-z0-9_]"), "")
        if (baseUid.isBlank()) return

        // Ensure unique uid by appending a numeric suffix if needed
        var uid = baseUid
        var suffix = 2
        while (state.editor.pageNames.contains(uid)) {
            uid = "${baseUid}_$suffix"
            suffix++
        }

        val newPage = TilePageState(
            uid = uid,
            label = label.trim(),
            layout = 6
        )

        // Insert after the currently selected page
        val insertIndex = state.editor.currentPageIndex + 1
        val newPages = state.editor.pages.toMutableList().apply { add(insertIndex, newPage) }
        _uiState.value = state.copy(
            editor = state.editor.copy(pages = newPages, currentPageIndex = insertIndex)
        )

        // Create on server
        viewModelScope.launch {
            val config = localConfig ?: return@launch
            val namespace = credentialStore.tileNamespace
            apiService.createTilePage(config, newPage.toDto(), namespace)
                .onFailure { _snackbarMessage.value = "Failed to create page: ${it.message}" }
                .onSuccess { _snackbarMessage.value = "Page created" }
        }
        persistPageOrder()
    }

    /** Duplicate a page with all its slots. */
    fun duplicatePage(pageUid: String, newLabel: String) {
        val state = (_uiState.value as? TileDesignUiState.Success) ?: return
        val sourcePage = state.editor.pages.find { it.uid == pageUid } ?: return

        // Generate unique uid from label
        val baseUid = newLabel.trim().lowercase().replace(" ", "_").replace(Regex("[^a-z0-9_]"), "")
        if (baseUid.isBlank()) return

        var uid = baseUid
        var suffix = 2
        while (state.editor.pageNames.contains(uid)) {
            uid = "${baseUid}_$suffix"
            suffix++
        }

        val newPage = sourcePage.copy(
            uid = uid,
            label = newLabel.trim(),
            configVersion = 0
        )

        // Insert after the currently selected page
        val insertIndex = state.editor.currentPageIndex + 1
        val newPages = state.editor.pages.toMutableList().apply { add(insertIndex, newPage) }
        _uiState.value = state.copy(
            editor = state.editor.copy(pages = newPages, currentPageIndex = insertIndex)
        )

        // Create on server
        viewModelScope.launch {
            val config = localConfig ?: return@launch
            val namespace = credentialStore.tileNamespace
            apiService.createTilePage(config, newPage.toDto(), namespace)
                .onFailure { _snackbarMessage.value = "Failed to duplicate page: ${it.message}" }
                .onSuccess { _snackbarMessage.value = "Page duplicated" }
        }
        persistPageOrder()
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
        persistPageOrder()
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
            val itemsResult = apiService.getItemsWithMetadata(config)
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

    /** Update the main page's pageOrder to reflect current pages list and persist. */
    private fun persistPageOrder() {
        val state = (_uiState.value as? TileDesignUiState.Success) ?: return
        val pageOrder = state.editor.pages.map { it.uid }
        val mainPage = state.editor.pages.find { it.uid == "main" } ?: return
        val updatedMain = mainPage.copy(pageOrder = pageOrder)
        updatePageInState(updatedMain)
        savePage(updatedMain)
    }

    private fun savePage(page: TilePageState) {
        viewModelScope.launch {
            val _traceStart = System.currentTimeMillis()
            AppLog.d(TAG, "→ savePage()")
            try {
            val config = localConfig
            if (config == null || !config.isConfigured) {
                _snackbarMessage.value = "Local server not configured — cannot save"
                return@launch
            }

            val namespace = credentialStore.tileNamespace
            _isSaving.value = true

            // Always increment configVersion on the main page to signal sync needed.
            // The watch uses the main page's configVersion for change detection.
            val state = (_uiState.value as? TileDesignUiState.Success)
            val mainPage = state?.editor?.pages?.find { it.uid == "main" }
            if (mainPage != null) {
                val updatedMain = mainPage.copy(configVersion = mainPage.configVersion + 1)
                updatePageInState(updatedMain)
                if (page.uid != "main") {
                    // Save main page configVersion bump separately (items will be embedded on the actual page save)
                    apiService.updateTilePage(config, updatedMain.toDto(), namespace)
                }
            }

            // Save the actual page (with bumped configVersion if it IS main)
            val pageToSave = if (page.uid == "main" && mainPage != null) {
                page.copy(configVersion = mainPage.configVersion + 1)
            } else page

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
            } finally {
                AppLog.d(TAG, "← savePage() ${System.currentTimeMillis() - _traceStart}ms")
            }
        }
    }

    // ─── Live Item States ───

    /**
     * Fetch current states for all items referenced in the tile pages.
     */
    private fun fetchItemStates(localConfig: LocalServerConfig, pages: List<TilePageState>) {
        viewModelScope.launch {
            val _traceStart = System.currentTimeMillis()
            AppLog.d(TAG, "→ fetchItemStates()")
            try {
            val itemNames = collectReferencedItems(pages)
            if (itemNames.isEmpty()) return@launch

            val result = apiService.getAllItems(localConfig)
            result.onSuccess { items ->
                val stateMap = items
                    .filter { it.name in itemNames }
                    .associate { it.name to it.state }
                _itemStates.value = stateMap
                AppLog.d(TAG, "Fetched ${stateMap.size} item states")
            }
            } finally {
                AppLog.d(TAG, "← fetchItemStates() ${System.currentTimeMillis() - _traceStart}ms")
            }
        }
    }

    /**
     * Collect all item names referenced by tile slot configs (item, stateItem, actionItem).
     */
    private fun collectReferencedItems(pages: List<TilePageState>): Set<String> {
        val names = mutableSetOf<String>()
        for (page in pages) {
            for (slot in page.slots) {
                slot.item?.let { names.add(it) }
                slot.stateItem?.let { names.add(it) }
                slot.actionItem?.let { names.add(it) }
                slot.doubleTapItem?.let { names.add(it) }
            }
        }
        return names
    }

    /**
     * Start SSE connection for real-time item state updates.
     * Read-only — only receives statechanged events, never sends commands.
     */
    private fun startSse(localConfig: LocalServerConfig) {
        sseJob?.cancel()
        sseJob = viewModelScope.launch {
            AppLog.d(TAG, "→ startSse()")
            val baseUrl = localConfig.serverUrl.trimEnd('/')
            val url = "$baseUrl/rest/events?topics=openhab/items/*/statechanged"
            AppLog.d(TAG, "Starting SSE: $url")

            val sseClient = okHttpClient.newBuilder()
                .readTimeout(0, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Accept", "text/event-stream")
                .apply { localConfig.resolveAuthHeader()?.let { header("Authorization", it) } }
                .build()

            val factory = EventSources.createFactory(sseClient)

            while (isActive) {
                try {
                    val channel = kotlinx.coroutines.channels.Channel<SseMsg>(kotlinx.coroutines.channels.Channel.BUFFERED)

                    val eventSource = factory.newEventSource(request, object : EventSourceListener() {
                        override fun onOpen(eventSource: EventSource, response: Response) {
                            AppLog.d(TAG, "SSE connected: status=${response.code}, url=${response.request.url}")
                        }

                        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                            AppLog.d(TAG, "SSE onEvent: type=$type, id=$id, dataLen=${data.length}")
                            val result = channel.trySend(SseMsg.Data(data))
                            if (result.isFailure) {
                                AppLog.w(TAG, "SSE channel send failed: ${result.exceptionOrNull()?.message}")
                            }
                        }

                        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                            AppLog.w(TAG, "SSE failed: ${t?.message ?: "status ${response?.code}"} (${t?.javaClass?.simpleName})")
                            channel.trySend(SseMsg.Failed)
                        }

                        override fun onClosed(eventSource: EventSource) {
                            AppLog.d(TAG, "SSE closed by server")
                            channel.trySend(SseMsg.Failed)
                        }
                    })

                    try {
                        AppLog.d(TAG, "SSE entering event loop")
                        for (msg in channel) {
                            when (msg) {
                                is SseMsg.Data -> {
                                    AppLog.d(TAG, "SSE event received (${msg.data.length} chars)")
                                    handleSseEvent(msg.data)
                                }
                                is SseMsg.Failed -> break
                            }
                        }
                    } finally {
                        eventSource.cancel()
                        channel.close()
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.w(TAG, "SSE error: ${e.message}")
                }

                // Reconnect after delay
                if (isActive) {
                    delay(5000)
                }
            }
        }
    }

    private sealed interface SseMsg {
        data class Data(val data: String) : SseMsg
        data object Failed : SseMsg
    }

    /**
     * Extract the new state value from SSE event data.
     * Handles both formats:
     * - Escaped payload: "payload":"{\"value\":\"OFF\",...}" → searches for \"value\":\"
     * - Unescaped (direct): "value":"OFF" → searches for "value":"
     */
    private fun extractNewState(data: String): String? {
        // Try escaped format first (payload is a JSON string with escaped quotes)
        val escapedKey = "\\\"value\\\":\\\""
        var idx = data.indexOf(escapedKey)
        if (idx >= 0) {
            val start = idx + escapedKey.length
            val end = data.indexOf("\\\"", start)
            if (end > start) return data.substring(start, end)
        }

        // Fallback: unescaped format (same as watch TileStateEventSource)
        val plainKey = "\"value\":\""
        idx = data.indexOf(plainKey)
        if (idx >= 0) {
            val start = idx + plainKey.length
            val end = data.indexOf("\"", start)
            if (end > start) return data.substring(start, end)
        }

        return null
    }

    /**
     * Parse SSE statechanged event and update the item states map.
     */
    private fun handleSseEvent(data: String) {
        val _traceStart = System.currentTimeMillis()
        AppLog.d(TAG, "→ handleSseEvent()")
        try {
            // Skip ALIVE heartbeats
            if (data.contains("\"type\":\"ALIVE\"") || data.contains("\"ALIVE\"")) return

            AppLog.d(TAG, "SSE parsing: ${data.take(150)}")

            // Extract item name from topic: openhab/items/{name}/statechanged
            val topicStart = data.indexOf("\"topic\":\"") + 9
            if (topicStart < 9) return
            val topicEnd = data.indexOf("\"", topicStart)
            if (topicEnd < 0) return
            val topic = data.substring(topicStart, topicEnd)

            val parts = topic.split("/")
            if (parts.size < 4 || parts[3] != "statechanged") return
            val itemName = parts[2]

            // Extract new state value from payload — handle both escaped and unescaped formats.
            // Direct server: payload field contains escaped JSON → \"value\":\"OFF\"
            // Use same approach as watch TileStateEventSource.extractNewState
            val newState = extractNewState(data) ?: return

            // Update the states map
            val current = _itemStates.value
            if (current.containsKey(itemName) || isReferencedItem(itemName)) {
                _itemStates.value = current + (itemName to newState)
                AppLog.d(TAG, "SSE state: $itemName → $newState")
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "SSE parse error: ${e.message}")
        } finally {
            AppLog.d(TAG, "← handleSseEvent() ${System.currentTimeMillis() - _traceStart}ms")
        }
    }

    /**
     * Check if an item name is referenced in any tile page slot.
     */
    private fun isReferencedItem(itemName: String): Boolean {
        val state = (_uiState.value as? TileDesignUiState.Success) ?: return false
        return state.editor.pages.any { page ->
            page.slots.any { slot ->
                slot.item == itemName || slot.stateItem == itemName || slot.actionItem == itemName
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sseJob?.cancel()
    }

    companion object {
        private const val TAG = "TileDesignVM"
    }
}
