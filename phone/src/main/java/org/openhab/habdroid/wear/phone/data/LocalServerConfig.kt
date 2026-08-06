package org.openhab.habdroid.wear.phone.data

import okhttp3.Credentials

/**
 * Local server connection configuration for the phone companion app.
 * Used for write operations (tile editor) when on the home WiFi network.
 */
data class LocalServerConfig(
    val serverUrl: String,
    val username: String = "",
    val password: String = "",
    val apiToken: String = "",
    val homeWifiSsid: String = ""
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank()
    val hasAuth: Boolean get() = username.isNotBlank() && password.isNotBlank()
    val hasApiToken: Boolean get() = apiToken.isNotBlank()
    val hasWifiSsid: Boolean get() = homeWifiSsid.isNotBlank()

    /**
     * Resolves the Authorization header value.
     * Prefers API token (Bearer) over Basic Auth with username/password.
     * Returns null if no auth is configured.
     */
    fun resolveAuthHeader(): String? = when {
        hasApiToken -> "Bearer $apiToken"
        hasAuth -> Credentials.basic(username, password)
        else -> null
    }
}
