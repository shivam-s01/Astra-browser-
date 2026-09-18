package com.astra.browser.core.engine

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
    private val onFullscreenChange: (View?, CustomViewCallback?) -> Unit
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

    /**
     * Video sites (YouTube etc.) call this to go fullscreen. Previously
     * onFullscreenChange was wired to an empty lambda `{ }`, which meant:
     *  - the custom view was never actually attached anywhere, so
     *    fullscreen video had nowhere to render
     *  - `callback` (CustomViewCallback) was silently dropped and its
     *    onCustomViewHidden() was NEVER invoked
     * The second part is the actual crash cause: WebView/Chromium keeps
     * internal state tied to that callback lifecycle. Never resolving it
     * left the WebView in a broken internal state that could surface as a
     * crash on the next navigation (e.g. searching right after visiting a
     * video-heavy site like YouTube).
     */
    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        onFullscreenChange(view, callback)
    }

    override fun onHideCustomView() {
        onFullscreenChange(null, null)
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        permissionManager.handleWebPermissionRequest(request)
    }
}
