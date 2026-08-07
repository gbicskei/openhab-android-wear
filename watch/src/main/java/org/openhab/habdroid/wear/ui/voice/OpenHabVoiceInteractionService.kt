package org.openhab.habdroid.wear.ui.voice

import android.service.voice.VoiceInteractionService
import org.openhab.habdroid.wear.util.AppLog

/**
 * Top-level VoiceInteractionService that registers this app as an available assistant.
 *
 * This service is kept lightweight — it only exists so the system recognizes the app
 * as a selectable digital assistant. The actual voice interaction logic is handled by
 * [OpenHabVoiceInteractionSessionService] and [OpenHabVoiceInteractionSession].
 */
class OpenHabVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        AppLog.d(TAG, "VoiceInteractionService ready")
    }

    override fun onShutdown() {
        super.onShutdown()
        AppLog.d(TAG, "VoiceInteractionService shutdown")
    }

    companion object {
        private const val TAG = "OHVoiceService"
    }
}
