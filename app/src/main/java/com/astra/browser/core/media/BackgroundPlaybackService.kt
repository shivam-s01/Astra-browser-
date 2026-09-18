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
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.astra.browser.MainActivity
import com.astra.browser.R

/**
 * Keeps Astra's process (and its live WebViews) alive while a page plays
 * audio/video and the app is in the background, and exposes real lock-screen
 * / Bluetooth / notification-shade media controls via a MediaSessionCompat.
 *
 * Without a MediaSession, Android has no idea Astra is "playing media" --
 * the notification alone does not get you lock-screen transport controls.
 * The session's PlaybackState + metadata is what makes the OS treat this
 * like a real media app (lock screen widget, Bluetooth headset buttons, etc).
 *
 * Only runs while media is actually playing AND the user has Background
 * playback enabled. A partial wake lock is held only for that window so the
 * CPU keeps decoding audio with the screen off; it is released the moment
 * the service stops, so it never lingers as a battery drain.
 */
class BackgroundPlaybackService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSessionCompat? = null
    private var isPlaying = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        setUpMediaSession()
    }

    private fun setUpMediaSession() {
        val session = MediaSessionCompat(this, "AstraBrowser").apply {
            // Without this, some OEM skins (and stock Android on some
            // versions) don't reliably surface lock-screen transport
            // controls for a session that never declares what kind of
            // audio it's playing.
            setPlaybackToLocal(android.media.AudioManager.STREAM_MUSIC)
            // Routes hardware media-button presses (Bluetooth/wired headset
            // play/pause/next/prev) through MediaButtonReceiver -> this
            // service's onStartCommand -> MediaButtonReceiver.handleIntent,
            // which dispatches into the session's Callback below. Without
            // this, headset buttons only work while the notification/app
            // is already actively foreground.
            setMediaButtonReceiver(
                PendingIntent.getBroadcast(
                    this@BackgroundPlaybackService, 0,
                    Intent(android.content.Intent.ACTION_MEDIA_BUTTON).setClass(
                        this@BackgroundPlaybackService,
                        androidx.media.session.MediaButtonReceiver::class.java
                    ),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    isPlaying = true
                    MediaPlaybackBridge.requestPlayAll()
                    updatePlaybackState()
                }

                override fun onPause() {
                    isPlaying = false
                    MediaPlaybackBridge.requestPauseAll()
                    updatePlaybackState()
                }

                override fun onStop() {
                    isPlaying = false
                    MediaPlaybackBridge.requestPauseAll()
                    stopSelf()
                }

                override fun onSkipToNext() {
                    MediaPlaybackBridge.requestSeek(15.0)
                }

                override fun onSkipToPrevious() {
                    MediaPlaybackBridge.requestSeek(-15.0)
                }
            })
            isActive = true
        }
        mediaSession = session
        updatePlaybackState()
    }

    private fun updatePlaybackState() {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build()
        )
    }

    private fun updateMetadata(title: String) {
        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Astra Browser")
                .build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Let the media session's own button receiver handle system-generated
        // media button intents (Bluetooth headset, wired headset, etc).
        mediaSession?.let { MediaButtonReceiver.handleIntent(it, intent) }

        if (intent?.action == ACTION_STOP) {
            isPlaying = false
            MediaPlaybackBridge.requestPauseAll()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PLAY_PAUSE) {
            if (isPlaying) {
                isPlaying = false
                MediaPlaybackBridge.requestPauseAll()
            } else {
                isPlaying = true
                MediaPlaybackBridge.requestPlayAll()
            }
            updatePlaybackState()
            startForeground(NOTIFICATION_ID, buildNotification(lastTitle))
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: lastTitle
        lastTitle = title
        isPlaying = true
        updatePlaybackState()
        updateMetadata(title)
        val notification = buildNotification(title)

        // startForeground MUST be called quickly after startForegroundService
        // or Android 12+ kills the app with ForegroundServiceDidNotStartInTime.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        acquireWakeLock()
        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Astra:BackgroundPlayback").apply {
            setReferenceCounted(false)
            // Safety net: auto-release after 6h even if something goes wrong.
            acquire(6 * 60 * 60 * 1000L)
        }
    }

    private fun buildNotification(title: String): Notification {
        ensureChannel()

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, BackgroundPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPause = PendingIntent.getService(
            this, 2,
            Intent(this, BackgroundPlaybackService::class.java).setAction(ACTION_PLAY_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseLabel = if (isPlaying) "Pause" else "Play"

        val session = mediaSession

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Playing in Astra")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openApp)
            .setOngoing(isPlaying)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Back 15s",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS))
            .addAction(playPauseIcon, playPauseLabel, playPause)
            .addAction(android.R.drawable.ic_media_next, "Forward 15s",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT))
            .addAction(0, "Stop", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (session != null) {
                    setStyle(
                        androidx.media.app.NotificationCompat.MediaStyle()
                            .setMediaSession(session.sessionToken)
                            .setShowActionsInCompactView(0, 1, 2)
                    )
                }
            }
            .build()
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
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
        const val ACTION_PLAY_PAUSE = "com.astra.browser.action.PLAY_PAUSE"
        private const val CHANNEL_ID = "astra_background_playback"
        private const val NOTIFICATION_ID = 4201
        private var lastTitle: String = "Playing in Astra"
    }
}
