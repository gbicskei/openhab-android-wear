package org.openhab.habdroid.wear.util

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    var debugMode: Boolean = false

    /** Log debug message (only in debug builds). */
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    /** Log info message (only in debug builds). */
    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message)
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
