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
                isBlankTab = false,
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
        injectMediaPlaybackWatcher(view)
        onPageFinished(tabId, url)
    }

    /**
     * Lightweight JS hook so Astra knows when a <video>/<audio> element on
     * the page starts or stops playing. This is what lets "Background
     * playback" (Settings) know whether there's actually anything to keep
     * alive — without it we'd have to guess, or keep every tab alive always
     * (a real battery drain), or never support it at all.
     */
    private fun injectMediaPlaybackWatcher(view: WebView) {
        view.evaluateJavascript(
            """
            (function() {
                if (window.__astraMediaWatcherInstalled) return;
                window.__astraMediaWatcherInstalled = true;
                function attach(el) {
                    el.addEventListener('play', function() { AstraMedia.onPlaybackState(true); });
                    el.addEventListener('pause', function() { AstraMedia.onPlaybackState(false); });
                    el.addEventListener('ended', function() { AstraMedia.onPlaybackState(false); });
                }
                document.querySelectorAll('video, audio').forEach(attach);
                new MutationObserver(function(mutations) {
                    mutations.forEach(function(m) {
                        m.addedNodes && m.addedNodes.forEach(function(node) {
                            if (node.tagName === 'VIDEO' || node.tagName === 'AUDIO') attach(node);
                            if (node.querySelectorAll) node.querySelectorAll('video, audio').forEach(attach);
                        });
                    });
                }).observe(document.body || document.documentElement, { childList: true, subtree: true });
            })();
            """.trimIndent(),
            null
        )
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        // shouldInterceptRequest is called by Chromium on a background
        // thread (not the main/UI thread). Calling ANY WebView method
        // (view.getUrl(), view.getTitle(), etc.) from here throws --
        // WebView enforces that all its methods run on the thread that
        // created it. This was the actual crash: `view.url` below used to
        // call the real WebView.getUrl() getter from that background
        // thread on every single request the page made (i.e. constantly),
        // which is why it crashed on essentially any navigation/search.
        //
        // Fix: read the page's current URL from TabManager's state
        // instead -- it's a plain in-memory value backed by a StateFlow,
        // safe to read from any thread, no WebView call involved.
        val pageUrl = tabManager.tabs.value.find { it.id == tabId }?.url ?: ""
        val pageOrigin = contentBlocker.originOf(pageUrl)
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
