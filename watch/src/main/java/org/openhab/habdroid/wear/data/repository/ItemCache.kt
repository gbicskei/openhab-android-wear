package org.openhab.habdroid.wear.data.repository

import org.openhab.habdroid.wear.data.model.TileItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cache for tile items. Stores configuration (metadata, positions)
 * and last-known states. States are updated via SSE events.
 *
 * Config is loaded once and persists until "Reload Items" clears it.
 * States are refreshed on each tile enter (swipe to tile).
 */
@Singleton
class ItemCache @Inject constructor() {

    @Volatile
    private var cachedItems: List<TileItem>? = null

    /** Whether fresh states have been loaded since last tile enter. */
    @Volatile
    var statesLoaded: Boolean = false

    /** Returns cached items, or null if cache is empty (needs fetch). */
    fun get(): List<TileItem>? = cachedItems

    /** Store items in cache. */
    fun put(items: List<TileItem>) {
        cachedItems = items
    }

    /** Clear cache — next access will return null, forcing a re-fetch. */
    fun clear() {
        cachedItems = null
        statesLoaded = false
    }

    /** Whether cache has config data. */
    val isLoaded: Boolean get() = cachedItems != null

    /**
     * Update a single item's state in the cache (from SSE or state fetch).
     * Checks both primary item names and valueItem names for matches.
     */
    fun updateItemState(itemName: String, newState: String) {
        val items = cachedItems ?: return
        cachedItems = items.map { tileItem ->
            when (itemName) {
                tileItem.valueItemName ->
                    tileItem.copy(valueItem = tileItem.valueItem?.copy(state = newState))
                tileItem.item.name ->
                    tileItem.copy(item = tileItem.item.copy(state = newState))
                else -> tileItem
            }
        }
    }

    /**
     * Bulk update states from fresh server data.
     * Matches by item name (both primary and valueItem), updates state only (preserves cached config).
     */
    fun updateStates(freshItems: List<TileItem>) {
        val items = cachedItems ?: return
        // Build state map from both primary items and valueItems
        val stateMap = mutableMapOf<String, String>()
        for (fresh in freshItems) {
            stateMap[fresh.item.name] = fresh.item.state
            if (fresh.valueItem != null) {
                stateMap[fresh.valueItem.name] = fresh.valueItem.state
            }
        }
        cachedItems = items.map { tileItem ->
            var updated = tileItem
            // Update primary item state
            stateMap[tileItem.item.name]?.let { newState ->
                updated = updated.copy(item = updated.item.copy(state = newState))
            }
            // Update valueItem state
            tileItem.valueItemName?.let { valueName ->
                stateMap[valueName]?.let { newState ->
                    updated = updated.copy(valueItem = updated.valueItem?.copy(state = newState))
                }
            }
            updated
        }
        statesLoaded = true
    }

    /** Mark states as stale (tile was left and re-entered). */
    fun invalidateStates() {
        statesLoaded = false
    }
}
