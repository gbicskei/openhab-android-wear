package org.openhab.habdroid.wear.notification

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.util.AppLog
import javax.inject.Inject

/**
 * Receives FCM data messages from the openHAB Cloud.
 *
 * Handles two message types:
 * - "notification": a push notification with title, message, icon, actions, etc.
 * - "hideNotification": dismisses a previously shown notification.
 *
 * Delegates actual notification processing (display, chime, TTS) to [NotificationHandler].
 */
@AndroidEntryPoint
class FcmMessageListenerService : FirebaseMessagingService() {

    @Inject
    lateinit var notificationHandler: NotificationHandler

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        AppLog.d(TAG, "FCM token refreshed")
        FcmRegistrationWorker.schedule(this)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        AppLog.d(TAG, "FCM message received: type=${data["type"]}, tag=${data["tag"]}")

        when (data["type"]) {
            "notification" -> {
                serviceScope.launch {
                    notificationHandler.handleNotification(
                        title = data["title"].orEmpty(),
                        message = data["message"].orEmpty(),
                        icon = data["icon"],
                        tag = data["tag"],
                        referenceId = data["reference-id"],
                        timestamp = data["timestamp"]?.toLongOrNull() ?: message.sentTime,
                        mediaAttachmentUrl = data["media-attachment-url"],
                        actions = data["actions"]
                    )
                }
            }

            "hideNotification" -> {
                val tag = data["tag"]
                val referenceId = data["reference-id"]
                AppLog.d(TAG, "Hide notification: tag=$tag, referenceId=$referenceId")
                notificationHandler.hideNotification(tag, referenceId)
            }

            else -> {
                AppLog.w(TAG, "Unknown FCM message type: ${data["type"]}")
            }
        }
    }

    companion object {
        private const val TAG = "FcmListener"
    }
}
