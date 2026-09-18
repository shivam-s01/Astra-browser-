package com.astra.browser.core.media

import android.content.Context
import android.content.Intent
import android.os.Build
import android.webkit.JavascriptInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide tracker of which tabs are currently playing media.
 * JS callbacks arrive on a WebView background thread, hence the concurrent set.
 */
@Singleton
class MediaPlaybackBridge @Inject constructor() {

    private val _isAnyTabPlaying = MutableStateFlow(false)
    val isAnyTabPlaying: StateFlow<Boolean> = _isAnyTabPlaying

    private val playingTabIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Set by TabManager so the notification's Stop button can pause pages. */
    var pauseAllHandler: (() -> Unit)? = null

    /** Set by TabManager so the lock-screen Play button can resume the active tab's media. */
    var playAllHandler: (() -> Unit)? = null

    /** Set by TabManager so lock-screen skip buttons can seek the active tab's media by [seconds]. */
    var seekHandler: ((seconds: Double) -> Unit)? = null

    private val _keepPlayingInBackground = MutableStateFlow(false)
    val keepPlayingInBackground: StateFlow<Boolean> = _keepPlayingInBackground

    /** Toggled by BackgroundPlaybackController from the "Background playback" setting. */
    fun setKeepPlayingInBackground(enabled: Boolean) {
        _keepPlayingInBackground.value = enabled
    }

    init { instance = this }

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

    companion object {
        @Volatile private var instance: MediaPlaybackBridge? = null
        fun requestPauseAll() { instance?.pauseAllHandler?.invoke() }
        fun requestPlayAll() { instance?.playAllHandler?.invoke() }
        fun requestSeek(seconds: Double) { instance?.seekHandler?.invoke(seconds) }
    }
}

/**
 * Starts/stops [BackgroundPlaybackService] to match real playback state,
 * gated by the "Background playback" setting. Singleton so it is only ever
 * started once (a second collector would start the service twice).
 */
@Singleton
class BackgroundPlaybackController @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val bridge: MediaPlaybackBridge,
    private val settingsStore: com.astra.browser.data.store.SettingsStore,
    private val tabManager: com.astra.browser.core.tabs.TabManager
) {
    private var started = false

    // Own process-lifetime scope. Using a ViewModel's scope made the
    // collector die when the ViewModel was cleared, so background playback
    // silently stopped working after e.g. a configuration change.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Idempotent: safe to call from every ViewModel/Activity creation. */
    fun start() {
        if (started) return
        started = true

        val titleFlow = combine(tabManager.tabs, tabManager.activeTabId) { list, id ->
            list.find { it.id == id }?.title?.takeIf { it.isNotBlank() } ?: "Playing in Astra"
        }

        // Keep the bridge's flag (which every WebView's visibility-spoof JS
        // reads from) in sync with the user's setting. This is what stops
        // YouTube (and other sites that pause themselves via the Page
        // Visibility API) from self-pausing the moment the app goes to the
        // background, when the user has asked for background playback.
        scope.launch {
            settingsStore.backgroundPlayback.distinctUntilChanged().collect { enabled ->
                bridge.setKeepPlayingInBackground(enabled)
            }
        }

        scope.launch {
            combine(bridge.isAnyTabPlaying, settingsStore.backgroundPlayback, titleFlow) { playing, enabled, title ->
                (playing && enabled) to title
            }.distinctUntilChanged().collect { (shouldRun, title) ->
                val intent = Intent(context, BackgroundPlaybackService::class.java)
                if (shouldRun) {
                    intent.putExtra(BackgroundPlaybackService.EXTRA_TITLE, title)
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    } catch (e: Exception) {
                        // Android 12+ may refuse a foreground-service start
                        // from the background; never crash over it.
                    }
                } else {
                    context.stopService(intent)
                }
            }
        }
    }
}
