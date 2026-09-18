package com.astra.browser.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.astra.browser.ui.bookmarks.BookmarksScreen
import com.astra.browser.ui.browser.BrowserScreen
import com.astra.browser.ui.downloads.DownloadsScreen
import com.astra.browser.ui.history.HistoryScreen
import com.astra.browser.ui.privacy.PrivacyDashboardScreen
import com.astra.browser.ui.settings.SettingsScreen
import com.astra.browser.ui.tabswitcher.TabSwitcherScreen

object AstraRoutes {
    const val BROWSER = "browser"
    const val TAB_SWITCHER = "tab_switcher"
    const val BOOKMARKS = "bookmarks"
    const val HISTORY = "history"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val PRIVACY_DASHBOARD = "privacy_dashboard"
}

@Composable
fun AstraApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = AstraRoutes.BROWSER) {
        composable(AstraRoutes.BROWSER) {
            BrowserScreen(navController = navController)
        }
        composable(AstraRoutes.TAB_SWITCHER) {
            TabSwitcherScreen(navController = navController)
        }
        composable(AstraRoutes.BOOKMARKS) {
            BookmarksScreen(navController = navController)
        }
        composable(AstraRoutes.HISTORY) {
            HistoryScreen(navController = navController)
        }
        composable(AstraRoutes.DOWNLOADS) {
            DownloadsScreen(navController = navController)
        }
        composable(AstraRoutes.SETTINGS) {
            SettingsScreen(navController = navController)
        }
        composable(AstraRoutes.PRIVACY_DASHBOARD) {
            PrivacyDashboardScreen(navController = navController)
        }
    }
}
