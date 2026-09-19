package com.astra.browser

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.astra.browser.data.store.SettingsStore
import com.astra.browser.theme.AstraThemeId
import com.astra.browser.theme.AstraTheme
import com.astra.browser.ui.AstraApp
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var tabManager: com.astra.browser.core.tabs.TabManager
    @Inject lateinit var mediaBridge: com.astra.browser.core.media.MediaPlaybackBridge

    @Volatile private var backgroundPlaybackEnabled = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Track the setting in a plain field so onStop can read it instantly
        // (no suspend call while the app is being backgrounded).
        lifecycleScope.launch {
            settingsStore.backgroundPlayback.collect { backgroundPlaybackEnabled = it }
        }

        // Android 13+ requires this at runtime or DownloadManager's
        // completion/progress notification is silently suppressed -- a
        // download can succeed in the background with no visible sign it
        // happened at all, which reads as "downloads don't work".
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // --- Temporary crash-viewer (debugging aid) ---
        // If AstraApplication's crash logger wrote a crash file on the
        // previous run, show it full-screen and selectable on next launch
        // instead of the normal browser UI. This is needed because file
        // manager access to internal app storage (filesDir) isn't
        // straightforward without a rooted device or ADB. Once the
        // underlying crash is diagnosed and fixed, this block (and the
        // logger in AstraApplication) should be removed.
        val crashFiles = filesDir.listFiles { f -> f.name.startsWith("astra_crash_") }
            ?.sortedByDescending { it.lastModified() }
        val latestCrash = crashFiles?.firstOrNull()

        if (latestCrash != null) {
            setContent {
                CrashViewerScreen(crashFile = latestCrash, onDismiss = {
                    crashFiles.forEach { it.delete() }
                    recreate()
                })
            }
            return
        }

        setContent {
            val themeIdName by settingsStore.themeId.collectAsState(initial = "SYSTEM")
            val themeId = runCatching { AstraThemeId.valueOf(themeIdName) }.getOrDefault(AstraThemeId.SYSTEM)

            AstraTheme(themeId = themeId) {
                AstraApp()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // IMPORTANT: onPause() fires for things that DON'T actually leave
        // the app invisible -- a permission dialog, a share sheet, split-
        // screen losing focus. Freezing the WebView here would visibly
        // stop video the user can still see on screen. So onPause() only
        // ever CHECKS playback state (while the WebView is still guaranteed
        // responsive); onStop() is what actually applies the freeze/keep
        // decision, once we know the app is genuinely going to background.
        if (backgroundPlaybackEnabled) {
            tabManager.refreshPlaybackSnapshot()
        }
    }

    override fun onStop() {
        super.onStop()
        // Real fix for "song stops the instant I leave the app", without
        // keeping every WebView alive 24/7 (that would drain battery/heat on
        // low-end devices even when nothing is playing -- exactly what we
        // don't want).
        //
        // The naive "keepAlive = enabled && isAnyTabPlaying.value" read has
        // TWO race windows, not one:
        //  1. Timing: the JS 'play' event that flips isAnyTabPlaying to true
        //     is dispatched async from the WebView's own thread, so it can
        //     still read false for a moment even though media IS playing.
        //  2. Missed event: on a page that was still loading/attaching its
        //     listeners, the flag may never have been set at all yet.
        // Freezing the WebView in either case kills playback for good --
        // Chromium doesn't resume decode once paused mid-stream.
        //
        // Fix: if background playback is OFF, always freeze (cheap, correct,
        // matches user intent -- also the common case, since the setting
        // defaults off). If it's ON, confirmPlaybackAndFreeze() uses the
        // snapshot refreshPlaybackSnapshot() already started in onPause() if
        // it landed in time, or -- since that's filled in asynchronously via
        // WebView JS callbacks and onStop() can in rare cases follow
        // onPause() fast enough that it hasn't landed yet -- falls back to
        // querying fresh itself. Either way it's ground truth, not the
        // possibly-stale/racy cached isAnyTabPlaying flag.
        if (!backgroundPlaybackEnabled) {
            tabManager.onAppBackgrounded(keepMediaAlive = false)
            return
        }
        tabManager.confirmPlaybackAndFreeze()
    }

    override fun onStart() {
        super.onStart()
        tabManager.onAppForegrounded()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        tabManager.trimBackgroundTabs(level)
    }
}

@androidx.compose.runtime.Composable
private fun CrashViewerScreen(crashFile: File, onDismiss: () -> Unit) {
    val text = runCatching { crashFile.readText() }.getOrElse { "Could not read crash file: ${it.message}" }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxSize()
        ) {
            androidx.compose.material3.Button(onClick = onDismiss) {
                Text("Continue to browser (dismiss this crash log)")
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
            SelectionContainer {
                Text(
                    text = "Last crash (long-press to select & copy):\n\n$text",
                    color = Color(0xFF00FF66),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        }
    }
}
