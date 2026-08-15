package org.openhab.habdroid.wear.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.openhab.habdroid.wear.shared.model.ServerCredentials
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists server connection credentials using DataStore.
 */
@Singleton
class CredentialStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private companion object {
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_LOCAL_SERVER_URL = stringPreferencesKey("local_server_url")
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_PASSWORD = stringPreferencesKey("password")
        val KEY_USER_KEY = stringPreferencesKey("user_key")
        val KEY_DEBUG_MODE = booleanPreferencesKey("debug_mode")
    }

    /** Flow of current credentials, null if not configured */
    val credentials: Flow<ServerCredentials?> = dataStore.data.map { prefs ->
        val url = prefs[KEY_SERVER_URL] ?: return@map null
        ServerCredentials(
            serverUrl = url,
            username = prefs[KEY_USERNAME] ?: "",
            password = prefs[KEY_PASSWORD] ?: "",
            userKey = prefs[KEY_USER_KEY] ?: ""
        )
    }

    /** Flow of local (direct/LAN) server URL for Happy Eyeballs racing. Empty = cloud-only. */
    val localServerUrl: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_LOCAL_SERVER_URL] ?: ""
    }

    /** Whether credentials have been configured */
    val isConfigured: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL]?.isNotBlank() == true
    }

    /** Flow of debug mode state */
    val debugMode: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DEBUG_MODE] ?: false
    }

    /** Save server credentials */
    suspend fun saveCredentials(credentials: ServerCredentials) {
        dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = credentials.serverUrl
            prefs[KEY_USERNAME] = credentials.username
            prefs[KEY_PASSWORD] = credentials.password
            prefs[KEY_USER_KEY] = credentials.userKey
        }
    }

    /** Save local (direct/LAN) server URL for Happy Eyeballs racing */
    suspend fun saveLocalServerUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[KEY_LOCAL_SERVER_URL] = url
        }
    }

    /** Save debug mode persistently */
    suspend fun setDebugMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_DEBUG_MODE] = enabled
        }
    }

    /** Read debug mode synchronously (for init) */
    suspend fun getDebugMode(): Boolean = dataStore.data.first()[KEY_DEBUG_MODE] ?: false

    /** Clear all stored credentials */
    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
