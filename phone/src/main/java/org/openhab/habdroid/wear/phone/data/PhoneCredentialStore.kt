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
        const val KEY_GOOGLE_TTS_API_KEY = "google_tts_api_key"
        const val KEY_DEBUG_MODE = "debug_mode"
        const val KEY_VOICE_COMMANDS_ENABLED = "voice_commands_enabled"
        const val KEY_READ_ALOUD_ENABLED = "read_aloud_enabled"
        const val KEY_USE_SERVER_TTS = "use_server_tts"
        const val KEY_SERVER_TTS_VOICE = "server_tts_voice"
        const val KEY_TTS_VOLUME = "tts_volume"
        const val KEY_TTS_SPEECH_RATE = "tts_speech_rate"
        const val KEY_TTS_PITCH = "tts_pitch"
        const val KEY_SETTINGS_NEED_SYNC = "settings_need_sync"
        const val KEY_BACKUP_ENABLED = "backup_enabled"
        const val KEY_WATCH_USE_LOCAL_SERVER = "watch_use_local_server"
        const val KEY_DEVICE_NAME = "device_name"
        const val KEY_NOTIFICATION_READ_ALOUD = "notification_read_aloud"
        const val KEY_NOTIFICATION_CHIME = "notification_chime"
        const val KEY_NOTIFICATION_VOLUME = "notification_volume"
        const val KEY_BINDING_INSTALLED = "binding_installed"
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
        // Sync debug mode to AppLog on startup (read directly from prefs, not via _debugModeFlow)
        org.openhab.habdroid.wear.phone.util.AppLog.debugMode = try {
            encryptedPrefs.getBoolean(KEY_DEBUG_MODE, false)
        } catch (_: Exception) { false }
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

    /** Get the watch device name (stable identifier for audio sink). */
    val deviceName: String get() = try {
        encryptedPrefs.getString(KEY_DEVICE_NAME, "") ?: ""
    } catch (_: Exception) { "" }

    /** Save the watch device name. */
    suspend fun saveDeviceName(name: String) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit()
                .putString(KEY_DEVICE_NAME, name)
                .apply()
        }
    }

    /** Whether the Mobile Audio binding is installed on the server. Default: false. */
    val isBindingInstalled: Boolean get() = try {
        encryptedPrefs.getBoolean(KEY_BINDING_INSTALLED, false)
    } catch (_: Exception) { false }

    /** Save binding installed status. */
    suspend fun saveBindingInstalled(installed: Boolean) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit()
                .putBoolean(KEY_BINDING_INSTALLED, installed)
                .apply()
        }
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

    /** Observable theme state for Compose reactivity. */
    val selectedThemeState = kotlinx.coroutines.flow.MutableStateFlow(
        try { encryptedPrefs.getString(KEY_SELECTED_THEME, DEFAULT_THEME) ?: DEFAULT_THEME }
        catch (_: Exception) { DEFAULT_THEME }
    )

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
        selectedThemeState.value = themeName
    }

    /** Get the stored Google Cloud TTS API key. Empty string if not set. */
    fun getGoogleTtsApiKey(): String {
        return try {
            encryptedPrefs.getString(KEY_GOOGLE_TTS_API_KEY, "") ?: ""
        } catch (_: Exception) { "" }
    }

    /** Whether a Google TTS API key has been stored. */
    val hasGoogleTtsApiKey: Boolean get() = getGoogleTtsApiKey().isNotBlank()

    /** Save the Google Cloud TTS API key securely. */
    suspend fun saveGoogleTtsApiKey(key: String) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit()
                .putString(KEY_GOOGLE_TTS_API_KEY, key)
                .apply()
        }
    }

    /** Whether debug mode is enabled. */
    val isDebugMode: Boolean get() = _debugModeFlow.value

    /** Observable debug mode state. */
    private val _debugModeFlow = MutableStateFlow(
        try { encryptedPrefs.getBoolean(KEY_DEBUG_MODE, false) } catch (_: Exception) { false }
    )
    val debugModeFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _debugModeFlow.asStateFlow()

    /** Set debug mode. */
    suspend fun setDebugMode(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit()
                .putBoolean(KEY_DEBUG_MODE, enabled)
                .apply()
        }
        _debugModeFlow.value = enabled
        org.openhab.habdroid.wear.phone.util.AppLog.debugMode = enabled
    }

    // ─── Backup toggle ───

    /** Whether server backup is enabled. */
    val isBackupEnabled: Boolean get() = try {
        encryptedPrefs.getBoolean(KEY_BACKUP_ENABLED, false)
    } catch (_: Exception) { false }

    /** Set backup enabled state. */
    suspend fun setBackupEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit()
                .putBoolean(KEY_BACKUP_ENABLED, enabled)
                .apply()
        }
    }

    // ─── Watch Local Server Toggle ───

    /** Whether the watch is allowed to use the config (local) server connection. Default: false. */
    val isWatchUseLocalServer: Boolean get() = try {
        encryptedPrefs.getBoolean(KEY_WATCH_USE_LOCAL_SERVER, false)
    } catch (_: Exception) { false }

    /** Set whether the watch should use the config (local) server. */
    suspend fun setWatchUseLocalServer(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit()
                .putBoolean(KEY_WATCH_USE_LOCAL_SERVER, enabled)
                .apply()
        }
    }

    // ─── Voice Settings ───

    /** Whether voice commands are enabled. Default: true. */
    val isVoiceCommandsEnabled: Boolean get() = try {
        encryptedPrefs.getBoolean(KEY_VOICE_COMMANDS_ENABLED, true)
    } catch (_: Exception) { true }

    /** Whether read-aloud is enabled. Default: false. */
    val isReadAloudEnabled: Boolean get() = try {
        encryptedPrefs.getBoolean(KEY_READ_ALOUD_ENABLED, false)
    } catch (_: Exception) { false }

    /** Whether server TTS is enabled. Default: false. */
    val isUseServerTts: Boolean get() = try {
        encryptedPrefs.getBoolean(KEY_USE_SERVER_TTS, false)
    } catch (_: Exception) { false }

    /** Selected server TTS voice name. */
    val serverTtsVoice: String get() = try {
        encryptedPrefs.getString(KEY_SERVER_TTS_VOICE, "") ?: ""
    } catch (_: Exception) { "" }

    /** TTS speech rate. Default: 1.0. */
    val ttsSpeechRate: Float get() = try {
        encryptedPrefs.getFloat(KEY_TTS_SPEECH_RATE, 1.0f)
    } catch (_: Exception) { 1.0f }

    /** TTS pitch. Default: 1.0. */
    val ttsPitch: Float get() = try {
        encryptedPrefs.getFloat(KEY_TTS_PITCH, 1.0f)
    } catch (_: Exception) { 1.0f }

    /** Save all voice settings at once. */
    suspend fun saveVoiceSettings(
        voiceCommandsEnabled: Boolean,
        readAloudEnabled: Boolean,
        useServerTts: Boolean,
        serverTtsVoice: String,
        speechRate: Float,
        pitch: Float
    ) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit()
                .putBoolean(KEY_VOICE_COMMANDS_ENABLED, voiceCommandsEnabled)
                .putBoolean(KEY_READ_ALOUD_ENABLED, readAloudEnabled)
                .putBoolean(KEY_USE_SERVER_TTS, useServerTts)
                .putString(KEY_SERVER_TTS_VOICE, serverTtsVoice)
                .putFloat(KEY_TTS_SPEECH_RATE, speechRate)
                .putFloat(KEY_TTS_PITCH, pitch)
                .putBoolean(KEY_SETTINGS_NEED_SYNC, true)
                .apply()
        }
    }

    // ─── Settings sync flag ───

    // ─── Notification Settings ───

    /** Whether notification read-aloud is enabled. Default: false. */
    val isNotificationReadAloud: Boolean get() = try {
        encryptedPrefs.getBoolean(KEY_NOTIFICATION_READ_ALOUD, false)
    } catch (_: Exception) { false }

    /** Whether notification chime is enabled. Default: true. */
    val isNotificationChime: Boolean get() = try {
        encryptedPrefs.getBoolean(KEY_NOTIFICATION_CHIME, true)
    } catch (_: Exception) { true }

    /** Save notification settings. */
    suspend fun saveNotificationSettings(readAloud: Boolean, chime: Boolean) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit()
                .putBoolean(KEY_NOTIFICATION_READ_ALOUD, readAloud)
                .putBoolean(KEY_NOTIFICATION_CHIME, chime)
                .putBoolean(KEY_SETTINGS_NEED_SYNC, true)
                .apply()
        }
    }

    // ─── Settings sync flag (original) ───

    /** Whether saved settings need to be synced to the watch. */
    val settingsNeedSync: Boolean get() = try {
        encryptedPrefs.getBoolean(KEY_SETTINGS_NEED_SYNC, false)
    } catch (_: Exception) { false }

    /** Mark settings as synced (called after successful sync to watch). */
    suspend fun clearSettingsNeedSync() {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit()
                .putBoolean(KEY_SETTINGS_NEED_SYNC, false)
                .apply()
        }
    }

    /** Mark settings as needing sync. */
    suspend fun markSettingsNeedSync() {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit()
                .putBoolean(KEY_SETTINGS_NEED_SYNC, true)
                .apply()
        }
    }
}
