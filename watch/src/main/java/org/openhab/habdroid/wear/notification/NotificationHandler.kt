package org.openhab.habdroid.wear.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.openhab.habdroid.wear.R
import org.openhab.habdroid.wear.data.repository.NotificationPreferenceStore
import org.openhab.habdroid.wear.data.repository.VoicePreferenceStore
import org.openhab.habdroid.wear.util.AppLog
import org.openhab.habdroid.wear.util.AudioUrlPlayer
import org.openhab.habdroid.wear.util.ServerTtsPlayer
import org.openhab.habdroid.wear.util.TtsManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Processes incoming push notifications from the openHAB Cloud.
 *
 * Responsibilities:
 * 1. Show a system notification on the watch (vibration + visual)
 * 2. Optionally play an alert chime
 * 3. Optionally read the message aloud via TTS (local or server)
 *
 * Settings are read from [NotificationPreferenceStore] and [VoicePreferenceStore].
 */
@Singleton
class NotificationHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ttsManager: TtsManager,
    private val serverTtsPlayer: ServerTtsPlayer,
    private val audioUrlPlayer: AudioUrlPlayer,
    private val notificationPrefs: NotificationPreferenceStore,
    private val voicePrefs: VoicePreferenceStore
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        createNotificationChannel()
    }

    /**
     * Handle an incoming notification message.
     * Shows visual notification, then optionally plays chime + reads aloud.
     */
    suspend fun handleNotification(
        title: String,
        message: String,
        icon: String?,
        tag: String?,
        referenceId: String?,
        timestamp: Long,
        mediaAttachmentUrl: String?,
        actions: String?
    ) {
        AppLog.d(TAG, "handleNotification: title='$title', message='$message', tag=$tag")

        // Check master switch
        if (!notificationPrefs.notificationsEnabled.first()) {
            AppLog.d(TAG, "Notifications disabled — ignoring")
            return
        }

        // Show system notification
        showNotification(title, message, tag, referenceId, timestamp)

        // Branch by tag: audio-sink plays a URL, audio-tts speaks text
        when (tag) {
            TAG_AUDIO_SINK -> {
                val audioUrl = mediaAttachmentUrl ?: message
                if (audioUrl.isNotBlank()) {
                    AppLog.d(TAG, "audio-sink: playing URL '$audioUrl'")
                    playAudioWithUnmute(audioUrl)
                } else {
                    AppLog.w(TAG, "audio-sink: no URL in mediaAttachmentUrl or message")
                }
            }
            else -> {
                // audio-tts or any other tag: speak text via TTS
                val readAloud = notificationPrefs.readAloudEnabled.first()
                AppLog.d(TAG, "readAloudEnabled=$readAloud, message.isNotBlank=${message.isNotBlank()}")
                if (readAloud && message.isNotBlank()) {
                    playTtsWithUnmute(title, message)
                }
            }
        }
    }

    /**
     * Temporarily unmute the watch and play an audio URL (for audio-sink messages).
     * Plays optional chime before the audio, then restores ringer mode and volume.
     */
    private suspend fun playAudioWithUnmute(audioUrl: String) {
        showSpeakDisplay("openHAB", audioUrl)

        val originalRingerMode = audioManager.ringerMode
        val wasMuted = originalRingerMode != AudioManager.RINGER_MODE_NORMAL
        if (wasMuted) {
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            AppLog.d(TAG, "Temporarily unmuted for audio-sink (was mode=$originalRingerMode)")
        }

        // Set the desired notification volume
        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val targetVolume = calculateStreamVolume(notificationPrefs.notificationVolume.first())
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        AppLog.d(TAG, "Set stream volume to $targetVolume (was $originalVolume)")

        try {
            val chime = notificationPrefs.chimeEnabled.first()
            if (chime) {
                playChime()
            }
            audioUrlPlayer.play(audioUrl)
        } finally {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
            if (wasMuted) {
                audioManager.ringerMode = originalRingerMode
                AppLog.d(TAG, "Restored ringer mode=$originalRingerMode")
            }
            dismissSpeakDisplay()
        }
    }

    /**
     * Temporarily unmute the watch and speak text via TTS (for audio-tts messages).
     * Plays optional chime before speaking, then restores ringer mode and volume.
     */
    private suspend fun playTtsWithUnmute(title: String, message: String) {
        showSpeakDisplay(title, message)

        val originalRingerMode = audioManager.ringerMode
        val wasMuted = originalRingerMode != AudioManager.RINGER_MODE_NORMAL
        if (wasMuted) {
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            AppLog.d(TAG, "Temporarily unmuted for TTS (was mode=$originalRingerMode)")
        }

        // Set the desired notification volume
        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val targetVolume = calculateStreamVolume(notificationPrefs.notificationVolume.first())
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        AppLog.d(TAG, "Set stream volume to $targetVolume (was $originalVolume)")

        try {
            val chime = notificationPrefs.chimeEnabled.first()
            if (chime) {
                playChime()
            }
            val spokenText = buildSpokenText(title, message)
            AppLog.d(TAG, "Speaking: '$spokenText'")
            speakMessage(spokenText)
        } finally {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
            if (wasMuted) {
                audioManager.ringerMode = originalRingerMode
                AppLog.d(TAG, "Restored ringer mode=$originalRingerMode")
            }
            dismissSpeakDisplay()
        }
    }

    /**
     * Hide/dismiss a notification by tag or referenceId.
     */
    fun hideNotification(tag: String?, referenceId: String?) {
        val notifId = (referenceId ?: tag)?.hashCode() ?: return
        notificationManager.cancel(notifId)
        AppLog.d(TAG, "Dismissed notification id=$notifId")
    }

    private fun showNotification(
        title: String,
        message: String,
        tag: String?,
        referenceId: String?,
        timestamp: Long
    ) {
        val notifId = (referenceId ?: tag ?: message)?.hashCode() ?: System.currentTimeMillis().toInt()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { "openHAB" })
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setWhen(timestamp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        notificationManager.notify(notifId, notification)
        AppLog.d(TAG, "Showing notification id=$notifId")
    }

    /**
     * Build the text to be spoken. If title is present, prefix it.
     */
    private fun buildSpokenText(title: String, message: String): String {
        return if (title.isNotBlank() && title != "openHAB" && title != "MobileAudio") {
            "$title. $message"
        } else {
            message
        }
    }

    /**
     * Play a system alert sound before TTS. Suspends until playback completes.
     */
    private suspend fun playChime() = withContext(Dispatchers.IO) {
        try {
            val uri = getChimeUri() ?: return@withContext

            suspendCancellableCoroutine { cont ->
                val player = try {
                    MediaPlayer().apply {
                        setDataSource(context, uri)
                        prepare()
                    }
                } catch (e: Exception) {
                    AppLog.w(TAG, "Failed to create MediaPlayer for chime", e)
                    if (cont.isActive) cont.resume(Unit)
                    return@suspendCancellableCoroutine
                }

                player.setOnCompletionListener {
                    it.release()
                    if (cont.isActive) cont.resume(Unit)
                }
                player.setOnErrorListener { mp, _, _ ->
                    mp.release()
                    if (cont.isActive) cont.resume(Unit)
                    true
                }
                player.start()

                cont.invokeOnCancellation {
                    try {
                        player.stop()
                        player.release()
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Chime playback failed", e)
        }
    }

    /**
     * Get the system sound URI for the configured chime type.
     * Returns null if chime is set to "none".
     */
    private suspend fun getChimeUri(): Uri? {
        return when (notificationPrefs.chimeSound.first()) {
            "alarm" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            "none" -> null
            else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
    }

    /**
     * Speak the message using the preferred TTS engine (local or server).
     */
    private suspend fun speakMessage(text: String) {
        val useServerTts = voicePrefs.serverTtsEnabled.first()

        // Ensure ServerTtsPlayer has the API key loaded from preferences
        if (useServerTts && !serverTtsPlayer.isConfigured) {
            val apiKey = voicePrefs.serverTtsApiKey.first()
            if (apiKey.isNotBlank()) {
                serverTtsPlayer.setApiKey(apiKey)
            }
        }

        if (useServerTts && serverTtsPlayer.isConfigured) {
            val voice = voicePrefs.serverTtsVoice.first()
            AppLog.d(TAG, "Speaking via server TTS (voice=$voice): '${text.take(50)}...'")
            serverTtsPlayer.speakFromServer(text, voice)
        } else {
            val volume = voicePrefs.ttsVolume.first()
            val rate = voicePrefs.ttsSpeechRate.first()
            val pitch = voicePrefs.ttsPitch.first()
            AppLog.d(TAG, "Speaking via local TTS: '${text.take(50)}...'")
            ttsManager.speak(text, volume, rate, pitch)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "openHAB Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Push notifications from openHAB Cloud"
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Convert a 0.0–1.0 volume fraction to an absolute stream volume index.
     */
    private fun calculateStreamVolume(fraction: Float): Int {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return (fraction.coerceIn(0.0f, 1.0f) * maxVolume).toInt().coerceAtLeast(1)
    }

    /**
     * Launch [SpeakDisplayActivity] to show the message text on screen during playback.
     */
    private fun showSpeakDisplay(title: String, message: String) {
        try {
            val intent = SpeakDisplayActivity.createIntent(context, title, message)
            context.startActivity(intent)
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to launch SpeakDisplayActivity", e)
        }
    }

    /**
     * Dismiss the [SpeakDisplayActivity] after playback completes.
     */
    private fun dismissSpeakDisplay() {
        try {
            val intent = SpeakDisplayActivity.createDismissIntent(context)
            context.startActivity(intent)
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to dismiss SpeakDisplayActivity", e)
        }
    }

    companion object {
        private const val TAG = "NotificationHandler"
        private const val CHANNEL_ID = "openhab_cloud_notifications"
        private const val TAG_AUDIO_SINK = "audio-sink"
    }
}
