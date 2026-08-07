package org.openhab.habdroid.wear.ui.voice

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import org.openhab.habdroid.wear.util.AppLog

/**
 * Creates [VoiceInteractionSession] instances when the system activates this assistant.
 *
 * This service runs in a separate process (recommended by Android docs) but for Wear OS
 * simplicity we keep it in the main process. The system binds to this service when a
 * voice interaction session is requested.
 */
class OpenHabVoiceInteractionSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        AppLog.d(TAG, "Creating new VoiceInteractionSession")
        return OpenHabVoiceInteractionSession(this)
    }

    companion object {
        private const val TAG = "OHSessionService"
    }
}
