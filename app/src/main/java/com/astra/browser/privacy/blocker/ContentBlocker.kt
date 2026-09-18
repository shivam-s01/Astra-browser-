package com.astra.browser.privacy.blocker

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real, verifiable content blocking: every request WebView makes passes
 * through here via shouldInterceptRequest. A matched host is answered with
 * an empty 200 response instead of being allowed to load — this genuinely
 * stops the request, it does not just hide something client-side.
 *
 * The blocklist below is a small curated set of well-known tracker/ad
 * domains for demonstration; ship a maintained list (e.g. EasyList/EasyPrivacy
 * converted to a host set) for production coverage.
 */
@Singleton
class ContentBlocker @Inject constructor() {

    private val trackerHosts = setOf(
        "doubleclick.net", "googlesyndication.com", "google-analytics.com",
        "googletagmanager.com", "googletagservices.com", "facebook.net",
        "connect.facebook.net", "analytics.twitter.com", "ads-twitter.com",
        "scorecardresearch.com", "adnxs.com", "adsrvr.org", "criteo.com",
        "outbrain.com", "taboola.com", "mopub.com", "chartbeat.com",
        "hotjar.com", "segment.io", "mixpanel.com", "amplitude.com",
        "branch.io", "appsflyer.com", "adjust.com", "moatads.com",
        "quantserve.com", "yieldmo.com", "rubiconproject.com", "pubmatic.com",
        "openx.net", "casalemedia.com", "bidswitch.net", "adform.net"
    )

    private var trackingProtectionEnabled = true
    private var adBlockingEnabled = true
    private var perSiteOverrides = mutableMapOf<String, Boolean>() // origin -> enabled

    private val _blockedCountByTab = mutableMapOf<String, Int>()

    fun setGlobalEnabled(tracking: Boolean, ads: Boolean) {
        trackingProtectionEnabled = tracking
        adBlockingEnabled = ads
    }

    fun setSiteOverride(origin: String, enabled: Boolean) {
        perSiteOverrides[origin] = enabled
    }

    fun clearSiteOverride(origin: String) {
        perSiteOverrides.remove(origin)
    }

    fun blockedCountForTab(tabId: String): Int = _blockedCountByTab[tabId] ?: 0

    fun resetCountForTab(tabId: String) {
        _blockedCountByTab[tabId] = 0
    }

    /**
     * Called from WebViewClient.shouldInterceptRequest. Returns a blocking
     * empty response if the request should be blocked, or null to let it
     * proceed normally.
     */
    fun intercept(tabId: String, pageOrigin: String?, request: WebResourceRequest): WebResourceResponse? {
        val siteEnabled = pageOrigin?.let { perSiteOverrides[it] } ?: true
        if (!siteEnabled) return null
        if (!trackingProtectionEnabled && !adBlockingEnabled) return null

        val host = request.url.host ?: return null
        val isTracker = trackerHosts.any { host == it || host.endsWith(".$it") }

        if (isTracker) {
            _blockedCountByTab[tabId] = (_blockedCountByTab[tabId] ?: 0) + 1
            return WebResourceResponse(
                "text/plain",
                "utf-8",
                ByteArrayInputStream(ByteArray(0))
            )
        }
        return null
    }

    fun originOf(url: String): String? = try {
        val uri = URI(url)
        "${uri.scheme}://${uri.host}"
    } catch (e: Exception) {
        null
    }
}
