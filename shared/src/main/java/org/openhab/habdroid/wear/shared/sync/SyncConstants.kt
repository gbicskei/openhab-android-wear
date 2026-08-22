package org.openhab.habdroid.wear.shared.sync

/**
 * Constants for Data Layer communication between phone and watch.
 */
object SyncConstants {
    /** Message path for connection/credential sync (phone → watch, MessageClient). */
    const val PATH_CONNECTION = "/openhab/connection"

    /** Message path for requesting the watch to reload items (phone → watch, MessageClient). */
    const val PATH_RELOAD = "/openhab/reload"

    /** Message path for requesting TTS test playback on watch (phone → watch, MessageClient). */
    const val PATH_TTS_TEST = "/openhab/tts-test"

    /** Message path for assistant status query (phone → watch, MessageClient). */
    const val PATH_ASSISTANT_STATUS_REQUEST = "/openhab/assistant-status-request"

    /** Message path for assistant status response (watch → phone, MessageClient). */
    const val PATH_ASSISTANT_STATUS_RESPONSE = "/openhab/assistant-status-response"

    /** Message path for assistant setup command (phone → watch: register after ADB grant). */
    const val PATH_ASSISTANT_REGISTER = "/openhab/assistant-register"

    /** Message path for requesting the watch app version (phone → watch, MessageClient). */
    const val PATH_VERSION_REQUEST = "/openhab/version-request"

    /** Message path for watch app version response (watch → phone, MessageClient). */
    const val PATH_VERSION_RESPONSE = "/openhab/version-response"

    /** Default UI components namespace for wear tile config */
    const val DEFAULT_TILE_NAMESPACE = "wear:tile"

    /** Builds the tile namespace for a given user key */
    fun tileNamespace(userKey: String): String =
        if (userKey.isBlank()) DEFAULT_TILE_NAMESPACE else "$DEFAULT_TILE_NAMESPACE:$userKey"
}
