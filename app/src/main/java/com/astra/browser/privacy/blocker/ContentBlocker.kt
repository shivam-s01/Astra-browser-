package com.astra.browser.privacy.blocker

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Request-level ad/tracker blocker.
 *
 * Light + cool-running by design:
 *  - Host list lives in assets/ and is loaded ONCE into a HashSet (O(1)
 *    lookups, no per-request regex over thousands of rules).
 *  - A host is checked by walking parent domains (a.b.c.com -> b.c.com ->
 *    c.com), so subdomains of a listed host are covered without storing them.
 *  - Only cheap string checks run on the hot path (shouldInterceptRequest
 *    fires for EVERY resource, on Chromium's IO threads).
 *  - Thread-safe: all shared state uses concurrent collections / @Volatile.
 *
 * CI can drop a full EasyList/EasyPrivacy-derived hosts file at
 * assets/blocklist_hosts.txt (see build.yml) with no code change.
 */
@Singleton
class ContentBlocker @Inject constructor(
    @ApplicationContext private val context: Context
) {

    @Volatile private var trackingProtectionEnabled = true
    @Volatile private var adBlockingEnabled = true
    private val perSiteOverrides = ConcurrentHashMap<String, Boolean>()
    private val blockedCountByTab = ConcurrentHashMap<String, Int>()

    /** Loaded lazily on first request so app start-up isn't slowed. */
    private val blockedHosts: Set<String> by lazy { loadHosts() }

    private fun loadHosts(): Set<String> = try {
        context.assets.open("blocklist_hosts.txt").bufferedReader().useLines { lines ->
            lines.map { it.trim().lowercase() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toHashSet()
        }
    } catch (e: Exception) {
        FALLBACK_HOSTS
    }

    // Ad-delivery path fragments. Small and specific on purpose: broad
    // patterns cause false positives that break real sites. Only applied to
    // THIRD-PARTY requests.
    private val adPathFragments = arrayOf(
        "/pagead/", "/adserver/", "/adframe", "/doubleclick/", "/googleads",
        "/show_ads", "/adsbygoogle", "/prebid", "/pop-under", "/popunder", "/ima3.js"
    )

    private val trackerPathFragments = arrayOf(
        "/analytics.js", "/gtag/js", "/gtm.js", "/fbevents.js", "/pixel.gif",
        "/__utm.gif", "/tracking.js"
    )

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

    fun blockedCountForTab(tabId: String): Int = blockedCountByTab[tabId] ?: 0

    fun resetCountForTab(tabId: String) {
        blockedCountByTab[tabId] = 0
    }

    fun isEnabledFor(origin: String?): Boolean =
        (origin?.let { perSiteOverrides[it] } ?: true) && (adBlockingEnabled || trackingProtectionEnabled)

    fun adBlockingOn(): Boolean = adBlockingEnabled

    /**
     * Returns an empty response for a blocked request, or null to let it
     * load. Never blocks the top-level document.
     */
    fun intercept(tabId: String, pageOrigin: String?, request: WebResourceRequest): WebResourceResponse? {
        if (request.isForMainFrame) return null
        if (!isEnabledFor(pageOrigin)) return null

        val url = request.url
        val host = url.host?.lowercase() ?: return null

        val blocked = isBlockedHost(host) ||
            (adBlockingEnabled && matchesFragment(url.toString(), adPathFragments, pageOrigin, host)) ||
            (trackingProtectionEnabled && matchesFragment(url.toString(), trackerPathFragments, pageOrigin, host))

        if (!blocked) return null

        blockedCountByTab.merge(tabId, 1, Int::plus)
        return emptyResponseFor(url.path.orEmpty())
    }

    private fun isBlockedHost(host: String): Boolean {
        var h = host
        while (true) {
            if (h in blockedHosts) return true
            val dot = h.indexOf('.')
            if (dot == -1 || h.indexOf('.', dot + 1) == -1) return false
            h = h.substring(dot + 1)
        }
    }

    private fun matchesFragment(url: String, fragments: Array<String>, pageOrigin: String?, host: String): Boolean {
        if (isFirstParty(pageOrigin, host)) return false
        val lower = url.lowercase()
        for (f in fragments) if (lower.contains(f)) return true
        return false
    }

    private fun isFirstParty(pageOrigin: String?, requestHost: String): Boolean {
        val pageHost = pageOrigin?.substringAfter("://")?.lowercase() ?: return false
        return registrableDomain(pageHost) == registrableDomain(requestHost)
    }

    /** Cheap eTLD+1 approximation: last two labels (three for co.uk-style). */
    private fun registrableDomain(host: String): String {
        val parts = host.split('.')
        if (parts.size <= 2) return host
        val twoPartTld = parts[parts.size - 2] in TWO_PART_SLDS && parts.last().length == 2
        val take = if (twoPartTld) 3 else 2
        return parts.takeLast(take).joinToString(".")
    }

    private fun emptyResponseFor(path: String): WebResourceResponse {
        val p = path.lowercase()
        val (mime, body) = when {
            p.endsWith(".js") -> "application/javascript" to ByteArray(0)
            p.endsWith(".css") -> "text/css" to ByteArray(0)
            p.endsWith(".gif") || p.endsWith(".png") || p.endsWith(".jpg") ||
                p.endsWith(".jpeg") || p.endsWith(".webp") -> "image/gif" to TRANSPARENT_GIF
            else -> "text/plain" to ByteArray(0)
        }
        return WebResourceResponse(
            mime, "utf-8", 200, "OK",
            mapOf("Access-Control-Allow-Origin" to "*"),
            ByteArrayInputStream(body)
        )
    }

    fun originOf(url: String): String? = try {
        val uri = URI(url)
        if (uri.host == null) null else "${uri.scheme}://${uri.host}"
    } catch (e: Exception) {
        null
    }

    /** CSS hiding the empty boxes ad networks leave behind. Injected on page finish. */
    fun cosmeticCss(): String = COSMETIC_CSS

    private companion object {
        val TWO_PART_SLDS = setOf("co", "com", "org", "net", "gov", "ac", "edu")

        val FALLBACK_HOSTS = hashSetOf(
            "doubleclick.net", "googlesyndication.com", "google-analytics.com",
            "googletagmanager.com", "googleadservices.com", "adnxs.com", "adsrvr.org",
            "criteo.com", "outbrain.com", "taboola.com", "scorecardresearch.com",
            "moatads.com", "pubmatic.com", "rubiconproject.com", "openx.net",
            "amazon-adsystem.com", "popads.net", "propellerads.com", "exoclick.com"
        )

        // 1x1 transparent GIF so blocked <img> ads don't show a broken-image icon.
        val TRANSPARENT_GIF = byteArrayOf(
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, 0x80.toByte(), 0x00, 0x00,
            0x00, 0x00, 0x00, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x21, 0xF9.toByte(), 0x04,
            0x01, 0x00, 0x00, 0x00, 0x00, 0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0x02, 0x02, 0x44, 0x01, 0x00, 0x3B
        )

        const val COSMETIC_CSS =
            "ins.adsbygoogle,.adsbygoogle,[id^=\"google_ads_\"],[id^=\"div-gpt-ad\"]," +
            "iframe[src*=\"doubleclick.net\"],iframe[src*=\"googlesyndication\"]," +
            "iframe[id^=\"google_ads_iframe\"],[data-ad-slot],[data-google-query-id]," +
            ".taboola,.OUTBRAIN,#taboola-below-article,.trc_rbox_container,.ob-widget" +
            "{display:none!important;visibility:hidden!important;height:0!important;min-height:0!important}"
    }
}
