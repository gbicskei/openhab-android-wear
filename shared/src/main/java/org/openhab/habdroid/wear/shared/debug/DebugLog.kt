package org.openhab.habdroid.wear.shared.debug

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Source of a debug log entry.
 */
enum class LogSource {
    WATCH, PHONE
}

/**
 * Severity level.
 */
enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

/**
 * A single debug log entry.
 */
@Serializable
data class DebugLogEntry(
    val timestamp: Long,
    val source: LogSource,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
}

/**
 * Thread-safe in-memory ring buffer for debug log entries.
 * Captures warnings and errors from both watch and phone for display in a Debug screen.
 *
 * Keeps entries from the last [RETENTION_MS] (5 minutes); older entries are pruned on each append.
 * A hard cap of [MAX_ENTRIES] prevents unbounded growth during high-frequency logging.
 */
object DebugLog {
    private const val RETENTION_MS = 5 * 60 * 1000L // 5 minutes
    private const val MAX_ENTRIES = 5000

    private val buffer = ConcurrentLinkedDeque<DebugLogEntry>()

    /** Log a debug message. */
    fun d(source: LogSource, tag: String, message: String) {
        append(DebugLogEntry(
            timestamp = System.currentTimeMillis(),
            source = source,
            level = LogLevel.DEBUG,
            tag = tag,
            message = message
        ))
    }

    /** Log an info message. */
    fun i(source: LogSource, tag: String, message: String) {
        append(DebugLogEntry(
            timestamp = System.currentTimeMillis(),
            source = source,
            level = LogLevel.INFO,
            tag = tag,
            message = message
        ))
    }

    /** Log a warning. */
    fun w(source: LogSource, tag: String, message: String, throwable: Throwable? = null) {
        append(DebugLogEntry(
            timestamp = System.currentTimeMillis(),
            source = source,
            level = LogLevel.WARN,
            tag = tag,
            message = message,
            stackTrace = throwable?.stackTraceToString()
        ))
    }

    /** Log an error. */
    fun e(source: LogSource, tag: String, message: String, throwable: Throwable? = null) {
        append(DebugLogEntry(
            timestamp = System.currentTimeMillis(),
            source = source,
            level = LogLevel.ERROR,
            tag = tag,
            message = message,
            stackTrace = throwable?.stackTraceToString()
        ))
    }

    /** Get a snapshot of all entries (newest last). */
    fun entries(): List<DebugLogEntry> = buffer.toList()

    /** Clear all entries. */
    fun clear() {
        buffer.clear()
    }

    /** Add entries received from the remote side (watch→phone sync). Deduplicates by timestamp+tag+message. */
    fun addRemoteEntries(entries: List<DebugLogEntry>) {
        val existing = buffer.map { Triple(it.timestamp, it.tag, it.message) }.toSet()
        entries.forEach { entry ->
            if (Triple(entry.timestamp, entry.tag, entry.message) !in existing) {
                append(entry)
            }
        }
    }

    private fun append(entry: DebugLogEntry) {
        buffer.addLast(entry)
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        while (buffer.peekFirst()?.let { it.timestamp < cutoff } == true || buffer.size > MAX_ENTRIES) {
            buffer.pollFirst() ?: break
        }
    }
}
