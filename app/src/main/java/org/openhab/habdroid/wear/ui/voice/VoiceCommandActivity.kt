package org.openhab.habdroid.wear.ui.voice

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

/**
 * Activity that handles voice command input on the watch.
 * Launches the system speech recognizer, captures the result,
 * and sends it to the openHAB voice interpreter endpoint.
 */
@AndroidEntryPoint
class VoiceCommandActivity : ComponentActivity() {

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()

            if (spokenText != null) {
                Log.d(TAG, "Recognized: $spokenText")
                viewModel.sendVoiceCommand(spokenText)
            } else {
                viewModel.setError("No speech recognized")
            }
        } else {
            // User cancelled or recognition failed
            finish()
        }
    }

    private lateinit var viewModel: VoiceCommandViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val vm: VoiceCommandViewModel = hiltViewModel()
            viewModel = vm
            VoiceCommandScreen(
                viewModel = vm,
                onStartListening = { startSpeechRecognition() },
                onDone = { finish() }
            )
        }

        // Immediately launch speech recognition on activity start
        startSpeechRecognition()
    }

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "openHAB command")
        }

        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Speech recognition not available", e)
            viewModel.setError("Speech recognition not available")
        }
    }

    companion object {
        private const val TAG = "VoiceCommandActivity"
    }
}

@Composable
fun VoiceCommandScreen(
    viewModel: VoiceCommandViewModel,
    onStartListening: () -> Unit,
    onDone: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (val state = uiState) {
            is VoiceUiState.Idle -> {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Microphone",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Tap to speak", textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onStartListening) {
                    Text("Listen")
                }
            }

            is VoiceUiState.Sending -> {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "\"${state.command}\"",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Sending...", textAlign = TextAlign.Center)
            }

            is VoiceUiState.Success -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Success",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Done", textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onDone) {
                    Text("OK")
                }
            }

            is VoiceUiState.Error -> {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Error",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    state.message,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onStartListening) {
                    Text("Retry")
                }
            }
        }
    }
}
