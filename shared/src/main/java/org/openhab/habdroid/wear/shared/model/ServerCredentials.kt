package org.openhab.habdroid.wear.shared.model

/**
 * Stores the openHAB server connection details.
 * Shared between phone and watch modules.
 */
data class ServerCredentials(
    val serverUrl: String,
    val username: String = "",
    val password: String = "",
    val userKey: String = ""
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank()
    val hasAuth: Boolean get() = username.isNotBlank() && password.isNotBlank()

    /**
     * The UI components namespace derived from the user key.
     * Empty userKey = shared "wear:tile", otherwise "wear:tile:{userKey}".
     */
    val tileNamespace: String
        get() = if (userKey.isBlank()) "wear:tile" else "wear:tile:$userKey"
}
