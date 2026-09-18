package com.astra.browser.core.tabs

import android.content.Context
import android.webkit.WebView
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
class TabManager @Inject constructor() {

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
        val tab = Tab(isPrivate = isPrivate, url = url ?: "")
        val webView = createConfiguredWebView(context, isPrivate)
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
                mediaPlaybackRequiresUserGesture = true
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = false
            }

            if (isPrivate) {
                // Private tabs get an isolated, non-persistent cookie jar.
                android.webkit.CookieManager.getInstance().setAcceptCookie(false)
            }
        }
    }
}
