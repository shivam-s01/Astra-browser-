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
    private val onFullscreenChange: (View?) -> Unit
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
        onFullscreenChange(view)
    }

    override fun onHideCustomView() {
        onFullscreenChange(null)
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        permissionManager.handleWebPermissionRequest(request)
    }
}
