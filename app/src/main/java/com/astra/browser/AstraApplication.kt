package com.astra.browser

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltAndroidApp
class AstraApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
    }

    /**
     * Temporary diagnostic aid: writes the full crash stack trace to a
     * plain text file, then lets the default system crash handler run as
     * normal (so the OS still shows its usual "app keeps stopping"
     * dialog).
     *
     * Uses internal app storage (filesDir) instead of external storage --
     * it's always immediately available with no permission or directory
     * creation delay, which matters if the crash happens very early in
     * startup. Also logs to Logcat via Log.e as a second path, in case
     * file I/O itself is what's failing.
     *
     * This exists because logcat/ADB access wasn't available for
     * debugging. Once the underlying crash is found and fixed, this
     * should be removed.
     */
    private fun installCrashLogger() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val fullTrace = "Thread: ${thread.name}\n\n$sw"

                // Log to Logcat too (visible via `logcat -d` even if file
                // writing below fails for any reason).
                Log.e("AstraCrashLogger", fullTrace)

                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                // filesDir is internal app storage: always exists already,
                // no permission needed, no external-storage mount delay.
                val file = File(filesDir, "astra_crash_$timestamp.txt")
                file.writeText(fullTrace)
            } catch (loggerError: Throwable) {
                // Never let the crash logger itself cause a crash loop,
                // but do surface that logging failed.
                Log.e("AstraCrashLogger", "Failed to write crash log", loggerError)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
