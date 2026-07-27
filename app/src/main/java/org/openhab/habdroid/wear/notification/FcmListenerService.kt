package org.openhab.habdroid.wear.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import org.openhab.habdroid.wear.R
import org.openhab.habdroid.wear.ui.MainActivity
import javax.inject.Inject

/**
 * Receives FCM push notifications from the openHAB Cloud connector.
 * Displays notifications on the watch with optional action buttons.
 *
 * The openHAB Cloud sends notifications with data payload containing:
 * - type: "notification" or "hideNotification"
 * - message: notification text
 * - title: optional title (defaults to "openHAB")
 * - icon: openHAB icon name
 * - tag: grouping tag
 * - persistedId: unique ID
 * - reference-id: for replacing/hiding notifications
 * - actions: JSON array of action buttons
 * - on-click: action when notification is tapped
 */
@AndroidEntryPoint
class FcmListenerService : FirebaseMessagingService() {

    @Inject
    lateinit var fcmRegistrationManager: FcmRegistrationManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM token received")
        fcmRegistrationManager.scheduleRegistration(this)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        Log.d(TAG, "FCM message received: type=${data["type"]}")

        when (data["type"]) {
            "notification" -> handleNotification(data, message.sentTime)
            "hideNotification" -> handleHideNotification(data)
            else -> Log.w(TAG, "Unknown message type: ${data["type"]}")
        }
    }

    private fun handleNotification(data: Map<String, String>, sentTime: Long) {
        val notificationId = data["persistedId"]?.hashCode()
            ?: System.currentTimeMillis().toInt()

        val title = data["title"] ?: "openHAB"
        val messageText = data["message"] ?: return
        val tag = data["tag"]
        val referenceId = data["reference-id"]

        // Build tap action — opens the main app
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_openhab_notification)
            .setContentTitle(title)
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setWhen(data["timestamp"]?.toLongOrNull() ?: sentTime)
            .setGroup(tag ?: "openhab")
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)

        // Use reference-id for replacing existing notifications if provided
        val displayId = referenceId?.hashCode() ?: notificationId
        notificationManager.notify(tag, displayId, notification)
    }

    private fun handleHideNotification(data: Map<String, String>) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val tag = data["tag"]
        val referenceId = data["reference-id"]

        if (referenceId != null) {
            notificationManager.cancel(tag, referenceId.hashCode())
        } else if (tag != null) {
            // Cancel all notifications with this tag
            notificationManager.activeNotifications
                .filter { it.tag == tag }
                .forEach { notificationManager.cancel(it.tag, it.id) }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "openHAB Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications from your openHAB smart home"
            enableVibration(true)
        }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "FcmListenerService"
        const val CHANNEL_ID = "openhab_notifications"
    }
}
