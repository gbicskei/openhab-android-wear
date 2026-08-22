package org.openhab.habdroid.wear.util

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.openhab.habdroid.wear.data.api.ServerSelector
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Plays audio from a URL on the watch speaker.
 *
 * Downloads audio via OkHttp with proper authentication (resolved by [ServerSelector]),
 * saves to a temp file, then plays locally using [MediaPlayer].
 *
 * Uses the same connection/auth pattern as [FcmRegistrationWorker]:
 * - [ServerSelector] resolves which server is active and provides the auth header
 * - `@Named("plainClient")` provides an OkHttpClient without the URL-rewriting interceptor
 *   (since audio URLs are already absolute)
 */
@Singleton
class AudioUrlPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("plainClient") private val httpClient: OkHttpClient,
    private val serverSelector: ServerSelector
) {
    companion object {
        private const val TAG = "AudioUrlPlayer"
    }

    private var mediaPlayer: MediaPlayer? = null

    /** Whether this device has a speaker */
    val hasAudioOutput: Boolean by lazy {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)
    }

    /**
     * Download and play audio from the given URL.
     * Suspends until playback completes (or fails).
     *
     * @param url The audio URL to play (absolute HTTP/HTTPS URL)
     * @return true if playback completed successfully, false on error
     */
    suspend fun play(url: String): Boolean {
        if (!hasAudioOutput) {
            AppLog.d(TAG, "No audio output — skipping audio URL playback")
            return false
        }
        if (url.isBlank()) {
            AppLog.w(TAG, "Empty audio URL — nothing to play")
            return false
        }

        AppLog.d(TAG, "Playing audio from URL: $url")

        return try {
            val audioFile = downloadAudio(url) ?: return false
            playFile(audioFile)
        } catch (e: Exception) {
            AppLog.e(TAG, "Audio URL playback failed", e)
            false
        }
    }

    /**
     * Download audio with authentication resolved via [ServerSelector].
     * Same pattern as [FcmRegistrationWorker]: plain client + manual auth header.
     */
    private suspend fun downloadAudio(url: String): File? = withContext(Dispatchers.IO) {
        // Ensure server is resolved (may trigger Happy Eyeballs race on first call)
        serverSelector.resolveUrl()
        val authHeader = serverSelector.resolveAuthHeader()

        val requestBuilder = Request.Builder().url(url).get()
        if (authHeader != null) {
            requestBuilder.header("Authorization", authHeader)
        }

        try {
            val response = httpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                AppLog.e(TAG, "Download failed: HTTP ${response.code} for $url")
                response.close()
                return@withContext null
            }

            val bytes = response.body?.bytes()
            response.close()

            if (bytes == null || bytes.isEmpty()) {
                AppLog.e(TAG, "Download returned empty body")
                return@withContext null
            }

            AppLog.d(TAG, "Downloaded ${bytes.size} bytes")

            val tempFile = File(context.cacheDir, "audio_sink.audio")
            tempFile.writeBytes(bytes)
            tempFile
        } catch (e: Exception) {
            AppLog.e(TAG, "Download failed: ${e.message}", e)
            null
        }
    }

    /**
     * Play audio from a local file on the main thread (MediaPlayer needs a Looper for callbacks).
     */
    private suspend fun playFile(file: File): Boolean = withContext(Dispatchers.Main) {
        stop()
        suspendCancellableCoroutine { cont ->
            mediaPlayer = MediaPlayer().apply {
                try {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setDataSource(file.absolutePath)

                    setOnCompletionListener { mp ->
                        AppLog.d(TAG, "Playback complete")
                        mp.release()
                        mediaPlayer = null
                        if (cont.isActive) cont.resume(true)
                    }

                    setOnErrorListener { mp, what, extra ->
                        AppLog.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                        mp.release()
                        mediaPlayer = null
                        if (cont.isActive) cont.resume(false)
                        true
                    }

                    prepare()
                    AppLog.d(TAG, "Prepared — starting playback (duration=${duration}ms)")
                    start()
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to play audio file", e)
                    release()
                    mediaPlayer = null
                    if (cont.isActive) cont.resume(false)
                }
            }

            cont.invokeOnCancellation { stop() }
        }
    }

    /** Stop current playback and release resources. */
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
