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
 * Stores complication slot labels (cached from server config during sync)
 * and the legacy mapping between complication slot IDs and openHAB item names.
 */
@Singleton
class ComplicationPreferenceStore @Inject constructor(
    @Named("complications") private val dataStore: DataStore<Preferences>
) {
    // ─── Slot Labels (cached from server config for preview data) ───

    /**
     * Store the display label for a slot (cached during sync).
     * Used by getPreviewData() to show the item name in the complication picker.
     */
    suspend fun setSlotLabel(slotNumber: Int, label: String) {
        val key = stringPreferencesKey("slot_${slotNumber}_label")
        dataStore.edit { it[key] = label }
    }

    /**
     * Read the cached label for a slot synchronously (blocking).
     * Only used in getPreviewData() which must return immediately.
     */
    fun getSlotLabelSync(slotNumber: Int): String? {
        val key = stringPreferencesKey("slot_${slotNumber}_label")
        return kotlinx.coroutines.runBlocking {
            dataStore.data.first()[key]
        }
    }

    /**
     * Clear all slot labels (called before re-syncing).
     */
    suspend fun clearSlotLabels() {
        dataStore.edit { prefs ->
            prefs.asMap().keys
                .filter { it.name.startsWith("slot_") && it.name.endsWith("_label") }
                .forEach { prefs.remove(it) }
        }
    }

    // ─── Legacy: slot ID to item mapping (kept for old ComplicationService compatibility) ───
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
