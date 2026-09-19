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

    /** Host the current Shield counters belong to (see onPageStarted). */
    private var lastStatsHost: String? = null

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        // Reset the per-page counters only when we land on a DIFFERENT site.
        // Resetting on every onPageStarted zeroed the numbers in the middle of
        // redirect chains (link hubs bounce through several URLs), so the
        // Shield popup showed 0 for pages that had actually blocked plenty.
        val newHost = contentBlocker.originOf(url)
        if (newHost != lastStatsHost) {
            contentBlocker.resetCountForTab(tabId)
            lastStatsHost = newHost
        }
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
        // Note: the popunder/click-hijack guard itself now runs via
        // WebViewCompat.addDocumentStartJavaScript (installed once per
        // WebView in TabManager), which guarantees it executes before the
        // page's own scripts -- something evaluateJavascript() from here
        // cannot guarantee, since onPageStarted can already fire after the
        // page's <head> scripts have started running.

        // Install media hooks as early as possible; YouTube-style SPAs never
        // fire a full page load again after the first one.
        // A fresh page starts with window.__astra at its built-in defaults;
        // push the user's real settings in straight away so a page loaded
        // while "Play in background" / "Skip YouTube ads" is OFF is never
        // spoofed or stripped.
        tabManager.pushFlags(view)
        installMediaWatcher(view)
        applyDesktopViewport(view)
        injectCosmeticFilter(view, url)
    }

    /**
     * Real "Desktop site". The UA string + WebView settings are switched in
     * AstraWebViewHost; here we only flip the per-page flag that the
     * document-start viewport script (PageScripts.DESKTOP_VIEWPORT_JS) reads.
     * No MutationObserver over the whole DOM any more -- that re-ran on every
     * DOM change of heavy pages and was a real source of heat.
     */
    private fun applyDesktopViewport(view: WebView) {
        val desktop = tabManager.tabs.value.firstOrNull { it.id == tabId }?.desktopSiteEnabled == true
        tabManager.setDesktopFlag(view, desktop)
        if (desktop) {
            view.evaluateJavascript(
                "window.dispatchEvent(new Event('load'))&&0;",
                null
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
        installMediaWatcher(view)
        applyDesktopViewport(view)
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
                var last = null;
                function report() {
                    var playing = false;
                    var els = document.getElementsByTagName('video');
                    for (var i = 0; i < els.length; i++) { var e = els[i]; if (!e.paused && !e.ended && e.readyState > 2) { playing = true; break; } }
                    if (!playing) {
                        els = document.getElementsByTagName('audio');
                        for (var j = 0; j < els.length; j++) { var a = els[j]; if (!a.paused && !a.ended && a.readyState > 2) { playing = true; break; } }
                    }
                    if (playing !== last) { last = playing; try { AstraMedia.onPlaybackState(playing); } catch (e) {} }
                }
                // Media events do not bubble, but they DO propagate in the capture phase
                // from document: ONE listener per event type covers every current and
                // future <video>/<audio>. No MutationObserver, no per-element wiring.
                ['play','playing','pause','ended','emptied','waiting','stalled'].forEach(function(ev) {
                    document.addEventListener(ev, function(e) {
                        var t = e.target && e.target.tagName;
                        if (t === 'VIDEO' || t === 'AUDIO') report();
                    }, true);
                });
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
        // O(1) lookup via TabManager.urlForTab (a HashMap) instead of a
        // linear scan of the full tab list -- this runs on EVERY single
        // sub-resource of every page (every image/script/font/XHR, easily
        // hundreds on a heavy/ad-dense download or streaming site), so it's
        // the hottest path in the whole content-blocking pipeline.
        val pageUrl = tabManager.urlForTab(tabId) ?: ""
        val pageOrigin = contentBlocker.originOf(pageUrl)
        contentBlocker.intercept(tabId, pageOrigin, request)?.let { return it }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        tabManager.updateTab(tabId) { it.copy(isSecure = false, isLoading = false) }
    }

    /**
     * Heavy/ad-dense sites (exactly the download-portal case this browser
     * is regularly used on) are the most likely pages to crash the
     * Chromium renderer process on a low-end device -- too many
     * simultaneous video/script/iframe loads exhausting the renderer's own
     * memory. Without overriding this, that crash takes the WHOLE APP down
     * with it (the system's default behavior). Returning true here tells
     * Android we've handled it: the crashed WebView instance is now unusable
     * per WebView's own contract and must not be interacted with further,
     * so it's swapped out for a fresh one in the same tab slot instead of
     * touching the dead instance.
     */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        tabManager.replaceCrashedWebView(tabId, view)
        return true
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
