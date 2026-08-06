package org.openhab.habdroid.wear.data.repository

import org.openhab.habdroid.wear.util.AppLog
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.openhab.habdroid.wear.data.api.OpenHabApiService
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.data.model.TileItem
import org.openhab.habdroid.wear.data.model.ValueDisplay
import org.openhab.habdroid.wear.data.model.WearComplicationConfig
import org.openhab.habdroid.wear.shared.sync.SyncConstants
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that coordinates between the API service and local storage.
 * Single source of truth for openHAB item data on the watch.
 *
 * Fetch strategy:
 * - Cold load: fetches tile config (wear:tile components) + all items in batch (2 API calls).
 *   Cached in memory and persisted to disk. Only re-fetched on explicit reload signal.
 * - Hot path (refreshStates): single batch API call returning only name, state, type, members.
 *   Updates states in the cached TileItems without re-fetching config/metadata.
 */
@Singleton
class OpenHabRepository @Inject constructor(
    private val apiService: OpenHabApiService,
    private val credentialStore: CredentialStore,
    private val tilePreferenceStore: TilePreferenceStore,
    private val itemCache: ItemCache,
    private val diskCache: TileConfigDiskCache,
    private val watchStatusWriter: org.openhab.habdroid.wear.sync.WatchStatusWriter,
    private val themeStore: ThemeStore
) {
    companion object {
        private const val TAG = "OpenHabRepo"

        /** Metadata namespace used to mark items for the watch tile */
        const val WEAR_TILE_METADATA = "wearTile"

        /** Max concurrent API requests (cloud relay throttle protection) */
        private const val MAX_CONCURRENT_REQUESTS = 3
    }

    /** Latest config version from server (set during cold load) */
    var lastConfigVersion: Int = 0
        private set

    /** Page uid → display label map (populated during cold load) */
    var pageLabels: Map<String, String> = emptyMap()
        private set

    /**
     * Get all tile items. Returns from cache if available, otherwise performs cold load.
     */
    suspend fun getAvailableTileItems(): Result<List<TileItem>> = runCatching {
        // Return from cache if available
        itemCache.get()?.let { return@runCatching it }

        // Cache miss — perform cold load
        val items = coldLoad()
        itemCache.put(items)

        // Write status to DataClient after initial cold load
        val theme = themeStore.getTheme().name
        watchStatusWriter.writeStatus(lastConfigVersion.toString(), theme)

        items
    }

    /**
     * Clears the item cache and performs a full cold load from server.
     * Returns the count of items loaded, or throws on failure.
     */
    suspend fun clearAndReload(): Result<Int> = runCatching {
        val _traceStart = System.currentTimeMillis()
        AppLog.d(TAG, "→ clearAndReload()")
        try {
            itemCache.clear()
            val items = coldLoad()
            itemCache.put(items)
            itemCache.statesLoaded = true

            // Write status to DataClient so phone can detect sync state
            val theme = themeStore.getTheme().name
            watchStatusWriter.writeStatus(lastConfigVersion.toString(), theme)

            items.size
        } finally {
            AppLog.d(TAG, "← clearAndReload() ${System.currentTimeMillis() - _traceStart}ms")
        }
    }

    /**
     * Hot path: fetch only item states from server in a single batch call.
     * Updates the cached items' states without replacing config/metadata.
     */
    suspend fun refreshStates(): Result<Unit> = runCatching {
        val _traceStart = System.currentTimeMillis()
        AppLog.d(TAG, "→ refreshStates()")
        try {
        val cached = itemCache.get() ?: return@runCatching

        // Collect all item names we need states for (primary + stateItem + doubleTapItem + members of groups)
        val neededNames = cached.flatMap { tileItem ->
            listOfNotNull(tileItem.item.name, tileItem.valueItemName, tileItem.commandItemName, tileItem.doubleTapItem)
        }.filter { it != "unknown" }.distinct().toSet()

        // Parallel fetch individual items (much faster than fetching all 748 items)
        AppLog.d(TAG, "refreshStates: parallel fetching ${neededNames.size} items")
        val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
        val freshMap: Map<String, Item> = coroutineScope {
            neededNames.map { name ->
                async {
                    semaphore.withPermit {
                        try {
                            name to apiService.getItem(name)
                        } catch (e: Exception) {
                            AppLog.w(TAG, "refreshStates: failed to fetch '$name': ${e.message}")
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
        AppLog.d(TAG, "refreshStates: got ${freshMap.size}/${neededNames.size} items")

        // Update cached tile items with fresh states
        val updated = cached.map { tileItem ->
            var result = tileItem

            // Update primary item state + members
            freshMap[tileItem.item.name]?.let { fresh ->
                result = result.copy(
                    item = tileItem.item.copy(
                        state = fresh.state,
                        members = fresh.members
                    )
                )
            }

            // Update valueItem state
            tileItem.valueItemName?.let { valueName ->
                freshMap[valueName]?.let { fresh ->
                    result = result.copy(
                        valueItem = tileItem.valueItem?.copy(
                            state = fresh.state,
                            members = fresh.members
                        )
                    )
                }
            }

            result
        }

        itemCache.putStates(updated)

        // Store doubleTapItem states in the extra map for composite rendering
        val doubleTapStates = cached
            .mapNotNull { it.doubleTapItem }
            .distinct()
            .mapNotNull { name -> freshMap[name]?.let { name to it.state } }
            .toMap()
        if (doubleTapStates.isNotEmpty()) {
            itemCache.putExtraItemStates(doubleTapStates)
        }
        } finally {
            AppLog.d(TAG, "← refreshStates() ${System.currentTimeMillis() - _traceStart}ms")
        }
    }

    /**
     * Cold load: fetches tile config + all referenced items in 2 API calls.
     * This is the full configuration load — items include metadata, descriptions, etc.
     */
    private suspend fun coldLoad(): List<TileItem> {
        val _traceStart = System.currentTimeMillis()
        AppLog.d(TAG, "→ coldLoad()")
        try {
        // 1. Fetch tile config from user's namespace
        val namespace = credentialStore.credentials.first()?.tileNamespace
            ?: SyncConstants.DEFAULT_TILE_NAMESPACE
        AppLog.d(TAG, "coldLoad: fetching tile components (namespace=$namespace)")
        val components = try {
            apiService.getTileComponents(namespace)
        } catch (e: kotlinx.serialization.SerializationException) {
            // Server may return empty body when namespace has no components
            AppLog.w(TAG, "coldLoad: no components in namespace (empty response), treating as empty")
            emptyList()
        }
        AppLog.d(TAG, "coldLoad: got ${components.size} components")
        val tilePages = components.filter { it.isTilePage }

        // Capture the config version from the main page
        val mainPage = tilePages.find { it.uid == "main" }
        lastConfigVersion = mainPage?.config?.configVersionInt ?: 0

        // Capture page labels for tile title rendering
        pageLabels = tilePages.associate { it.uid to it.config.label }

        if (tilePages.isEmpty()) return emptyList()

        // Check if disk cache is still valid (configVersion hasn't changed since last fetch).
        // If valid, use cached items — skip network fetch entirely.
        val storedVersion = diskCache.getStoredConfigVersion()
        if (storedVersion == lastConfigVersion && storedVersion >= 0) {
            val cachedItems = diskCache.load()
            if (cachedItems != null) {
                AppLog.d(TAG, "coldLoad: disk cache valid (configVersion=$storedVersion), using ${cachedItems.size} cached items")
                return cachedItems
            }
        }

        // Collect all item names referenced in slots (including doubleTap items)
        val allItemNames = tilePages.flatMap { page ->
            page.slots.default.flatMap { slot ->
                listOfNotNull(slot.config.item, slot.config.stateItem, slot.config.actionItem, slot.config.doubleTapItem)
            }
        }.distinct().toSet()

        // Fetch only the referenced items in parallel (much faster than fetching all 748 items)
        AppLog.d(TAG, "coldLoad: parallel fetching ${allItemNames.size} items (configVersion changed: $storedVersion → $lastConfigVersion)")
        val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
        val itemMap: Map<String, Item> = coroutineScope {
            allItemNames.map { name ->
                async {
                    semaphore.withPermit {
                        try {
                            name to apiService.getItem(name)
                        } catch (e: Exception) {
                            AppLog.w(TAG, "coldLoad: failed to fetch '$name': ${e.message}")
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
        AppLog.d(TAG, "coldLoad: fetched ${itemMap.size}/${allItemNames.size} referenced items")

        // Build TileItems from page slots
        val tileItems = tilePages.flatMap { page ->
            page.slots.default.map { slot ->
                val config = slot.config
                val primaryItem = config.item?.let { itemMap[it] }
                val valueItemName = config.stateItem
                val valueItem = valueItemName?.let { itemMap[it] }
                val commandItemName = config.actionItem

                val valueDisplay = ValueDisplay.fromString(config.stateDisplay)
                val action = config.action?.let { a ->
                    when {
                        a == "toggle" -> "toggle" // explicit toggle (forces toggle even for range items)
                        a == "command" -> "command"
                        a.startsWith("page:") -> a
                        else -> null
                    }
                }

                TileItem(
                    item = primaryItem ?: Item(
                        name = config.item ?: "unknown",
                        label = config.label,
                        category = config.icon
                    ),
                    page = page.uid,
                    slot = config.positionInt,
                    pageLayout = page.config.layoutInt,
                    icon = config.icon,
                    label = config.label,
                    needsConfirmation = config.actionConfirmation,
                    valueDisplay = valueDisplay,
                    action = action,
                    valueItemName = valueItemName,
                    valueItem = valueItem,
                    invertValue = config.invertState,
                    commandItemName = commandItemName,
                    commandValue = config.actionCommand,
                    aggregateState = config.aggregateState,
                    doubleTapItem = config.doubleTapItem,
                    doubleTapAction = config.doubleTapAction,
                    doubleTapCommand = config.doubleTapCommand,
                    doubleTapConfirmation = config.doubleTapConfirmation,
                    doubleTapStateDisplay = org.openhab.habdroid.wear.data.model.ValueDisplay.fromString(config.doubleTapStateDisplay)
                )
            }
        }.sorted()

        // Store doubleTap item states and full objects in extra cache
        val doubleTapItemNames = tilePages.flatMap { page ->
            page.slots.default.mapNotNull { it.config.doubleTapItem }
        }.distinct()
        val doubleTapStates = doubleTapItemNames
            .mapNotNull { name -> itemMap[name]?.let { name to it.state } }
            .toMap()
        if (doubleTapStates.isNotEmpty()) {
            itemCache.putExtraItemStates(doubleTapStates)
        }
        val doubleTapItems = doubleTapItemNames
            .mapNotNull { name -> itemMap[name]?.let { name to it } }
            .toMap()
        if (doubleTapItems.isNotEmpty()) {
            itemCache.putExtraItems(doubleTapItems)
        }

        // Persist to disk with configVersion — next cold load skips item fetch if version matches
        diskCache.save(tileItems, lastConfigVersion)

        return tileItems
        } finally {
            AppLog.d(TAG, "← coldLoad() ${System.currentTimeMillis() - _traceStart}ms")
        }
    }

    /**
     * Get tile items for a specific page, sorted by slot.
     */
    suspend fun getTileItemsForPage(page: String): Result<List<TileItem>> = runCatching {
        val all = getAvailableTileItems().getOrThrow()
        all.filter { it.page == page }.sortedBy { it.slot }.take(7)
    }

    /**
     * Get all page names that have at least one item.
     */
    suspend fun getTilePages(): Result<List<String>> = runCatching {
        val all = getAvailableTileItems().getOrThrow()
        all.map { it.page }.distinct()
    }

    /**
     * Get the items to display on the tile based on local user selection.
     * If the user has made a local selection, returns only those items (in their chosen order).
     * If no local selection exists, falls back to all server wearTile items on the main page.
     */
    suspend fun getSelectedTileItems(): Result<List<TileItem>> = runCatching {
        val available = getAvailableTileItems().getOrThrow()
        val mainPageItems = available.filter { it.page == TileItem.PAGE_MAIN }
        val selectedNames = tilePreferenceStore.selectedItemNames.first()

        if (selectedNames.isEmpty()) {
            // No local selection — show all main page items, capped at 7
            mainPageItems.sortedBy { it.slot }.take(7)
        } else {
            // Return items in the order the user selected them
            selectedNames.mapNotNull { name ->
                available.find { it.item.name == name }
            }.take(7)
        }
    }

    /**
     * Legacy method — kept for backward compatibility.
     * Delegates to getSelectedTileItems().
     */
    suspend fun getTileItems(): Result<List<TileItem>> = getSelectedTileItems()

    /**
     * Fetch all available items (for item picker / configuration).
     */
    suspend fun getAllItems(): Result<List<Item>> = runCatching {
        apiService.getItems(
            fields = "name,label,type,state,category,tags,groupNames",
            language = Locale.getDefault().language
        )
    }

    /**
     * Fetch items eligible for watch face complications from wear:complication-list document.
     * Falls back to the old wearTile metadata approach if no document exists.
     */
    suspend fun getComplicationItems(): Result<List<Item>> = runCatching {
        // Try new approach: read from wear:complication-list document
        val namespace = credentialStore.credentials.first()?.tileNamespace
            ?: SyncConstants.DEFAULT_TILE_NAMESPACE
        val components = apiService.getTileComponents(namespace)
        val complicationList = components.find { it.isComplicationList }

        if (complicationList != null) {
            // Get item names from the complication slots
            val itemNames = complicationList.slots.default.mapNotNull { slot ->
                slot.config.item
            }.distinct()

            // Fetch those items
            if (itemNames.isEmpty()) return@runCatching emptyList()
            val allItems = apiService.getItems(
                fields = "name,label,type,state,category,tags,groupNames,stateDescription",
                language = Locale.getDefault().language
            )
            allItems.filter { it.name in itemNames.toSet() }
                .sortedBy { it.displayLabel.lowercase() }
        } else {
            // Fallback: old metadata approach
            val items = apiService.getItems(
                metadata = WEAR_TILE_METADATA,
                language = Locale.getDefault().language
            )
            items
                .filter { it.isForComplication }
                .sortedBy { it.displayLabel.lowercase() }
        }
    }

    /**
     * Get parsed complication configurations from the wear:complication-list document.
     * Returns empty list if the document doesn't exist.
     */
    suspend fun getComplicationConfigs(): Result<List<WearComplicationConfig>> = runCatching {
        val namespace = credentialStore.credentials.first()?.tileNamespace
            ?: SyncConstants.DEFAULT_TILE_NAMESPACE
        val rawDoc = try {
            apiService.getComplicationListRaw(namespace)
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 404) return@runCatching emptyList()
            throw e
        }

        val slotsObj = rawDoc["slots"]?.let {
            kotlinx.serialization.json.Json.parseToJsonElement(it.toString()).jsonObject
        }
        val defaultSlots = slotsObj?.get("default")?.let {
            kotlinx.serialization.json.Json.parseToJsonElement(it.toString()).jsonArray
        } ?: return@runCatching emptyList()

        defaultSlots.mapNotNull { element ->
            val slotObj = element.jsonObject
            val configObj = slotObj["config"]?.jsonObject ?: return@mapNotNull null
            WearComplicationConfig.fromJson(configObj)
        }
    }

    /**
     * Get a single item's current state.
     */
    suspend fun getItem(itemName: String): Result<Item> = runCatching {
        val _traceStart = System.currentTimeMillis()
        AppLog.d(TAG, "→ getItem()")
        try {
            apiService.getItem(itemName)
        } finally {
            AppLog.d(TAG, "← getItem() ${System.currentTimeMillis() - _traceStart}ms")
        }
    }

    /**
     * Get the cached state for an item without making an API call.
     * Returns the state string or null if not in cache.
     */
    fun getCachedItemState(itemName: String): String? {
        val items = itemCache.get() ?: return null
        val item = items.find { it.displayItemName == itemName || it.commandTargetName == itemName }
        return item?.displayItem?.state
    }

    /**
     * Send a command to an item.
     */
    suspend fun sendCommand(itemName: String, command: String): Result<Unit> = runCatching {
        val _traceStart = System.currentTimeMillis()
        AppLog.d(TAG, "→ sendCommand()")
        try {
            val body = command.toRequestBody("text/plain".toMediaType())
            apiService.sendCommand(itemName, body)
        } finally {
            AppLog.d(TAG, "← sendCommand() ${System.currentTimeMillis() - _traceStart}ms")
        }
    }

    /**
     * Toggle a switch item (ON→OFF, OFF→ON).
     */
    suspend fun toggleItem(item: Item): Result<Unit> {
        val newCommand = if (item.isActive) "OFF" else "ON"
        return sendCommand(item.name, newCommand)
    }

    /**
     * Execute the tap action for a tile item.
     * Handles command routing (commandItem) and fixed commands (commandValue).
     */
    suspend fun executeTileAction(tileItem: TileItem): Result<Unit> {
        val targetName = tileItem.commandTargetName
        val command = when {
            tileItem.isCommand && tileItem.commandValue != null -> tileItem.commandValue
            tileItem.displayItem.isActive -> "OFF"
            else -> "ON"
        }
        return sendCommand(targetName, command)
    }

    /**
     * Send a voice command to the openHAB interpreter.
     * Returns the interpreter's response text on success, or throws with a meaningful error message.
     */
    suspend fun sendVoiceCommand(text: String): Result<String> = runCatching {
        val body = text.toRequestBody("text/plain".toMediaType())
        val response = apiService.interpretVoiceCommand(
            command = body,
            language = Locale.getDefault().language
        )

        if (response.isSuccessful) {
            response.body()?.string()?.trim() ?: ""
        } else {
            val errorBody = response.errorBody()?.string()
            val errorMessage = parseErrorMessage(response.code(), errorBody)
            throw VoiceCommandException(errorMessage)
        }
    }

    /**
     * Parse the error message from the voice interpreter response.
     * openHAB returns JSON: {"error":{"message":"...","http-code":400}}
     */
    private fun parseErrorMessage(httpCode: Int, errorBody: String?): String {
        if (errorBody.isNullOrBlank()) {
            return when (httpCode) {
                401 -> "Authentication failed"
                404 -> "No voice interpreter configured"
                else -> "Command failed (HTTP $httpCode)"
            }
        }

        return try {
            val json = kotlinx.serialization.json.Json.parseToJsonElement(errorBody)
            val errorObj = json.jsonObject["error"]?.jsonObject
            errorObj?.get("message")?.let {
                it.toString().trim('"')
            } ?: "Command failed (HTTP $httpCode)"
        } catch (_: Exception) {
            // Not JSON — might be plain text error
            errorBody.take(200)
        }
    }

    /**
     * Get the icon URL for an item.
     */
    suspend fun getIconUrl(item: Item): String? {
        val credentials = credentialStore.credentials.first() ?: return null
        return OpenHabApiService.iconUrl(
            baseUrl = credentials.serverUrl,
            iconName = item.iconName,
            state = item.state
        )
    }
}
