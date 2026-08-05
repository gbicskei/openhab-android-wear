package org.openhab.habdroid.wear.phone.ui.debug

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.openhab.habdroid.wear.phone.sync.DebugLogReader
import org.openhab.habdroid.wear.shared.debug.DebugLog
import org.openhab.habdroid.wear.shared.debug.DebugLogEntry
import javax.inject.Inject

/**
 * ViewModel for the Debug Log screen.
 * Merges phone-local errors with watch errors received via DataLayer.
 */
@HiltViewModel
class DebugLogViewModel @Inject constructor(
    private val debugLogReader: DebugLogReader
) : ViewModel() {

    private val _entries = MutableStateFlow<List<DebugLogEntry>>(emptyList())
    val entries: StateFlow<List<DebugLogEntry>> = _entries.asStateFlow()

    fun startListening() {
        debugLogReader.startListening()
        refresh()
    }

    fun stopListening() {
        debugLogReader.stopListening()
    }

    fun clear() {
        DebugLog.clear()
        _entries.value = emptyList()
    }

    fun refresh() {
        _entries.value = DebugLog.entries().sortedBy { it.timestamp }
    }
}
