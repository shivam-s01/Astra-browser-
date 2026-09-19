package com.astra.browser.ui.browser.components

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.astra.browser.core.tabs.TabManager
import com.astra.browser.domain.model.Tab

@Composable
fun AstraWebViewHost(
    tab: Tab,
    tabManager: TabManager,
    onPageVisited: (tabId: String, url: String) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val webView = tabManager.getWebView(tab.id) ?: tabManager.createTab(ctx).let {
                tabManager.getWebView(it.id)!!
            }
            webView.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                (parent as? ViewGroup)?.removeView(this)
                // Lets the WebView's own fling/overscroll physics run
                // uninterrupted rather than Android's default edge-glow
                // container behavior, which on some devices was fighting
                // with fast scrolls on long/tall pages (e.g. YouTube) and
                // turning a scroll gesture's touch-up into a stray tap.
                overScrollMode = View.OVER_SCROLL_ALWAYS
                isScrollbarFadingEnabled = true
            }
            webView
        },
        update = { webView ->
            // Only touch WebView settings when the desktop-mode flag
            // actually changed for this tab, instead of unconditionally
            // re-applying them on every single recomposition (which
            // happens far more often than the user actually toggles
            // desktop mode -- e.g. on every progress/title update from
            // the page). Repeatedly rewriting WebSettings mid-scroll was
            // an unnecessary source of touch-handling jitter.
            val desiredUa = if (tab.desktopSiteEnabled) DESKTOP_USER_AGENT else null
            if (webView.settings.userAgentString != desiredUa) {
                settings_applyDesktopMode(webView, tab.desktopSiteEnabled)
            }

            // Only load when the tab's URL actually changed from what this
            // WebView last loaded (e.g. address-bar navigation, reopen). We
            // compare against webView.url (the WebView's own current URL)
            // rather than re-triggering on every recomposition, which is
            // what previously caused reloads/tears-down mid-navigation.
            if (tab.url.isNotBlank() && webView.url != tab.url && webView.url == null) {
                webView.loadUrl(tab.url)
            }
        }
    )
}

private fun settings_applyDesktopMode(webView: android.webkit.WebView, desktop: Boolean) {
    val settings = webView.settings
    if (desktop) {
        settings.userAgentString = DESKTOP_USER_AGENT
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
    } else {
        settings.userAgentString = null // reverts to system default mobile UA
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
    }
}

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
