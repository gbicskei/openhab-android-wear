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
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _credentials = MutableStateFlow<ServerCredentials?>(null)

    /** Flow of current credentials. Emits null if not configured. */
    val credentials: Flow<ServerCredentials?> = _credentials.asStateFlow()

    init {
        // Load initial state from encrypted storage
        _credentials.value = readCredentials()
    }

    private fun readCredentials(): ServerCredentials? {
        val url = encryptedPrefs.getString(KEY_SERVER_URL, null) ?: return null
        return ServerCredentials(
            serverUrl = url,
            username = encryptedPrefs.getString(KEY_USERNAME, "") ?: "",
            password = encryptedPrefs.getString(KEY_PASSWORD, "") ?: ""
        )
    }

    /** Save server credentials securely. */
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

    /** Clear all stored credentials. */
    suspend fun clear() {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit().clear().apply()
        }
        _credentials.value = null
    }
}
