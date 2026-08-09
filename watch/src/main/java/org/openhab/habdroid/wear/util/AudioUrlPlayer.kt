package org.openhab.habdroid.wear.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Plays audio from a URL on the watch speaker using [MediaPlayer].
 *
 * Used for the "audio-sink" notification type where the openHAB server
 * pre-synthesizes audio (via its TTS engine) and sends the download URL
 * to the watch via FCM.
 */
@Singleton
class AudioUrlPlayer @Inject constructor(
    @ApplicationContext @Suppress("unused") private val context: Context
) {
    companion object {
        private const val TAG = "AudioUrlPlayer"
    }

    private var mediaPlayer: MediaPlayer? = null

    /**
     * Stream and play audio from the given URL.
     * Suspends until playback completes (or fails).
     *
     * @param url The audio URL to play (absolute HTTP/HTTPS URL)
     * @return true if playback completed successfully, false on error
     */
    suspend fun play(url: String): Boolean = withContext(Dispatchers.IO) {
        if (url.isBlank()) {
            AppLog.w(TAG, "Empty audio URL — nothing to play")
            return@withContext false
        }

        AppLog.d(TAG, "Playing audio from URL: $url")

        try {
            playUrl(url)
        } catch (e: Exception) {
            AppLog.e(TAG, "Audio URL playback failed", e)
            false
        }
    }

    private suspend fun playUrl(url: String): Boolean = suspendCancellableCoroutine { cont ->
        stop()

        mediaPlayer = MediaPlayer().apply {
            try {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(url)

                setOnPreparedListener { mp ->
                    AppLog.d(TAG, "Prepared — starting playback (duration=${mp.duration}ms)")
                    mp.start()
                }

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

                // Use async prepare for network URLs
                prepareAsync()
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to set up MediaPlayer", e)
                release()
                mediaPlayer = null
                if (cont.isActive) cont.resume(false)
            }
        }

        cont.invokeOnCancellation { stop() }
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
