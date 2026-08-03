package org.openhab.habdroid.wear.phone.data

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
}
