package org.openhab.habdroid.wear.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists voice command preferences (e.g., TTS spoken responses).
 */
@Singleton
class VoicePreferenceStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private companion object {
        val KEY_VOICE_RESPONSE_SPOKEN = booleanPreferencesKey("voice_response_spoken")
        val KEY_VOICE_COMMANDS_ENABLED = booleanPreferencesKey("voice_commands_enabled")
        val KEY_SERVER_TTS_ENABLED = booleanPreferencesKey("server_tts_enabled")
        val KEY_SERVER_TTS_API_KEY = stringPreferencesKey("server_tts_api_key")
        val KEY_SERVER_TTS_VOICE = stringPreferencesKey("server_tts_voice")
        val KEY_TTS_VOLUME = floatPreferencesKey("tts_volume")
        val KEY_TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val KEY_TTS_PITCH = floatPreferencesKey("tts_pitch")
    }

    /**
     * Whether the voice interpreter's response should be spoken aloud via TTS.
     * Default: false.
     */
    val voiceResponseSpoken: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_VOICE_RESPONSE_SPOKEN] ?: false
    }

    /** Enable or disable spoken voice responses. */
    suspend fun setVoiceResponseSpoken(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_VOICE_RESPONSE_SPOKEN] = enabled
        }
    }

    /**
     * Whether to use server-side TTS (Google Cloud) instead of local watch TTS.
     * Default: false (use local TTS).
     */
    val serverTtsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SERVER_TTS_ENABLED] ?: false
    }

    /** Enable or disable server-side TTS. */
    suspend fun setServerTtsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SERVER_TTS_ENABLED] = enabled
        }
    }

    /** Google Cloud TTS API key. */
    val serverTtsApiKey: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_SERVER_TTS_API_KEY] ?: ""
    }

    /** Set the Google Cloud TTS API key. */
    suspend fun setServerTtsApiKey(key: String) {
        dataStore.edit { prefs ->
            prefs[KEY_SERVER_TTS_API_KEY] = key
        }
    }

    /** Google Cloud TTS voice name (e.g. "en-US-Wavenet-D"). */
    val serverTtsVoice: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_SERVER_TTS_VOICE] ?: "en-US-Wavenet-D"
    }

    /** Set the TTS voice name. */
    suspend fun setServerTtsVoice(voice: String) {
        dataStore.edit { prefs ->
            prefs[KEY_SERVER_TTS_VOICE] = voice
        }
    }

    /** Whether voice commands are enabled (requires OH ≥5.2.1). Default: true. */
    val voiceCommandsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_VOICE_COMMANDS_ENABLED] ?: true
    }

    suspend fun setVoiceCommandsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_VOICE_COMMANDS_ENABLED] = enabled
        }
    }

    /** System TTS speech rate (0.25–3.0). Default: 1.0. */
    val ttsSpeechRate: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_TTS_SPEECH_RATE] ?: 1.0f
    }

    suspend fun setTtsSpeechRate(rate: Float) {
        dataStore.edit { prefs ->
            prefs[KEY_TTS_SPEECH_RATE] = rate.coerceIn(0.25f, 3.0f)
        }
    }

    /** System TTS pitch (0.25–2.0). Default: 1.0. */
    val ttsPitch: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_TTS_PITCH] ?: 1.0f
    }

    suspend fun setTtsPitch(pitch: Float) {
        dataStore.edit { prefs ->
            prefs[KEY_TTS_PITCH] = pitch.coerceIn(0.25f, 2.0f)
        }
    }
}
