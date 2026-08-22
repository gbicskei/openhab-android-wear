package org.openhab.habdroid.wear.shared.sync

import kotlinx.serialization.Serializable

/**
 * Atomic payload for all non-credential watch settings synced from phone to watch.
 *
 * Sent on PATH_SETTINGS whenever any preference changes (voice, notifications, theme, debug).
 * The phone's WatchSettingsViewModel always holds the complete state in memory — no partial
 * updates are possible.
 *
 * This is also the structure backed up to / restored from the openHAB server.
 * No secrets (passwords, API keys, URLs) are included.
 */
@Serializable
data class WatchSettingsPayload(
    // ─── Voice / TTS ───
    val voiceCommandsEnabled: Boolean = true,
    val readAloudEnabled: Boolean = false,
    val useServerTts: Boolean = false,
    val serverTtsVoice: String = "",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,

    // ─── Notifications ───
    val notificationsEnabled: Boolean = true,
    val notificationReadAloudEnabled: Boolean = false,
    val chimeEnabled: Boolean = true,
    val chimeSound: String = "default",
    val minReadAloudPriority: String = "normal",

    // ─── Appearance ───
    val theme: String = "",

    // ─── Debug ───
    val debugMode: Boolean = false
) {
    /**
     * Convert to a flat string map suitable for openHAB item metadata config (server backup).
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
        /** Schema version for server backup format */
        const val SCHEMA_VERSION = "2"

        /** Metadata namespace used for server backup */
        const val METADATA_NAMESPACE = "wearConfig"

        /**
         * Parse from openHAB item metadata config map (all string values).
         * Missing keys fall back to defaults.
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
    }
}
