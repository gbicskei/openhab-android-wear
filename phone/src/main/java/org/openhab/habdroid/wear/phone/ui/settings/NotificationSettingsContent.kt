package org.openhab.habdroid.wear.phone.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun NotificationSettingsContent(viewModel: NotificationSettingsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column {
        Text(
            "Notifications",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Configure how the watch handles incoming push notifications",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        NotificationToggle(
            label = "Read aloud",
            subtitle = "Speak notification messages via TTS on the watch",
            checked = uiState.readAloudEnabled,
            onCheckedChange = viewModel::onReadAloudChanged
        )

        AnimatedVisibility(visible = uiState.readAloudEnabled) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                NotificationToggle(
                    label = "Alert sound",
                    subtitle = "Play a chime before reading the notification",
                    checked = uiState.chimeEnabled,
                    onCheckedChange = viewModel::onChimeChanged
                )
            }
        }
    }
}

@Composable
private fun NotificationToggle(
    label: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
