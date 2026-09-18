package com.astra.browser.ui.browser.components

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
            }
            webView
        },
        update = { webView ->
            settings_applyDesktopMode(webView, tab.desktopSiteEnabled)

            if (webView.url.isNullOrBlank() && tab.url.isNotBlank()) {
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
