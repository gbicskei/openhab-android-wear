package org.openhab.habdroid.wear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.data.repository.CredentialStore
import org.openhab.habdroid.wear.data.repository.NotificationPreferenceStore
import org.openhab.habdroid.wear.data.repository.VoicePreferenceStore
import org.openhab.habdroid.wear.util.AppLog
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SettingsNavHost() }
    }
}

private object SettingsRoutes {
    const val MAIN = "settings_main"
    const val VOICE = "settings_voice"
    const val VOICE_PICKER = "settings_voice_picker"
    const val NOTIFICATIONS = "settings_notifications"
}

@Composable
fun SettingsNavHost(viewModel: WatchSettingsViewModel = hiltViewModel()) {
    val navController = rememberSwipeDismissableNavController()

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = SettingsRoutes.MAIN
    ) {
        composable(SettingsRoutes.MAIN) {
            SettingsMainScreen(
                viewModel = viewModel,
                onVoice = { navController.navigate(SettingsRoutes.VOICE) },
                onNotifications = { navController.navigate(SettingsRoutes.NOTIFICATIONS) }
            )
        }
        composable(SettingsRoutes.VOICE) {
            VoiceSettingsScreen(
                viewModel = viewModel,
                onPickVoice = {
                    viewModel.loadVoices()
                    navController.navigate(SettingsRoutes.VOICE_PICKER)
                }
            )
        }
        composable(SettingsRoutes.VOICE_PICKER) {
            VoicePickerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(SettingsRoutes.NOTIFICATIONS) {
            NotificationSettingsScreen(viewModel = viewModel)
        }
    }
}

// ─── Main Settings (top level) ───

