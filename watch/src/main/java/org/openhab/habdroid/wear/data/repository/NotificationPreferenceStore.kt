package org.openhab.habdroid.wear.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Singleton

/**
 * Persists notification preferences (FCM push notification behavior).
 *
 * Controls whether incoming cloud notifications are:
 * - Shown visually (system notification)
 * - Read aloud via TTS
 * - Preceded by an alert chime
 */
@Singleton
class NotificationPreferenceStore(
    private val dataStore: DataStore<Preferences>
) {
    private companion object {
        val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val KEY_READ_ALOUD_ENABLED = booleanPreferencesKey("notification_read_aloud_enabled")
        val KEY_CHIME_ENABLED = booleanPreferencesKey("notification_chime_enabled")
        val KEY_CHIME_SOUND = stringPreferencesKey("notification_chime_sound")
        val KEY_NOTIFICATION_VOLUME = floatPreferencesKey("notification_volume")
        val KEY_MIN_READ_ALOUD_PRIORITY = stringPreferencesKey("notification_min_read_aloud_priority")
    }

    /**
     * Master switch for receiving and processing push notifications.
     * Default: true.
     */
    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_NOTIFICATIONS_ENABLED] ?: true
    }

    /** Enable or disable push notification processing. */
    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    /**
     * Whether to speak the notification message aloud via TTS.
     * Default: true.
     */
    val readAloudEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_READ_ALOUD_ENABLED] ?: true
    }

    /** Enable or disable TTS read-aloud for notifications. */
    suspend fun setReadAloudEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_READ_ALOUD_ENABLED] = enabled
        }
    }

    /**
     * Whether to play an alert chime before reading the notification aloud.
     * Only effective when [readAloudEnabled] is true.
     * Default: true.
     */
    val chimeEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_CHIME_ENABLED] ?: true
    }

    /** Enable or disable the pre-TTS chime sound. */
    suspend fun setChimeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_CHIME_ENABLED] = enabled
        }
    }

    /**
     * The chime sound to play before TTS.
     * Values: "default" (system notification), "alarm" (system alarm), "none" (silent).
     * Default: "default".
     */
    val chimeSound: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_CHIME_SOUND] ?: "default"
    }

    /** Set the chime sound name. */
    suspend fun setChimeSound(sound: String) {
        dataStore.edit { prefs ->
            prefs[KEY_CHIME_SOUND] = sound
        }
    }

    /**
     * Minimum priority level for read-aloud.
     * Values: "low", "normal", "high". Default: "normal".
     * Messages below this threshold are shown visually but not spoken.
     */
    val minReadAloudPriority: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_MIN_READ_ALOUD_PRIORITY] ?: "normal"
    }

    /** Set the minimum priority for read-aloud. */
    suspend fun setMinReadAloudPriority(priority: String) {
        dataStore.edit { prefs ->
            prefs[KEY_MIN_READ_ALOUD_PRIORITY] = priority
        }
    }
}
