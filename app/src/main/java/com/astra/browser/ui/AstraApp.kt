package com.astra.browser.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.astra.browser.ui.bookmarks.BookmarksScreen
import com.astra.browser.ui.browser.BrowserScreen
import com.astra.browser.ui.downloads.DownloadsScreen
import com.astra.browser.ui.history.HistoryScreen
import com.astra.browser.ui.motion.astraEnter
import com.astra.browser.ui.motion.astraExit
import com.astra.browser.ui.prefs.LocalUiPrefs
import com.astra.browser.ui.privacy.PrivacyDashboardScreen
import com.astra.browser.ui.settings.SettingsScreen
import com.astra.browser.ui.settings.SettingsSubScreen
import com.astra.browser.ui.settings.SettingsSection
import com.astra.browser.ui.settings.WallpaperScreen
import com.astra.browser.ui.tabswitcher.TabSwitcherScreen

object AstraRoutes {
    const val BROWSER = "browser"
    const val TAB_SWITCHER = "tab_switcher"
    const val BOOKMARKS = "bookmarks"
    const val HISTORY = "history"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val PRIVACY_DASHBOARD = "privacy_dashboard"
    const val WALLPAPER = "wallpaper"

    // Settings sub-screens
    const val SET_HOME = "settings/home"
    const val SET_LAYOUT = "settings/layout"
    const val SET_MOTION = "settings/motion"
    const val SET_PRIVACY = "settings/privacy"
    const val SET_MEDIA = "settings/media"
    const val SET_GENERAL = "settings/general"
}

@Composable
fun AstraApp() {
    val navController = rememberNavController()
    val motion = LocalUiPrefs.current.motion

    // The browser screen is the "base": it must never slide (it hosts the live
    // WebView -- animating a WebView-sized layer is what makes weak GPUs stutter),
    // so it only ever gets a quick fade. Every other screen uses the chosen style.
    NavHost(
        navController = navController,
        startDestination = AstraRoutes.BROWSER,
        enterTransition = { astraEnter(motion, forward = true) },
        exitTransition = { astraExit(motion, forward = true) },
        popEnterTransition = { astraEnter(motion, forward = false) },
        popExitTransition = { astraExit(motion, forward = false) }
    ) {
        composable(AstraRoutes.BROWSER) { BrowserScreen(navController = navController) }
        composable(AstraRoutes.TAB_SWITCHER) { TabSwitcherScreen(navController = navController) }
        composable(AstraRoutes.BOOKMARKS) { BookmarksScreen(navController = navController) }
        composable(AstraRoutes.HISTORY) { HistoryScreen(navController = navController) }
        composable(AstraRoutes.DOWNLOADS) { DownloadsScreen(navController = navController) }
        composable(AstraRoutes.SETTINGS) { SettingsScreen(navController = navController) }
        composable(AstraRoutes.PRIVACY_DASHBOARD) { PrivacyDashboardScreen(navController = navController) }
        composable(AstraRoutes.WALLPAPER) { WallpaperScreen(navController = navController) }

        composable(AstraRoutes.SET_HOME) { SettingsSubScreen(navController, SettingsSection.HOME) }
        composable(AstraRoutes.SET_LAYOUT) { SettingsSubScreen(navController, SettingsSection.LAYOUT) }
        composable(AstraRoutes.SET_MOTION) { SettingsSubScreen(navController, SettingsSection.MOTION) }
        composable(AstraRoutes.SET_PRIVACY) { SettingsSubScreen(navController, SettingsSection.PRIVACY) }
        composable(AstraRoutes.SET_MEDIA) { SettingsSubScreen(navController, SettingsSection.MEDIA) }
        composable(AstraRoutes.SET_GENERAL) { SettingsSubScreen(navController, SettingsSection.GENERAL) }
    }
}
