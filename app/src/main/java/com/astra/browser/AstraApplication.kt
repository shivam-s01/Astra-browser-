package com.astra.browser

import android.app.Application
import android.os.Environment
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
     * plain text file under the public Downloads folder, then lets the
     * default system crash handler run as normal (so the OS still shows
     * its usual "app keeps stopping" dialog).
     *
     * This exists because logcat access wasn't available for debugging.
     * Once the underlying crash is found and fixed, this can be removed.
     */
    private fun installCrashLogger() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: getExternalFilesDir(null)
                val file = File(downloadsDir, "astra_crash_$timestamp.txt")
                file.writeText(
                    "Thread: ${thread.name}\n\n${sw}"
                )
            } catch (_: Throwable) {
                // Never let the crash logger itself cause a crash loop.
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
