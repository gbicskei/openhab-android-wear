package org.openhab.habdroid.wear.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.openhab.habdroid.wear.R
import org.openhab.habdroid.wear.util.AppLog

/**
 * Short-lived foreground service that keeps the process alive and in "foreground" state
 * while audio (TTS or URL playback) is active.
 *
 * This prevents:
 * - lmkd from killing the process during network calls (TTS API) or playback
 * - Samsung AudioHardening from muting background media playback
 *
 * Started by [NotificationHandler] before audio begins, stopped when playback completes.
 */
class AudioPlaybackService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Playing audio"
        AppLog.d(TAG, "Foreground service started: $text")

        val notification = buildNotification(text)
        startForeground(NOTIFICATION_ID, notification)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        AppLog.d(TAG, "Foreground service stopped")
        super.onDestroy()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Audio Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while openHAB is playing audio on the watch"
            setShowBadge(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("openHAB")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val TAG = "AudioPlaybackSvc"
        private const val CHANNEL_ID = "audio_playback"
        private const val NOTIFICATION_ID = 9100
        private const val EXTRA_TEXT = "text"

        fun start(context: Context, text: String = "Playing audio") {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                putExtra(EXTRA_TEXT, text)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AudioPlaybackService::class.java))
        }
    }
}
