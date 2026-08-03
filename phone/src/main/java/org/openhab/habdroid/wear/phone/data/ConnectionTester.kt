package org.openhab.habdroid.wear.phone.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tests connectivity to an openHAB server by making a lightweight REST API call.
 */
@Singleton
class ConnectionTester @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    /**
     * Tests whether the given server URL and credentials are valid.
     * Makes a GET request to /rest/items with minimal fields.
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

    /**
     * Tests whether the config server supports write operations.
     * Reads from /rest/ui/components/wear:tile, then creates and deletes a test entry.
     */
    suspend fun testConfigConnection(
        serverUrl: String,
        username: String,
        password: String
    ): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val baseUrl = serverUrl.trimEnd('/')
            val auth = if (username.isNotBlank() && password.isNotBlank())
                Credentials.basic(username, password) else null

            // Step 1: Test read access
            val readRequest = Request.Builder()
                .url("$baseUrl/rest/ui/components/wear:tile")
                .apply { auth?.let { header("Authorization", it) } }
                .header("Accept", "application/json")
                .build()

            val readResponse = okHttpClient.newCall(readRequest).execute()
            when {
                readResponse.code == 401 -> throw InvalidCredentialsException()
                readResponse.code == 404 -> throw ServerNotFoundException()
                !readResponse.isSuccessful -> throw ConnectionException("Read failed: ${readResponse.code}")
            }

            // Step 2: Test write access (POST + DELETE)
            val testUid = "_connection_test_${System.currentTimeMillis()}"
            val testBody = """{"uid":"$testUid","tags":[],"props":{},"component":"wear:test","config":{}}"""

            val writeRequest = Request.Builder()
                .url("$baseUrl/rest/ui/components/wear:tile")
                .post(testBody.toRequestBody("application/json".toMediaType()))
                .apply { auth?.let { header("Authorization", it) } }
                .header("Content-Type", "application/json")
                .build()

            val writeResponse = okHttpClient.newCall(writeRequest).execute()
            when {
                writeResponse.code == 401 -> throw WriteNotAllowedException()
                !writeResponse.isSuccessful -> throw WriteNotAllowedException()
            }

            // Cleanup: delete the test entry
            val deleteRequest = Request.Builder()
                .url("$baseUrl/rest/ui/components/wear:tile/$testUid")
                .delete()
                .apply { auth?.let { header("Authorization", it) } }
                .build()
            okHttpClient.newCall(deleteRequest).execute()
        }
    }

    /**
     * Fetch the configVersion from the server's main tile page.
     * Returns the integer version, or null on failure.
     */
    suspend fun fetchConfigVersion(
        serverUrl: String,
        username: String,
        password: String
    ): Int? = withContext(Dispatchers.IO) {
        try {
            val url = "${serverUrl.trimEnd('/')}/rest/ui/components/wear:tile/main"
            val auth = if (username.isNotBlank() && password.isNotBlank())
                Credentials.basic(username, password) else null

            val request = Request.Builder()
                .url(url)
                .apply { auth?.let { header("Authorization", it) } }
                .header("Accept", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            // Extract configVersion from the config object
            val versionRegex = """"configVersion"\s*:\s*(\d+(?:\.\d+)?)""".toRegex()
            versionRegex.find(body)?.groupValues?.get(1)?.toDoubleOrNull()?.toInt()
        } catch (_: Exception) {
            null
        }
    }
}

class InvalidCredentialsException : Exception("Invalid username or password")
class ServerNotFoundException : Exception("Server not found or REST API unavailable")
class ConnectionException(message: String) : Exception(message)
class WriteNotAllowedException : Exception("Write access denied — check credentials or server config")
