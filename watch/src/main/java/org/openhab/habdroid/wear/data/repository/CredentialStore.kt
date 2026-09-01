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
        val KEY_LOCAL_USERNAME = stringPreferencesKey("local_username")
        val KEY_LOCAL_PASSWORD = stringPreferencesKey("local_password")
        val KEY_LOCAL_API_TOKEN = stringPreferencesKey("local_api_token")
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_PASSWORD = stringPreferencesKey("password")
        val KEY_USER_KEY = stringPreferencesKey("user_key")
        val KEY_DEVICE_NAME = stringPreferencesKey("device_name")
        val KEY_BINDING_INSTALLED = booleanPreferencesKey("binding_installed")
        val KEY_DEBUG_MODE = booleanPreferencesKey("debug_mode")
        val KEY_LAST_REGISTERED_FCM_TOKEN = stringPreferencesKey("last_registered_fcm_token")
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

    /** Flow of local server credentials (username, password, apiToken). All empty = no auth. */
    val localCredentials: Flow<LocalCredentials> = dataStore.data.map { prefs ->
        LocalCredentials(
            username = prefs[KEY_LOCAL_USERNAME] ?: "",
            password = prefs[KEY_LOCAL_PASSWORD] ?: "",
            apiToken = prefs[KEY_LOCAL_API_TOKEN] ?: ""
        )
    }

    /** Whether credentials have been configured */
    val isConfigured: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL]?.isNotBlank() == true
    }

    /** Flow of debug mode state */
    val debugMode: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DEBUG_MODE] ?: false
    }

    /** Flow of device name (stable identifier for audio sink binding) */
    val deviceName: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_DEVICE_NAME] ?: ""
    }

    /** Flow of binding installed status (synced from phone) */
    val bindingInstalled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_BINDING_INSTALLED] ?: false
    }

    /**
     * FCM token that was last successfully registered with the MobileAudio binding.
     * Used to skip redundant re-registration when the token hasn't changed.
     * Empty until the first successful registration.
     */
    val lastRegisteredFcmToken: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_REGISTERED_FCM_TOKEN] ?: ""
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

    /** Save local (direct/LAN) server URL and credentials for Happy Eyeballs racing */
    suspend fun saveLocalServerUrl(url: String, username: String = "", password: String = "", apiToken: String = "") {
        dataStore.edit { prefs ->
            prefs[KEY_LOCAL_SERVER_URL] = url
            prefs[KEY_LOCAL_USERNAME] = username
            prefs[KEY_LOCAL_PASSWORD] = password
            prefs[KEY_LOCAL_API_TOKEN] = apiToken
        }
    }

    /** Save device name (stable identifier for audio sink binding) */
    suspend fun saveDeviceName(name: String) {
        dataStore.edit { prefs ->
            prefs[KEY_DEVICE_NAME] = name
        }
    }

    /** Save binding installed status (synced from phone) */
    suspend fun saveBindingInstalled(installed: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_BINDING_INSTALLED] = installed
        }
    }

    /** Record the FCM token that was last successfully registered with the binding */
    suspend fun saveLastRegisteredFcmToken(token: String) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_REGISTERED_FCM_TOKEN] = token
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

/**
 * Local server credentials synced from the phone companion.
 * All empty = no auth required (common for LAN-only openHAB servers).
 */
data class LocalCredentials(
    val username: String = "",
    val password: String = "",
    val apiToken: String = ""
) {
    val hasApiToken: Boolean get() = apiToken.isNotBlank()
    val hasBasicAuth: Boolean get() = username.isNotBlank() && password.isNotBlank()
    val hasAnyAuth: Boolean get() = hasApiToken || hasBasicAuth
}
