package com.astra.browser.core.engine

/**
 * All JavaScript Astra injects into pages, in one place.
 *
 * Every script here is injected via WebViewCompat.addDocumentStartJavaScript,
 * so it runs BEFORE any page script, on every navigation (including SPA
 * navigations). All of them are idempotent and hold their own on/off state
 * in `window.__astra` -- no dependency on a JS interface being registered in
 * time, which was the original reason background playback was unreliable.
 *
 * Kept deliberately small and allocation-light: no MutationObserver on the
 * whole document, no polling faster than 1s, nothing that runs per-frame.
 * That is what keeps heat/battery low on budget phones.
 */
object PageScripts {

    /**
     * Runtime flags the native side flips via evaluateJavascript("__astra.set(...)").
     * Installed FIRST so every other script can read window.__astra.
     */
    const val STATE_JS = """
        (function(){
          if (window.__astra) return;
          var st = { bg: true, ytSkip: true };
          Object.defineProperty(window, '__astra', {
            value: {
              st: st,
              set: function(k, v){ st[k] = !!v; }
            },
            enumerable: false, configurable: false, writable: false
          });
        })();
    """

    /**
     * BACKGROUND PLAYBACK.
     *
     * Why the old version failed on YouTube:
     *  1. It read the flag through a JS interface (AstraBackgroundState) that
     *     is registered with addJavascriptInterface -- that call is NOT
     *     guaranteed to have happened before a document-start script runs, so
     *     shouldSpoof() often returned false at exactly the moment it mattered.
     *  2. It only wrapped 'visibilitychange' listeners registered on `document`.
     *     YouTube also listens on `window` (pagehide / blur / freeze) and
     *     polls document.visibilityState directly.
     *  3. The release-build proguard config didn't keep that interface, so in a
     *     signed APK the method vanished entirely.
     *
     * This version needs no interface at all: the flag lives in window.__astra
     * (pushed from native), every visibility-related property AND event is
     * neutralised, and a light 'pause guard' re-plays a video that the site
     * pauses by itself right after we lose focus.
     */
    const val BACKGROUND_SPOOF_JS = """
        (function(){
          if (window.__astraBgInstalled) return;
          window.__astraBgInstalled = true;
          function on(){ return !window.__astra || window.__astra.st.bg; }

          function def(obj, prop, getter){
            try { Object.defineProperty(obj, prop, { configurable: true, get: getter }); } catch(e){}
          }
          var D = Document.prototype;
          var nHidden = Object.getOwnPropertyDescriptor(D, 'hidden');
          var nState  = Object.getOwnPropertyDescriptor(D, 'visibilityState');
          def(document, 'hidden',           function(){ return on() ? false : (nHidden && nHidden.get ? nHidden.get.call(document) : false); });
          def(document, 'webkitHidden',     function(){ return on() ? false : (nHidden && nHidden.get ? nHidden.get.call(document) : false); });
          def(document, 'visibilityState',  function(){ return on() ? 'visible' : (nState && nState.get ? nState.get.call(document) : 'visible'); });
          def(document, 'webkitVisibilityState', function(){ return on() ? 'visible' : (nState && nState.get ? nState.get.call(document) : 'visible'); });
          try {
            var nFocus = D.hasFocus;
            document.hasFocus = function(){ return on() ? true : nFocus.call(document); };
          } catch(e){}

          // Swallow lifecycle events at the CAPTURE phase on window before any
          // site listener (bubble or capture, on window or document) sees them.
          var KILL = { visibilitychange:1, webkitvisibilitychange:1, pagehide:1, freeze:1, blur:1, resume:0 };
          function kill(ev){
            if (on() && KILL[ev.type] === 1) { ev.stopImmediatePropagation(); }
          }
          ['visibilitychange','webkitvisibilitychange','pagehide','freeze','blur'].forEach(function(t){
            window.addEventListener(t, kill, true);
            document.addEventListener(t, kill, true);
          });

          // Pause guard: if the site pauses a playing video within 1.2s of the
          // page being backgrounded (some players do it from a timer, not an
          // event), resume it. Only ever acts on media that WAS playing.
          var lastPlaying = 0;
          document.addEventListener('playing', function(e){ lastPlaying = Date.now(); e.target.__astraWasPlaying = true; }, true);
          document.addEventListener('pause', function(e){
            var v = e.target;
            if (!on() || !v || !v.__astraWasPlaying || v.ended) return;
            if (window.__astraUserPaused) { return; }
            if (Date.now() - lastPlaying < 600) return; // genuine quick pause
            if (v.__astraBgResume) return;
            v.__astraBgResume = true;
            setTimeout(function(){
              v.__astraBgResume = false;
              if (on() && v.paused && !v.ended && !window.__astraUserPaused) { try { v.play(); } catch(x){} }
            }, 250);
          }, true);

          // A pause the USER causes (tap on the player, headset button) must
          // not be fought. Real taps mark a short window.
          ['touchstart','click','keydown'].forEach(function(t){
            document.addEventListener(t, function(){
              window.__astraUserPaused = true;
              clearTimeout(window.__astraUPT);
              window.__astraUPT = setTimeout(function(){ window.__astraUserPaused = false; }, 1500);
            }, true);
          });
        })();
    """

