package org.openhab.habdroid.wear.data.repository

import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.openhab.habdroid.wear.data.api.OpenHabApiService
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.data.model.TileItem
import org.openhab.habdroid.wear.data.model.ValueDisplay
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that coordinates between the API service and local storage.
 * Single source of truth for openHAB item data on the watch.
 */
@Singleton
class OpenHabRepository @Inject constructor(
    private val apiService: OpenHabApiService,
    private val credentialStore: CredentialStore,
    private val tilePreferenceStore: TilePreferenceStore,
    private val itemCache: ItemCache
) {
    companion object {
        /** Metadata namespace used to mark items for the watch tile */
        const val WEAR_TILE_METADATA = "wearTile"
    }

    /**
     * Fetch all items tagged with wearTile metadata on the server.
     * Uses in-memory cache — only fetches from network if cache is empty.
     * Call clearAndReload() to force a fresh fetch.
     */
    suspend fun getAvailableTileItems(): Result<List<TileItem>> = runCatching {
        // Return from cache if available
        itemCache.get()?.let { return@runCatching it }

        // Cache miss — fetch from server
        val items = fetchTileItemsFromServer()
        itemCache.put(items)
        items
    }

    /**
     * Clears the item cache and fetches fresh from server.
     * Returns the count of items loaded, or throws on failure.
     */
    suspend fun clearAndReload(): Result<Int> = runCatching {
        itemCache.clear()
        val items = fetchTileItemsFromServer()
        itemCache.put(items)
        itemCache.statesLoaded = true
        items.size
    }

    /**
     * Fetch fresh states from server (bypasses cache).
     * Updates the cached items' states without replacing config.
     */
    suspend fun refreshStates(): Result<Unit> = runCatching {
        val freshItems = fetchTileItemsFromServer()
        itemCache.updateStates(freshItems)
    }

    private suspend fun fetchTileItemsFromServer(): List<TileItem> {
        val items = apiService.getItems(
            metadata = WEAR_TILE_METADATA,
            language = Locale.getDefault().language
        )

        val tileItems = items
            .filter { it.metadata?.containsKey(WEAR_TILE_METADATA) == true }
            .filter { it.isForTile }
            .filter { it.isSupportedForTile || it.metadata?.get(WEAR_TILE_METADATA)?.config?.let { cfg ->
                cfg["action"]?.startsWith("page:") == true || cfg["action"] == "command"
            } == true }
            .map { item ->
                val config = item.metadata
                    ?.get(WEAR_TILE_METADATA)
                    ?.config
                val rawPosition = config?.get("position") ?: "1"
                val (page, slot) = TileItem.parsePosition(rawPosition)
                val icon = config?.get("icon")
                val label = config?.get("label")
                val needsConfirmation = config?.get("needsConfirmation")?.toBooleanStrictOrNull() ?: false
                val valueDisplay = ValueDisplay.fromString(config?.get("valueDisplay"))
                val action = config?.get("action")
                val valueItemName = config?.get("valueItem")
                val invertValue = config?.get("invertValue")?.toBooleanStrictOrNull() ?: false
                val commandItemName = config?.get("commandItem")
                val commandValue = config?.get("commandValue")
                val aggregateState = config?.get("aggregateState")?.toBooleanStrictOrNull() ?: false
                TileItem(
                    item = item,
                    page = page,
                    slot = slot,
                    icon = icon,
                    label = label,
                    needsConfirmation = needsConfirmation,
                    valueDisplay = valueDisplay,
                    action = action,
                    valueItemName = valueItemName,
                    invertValue = invertValue,
                    commandItemName = commandItemName,
                    commandValue = commandValue,
                    aggregateState = aggregateState
                )
            }
            .sorted()

        // Fetch valueItems — these are separate items not included in the metadata query
        return resolveValueItems(tileItems)
    }

    /**
     * For each TileItem that has a valueItemName, fetch that item from the server
     * and attach it as the valueItem. Items are fetched individually by name.
     */
    private suspend fun resolveValueItems(tileItems: List<TileItem>): List<TileItem> {
        val valueItemNames = tileItems.mapNotNull { it.valueItemName }.distinct()
        if (valueItemNames.isEmpty()) return tileItems

        // Fetch each valueItem individually
        val valueItemMap = mutableMapOf<String, Item>()
        for (name in valueItemNames) {
            try {
                val item = apiService.getItem(name)
                valueItemMap[name] = item
            } catch (_: Exception) {
                // If a valueItem can't be fetched, the TileItem will fall back to primary item state
            }
        }

        // Attach resolved valueItems to their TileItems
        return tileItems.map { tileItem ->
            val resolvedValueItem = tileItem.valueItemName?.let { valueItemMap[it] }
            if (resolvedValueItem != null) {
                tileItem.copy(valueItem = resolvedValueItem)
            } else {
                tileItem
            }
        }
    }

    /**
     * Get tile items for a specific page, sorted by slot.
     */
    suspend fun getTileItemsForPage(page: String): Result<List<TileItem>> = runCatching {
        val all = getAvailableTileItems().getOrThrow()
        all.filter { it.page == page }.sortedBy { it.slot }.take(6)
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
            // No local selection — show all main page items, capped at 6
            mainPageItems.sortedBy { it.slot }.take(6)
        } else {
            // Return items in the order the user selected them
            selectedNames.mapNotNull { name ->
                available.find { it.item.name == name }
            }.take(6)
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
     * Fetch items eligible for watch face complications.
     * Uses the same wearTile metadata query as the tile, but filters to items
     * where the metadata value is "complication" or config contains complication="true".
     * Returns items sorted by label for the picker UI.
     */
    suspend fun getComplicationItems(): Result<List<Item>> = runCatching {
        val items = apiService.getItems(
            metadata = WEAR_TILE_METADATA,
            language = Locale.getDefault().language
        )
        items
            .filter { it.isForComplication }
            .sortedBy { it.displayLabel.lowercase() }
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
