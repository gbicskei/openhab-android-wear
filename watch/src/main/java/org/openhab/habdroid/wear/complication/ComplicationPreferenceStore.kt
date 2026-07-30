package org.openhab.habdroid.wear.complication

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Stores the mapping between complication slot IDs and openHAB item names.
 * Each complication instance on the watch face has a unique ID assigned by the system.
 * We persist which item the user selected for each slot.
 */
@Singleton
class ComplicationPreferenceStore @Inject constructor(
    @Named("complications") private val dataStore: DataStore<Preferences>
) {
    /**
     * Get the item name configured for a given complication slot.
     * Returns null if no item has been configured for this slot.
     */
    suspend fun getItemForSlot(complicationId: Int): String? {
        val key = stringPreferencesKey("complication_${complicationId}_item")
        return dataStore.data.first()[key]
    }

    /**
     * Store the item name for a given complication slot.
     * Called when the user picks an item in the config activity.
     */
    suspend fun setItemForSlot(complicationId: Int, itemName: String) {
        val key = stringPreferencesKey("complication_${complicationId}_item")
        dataStore.edit { it[key] = itemName }
    }

    /**
     * Remove the stored preference for a complication slot.
     * Called when the complication is deactivated (user removes it from watch face).
     */
    suspend fun removeSlot(complicationId: Int) {
        val key = stringPreferencesKey("complication_${complicationId}_item")
        dataStore.edit { it.remove(key) }
    }

    /**
     * Returns all currently configured complication item names.
     * Useful for bulk refresh (WorkManager triggers update for all active complications).
     */
    suspend fun getAllConfiguredItems(): List<String> {
        return dataStore.data.first().asMap()
            .filter { it.key.name.endsWith("_item") }
            .values
            .filterIsInstance<String>()
    }

    /**
     * Returns true if any complication slot is currently configured.
     * Used to decide whether to schedule/cancel the periodic WorkManager job.
     */
    suspend fun hasActiveComplications(): Boolean {
        return getAllConfiguredItems().isNotEmpty()
    }
}
