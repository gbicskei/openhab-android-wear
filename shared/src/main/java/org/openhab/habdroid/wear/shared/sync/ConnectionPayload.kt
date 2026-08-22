package org.openhab.habdroid.wear.shared.sync

import kotlinx.serialization.Serializable

/**
 * Atomic payload for connection credentials and server configuration synced from phone to watch.
 *
 * Sent on PATH_CONNECTION whenever the user saves connection settings in the Setup screen.
 * Contains secrets (passwords, API keys, URLs) — never backed up to the server.
 *
 * The phone's SetupViewModel always knows all fields from its UI state + credential store.
 * No partial updates are possible.
 */
@Serializable
data class ConnectionPayload(
    // ─── Cloud/main server ───
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val userKey: String = "",

    // ─── Local server (Happy Eyeballs racing) ───
    val localServerUrl: String = "",
    val localUsername: String = "",
    val localPassword: String = "",
    val localApiToken: String = "",

    // ─── DNS pre-resolution (phone resolves, watch caches) ───
    val resolvedIps: List<String> = emptyList(),

    // ─── Device identity ───
    val deviceName: String = "",
    val bindingInstalled: Boolean = false,

    // ─── TTS API key (secret, not backed up) ───
    val googleTtsApiKey: String = "",

    // ─── Sync control ───
    /** When true, the watch clears its tile cache and reloads from server after applying. */
    val triggerReload: Boolean = false
) {
    /**
     * The UI components namespace derived from the user key.
     * Empty userKey = shared "wear:tile", otherwise "wear:tile:{userKey}".
     */
    val tileNamespace: String
        get() = if (userKey.isBlank()) "wear:tile" else "wear:tile:$userKey"
}