    /**
     * YOUTUBE AD REMOVAL (the part network-blocking alone can never do).
     *
     * YouTube delivers ads INSIDE its own player-response JSON, from the same
     * host/path as the video itself, so a host or path blocklist can't touch
     * them. Brave/uBlock beat this by rewriting that JSON before the player
     * reads it. We do the same, in three cheap layers:
     *
     *  1. JSON.parse hook  -> strips adPlacements / playerAds / adSlots /
     *     adBreakHeartbeatParams from any player response.
     *  2. fetch/XHR hook   -> same strip for /youtubei/v1/player and /next
     *     responses that arrive as network JSON.
     *  3. Safety net       -> if an ad still slips through (video with
     *     .ad-showing), click Skip when it appears, otherwise mute + fast-
     *     forward it to the end. A 1s timer that ONLY runs on YouTube hosts.
     *
     * Cosmetic hide for the leftover ad slots is included as a <style>.
     */
    const val YOUTUBE_ADS_JS = """
        (function(){
          if (!/(^|\.)youtube\.com${'$'}|(^|\.)youtube-nocookie\.com${'$'}/.test(location.hostname)) return;
          if (window.__astraYtInstalled) return;
          window.__astraYtInstalled = true;
          function on(){ return !window.__astra || window.__astra.st.ytSkip; }

          var AD_KEYS = ['adPlacements','playerAds','adSlots','adBreakHeartbeatParams','adBreakParams'];
          function strip(o){
            if (!o || typeof o !== 'object') return o;
            try {
              for (var i = 0; i < AD_KEYS.length; i++) { if (AD_KEYS[i] in o) { try { delete o[AD_KEYS[i]]; } catch(e){ o[AD_KEYS[i]] = undefined; } } }
              if (o.playerResponse) strip(o.playerResponse);
              if (o.response && o.response.playerResponse) strip(o.response.playerResponse);
              if (o.streamingData && o.streamingData.serverAbrStreamingUrl === undefined) {}
            } catch(e){}
            return o;
          }

          // 1. JSON.parse
          var nParse = JSON.parse;
          JSON.parse = function(text, rev){
            var r = nParse.call(JSON, text, rev);
            if (on() && r && typeof r === 'object' && (r.adPlacements || r.playerAds || r.adSlots || r.playerResponse)) strip(r);
            return r;
          };

          // 2a. fetch
          var nFetch = window.fetch;
          if (nFetch) {
            window.fetch = function(input, init){
              var url = (typeof input === 'string') ? input : (input && input.url) || '';
              var p = nFetch.apply(this, arguments);
              if (!on() || !/youtubei\/v1\/(player|next|get_watch)/.test(url)) return p;
              return p.then(function(resp){
                try {
                  var c = resp.clone();
                  return c.json().then(function(j){
                    strip(j);
                    return new Response(JSON.stringify(j), { status: resp.status, statusText: resp.statusText, headers: resp.headers });
                  }).catch(function(){ return resp; });
                } catch(e){ return resp; }
              });
            };
          }

          // 2b. XHR
          var nOpen = XMLHttpRequest.prototype.open;
          XMLHttpRequest.prototype.open = function(m, u){
            this.__astraU = String(u || '');
            return nOpen.apply(this, arguments);
          };
          var dText = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'responseText');
          var dResp = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'response');
          function patchXhr(desc, name){
            if (!desc || !desc.get) return;
            Object.defineProperty(XMLHttpRequest.prototype, name, {
              configurable: true,
              get: function(){
                var v = desc.get.call(this);
                if (on() && typeof v === 'string' && this.__astraU && /youtubei\/v1\/(player|next|get_watch)/.test(this.__astraU) && v.indexOf('adPlacements') > -1) {
                  try { return JSON.stringify(strip(nParse.call(JSON, v))); } catch(e){}
                }
                return v;
              }
            });
          }
          patchXhr(dText, 'responseText');
          patchXhr(dResp, 'response');

          // Pre-seeded page data (first paint of a watch page)
          try {
            var _ypr;
            Object.defineProperty(window, 'ytInitialPlayerResponse', {
              configurable: true,
              get: function(){ return _ypr; },
              set: function(v){ _ypr = on() ? strip(v) : v; }
            });
          } catch(e){}

          // 3. Safety net (only runs on YouTube, once per second)
          function tick(){
            if (!on()) return;
            var p = document.querySelector('.html5-video-player');
            if (!p) return;
            var btn = document.querySelector('.ytp-skip-ad-button, .ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button__text');
            if (btn) { try { (btn.closest('button') || btn).click(); } catch(e){} }
            if (p.classList.contains('ad-showing') || p.classList.contains('ad-interrupting')) {
              var v = p.querySelector('video');
              if (v && isFinite(v.duration) && v.duration > 0) {
                try { v.muted = true; v.playbackRate = 16; v.currentTime = Math.max(0, v.duration - 0.1); } catch(e){}
              }
              var close = document.querySelector('.ytp-ad-overlay-close-button');
              if (close) { try { close.click(); } catch(e){} }
            }
          }
          setInterval(tick, 800);

          // Cosmetic: hide leftover ad shells (style tag, no observer needed)
          var css = 'ytd-ad-slot-renderer,ytd-in-feed-ad-layout-renderer,ytd-banner-promo-renderer,ytd-promoted-sparkles-web-renderer,' +
                    'ytd-display-ad-renderer,ytd-companion-slot-renderer,ytd-player-legacy-desktop-watch-ads-renderer,' +
                    '#masthead-ad,#player-ads,.ytd-merch-shelf-renderer,ytm-promoted-sparkles-web-renderer,ytm-companion-ad-renderer,' +
                    '.ytp-ad-overlay-container,.ytp-ad-module,ad-slot-renderer,ytm-ad-slot-renderer,' +
                    '.video-ads,.ytp-paid-content-overlay{display:none!important}';
          function addStyle(){
            if (document.getElementById('__astra_yt_css')) return;
            var s = document.createElement('style'); s.id = '__astra_yt_css'; s.textContent = css;
            (document.head || document.documentElement).appendChild(s);
          }
          addStyle();
          document.addEventListener('DOMContentLoaded', addStyle);
        })();
    """

