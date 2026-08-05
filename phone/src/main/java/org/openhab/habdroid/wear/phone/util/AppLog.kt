package org.openhab.habdroid.wear.phone.util

import android.util.Log
import org.openhab.habdroid.wear.phone.BuildConfig
import org.openhab.habdroid.wear.shared.debug.DebugLog
import org.openhab.habdroid.wear.shared.debug.LogSource

/**
 * Centralized logging utility. Debug/verbose logs are stripped in release builds.
 * Warnings and errors always log and are captured in [DebugLog] for the Debug screen.
 */
object AppLog {
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

    /** Log warning message (always). */
    fun w(tag: String, message: String) {
        Log.w(tag, message)
        DebugLog.w(LogSource.PHONE, tag, message)
    }

    /** Log warning message with throwable (always). */
    fun w(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
        DebugLog.w(LogSource.PHONE, tag, message, throwable)
    }

    /** Log error message (always). */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
        DebugLog.e(LogSource.PHONE, tag, message, throwable)
    }
}
