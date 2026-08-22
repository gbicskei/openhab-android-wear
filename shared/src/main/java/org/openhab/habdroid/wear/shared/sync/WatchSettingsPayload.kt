package org.openhab.habdroid.wear.shared.sync

import kotlinx.serialization.Serializable

/**
 * Unified DataItem payload for all watch settings + status, shared bidirectionally.
 *
 * Stored as a DataItem at path `/openhab/watch-settings`. Both phone and watch can read
 * it offline (persisted locally by Google Play Services). Last writer wins.
 *
 * Two field categories:
 * - **Settings** (phone writes, watch applies): voice, notifications, theme, debug
 * - **Status** (watch writes, phone reads): configTimestamp, screenWidthDp, appVersion, hasSpeaker
 *
 * The phone NEVER writes status fields. The watch NEVER writes settings fields from this
 * DataItem — it only applies them when the phone changes them (detected via onDataChanged).
 *
 * This structure also serves as the server backup format (settings fields only, no status).
 */
@Serializable
data class WatchSettingsPayload(
    // ─── Settings (phone → watch) ───

    // Voice / TTS
    val voiceCommandsEnabled: Boolean = true,
    val readAloudEnabled: Boolean = false,
    val useServerTts: Boolean = false,
    val serverTtsVoice: String = "",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,

    // Notifications
    val notificationsEnabled: Boolean = true,
    val notificationReadAloudEnabled: Boolean = false,
    val chimeEnabled: Boolean = true,
    val chimeSound: String = "default",
    val minReadAloudPriority: String = "normal",

    // Appearance
    val theme: String = "",

    // Debug
    val debugMode: Boolean = false,

    // ─── Status (watch → phone, read-only for phone) ───

    val configTimestamp: String = "",
    val screenWidthDp: Int = 0,
    val appVersion: String = "",
    val hasSpeaker: Boolean = true
) {
    /**
     * Convert settings fields to a flat string map for server backup (item metadata).
     * Status fields are excluded — they are device-specific and transient.
     */
    fun toMetadataConfig(): Map<String, String> = mapOf(
        "voiceCommandsEnabled" to voiceCommandsEnabled.toString(),
        "readAloudEnabled" to readAloudEnabled.toString(),
        "useServerTts" to useServerTts.toString(),
        "serverTtsVoice" to serverTtsVoice,
        "speechRate" to speechRate.toString(),
        "pitch" to pitch.toString(),
        "notificationsEnabled" to notificationsEnabled.toString(),
        "notificationReadAloudEnabled" to notificationReadAloudEnabled.toString(),
        "chimeEnabled" to chimeEnabled.toString(),
        "chimeSound" to chimeSound,
        "minReadAloudPriority" to minReadAloudPriority,
        "theme" to theme,
        "debugMode" to debugMode.toString()
    )

    companion object {
        /** DataItem path for the unified settings + status DataItem. */
        const val DATA_PATH = "/openhab/watch-settings"

        /** Schema version for server backup format */
        const val SCHEMA_VERSION = "2"

        /** Metadata namespace used for server backup */
        const val METADATA_NAMESPACE = "wearConfig"

        // DataMap keys (flat keys for DataItem storage)
        const val KEY_VOICE_COMMANDS_ENABLED = "voiceCommandsEnabled"
        const val KEY_READ_ALOUD_ENABLED = "readAloudEnabled"
        const val KEY_USE_SERVER_TTS = "useServerTts"
        const val KEY_SERVER_TTS_VOICE = "serverTtsVoice"
        const val KEY_SPEECH_RATE = "speechRate"
        const val KEY_PITCH = "pitch"
        const val KEY_NOTIFICATIONS_ENABLED = "notificationsEnabled"
        const val KEY_NOTIFICATION_READ_ALOUD_ENABLED = "notificationReadAloudEnabled"
        const val KEY_CHIME_ENABLED = "chimeEnabled"
        const val KEY_CHIME_SOUND = "chimeSound"
        const val KEY_MIN_READ_ALOUD_PRIORITY = "minReadAloudPriority"
        const val KEY_THEME = "theme"
        const val KEY_DEBUG_MODE = "debugMode"
        const val KEY_CONFIG_TIMESTAMP = "configTimestamp"
        const val KEY_SCREEN_WIDTH_DP = "screenWidthDp"
        const val KEY_APP_VERSION = "appVersion"
        const val KEY_HAS_SPEAKER = "hasSpeaker"

        /**
         * Parse settings fields from server backup metadata.
         * Status fields get defaults (they're device-specific).
         */
        fun fromMetadataConfig(config: Map<String, String>): WatchSettingsPayload =
            WatchSettingsPayload(
                voiceCommandsEnabled = config["voiceCommandsEnabled"]?.toBooleanStrictOrNull() ?: true,
                readAloudEnabled = config["readAloudEnabled"]?.toBooleanStrictOrNull() ?: false,
                useServerTts = config["useServerTts"]?.toBooleanStrictOrNull() ?: false,
                serverTtsVoice = config["serverTtsVoice"] ?: "",
                speechRate = config["speechRate"]?.toFloatOrNull() ?: 1.0f,
                pitch = config["pitch"]?.toFloatOrNull() ?: 1.0f,
                notificationsEnabled = config["notificationsEnabled"]?.toBooleanStrictOrNull() ?: true,
                notificationReadAloudEnabled = config["notificationReadAloudEnabled"]?.toBooleanStrictOrNull() ?: false,
                chimeEnabled = config["chimeEnabled"]?.toBooleanStrictOrNull() ?: true,
                chimeSound = config["chimeSound"] ?: "default",
                minReadAloudPriority = config["minReadAloudPriority"] ?: "normal",
                theme = config["theme"] ?: "",
                debugMode = config["debugMode"]?.toBooleanStrictOrNull() ?: false
            )

        /**
         * Parse from a Wearable DataMap (DataItem storage).
         */
        fun fromDataMap(dataMap: com.google.android.gms.wearable.DataMap): WatchSettingsPayload =
            WatchSettingsPayload(
                voiceCommandsEnabled = dataMap.getBoolean(KEY_VOICE_COMMANDS_ENABLED, true),
                readAloudEnabled = dataMap.getBoolean(KEY_READ_ALOUD_ENABLED, false),
                useServerTts = dataMap.getBoolean(KEY_USE_SERVER_TTS, false),
                serverTtsVoice = dataMap.getString(KEY_SERVER_TTS_VOICE, "") ?: "",
                speechRate = dataMap.getFloat(KEY_SPEECH_RATE, 1.0f),
                pitch = dataMap.getFloat(KEY_PITCH, 1.0f),
                notificationsEnabled = dataMap.getBoolean(KEY_NOTIFICATIONS_ENABLED, true),
                notificationReadAloudEnabled = dataMap.getBoolean(KEY_NOTIFICATION_READ_ALOUD_ENABLED, false),
                chimeEnabled = dataMap.getBoolean(KEY_CHIME_ENABLED, true),
                chimeSound = dataMap.getString(KEY_CHIME_SOUND, "default") ?: "default",
                minReadAloudPriority = dataMap.getString(KEY_MIN_READ_ALOUD_PRIORITY, "normal") ?: "normal",
                theme = dataMap.getString(KEY_THEME, "") ?: "",
                debugMode = dataMap.getBoolean(KEY_DEBUG_MODE, false),
                configTimestamp = dataMap.getString(KEY_CONFIG_TIMESTAMP, "") ?: "",
                screenWidthDp = dataMap.getInt(KEY_SCREEN_WIDTH_DP, 0),
                appVersion = dataMap.getString(KEY_APP_VERSION, "") ?: "",
                hasSpeaker = dataMap.getBoolean(KEY_HAS_SPEAKER, true)
            )
    }

    /**
     * Write all fields to a Wearable DataMap (for DataItem storage).
     */
    fun toDataMap(dataMap: com.google.android.gms.wearable.DataMap) {
        dataMap.putBoolean(KEY_VOICE_COMMANDS_ENABLED, voiceCommandsEnabled)
        dataMap.putBoolean(KEY_READ_ALOUD_ENABLED, readAloudEnabled)
        dataMap.putBoolean(KEY_USE_SERVER_TTS, useServerTts)
        dataMap.putString(KEY_SERVER_TTS_VOICE, serverTtsVoice)
        dataMap.putFloat(KEY_SPEECH_RATE, speechRate)
        dataMap.putFloat(KEY_PITCH, pitch)
        dataMap.putBoolean(KEY_NOTIFICATIONS_ENABLED, notificationsEnabled)
        dataMap.putBoolean(KEY_NOTIFICATION_READ_ALOUD_ENABLED, notificationReadAloudEnabled)
        dataMap.putBoolean(KEY_CHIME_ENABLED, chimeEnabled)
        dataMap.putString(KEY_CHIME_SOUND, chimeSound)
        dataMap.putString(KEY_MIN_READ_ALOUD_PRIORITY, minReadAloudPriority)
        dataMap.putString(KEY_THEME, theme)
        dataMap.putBoolean(KEY_DEBUG_MODE, debugMode)
        dataMap.putString(KEY_CONFIG_TIMESTAMP, configTimestamp)
        dataMap.putInt(KEY_SCREEN_WIDTH_DP, screenWidthDp)
        dataMap.putString(KEY_APP_VERSION, appVersion)
        dataMap.putBoolean(KEY_HAS_SPEAKER, hasSpeaker)
    }

    /**
     * Compare only settings fields (ignoring status fields).
     * Used to detect whether an incoming DataItem change is from the phone (settings changed)
     * or from ourselves (status update that the phone echoed back).
     */
    fun settingsEqual(other: WatchSettingsPayload): Boolean =
        voiceCommandsEnabled == other.voiceCommandsEnabled &&
        readAloudEnabled == other.readAloudEnabled &&
        useServerTts == other.useServerTts &&
        serverTtsVoice == other.serverTtsVoice &&
        speechRate == other.speechRate &&
        pitch == other.pitch &&
        notificationsEnabled == other.notificationsEnabled &&
        notificationReadAloudEnabled == other.notificationReadAloudEnabled &&
        chimeEnabled == other.chimeEnabled &&
        chimeSound == other.chimeSound &&
        minReadAloudPriority == other.minReadAloudPriority &&
        theme == other.theme &&
        debugMode == other.debugMode
}
