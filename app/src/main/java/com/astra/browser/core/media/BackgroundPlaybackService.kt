package com.astra.browser.core.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.astra.browser.MainActivity
import com.astra.browser.R

/**
 * Keeps Astra's process (and therefore its live WebViews) alive while a page
 * is playing audio/video and the app goes to the background — the same
 * mechanism music/video apps use. Android will otherwise suspend/kill a
 * backgrounded app's WebView, which stops playback.
 *
 * This is opt-in: only started while BrowserViewModel detects media is
 * actually playing (see MediaPlaybackTracker), and only if the user has
 * "Background playback" enabled in Settings. It is stopped the moment
 * playback ends or the setting is turned off, so it never lingers as a
 * silent battery drain.
 */
class BackgroundPlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Playing in Astra"
        startForeground(NOTIFICATION_ID, buildNotification(title), foregroundServiceType())
        return START_STICKY
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else 0

    private fun buildNotification(title: String): Notification {
        ensureChannel()

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, BackgroundPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Playing in the background")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .addAction(0, "Stop", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Background playback", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val ACTION_STOP = "com.astra.browser.action.STOP_PLAYBACK"
        private const val CHANNEL_ID = "astra_background_playback"
        private const val NOTIFICATION_ID = 4201
    }
}
