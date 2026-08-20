package org.openhab.habdroid.wear.shared.sync

import kotlinx.serialization.Serializable

/**
 * JSON payload for voice settings sync from phone to watch.
 */
@Serializable
data class SyncVoiceSettingsPayload(
    val voiceCommandsEnabled: Boolean = true,
    val readAloudEnabled: Boolean = false,
    val useServerTts: Boolean = false,
    val serverTtsVoice: String = "",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f
)
