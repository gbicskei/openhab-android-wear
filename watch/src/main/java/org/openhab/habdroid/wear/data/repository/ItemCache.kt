package org.openhab.habdroid.wear.data.repository

import org.openhab.habdroid.wear.util.AppLog
import org.openhab.habdroid.wear.data.model.TileItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory + disk cache for tile items.
 *
 * Stores configuration (item metadata, positions, actions) and last-known states.
 * On process restart, loads from disk (warm start) so the tile renders immediately.
 * States are refreshed via the hot path (single batch API call) on tile enter.
 *
 * Lifecycle:
 * - Cold load (network): config + items fetched → stored in memory + disk
 * - Warm load (disk): process restart → loaded from disk → states stale, refreshed on tile enter
 * - Hot path (network): state-only batch fetch → updates memory cache
 */
@Singleton
class ItemCache @Inject constructor(
    private val diskCache: TileConfigDiskCache
) {
    companion object {
        private const val TAG = "ItemCache"
    }

    @Volatile
    private var cachedItems: List<TileItem>? = null

    /** Whether fresh states have been loaded since last tile enter. */
    @Volatile
    var statesLoaded: Boolean = false

    /**
     * Returns cached items from memory. If memory is empty, attempts to load from disk.
     * Returns null only if both memory and disk are empty (needs network cold load).
     */
    fun get(): List<TileItem>? {
        cachedItems?.let { return it }

        // Memory miss — try disk
        val fromDisk = diskCache.load()
        if (fromDisk != null) {
            AppLog.d(TAG, "Warm start: loaded ${fromDisk.size} items from disk")
            cachedItems = fromDisk
            // States are stale from disk — don't mark statesLoaded
        }
        return cachedItems
    }

    /** Store items in cache after a cold load. Persists to disk. */
    fun put(items: List<TileItem>) {
        cachedItems = items
        diskCache.save(items)
    }

    /**
     * Replace cached items with state-updated versions (hot path result).
     * Does NOT persist to disk — states are ephemeral, only config is persisted.
     */
    fun putStates(items: List<TileItem>) {
        cachedItems = items
        statesLoaded = true
    }

    /** Clear cache (memory + disk). Next access will require a network cold load. */
    fun clear() {
        cachedItems = null
        statesLoaded = false
        diskCache.clear()
    }

    /** Whether cache has config data (from memory or disk). */
    val isLoaded: Boolean get() = get() != null

    /**
     * Update a single item's state in the cache (from SSE event).
     * Checks primary item names, valueItem names, and Group members for matches.
     */
    fun updateItemState(itemName: String, newState: String) {
        val items = cachedItems ?: return
        cachedItems = items.map { tileItem ->
            when (itemName) {
                tileItem.valueItemName ->
                    tileItem.copy(valueItem = tileItem.valueItem?.copy(state = newState))
                tileItem.item.name ->
                    tileItem.copy(item = tileItem.item.copy(state = newState))
                else -> {
                    // Check if itemName is a member of a Group item — update member state
                    if (tileItem.item.isGroup && tileItem.item.members?.any { it.name == itemName } == true) {
                        val updatedMembers = tileItem.item.members.map { member ->
                            if (member.name == itemName) member.copy(state = newState) else member
                        }
                        tileItem.copy(item = tileItem.item.copy(members = updatedMembers))
                    } else {
                        tileItem
                    }
                }
            }
        }
    }

    /** Mark states as stale (tile was left and re-entered). */
    fun invalidateStates() {
        statesLoaded = false
    }
}
