package org.openhab.habdroid.wear.phone.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.openhab.habdroid.wear.shared.sync.SyncConstants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tests connectivity to an openHAB server by making a lightweight REST API call.
 */
@Singleton
class ConnectionTester @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: android.content.Context
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
     * Tests whether the config server supports write operations on the given namespace.
     * Reads from /rest/ui/components/{namespace}, then creates and deletes a test entry.
     */
    suspend fun testConfigConnection(
        serverUrl: String,
        username: String,
        password: String,
        apiToken: String = "",
        namespace: String = SyncConstants.DEFAULT_TILE_NAMESPACE
    ): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val baseUrl = serverUrl.trimEnd('/')
            val auth = when {
                apiToken.isNotBlank() -> "Bearer $apiToken"
                username.isNotBlank() && password.isNotBlank() -> Credentials.basic(username, password)
                else -> null
            }

            // Step 1: Test read access
            val readRequest = Request.Builder()
                .url("$baseUrl/rest/ui/components/$namespace")
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
                .url("$baseUrl/rest/ui/components/$namespace")
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
                .url("$baseUrl/rest/ui/components/$namespace/$testUid")
                .delete()
                .apply { auth?.let { header("Authorization", it) } }
                .build()
            okHttpClient.newCall(deleteRequest).execute()
        }
    }

    /**
     * Tests a Google Cloud TTS API key by making a minimal synthesis request.
     */
    suspend fun testGoogleTts(apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = """{"input":{"text":"test"},"voice":{"languageCode":"en-US","name":"en-US-Wavenet-D"},"audioConfig":{"audioEncoding":"MP3"}}"""
            val request = Request.Builder()
                .url("https://texttospeech.googleapis.com/v1/text:synthesize?key=$apiKey")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            when {
                response.isSuccessful -> Unit
                response.code == 400 -> throw ConnectionException("Invalid API key or request")
                response.code == 403 -> throw ConnectionException("API key not authorized for Text-to-Speech API")
                else -> throw ConnectionException("Google TTS API returned ${response.code}")
            }
        }
    }

    /**
     * Synthesizes a test phrase with the given voice and plays it on the phone speaker.
     */
    suspend fun playTestVoice(apiKey: String, voiceName: String) = withContext(Dispatchers.IO) {
        val languageCode = voiceName.substringBefore("-", "en") + "-" +
            voiceName.split("-").getOrElse(1) { "US" }

        val payload = """{"input":{"text":"This is how I sound. Do you like this voice?"},"voice":{"languageCode":"$languageCode","name":"$voiceName"},"audioConfig":{"audioEncoding":"MP3"}}"""
        val request = Request.Builder()
            .url("https://texttospeech.googleapis.com/v1/text:synthesize?key=$apiKey")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) throw ConnectionException("TTS API returned ${response.code}")

        val body = response.body?.string() ?: throw ConnectionException("Empty response")
        val contentRegex = """"audioContent"\s*:\s*"([^"]+)"""".toRegex()
        val audioBase64 = contentRegex.find(body)?.groupValues?.get(1)
            ?: throw ConnectionException("No audio in response")

        val audioBytes = android.util.Base64.decode(audioBase64, android.util.Base64.DEFAULT)
        val tempFile = java.io.File.createTempFile("tts_test", ".mp3")
        tempFile.writeBytes(audioBytes)

        val mediaPlayer = android.media.MediaPlayer()
        mediaPlayer.setDataSource(tempFile.absolutePath)
        mediaPlayer.prepare()
        mediaPlayer.setOnCompletionListener {
            it.release()
            tempFile.delete()
        }
        mediaPlayer.start()
    }

    /** Retained TTS instance for system voice testing. */
    private var systemTts: android.speech.tts.TextToSpeech? = null

    /**
     * Tests the system TTS with the given speech rate and pitch on the phone speaker.
     */
    fun playSystemTtsTest(speechRate: Float, pitch: Float) {
        // Shutdown previous instance if any
        systemTts?.stop()
        systemTts?.shutdown()
        systemTts = android.speech.tts.TextToSpeech(context) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                systemTts?.setSpeechRate(speechRate)
                systemTts?.setPitch(pitch)
                systemTts?.speak(
                    "This is a test of the text to speech system.",
                    android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                    null,
                    "test_utterance"
                )
            }
        }
    }

    /**
     * Fetches the openHAB server version string (e.g. "5.2.1").
     */
    suspend fun fetchOpenHabVersion(
        serverUrl: String,
        username: String,
        password: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val url = "${serverUrl.trimEnd('/')}/rest/"
            val requestBuilder = Request.Builder().url(url)
            if (username.isNotBlank() && password.isNotBlank()) {
                requestBuilder.header("Authorization", Credentials.basic(username, password))
            }
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null
            // Extract runtimeInfo.version (not the top-level API "version")
            val versionRegex = """"runtimeInfo"\s*:\s*\{[^}]*"version"\s*:\s*"([^"]+)"""".toRegex()
            versionRegex.find(body)?.groupValues?.get(1)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fetches available Google Cloud TTS voices for the given language code.
     */
    suspend fun fetchGoogleVoices(apiKey: String, languageCode: String = "en"): List<org.openhab.habdroid.wear.phone.ui.voice.VoiceOption> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://texttospeech.googleapis.com/v1/voices?key=$apiKey&languageCode=$languageCode")
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) return@withContext emptyList()

        val body = response.body?.string() ?: return@withContext emptyList()
        val nameRegex = """"name"\s*:\s*"([^"]+)"""".toRegex()
        nameRegex.findAll(body)
            .map { it.groupValues[1] }
            .map { name ->
                org.openhab.habdroid.wear.phone.ui.voice.VoiceOption(
                    id = name,
                    label = name.removePrefix("$languageCode-").removePrefix(languageCode.replace("-", "") + "-"),
                    locale = languageCode
                )
            }
            .toList()
            .sortedBy { it.label }
    }

    /**
     * Fetch the configVersion from the server's main tile page.
     * Returns the integer version, or null on failure.
     */
    suspend fun fetchConfigVersion(
        serverUrl: String,
        username: String,
        password: String,
        namespace: String = SyncConstants.DEFAULT_TILE_NAMESPACE
    ): Int? = withContext(Dispatchers.IO) {
        try {
            val url = "${serverUrl.trimEnd('/')}/rest/ui/components/$namespace/main"
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
