package org.openhab.habdroid.wear.util

import android.util.Log
import org.openhab.habdroid.wear.BuildConfig

/**
 * Centralized logging utility. Debug/verbose logs are stripped in release builds.
 * Warnings and errors always log.
 */
object AppLog {
    /** Log debug message (only in debug builds). */
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    /** Log warning message (always). */
    fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    /** Log warning message with throwable (always). */
    fun w(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }

    /** Log error message (always). */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
}