    /**
     * DESKTOP SITE.
     *
     * Old approach: rewrite <meta viewport> from a MutationObserver watching the
     * ENTIRE document subtree -- it re-ran on every DOM change, which on a heavy
     * page means hundreds of runs per second (heat), and initial-scale=0.1 made
     * the first paint jump. This version sets the viewport once, when <head>
     * appears, and only re-applies if a script replaces that one meta tag.
     * Gated by window.__astraDesktop which native sets per tab.
     */
    const val DESKTOP_VIEWPORT_JS = """
        (function(){
          if (window.__astraDeskInstalled) return;
          window.__astraDeskInstalled = true;
          var W = 1100;
          function apply(){
            if (!window.__astraDesktop) return;
            var m = document.querySelector('meta[name=viewport]');
            if (!m) {
              m = document.createElement('meta'); m.name = 'viewport';
              (document.head || document.documentElement).appendChild(m);
            }
            var want = 'width=' + W + ', initial-scale=' + (Math.min(screen.width || 400, 1080) / W).toFixed(3) + ', minimum-scale=0.25, maximum-scale=4, user-scalable=yes';
            if (m.getAttribute('content') !== want) m.setAttribute('content', want);
          }
          // Wait for <head> without observing the whole subtree.
          if (document.head) apply();
          else {
            var mo = new MutationObserver(function(){
              if (document.head) { mo.disconnect(); apply(); }
            });
            mo.observe(document.documentElement, { childList: true });
          }
          document.addEventListener('DOMContentLoaded', apply);
          window.addEventListener('load', apply);
        })();
    """
}
