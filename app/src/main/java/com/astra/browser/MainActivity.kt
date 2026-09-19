package com.astra.browser

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.astra.browser.data.store.SettingsStore
import com.astra.browser.theme.AstraThemeId
import com.astra.browser.theme.AstraTheme
import com.astra.browser.ui.AstraApp
import com.astra.browser.ui.browser.BrowserViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var settingsStore: SettingsStore

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Android 13+ requires this at runtime or DownloadManager's
        // completion/progress notification is silently suppressed -- a
        // download can succeed in the background with no visible sign it
        // happened at all, which reads as "downloads don't work".
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val themeIdName by settingsStore.themeId.collectAsState(initial = "SYSTEM")
            val themeId = runCatching { AstraThemeId.valueOf(themeIdName) }.getOrDefault(AstraThemeId.SYSTEM)

            AstraTheme(themeId = themeId) {
                AppLockGate(activity = this) {
                    AstraApp()
                    PrivateTabScreenGuard()
                }
            }
        }
    }
}

/**
 * Applies FLAG_SECURE (blocks screenshots, and shows a blank thumbnail
 * instead of page content in the Recents/app-switcher view) whenever the
 * currently active tab is a private tab, and removes it otherwise. Without
 * this, "private" browsing still leaks its content the moment the user
 * takes a screenshot or opens the app switcher -- the OS keeps a full
 * bitmap of the last frame regardless of any in-page privacy setting.
 */
@androidx.compose.runtime.Composable
private fun PrivateTabScreenGuard(viewModel: BrowserViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val activity = androidx.compose.ui.platform.LocalContext.current as? ComponentActivity ?: return
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val isActivePrivate = tabs.find { it.id == activeTabId }?.isPrivate == true

    LaunchedEffect(isActivePrivate) {
        if (isActivePrivate) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

/**
 * When "App lock" is enabled in Settings, requires a successful biometric
 * (fingerprint/face) or device-credential (PIN/pattern) check before
 * showing the browser content, on cold start and whenever the app returns
 * to the foreground. If the device has no biometric/PIN enrolled at all,
 * the lock is skipped -- there's no enrolled credential to check against,
 * so treating that as a permanent lock-out would strand the user.
 */
@androidx.compose.runtime.Composable
private fun AppLockGate(
    activity: FragmentActivity,
    content: @androidx.compose.runtime.Composable () -> Unit
) {
    val settingsStore: SettingsStore = androidx.hilt.navigation.compose.hiltViewModel<AppLockViewModel>().settingsStore
    val appLockEnabled by settingsStore.appLockEnabled.collectAsState(initial = false)
    var isUnlocked by remember { mutableStateOf(!appLockEnabled) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    // Re-lock whenever the app comes back to the foreground (not just on
    // cold start), e.g. the user switches to another app to check
    // something and comes back -- that's exactly the moment an app lock
    // is supposed to guard.
    DisposableEffect(lifecycleOwner, appLockEnabled) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START && appLockEnabled) {
                isUnlocked = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(appLockEnabled, isUnlocked) {
        if (!appLockEnabled) {
            isUnlocked = true
            return@LaunchedEffect
        }
        if (isUnlocked) return@LaunchedEffect
        val biometricManager = BiometricManager.from(activity)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            // Nothing enrolled (no fingerprint/face/PIN) -- can't lock
            // against a credential that doesn't exist. Let the user in
            // rather than blocking them out of their own browser.
            isUnlocked = true
            return@LaunchedEffect
        }

        isUnlocked = false
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    isUnlocked = true
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Leave locked; the prompt UI itself already showed
                    // the error / lets the user retry or cancel.
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Astra")
            .setSubtitle("Verify it's you to continue")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(promptInfo)
    }

    if (isUnlocked) {
        content()
    } else {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@dagger.hilt.android.lifecycle.HiltViewModel
class AppLockViewModel @Inject constructor(val settingsStore: SettingsStore) : androidx.lifecycle.ViewModel()
