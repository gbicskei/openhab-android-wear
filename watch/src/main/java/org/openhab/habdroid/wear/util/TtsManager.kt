package org.openhab.habdroid.wear.util

import android.content.Context
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Android TextToSpeech for speaking voice command responses on the watch.
 *
 * Lifecycle:
 * - Initializes TTS engine lazily on first [speak] call.
 * - Shuts down engine when [shutdown] is called.
 * - Re-initializes if needed after shutdown.
 *
 * Constraints:
 * - Only speaks if the device has FEATURE_AUDIO_OUTPUT.
 * - Skips responses longer than [MAX_SPEAK_LENGTH] characters.
 * - Uses the TTS engine's configured language (set in watch TTS settings).
 */
@Singleton
class TtsManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "TtsManager"
        /** Maximum response length to speak (characters). Longer responses are skipped. */
        const val MAX_SPEAK_LENGTH = 200
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingSpeechRate = 1.0f
    private var pendingPitch = 1.0f

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    /** Whether this device has a speaker */
    val hasAudioOutput: Boolean by lazy {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)
    }

    /**
     * Speak the given text. Initializes TTS if not already done.
     * No-op if:
     * - Device has no audio output
     * - Text is blank or exceeds [MAX_SPEAK_LENGTH]
     */
    fun speak(text: String, speechRate: Float = 1.0f, pitch: Float = 1.0f) {
        if (!hasAudioOutput) {
            AppLog.d(TAG, "No audio output — skipping TTS")
            return
        }
        if (text.isBlank() || text.length > MAX_SPEAK_LENGTH) {
            AppLog.d(TAG, "Text empty or too long (${text.length} chars) — skipping TTS")
            return
        }

        pendingSpeechRate = speechRate
        pendingPitch = pitch

        if (tts == null || !isInitialized) {
            initAndSpeak(text)
        } else {
            doSpeak(text)
        }
    }

    /** Stop any current speech */
    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    /** Release TTS resources. Safe to call multiple times. */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        _isSpeaking.value = false
    }

    private fun initAndSpeak(text: String) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                AppLog.d(TAG, "TTS initialized successfully")
                doSpeak(text)
            } else {
                AppLog.e(TAG, "TTS initialization failed with status: $status")
                isInitialized = false
            }
        }
    }

    private fun doSpeak(text: String) {
        val engine = tts ?: return

        // Apply speech rate and pitch
        engine.setSpeechRate(pendingSpeechRate)
        engine.setPitch(pendingPitch)

        // Set up progress listener
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                AppLog.e(TAG, "TTS error for utterance: $utteranceId")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                AppLog.e(TAG, "TTS error code $errorCode for utterance: $utteranceId")
            }
        })

        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "openhab_voice_response")
    }
}
