package com.astra.browser.ui.browser.components

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    // Tracks the last URL WE told this WebView to load (via loadUrl), keyed
    // per-tab, so we can tell "tab.url changed because the user navigated
    // somewhere new" apart from "tab.url changed because the WebView itself
    // reported a page load / SPA route change back up through TabManager".
    //
    // The previous check compared against webView.url and additionally
    // required webView.url == null, which is only ever true before the very
    // first page load. That meant loadUrl() was called at most ONCE per
    // WebView, ever -- every subsequent navigate() (address bar entry, a
    // "Download / Watch" button that sets tab.url via JS-driven navigation,
    // reopening a tab, etc.) silently did nothing because this condition
    // was already false. This is the root cause of "clicking links/buttons
    // does nothing" reports.
    val lastLoadedUrl = remember(tab.id) { mutableStateOf<String?>(null) }

    // AndroidView's `factory` only ever runs ONCE per composable instance;
    // its `update` block keeps operating on that same original WebView
    // forever, even if TabManager swaps in a brand-new one underneath (see
    // replaceCrashedWebView -- a crashed render process leaves its old
    // WebView permanently unusable, so a fresh instance takes its place).
    // Without this key(), a crash on the currently-visible tab would leave
    // the screen stuck showing the dead WebView while the real, working
    // replacement sits unused in TabManager. Keying on the CURRENT
    // WebView's identity hash forces Compose to dispose and recreate this
    // AndroidView (re-running factory) whenever that identity changes --
    // i.e. exactly on a crash-replacement, and a no-op cost the rest of the
    // time since the identity is stable across ordinary recompositions.
    val currentWebViewIdentity = System.identityHashCode(tabManager.getWebView(tab.id))

    key(tab.id, currentWebViewIdentity) {
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
                // A WebView created earlier by TabManager (e.g. on cold start,
                // before this composable existed, or just now by a crash
                // recovery) may already have a URL loaded or in flight;
                // treat that as "already loaded" so we don't reload it.
                lastLoadedUrl.value = webView.url
                webView
            },
            update = { webView ->
                settings_applyDesktopMode(webView, tab.desktopSiteEnabled)

                // Safety net for the tab-freezing added in TabManager: this
                // composable only ever hosts the ACTIVE tab (BrowserScreen only
                // renders AstraWebViewHost for activeTab), so whichever WebView
                // is here should always be running. Normally switchTo() already
                // resumes it, but calling this here too is a cheap no-op when
                // already resumed and guards against any path that shows a tab
                // without going through switchTo() leaving it stuck paused.
                webView.onResume()

                // Load whenever tab.url points somewhere we haven't actually
                // told THIS WebView to load yet. This correctly handles:
                //  - first load into a fresh WebView
                //  - address-bar / new-tab-page navigation to a new URL
                //  - re-navigating to the same URL after the user pressed back
                //    to it and forward again (webView.url would already match,
                //    but lastLoadedUrl is reset per recomposition key)
                // and correctly IGNORES:
                //  - tab.url changes caused by the WebView's own navigation
                //    (SPA route changes, redirects) which are already loaded.
                if (tab.url.isNotBlank() && tab.url != lastLoadedUrl.value && tab.url != webView.url) {
                    lastLoadedUrl.value = tab.url
                    webView.loadUrl(tab.url)
                } else if (tab.url.isNotBlank() && tab.url == webView.url) {
                    // Keep them in sync without forcing a reload.
                    lastLoadedUrl.value = tab.url
                }
            }
        )
    }
}

private fun settings_applyDesktopMode(webView: android.webkit.WebView, desktop: Boolean) {
    val settings = webView.settings
    if (desktop) {
        settings.userAgentString = DESKTOP_USER_AGENT
    } else {
        // Android WebView's actual default UA includes a "; wv)" token
        // (e.g. "Mozilla/5.0 (Linux; Android 13; Pixel Build/...; wv)...")
        // that marks the request as coming from an embedded WebView rather
        // than a real browser. A lot of JS-heavy sites (video platforms,
        // download portals, anything that server-side feature-detects)
        // either serve a stripped-down page, block outright, or show an
        // "open in app" nag when they see that tag -- which reads exactly
        // like "heavy sites won't open". Stripping just the "wv" token
        // (same trick Chrome-derived browsers like Brave use) makes every
        // site treat Astra as a normal mobile Chrome browser while keeping
        // the OS/device/Chromium version info accurate and unspoofed.
        val default = settings.userAgentString
        settings.userAgentString = default
            ?.replace("; wv)", ")")
            ?.replace(" wv)", ")")
            ?: default
    }
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
}

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
