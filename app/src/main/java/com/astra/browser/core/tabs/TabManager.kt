package com.astra.browser.core.tabs

import android.content.Context
import android.webkit.WebView
import com.astra.browser.core.media.MediaPlaybackBridge
import com.astra.browser.domain.model.Tab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the real WebView instance for every open tab. This is the single
 * source of truth for tab state; UI observes [tabs] and [activeTabId].
 *
 * WebViews are kept alive while their tab exists so back/forward history,
 * scroll position and page state survive tab switches — matching real
 * browser behavior rather than destroying/recreating on every switch.
 */
@Singleton
class TabManager @Inject constructor(
    private val mediaPlaybackBridge: MediaPlaybackBridge
) {

    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs: StateFlow<List<Tab>> = _tabs

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId

    private val webViews = mutableMapOf<String, WebView>()

    /** Set once by the browser UI layer; attaches clients to every new WebView. */
    var webViewConfigurer: ((tabId: String, webView: WebView) -> Unit)? = null

    val activeTab: Tab?
        get() = _tabs.value.find { it.id == _activeTabId.value }

    fun getWebView(tabId: String): WebView? = webViews[tabId]

    fun createTab(context: Context, isPrivate: Boolean = false, url: String? = null): Tab {
        val tab = Tab(isPrivate = isPrivate, url = url ?: "", isBlankTab = url.isNullOrBlank())
        val webView = createConfiguredWebView(context, isPrivate)
        webView.addJavascriptInterface(mediaPlaybackBridge.jsInterfaceFor(tab.id), "AstraMedia")
        webViews[tab.id] = webView
        webViewConfigurer?.invoke(tab.id, webView)
        _tabs.update { it + tab }
        _activeTabId.value = tab.id
        if (!url.isNullOrBlank()) webView.loadUrl(url)
        return tab
    }

    fun closeTab(tabId: String): Tab? {
        val closedTab = _tabs.value.find { it.id == tabId }
        webViews[tabId]?.apply {
            stopLoading()
            destroy()
        }
        webViews.remove(tabId)
        mediaPlaybackBridge.clearTab(tabId)
        _tabs.update { list -> list.filterNot { it.id == tabId } }

        if (_activeTabId.value == tabId) {
            _activeTabId.value = _tabs.value.lastOrNull()?.id
        }
        return closedTab
    }

    fun closeAllTabs() {
        webViews.values.forEach { it.stopLoading(); it.destroy() }
        webViews.clear()
        _tabs.value = emptyList()
        _activeTabId.value = null
    }

    fun closeAllPrivateTabs() {
        val privateIds = _tabs.value.filter { it.isPrivate }.map { it.id }
        privateIds.forEach { closeTab(it) }
        // Cookies are a single process-wide store in Android WebView --
        // closing a private tab alone leaves anything it set (session
        // cookies, login state) sitting in memory for the next private
        // tab, or even a normal tab, to see. Flush it explicitly so
        // "close private tabs" actually behaves like "forget this
        // session" rather than just removing the tab UI.
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        android.webkit.WebStorage.getInstance().deleteAllData()
    }

    fun switchTo(tabId: String) {
        if (_tabs.value.any { it.id == tabId }) {
            _activeTabId.value = tabId
            updateTab(tabId) { it.copy(lastAccessedAt = System.currentTimeMillis()) }
        }
    }

    fun duplicateTab(context: Context, tabId: String): Tab? {
        val source = _tabs.value.find { it.id == tabId } ?: return null
        return createTab(context, isPrivate = source.isPrivate, url = source.url)
    }

    fun updateTab(tabId: String, transform: (Tab) -> Tab) {
        _tabs.update { list -> list.map { if (it.id == tabId) transform(it) else it } }
    }

    fun tabCount(includePrivate: Boolean = true): Int =
        _tabs.value.count { includePrivate || !it.isPrivate }

    private fun createConfiguredWebView(context: Context, isPrivate: Boolean): WebView {
        return WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = !isPrivate
                databaseEnabled = !isPrivate
                cacheMode = if (isPrivate) android.webkit.WebSettings.LOAD_NO_CACHE
                            else android.webkit.WebSettings.LOAD_DEFAULT
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                // Video sites (YouTube etc.) need this false, or autoplay /
                // inline playback / fullscreen video breaks on first tap.
                mediaPlaybackRequiresUserGesture = false
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = false

                // --- Required for modern sites (YouTube, Gmail, Twitter/X,
                // anything React/Vue-based) to lay out and behave correctly.
                // These were missing entirely before, which is the main
                // reason complex sites rendered broken or refused to load
                // their full desktop-grade UI/scripts.
                useWideViewPort = true
                loadWithOverviewMode = true
                allowContentAccess = true
                allowFileAccess = false // security: no arbitrary file:// reads
                loadsImagesAutomatically = true
                textZoom = 100
                setRenderPriority(android.webkit.WebSettings.RenderPriority.HIGH)
            }

            // Hardware-accelerated layer for smooth scrolling/video on heavy
            // pages (matches the Activity's android:hardwareAccelerated flag).
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.setAcceptCookie(true) // Astra's own tracking-protection layer decides third-party blocking per-request; site login state needs first-party cookies even in private mode.
            cookieManager.setAcceptThirdPartyCookies(this, !isPrivate)
        }
    }
}
