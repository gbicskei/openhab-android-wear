package org.openhab.habdroid.wear.phone.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openhab.habdroid.wear.phone.ui.voice.VoiceSettingsContent
import org.openhab.habdroid.wear.phone.ui.voice.VoiceSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    voiceViewModel: VoiceSettingsViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val voiceState by voiceViewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val anyUnsaved = settingsState.hasUnsavedChanges || voiceState.hasUnsavedChanges

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ─── Voice Settings card ───
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    VoiceSettingsContent(viewModel = voiceViewModel)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ─── Assistant Setup card ───
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    AssistantSetupSection()
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ─── Debug card ───
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DebugSection(
                        debugMode = settingsState.debugMode,
                        onDebugModeChanged = settingsViewModel::setDebugMode
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Unified Save button at bottom ───
            Button(
                onClick = {
                    voiceViewModel.save()
                    settingsViewModel.save()
                },
                enabled = anyUnsaved,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
