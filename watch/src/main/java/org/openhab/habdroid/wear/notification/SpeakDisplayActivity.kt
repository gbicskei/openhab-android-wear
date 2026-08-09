package org.openhab.habdroid.wear.notification

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import org.openhab.habdroid.wear.R

/**
 * Lightweight Activity that displays the notification message text
 * on the watch screen while TTS or audio is playing.
 *
 * Shows the openHAB logo, title, and message. Auto-finishes when
 * [NotificationHandler] sends the dismiss broadcast after playback completes.
 */
class SpeakDisplayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "openHAB"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""

        // Keep screen on while displaying the message
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Turn screen on if off (for alert-type messages)
        setTurnScreenOn(true)
        setShowWhenLocked(true)

        setContent {
            SpeakDisplayScreen(title = title, message = message)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_DISMISS, false)) {
            finish()
        }
    }

    @Composable
    private fun SpeakDisplayScreen(title: String, message: String) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_openhab_logo),
                contentDescription = "openHAB",
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (title.isNotBlank() && title != "openHAB" && title != "MobileAudio") {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_DISMISS = "dismiss"

        /** Create an intent to show the speak display. */
        fun createIntent(context: Context, title: String, message: String): Intent {
            return Intent(context, SpeakDisplayActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }

        /** Create an intent to dismiss the speak display. */
        fun createDismissIntent(context: Context): Intent {
            return Intent(context, SpeakDisplayActivity::class.java).apply {
                putExtra(EXTRA_DISMISS, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }
}
