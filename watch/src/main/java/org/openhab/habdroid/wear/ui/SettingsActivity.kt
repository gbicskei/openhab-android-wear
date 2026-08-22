package org.openhab.habdroid.wear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
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
import org.openhab.habdroid.wear.data.repository.ThemeStore
import org.openhab.habdroid.wear.data.repository.TileTheme
import org.openhab.habdroid.wear.data.repository.VoicePreferenceStore
import org.openhab.habdroid.wear.ui.components.AppLogoHeader
import org.openhab.habdroid.wear.ui.theme.WearOHTheme
import org.openhab.habdroid.wear.util.AppLog
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    @Inject
    lateinit var themeStore: ThemeStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearOHTheme(themeFlow = themeStore.theme) { AppScaffold { SettingsNavHost() } } }
    }
}

private object SettingsRoutes {
    const val MAIN = "settings_main"
    const val VOICE = "settings_voice"
    const val VOICE_PICKER = "settings_voice_picker"
    const val NOTIFICATIONS = "settings_notifications"
    const val THEME = "settings_theme"
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
                onNotifications = { navController.navigate(SettingsRoutes.NOTIFICATIONS) },
                onTheme = { navController.navigate(SettingsRoutes.THEME) }
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
        composable(SettingsRoutes.THEME) {
            ThemeSettingsScreen(
                viewModel = viewModel,
                onSelected = { navController.popBackStack() }
            )
        }
    }
}

// ─── Main Settings (top level) ───

