package org.openhab.habdroid.wear.phone.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AssistantSetupSection(
    viewModel: AssistantSetupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Watch Voice Assistant",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Use openHAB as the voice assistant on your watch. " +
                "After setup, long-press the Home button to send voice commands to openHAB.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ─── Check status ───
        OutlinedButton(
            onClick = viewModel::test,
            enabled = !state.isTesting
        ) {
            if (state.isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.height(16.dp).width(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Check watch status")
        }

        state.testResult?.let { result ->
            Text(
                text = when {
                    result.hasPermission && result.isRegistered ->
                        "\u2705 Assistant configured correctly. Long-press Home button to use."
                    result.hasPermission && !result.isRegistered ->
                        "\u26A0\uFE0F Permission granted but not registered. Relaunch the openHAB watch app."
                    else ->
                        "\u274C Setup needed \u2014 follow the instructions below."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (result.hasPermission && result.isRegistered)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        state.statusMessage?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // ─── Setup instructions ───
        Text(
            text = "One-time setup (from a PC)",
            style = MaterialTheme.typography.titleSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This requires a one-time ADB command from a computer. " +
                "You already have ADB if you sideloaded the watch app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "1. Connect your watch to the PC via ADB:",
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            text = "\u2022 On watch: Settings \u2192 About watch \u2192 tap Software version 5 times\n" +
                "\u2022 Settings \u2192 Developer options \u2192 enable ADB debugging\n" +
                "\u2022 Enable Wireless debugging \u2192 Pair new device\n" +
                "\u2022 On PC: adb pair <ip>:<pairing-port> <code>\n" +
                "\u2022 Then: adb connect <ip>:<port>",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "2. Run the setup command:",
            style = MaterialTheme.typography.labelLarge
        )

        Spacer(modifier = Modifier.height(4.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        ) {
            SelectionContainer {
                Text(
                    text = "adb shell pm grant org.openhab.habdroid.wear android.permission.WRITE_SECURE_SETTINGS",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "3. That\u2019s it! The watch app auto-registers on every launch. " +
                "You can disable ADB debugging and disconnect. " +
                "Use \u201CCheck watch status\u201D above to verify.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
