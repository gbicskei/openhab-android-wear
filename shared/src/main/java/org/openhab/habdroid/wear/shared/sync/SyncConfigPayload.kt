package org.openhab.habdroid.wear.shared.sync

import kotlinx.serialization.Serializable

/**
 * JSON payload sent from phone to watch via Data Layer for credential sync.
 *
 * Example:
 * ```json
 * {
 *   "serverUrl": "https://myopenhab.org",
 *   "username": "user@email.com",
 *   "password": "secret"
 * }
 * ```
 */
@Serializable
data class SyncConfigPayload(
    val serverUrl: String,
    val username: String = "",
    val password: String = "",
    val userKey: String = "",
    val deviceName: String = "",
    val googleTtsApiKey: String = "",
    val debugMode: Boolean = false,
    /** Pre-resolved IP addresses for the server hostname (seeded by phone DNS). */
    val resolvedIps: List<String> = emptyList()
) {
    /**
     * The UI components namespace derived from the user key.
     * Empty userKey = shared "wear:tile", otherwise "wear:tile:{userKey}".
     */
    val tileNamespace: String
        get() = if (userKey.isBlank()) "wear:tile" else "wear:tile:$userKey"
}
