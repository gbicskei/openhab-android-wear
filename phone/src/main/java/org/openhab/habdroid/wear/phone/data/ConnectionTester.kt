package org.openhab.habdroid.wear.phone.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tests connectivity to an openHAB server by making a lightweight REST API call.
 * Uses a direct OkHttp request rather than Retrofit for simplicity.
 */
@Singleton
class ConnectionTester @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    /**
     * Tests whether the given server URL and credentials are valid.
     * Makes a GET request to /rest/items with minimal fields.
     *
     * @return Result.success if the server responds with 200,
     *         Result.failure with a descriptive exception otherwise.
     */
    suspend fun testConnection(
        serverUrl: String,
        username: String,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${serverUrl.trimEnd('/')}/rest/items?fields=name&limit=1"

            val requestBuilder = Request.Builder().url(url)

            if (username.isNotBlank() && password.isNotBlank()) {
                requestBuilder.header("Authorization", Credentials.basic(username, password))
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()

            when {
                response.isSuccessful -> Unit
                response.code == 401 -> throw InvalidCredentialsException()
                response.code == 404 -> throw ServerNotFoundException()
                else -> throw ConnectionException("Server returned ${response.code}")
            }
        }
    }
}

class InvalidCredentialsException : Exception("Invalid username or password")
class ServerNotFoundException : Exception("Server not found or REST API unavailable")
class ConnectionException(message: String) : Exception(message)
