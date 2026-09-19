package com.astra.browser.core.media

import android.content.Context
import android.content.Intent
import android.os.Build
import android.webkit.JavascriptInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One instance shared app-wide. Each tab's WebView gets its own
 * addJavascriptInterface("AstraMedia", ...) call but they all report into
 * this single tracker, since only one foreground notification/service is
 * needed regardless of how many tabs are playing something.
 */
@Singleton
class MediaPlaybackBridge @Inject constructor() {

    private val _isAnyTabPlaying = MutableStateFlow(false)
    val isAnyTabPlaying: StateFlow<Boolean> = _isAnyTabPlaying

    private val playingTabIds = mutableSetOf<String>()

    fun jsInterfaceFor(tabId: String) = JsInterface(tabId)

    fun clearTab(tabId: String) {
        playingTabIds.remove(tabId)
        _isAnyTabPlaying.value = playingTabIds.isNotEmpty()
    }

    inner class JsInterface(private val tabId: String) {
        @JavascriptInterface
        fun onPlaybackState(isPlaying: Boolean) {
            if (isPlaying) playingTabIds.add(tabId) else playingTabIds.remove(tabId)
            _isAnyTabPlaying.value = playingTabIds.isNotEmpty()
        }
    }
}

/**
 * Starts/stops [BackgroundPlaybackService] to match real playback state,
 * gated by the user's "Background playback" setting. Call [start] once
 * (e.g. from AstraApplication or the top-level ViewModel) and it runs for
 * the process lifetime.
 */
class BackgroundPlaybackController @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val bridge: MediaPlaybackBridge
) {
    fun start(scope: CoroutineScope, backgroundPlaybackEnabled: StateFlow<Boolean>, activeTabTitle: StateFlow<String>) {
        scope.launch {
            combine(bridge.isAnyTabPlaying, backgroundPlaybackEnabled, activeTabTitle) { playing, enabled, title ->
                Triple(playing, enabled, title)
            }.distinctUntilChanged().collect { (playing, enabled, title) ->
                val shouldRun = playing && enabled
                val intent = Intent(context, BackgroundPlaybackService::class.java)
                if (shouldRun) {
                    intent.putExtra(BackgroundPlaybackService.EXTRA_TITLE, title)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } else {
                    context.stopService(intent)
                }
            }
        }
    }
}
