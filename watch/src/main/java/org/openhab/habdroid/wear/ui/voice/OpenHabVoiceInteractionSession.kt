package org.openhab.habdroid.wear.ui.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import org.openhab.habdroid.wear.util.AppLog

/**
 * Voice interaction session that launches [VoiceCommandActivity] to handle
 * the assistant interaction.
 *
 * When the user activates this app as their assistant (e.g., long-press power button),
 * the system creates this session. We immediately launch VoiceCommandActivity which
 * handles speech recognition and sending commands to openHAB.
 */
class OpenHabVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        AppLog.d(TAG, "VoiceInteractionSession onShow")

        // Launch the VoiceCommandActivity which handles speech recognition + command sending
        val intent = Intent(context, VoiceCommandActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)

        // Finish the session immediately — VoiceCommandActivity handles everything
        finish()
    }

    override fun onHandleAssist(state: AssistState) {
        super.onHandleAssist(state)
        AppLog.d(TAG, "onHandleAssist")
    }

    companion object {
        private const val TAG = "OHVoiceSession"
    }
}
