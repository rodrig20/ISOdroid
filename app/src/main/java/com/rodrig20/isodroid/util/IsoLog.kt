package com.rodrig20.isodroid.util

import android.util.Log

/**
 * Single logging entry point for the whole app.
 *
 * Root operations shell out to scripts and throw away everything except the
 * trailing status line, so a bug report without logs says almost nothing: the
 * app can only say "Error: Could not bind UDC". Everything interesting happens
 * inside that discarded output. These logs are what a user attaches to a bug
 * report, so they are emitted unconditionally (including in release builds)
 * under one tag:
 *
 *     adb logcat -s ISOdroid
 *
 * Capture the whole session, which is what bug reports should include:
 *
 *     adb logcat -c && adb logcat -v threadtime -s ISOdroid > isodroid.log
 *
 * They must stay unconditional: gating on BuildConfig.DEBUG would leave release
 * users, the ones hitting kernel-specific problems, with nothing to send. The
 * volume is bounded instead (see [block]).
 */
object IsoLog {

    /** Logcat tag. Kept short so `adb logcat -s ISOdroid` is all a user needs. */
    const val TAG = "ISOdroid"

    /** Cap on lines emitted per [block] call, so a runaway script cannot flood logcat. */
    private const val MAX_BLOCK_LINES = 200

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun w(message: String, error: Throwable? = null) {
        Log.w(TAG, message, error)
    }

    fun e(message: String, error: Throwable? = null) {
        Log.e(TAG, message, error)
    }

    /**
     * Logs a block of text (typically merged stdout+stderr from a root script)
     * one line per entry.
     *
     * A single multi-line message is awkward in logcat: it is hard to read,
     * hard to grep and only the first line is obvious in most viewers. Emitting
     * each line separately keeps the structure intact and stays greppable.
     */
    fun block(prefix: String, text: String) {
        val lines = text.lines()
        if (lines.size > MAX_BLOCK_LINES) {
            lines.take(MAX_BLOCK_LINES).forEach { line -> Log.d(TAG, "$prefix | $line") }
            Log.w(TAG, "$prefix | ... truncated, ${lines.size - MAX_BLOCK_LINES} more line(s)")
        } else {
            lines.forEach { line -> Log.d(TAG, "$prefix | $line") }
        }
    }
}
