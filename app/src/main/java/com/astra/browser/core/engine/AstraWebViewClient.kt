package com.astra.browser.core.engine

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.*
import com.astra.browser.core.tabs.TabManager
import com.astra.browser.privacy.blocker.ContentBlocker

/**
 * Drives tab state (loading, progress, secure indicator, back/forward
 * availability) from real WebView lifecycle callbacks, and routes every
 * outgoing request through ContentBlocker for genuine interception.
 */
class AstraWebViewClient(
    private val tabId: String,
    private val tabManager: TabManager,
    private val contentBlocker: ContentBlocker,
    private val onPageFinished: (String, String) -> Unit // tabId, url
) : WebViewClient() {

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        contentBlocker.resetCountForTab(tabId)
        tabManager.updateTab(tabId) {
            it.copy(
                url = url,
                isLoading = true,
                loadProgress = 0,
                isSecure = url.startsWith("https://"),
                canGoBack = view.canGoBack(),
                canGoForward = view.canGoForward()
            )
        }
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        tabManager.updateTab(tabId) {
            it.copy(
                url = url,
                title = view.title ?: it.title,
                isLoading = false,
                loadProgress = 100,
                isSecure = url.startsWith("https://"),
                canGoBack = view.canGoBack(),
                canGoForward = view.canGoForward(),
                trackersBlockedCount = contentBlocker.blockedCountForTab(tabId)
            )
        }
        onPageFinished(tabId, url)
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val pageOrigin = contentBlocker.originOf(view.url ?: "")
        contentBlocker.intercept(tabId, pageOrigin, request)?.let { return it }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        // Never silently proceed past a genuine cert error — cancel by
        // default and surface it as an error page rather than pretending
        // the connection is secure.
        handler.cancel()
        tabManager.updateTab(tabId) { it.copy(isSecure = false, isLoading = false) }
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        // Let the WebView handle standard web schemes; intent:// and other
        // app-invoking schemes are left for the platform to resolve.
        return if (url.startsWith("http://") || url.startsWith("https://")) {
            false
        } else {
            true
        }
    }
}
