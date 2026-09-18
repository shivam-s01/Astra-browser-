package com.astra.browser.core.engine

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.*
import com.astra.browser.core.tabs.TabManager
import com.astra.browser.privacy.blocker.ContentBlocker

/**
 * Drives tab state from real WebView lifecycle callbacks and routes every
 * outgoing request through ContentBlocker.
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
        // Install media hooks as early as possible; YouTube-style SPAs never
        // fire a full page load again after the first one.
        installMediaWatcher(view)
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
        installMediaWatcher(view)
        injectCosmeticFilter(view, url)
        onPageFinished(tabId, url)
    }

    /** SPA route changes (YouTube etc.) update the URL without a page load. */
    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        tabManager.updateTab(tabId) {
            it.copy(
                url = url,
                canGoBack = view.canGoBack(),
                canGoForward = view.canGoForward()
            )
        }
    }

    /**
     * Hides the empty boxes ad networks leave behind. Only when ad blocking
     * is on and this site hasn't been exempted.
     */
    private fun injectCosmeticFilter(view: WebView, url: String) {
        val origin = contentBlocker.originOf(url)
        if (!contentBlocker.adBlockingOn() || !contentBlocker.isEnabledFor(origin)) return
        val css = contentBlocker.cosmeticCss().replace("\\", "\\\\").replace("'", "\\'")
        view.evaluateJavascript(
            """
            (function(){
                if (document.getElementById('__astra_cosmetic')) return;
                var s = document.createElement('style');
                s.id = '__astra_cosmetic';
                s.textContent = '$css';
                (document.head || document.documentElement).appendChild(s);
            })();
            """.trimIndent(),
            null
        )
    }

    /**
     * Reports <video>/<audio> play/pause to the app so Background playback
     * knows if anything is actually playing. Idempotent, and also watches
     * for elements added later (SPAs).
     */
    private fun installMediaWatcher(view: WebView) {
        view.evaluateJavascript(
            """
            (function() {
                if (window.__astraMediaWatcherInstalled) return;
                window.__astraMediaWatcherInstalled = true;
                function report() {
                    var playing = false;
                    document.querySelectorAll('video, audio').forEach(function(el) {
                        if (!el.paused && !el.ended && el.readyState > 2) playing = true;
                    });
                    try { AstraMedia.onPlaybackState(playing); } catch (e) {}
                }
                function attach(el) {
                    if (el.__astraAttached) return;
                    el.__astraAttached = true;
                    ['play','playing','pause','ended','emptied','waiting'].forEach(function(ev) {
                        el.addEventListener(ev, report, true);
                    });
                }
                document.querySelectorAll('video, audio').forEach(attach);
                new MutationObserver(function(mutations) {
                    mutations.forEach(function(m) {
                        m.addedNodes && m.addedNodes.forEach(function(node) {
                            if (node.tagName === 'VIDEO' || node.tagName === 'AUDIO') attach(node);
                            if (node.querySelectorAll) node.querySelectorAll('video, audio').forEach(attach);
                        });
                    });
                }).observe(document.documentElement, { childList: true, subtree: true });
                report();
            })();
            """.trimIndent(),
            null
        )
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        // Runs on a Chromium IO thread: never call WebView methods here.
        // Read the page URL from TabManager's in-memory state instead.
        val pageUrl = tabManager.tabs.value.find { it.id == tabId }?.url ?: ""
        val pageOrigin = contentBlocker.originOf(pageUrl)
        contentBlocker.intercept(tabId, pageOrigin, request)?.let { return it }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        tabManager.updateTab(tabId) { it.copy(isSecure = false, isLoading = false) }
    }

    /**
     * Previously anything that wasn't http/https returned `true` (= "handled")
     * WITHOUT doing anything, so those links silently died. Now:
     *  - http/https  -> WebView loads it
     *  - intent://   -> parsed, fallback URL loaded if the app isn't installed
     *  - mailto/tel/sms/market/etc -> handed to the system
     *  - blob:/data:/about:/javascript: -> left to the WebView
     */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        val scheme = request.url.scheme?.lowercase() ?: return false

        return when (scheme) {
            "http", "https", "about", "blob", "data", "javascript", "file" -> false
            "intent" -> handleIntentScheme(view, url)
            else -> launchExternal(view, request.url)
        }
    }

    private fun handleIntentScheme(view: WebView, url: String): Boolean {
        return try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
                selector = null
            }
            view.context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            // App not installed: use the page's own fallback URL if it has one.
            val fallback = runCatching {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url")
            }.getOrNull()
            if (!fallback.isNullOrBlank()) view.loadUrl(fallback)
            true
        } catch (e: Exception) {
            true
        }
    }

    private fun launchExternal(view: WebView, uri: Uri): Boolean {
        return try {
            view.context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            true
        }
    }
}
