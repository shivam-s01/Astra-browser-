package com.astra.browser.domain.model

import java.util.UUID

/**
 * Represents a single browser tab. Backed by a real WebView instance held in
 * TabManager; this class holds the observable UI-facing state for that tab.
 */
data class Tab(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Tab",
    val url: String = "",
    val faviconUrl: String? = null,
    val isPrivate: Boolean = false,
    val isLoading: Boolean = false,
    val loadProgress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isSecure: Boolean = false,
    val isBookmarked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val thumbnailPath: String? = null,
    val desktopSiteEnabled: Boolean = false,
    val trackersBlockedCount: Int = 0,
    /**
     * True only until the very first navigation is dispatched for this tab.
     * Drives the NewTabPage <-> WebView switch in BrowserScreen. We deliberately
     * do NOT use `url.isBlank()` for this (that flips unreliably the instant a
     * navigation is in flight but onPageStarted hasn't landed yet, which was
     * tearing down/recreating the live WebView mid-load and crashing).
     */
    val isBlankTab: Boolean = true
)

enum class SearchEngine(val displayName: String, val searchUrlTemplate: String, val homeUrl: String) {
    GOOGLE("Google", "https://www.google.com/search?q=%s", "https://www.google.com"),
    BING("Bing", "https://www.bing.com/search?q=%s", "https://www.bing.com"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com"),
    BRAVE("Brave Search", "https://search.brave.com/search?q=%s", "https://search.brave.com"),
    ECOSIA("Ecosia", "https://www.ecosia.org/search?q=%s", "https://www.ecosia.org"),
    CUSTOM("Custom", "", "")
}
