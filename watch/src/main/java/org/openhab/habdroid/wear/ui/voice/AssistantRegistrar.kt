package org.openhab.habdroid.wear.ui.voice

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import org.openhab.habdroid.wear.util.AppLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles registering/checking the app as the system assistant.
 *
 * On every app launch, if WRITE_SECURE_SETTINGS has been granted (via ADB),
 * this class writes the necessary secure settings to keep the app registered
 * as the default assistant — surviving reinstalls.
 */
@Singleton
class AssistantRegistrar @Inject constructor() {

    companion object {
        private const val TAG = "AssistantRegistrar"
        private const val VOICE_INTERACTION_SERVICE_COMPONENT =
            "org.openhab.habdroid.wear/org.openhab.habdroid.wear.ui.voice.OpenHabVoiceInteractionService"
        private const val ASSISTANT_COMPONENT =
            "org.openhab.habdroid.wear/org.openhab.habdroid.wear.ui.voice.VoiceCommandActivity"
    }

    /**
     * Check if WRITE_SECURE_SETTINGS permission has been granted.
     */
    fun hasWriteSecureSettings(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if the app is currently registered as the system assistant.
     */
    fun isRegistered(context: Context): Boolean {
        val currentVis = Settings.Secure.getString(
            context.contentResolver, "voice_interaction_service"
        )
        return currentVis?.contains("org.openhab.habdroid.wear") == true
    }

    /**
     * Register the app as the system assistant by writing secure settings.
     * Requires WRITE_SECURE_SETTINGS permission (granted via ADB).
     * Returns true if successful.
     */
    fun register(context: Context): Boolean {
        if (!hasWriteSecureSettings(context)) {
            AppLog.d(TAG, "Cannot register: WRITE_SECURE_SETTINGS not granted")
            return false
        }

        return try {
            Settings.Secure.putString(
                context.contentResolver,
                "voice_interaction_service",
                VOICE_INTERACTION_SERVICE_COMPONENT
            )
            Settings.Secure.putString(
                context.contentResolver,
                "assistant",
                ASSISTANT_COMPONENT
            )
            AppLog.d(TAG, "Assistant registered successfully")
            true
        } catch (e: Exception) {
            AppLog.d(TAG, "Failed to register assistant: ${e.message}")
            false
        }
    }

    /**
     * Attempt to register on app launch if permission is available.
     * Safe to call repeatedly — only writes if needed.
     */
    fun ensureRegistered(context: Context) {
        if (hasWriteSecureSettings(context) && !isRegistered(context)) {
            register(context)
        }
    }

    /**
     * Returns a status summary for the phone app's Test button.
     */
    fun getStatus(context: Context): AssistantStatus {
        return AssistantStatus(
            hasPermission = hasWriteSecureSettings(context),
            isRegistered = isRegistered(context)
        )
    }
}

data class AssistantStatus(
    val hasPermission: Boolean,
    val isRegistered: Boolean
)
