package org.openhab.habdroid.wear.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the user's local tile item selection.
 *
 * The server provides a pool of items tagged with wearTile metadata.
 * This store holds which of those items the user has chosen to display
 * on their tile, and in what order. Items are stored as an ordered,
 * comma-separated list of item names.
 */
@Singleton
class TilePreferenceStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private companion object {
        val KEY_SELECTED_ITEMS = stringPreferencesKey("tile_selected_items")
        val KEY_CURRENT_PAGE = stringPreferencesKey("tile_current_page")
    }

    /**
     * Flow of currently selected item names, in display order.
     * Empty list means no local selection has been made yet (use server defaults).
     */
    val selectedItemNames: Flow<List<String>> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_SELECTED_ITEMS] ?: return@map emptyList()
        if (raw.isBlank()) emptyList()
        else raw.split(",").filter { it.isNotBlank() }
    }

    /**
     * Flow of the current tile page being displayed.
     * Defaults to "main" if not set.
     */
    val currentPage: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_CURRENT_PAGE] ?: "main"
    }

    /**
     * Set the current tile page. Called by PageNavigationActivity after auth.
     */
    suspend fun setCurrentPage(page: String) {
        dataStore.edit { prefs ->
            prefs[KEY_CURRENT_PAGE] = page
        }
    }

    /**
     * Whether the user has made a local tile selection.
     * If false, the tile should fall back to showing all server-side wearTile items.
     */
    val hasLocalSelection: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs.contains(KEY_SELECTED_ITEMS)
    }

    /**
     * Save the user's tile item selection (ordered list of item names).
     */
    suspend fun saveSelection(itemNames: List<String>) {
        dataStore.edit { prefs ->
            prefs[KEY_SELECTED_ITEMS] = itemNames.joinToString(",")
        }
    }

    /**
     * Add an item to the tile selection at the end.
     * No-op if already selected or if 6 items are already chosen.
     */
    suspend fun addItem(itemName: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_SELECTED_ITEMS]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            if (current.size >= 6 || current.contains(itemName)) return@edit
            prefs[KEY_SELECTED_ITEMS] = (current + itemName).joinToString(",")
        }
    }

    /**
     * Remove an item from the tile selection.
     */
    suspend fun removeItem(itemName: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_SELECTED_ITEMS]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            prefs[KEY_SELECTED_ITEMS] = current.filter { it != itemName }.joinToString(",")
        }
    }

    /**
     * Clear the local selection. The tile will revert to showing
     * all server-side wearTile items.
     */
    suspend fun clearSelection() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_SELECTED_ITEMS)
        }
    }
}
