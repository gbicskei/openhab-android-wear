package org.openhab.habdroid.wear.util

import android.content.Context
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Manages Android TextToSpeech for speaking on the watch.
 *
 * Lifecycle:
 * - Initializes TTS engine lazily on first [speak] call.
 * - Shuts down engine when [shutdown] is called.
 * - Re-initializes if needed after shutdown.
 *
 * The [speak] method suspends until the utterance finishes (or times out),
 * so callers can keep foreground state and ringer mode active for the full duration.
 */
@Singleton
class TtsManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "TtsManager"
        /** Maximum response length to speak (characters). Longer responses are skipped. */
        const val MAX_SPEAK_LENGTH = 200
        /** Timeout for TTS engine initialization (ms) */
        private const val INIT_TIMEOUT_MS = 5000L
        /** Maximum time to wait for an utterance to complete (ms) */
        private const val UTTERANCE_TIMEOUT_MS = 30000L
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    /** Whether this device has a speaker */
    val hasAudioOutput: Boolean by lazy {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)
    }

    /**
     * Speak the given text. Suspends until the utterance completes or times out.
     *
     * Initializes the TTS engine if not already done (adds ~1-3s on first call).
     *
     * @return true if the text was spoken successfully, false on failure or timeout
     */
    suspend fun speak(text: String, speechRate: Float = 1.0f, pitch: Float = 1.0f): Boolean {
        if (!hasAudioOutput) {
            AppLog.d(TAG, "No audio output — skipping TTS")
            return false
        }
        if (text.isBlank() || text.length > MAX_SPEAK_LENGTH) {
            AppLog.d(TAG, "Text empty or too long (${text.length} chars) — skipping TTS")
            return false
        }

        val engine = getOrInitEngine() ?: return false

        engine.setSpeechRate(speechRate)
        engine.setPitch(pitch)

        return speakAndAwait(engine, text)
    }

    /** Stop any current speech */
    fun stop() {
        tts?.stop()
    }

    /** Release TTS resources. Safe to call multiple times. */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    /**
     * Get the existing engine or initialize a new one.
     * Suspends until initialization completes (up to [INIT_TIMEOUT_MS]).
     */
    private suspend fun getOrInitEngine(): TextToSpeech? {
        tts?.let { if (isInitialized) return it }

        // Shut down any stale instance
        tts?.shutdown()
        tts = null
        isInitialized = false

        AppLog.d(TAG, "Initializing TTS engine...")

        val engine = withTimeoutOrNull(INIT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val instance = TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        AppLog.d(TAG, "TTS initialized successfully")
                        if (cont.isActive) cont.resume(true)
                    } else {
                        AppLog.e(TAG, "TTS initialization failed with status: $status")
                        if (cont.isActive) cont.resume(false)
                    }
                }

                tts = instance
                cont.invokeOnCancellation {
                    instance.shutdown()
                    tts = null
                }
            }
        }

        if (engine != true) {
            AppLog.w(TAG, "TTS init timed out or failed")
            tts?.shutdown()
            tts = null
            isInitialized = false
            return null
        }

        isInitialized = true
        return tts
    }

    /**
     * Speak text and suspend until the utterance completes, errors, or times out.
     */
    private suspend fun speakAndAwait(engine: TextToSpeech, text: String): Boolean {
        val utteranceId = "openhab_${System.currentTimeMillis()}"

        val result = withTimeoutOrNull(UTTERANCE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}

                    override fun onDone(id: String?) {
                        if (id == utteranceId && cont.isActive) cont.resume(true)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) {
                        AppLog.e(TAG, "TTS error for utterance: $id")
                        if (id == utteranceId && cont.isActive) cont.resume(false)
                    }

                    override fun onError(id: String?, errorCode: Int) {
                        AppLog.e(TAG, "TTS error code $errorCode for utterance: $id")
                        if (id == utteranceId && cont.isActive) cont.resume(false)
                    }
                })

                val queueResult = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                if (queueResult != TextToSpeech.SUCCESS) {
                    AppLog.e(TAG, "TTS speak() returned error: $queueResult")
                    if (cont.isActive) cont.resume(false)
                }

                cont.invokeOnCancellation { engine.stop() }
            }
        }

        if (result == null) {
            AppLog.w(TAG, "TTS utterance timed out")
            engine.stop()
            return false
        }

        return result
    }
}
