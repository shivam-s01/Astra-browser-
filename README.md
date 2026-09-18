# Astra Browser

A privacy-focused Android browser built with Kotlin + Jetpack Compose, using
real `WebView`-backed tabs, genuine tracker/ad request interception, the
platform `DownloadManager`, and Room-backed history/bookmarks/downloads.

## Push from Termux and build via GitHub Actions

```bash
cd astra-browser
git init
git add .
git commit -m "Initial Astra Browser scaffold"
git branch -M main
git remote add origin https://github.com/<your-username>/astra-browser.git
git push -u origin main
```

GitHub Actions (`.github/workflows/build.yml`) will:
- Build a **debug APK** on every push to `main` — download it from the
  workflow run's **Artifacts** section.
- Build a **release APK** and publish a GitHub Release when you push a tag
  matching `v*.*.*`, e.g.:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Project structure

- `core/tabs` — `TabManager`: owns real per-tab `WebView` instances
- `core/engine` — `WebViewClient`/`WebChromeClient` wiring, download manager
- `privacy/blocker` — real tracker/ad request interception
- `privacy/permissions` — per-site camera/mic/location decisions
- `data/local` — Room database (history, bookmarks, downloads, permissions)
- `data/store` — DataStore-backed settings
- `theme` — the 9 built-in themes + custom theme support
- `ui/*` — Compose screens: browser chrome, new tab, tab switcher, bookmarks,
  history, downloads, settings, privacy dashboard

## Known platform-limited items

- Ad/tracker blocking loads `assets/blocklist_hosts.txt`. CI regenerates it from
  EasyList + EasyPrivacy + Peter Lowe on every build (see `build.yml`), so the
  APK ships a full-size list. The committed file is only a small fallback.
- Reader mode and full fingerprinting protection are not yet implemented in
  this scaffold and should be added as a next milestone.
- Setting Astra as the default browser requires Android's default-app flow,
  which the manifest's `BROWSABLE` intent filter enables but doesn't force.
