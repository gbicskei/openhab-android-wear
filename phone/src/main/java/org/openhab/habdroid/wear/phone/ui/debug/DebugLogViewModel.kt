package org.openhab.habdroid.wear.phone.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.phone.BuildConfig
import org.openhab.habdroid.wear.phone.data.DebugLogPersistence
import org.openhab.habdroid.wear.phone.data.PhoneCredentialStore
import org.openhab.habdroid.wear.phone.sync.DebugLogReader
import org.openhab.habdroid.wear.phone.sync.WatchStatusReader
import org.openhab.habdroid.wear.shared.debug.DebugLog
import org.openhab.habdroid.wear.shared.debug.DebugLogEntry
import javax.inject.Inject

/**
 * ViewModel for the Debug Log screen.
 * Merges phone-local errors with watch errors received via DataLayer.
 * Persists entries to disk with a 24-hour retention window.
 */
@HiltViewModel
class DebugLogViewModel @Inject constructor(
    private val debugLogReader: DebugLogReader,
    private val persistence: DebugLogPersistence,
    private val credentialStore: PhoneCredentialStore,
    private val watchStatusReader: WatchStatusReader
) : ViewModel() {

    private val _entries = MutableStateFlow<List<DebugLogEntry>>(emptyList())
    val entries: StateFlow<List<DebugLogEntry>> = _entries.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    /** How many entries to show initially and per page */
    private val pageSize = 50
    private var loadedCount = pageSize
    private var allEntries: List<DebugLogEntry> = emptyList()

    init {
        // Restore persisted entries from previous sessions
        viewModelScope.launch {
            persistence.load()
            refreshAll()
        }
        // Auto-refresh when watch pushes new entries
        viewModelScope.launch {
            debugLogReader.watchEntries.collect {
                if (!_paused.value) {
                    refreshAll()
                }
            }
        }
    }

    fun togglePaused() {
        val wasPaused = _paused.value
        _paused.value = !wasPaused
        if (wasPaused) {
            // Unpausing — refresh to show everything that arrived while paused
            refreshAll()
        }
    }

    fun startListening() {
        debugLogReader.startListening()
        if (!_paused.value) {
            refreshAll()
        }
    }

    fun stopListening() {
        debugLogReader.stopListening()
        viewModelScope.launch { persistence.save() }
    }

    fun clear() {
        DebugLog.clear()
        allEntries = emptyList()
        loadedCount = pageSize
        _entries.value = emptyList()
        viewModelScope.launch {
            persistence.clear()
            debugLogReader.clearWatchData()
        }
    }

    /** Load more older entries when user scrolls up */
    fun loadMore() {
        if (loadedCount >= allEntries.size) return // nothing more to load
        loadedCount = (loadedCount + pageSize).coerceAtMost(allEntries.size)
        _entries.value = allEntries.takeLast(loadedCount)
    }

    fun refresh() = refreshAll()

    private fun refreshAll() {
        allEntries = DebugLog.entries().sortedBy { it.timestamp }
        // Show the tail (latest entries), capped at loadedCount
        loadedCount = loadedCount.coerceAtMost(allEntries.size).coerceAtLeast(pageSize.coerceAtMost(allEntries.size))
        _entries.value = allEntries.takeLast(loadedCount)
        viewModelScope.launch { persistence.save() }
    }

    /**
     * Build a non-sensitive configuration summary for debug log export header.
     */
    suspend fun getConfigSummary(): String {
        val sb = StringBuilder()
        sb.appendLine("Phone app: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")

        val watchStatus = watchStatusReader.readStatus()
        if (watchStatus != null) {
            sb.appendLine("Watch app: ${watchStatus.appVersion}")
            sb.appendLine("Watch screen: ${watchStatus.screenWidthDp}dp")
            sb.appendLine("Watch theme: ${watchStatus.theme}")
            sb.appendLine("Watch configTimestamp: ${watchStatus.configTimestamp}")
        } else {
            sb.appendLine("Watch: not connected")
        }

        val creds = credentialStore.credentials.first()
        if (creds != null) {
            sb.appendLine("Main server: ${creds.serverUrl}")
            sb.appendLine("Main auth: Basic")
        }

        val local = credentialStore.localConfig.first()
        if (local != null && local.serverUrl.isNotBlank()) {
            sb.appendLine("Config server: ${local.serverUrl}")
            sb.appendLine("Config auth: ${if (local.hasApiToken) "API Token" else if (local.hasAuth) "Basic" else "None"}")
        }

        sb.appendLine("Watch uses config server: ${credentialStore.isWatchUseLocalServer}")
        sb.appendLine("User key: ${credentialStore.currentUserKey.ifBlank { "(none)" }}")
        sb.appendLine("Device name: ${credentialStore.deviceName.ifBlank { "(not set)" }}")
        sb.appendLine("Binding installed: ${credentialStore.isBindingInstalled}")
        sb.appendLine("Google TTS: ${if (credentialStore.hasGoogleTtsApiKey) "configured" else "not configured"}")

        return sb.toString()
    }
}
