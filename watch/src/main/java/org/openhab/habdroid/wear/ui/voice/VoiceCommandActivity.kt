package org.openhab.habdroid.wear.ui.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import org.openhab.habdroid.wear.R
import org.openhab.habdroid.wear.ui.components.AppLogoHeader
import org.openhab.habdroid.wear.util.AppLog
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Activity that handles voice command input on the watch.
 * Uses SpeechRecognizer directly for a custom listening UI,
 * then sends the result to the openHAB voice interpreter endpoint.
 *
 * This activity is launched in two ways:
 * 1. Internally from the tile or app navigation (no intent action)
 * 2. By the system via [Intent.ACTION_ASSIST] when the user selects this app
 *    as their default assistant, or via [OpenHabVoiceInteractionSession]
 */
@AndroidEntryPoint
class VoiceCommandActivity : ComponentActivity() {

    private lateinit var viewModel: VoiceCommandViewModel
    private var speechRecognizer: SpeechRecognizer? = null
    private var pendingStart = false

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            viewModel.setError("Microphone permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == Intent.ACTION_ASSIST) {
            AppLog.d(TAG, "Launched via ASSIST action")
        }

        pendingStart = true

        setContent {
            val vm: VoiceCommandViewModel = hiltViewModel()
            viewModel = vm

            // Start listening once the viewModel is available
            LaunchedEffect(Unit) {
                if (pendingStart) {
                    pendingStart = false
                    requestMicAndListen()
                }
            }

            VoiceCommandScreen(
                viewModel = vm,
                onStartListening = { requestMicAndListen() },
                onDone = { finish() }
            )
        }
    }

    private fun requestMicAndListen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening() {
        viewModel.setListening()

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            viewModel.setError("Speech recognition not available")
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    AppLog.d(TAG, "Ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    AppLog.d(TAG, "Speech started")
                    viewModel.setSpeaking()
                }

                override fun onRmsChanged(rmsdB: Float) {
                    viewModel.updateRmsLevel(rmsdB)
                }

                override fun onEndOfSpeech() {
                    AppLog.d(TAG, "Speech ended")
                    viewModel.setProcessing()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()
                    if (text != null) {
                        AppLog.d(TAG, "Recognized: $text")
                        viewModel.sendVoiceCommand(text)
                    } else {
                        viewModel.setError("No speech recognized")
                    }
                }

                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
                        else -> "Recognition error ($error)"
                    }
                    AppLog.d(TAG, "Speech error: $message")
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        // No speech — just finish
                        finish()
                    } else {
                        viewModel.setError(message)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (partial != null) {
                        viewModel.setPartialResult(partial)
                    }
                }

                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.startListening(recognizerIntent)
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
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

    // Auto-dismiss: immediately when TTS finishes, or after 2s if read-aloud is disabled
    val successState = uiState as? VoiceUiState.Success
    if (successState != null && !successState.isSpeaking) {
        LaunchedEffect(Unit) {
            if (!successState.ttsUsed) {
                delay(2000L)
            }
            onDone()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppLogoHeader(showIndicator = false)

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
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

            is VoiceUiState.Listening -> {
                ListeningIndicator(rmsLevel = state.rmsLevel)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    state.partialText.ifBlank { "Listening..." },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            is VoiceUiState.Processing -> {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Processing...", textAlign = TextAlign.Center)
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
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    state.responseText,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onDone) {
                    Text("OK")
                }
            }

            is VoiceUiState.Error -> {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Error",
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    state.message,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onStartListening) {
                    Text("Retry")
                }
            }
        }
    }
    }
}

/**
 * Animated indicator that pulses based on voice input level (RMS dB).
 * Shows the openHAB logo inside a reactive ring.
 */
@Composable
fun ListeningIndicator(rmsLevel: Float) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)

    // Pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Normalize RMS (typically -2 to 10 dB)
    val normalizedLevel = ((rmsLevel + 2f) / 12f).coerceIn(0f, 1f)
    val ringRadius = 48f + (normalizedLevel * 24f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(120.dp)
    ) {
        // Outer pulsing ring based on voice level
        Canvas(modifier = Modifier.size(120.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(
                color = primaryColor.copy(alpha = 0.3f * normalizedLevel + 0.1f),
                radius = ringRadius * pulseScale,
                center = center,
                style = Stroke(width = 4.dp.toPx())
            )
            drawCircle(
                color = surfaceColor,
                radius = 36f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // openHAB logo filling the inner ring area
        Image(
            painter = painterResource(R.drawable.ic_wearoh_logo),
            contentDescription = "openHAB listening",
            modifier = Modifier.size(56.dp)
        )
    }
}
