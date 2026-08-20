package org.openhab.habdroid.wear.shared.sync

import kotlinx.serialization.Serializable

/**
 * Complete snapshot of watch-owned settings.
 *
 * Used for:
 * - Watch → Phone response when the phone requests current settings
 * - Phone → Watch push when restoring from server backup
 * - Serialization to/from server item metadata for backup
 *
 * Does NOT include: credentials, server URLs, API keys, theme (part of tile config).
 */
@Serializable
data class WatchSettingsSnapshot(
    // Debug
    val debugMode: Boolean = false,
    // Voice
    val voiceCommandsEnabled: Boolean = true,
    val readAloudEnabled: Boolean = false,
    val useServerTts: Boolean = false,
    val serverTtsVoice: String = "",
    val ttsSpeechRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    // Notifications
    val notificationsEnabled: Boolean = true,
    val notificationReadAloud: Boolean = false,
    val chimeEnabled: Boolean = true,
    val chimeSound: String = "default",
    val minReadAloudPriority: String = "normal"
) {
    /**
     * Convert to a flat string map suitable for openHAB item metadata config.
     * All values are stored as strings.
     */
    fun toMetadataConfig(): Map<String, String> = mapOf(
        "debugMode" to debugMode.toString(),
        "voiceCommandsEnabled" to voiceCommandsEnabled.toString(),
        "readAloudEnabled" to readAloudEnabled.toString(),
        "useServerTts" to useServerTts.toString(),
        "serverTtsVoice" to serverTtsVoice,
        "ttsSpeechRate" to ttsSpeechRate.toString(),
        "ttsPitch" to ttsPitch.toString(),
        "notificationsEnabled" to notificationsEnabled.toString(),
        "notificationReadAloud" to notificationReadAloud.toString(),
        "chimeEnabled" to chimeEnabled.toString(),
        "chimeSound" to chimeSound,
        "minReadAloudPriority" to minReadAloudPriority
    )

    companion object {
        /** Schema version stored in metadata "value" field */
        const val SCHEMA_VERSION = "1"

        /** Metadata namespace used for server backup */
        const val METADATA_NAMESPACE = "wearConfig"

        /**
         * Parse from openHAB item metadata config map (all string values).
         * Missing keys fall back to defaults.
         */
        fun fromMetadataConfig(config: Map<String, String>): WatchSettingsSnapshot =
            WatchSettingsSnapshot(
                debugMode = config["debugMode"]?.toBooleanStrictOrNull() ?: false,
                voiceCommandsEnabled = config["voiceCommandsEnabled"]?.toBooleanStrictOrNull() ?: true,
                readAloudEnabled = config["readAloudEnabled"]?.toBooleanStrictOrNull() ?: false,
                useServerTts = config["useServerTts"]?.toBooleanStrictOrNull() ?: false,
                serverTtsVoice = config["serverTtsVoice"] ?: "",
                ttsSpeechRate = config["ttsSpeechRate"]?.toFloatOrNull() ?: 1.0f,
                ttsPitch = config["ttsPitch"]?.toFloatOrNull() ?: 1.0f,
                notificationsEnabled = config["notificationsEnabled"]?.toBooleanStrictOrNull() ?: true,
                notificationReadAloud = config["notificationReadAloud"]?.toBooleanStrictOrNull() ?: false,
                chimeEnabled = config["chimeEnabled"]?.toBooleanStrictOrNull() ?: true,
                chimeSound = config["chimeSound"] ?: "default",
                minReadAloudPriority = config["minReadAloudPriority"] ?: "normal"
            )
    }
}
