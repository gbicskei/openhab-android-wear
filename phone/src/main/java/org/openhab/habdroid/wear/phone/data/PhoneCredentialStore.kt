package org.openhab.habdroid.wear.phone.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.openhab.habdroid.wear.shared.model.ServerCredentials
import org.openhab.habdroid.wear.shared.sync.SyncConstants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Securely persists server credentials on the phone using EncryptedSharedPreferences.
 *
 * Security properties:
 * - AES-256-GCM encryption for values
 * - AES-256-SIV (deterministic AEAD) for keys
 * - Master key stored in Android Keystore (hardware-backed where available)
 * - Data at rest is encrypted; only this app with this signing key can decrypt
 * - Wiped on app uninstall (app-private storage)
 */
@Singleton
class PhoneCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val PREFS_FILE = "openhab_credentials_encrypted"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_LOCAL_SERVER_URL = "local_server_url"
        const val KEY_LOCAL_USERNAME = "local_username"
        const val KEY_LOCAL_PASSWORD = "local_password"
        const val KEY_LOCAL_API_TOKEN = "local_api_token"
        const val KEY_HOME_WIFI_SSID = "home_wifi_ssid"
        const val KEY_USER_KEY = "user_key"
        const val KEY_SELECTED_THEME = "selected_theme"
        const val DEFAULT_THEME = "AMBER"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Keystore key mismatch after reinstall — clear and recreate
            context.getSharedPreferences(PREFS_FILE, android.content.Context.MODE_PRIVATE)
                .edit().clear().apply()
            // Delete the file to force recreation
            val prefsFile = java.io.File(context.filesDir.parent, "shared_prefs/$PREFS_FILE.xml")
            prefsFile.delete()
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    private val _credentials = MutableStateFlow<ServerCredentials?>(null)
    private val _localCredentials = MutableStateFlow<LocalServerConfig?>(null)
    private val _userKey = MutableStateFlow("")

    /** Flow of remote (watch) credentials. Emits null if not configured. */
    val credentials: Flow<ServerCredentials?> = _credentials.asStateFlow()

    /** Flow of local server config. Emits null if not configured. */
    val localConfig: Flow<LocalServerConfig?> = _localCredentials.asStateFlow()

    /** Flow of the user key (namespace identifier). Empty string = default/shared namespace. */
    val userKey: Flow<String> = _userKey.asStateFlow()

    /** Current user key value (non-suspend accessor). */
    val currentUserKey: String get() = _userKey.value

    /**
     * The tile namespace derived from the current user key.
     * Empty key = "wear:tile", otherwise "wear:tile:{key}".
     */
    val tileNamespace: String
        get() = SyncConstants.tileNamespace(_userKey.value)

    init {
        // Load initial state from encrypted storage
        _credentials.value = try { readCredentials() } catch (_: Exception) { null }
        _localCredentials.value = try { readLocalConfig() } catch (_: Exception) { null }
        _userKey.value = try {
            encryptedPrefs.getString(KEY_USER_KEY, "") ?: ""
        } catch (_: Exception) { "" }
    }

    private fun readCredentials(): ServerCredentials? {
        val url = encryptedPrefs.getString(KEY_SERVER_URL, null) ?: return null
        return ServerCredentials(
            serverUrl = url,
            username = encryptedPrefs.getString(KEY_USERNAME, "") ?: "",
            password = encryptedPrefs.getString(KEY_PASSWORD, "") ?: ""
        )
    }

    private fun readLocalConfig(): LocalServerConfig? {
        val url = encryptedPrefs.getString(KEY_LOCAL_SERVER_URL, null) ?: return null
        return LocalServerConfig(
            serverUrl = url,
            username = encryptedPrefs.getString(KEY_LOCAL_USERNAME, "") ?: "",
            password = encryptedPrefs.getString(KEY_LOCAL_PASSWORD, "") ?: "",
            apiToken = encryptedPrefs.getString(KEY_LOCAL_API_TOKEN, "") ?: "",
            homeWifiSsid = encryptedPrefs.getString(KEY_HOME_WIFI_SSID, "") ?: ""
        )
    }

    /** Save remote server credentials securely. */
    suspend fun saveCredentials(credentials: ServerCredentials) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit()
                .putString(KEY_SERVER_URL, credentials.serverUrl)
                .putString(KEY_USERNAME, credentials.username)
                .putString(KEY_PASSWORD, credentials.password)
                .apply()
        }
        _credentials.value = credentials
    }

    /** Save local server configuration securely. */
    suspend fun saveLocalConfig(config: LocalServerConfig) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit()
                .putString(KEY_LOCAL_SERVER_URL, config.serverUrl)
                .putString(KEY_LOCAL_USERNAME, config.username)
                .putString(KEY_LOCAL_PASSWORD, config.password)
                .putString(KEY_LOCAL_API_TOKEN, config.apiToken)
                .putString(KEY_HOME_WIFI_SSID, config.homeWifiSsid)
                .apply()
        }
        _localCredentials.value = config
    }

    /** Save the user key (namespace identifier) for multi-user config. */
    suspend fun saveUserKey(key: String) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit()
                .putString(KEY_USER_KEY, key)
                .apply()
        }
        _userKey.value = key
    }

    /** Clear all stored credentials. */
    suspend fun clear() {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit().clear().apply()
        }
        _credentials.value = null
        _localCredentials.value = null
        _userKey.value = ""
    }

    /** Get the currently selected tile theme name. */
    fun getSelectedTheme(): String {
        return encryptedPrefs.getString(KEY_SELECTED_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
    }

    /** Save the selected tile theme name. */
    suspend fun saveSelectedTheme(themeName: String) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit()
                .putString(KEY_SELECTED_THEME, themeName)
                .apply()
        }
    }
}
