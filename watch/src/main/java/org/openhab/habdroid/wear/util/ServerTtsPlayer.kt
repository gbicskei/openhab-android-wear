package org.openhab.habdroid.wear.util

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Plays TTS audio from Google Cloud Text-to-Speech API directly on the watch speaker.
 * Uses the API key approach (no OAuth needed).
 */
@Singleton
class ServerTtsPlayer @Inject constructor(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "ServerTtsPlayer"
        private const val TTS_URL = "https://texttospeech.googleapis.com/v1/text:synthesize"
        private const val DEFAULT_VOICE = "en-US-Wavenet-D"
        private const val DEFAULT_LANGUAGE = "en-US"

        /** Maximum text length to synthesize */
        const val MAX_TEXT_LENGTH = 300
    }

    private var mediaPlayer: MediaPlayer? = null
    private var apiKey: String? = null

    /** Set the Google Cloud TTS API key */
    fun setApiKey(key: String) {
        apiKey = key
    }

    /** Whether the player has an API key configured */
    val isConfigured: Boolean get() = !apiKey.isNullOrBlank()

    /**
     * Synthesize and play text on the watch speaker.
     * Returns true if audio was played, false on failure.
     */
    suspend fun speakFromServer(
        text: String,
        voice: String = DEFAULT_VOICE,
        languageCode: String = DEFAULT_LANGUAGE
    ): Boolean = withContext(Dispatchers.IO) {
        val key = apiKey
        if (key.isNullOrBlank()) {
            AppLog.w(TAG, "No API key configured")
            return@withContext false
        }
        if (text.isBlank() || text.length > MAX_TEXT_LENGTH) {
            AppLog.d(TAG, "Text empty or too long (${text.length} chars)")
            return@withContext false
        }

        try {
            // Derive language code from voice name (e.g. "en-GB-Wavenet-A" -> "en-GB")
            val effectiveLanguage = if (voice.contains("-")) {
                voice.split("-").take(2).joinToString("-")
            } else {
                languageCode
            }

            // Build request
            val payload = JSONObject().apply {
                put("input", JSONObject().put("text", text))
                put("voice", JSONObject().apply {
                    put("languageCode", effectiveLanguage)
                    put("name", voice)
                })
                put("audioConfig", JSONObject().put("audioEncoding", "MP3"))
            }

            val request = Request.Builder()
                .url("$TTS_URL?key=$key")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            // Execute
            AppLog.d(TAG, "Calling Google Cloud TTS...")
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                AppLog.e(TAG, "TTS API error: ${response.code}")
                return@withContext false
            }

            val body = response.body?.string() ?: return@withContext false
            val audioContent = JSONObject(body).getString("audioContent")

            // Decode base64 MP3
            val audioBytes = android.util.Base64.decode(audioContent, android.util.Base64.DEFAULT)
            AppLog.d(TAG, "Got ${audioBytes.size} bytes of audio")

            // Write to temp file (MediaPlayer needs a file or fd)
            val tempFile = File(context.cacheDir, "server_tts.mp3")
            tempFile.writeBytes(audioBytes)

            // Play
            playFile(tempFile)
        } catch (e: Exception) {
            AppLog.e(TAG, "Server TTS failed", e)
            false
        }
    }

    private suspend fun playFile(file: File): Boolean = suspendCancellableCoroutine { cont ->
        stop()
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                    if (cont.isActive) cont.resume(true)
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    mediaPlayer = null
                    if (cont.isActive) cont.resume(false)
                    true
                }
                prepare()
                setVolume(1.0f, 1.0f)
                start()
            } catch (e: Exception) {
                release()
                mediaPlayer = null
                if (cont.isActive) cont.resume(false)
            }
        }

        cont.invokeOnCancellation { stop() }
    }

    /** Stop current playback */
    fun stop() {
        mediaPlayer?.let {
            try {
                it.stop()
                it.release()
            } catch (_: Exception) {}
        }
        mediaPlayer = null
    }
}
