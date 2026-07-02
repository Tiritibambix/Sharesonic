package com.tiritibambix.sharesonic

import android.app.Application
import android.content.Context
import android.util.Log

/**
 * Installs a process-wide uncaught-exception handler so a hard-to-reproduce crash
 * (e.g. the first-launch-after-saving-server-info crash) can be captured without
 * adb: the full stack trace is logged (tag "SharesonicCrash") AND persisted to
 * SharedPreferences, then surfaced in a copyable dialog on the next launch (see
 * MainActivity). The previous default handler is still invoked so the OS shows
 * its normal "app stopped" behaviour.
 */
class SharesonicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val trace = Log.getStackTraceString(throwable)
                Log.e("SharesonicCrash", "Uncaught on '${thread.name}':\n$trace")
                getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_CRASH, "${throwable}\n\n$trace")
                    .commit() // synchronous — the process is about to die
            } catch (_: Throwable) {
                // The crash handler must never crash.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        const val CRASH_PREFS = "sharesonic_crash"
        const val KEY_LAST_CRASH = "last_crash"
    }
}
