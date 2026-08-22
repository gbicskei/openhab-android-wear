package org.openhab.habdroid.wear.phone.ui.watchsettings

import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Check
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
    val context = LocalContext.current

    // Track which sub-screen is showing (null = overview)
    var currentSection by remember { mutableStateOf<SettingsSection?>(null) }

    // Auto-navigate back if watch disconnects
    LaunchedEffect(watchDisconnected) {
        if (watchDisconnected) {
            Toast.makeText(context, "Watch disconnected", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val title = when (currentSection) {
        SettingsSection.VOICE -> "Voice"
        SettingsSection.NOTIFICATIONS -> "Notifications"
        SettingsSection.MISC -> "Misc"
        SettingsSection.THEME -> "Theme"
        null -> "Watch Settings"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title)
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
                    IconButton(onClick = {
                        if (currentSection != null) currentSection = null else onBack()
                    }) {
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
                when (currentSection) {
                    null -> SettingsOverview(
                        state = state,
                        onSectionSelected = { currentSection = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 16.dp)
                    )
                    SettingsSection.VOICE -> VoiceSettingsContent(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState())
                    )
                    SettingsSection.NOTIFICATIONS -> NotificationSettingsContent(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState())
                    )
                    SettingsSection.MISC -> MiscSettingsContent(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState())
                    )
                    SettingsSection.THEME -> ThemeSettingsContent(
                        state = state,
                        viewModel = viewModel,
                        onDone = { currentSection = null },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

private enum class SettingsSection { VOICE, NOTIFICATIONS, MISC, THEME }

@Composable
private fun SettingsOverview(
    state: WatchSettingsUiState,
    onSectionSelected: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(top = 8.dp)) {
        SettingsCategoryCard(
            title = "Voice",
            subtitle = if (state.snapshot.voiceCommandsEnabled) "Commands enabled" else "Commands disabled",
            onClick = { onSectionSelected(SettingsSection.VOICE) }
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCategoryCard(
            title = "Notifications",
            subtitle = if (!state.bindingInstalled) "Binding not installed"
                else if (state.snapshot.notificationsEnabled) "Enabled" else "Disabled",
            onClick = { onSectionSelected(SettingsSection.NOTIFICATIONS) }
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCategoryCard(
            title = "Theme",
            subtitle = state.selectedTheme.ifBlank { "Amber" }.replaceFirstChar { it.uppercase() },
            onClick = { onSectionSelected(SettingsSection.THEME) }
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCategoryCard(
            title = "Misc",
            subtitle = buildString {
                if (state.snapshot.debugMode) append("Debug on")
                else append("Debug off")
                if (state.backupEnabled) append(" · Backup on")
            },
            onClick = { onSectionSelected(SettingsSection.MISC) }
        )
    }
}

@Composable
private fun SettingsCategoryCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VoiceSettingsContent(
    state: WatchSettingsUiState,
    viewModel: WatchSettingsViewModel,
    modifier: Modifier = Modifier
) {
    val snapshot = state.snapshot
    val googleTtsAvailable = state.googleTtsAvailable
    val hasSpeaker = state.watchHasSpeaker

    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(8.dp))

        SettingSwitch(
            label = "Voice Commands",
            checked = snapshot.voiceCommandsEnabled,
            onCheckedChange = viewModel::setVoiceCommandsEnabled
        )
        if (hasSpeaker) {
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
                    value = snapshot.speechRate,
                    onValueChange = viewModel::setTtsSpeechRate,
                    valueRange = 0.5f..2.0f,
                    valueLabel = "${String.format("%.1f", snapshot.speechRate)}x"
                )
                SettingSlider(
                    label = "Pitch",
                    value = snapshot.pitch,
                    onValueChange = viewModel::setTtsPitch,
                    valueRange = 0.5f..2.0f,
                    valueLabel = "${String.format("%.1f", snapshot.pitch)}x"
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.testVoice() },
                enabled = !state.testPlaying && snapshot.readAloudEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.testPlaying) "Playing..." else "Test Voice")
            }
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Audio response settings unavailable — watch has no speaker.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun NotificationSettingsContent(
    state: WatchSettingsUiState,
    viewModel: WatchSettingsViewModel,
    modifier: Modifier = Modifier
) {
    val snapshot = state.snapshot
    val hasSpeaker = state.watchHasSpeaker

    Column(modifier = modifier) {
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
            if (hasSpeaker) {
                SettingSwitch(
                    label = "Read Aloud",
                    checked = snapshot.notificationReadAloudEnabled,
                    enabled = snapshot.notificationsEnabled,
                    onCheckedChange = viewModel::setNotificationReadAloud
                )
                SettingSwitch(
                    label = "Alert Sound",
                    checked = snapshot.chimeEnabled,
                    enabled = snapshot.notificationsEnabled && snapshot.notificationReadAloudEnabled,
                    onCheckedChange = viewModel::setChimeEnabled
                )
                PriorityDropdown(
                    label = "Read Priority",
                    selected = snapshot.minReadAloudPriority,
                    enabled = snapshot.notificationsEnabled && snapshot.notificationReadAloudEnabled,
                    onSelected = viewModel::setMinReadAloudPriority
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Audio settings unavailable — watch has no speaker.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun MiscSettingsContent(
    state: WatchSettingsUiState,
    viewModel: WatchSettingsViewModel,
    modifier: Modifier = Modifier
) {
    val snapshot = state.snapshot

    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(8.dp))

        SettingSwitch(
            label = "Debug Mode",
            checked = snapshot.debugMode,
            onCheckedChange = viewModel::setDebugMode
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingSwitch(
            label = "Server Backup",
            checked = state.backupEnabled,
            onCheckedChange = viewModel::setBackupEnabled
        )

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.restoreFromBackup() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Restore from Backup")
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ThemeSettingsContent(
    state: WatchSettingsUiState,
    viewModel: WatchSettingsViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themes = listOf("AMBER", "BLUE", "GREEN", "PURPLE", "RED")
    val displayNames = mapOf(
        "AMBER" to "Amber",
        "BLUE" to "Blue",
        "GREEN" to "Green",
        "PURPLE" to "Purple",
        "RED" to "Red"
    )
    val colors = mapOf(
        "AMBER" to Color(0xFFFFB950),
        "BLUE" to Color(0xFFA8C8FF),
        "GREEN" to Color(0xFF8AD88E),
        "PURPLE" to Color(0xFFD4BBFF),
        "RED" to Color(0xFFFFB4AB)
    )

    Column(modifier = modifier.padding(top = 8.dp)) {
        themes.forEach { theme ->
            val isSelected = theme == state.selectedTheme
            ElevatedCard(
                onClick = {
                    viewModel.setTheme(theme)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                elevation = CardDefaults.elevatedCardElevation(
                    defaultElevation = if (isSelected) 4.dp else 1.dp
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Canvas(modifier = Modifier.size(24.dp)) {
                        drawCircle(color = colors[theme] ?: Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = displayNames[theme] ?: theme,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
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
private fun PriorityDropdown(
    label: String,
    selected: String,
    enabled: Boolean,
    onSelected: (String) -> Unit
) {
    val options = listOf("low", "normal", "high")
    val displayLabels = mapOf("low" to "Low", "normal" to "Normal", "high" to "High")
    var expanded by remember { mutableStateOf(false) }

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
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (enabled) expanded = it }
        ) {
            OutlinedTextField(
                value = displayLabels[selected] ?: "Normal",
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                modifier = Modifier
                    .width(140.dp)
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                singleLine = true
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(displayLabels[option] ?: option) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
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
                    text = {
                        Text(
                            voice,
                            fontWeight = if (voice == selectedVoice) androidx.compose.ui.text.font.FontWeight.Bold else null,
                            color = if (voice == selectedVoice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingIcon = if (voice == selectedVoice) {
                        { androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary
                        ) }
                    } else null,
                    onClick = {
                        onVoiceSelected(voice)
                        expanded = false
                    }
                )
            }
        }
    }
}
