package org.openhab.habdroid.wear.ui.setup

import android.app.Activity
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import dagger.hilt.android.AndroidEntryPoint

private const val KEY_INPUT = "input"

/**
 * Setup activity for server configuration.
 * Uses Wear OS RemoteInput for text entry (keyboard/voice/handwriting).
 */
@AndroidEntryPoint
class SetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SetupScreen(onSetupComplete = { finish() })
        }
    }
}

@Composable
fun SetupScreen(
    viewModel: SetupViewModel = hiltViewModel(),
    onSetupComplete: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is SetupUiState.ManualEntry -> {
            ManualEntryScreen(
                serverUrl = state.serverUrl,
                username = state.username,
                password = state.password,
                onServerUrlChanged = { viewModel.updateServerUrl(it) },
                onUsernameChanged = { viewModel.updateUsername(it) },
                onPasswordChanged = { viewModel.updatePassword(it) },
                onSave = { viewModel.saveManualCredentials() }
            )
        }

        is SetupUiState.Success -> {
            SuccessScreen(onDone = onSetupComplete)
        }

        is SetupUiState.Error -> {
            ErrorScreen(
                message = state.message,
                onRetry = { viewModel.reset() }
            )
        }
    }
}

@Composable
private fun ManualEntryScreen(
    serverUrl: String,
    username: String,
    password: String,
    onServerUrlChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSave: () -> Unit
) {
    val urlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val input = RemoteInput.getResultsFromIntent(result.data)?.getCharSequence(KEY_INPUT)
            if (input != null) onServerUrlChanged(input.toString())
        }
    }

    val userLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val input = RemoteInput.getResultsFromIntent(result.data)?.getCharSequence(KEY_INPUT)
            if (input != null) onUsernameChanged(input.toString())
        }
    }

    val passLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val input = RemoteInput.getResultsFromIntent(result.data)?.getCharSequence(KEY_INPUT)
            if (input != null) onPasswordChanged(input.toString())
        }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            ListHeader {
                Text("openHAB Setup")
            }
        }
        item {
            Button(
                onClick = {
                    urlLauncher.launch(createRemoteInputIntent("Server URL", serverUrl))
                },
                modifier = Modifier.fillMaxWidth(0.9f),
                label = { Text("Server URL") },
                secondaryLabel = {
                    Text(
                        serverUrl.ifEmpty { "Not set" },
                        maxLines = 1
                    )
                }
            )
        }
        item {
            Button(
                onClick = {
                    userLauncher.launch(createRemoteInputIntent("Username (email)", username))
                },
                modifier = Modifier.fillMaxWidth(0.9f),
                label = { Text("Username") },
                secondaryLabel = {
                    Text(
                        username.ifEmpty { "Not set" },
                        maxLines = 1
                    )
                }
            )
        }
        item {
            Button(
                onClick = {
                    passLauncher.launch(createRemoteInputIntent("Password", password))
                },
                modifier = Modifier.fillMaxWidth(0.9f),
                label = { Text("Password") },
                secondaryLabel = {
                    Text(
                        if (password.isEmpty()) "Not set" else "••••••",
                        maxLines = 1
                    )
                }
            )
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        item {
            Button(
                onClick = onSave,
                enabled = serverUrl.isNotBlank() && username.isNotBlank(),
                modifier = Modifier.fillMaxWidth(0.9f),
                label = { Text("Save & Connect") }
            )
        }
    }
}

/** Creates a RemoteInput intent for Wear OS text entry. */
private fun createRemoteInputIntent(label: String, prefill: String): Intent {
    val remoteInput = RemoteInput.Builder(KEY_INPUT)
        .setLabel(label)
        .build()

    return RemoteInputIntentHelper.createActionRemoteInputIntent().apply {
        RemoteInputIntentHelper.putRemoteInputsExtra(this, listOf(remoteInput))
    }
}

@Composable
private fun SuccessScreen(onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = "Success",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Connected!", textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onDone) {
            Text("Done")
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            message,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}
