package org.openhab.habdroid.wear.phone.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.openhab.habdroid.wear.shared.debug.DebugLog
import org.openhab.habdroid.wear.shared.debug.DebugLogEntry
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists debug log entries to local storage with a 24-hour time window.
 * Entries older than 24 hours are automatically pruned on save.
 *
 * File location: app-private files/debug_log.json
 */
@Singleton
class DebugLogPersistence @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    companion object {
        private const val FILE_NAME = "debug_log.json"
        private const val RETENTION_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    private val file: File get() = File(context.filesDir, FILE_NAME)

    /**
     * Save current DebugLog entries to disk, pruning entries older than 24 hours.
     */
    suspend fun save() = withContext(Dispatchers.IO) {
        try {
            val cutoff = System.currentTimeMillis() - RETENTION_MS
            val entries = DebugLog.entries().filter { it.timestamp >= cutoff }
            val data = json.encodeToString(entries)
            file.writeText(data)
        } catch (_: Exception) {
            // Non-fatal — log persistence is best-effort
        }
    }

    /**
     * Load persisted entries from disk and merge into DebugLog (in-memory buffer).
     * Called on app startup to restore entries from a previous session.
     */
    suspend fun load() = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext
            val data = file.readText()
            if (data.isBlank()) return@withContext

            val cutoff = System.currentTimeMillis() - RETENTION_MS
            val entries = json.decodeFromString<List<DebugLogEntry>>(data)
                .filter { it.timestamp >= cutoff }
            DebugLog.addRemoteEntries(entries)
        } catch (_: Exception) {
            // Non-fatal — if file is corrupt, just ignore
        }
    }

    /**
     * Clear persisted log file.
     */
    suspend fun clear() = withContext(Dispatchers.IO) {
        try { file.delete() } catch (_: Exception) {}
    }
}
