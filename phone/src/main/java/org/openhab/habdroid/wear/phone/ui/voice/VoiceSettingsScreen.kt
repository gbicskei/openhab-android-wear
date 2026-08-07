package org.openhab.habdroid.wear.phone.ui.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Voice settings content — embedded in the General Settings screen.
 * Saving is handled by the parent screen's unified Save button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsContent(viewModel: VoiceSettingsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // ─── Voice Settings (parent section) ───
    SectionTitle("Voice Settings")

    SettingsToggle(
        label = "Enable voice commands",
        subtitle = if (!uiState.ohVersionSupported)
            "Requires openHAB 5.2.1+ (detected: ${uiState.ohVersion ?: "unknown"})"
        else if (uiState.ohVersion != null)
            "openHAB ${uiState.ohVersion}"
        else null,
        checked = uiState.voiceCommandsEnabled,
        onCheckedChange = viewModel::onVoiceCommandsEnabledChanged,
        enabled = uiState.ohVersionSupported
    )

    if (!uiState.ohVersionSupported) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Voice interpreter requires openHAB 5.2.1 or later",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    AnimatedVisibility(visible = uiState.voiceCommandsEnabled) {
        Column {
            Spacer(modifier = Modifier.height(12.dp))

            SettingsToggle(
                label = "Read responses aloud",
                subtitle = "Speak the voice command response on the watch",
                checked = uiState.readAloudEnabled,
                onCheckedChange = viewModel::onReadAloudChanged
            )

            AnimatedVisibility(visible = uiState.readAloudEnabled) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Volume slider
                    SliderSetting(
                        label = "Volume",
                        value = uiState.volume,
                        onValueChange = viewModel::onVolumeChanged,
                        valueRange = 0f..1f,
                        valueLabel = "${(uiState.volume * 100).toInt()}%"
                    )

                    // ─── Server TTS (Google WaveNet) ───
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingsToggle(
                        label = "Use Google WaveNet voices",
                        subtitle = if (uiState.hasGoogleTtsKey)
                            "High-quality server-side speech synthesis"
                        else
                            "Requires Google Cloud TTS API key (Connection settings)",
                        checked = uiState.useServerTts,
                        onCheckedChange = viewModel::onUseServerTtsChanged,
                        enabled = uiState.hasGoogleTtsKey
                    )

                    AnimatedVisibility(visible = uiState.useServerTts && uiState.hasGoogleTtsKey) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            VoicePicker(
                                selectedVoice = uiState.selectedVoice,
                                voices = uiState.availableVoices,
                                loading = uiState.voicesLoading,
                                onVoiceSelected = viewModel::onVoiceSelected
                            )
                        }
                    }

                    AnimatedVisibility(visible = !uiState.useServerTts || !uiState.hasGoogleTtsKey) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            SystemTtsSettings(
                                speechRate = uiState.speechRate,
                                pitch = uiState.pitch,
                                onSpeechRateChanged = viewModel::onSpeechRateChanged,
                                onPitchChanged = viewModel::onPitchChanged
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(onClick = { viewModel.testVoice() }) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test voice")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingsToggle(
    label: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(text = valueLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

@Composable
private fun SystemTtsSettings(
    speechRate: Float,
    pitch: Float,
    onSpeechRateChanged: (Float) -> Unit,
    onPitchChanged: (Float) -> Unit
) {
    Column {
        SliderSetting(
            label = "Speech rate",
            value = speechRate,
            onValueChange = onSpeechRateChanged,
            valueRange = 0.25f..2.0f,
            valueLabel = "%.1fx".format(speechRate)
        )
        Spacer(modifier = Modifier.height(4.dp))
        SliderSetting(
            label = "Pitch",
            value = pitch,
            onValueChange = onPitchChanged,
            valueRange = 0.25f..2.0f,
            valueLabel = "%.1fx".format(pitch)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoicePicker(
    selectedVoice: String,
    voices: List<VoiceOption>,
    loading: Boolean,
    onVoiceSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = voices.find { it.id == selectedVoice }?.label ?: selectedVoice.ifBlank { "Select a voice" }

    if (loading) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Loading voices...", style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    if (voices.isEmpty()) {
        Text(
            "No voices loaded. Check API key in Connection settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Voice") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            voices.forEach { voice ->
                DropdownMenuItem(
                    text = { Text(voice.label) },
                    onClick = {
                        onVoiceSelected(voice.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
