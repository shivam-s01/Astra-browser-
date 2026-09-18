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

    override fun onStop() {
        super.onStop()
        // Keep media alive only if the user opted in AND something is playing.
        val keepAlive = backgroundPlaybackEnabled && mediaBridge.isAnyTabPlaying.value
        tabManager.onAppBackgrounded(keepMediaAlive = keepAlive)
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
