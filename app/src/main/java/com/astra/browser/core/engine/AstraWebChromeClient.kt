package com.astra.browser.core.engine

import android.os.Message
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.astra.browser.core.tabs.TabManager
import com.astra.browser.privacy.permissions.PermissionManager

class AstraWebChromeClient(
    private val tabId: String,
    private val tabManager: TabManager,
    private val permissionManager: PermissionManager,
    private val onFullscreenChange: (View?, CustomViewCallback?) -> Unit,
    /** Called when a page asks for a new window/tab (target=_blank, window.open). */
    private val onNewWindowRequested: (url: String, userGesture: Boolean) -> Unit,
    private val popupsBlocked: () -> Boolean,
    /** True if this popup target is a known ad/tracker host and should be dropped (and counted). */
    private val isAdPopupTarget: (url: String) -> Boolean = { false }
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        tabManager.updateTab(tabId) { it.copy(loadProgress = newProgress, isLoading = newProgress < 100) }
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        super.onReceivedTitle(view, title)
        if (!title.isNullOrBlank()) {
            tabManager.updateTab(tabId) { it.copy(title = title) }
        }
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        onFullscreenChange(view, callback)
    }

    override fun onHideCustomView() {
        onFullscreenChange(null, null)
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        permissionManager.handleWebPermissionRequest(request)
    }

    /**
     * setSupportMultipleWindows(true) is set on every WebView, which means
     * links with target="_blank" and window.open() call THIS method. It was
     * never overridden, so WebView returned false and those links did
     * nothing at all -- a big part of "links don't open".
     *
     * Trick: give the requester a throw-away WebView, let it resolve the
     * real target URL (the transport hands it to us), then open that URL in
     * a proper new tab and discard the throw-away.
     *
     * Popups without a user gesture are dropped when popup blocking is on.
     */
    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message
    ): Boolean {
        if (popupsBlocked() && !isUserGesture) return false

        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        val temp = WebView(view.context)
        temp.webViewClient = object : android.webkit.WebViewClient() {
            private var handled = false
            override fun shouldOverrideUrlLoading(v: WebView, request: android.webkit.WebResourceRequest): Boolean {
                deliver(v, request.url.toString())
                return true
            }
            override fun onPageStarted(v: WebView, url: String, favicon: android.graphics.Bitmap?) {
                deliver(v, url)
            }
            private fun deliver(v: WebView, url: String) {
                if (handled || url.isBlank() || url == "about:blank") return
                handled = true
                v.stopLoading()
                // Ad popups (known ad-network hosts) are dropped instead of
                // opening a new tab; genuine link targets open normally.
                if (!(popupsBlocked() && isAdPopupTarget(url))) {
                    onNewWindowRequested(url, isUserGesture)
                }
                v.post { v.destroy() }
            }
        }
        transport.webView = temp
        resultMsg.sendToTarget()
        return true
    }
}
