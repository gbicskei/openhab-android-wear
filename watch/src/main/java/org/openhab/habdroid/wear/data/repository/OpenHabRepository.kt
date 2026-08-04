package org.openhab.habdroid.wear.data.repository

import org.openhab.habdroid.wear.util.AppLog
import kotlinx.coroutines.flow.first
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
    private val watchStatusWriter: org.openhab.habdroid.wear.sync.WatchStatusWriter,
    private val themeStore: ThemeStore
) {
    companion object {
        private const val TAG = "OpenHabRepo"

        /** Metadata namespace used to mark items for the watch tile */
        const val WEAR_TILE_METADATA = "wearTile"

        /** Fields needed for the cold load (full item metadata) */
        private const val COLD_LOAD_FIELDS =
            "name,label,type,state,category,tags,groupNames,stateDescription,commandDescription,groupType,function,members"

        /** Fields needed for the hot path (state refresh only) */
        private const val STATE_REFRESH_FIELDS = "name,state,type,members"
    }

    /** Latest config version from server (set during cold load) */
    var lastConfigVersion: Int = 0
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
        itemCache.clear()
        val items = coldLoad()
        itemCache.put(items)
        itemCache.statesLoaded = true

        // Write status to DataClient so phone can detect sync state
        val theme = themeStore.getTheme().name
        watchStatusWriter.writeStatus(lastConfigVersion.toString(), theme)

        items.size
    }

    /**
     * Hot path: fetch only item states from server in a single batch call.
     * Updates the cached items' states without replacing config/metadata.
     */
    suspend fun refreshStates(): Result<Unit> = runCatching {
        val cached = itemCache.get() ?: return@runCatching

        // Collect all item names we need states for (primary + stateItem + members of groups)
        val neededNames = cached.flatMap { tileItem ->
            listOfNotNull(tileItem.item.name, tileItem.valueItemName, tileItem.commandItemName)
        }.distinct().toSet()

        // Single batch call — fetch all items with state fields only
        AppLog.d(TAG, "refreshStates: batch fetching ${neededNames.size} items")
        val allItems = apiService.getItems(
            fields = STATE_REFRESH_FIELDS,
            recursive = true
        )

        // Build state map: item name → fresh Item (with state + members)
        val freshMap = allItems.associateBy { it.name }

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
    }

    /**
     * Cold load: fetches tile config + all referenced items in 2 API calls.
     * This is the full configuration load — items include metadata, descriptions, etc.
     */
    private suspend fun coldLoad(): List<TileItem> {
        // 1. Fetch tile config from user's namespace
        val namespace = credentialStore.credentials.first()?.tileNamespace
            ?: SyncConstants.DEFAULT_TILE_NAMESPACE
        AppLog.d(TAG, "coldLoad: fetching tile components (namespace=$namespace)")
        val components = apiService.getTileComponents(namespace)
        AppLog.d(TAG, "coldLoad: got ${components.size} components")
        val tilePages = components.filter { it.isTilePage }

        // Capture the config version from the main page
        val mainPage = tilePages.find { it.uid == "main" }
        lastConfigVersion = mainPage?.config?.configVersionInt ?: 0

        if (tilePages.isEmpty()) return emptyList()

        // Collect all item names referenced in slots
        val allItemNames = tilePages.flatMap { page ->
            page.slots.default.flatMap { slot ->
                listOfNotNull(slot.config.item, slot.config.stateItem, slot.config.actionItem)
            }
        }.distinct().toSet()

        // 2. Batch fetch all items with full metadata (single API call)
        AppLog.d(TAG, "coldLoad: batch fetching items (need ${allItemNames.size} names)")
        val allItems = apiService.getItems(
            fields = COLD_LOAD_FIELDS,
            recursive = true
        )
        AppLog.d(TAG, "coldLoad: got ${allItems.size} items from server")

        // Filter to only items we need
        val itemMap = allItems.filter { it.name in allItemNames }.associateBy { it.name }
        AppLog.d(TAG, "coldLoad: matched ${itemMap.size}/${allItemNames.size} referenced items")

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
                        a == "toggle" -> null // null = auto-toggle in TileItem
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
                    aggregateState = config.aggregateState
                )
            }
        }.sorted()

        return tileItems
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
        apiService.getItem(itemName)
    }

    /**
     * Send a command to an item.
     */
    suspend fun sendCommand(itemName: String, command: String): Result<Unit> = runCatching {
        val body = command.toRequestBody("text/plain".toMediaType())
        apiService.sendCommand(itemName, body)
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
     */
    suspend fun sendVoiceCommand(text: String): Result<Unit> = runCatching {
        val body = text.toRequestBody("text/plain".toMediaType())
        apiService.interpretVoiceCommand(
            command = body,
            language = Locale.getDefault().language
        )
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
