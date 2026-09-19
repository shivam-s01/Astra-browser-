package com.astra.browser.core.tabs

import android.content.Context
import android.webkit.WebView
import com.astra.browser.core.media.MediaPlaybackBridge
import com.astra.browser.domain.model.Tab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
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

    init {
        // Notification/lock-screen "Pause" -> pause every <video>/<audio> on every tab.
        mediaPlaybackBridge.pauseAllHandler = {
            webViews.values.forEach { wv ->
                wv.post {
                    wv.evaluateJavascript(
                        "document.querySelectorAll('video,audio').forEach(function(e){try{e.pause()}catch(x){}})",
                        null
                    )
                }
            }
        }
        // Notification/lock-screen "Play" -> resume the currently-playing (or
        // most recently playing) element on the active tab. We only resume
        // the active tab so we don't start audio on a background tab the
        // user never actually asked to play.
        mediaPlaybackBridge.playAllHandler = {
            withActiveWebView { wv ->
                wv.evaluateJavascript(
                    """
                    (function(){
                        var els = document.querySelectorAll('video,audio');
                        var target = null;
                        els.forEach(function(e){ if (!target && e.currentTime > 0) target = e; });
                        if (!target && els.length) target = els[0];
                        if (target) { try { target.play(); } catch(x) {} }
                    })();
                    """.trimIndent(),
                    null
                )
            }
        }
        // Lock-screen skip-forward/back -> seek the active tab's playing element.
        mediaPlaybackBridge.seekHandler = { seconds ->
            withActiveWebView { wv ->
                wv.evaluateJavascript(
                    """
                    (function(){
                        var els = document.querySelectorAll('video,audio');
                        var target = null;
                        els.forEach(function(e){ if (!target && !e.paused) target = e; });
                        if (!target && els.length) target = els[0];
                        if (target) { try { target.currentTime = Math.max(0, target.currentTime + ($seconds)); } catch(x) {} }
                    })();
                    """.trimIndent(),
                    null
                )
            }
        }
    }

    /** Runs [block] on the active tab's WebView, posted onto its own thread. */
    private fun withActiveWebView(block: (WebView) -> Unit) {
        val wv = _activeTabId.value?.let { webViews[it] } ?: return
        wv.post { block(wv) }
    }

    /**
     * App went to the background. Freeze every tab (saves CPU/heat), unless
     * [keepMediaAlive] is true, in which case ALL WebViews stay resumed so
     * whichever one is actually playing audio keeps playing.
     *
     * This is deliberately simple and NEVER tries to first ask JS "is
     * something actually playing right now" before deciding whether to
     * freeze. That approach (tried and removed) queries every WebView via
     * evaluateJavascript, which is asynchronous, then waits up to 400ms for
     * an answer before onStop() can finish -- but onStop() is a synchronous
     * lifecycle callback the OS does not wait around for, and the moment it
     * returns, Android is free to fully suspend the process. In practice
     * that query routinely lost the race (or hit its own timeout), silently
     * fell back to "nothing is playing", and froze the WebView -- which is
     * exactly the "song stops the instant I background the app" bug. There
     * is no async-query version of this that can be made reliable, because
     * the deadline (onStop returning) is not something we control.
     *
     * The only correct fix is to never gate the freeze decision on a query
     * at all: if the user has background playback on, every WebView simply
     * stays resumed while backgrounded, full stop. A WebView with nothing
     * playing costs a small, fixed amount of idle CPU (it's not rendering,
     * screen is off) -- nowhere near what a query-and-maybe-freeze race
     * costs when it guesses wrong and kills real playback.
     */
    fun onAppBackgrounded(keepMediaAlive: Boolean) {
        if (keepMediaAlive) {
            webViews.values.forEach { wv ->
                wv.onResume()
                wv.resumeTimers()
            }
        } else {
            webViews.values.forEach { it.onPause() }
            // WebView.pauseTimers() is a PROCESS-WIDE static call, not
            // per-instance -- calling it once affects every WebView in the
            // app. Calling it on more than one instance would be redundant,
            // not "more paused".
            webViews.values.firstOrNull()?.pauseTimers()
        }
    }

    fun onAppForegrounded() {
        webViews.values.forEach { it.onResume() }
        webViews.values.firstOrNull()?.resumeTimers()
    }

    /** Frees memory of tabs not looked at for a while (lightweight / cool). */
    fun trimBackgroundTabs(level: Int) {
        val active = _activeTabId.value
        webViews.forEach { (id, wv) ->
            if (id != active) {
                wv.clearCache(false)
                if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) wv.freeMemory()
            }
        }
    }

    val activeTab: Tab?
        get() = _tabs.value.find { it.id == _activeTabId.value }

    fun getWebView(tabId: String): WebView? = webViews[tabId]

    /**
     * Pauses whatever tab is about to stop being the active one, unless
     * it's confirmed still playing audio/video. Shared by createTab() and
     * switchTo() -- opening a new tab is exactly as much of an "away from
     * this tab" event as switching to an existing one, and was previously
     * left out, so opening tabs while a heavy site sat in the background
     * still burned CPU on it.
     */
    private fun pauseIfNoLongerActive(tabId: String?) {
        if (tabId == null) return
        if (!mediaPlaybackBridge.isPlaying(tabId)) {
            webViews[tabId]?.let { runCatching { it.onPause() } }
        }
    }

    /**
     * Called by AstraWebViewClient.onRenderProcessGone when a tab's renderer
     * process crashes -- most likely on a heavy/ad-dense site overloading a
     * low-end device's renderer. Per WebView's own contract, a WebView whose
     * render process died is permanently unusable: calling destroy() (or
     * almost anything else) on it can itself throw/crash, so the dead
     * instance is only detached from its parent view and dropped, never
     * interacted with further. A brand-new WebView is created in its place
     * under the SAME tab ID, so the tab survives (title, position in the
     * tab list, etc.) even though the underlying renderer had to restart --
     * exactly what real browsers do here instead of taking the whole app
     * down.
     */
    fun replaceCrashedWebView(tabId: String, deadWebView: WebView) {
        val tab = _tabs.value.find { it.id == tabId } ?: return
        val wasActive = _activeTabId.value == tabId
        val urlToRestore = tab.url

        runCatching { (deadWebView.parent as? android.view.ViewGroup)?.removeView(deadWebView) }

        val context = deadWebView.context
        val freshWebView = createConfiguredWebView(context, tab.isPrivate)
        freshWebView.addJavascriptInterface(mediaPlaybackBridge.jsInterfaceFor(tabId), "AstraMedia")
        freshWebView.addJavascriptInterface(BackgroundStateBridge(), "AstraBackgroundState")
        webViews[tabId] = freshWebView
        webViewConfigurer?.invoke(tabId, freshWebView)

        // A brand-new WebView has no load history of its own (its `update`
        // block in AstraWebViewHost would otherwise think tab.url is
        // already loaded and skip it), so load directly here as the
        // immediate, authoritative restore rather than relying on that
        // comparison to notice on the next recomposition.
        if (urlToRestore.isNotBlank()) {
            freshWebView.loadUrl(urlToRestore)
        }
        if (!wasActive) {
            // Off-screen crashed tab: keep it frozen like any other
            // background tab until the user actually switches to it,
            // rather than burning CPU re-rendering a page nobody's looking
            // at yet.
            runCatching { freshWebView.onPause() }
        }
        updateTab(tabId) { it.copy(isLoading = urlToRestore.isNotBlank()) }
    }

    fun createTab(context: Context, isPrivate: Boolean = false, url: String? = null): Tab {
        val previous = _activeTabId.value
        val tab = Tab(isPrivate = isPrivate, url = url ?: "", isBlankTab = url.isNullOrBlank())
        val webView = createConfiguredWebView(context, isPrivate)
        webView.addJavascriptInterface(mediaPlaybackBridge.jsInterfaceFor(tab.id), "AstraMedia")
        webView.addJavascriptInterface(BackgroundStateBridge(), "AstraBackgroundState")
        webViews[tab.id] = webView
        webViewConfigurer?.invoke(tab.id, webView)
        _tabs.update { it + tab }
        tabUrlCache[tab.id] = tab.url
        _activeTabId.value = tab.id
        if (!url.isNullOrBlank()) webView.loadUrl(url)
        pauseIfNoLongerActive(previous)
        return tab
    }

    /**
     * Exposes MediaPlaybackBridge.keepPlayingInBackground to page JS so the
     * document-start visibility-spoof script can read the current value
     * synchronously (a WebView JS interface call is synchronous), without
     * needing a round trip through evaluateJavascript on every toggle.
     */
    private inner class BackgroundStateBridge {
        @android.webkit.JavascriptInterface
        fun keepPlayingInBackground(): Boolean = mediaPlaybackBridge.keepPlayingInBackground.value
    }

    fun closeTab(tabId: String): Tab? {
        val closedTab = _tabs.value.find { it.id == tabId }
        webViews[tabId]?.apply {
            stopLoading()
            destroy()
        }
        webViews.remove(tabId)
        tabUrlCache.remove(tabId)
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
        tabUrlCache.clear()
        _tabs.value = emptyList()
        _activeTabId.value = null
    }

    fun closeAllPrivateTabs() {
        val privateIds = _tabs.value.filter { it.isPrivate }.map { it.id }
        privateIds.forEach { closeTab(it) }
    }

    /**
     * Switching tabs is where a LOT of unnecessary heat/battery drain was
     * coming from: this used to only flip which tab is considered "active"
     * in the UI, while every WebView -- including every tab NOT on screen
     * -- kept running its JS timers, animations, and rendering at full
     * speed indefinitely. Open 4-5 heavy sites (exactly the ad-heavy
     * download-portal case this browser is built for) and all of them were
     * burning CPU simultaneously even though only one was ever visible.
     *
     * Fix: pause every other tab's WebView when switching away from it,
     * unless MediaPlaybackBridge confirms it's actually playing audio/video
     * (that tab needs to keep running so the sound doesn't cut out) or
     * background playback would otherwise keep it alive anyway. The tab
     * being switched TO always resumes.
     */
    fun switchTo(tabId: String) {
        if (_tabs.value.any { it.id == tabId }) {
            val previous = _activeTabId.value
            _activeTabId.value = tabId
            updateTab(tabId) { it.copy(lastAccessedAt = System.currentTimeMillis()) }

            webViews[tabId]?.let { runCatching { it.onResume() } }
            if (previous != tabId) pauseIfNoLongerActive(previous)
        }
    }

    fun duplicateTab(context: Context, tabId: String): Tab? {
        val source = _tabs.value.find { it.id == tabId } ?: return null
        return createTab(context, isPrivate = source.isPrivate, url = source.url)
    }

    fun updateTab(tabId: String, transform: (Tab) -> Tab) {
        _tabs.update { list -> list.map { if (it.id == tabId) transform(it) else it } }
        _tabs.value.find { it.id == tabId }?.let { tabUrlCache[tabId] = it.url }
    }

    // O(1) URL lookup for shouldInterceptRequest, which previously did a
    // linear scan of the full tab list on EVERY single network sub-resource
    // of every page (every image, script, font, XHR -- easily hundreds on a
    // heavy/ad-dense site). With more tabs open, that scan got proportionally
    // slower on exactly the hot path that runs most often. A plain HashMap
    // keyed by tab ID, kept in sync wherever a tab's URL can change, turns
    // that into a single map lookup regardless of tab count.
    private val tabUrlCache = ConcurrentHashMap<String, String>()

    /** O(1) equivalent of `tabs.value.find { it.id == tabId }?.url` for the WebView IO-thread hot path. */
    fun urlForTab(tabId: String): String? = tabUrlCache[tabId]

    fun tabCount(includePrivate: Boolean = true): Int =
        _tabs.value.count { includePrivate || !it.isPrivate }

    /**
     * Runs our popunder/click-hijack guard via WebViewCompat's
     * addDocumentStartJavaScript, which -- unlike evaluateJavascript from
     * onPageStarted -- is guaranteed to execute before ANY of the page's
     * own scripts, on every navigation including SPA-style ones. This is
     * what actually makes "Download/Watch button doesn't respond" sites
     * work: their ad script no longer gets to install its click hijack
     * before we've already neutralized window.open() and overlay clicks.
     *
     * Falls back silently on devices/WebView versions that don't support
     * the feature (older WebView) -- AstraWebViewClient's onPageStarted
     * injection still runs as a best-effort second layer there.
     */
    private fun installDocumentStartAntiPopunderGuard(webView: WebView) {
        if (!androidx.webkit.WebViewFeature.isFeatureSupported(
                androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT
            )
        ) return
        androidx.webkit.WebViewCompat.addDocumentStartJavaScript(
            webView,
            ANTI_POPUNDER_JS,
            setOf("*")
        )
    }

    /**
     * YouTube (and most other video/music sites) listen for the Page
     * Visibility API and deliberately pause their own <video>/<audio> the
     * instant document.hidden becomes true -- which is exactly what happens
     * the moment the user locks the screen or switches app, even though
     * we've already told Chromium to keep this WebView's timers/rendering
     * alive (onResume + resumeTimers in TabManager.onAppBackgrounded).
     * That self-pause is why "background music" would start, then cut out
     * within a second of the screen turning off.
     *
     * Fix: while AstraBackgroundState.keepPlayingInBackground() is true
     * (mirrors the user's "Background playback" setting, kept live by
     * MediaPlaybackBridge), document.hidden / document.visibilityState /
     * document.hasFocus() are made to always report "visible" / "focused"
     * to page JS, and the 'visibilitychange' event is suppressed. The
     * WebView is still genuinely backgrounded at the OS level (no extra
     * rendering happens) -- only what the PAGE'S JS can observe changes, so
     * it never decides to pause itself.
     */
    private fun installDocumentStartVisibilitySpoof(webView: WebView) {
        if (!androidx.webkit.WebViewFeature.isFeatureSupported(
                androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT
            )
        ) return
        androidx.webkit.WebViewCompat.addDocumentStartJavaScript(
            webView,
            VISIBILITY_SPOOF_JS,
            setOf("*")
        )
    }

    private fun createConfiguredWebView(context: Context, isPrivate: Boolean): WebView {
        return WebView(context).apply {
            installDocumentStartAntiPopunderGuard(this)
            installDocumentStartVisibilitySpoof(this)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = !isPrivate
                databaseEnabled = !isPrivate
                cacheMode = if (isPrivate) android.webkit.WebSettings.LOAD_NO_CACHE
                            else android.webkit.WebSettings.LOAD_DEFAULT
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                // COMPATIBILITY_MODE (not NEVER_ALLOW): matches what real
                // desktop/mobile Chrome does today -- blocks genuinely
                // dangerous active mixed content (scripts, iframes over
                // HTTP) but tolerates passive content (images, some media)
                // that's still common on older/less-maintained sites,
                // notably the ad-heavy download/file-host sites this
                // browser is regularly used on. NEVER_ALLOW is stricter
                // than any mainstream browser ships by default and was
                // silently breaking pages on exactly those sites (missing
                // images, broken layout, dead-looking download buttons).
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                // Video sites (YouTube etc.) need this false, or autoplay /
                // inline playback / fullscreen video breaks on first tap.
                mediaPlaybackRequiresUserGesture = false
                setSupportMultipleWindows(true)
                // MUST be true: with false, WebView silently swallows EVERY
                // window.open() -- including the ones real "Download / Watch /
                // Get Links" buttons on link-hub sites use. Unwanted popups
                // are still filtered by AstraWebChromeClient.onCreateWindow
                // (no-gesture popups dropped) plus the JS guard below.
                javaScriptCanOpenWindowsAutomatically = true

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
                // (setRenderPriority is deprecated & a no-op on modern WebView;
                // removed. Forcing HIGH only encouraged extra CPU work.)
                offscreenPreRaster = false // don't rasterize off-screen content -> less GPU/heat
                // Google Safe Browsing shows a full, non-dismissible-by-
                // default red interstitial and can outright refuse to load
                // a page the instant it's flagged -- and it flags download
                // portals / streaming / anime-dub sites constantly, because
                // their AD NETWORKS occasionally serve malicious payloads
                // even when the site's own content is completely fine. From
                // the user's side this looks exactly like "heavy sites just
                // won't open" with no visible reason why. Astra's own
                // ad/tracker blocking (ContentBlocker) is the actual
                // protection layer here -- it stops the malicious ad
                // requests themselves rather than refusing the whole page
                // because of them. Real Chromium-based browsers built for
                // this kind of site (Brave included) ship their own
                // phishing/malware protection instead of Google's for
                // exactly this reason.
                safeBrowsingEnabled = false
            }

            // Default layer type (hardware via the Activity's flag). Forcing
            // LAYER_TYPE_HARDWARE on the WebView itself allocates an extra
            // full-screen GPU texture per tab -> more memory and heat.
            overScrollMode = android.view.View.OVER_SCROLL_NEVER

            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.setAcceptCookie(true) // Astra's own tracking-protection layer decides third-party blocking per-request; site login state needs first-party cookies even in private mode.
            cookieManager.setAcceptThirdPartyCookies(this, !isPrivate)
        }
    }

    private companion object {
        /**
         * Neutralizes the three click-hijack tricks that make buttons on
         * ad-heavy sites (download/streaming/anime-dub sites especially)
         * appear completely unresponsive:
         *  1. window.open() popunders fired without a genuine, current tap.
         *  2. Invisible full-viewport overlay elements stacked above the
         *     real button that steal the tap.
         *  3. Href-swap-on-click tricks that briefly point a link at an ad
         *     URL only during the click event, then restore it.
         * Runs before any page script via addDocumentStartJavaScript.
         */
        const val ANTI_POPUNDER_JS = """
            (function() {
                if (window.__astraPopunderGuardInstalled) return;
                window.__astraPopunderGuardInstalled = true;

                // Any real user input counts as a gesture. Touch devices fire
                // touchstart/touchend/click, NOT always pointerdown first, and
                // slow sites open the window well after 1.2 s, so track all of
                // them and allow a generous window.
                var lastGesture = 0;
                var opensThisGesture = 0;
                function markGesture() {
                    var now = Date.now();
                    // One physical tap fires touchstart/touchend/mousedown/click
                    // within a few ms of each other. Only treat it as a NEW tap
                    // (and reset the per-tap open budget) after a real gap.
                    if (now - lastGesture > 700) opensThisGesture = 0;
                    lastGesture = now;
                }
                ['pointerdown','touchstart','touchend','mousedown','click','keydown'].forEach(function(t) {
                    document.addEventListener(t, markGesture, true);
                });

                var nativeOpen = window.open;
                window.open = function(url, target, features) {
                    var withinGesture = (Date.now() - lastGesture) < 4000;
                    // No user tap at all -> classic popunder. Drop it.
                    if (!withinGesture) return null;
                    // ONE new tab per tap. A second window.open() from the same
                    // tap is almost always an ad, so it is dropped here.
                    if (opensThisGesture >= 1) return null;
                    opensThisGesture++;
                    // Blank opens are passed through too: some sites open
                    // about:blank first and then set its location.
                    return nativeOpen.call(window, url, target, features);
                };

                function isHijackOverlay(el) {
                    if (!el || el === document.body || el === document.documentElement) return false;
                    var cs = window.getComputedStyle(el);
                    if (cs.position !== 'fixed' && cs.position !== 'absolute') return false;
                    var r = el.getBoundingClientRect();
                    var coversViewport = r.width >= window.innerWidth * 0.8 &&
                                          r.height >= window.innerHeight * 0.8;
                    if (!coversViewport) return false;
                    var z = parseInt(cs.zIndex, 10) || 0;
                    // Require near-total transparency (not just low opacity)
                    // AND a transparent background specifically -- a legit
                    // full-screen modal/lightbox with a dim backdrop
                    // (opacity ~0.1-0.5, solid rgba background) was getting
                    // misclassified as a hijack overlay and killed on click,
                    // which is what made real download buttons on some sites
                    // look completely dead.
                    var nearInvisible = parseFloat(cs.opacity) < 0.05 &&
                                         cs.backgroundColor === 'rgba(0, 0, 0, 0)';
                    return z > 1 && nearInvisible;
                }

                document.addEventListener('click', function(ev) {
                    var el = ev.target;
                    var depth = 0;
                    while (el && depth < 4) {
                        if (isHijackOverlay(el)) {
                            el.style.pointerEvents = 'none';
                            el.style.display = 'none';
                            var real = document.elementFromPoint(ev.clientX, ev.clientY);
                            if (real && real !== el) {
                                real.dispatchEvent(new MouseEvent('click', {
                                    bubbles: true, cancelable: true,
                                    clientX: ev.clientX, clientY: ev.clientY
                                }));
                            }
                            ev.stopImmediatePropagation();
                            ev.preventDefault();
                            return;
                        }
                        el = el.parentElement;
                        depth++;
                    }
                }, true);

                document.addEventListener('mousedown', function(ev) {
                    var a = ev.target && ev.target.closest ? ev.target.closest('a[href]') : null;
                    if (a) a.dataset.__astraHref = a.getAttribute('href');
                }, true);
                document.addEventListener('click', function(ev) {
                    var a = ev.target && ev.target.closest ? ev.target.closest('a[href]') : null;
                    if (a && a.dataset.__astraHref && a.getAttribute('href') !== a.dataset.__astraHref) {
                        a.setAttribute('href', a.dataset.__astraHref);
                    }
                }, true);
            })();
        """

        /**
         * Spoofs the Page Visibility API so sites like YouTube don't pause
         * their own playback when the app is backgrounded / screen locked,
         * as long as the user has "Background playback" enabled. Reads the
         * live flag via the synchronous AstraBackgroundState JS interface
         * (added alongside AstraMedia on every WebView) rather than a
         * one-time snapshot, so toggling the setting takes effect
         * immediately on already-open tabs/pages without a reload.
         *
         * document.hidden / document.webkitHidden and
         * document.visibilityState are redefined as getters; hasFocus() is
         * overridden; and any 'visibilitychange' / 'webkitvisibilitychange'
         * listener the page adds is only actually invoked when we are NOT
         * spoofing (i.e. background playback is off, or the WebView is
         * genuinely visible) -- so real visibility changes (switching tabs
         * within Astra, etc.) still work normally when background playback
         * isn't in play.
         */
        const val VISIBILITY_SPOOF_JS = """
            (function() {
                if (window.__astraVisibilitySpoofInstalled) return;
                window.__astraVisibilitySpoofInstalled = true;

                function shouldSpoof() {
                    try { return !!(window.AstraBackgroundState && AstraBackgroundState.keepPlayingInBackground()); }
                    catch (e) { return false; }
                }

                var nativeHiddenDesc = Object.getOwnPropertyDescriptor(Document.prototype, 'hidden');
                var nativeStateDesc = Object.getOwnPropertyDescriptor(Document.prototype, 'visibilityState');
                var nativeHasFocus = Document.prototype.hasFocus;

                try {
                    Object.defineProperty(document, 'hidden', {
                        configurable: true,
                        get: function() {
                            if (shouldSpoof()) return false;
                            return nativeHiddenDesc && nativeHiddenDesc.get ? nativeHiddenDesc.get.call(document) : false;
                        }
                    });
                } catch (e) {}

                try {
                    Object.defineProperty(document, 'visibilityState', {
                        configurable: true,
                        get: function() {
                            if (shouldSpoof()) return 'visible';
                            return nativeStateDesc && nativeStateDesc.get ? nativeStateDesc.get.call(document) : 'visible';
                        }
                    });
                } catch (e) {}

                try {
                    document.hasFocus = function() {
                        if (shouldSpoof()) return true;
                        return nativeHasFocus.call(document);
                    };
                } catch (e) {}

                // Intercept visibilitychange listeners: while spoofing, the
                // event simply never fires for page-registered handlers, so
                // a site's own "pause on hide" handler never runs.
                var nativeAdd = EventTarget.prototype.addEventListener;
                EventTarget.prototype.addEventListener = function(type, listener, options) {
                    if ((type === 'visibilitychange' || type === 'webkitvisibilitychange') && this === document) {
                        var wrapped = function(ev) {
                            if (shouldSpoof()) return;
                            return listener.apply(this, arguments);
                        };
                        return nativeAdd.call(this, type, wrapped, options);
                    }
                    return nativeAdd.call(this, type, listener, options);
                };
            })();
        """
    }
}
