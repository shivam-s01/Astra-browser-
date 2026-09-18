package com.astra.browser.ui.browser

import android.content.Context
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astra.browser.core.tabs.TabManager
import com.astra.browser.data.repository.BookmarkRepository
import com.astra.browser.data.repository.ClosedTabRepository
import com.astra.browser.data.repository.HistoryRepository
import com.astra.browser.data.store.SettingsStore
import com.astra.browser.domain.model.SearchEngine
import com.astra.browser.domain.model.Tab
import com.astra.browser.core.engine.AstraWebChromeClient
import com.astra.browser.core.engine.AstraWebViewClient
import com.astra.browser.privacy.blocker.ContentBlocker
import com.astra.browser.privacy.permissions.PermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val tabManager: TabManager,
    private val bookmarkRepository: BookmarkRepository,
    private val historyRepository: HistoryRepository,
    private val closedTabRepository: ClosedTabRepository,
    private val contentBlocker: ContentBlocker,
    private val permissionManager: PermissionManager,
    private val settingsStore: SettingsStore
) : ViewModel() {

    val tabs: StateFlow<List<Tab>> = tabManager.tabs
    val activeTabId: StateFlow<String?> = tabManager.activeTabId

    val searchEngine: StateFlow<SearchEngine> = settingsStore.searchEngine
        .map { name -> runCatching { SearchEngine.valueOf(name) }.getOrDefault(SearchEngine.GOOGLE) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchEngine.GOOGLE)

    val customSearchUrl: StateFlow<String> =
        settingsStore.customSearchUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init {
        tabManager.webViewConfigurer = { tabId, webView ->
            webView.webViewClient = AstraWebViewClient(
                tabId = tabId,
                tabManager = tabManager,
                contentBlocker = contentBlocker,
                onPageFinished = { id, url -> onPageVisited(id, url) }
            )
            webView.webChromeClient = AstraWebChromeClient(
                tabId = tabId,
                tabManager = tabManager,
                permissionManager = permissionManager,
                onFullscreenChange = { }
            )
        }

        viewModelScope.launch {
            combine(settingsStore.trackingProtection, settingsStore.adBlocking) { tracking, ads ->
                tracking to ads
            }.collect { (tracking, ads) -> contentBlocker.setGlobalEnabled(tracking, ads) }
        }

        // Always have at least one tab on cold start.
        if (tabManager.tabs.value.isEmpty()) {
            tabManager.createTab(context)
        }
    }

    fun newTab(isPrivate: Boolean = false) {
        tabManager.createTab(context, isPrivate = isPrivate)
    }

    fun closeTab(tabId: String) {
        val tab = tabManager.tabs.value.find { it.id == tabId }
        tabManager.closeTab(tabId)
        if (tab != null && !tab.isPrivate && tab.url.isNotBlank()) {
            viewModelScope.launch { closedTabRepository.record(tab.url, tab.title) }
        }
    }

    fun switchTab(tabId: String) = tabManager.switchTo(tabId)

    fun duplicateTab(tabId: String) = tabManager.duplicateTab(context, tabId)

    fun reopenClosedTab(url: String) {
        tabManager.createTab(context, url = url)
    }

    /** Resolves raw address-bar text into either a direct URL or a search URL. */
    fun resolveInput(input: String): String {
        val trimmed = input.trim()
        val looksLikeUrl = Patterns.WEB_URL.matcher(trimmed).matches() &&
            !trimmed.contains(" ")

        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            looksLikeUrl -> "https://$trimmed"
            else -> buildSearchUrl(trimmed)
        }
    }

    private fun buildSearchUrl(query: String): String {
        val engine = searchEngine.value
        val template = if (engine == SearchEngine.CUSTOM) customSearchUrl.value else engine.searchUrlTemplate
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return if (template.contains("%s")) template.replace("%s", encoded)
        else "https://www.google.com/search?q=$encoded"
    }

    fun onPageVisited(tabId: String, url: String) {
        val tab = tabManager.tabs.value.find { it.id == tabId } ?: return
        if (tab.isPrivate) return
        viewModelScope.launch {
            historyRepository.record(url, tab.title.ifBlank { url })
            val bookmarked = bookmarkRepository.isBookmarked(url)
            tabManager.updateTab(tabId) { it.copy(isBookmarked = bookmarked) }
        }
    }

    fun toggleBookmark(tabId: String) {
        val tab = tabManager.tabs.value.find { it.id == tabId } ?: return
        if (tab.url.isBlank()) return
        viewModelScope.launch {
            if (tab.isBookmarked) {
                bookmarkRepository.removeByUrl(tab.url)
            } else {
                bookmarkRepository.add(tab.url, tab.title.ifBlank { tab.url })
            }
            tabManager.updateTab(tabId) { it.copy(isBookmarked = !tab.isBookmarked) }
        }
    }

    fun toggleDesktopSite(tabId: String) {
        tabManager.updateTab(tabId) { it.copy(desktopSiteEnabled = !it.desktopSiteEnabled) }
    }
}
