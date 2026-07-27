package org.openhab.habdroid.wear.data.model

/**
 * Stores the openHAB server connection details.
 */
data class ServerCredentials(
    val serverUrl: String,
    val username: String = "",
    val password: String = ""
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank()
    val hasAuth: Boolean get() = username.isNotBlank() && password.isNotBlank()
}
