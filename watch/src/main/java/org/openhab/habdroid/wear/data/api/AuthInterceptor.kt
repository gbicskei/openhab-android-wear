package org.openhab.habdroid.wear.data.api

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response
import org.openhab.habdroid.wear.data.repository.CredentialStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that:
 * 1. Replaces the placeholder base URL with the active server URL (selected by [ServerSelector])
 * 2. Adds the appropriate auth header based on which server won the race:
 *    - Local server: Bearer token if API token is set, else Basic Auth with local creds, else no auth
 *    - Cloud server: Basic Auth with cloud credentials
 *
 * On the first request, triggers [ServerSelector.resolveUrl] to race local vs cloud.
 * Subsequent requests use the cached winner.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val credentialStore: CredentialStore,
    private val serverSelector: ServerSelector
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val cloudCredentials = runBlocking { credentialStore.credentials.first() }
            ?: return chain.proceed(chain.request())

        val originalRequest = chain.request()

        // Use the Happy Eyeballs winner; trigger initial resolution if needed
        val serverUrl = runBlocking {
            serverSelector.getActiveUrlOrDefault()
                ?: serverSelector.resolveUrl()
        }.trimEnd('/') + "/"

        // Replace placeholder host with active server URL
        val newUrl = originalRequest.url.toString()
            .replace("https://placeholder.openhab.org/", serverUrl)

        val requestBuilder = originalRequest.newBuilder()
            .url(newUrl)

        // Add auth header based on which server is active
        val authHeader = if (serverSelector.isLocalActive()) {
            resolveLocalAuthHeader()
        } else {
            resolveCloudAuthHeader(cloudCredentials.username, cloudCredentials.password)
        }

        if (authHeader != null) {
            requestBuilder.header("Authorization", authHeader)
        }

        return chain.proceed(requestBuilder.build())
    }

    /**
     * Resolves the auth header for the local server.
     * Priority: API token (Bearer) > Basic Auth > no auth (null).
     */
    private fun resolveLocalAuthHeader(): String? {
        val localCreds = runBlocking { credentialStore.localCredentials.first() }
        return when {
            localCreds.hasApiToken -> "Bearer ${localCreds.apiToken}"
            localCreds.hasBasicAuth -> Credentials.basic(localCreds.username, localCreds.password)
            else -> null // No auth — common for LAN-only servers
        }
    }

    /**
     * Resolves the auth header for the cloud server (Basic Auth).
     */
    private fun resolveCloudAuthHeader(username: String, password: String): String? {
        return if (username.isNotBlank() && password.isNotBlank()) {
            Credentials.basic(username, password)
        } else {
            null
        }
    }
}
