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
 * 1. Replaces the placeholder base URL with the configured openHAB server URL
 * 2. Adds Basic Auth credentials if configured
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val credentialStore: CredentialStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val credentials = runBlocking { credentialStore.credentials.first() }
            ?: return chain.proceed(chain.request())

        val originalRequest = chain.request()

        // Replace placeholder host with actual server URL
        val newUrl = originalRequest.url.toString()
            .replace("https://placeholder.openhab.org/", credentials.serverUrl.trimEnd('/') + "/")

        val requestBuilder = originalRequest.newBuilder()
            .url(newUrl)

        // Add auth header if credentials are available
        if (credentials.username.isNotBlank() && credentials.password.isNotBlank()) {
            requestBuilder.header(
                "Authorization",
                Credentials.basic(credentials.username, credentials.password)
            )
        }

        return chain.proceed(requestBuilder.build())
    }
}
