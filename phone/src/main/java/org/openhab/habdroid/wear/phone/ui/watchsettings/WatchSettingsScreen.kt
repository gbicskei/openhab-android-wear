package org.openhab.habdroid.wear.phone.ui.watchsettings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchSettingsScreen(
    onBack: () -> Unit,
    viewModel: WatchSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val watchDisconnected by viewModel.watchDisconnected.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current

    // Auto-navigate back if watch disconnects
    LaunchedEffect(watchDisconnected) {
        if (watchDisconnected) {
            Toast.makeText(context, "Watch disconnected", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Watch Settings")
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.watchConnected) Color(0xFF4CAF50) else Color(0xFFF44336)
                                )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { paddingValues ->
        when (state.loadState) {
            LoadState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Reading settings from watch...")
                    }
                }
            }

            LoadState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            state.errorMessage ?: "Failed to load settings",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadSettingsFromWatch() }) {
                            Text("Retry")
                        }
                    }
                }
            }

            LoadState.Loaded -> {
                WatchSettingsContent(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

@Composable
private fun WatchSettingsContent(
    state: WatchSettingsUiState,
    viewModel: WatchSettingsViewModel,
    modifier: Modifier = Modifier
) {
    val snapshot = state.snapshot
    val backupEnabled = state.backupEnabled
    val googleTtsAvailable = state.googleTtsAvailable
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(8.dp))

        // ─── Voice Settings ───
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Voice", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))

                SettingSwitch(
                    label = "Voice Commands",
                    checked = snapshot.voiceCommandsEnabled,
                    onCheckedChange = viewModel::setVoiceCommandsEnabled
                )
                SettingSwitch(
                    label = "Read Aloud",
                    checked = snapshot.readAloudEnabled,
                    onCheckedChange = viewModel::setReadAloudEnabled
                )
                SettingSwitch(
                    label = "Google TTS",
                    checked = snapshot.useServerTts,
                    enabled = snapshot.readAloudEnabled && googleTtsAvailable,
                    onCheckedChange = viewModel::setUseServerTts
                )
                if (snapshot.useServerTts && googleTtsAvailable) {
                    Spacer(modifier = Modifier.height(8.dp))
                    VoicePickerDropdown(
                        selectedVoice = snapshot.serverTtsVoice,
                        voices = state.availableVoices,
                        loading = state.voicesLoading,
                        onVoiceSelected = viewModel::selectVoice
                    )
                }
                if (!snapshot.useServerTts) {
                    SettingSlider(
                        label = "Speech Rate",
                        value = snapshot.ttsSpeechRate,
                        onValueChange = viewModel::setTtsSpeechRate,
                        valueRange = 0.5f..2.0f,
                        valueLabel = "${String.format("%.1f", snapshot.ttsSpeechRate)}x"
                    )
                    SettingSlider(
                        label = "Pitch",
                        value = snapshot.ttsPitch,
                        onValueChange = viewModel::setTtsPitch,
                        valueRange = 0.5f..2.0f,
                        valueLabel = "${String.format("%.1f", snapshot.ttsPitch)}x"
                    )
                }
                SettingSlider(
                    label = "Volume",
                    value = snapshot.ttsVolume,
                    onValueChange = viewModel::setTtsVolume,
                    valueRange = 0f..1f,
                    valueLabel = "${(snapshot.ttsVolume * 100).toInt()}%"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.testVoice() },
                    enabled = !state.testPlaying && snapshot.readAloudEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.testPlaying) "Playing..." else "Test Voice")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ─── Notification Settings ───
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Notifications", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("(Experimental)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (!state.bindingInstalled) {
                    Text(
                        "Install the Mobile Audio binding on your openHAB server to enable push notifications to the watch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    SettingSwitch(
                        label = "Enabled",
                        checked = snapshot.notificationsEnabled,
                        onCheckedChange = viewModel::setNotificationsEnabled
                    )
                    SettingSwitch(
                        label = "Read Aloud",
                        checked = snapshot.notificationReadAloud,
                        enabled = snapshot.notificationsEnabled,
                        onCheckedChange = viewModel::setNotificationReadAloud
                    )
                    SettingSwitch(
                        label = "Alert Sound",
                        checked = snapshot.chimeEnabled,
                        enabled = snapshot.notificationsEnabled && snapshot.notificationReadAloud,
                        onCheckedChange = viewModel::setChimeEnabled
                    )
                    SettingSlider(
                        label = "Volume",
                        value = snapshot.notificationVolume,
                        onValueChange = viewModel::setNotificationVolume,
                        valueRange = 0f..1f,
                        enabled = snapshot.notificationsEnabled,
                        valueLabel = "${(snapshot.notificationVolume * 100).toInt()}%"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ─── Debug ───
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Debug", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))

                SettingSwitch(
                    label = "Debug Mode",
                    checked = snapshot.debugMode,
                    onCheckedChange = viewModel::setDebugMode
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ─── Backup & Restore ───
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Server Backup", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))

                SettingSwitch(
                    label = "Enable Backup",
                    checked = backupEnabled,
                    onCheckedChange = viewModel::setBackupEnabled
                )

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.restoreFromBackup() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restore from Backup")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    enabled: Boolean = true
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Text(
                valueLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoicePickerDropdown(
    selectedVoice: String,
    voices: List<String>,
    loading: Boolean,
    onVoiceSelected: (String) -> Unit
) {
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
            "No voices available",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }
    val displayLabel = selectedVoice.ifBlank { "Select a voice" }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = displayLabel,
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
                    text = { Text(voice) },
                    onClick = {
                        onVoiceSelected(voice)
                        expanded = false
                    }
                )
            }
        }
    }
}
