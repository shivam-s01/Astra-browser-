package com.astra.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
