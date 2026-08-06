package org.openhab.habdroid.wear.data.repository

import android.content.Context
import org.openhab.habdroid.wear.util.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.data.model.TileItem
import org.openhab.habdroid.wear.data.model.ValueDisplay
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the tile configuration (TileItems) to disk as JSON.
 * Used to provide instant tile rendering on process restart without a network call.
 *
 * States in the persisted data may be stale — the hot path (refreshStates) updates them.
 * Config (labels, icons, actions, positions) stays valid until an explicit reload.
 */
@Singleton
class TileConfigDiskCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    companion object {
        private const val TAG = "TileConfigDiskCache"
        private const val FILE_NAME = "tile_config_cache.json"
        private const val VERSION_FILE_NAME = "tile_config_version.txt"
    }

    private val cacheFile: File get() = File(context.filesDir, FILE_NAME)
    private val versionFile: File get() = File(context.filesDir, VERSION_FILE_NAME)

    /**
     * Save tile items to disk with the associated configVersion.
     * Call after a successful cold load that fetched items from the server.
     */
    fun save(items: List<TileItem>, configVersion: Int) {
        try {
            val dtos = items.map { it.toDto() }
            val jsonStr = json.encodeToString(dtos)
            cacheFile.writeText(jsonStr)
            versionFile.writeText(configVersion.toString())
            AppLog.d(TAG, "Saved ${items.size} items to disk (${jsonStr.length} bytes, configVersion=$configVersion)")
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to save tile config to disk", e)
        }
    }

    /**
     * Save tile items to disk. Call after a successful cold load.
     */
    fun save(items: List<TileItem>) {
        try {
            val dtos = items.map { it.toDto() }
            val jsonStr = json.encodeToString(dtos)
            cacheFile.writeText(jsonStr)
            AppLog.d(TAG, "Saved ${items.size} items to disk (${jsonStr.length} bytes)")
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to save tile config to disk", e)
        }
    }

    /**
     * Load tile items from disk. Returns null if no cache exists or if parsing fails.
     * States may be stale — caller should trigger a state refresh after loading.
     */
    fun load(): List<TileItem>? {
        return try {
            if (!cacheFile.exists()) {
                AppLog.d(TAG, "No disk cache found")
                return null
            }
            val jsonStr = cacheFile.readText()
            val dtos = json.decodeFromString<List<CachedTileItem>>(jsonStr)
            val items = dtos.map { it.toTileItem() }
            AppLog.d(TAG, "Loaded ${items.size} items from disk cache")
            items
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to load tile config from disk", e)
            null
        }
    }

    /**
     * Get the configVersion that was stored with the last cache save.
     * Returns -1 if no version is stored (cache doesn't exist or version file missing).
     */
    fun getStoredConfigVersion(): Int {
        return try {
            if (!versionFile.exists()) return -1
            versionFile.readText().trim().toIntOrNull() ?: -1
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Clear the disk cache. Called when user explicitly reloads or config is invalidated.
     */
    fun clear() {
        try {
            if (cacheFile.exists()) {
                cacheFile.delete()
                AppLog.d(TAG, "Disk cache cleared")
            }
            if (versionFile.exists()) {
                versionFile.delete()
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to clear disk cache", e)
        }
    }
}

/**
 * Serializable DTO for persisting TileItem to disk.
 */
@Serializable
private data class CachedTileItem(
    val item: Item,
    val page: String,
    val slot: Int,
    val pageLayout: Int = 6,
    val icon: String? = null,
    val label: String? = null,
    val needsConfirmation: Boolean = false,
    val valueDisplay: String = "value",
    val action: String? = null,
    val valueItemName: String? = null,
    val valueItem: Item? = null,
    val invertValue: Boolean = false,
    val commandItemName: String? = null,
    val commandValue: String? = null,
    val aggregateState: Boolean = false,
    val doubleTapItem: String? = null,
    val doubleTapAction: String? = null,
    val doubleTapCommand: String? = null,
    val doubleTapConfirmation: Boolean = false,
    val doubleTapStateDisplay: String = "none"
)

private fun TileItem.toDto() = CachedTileItem(
    item = item,
    page = page,
    slot = slot,
    pageLayout = pageLayout,
    icon = icon,
    label = label,
    needsConfirmation = needsConfirmation,
    valueDisplay = valueDisplay.name.lowercase(),
    action = action,
    valueItemName = valueItemName,
    valueItem = valueItem,
    invertValue = invertValue,
    commandItemName = commandItemName,
    commandValue = commandValue,
    aggregateState = aggregateState,
    doubleTapItem = doubleTapItem,
    doubleTapAction = doubleTapAction,
    doubleTapCommand = doubleTapCommand,
    doubleTapConfirmation = doubleTapConfirmation,
    doubleTapStateDisplay = doubleTapStateDisplay.name.lowercase()
)

private fun CachedTileItem.toTileItem() = TileItem(
    item = item,
    page = page,
    slot = slot,
    pageLayout = pageLayout,
    icon = icon,
    label = label,
    needsConfirmation = needsConfirmation,
    valueDisplay = ValueDisplay.fromString(valueDisplay),
    action = action,
    valueItemName = valueItemName,
    valueItem = valueItem,
    invertValue = invertValue,
    commandItemName = commandItemName,
    commandValue = commandValue,
    aggregateState = aggregateState,
    doubleTapItem = doubleTapItem,
    doubleTapAction = doubleTapAction,
    doubleTapCommand = doubleTapCommand,
    doubleTapConfirmation = doubleTapConfirmation,
    doubleTapStateDisplay = ValueDisplay.fromString(doubleTapStateDisplay)
)
