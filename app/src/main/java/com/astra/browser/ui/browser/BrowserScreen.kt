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
import androidx.activity.compose.BackHandler
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.astra.browser.theme.LocalAstraColors
import com.astra.browser.ui.AstraRoutes
import com.astra.browser.ui.browser.components.AstraWebViewHost
import com.astra.browser.ui.browser.components.BraveBottomBar
import com.astra.browser.ui.browser.components.BrowserMenu
import com.astra.browser.ui.browser.components.FindInPageBar
import com.astra.browser.ui.browser.components.ShieldSheet
import com.astra.browser.privacy.blocker.ContentBlocker
import com.astra.browser.ui.newtab.NewTabPage
import com.astra.browser.ui.prefs.LocalUiPrefs

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
    var showShield by remember { mutableStateOf(false) }
    var searchFocusKey by remember { mutableStateOf(0) }

    // ---- Shield (live) ----
    val shieldStats by viewModel.shieldStats.collectAsState()
    val adBlockingOn by viewModel.adBlockingEnabled.collectAsState()
    val trackingOn by viewModel.trackingProtectionEnabled.collectAsState()
    val popupsOn by viewModel.blockPopupsFlow.collectAsState()
    val lifetimeBlocked by viewModel.lifetimeBlocked.collectAsState()
    val shieldOffSites by viewModel.shieldOffSites.collectAsState()
    val tabStats = if (activeTab == null || activeTab.isBlankTab) ContentBlocker.TabStats()
                   else shieldStats[activeTab.id] ?: ContentBlocker.TabStats()
    val activeHost = remember(activeTab?.url, activeTab?.isBlankTab) {
        if (activeTab == null || activeTab.isBlankTab) null
        else runCatching { java.net.URI(activeTab.url).host?.lowercase() }.getOrNull()
    }
    val siteShieldOn = activeHost == null || activeHost !in shieldOffSites
    val fullscreenView by viewModel.fullscreenView.collectAsState()

    BackHandler(enabled = fullscreenView != null) {
        viewModel.exitFullscreen()
    }

    // Without this, Android's back button/gesture skipped the WebView
    // entirely and went straight to finishing the Activity -- so pressing
    // back while several pages deep into a site's own navigation history
    // closed the whole app instead of stepping back through that history,
    // exactly like every other real browser (Chrome, Brave) does it.
    //
    // Priority, evaluated in order every time back is pressed (Brave-style):
    //   1. Fullscreen video open -> handled by the BackHandler above already
    //      (Compose runs the innermost/first-declared enabled BackHandler).
    //   2. Find-in-page bar open -> close it, don't touch navigation yet.
    //   3. WebView has its own back history (canGoBack) -> step back a page
    //      in THIS tab, same as tapping the toolbar's back arrow.
    //   4. Tab has no more history AND it isn't the only/last tab -> close
    //      this tab and fall back to whichever tab was active before it
    //      (mirrors closing a tab in a real browser rather than exiting).
    //   5. Nothing left to step back through -> let the system handle it
    //      (backgrounds/exits the app normally). BackHandler is simply
    //      disabled in that case so this falls through to default behavior.
    // A tab sitting on the Astra start page (Home pressed) still has a page
    // underneath, so Back must be able to bring it back too.
    val hiddenPageBehindHome = activeTab != null && activeTab.isBlankTab && activeTab.url.isNotBlank()
    val canStepBackInPage = activeTab?.canGoBack == true || hiddenPageBehindHome
    BackHandler(enabled = fullscreenView == null && (showFindInPage || canStepBackInPage)) {
        when {
            showFindInPage -> showFindInPage = false
            canStepBackInPage -> activeTab?.let { viewModel.goBack(it.id) }
        }
    }

    // Case 4 above: no page history left, but more than one tab is open --
    // close the current tab instead of exiting the app. Separate
    // BackHandler so its `enabled` can react to tabs.size independently of
    // the page-history one above (both are re-evaluated fresh on every
    // back press by Compose, so ordering/overlap between them is safe).
    BackHandler(enabled = fullscreenView == null && !showFindInPage && !canStepBackInPage && tabs.size > 1) {
        activeTab?.let { viewModel.closeTab(it.id) }
    }

    LaunchedEffect(activeTab?.url) {
        if (!isAddressBarFocused) addressBarText = activeTab?.url ?: ""
    }

    // Toggling between the start page and a real page (Home / Back) must reset
    // the address text, otherwise anything typed on the start page and never
    // submitted would stick around in the bar when the page comes back.
    LaunchedEffect(activeTab?.isBlankTab) {
        addressBarText = activeTab?.url ?: ""
    }

    val urlBarBottom = LocalUiPrefs.current.urlBarBottom

    // The whole address-bar block (toolbar + load progress + find-in-page) is
    // built once and placed either in the top slot or the bottom slot, so both
    // layouts share exactly the same code and behaviour.
    val addressBlock: @Composable () -> Unit = {
        AstraToolbar(
            addressText = if (activeTab?.isBlankTab != false) "" else addressBarText,
            onAddressChange = { addressBarText = it },
            onAddressFocusChange = { isAddressBarFocused = it },
            isSecure = activeTab?.isSecure ?: false,
            isLoading = activeTab?.isLoading ?: false,
            isBookmarked = activeTab?.isBookmarked ?: false,
            isPrivate = activeTab?.isPrivate ?: false,
            isHome = activeTab == null || activeTab.isBlankTab,
            onNavigate = { input ->
                activeTab?.let { tab -> viewModel.navigate(tab.id, input) }
            },
            onReloadOrStop = {
                activeTab?.let { tab ->
                    if (tab.isLoading) viewModel.tabManager.getWebView(tab.id)?.stopLoading()
                    else viewModel.reload(tab.id)
                }
            },
            onBookmarkToggle = { activeTab?.let { viewModel.toggleBookmark(it.id) } },
            onBookmarksOpen = { navController.navigate(AstraRoutes.BOOKMARKS) },
            onShieldClick = { showShield = true },
            blockedCount = tabStats.blocked,
            shieldOn = (adBlockingOn || trackingOn) && siteShieldOn,
            focusRequestKey = searchFocusKey
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

    val navBar: @Composable () -> Unit = {
        BraveBottomBar(
            tabCount = tabs.size,
            isPrivate = activeTab?.isPrivate ?: false,
            onHome = { activeTab?.let { viewModel.goHome(it.id) } },
            onBookmarks = { navController.navigate(AstraRoutes.BOOKMARKS) },
            onSearch = { searchFocusKey++ },
            onTabs = { navController.navigate(AstraRoutes.TAB_SWITCHER) },
            onMenu = { showBrowserMenu = true }
        )
    }

    Scaffold(
        containerColor = colors.background,
        // imePadding on the bottom slot so the address bar rides above the
        // keyboard when it is at the bottom.
        topBar = {
            if (urlBarBottom) {
                Spacer(Modifier.statusBarsPadding())
            } else {
                Column(modifier = Modifier.statusBarsPadding()) { addressBlock() }
            }
        },
        bottomBar = {
            if (urlBarBottom) {
                Column(modifier = Modifier.imePadding()) {
                    addressBlock()
                    navBar()
                }
            } else {
                navBar()
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (activeTab == null || activeTab.isBlankTab) {
                NewTabPage(
                    onNavigate = { input ->
                        activeTab?.let { tab -> viewModel.navigate(tab.id, input) }
                    },
                    onOpenShield = { showShield = true }
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

    if (showShield) {
        ShieldSheet(
            host = activeHost,
            isHome = activeTab == null || activeTab.isBlankTab,
            stats = tabStats,
            lifetimeBlocked = lifetimeBlocked,
            adBlockingOn = adBlockingOn,
            trackingOn = trackingOn,
            popupsOn = popupsOn,
            siteShieldOn = siteShieldOn,
            onAdBlocking = { viewModel.setAdBlocking(it) },
            onTracking = { viewModel.setTrackingProtection(it) },
            onPopups = { viewModel.setBlockPopups(it) },
            onSiteShield = { on ->
                val tab = activeTab
                if (tab != null && activeHost != null) viewModel.setShieldForSite(tab.id, activeHost, on)
            },
            onOpenPrivacy = { navController.navigate(AstraRoutes.PRIVACY_DASHBOARD); showShield = false },
            onDismiss = { showShield = false }
        )
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
            onPrivacyDashboard = { navController.navigate(AstraRoutes.PRIVACY_DASHBOARD); showBrowserMenu = false },
            onBack = { activeTab?.let { viewModel.goBack(it.id) }; showBrowserMenu = false },
            onForward = { activeTab?.let { viewModel.goForward(it.id) }; showBrowserMenu = false },
            onReload = { activeTab?.let { viewModel.reload(it.id) }; showBrowserMenu = false },
            onBookmarkToggle = { activeTab?.let { viewModel.toggleBookmark(it.id) }; showBrowserMenu = false }
        )
    }

    // Fullscreen video overlay (YouTube etc.). Drawn on top of everything,
    // including the status bar. This is the fix for onShowCustomView being
    // dropped entirely before -- without an actual place to attach this
    // view and resolve its callback, WebView's internal state broke and
    // the next navigation (e.g. a search right after) could crash.
    fullscreenView?.let { view ->
        AndroidView(
            factory = {
                (view.parent as? android.view.ViewGroup)?.removeView(view)
                view
            },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
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
