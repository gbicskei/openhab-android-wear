package org.openhab.habdroid.wear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.data.repository.NotificationPreferenceStore
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SettingsScreen() }
    }
}

@HiltViewModel
class WatchSettingsViewModel @Inject constructor(
    private val notificationPrefs: NotificationPreferenceStore
) : ViewModel() {

    val readAloudEnabled = notificationPrefs.readAloudEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val chimeEnabled = notificationPrefs.chimeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun toggleReadAloud(enabled: Boolean) {
        viewModelScope.launch { notificationPrefs.setReadAloudEnabled(enabled) }
    }

    fun toggleChime(enabled: Boolean) {
        viewModelScope.launch { notificationPrefs.setChimeEnabled(enabled) }
    }
}

@Composable
fun SettingsScreen(viewModel: WatchSettingsViewModel = hiltViewModel()) {
    val readAloud by viewModel.readAloudEnabled.collectAsState()
    val chime by viewModel.chimeEnabled.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { ListHeader { Text("Notifications") } }
        item {
            SwitchButton(
                checked = readAloud,
                onCheckedChange = { viewModel.toggleReadAloud(it) },
                label = { Text("Read Aloud") }
            )
        }
        item {
            SwitchButton(
                checked = chime,
                onCheckedChange = { viewModel.toggleChime(it) },
                enabled = readAloud,
                label = { Text("Alert Sound") }
            )
        }
    }
}
