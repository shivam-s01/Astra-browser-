package com.astra.browser.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.astra.browser.theme.LocalAstraColors
import com.astra.browser.ui.AstraRoutes
import com.astra.browser.ui.browser.components.AstraWebViewHost
import com.astra.browser.ui.browser.components.BrowserMenu
import com.astra.browser.ui.browser.components.FindInPageBar
import com.astra.browser.ui.newtab.NewTabPage

@Composable
fun BrowserScreen(
    navController: NavController,
    viewModel: BrowserViewModel = hiltViewModel()
) {
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val activeTab = tabs.find { it.id == activeTabId }
    val colors = LocalAstraColors.current

    var addressBarText by remember(activeTab?.id) { mutableStateOf(activeTab?.url ?: "") }
    var isAddressBarFocused by remember { mutableStateOf(false) }
    var showBrowserMenu by remember { mutableStateOf(false) }
    var showFindInPage by remember { mutableStateOf(false) }

    LaunchedEffect(activeTab?.url) {
        if (!isAddressBarFocused) addressBarText = activeTab?.url ?: ""
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                AstraToolbar(
                    addressText = addressBarText,
                    onAddressChange = { addressBarText = it },
                    onAddressFocusChange = { isAddressBarFocused = it },
                    isSecure = activeTab?.isSecure ?: false,
                    isLoading = activeTab?.isLoading ?: false,
                    isBookmarked = activeTab?.isBookmarked ?: false,
                    isPrivate = activeTab?.isPrivate ?: false,
                    canGoBack = activeTab?.canGoBack ?: false,
                    canGoForward = activeTab?.canGoForward ?: false,
                    tabCount = tabs.size,
                    onNavigate = { input ->
                        activeTab?.let { tab -> viewModel.navigate(tab.id, input) }
                    },
                    onBack = { activeTab?.let { viewModel.tabManager.getWebView(it.id)?.goBack() } },
                    onForward = { activeTab?.let { viewModel.tabManager.getWebView(it.id)?.goForward() } },
                    onReloadOrStop = {
                        activeTab?.let { tab ->
                            val webView = viewModel.tabManager.getWebView(tab.id)
                            if (tab.isLoading) webView?.stopLoading() else webView?.reload()
                        }
                    },
                    onBookmarkToggle = { activeTab?.let { viewModel.toggleBookmark(it.id) } },
                    onTabSwitcherClick = { navController.navigate(AstraRoutes.TAB_SWITCHER) },
                    onMenuClick = { showBrowserMenu = true }
                )
                if (activeTab?.isLoading == true) {
                    LinearProgressIndicator(
                        progress = { (activeTab.loadProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = colors.accent,
                        trackColor = Color.Transparent
                    )
                }
                if (showFindInPage) {
                    FindInPageBar(
                        webView = activeTab?.let { viewModel.tabManager.getWebView(it.id) },
                        onClose = { showFindInPage = false }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (activeTab == null || activeTab.isBlankTab) {
                NewTabPage(
                    onNavigate = { input ->
                        activeTab?.let { tab -> viewModel.navigate(tab.id, input) }
                    }
                )
            } else {
                AstraWebViewHost(
                    tab = activeTab,
                    tabManager = viewModel.tabManager,
                    onPageVisited = { tabId, url -> viewModel.onPageVisited(tabId, url) }
                )
            }

            if (activeTab?.isPrivate == true) {
                PrivateModeBadge(modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
            }
        }
    }

    if (showBrowserMenu) {
        BrowserMenu(
            activeTab = activeTab,
            onDismiss = { showBrowserMenu = false },
            onNewTab = { viewModel.newTab(); showBrowserMenu = false },
            onNewPrivateTab = { viewModel.newTab(isPrivate = true); showBrowserMenu = false },
            onBookmarks = { navController.navigate(AstraRoutes.BOOKMARKS); showBrowserMenu = false },
            onHistory = { navController.navigate(AstraRoutes.HISTORY); showBrowserMenu = false },
            onDownloads = { navController.navigate(AstraRoutes.DOWNLOADS); showBrowserMenu = false },
            onFindInPage = { showFindInPage = true; showBrowserMenu = false },
            onDesktopSite = { activeTab?.let { viewModel.toggleDesktopSite(it.id) }; showBrowserMenu = false },
            onSettings = { navController.navigate(AstraRoutes.SETTINGS); showBrowserMenu = false },
            onPrivacyDashboard = { navController.navigate(AstraRoutes.PRIVACY_DASHBOARD); showBrowserMenu = false }
        )
    }
}

@Composable
private fun PrivateModeBadge(modifier: Modifier = Modifier) {
    val colors = LocalAstraColors.current
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = colors.surfaceVariant,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Filled.VisibilityOff, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
            Text("Private", style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
        }
    }
}
