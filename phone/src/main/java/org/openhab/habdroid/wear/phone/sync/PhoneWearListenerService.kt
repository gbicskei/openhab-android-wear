package org.openhab.habdroid.wear.phone.sync

import android.content.Intent
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.openhab.habdroid.wear.phone.ui.MainActivity

/**
 * Listens for messages from the watch via Data Layer.
 * Handles the "open app" request from the watch's "Setup on Phone" button.
 */
class PhoneWearListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            PATH_OPEN_APP -> {
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
            }
            else -> super.onMessageReceived(messageEvent)
        }
    }

    companion object {
        const val PATH_OPEN_APP = "/openhab/open-app"
    }
}
