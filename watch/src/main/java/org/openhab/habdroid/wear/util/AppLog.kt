package org.openhab.habdroid.wear.util

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.BuildConfig
import org.openhab.habdroid.wear.shared.debug.DebugLog
import org.openhab.habdroid.wear.shared.debug.LogSource

/**
 * Centralized logging utility. Debug/verbose logs are stripped in release builds.
 * Warnings and errors always log and are captured in [DebugLog] for remote debugging.
 */
object AppLog {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Optional callback to publish debug log after error/warning. Set by DI at app startup. */
    var onErrorLogged: (suspend () -> Unit)? = null

    /** Controls whether errors are published to the phone. Synced from phone settings. */
    var debugMode: Boolean
        get() = _debugModeFlow.value
        set(value) { _debugModeFlow.value = value }

    private val _debugModeFlow = MutableStateFlow(false)

    /** Observable flow of debug mode state for Compose UI. */
    val debugModeFlow: StateFlow<Boolean> = _debugModeFlow.asStateFlow()

    /** Debounced publish job for d/i entries */
    private var debouncedPublishJob: kotlinx.coroutines.Job? = null
    private const val DEBOUNCE_MS = 2000L
    private var lastPublishedTimestamp = 0L

    /** Log debug message (only in debug builds, or always when debugMode is on). */
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
        if (debugMode) {
            DebugLog.d(LogSource.WATCH, tag, message)
            scheduleDebouncedPublish()
        }
    }

    /** Log info message (only in debug builds, or always when debugMode is on). */
    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message)
        }
        if (debugMode) {
            DebugLog.i(LogSource.WATCH, tag, message)
            scheduleDebouncedPublish()
        }
    }

    /** Debounced publish — only fires if there are new entries since last publish */
    private fun scheduleDebouncedPublish() {
        if (debouncedPublishJob?.isActive == true) return
        debouncedPublishJob = scope.launch {
            kotlinx.coroutines.delay(DEBOUNCE_MS)
            val latestTimestamp = DebugLog.entries().lastOrNull()?.timestamp ?: 0L
            if (latestTimestamp > lastPublishedTimestamp) {
                lastPublishedTimestamp = latestTimestamp
                onErrorLogged?.invoke()
            }
        }
    }

    /** Tags whose warnings are excluded from DebugLog (too noisy, transient by nature) */
    private val SUPPRESSED_WARN_TAGS = setOf("TileStateSSE")

    /** Log warning message (always). */
    fun w(tag: String, message: String) {
        Log.w(tag, message)
        if (tag !in SUPPRESSED_WARN_TAGS) {
            DebugLog.w(LogSource.WATCH, tag, message)
            schedulePublish()
        }
    }

    /** Log warning message with throwable (always). */
    fun w(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
        if (tag !in SUPPRESSED_WARN_TAGS) {
            DebugLog.w(LogSource.WATCH, tag, message, throwable)
            schedulePublish()
        }
    }

    /** Log error message (always). */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
        DebugLog.e(LogSource.WATCH, tag, message, throwable)
        schedulePublish()
    }

    private fun schedulePublish() {
        if (!debugMode) return
        onErrorLogged?.let { publish ->
            scope.launch { publish() }
        }
    }
}