@Composable
private fun SettingsMainScreen(
    viewModel: WatchSettingsViewModel,
    onVoice: () -> Unit,
    onNotifications: () -> Unit,
    onTheme: () -> Unit
) {
    val debugMode by viewModel.debugMode.collectAsState()
    val bindingInstalled by viewModel.bindingInstalled.collectAsState()
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = columnState) { contentPadding ->
        TransformingLazyColumn(
            state = columnState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 48.dp
            )
        ) {
            item {
                ListHeader { AppLogoHeader() }
            }
            item {
                Button(
                    onClick = onVoice,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    label = { Text("Voice") },
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    transformation = SurfaceTransformation(transformationSpec)
                )
            }
            item {
                Button(
                    onClick = onNotifications,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    label = { Text("Notifications") },
                    icon = {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    enabled = bindingInstalled,
                    transformation = SurfaceTransformation(transformationSpec)
                )
            }
            item {
                Button(
                    onClick = onTheme,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    label = { Text("Theme") },
                    icon = {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    transformation = SurfaceTransformation(transformationSpec)
                )
            }
            item {
                Button(
                    onClick = { /* controlled from phone */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    label = { Text("Debug Mode") },
                    secondaryLabel = { Text(if (debugMode) "On • Set on phone" else "Off • Set on phone") },
                    icon = {
                        Icon(
                            Icons.Default.BugReport,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    transformation = SurfaceTransformation(transformationSpec)
                )
            }
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
    val ttsSpeechRate by viewModel.ttsSpeechRate.collectAsState()
    val testPlaying by viewModel.testPlaying.collectAsState()

    val hasSpeaker = viewModel.hasSpeaker
    val hasApiKey = apiKey.isNotBlank()
    val googleTtsAvailable = hasApiKey && serverOnline
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = columnState) { contentPadding ->
        TransformingLazyColumn(
            state = columnState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 48.dp
            )
        ) {
            item {
                ListHeader { AppLogoHeader() }
            }
            item {
                SwitchButton(
                    checked = voiceCommands,
                    onCheckedChange = { viewModel.toggleVoiceCommands(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    label = { Text("Voice Commands") },
                    transformation = SurfaceTransformation(transformationSpec)
                )
            }
            if (hasSpeaker) {
                item {
                    SwitchButton(
                        checked = readAloud,
                        onCheckedChange = { viewModel.toggleReadAloud(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        label = { Text("Read Aloud") },
                        transformation = SurfaceTransformation(transformationSpec)
                    )
                }
                item {
                    SwitchButton(
                        checked = serverTts,
                        onCheckedChange = { viewModel.toggleServerTts(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        enabled = readAloud && googleTtsAvailable,
                        label = { Text("Google TTS") },
                        transformation = SurfaceTransformation(transformationSpec)
                    )
                }
                if (serverTts && googleTtsAvailable) {
                    item {
                        Button(
                            onClick = onPickVoice,
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                            label = { Text(ttsVoice.ifBlank { "Select voice" }) },
                            transformation = SurfaceTransformation(transformationSpec)
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
                    Button(
                        onClick = { viewModel.testVoice() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        enabled = !testPlaying && readAloud,
                        label = { Text(if (testPlaying) "Playing..." else "Test Voice") },
                        transformation = SurfaceTransformation(transformationSpec)
                    )
                }
            } else {
                item {
                    Text(
                        "Audio response unavailable — no speaker detected",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = androidx.wear.compose.material3.MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

// ─── Voice Picker (sub-sub-screen) ───

@Composable
private fun VoicePickerScreen(viewModel: WatchSettingsViewModel, onBack: () -> Unit) {
    val voices by viewModel.voices.collectAsState()
    val loading by viewModel.voicesLoading.collectAsState()
    val currentVoice by viewModel.serverTtsVoice.collectAsState()
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    // Scroll to the currently selected voice when the list loads
    LaunchedEffect(voices, currentVoice) {
        if (voices.isNotEmpty() && currentVoice.isNotBlank()) {
            val index = voices.indexOf(currentVoice)
            if (index >= 0) {
                columnState.scrollToItem(index + 1) // +1 for header
            }
        }
    }

    ScreenScaffold(scrollState = columnState) { contentPadding ->
        TransformingLazyColumn(
            state = columnState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 48.dp
            )
        ) {
            item {
                ListHeader { AppLogoHeader() }
            }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        label = { Text(voice) },
                        colors = if (isSelected)
                            ButtonDefaults.buttonColors()
                        else
                            ButtonDefaults.filledTonalButtonColors(),
                        transformation = SurfaceTransformation(transformationSpec)
                    )
                }
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
    val minPriority by viewModel.minReadAloudPriority.collectAsState()
    val hasSpeaker = viewModel.hasSpeaker
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = columnState) { contentPadding ->
        TransformingLazyColumn(
            state = columnState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 48.dp
            )
        ) {
            item {
                ListHeader { AppLogoHeader() }
            }
            item {
                SwitchButton(
                    checked = notificationsEnabled,
                    onCheckedChange = { viewModel.toggleNotifications(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    label = { Text("Enabled") },
                    transformation = SurfaceTransformation(transformationSpec)
                )
            }
            if (hasSpeaker) {
                item {
                    SwitchButton(
                        checked = notifReadAloud,
                        onCheckedChange = { viewModel.toggleNotifReadAloud(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        enabled = notificationsEnabled,
                        label = { Text("Read Aloud") },
                        transformation = SurfaceTransformation(transformationSpec)
                    )
                }
                item {
                    SwitchButton(
                        checked = chime,
                        onCheckedChange = { viewModel.toggleChime(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        enabled = notificationsEnabled && notifReadAloud,
                        label = { Text("Alert Sound") },
                        transformation = SurfaceTransformation(transformationSpec)
                    )
                }
                item {
                    Button(
                        onClick = { viewModel.cycleMinReadAloudPriority() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        enabled = notificationsEnabled && notifReadAloud,
                        label = { Text("Min Priority: ${minPriority.replaceFirstChar { it.uppercase() }}") },
                        transformation = SurfaceTransformation(transformationSpec)
                    )
                }
            } else {
                item {
                    Text(
                        "Audio settings unavailable — no speaker detected",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = androidx.wear.compose.material3.MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

// ─── Theme Settings (sub-screen) ───

@Composable
private fun ThemeSettingsScreen(viewModel: WatchSettingsViewModel, onSelected: () -> Unit) {
    val selectedTheme by viewModel.selectedTheme.collectAsState()
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = columnState) { contentPadding ->
        TransformingLazyColumn(
            state = columnState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 48.dp
            )
        ) {
            item {
                ListHeader { Text("Accent Color") }
            }
            items(TileTheme.entries.size) { index ->
                val theme = TileTheme.entries[index]
                val isSelected = theme == selectedTheme
                Button(
                    onClick = {
                        viewModel.selectTheme(theme)
                        onSelected()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    label = { Text(theme.displayName) },
                    colors = if (isSelected)
                        ButtonDefaults.buttonColors()
                    else
                        ButtonDefaults.filledTonalButtonColors(),
                    icon = {
                        androidx.compose.foundation.Canvas(modifier = Modifier.size(16.dp)) {
                            drawCircle(
                                color = androidx.compose.ui.graphics.Color(
                                    theme.color.toLong() or 0xFF000000L
                                )
                            )
                        }
                    },
                    transformation = SurfaceTransformation(transformationSpec)
                )
            }
        }
    }
}

// ─── ViewModel ───

@HiltViewModel
class WatchSettingsViewModel @Inject constructor(
    private val voicePrefs: VoicePreferenceStore,
    private val notificationPrefs: NotificationPreferenceStore,
    private val credentialStore: CredentialStore,
    private val themeStore: ThemeStore,
    private val repository: org.openhab.habdroid.wear.data.repository.OpenHabRepository,
    private val okHttpClient: okhttp3.OkHttpClient,
    private val serverTtsPlayer: org.openhab.habdroid.wear.util.ServerTtsPlayer,
    private val watchStatusWriter: org.openhab.habdroid.wear.sync.WatchStatusWriter,
    private val ttsManager: org.openhab.habdroid.wear.util.TtsManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    companion object {
        private const val TAG = "WatchSettingsVM"
    }

    /** Whether the device has a speaker. Used to hide audio/TTS settings. */
    val hasSpeaker: Boolean get() = ttsManager.hasAudioOutput

    // ─── Theme ───
    val selectedTheme = themeStore.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TileTheme.AMBER)

    fun selectTheme(theme: TileTheme) {
        viewModelScope.launch {
            themeStore.setTheme(theme)
            watchStatusWriter.writeTheme(theme.name)
        }
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

    val ttsSpeechRate = voicePrefs.ttsSpeechRate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    // ─── Notifications ───
    val notificationsEnabled = notificationPrefs.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val notifReadAloudEnabled = notificationPrefs.readAloudEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val chimeEnabled = notificationPrefs.chimeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val minReadAloudPriority = notificationPrefs.minReadAloudPriority
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "normal")

    // ─── Debug ───
    val debugMode = AppLog.debugModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ─── Server connectivity (for Google TTS gate) ───
    private val _serverOnline = MutableStateFlow(false)
    val serverOnline: StateFlow<Boolean> = _serverOnline.asStateFlow()

    // ─── Binding installed (synced from phone) ───
    val bindingInstalled = credentialStore.bindingInstalled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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
                val lang = java.util.Locale.getDefault().language
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
                if (useServer) {
                    val apiKey = voicePrefs.serverTtsApiKey.first()
                    val voice = voicePrefs.serverTtsVoice.first()
                    AppLog.d(TAG, "testVoice: server TTS (voice=$voice)")
                    serverTtsPlayer.setApiKey(apiKey)
                    serverTtsPlayer.speakFromServer("This is a voice test.", voice = voice)
                } else {
                    AppLog.d(TAG, "testVoice: local TTS")
                    // System TTS — fire and wait
                    val rate = voicePrefs.ttsSpeechRate.first()
                    val pitch = voicePrefs.ttsPitch.first()
                    val tts = android.speech.tts.TextToSpeech(appContext, null)
                    kotlinx.coroutines.delay(800) // let engine init
                    tts.setSpeechRate(rate)
                    tts.setPitch(pitch)
                    tts.speak("This is a voice test.", android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "test")
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

    fun cycleMinReadAloudPriority() {
        viewModelScope.launch {
            val current = minReadAloudPriority.value
            val next = when (current) {
                "low" -> "normal"
                "normal" -> "high"
                else -> "low"
            }
            notificationPrefs.setMinReadAloudPriority(next)
        }
    }

    // ─── Debug actions ───

    fun toggleDebugMode(enabled: Boolean) {
        AppLog.debugMode = enabled
        viewModelScope.launch { credentialStore.setDebugMode(enabled) }
    }
}
