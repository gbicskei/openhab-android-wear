package org.openhab.habdroid.wear.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.data.repository.VoicePreferenceStore
import javax.inject.Inject

/**
 * Watch-local settings screen.
 * Provides toggles for preferences stored locally on the watch (not synced from phone).
 */
@AndroidEntryPoint
class LocalConfigActivity : ComponentActivity() {

    @Inject lateinit var voicePreferenceStore: VoicePreferenceStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocalConfigScreen(voicePreferenceStore = voicePreferenceStore)
        }
    }
}

@Composable
fun LocalConfigScreen(voicePreferenceStore: VoicePreferenceStore) {
    val ttsEnabled by voicePreferenceStore.voiceResponseSpoken.collectAsState(initial = false)
    val coroutineScope = rememberCoroutineScope()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            ListHeader { Text("Local Config") }
        }
        item {
            SwitchButton(
                checked = ttsEnabled,
                onCheckedChange = { enabled ->
                    coroutineScope.launch {
                        voicePreferenceStore.setVoiceResponseSpoken(enabled)
                    }
                },
                label = { Text("Speak responses") },
                secondaryLabel = { Text("Read voice replies aloud") },
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
        }
    }
}
