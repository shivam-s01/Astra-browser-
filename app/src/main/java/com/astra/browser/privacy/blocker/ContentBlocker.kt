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
 *  - Host list lives in assets/ (~8.5k hosts: AdAway + EasyList/EasyPrivacy
 *    domain-anchor rules + curated additions) and is loaded ONCE into a
 *    HashSet (O(1) lookups, no per-request regex over thousands of rules).
 *  - A host is checked by walking parent domains (a.b.c.com -> b.c.com ->
 *    c.com), so subdomains of a listed host are covered without storing them.
 *  - Beyond hosts, a small hand-picked set of unambiguous ad/tracker PATH
 *    fragments (adPathFragments/trackerPathFragments) catches same-domain
 *    ad endpoints -- e.g. YouTube's own ad-request calls -- that a host list
 *    alone can't, without the false-positive risk of importing EasyList's
 *    full generic-path ruleset wholesale.
 *  - Cosmetic filtering (cosmeticCss()) hides the leftover empty containers
 *    once a network request is blocked, using a filtered subset of
 *    EasyList's generic attribute-based hide rules -- picked for being both
 *    cross-site-safe and cheap to match, not the full ~13.6k rule set.
 *  - Only cheap string checks run on the hot path (shouldInterceptRequest
 *    fires for EVERY resource, on Chromium's IO threads).
 *  - Thread-safe: all shared state uses concurrent collections / @Volatile.
 *
 * CI can drop a freshly generated assets/blocklist_hosts.txt (one host per
 * line) with no code change required.
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

    // Ad-delivery path fragments, checked against BOTH first- and
    // third-party requests (see matchesFragment) since several of these --
    // notably the YouTube ones -- are first-party by nature. Kept specific
    // on purpose: broad/short fragments cause false positives that break
    // real sites, which matters more now that first-party is in scope too.
    private val adPathFragments = arrayOf(
        "/pagead/", "/adserver/", "/adframe", "/doubleclick/", "/googleads",
        "/show_ads", "/adsbygoogle", "/prebid", "/pop-under", "/popunder", "/ima3.js",
        // YouTube/Google Video ad-request endpoints. These are path-specific
        // (not the whole youtube.com/googlevideo.com host, which would break
        // playback) so only the ad break itself is dropped, not the video.
        "/api/stats/ads", "/pagead/interaction", "/pagead/adview",
        "/ptracking", "/get_midroll", "/annotations_invideo", "/gen_204?",
        // Generic in-stream video ad tags used across most video sites.
        // Full "/vast" + separator, not a bare "vast2"/"vast3" -- a bare
        // version-number fragment risks matching an unrelated path
        // (e.g. "/campaign/vast2023/") now that first-party is in scope.
        "/vast.xml", "/vast?", "/vast/", "/vmap.xml", "/vmap?", "/adtagurl"
    )

    private val trackerPathFragments = arrayOf(
        "/analytics.js", "/gtag/js", "/gtm.js", "/fbevents.js", "/pixel.gif",
        "/__utm.gif", "/tracking.js", "/collect?", "/beacon.js", "/telemetry",
        "/log_event", "/csi?", "/stats.g.doubleclick",
        // Added from EasyPrivacy's most common unambiguous tracker path
        // shapes. Deliberately NOT including broad segments like "/api/",
        // "/js/", "/assets/" that also showed up frequently in that data --
        // those are used by countless legitimate site features too and
        // would break real pages; only fragments whose name itself is
        // tracking-specific made the cut.
        "/tracker.js", "/track.js", "/tracking.gif", "/tracking.png",
        "/analytics.gif", "/analytics.png", "/metrics.js", "/pixel.png",
        "/pixel.js", "/beacon.gif", "/collector.js"
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
        val urlStr = url.toString()
        val host = url.host?.lowercase() ?: return null

        // Explicit allow-list checked FIRST, before any block logic. This is
        // the equivalent of EasyList's "@@" exception rules: a small, hand
        // -picked set of requests that would otherwise match a block
        // pattern but are actually required for a site's core functionality
        // to work (e.g. YouTube's own video-metadata endpoint). Kept tiny on
        // purpose -- this is a safety valve for known false positives, not
        // a general carve-out mechanism.
        if (isExplicitlyAllowed(urlStr)) return null

        val blocked = isBlockedHost(host) ||
            (adBlockingEnabled && matchesFragment(urlStr, adPathFragments, pageOrigin, host, thirdPartyOnly = false)) ||
            (trackingProtectionEnabled && matchesFragment(urlStr, trackerPathFragments, pageOrigin, host, thirdPartyOnly = true))

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

    private fun isExplicitlyAllowed(url: String): Boolean {
        val lower = url.lowercase()
        for (a in ALLOWLIST_FRAGMENTS) if (lower.contains(a)) return true
        return false
    }

    /**
     * [thirdPartyOnly] matters a lot: the original code always skipped
     * first-party requests here to avoid false positives on generic
     * patterns. But the YouTube/video-ad path fragments in [adPathFragments]
     * (e.g. "/api/stats/ads", "/get_midroll") are FIRST-PARTY requests --
     * they go to youtube.com / googlevideo.com, the exact same registrable
     * domain as the page itself. Always requiring third-party meant those
     * patterns could never fire on the site they were written for, silently
     * defeating the whole YouTube ad-block path. So ad-path fragments check
     * regardless of party; tracker fragments (analytics/pixels) stay
     * third-party-only since those DO commonly false-positive on a site's
     * own first-party analytics endpoints.
     */
    private fun matchesFragment(url: String, fragments: Array<String>, pageOrigin: String?, host: String, thirdPartyOnly: Boolean): Boolean {
        if (thirdPartyOnly && isFirstParty(pageOrigin, host)) return false
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

        // Known false-positive safety valve (EasyList's equivalent of an
        // "@@" exception rule): requests that would otherwise be caught by
        // a block pattern above but are actually required for real site
        // functionality. Checked before any block logic in intercept().
        // Currently empty in the shipped default -- add an entry here only
        // when a SPECIFIC site is confirmed broken by a SPECIFIC block
        // pattern, not preemptively.
        val ALLOWLIST_FRAGMENTS = arrayOf<String>(
            // e.g. "youtube.com/get_video_info" -- kept as a documented
            // example of the intended shape, not an active rule (this
            // request isn't matched by anything in adPathFragments or
            // trackerPathFragments in the first place).
        )

        // 1x1 transparent GIF so blocked <img> ads don't show a broken-image icon.
        val TRANSPARENT_GIF = byteArrayOf(
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, 0x80.toByte(), 0x00, 0x00,
            0x00, 0x00, 0x00, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x21, 0xF9.toByte(), 0x04,
            0x01, 0x00, 0x00, 0x00, 0x00, 0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0x02, 0x02, 0x44, 0x01, 0x00, 0x3B
        )

        const val COSMETIC_CSS =
            // Google/general programmatic ad containers
            "ins.adsbygoogle,.adsbygoogle,[id^=\"google_ads_\"],[id^=\"div-gpt-ad\"]," +
            "iframe[src*=\"doubleclick.net\"],iframe[src*=\"googlesyndication\"]," +
            "iframe[id^=\"google_ads_iframe\"],[data-ad-slot],[data-google-query-id]," +
            ".taboola,.OUTBRAIN,#taboola-below-article,.trc_rbox_container,.ob-widget," +
            // Generic class/id name patterns used across most ad-heavy sites
            // (download portals, anime/streaming sites, link shorteners).
            "[class*=\"ad-banner\"],[class*=\"ad-container\"],[class*=\"ad-wrapper\"]," +
            "[class*=\"advert\"],[id*=\"advert\"],[class^=\"ads-\"],[class*=\" ads-\"]," +
            "[id^=\"ads-\"],[id*=\" ads-\"],[class*=\"banner-ad\"],[id*=\"banner-ad\"]," +
            "[class*=\"sponsor-\"],[id*=\"sponsor-\"],[class*=\"popup-ad\"],[id*=\"popup-ad\"]," +
            "[class*=\"sticky-ad\"],[id*=\"sticky-ad\"],[class*=\"interstitial\"]," +
            "[class*=\"overlay-ad\"],[id*=\"overlay-ad\"]," +
            // Common third-party ad-network embed containers
            "[id^=\"ezoic-\"],ins.ezoic-adpicker-ad,[data-ezscrex]," +
            "iframe[src*=\"exoclick\"],iframe[src*=\"exosrv\"],iframe[src*=\"juicyads\"]," +
            "iframe[src*=\"propellerads\"],iframe[src*=\"popads\"],iframe[src*=\"adsterra\"]," +
            "iframe[src*=\"hilltopads\"],iframe[src*=\"mgid.com\"],iframe[src*=\"revcontent\"]," +
            "div[id^=\"aswift_\"],div[id^=\"google_ads_iframe_\"]," +
            // Explicit ad-labeled download-lookalike buttons only (never a
            // generic href/class pattern -- that risks hiding real download
            // buttons on legitimate file-host sites).
            ".download-ad,.fake-download,[class*=\"dl-ad\"],[id*=\"dl-ad\"]," +
            // Extracted from EasyList's generic (domain-less) hide rules --
            // ~13.6k such rules exist upstream, but injecting all of them
            // as one CSS blob would cost real selector-matching time on
            // every page load, which fights the "low CPU" goal. This is a
            // filtered subset: only attribute selectors (cheap families,
            // one rule catches many elements) whose attribute name/value
            // unambiguously signals "ad" -- no bare IDs/classes (too
            // site-specific to be worth 4000+ extra rules) and nothing
            // matching on inline style= (fragile, risks hiding unrelated
            // elements that happen to share a style string).
            "[class^=\"adDisplay-module\"],[class^=\"amp-ad-\"],[class^=\"tile-picker__CitrusBannerContainer-sc-\"]," +
            "[data-ad-cls],[data-ad-manager-id],[data-ad-module],[data-ad-name],[data-ad-width]," +
            "[data-block-type=\"ad\"],[data-d-ad-id],[data-desktop-ad-id],[data-id^=\"div-gpt-ad\"]," +
            "[data-identity=\"adhesive-ad\"],[data-m-ad-id],[data-mobile-ad-id]," +
            "[data-template-type=\"nativead\"],[data-testid=\"adBanner-wrapper\"],[data-testid=\"ad_testID\"]," +
            "[data-testid=\"prism-ad-wrapper\"],[id^=\"ad-wrap-\"],[id^=\"ad_sky\"],[id^=\"ad_slider\"]," +
            "[id^=\"section-ad-banner\"],[name^=\"google_ads_iframe\"]," +
            "aside[aria-label=\"retailmedia.complimentarySponsored\"],aside[id^=\"adrotate_widgets-\"]," +
            "div[aria-label=\"Ads\"],div[class^=\"Adstyled__AdWrapper-\"],div[data-ad-placeholder]," +
            "div[data-ad-region],div[data-ad-targeting],div[data-ad-wrapper],div[id^=\"ad-div-\"]," +
            "div[id^=\"ad-position-\"],div[id^=\"ad_position_\"],div[id^=\"adngin-\"]," +
            "div[id^=\"adrotate_widgets-\"],div[id^=\"adspot-\"],div[id^=\"apn_native_ad_slot_\"]," +
            "div[id^=\"dfp-ad-\"],div[id^=\"div-ads-\"],div[id^=\"ezoic-pub-ad-\"],div[id^=\"gpt_ad_\"]," +
            "div[id^=\"lazyad-\"],div[id^=\"sticky_ad_\"],div[id^=\"vuukle-ad-\"],div[ow-ad-unit-wrapper]," +
            "ins.adsbygoogle[data-ad-client],ins.adsbygoogle[data-ad-slot]," +
            "span[id^=\"ezoic-pub-ad-placeholder-\"]" +
            "{display:none!important;visibility:hidden!important;height:0!important;min-height:0!important;pointer-events:none!important}"
    }
}
