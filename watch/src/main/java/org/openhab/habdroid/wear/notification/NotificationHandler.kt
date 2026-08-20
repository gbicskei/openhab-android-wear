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
        actions: String?,
        priority: String? = null
    ) {
        AppLog.d(TAG, "handleNotification: title='$title', message='$message', tag=$tag")

        // Check master switch
        if (!notificationPrefs.notificationsEnabled.first()) {
            AppLog.d(TAG, "Notifications disabled — ignoring")
            return
        }

        // Don't show visual notification for audio sink messages (they just play audio)
        if (tag != TAG_AUDIO_SINK) {
            showNotification(title, message, tag, referenceId, timestamp, priority)
        }

        // Branch by tag: audio-sink plays a URL, audio-tts speaks text, others are user notifications
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
            TAG_AUDIO_TTS -> {
                // Direct TTS from binding's speak() action — always speak, ignore readAloud setting
                if (message.isNotBlank()) {
                    AppLog.d(TAG, "audio-tts: speaking '$message'")
                    playTtsWithUnmute(title, message)
                }
            }
            else -> {
                // Regular notification: speak text via TTS if read-aloud is enabled and priority meets threshold
                val readAloud = notificationPrefs.readAloudEnabled.first()
                val minPriority = notificationPrefs.minReadAloudPriority.first()
                val msgPriority = priority ?: "normal"
                val meetsThreshold = priorityLevel(msgPriority) >= priorityLevel(minPriority)
                AppLog.d(TAG, "readAloud=$readAloud, priority=$msgPriority, minPriority=$minPriority, meetsThreshold=$meetsThreshold")
                if (readAloud && message.isNotBlank() && meetsThreshold) {
                    playTtsWithUnmute(title, message)
                }
            }
        }
    }

    /**
     * Temporarily unmute the watch and play an audio URL (for audio-sink messages).
     * Plays optional chime before the audio, then restores ringer mode.
     */
    private suspend fun playAudioWithUnmute(audioUrl: String) {
        AudioPlaybackService.start(context, "Playing audio")
        showSpeakDisplay("openHAB", audioUrl)

        val originalRingerMode = audioManager.ringerMode
        val wasMuted = originalRingerMode != AudioManager.RINGER_MODE_NORMAL
        if (wasMuted) {
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            AppLog.d(TAG, "Temporarily unmuted for audio-sink (was mode=$originalRingerMode)")
        }

        try {
            val chime = notificationPrefs.chimeEnabled.first()
            if (chime) {
                playChime()
            }
            audioUrlPlayer.play(audioUrl)
        } finally {
            if (wasMuted) {
                audioManager.ringerMode = originalRingerMode
                AppLog.d(TAG, "Restored ringer mode=$originalRingerMode")
            }
            dismissSpeakDisplay()
            AudioPlaybackService.stop(context)
        }
    }

    /**
     * Temporarily unmute the watch and speak text via TTS (for audio-tts messages).
     * Plays optional chime before speaking, then restores ringer mode.
     */
    private suspend fun playTtsWithUnmute(title: String, message: String) {
        AudioPlaybackService.start(context, message.take(50))
        showSpeakDisplay(title, message)

        val originalRingerMode = audioManager.ringerMode
        val wasMuted = originalRingerMode != AudioManager.RINGER_MODE_NORMAL
        if (wasMuted) {
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            AppLog.d(TAG, "Temporarily unmuted for TTS (was mode=$originalRingerMode)")
        }

        try {
            val chime = notificationPrefs.chimeEnabled.first()
            if (chime) {
                playChime()
            }
            val spokenText = buildSpokenText(title, message)
            AppLog.d(TAG, "Speaking: '$spokenText'")
            speakMessage(spokenText)
        } finally {
            if (wasMuted) {
                audioManager.ringerMode = originalRingerMode
                AppLog.d(TAG, "Restored ringer mode=$originalRingerMode")
            }
            dismissSpeakDisplay()
            AudioPlaybackService.stop(context)
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
        timestamp: Long,
        priority: String? = null
    ) {
        val notifId = (referenceId ?: tag ?: message)?.hashCode() ?: System.currentTimeMillis().toInt()

        val (notifPriority, color) = when (priority?.lowercase()) {
            "high" -> Pair(
                NotificationCompat.PRIORITY_HIGH,
                0xFFF44336.toInt() // Red
            )
            "low" -> Pair(
                NotificationCompat.PRIORITY_LOW,
                0xFF2196F3.toInt() // Blue
            )
            else -> Pair(
                NotificationCompat.PRIORITY_DEFAULT,
                0xFFFF9800.toInt() // Orange
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { "wearOH" })
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setWhen(timestamp)
            .setAutoCancel(true)
            .setPriority(notifPriority)
            .setColor(color)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        notificationManager.notify(notifId, notification)
        AppLog.d(TAG, "Showing notification id=$notifId priority=$priority")
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
     * Times out after 5 seconds to avoid blocking TTS on cold start when audio system is slow.
     */
    private suspend fun playChime() = withContext(Dispatchers.IO) {
        try {
            val uri = getChimeUri() ?: return@withContext

            kotlinx.coroutines.withTimeoutOrNull(5000L) {
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
            } ?: AppLog.w(TAG, "Chime timed out — proceeding to TTS")
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
            val rate = voicePrefs.ttsSpeechRate.first()
            val pitch = voicePrefs.ttsPitch.first()
            AppLog.d(TAG, "Speaking via local TTS: '${text.take(50)}...'")
            ttsManager.speak(text, speechRate = rate, pitch = pitch)
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

    /**
     * Map a priority string to a numeric level for comparison.
     * high=3, normal=2, low=1.
     */
    private fun priorityLevel(priority: String): Int = when (priority.lowercase()) {
        "high" -> 3
        "normal" -> 2
        "low" -> 1
        else -> 2
    }

    companion object {
        private const val TAG = "NotificationHandler"
        private const val CHANNEL_ID = "openhab_cloud_notifications"
        private const val TAG_AUDIO_SINK = "audio-sink"
        private const val TAG_AUDIO_TTS = "audio-tts"
    }
}