@Composable
private fun SettingsMainScreen(
    viewModel: WatchSettingsViewModel,
    onVoice: () -> Unit,
    onNotifications: () -> Unit
) {
    val debugMode by viewModel.debugMode.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { ListHeader { Text("Settings") } }

        item {
            Button(
                onClick = onVoice,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Voice") },
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
        }

        item {
            Button(
                onClick = onNotifications,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notifications") },
                icon = {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
        }

        item {
            SwitchButton(
                checked = debugMode,
                onCheckedChange = { viewModel.toggleDebugMode(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Debug Mode") },
                icon = {
                    Icon(
                        Icons.Default.BugReport,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
        }
    }
}

// ─── Voice Settings (sub-screen) ───

@Composable
private fun VoiceSettingsScreen(viewModel: WatchSettingsViewModel, onPickVoice: () -> Unit) {
    val voiceCommands by viewModel.voiceCommandsEnabled.collectAsState()
    val readAloud by viewModel.readAloudEnabled.collectAsState()
    val serverTts by viewModel.serverTtsEnabled.collectAsState()
    val ttsVoice by viewModel.serverTtsVoice.collectAsState()
    val apiKey by viewModel.hasGoogleTtsApiKey.collectAsState()
    val serverOnline by viewModel.serverOnline.collectAsState()
    val ttsVolume by viewModel.ttsVolume.collectAsState()
    val ttsSpeechRate by viewModel.ttsSpeechRate.collectAsState()
    val testPlaying by viewModel.testPlaying.collectAsState()

    val hasApiKey = apiKey.isNotBlank()
    val googleTtsAvailable = hasApiKey && serverOnline

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { ListHeader { Text("Voice") } }
        item {
            SwitchButton(
                checked = voiceCommands,
                onCheckedChange = { viewModel.toggleVoiceCommands(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Voice Commands") }
            )
        }
        item {
            SwitchButton(
                checked = readAloud,
                onCheckedChange = { viewModel.toggleReadAloud(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Read Aloud") }
            )
        }
        item {
            SwitchButton(
                checked = serverTts,
                onCheckedChange = { viewModel.toggleServerTts(it) },
                modifier = Modifier.fillMaxWidth(),
                enabled = readAloud && googleTtsAvailable,
                label = { Text("Google TTS") }
            )
        }
        if (serverTts && googleTtsAvailable) {
            item {
                Button(
                    onClick = onPickVoice,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(ttsVoice.ifBlank { "Select voice" }) }
                )
            }
        }
        if (!serverTts) {
            item {
                Text(
                    "Speed: ${String.format("%.1f", ttsSpeechRate)}x",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item {
                Slider(
                    value = ttsSpeechRate,
                    onValueChange = { viewModel.setTtsSpeechRate(it) },
                    valueRange = 0.5f..2.0f,
                    steps = 5
                )
            }
        }
        item {
            Text(
                "Volume: ${(ttsVolume * 100).toInt()}%",
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        item {
            Slider(
                value = ttsVolume,
                onValueChange = { viewModel.setTtsVolume(it) },
                valueRange = 0f..1f,
                steps = 9
            )
        }
        item {
            Button(
                onClick = { viewModel.testVoice() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !testPlaying && readAloud,
                label = { Text(if (testPlaying) "Playing..." else "Test Voice") }
            )
        }
    }
}

// ─── Voice Picker (sub-sub-screen) ───

@Composable
private fun VoicePickerScreen(viewModel: WatchSettingsViewModel, onBack: () -> Unit) {
    val voices by viewModel.voices.collectAsState()
    val loading by viewModel.voicesLoading.collectAsState()
    val currentVoice by viewModel.serverTtsVoice.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { ListHeader { Text("Select Voice") } }

        if (loading) {
            item { Text("Loading voices...") }
        } else if (voices.isEmpty()) {
            item { Text("No voices available") }
        } else {
            items(voices.size) { index ->
                val voice = voices[index]
                val isSelected = voice == currentVoice
                Button(
                    onClick = {
                        viewModel.selectVoice(voice)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(voice) },
                    colors = if (isSelected)
                        androidx.wear.compose.material3.ButtonDefaults.buttonColors()
                    else
                        androidx.wear.compose.material3.ButtonDefaults.filledTonalButtonColors()
                )
            }
        }
    }
}

// ─── Notification Settings (sub-screen) ───

@Composable
private fun NotificationSettingsScreen(viewModel: WatchSettingsViewModel) {
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val notifReadAloud by viewModel.notifReadAloudEnabled.collectAsState()
    val chime by viewModel.chimeEnabled.collectAsState()
    val notifVolume by viewModel.notificationVolume.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { ListHeader { Text("Notifications") } }
        item {
            SwitchButton(
                checked = notificationsEnabled,
                onCheckedChange = { viewModel.toggleNotifications(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Enabled") }
            )
        }
        item {
            SwitchButton(
                checked = notifReadAloud,
                onCheckedChange = { viewModel.toggleNotifReadAloud(it) },
                modifier = Modifier.fillMaxWidth(),
                enabled = notificationsEnabled,
                label = { Text("Read Aloud") }
            )
        }
        item {
            SwitchButton(
                checked = chime,
                onCheckedChange = { viewModel.toggleChime(it) },
                modifier = Modifier.fillMaxWidth(),
                enabled = notificationsEnabled && notifReadAloud,
                label = { Text("Alert Sound") }
            )
        }
        item {
            Text(
                "Volume: ${(notifVolume * 100).toInt()}%",
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        item {
            Slider(
                value = notifVolume,
                onValueChange = { viewModel.setNotificationVolume(it) },
                valueRange = 0f..1f,
                steps = 9,
                enabled = notificationsEnabled
            )
        }
    }
}

// ─── ViewModel ───

@HiltViewModel
class WatchSettingsViewModel @Inject constructor(
    private val voicePrefs: VoicePreferenceStore,
    private val notificationPrefs: NotificationPreferenceStore,
    private val credentialStore: CredentialStore,
    private val repository: org.openhab.habdroid.wear.data.repository.OpenHabRepository,
    private val okHttpClient: okhttp3.OkHttpClient,
    private val serverTtsPlayer: org.openhab.habdroid.wear.util.ServerTtsPlayer,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    companion object {
        private const val TAG = "WatchSettingsVM"
    }

    // ─── Voice ───
    val voiceCommandsEnabled = voicePrefs.voiceCommandsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val readAloudEnabled = voicePrefs.voiceResponseSpoken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val serverTtsEnabled = voicePrefs.serverTtsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val serverTtsVoice = voicePrefs.serverTtsVoice
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val hasGoogleTtsApiKey = voicePrefs.serverTtsApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val ttsVolume = voicePrefs.ttsVolume
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val ttsSpeechRate = voicePrefs.ttsSpeechRate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    // ─── Notifications ───
    val notificationsEnabled = notificationPrefs.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val notifReadAloudEnabled = notificationPrefs.readAloudEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val chimeEnabled = notificationPrefs.chimeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val notificationVolume = notificationPrefs.notificationVolume
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    // ─── Debug ───
    val debugMode = AppLog.debugModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ─── Server connectivity (for Google TTS gate) ───
    private val _serverOnline = MutableStateFlow(false)
    val serverOnline: StateFlow<Boolean> = _serverOnline.asStateFlow()

    init {
        viewModelScope.launch {
            AppLog.d(TAG, "→ checkServerOnline()")
            val online = repository.ping().isSuccess
            _serverOnline.value = online
            AppLog.d(TAG, "← checkServerOnline() online=$online")
        }
    }

    // ─── Voice actions ───

    fun toggleVoiceCommands(enabled: Boolean) {
        AppLog.d(TAG, "toggleVoiceCommands($enabled)")
        viewModelScope.launch { voicePrefs.setVoiceCommandsEnabled(enabled) }
    }

    fun toggleReadAloud(enabled: Boolean) {
        AppLog.d(TAG, "toggleReadAloud($enabled)")
        viewModelScope.launch { voicePrefs.setVoiceResponseSpoken(enabled) }
    }

    fun toggleServerTts(enabled: Boolean) {
        AppLog.d(TAG, "toggleServerTts($enabled)")
        viewModelScope.launch { voicePrefs.setServerTtsEnabled(enabled) }
    }

    // ─── Google TTS voice loading & selection ───

    private val _voices = MutableStateFlow<List<String>>(emptyList())
    val voices: StateFlow<List<String>> = _voices.asStateFlow()

    private val _voicesLoading = MutableStateFlow(false)
    val voicesLoading: StateFlow<Boolean> = _voicesLoading.asStateFlow()

    private val _testPlaying = MutableStateFlow(false)
    val testPlaying: StateFlow<Boolean> = _testPlaying.asStateFlow()

    fun loadVoices() {
        viewModelScope.launch {
            _voicesLoading.value = true
            AppLog.d(TAG, "→ loadVoices()")
            try {
                val apiKey = voicePrefs.serverTtsApiKey.first()
                if (apiKey.isBlank()) {
                    AppLog.d(TAG, "← loadVoices() no API key")
                    _voices.value = emptyList()
                    return@launch
                }
                val lang = java.util.Locale.getDefault().toLanguageTag()
                val fetchedVoices = fetchGoogleVoices(apiKey, lang)
                _voices.value = fetchedVoices
                AppLog.d(TAG, "← loadVoices() got ${fetchedVoices.size} voices (lang=$lang)")
            } catch (e: Exception) {
                AppLog.d(TAG, "← loadVoices() failed: ${e.message}")
                _voices.value = emptyList()
            } finally {
                _voicesLoading.value = false
            }
        }
    }

    fun selectVoice(voice: String) {
        viewModelScope.launch { voicePrefs.setServerTtsVoice(voice) }
    }

    fun testVoice() {
        viewModelScope.launch {
            _testPlaying.value = true
            AppLog.d(TAG, "→ testVoice()")
            try {
                val useServer = voicePrefs.serverTtsEnabled.first()
                val volume = voicePrefs.ttsVolume.first()
                if (useServer) {
                    val apiKey = voicePrefs.serverTtsApiKey.first()
                    val voice = voicePrefs.serverTtsVoice.first()
                    AppLog.d(TAG, "testVoice: server TTS (voice=$voice)")
                    serverTtsPlayer.setApiKey(apiKey)
                    serverTtsPlayer.speakFromServer("This is a voice test.", voice = voice, volume = volume)
                } else {
                    AppLog.d(TAG, "testVoice: local TTS")
                    // System TTS — fire and wait
                    val rate = voicePrefs.ttsSpeechRate.first()
                    val pitch = voicePrefs.ttsPitch.first()
                    val tts = android.speech.tts.TextToSpeech(appContext, null)
                    kotlinx.coroutines.delay(800) // let engine init
                    tts.setSpeechRate(rate)
                    tts.setPitch(pitch)
                    val params = android.os.Bundle().apply {
                        putFloat(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
                    }
                    tts.speak("This is a voice test.", android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "test")
                    kotlinx.coroutines.delay(3000)
                    tts.shutdown()
                }
                AppLog.d(TAG, "← testVoice() done")
            } catch (e: Exception) {
                AppLog.w(TAG, "Voice test failed: ${e.message}")
            }
            _testPlaying.value = false
        }
    }

    private suspend fun fetchGoogleVoices(apiKey: String, languageCode: String): List<String> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val url = "https://texttospeech.googleapis.com/v1/voices?key=$apiKey&languageCode=$languageCode"
            val request = okhttp3.Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            val nameRegex = """"name"\s*:\s*"([^"]+)"""".toRegex()
            nameRegex.findAll(body)
                .map { it.groupValues[1] }
                .toList()
                .sorted()
        }

    fun setTtsVolume(volume: Float) {
        viewModelScope.launch { voicePrefs.setTtsVolume(volume) }
    }

    fun setTtsSpeechRate(rate: Float) {
        viewModelScope.launch { voicePrefs.setTtsSpeechRate(rate) }
    }

    // ─── Notification actions ───

    fun toggleNotifications(enabled: Boolean) {
        AppLog.d(TAG, "toggleNotifications($enabled)")
        viewModelScope.launch { notificationPrefs.setNotificationsEnabled(enabled) }
    }

    fun toggleNotifReadAloud(enabled: Boolean) {
        AppLog.d(TAG, "toggleNotifReadAloud($enabled)")
        viewModelScope.launch { notificationPrefs.setReadAloudEnabled(enabled) }
    }

    fun toggleChime(enabled: Boolean) {
        AppLog.d(TAG, "toggleChime($enabled)")
        viewModelScope.launch { notificationPrefs.setChimeEnabled(enabled) }
    }

    fun setNotificationVolume(volume: Float) {
        viewModelScope.launch { notificationPrefs.setNotificationVolume(volume) }
    }

    // ─── Debug actions ───

    fun toggleDebugMode(enabled: Boolean) {
        AppLog.debugMode = enabled
        viewModelScope.launch { credentialStore.setDebugMode(enabled) }
    }
}
