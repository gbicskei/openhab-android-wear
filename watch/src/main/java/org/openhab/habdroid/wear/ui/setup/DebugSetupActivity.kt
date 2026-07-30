package org.openhab.habdroid.wear.ui.setup

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import org.openhab.habdroid.wear.shared.model.ServerCredentials
import org.openhab.habdroid.wear.data.repository.CredentialStore
import javax.inject.Inject

/**
 * Debug-only activity that accepts credentials via ADB intent extras.
 * Usage:
 *   adb shell am start -n org.openhab.habdroid.wear/.ui.setup.DebugSetupActivity \
 *     --es url "https://myopenhab.org" \
 *     --es user "email@example.com" \
 *     --es pass "password"
 */
@AndroidEntryPoint
class DebugSetupActivity : ComponentActivity() {

    @Inject
    lateinit var credentialStore: CredentialStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra("url") ?: ""
        val user = intent.getStringExtra("user") ?: ""
        val pass = intent.getStringExtra("pass") ?: ""

        if (url.isNotBlank()) {
            runBlocking {
                credentialStore.saveCredentials(
                    ServerCredentials(serverUrl = url, username = user, password = pass)
                )
            }
            Log.d("DebugSetup", "Credentials saved: url=$url user=$user")
        } else {
            Log.e("DebugSetup", "No URL provided")
        }

        finish()
    }
}
