package org.openhab.habdroid.wear.shared.model

/**
 * Stores the openHAB server connection details.
 * Shared between phone and watch modules.
 */
data class ServerCredentials(
    val serverUrl: String,
    val username: String = "",
    val password: String = ""
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank()
    val hasAuth: Boolean get() = username.isNotBlank() && password.isNotBlank()
}
