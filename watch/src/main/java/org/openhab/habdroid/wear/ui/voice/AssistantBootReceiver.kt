package org.openhab.habdroid.wear.ui.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives MY_PACKAGE_REPLACED and BOOT_COMPLETED broadcasts to ensure
 * the assistant registration is restored after app updates and device reboots.
 *
 * This triggers Application.onCreate() → AssistantRegistrar.ensureRegistered()
 * without requiring the user to manually open the app.
 */
@AndroidEntryPoint
class AssistantBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var assistantRegistrar: AssistantRegistrar

    override fun onReceive(context: Context, intent: Intent?) {
        // The Hilt injection + Application.onCreate() handles the registration.
        // This receiver just ensures the process starts.
        assistantRegistrar.ensureRegistered(context)
    }
}
