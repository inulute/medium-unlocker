# v2.0 (2026-04-19)

---

<div align='center'>

<img src=./assets/medium_unlock_logo.svg width='150px'>

</div>

---

## New Features

### History & Bookmarks
- **Reading history** – Every article you unlock is automatically saved with its title, source domain, and timestamp.
- **Bookmarks** – Bookmark any article while reading and access it instantly from the History screen.
- **Search** – Live search across all history and bookmark entries by title or URL.
- **Swipe to delete** – Swipe left on any item to remove it.
- **Export / Import** – Back up and restore your history and bookmarks as a JSON file.

### Home Screen Feed
- **Recent articles** – The last 5 read articles appear directly on the home screen so you can jump back in without opening the full history.
- **Bookmark toggle** – Bookmark or unbookmark articles right from the home screen cards.
- **Configurable feed** – Settings let you choose what the home screen shows: Recent Articles, Bookmarks, or Both. In "Both" mode, bookmarked items are tagged so you can tell them apart at a glance.
- **See all** – Tap "See all →" to open the full History & Bookmarks screen.

### Settings Screen
- **Text Zoom** – Adjust article font size from 50% to 200% with a live preview; reset to 100% with one tap.
- **Remember Reading Position** – Toggle scroll-position memory on or off.
- **Max History Size** – Set how many articles are kept in history (10–1000).
- **Home Screen Feed** – Choose what appears in the home screen recent section.

### Reader Improvements
- **Resume reading** – Scroll position is saved per article and smoothly restored when you reopen it.
- **Forward navigation** – A forward button appears in the WebView toolbar when you've navigated back, letting you move forward again.
- **Smooth scroll restore** – Position is restored with a smooth scroll animation instead of a hard jump.

---

# v1.4 (2026-03-28)

---

<div align='center'>

<img src=./assets/medium_unlock_logo.svg width='150px'>

</div>

---

## New Features

### Open Medium Links Automatically
- **Deep link support** – Medium article links clicked in email, WhatsApp, or any other app now open directly in Medium Unlocker instead of the browser.
- **Android 12+ setup** – A banner on the home screen provides a one-tap **"Enable in Settings"** button that takes you directly to the app's link-handling settings.
- **Android 11 and below** – A system chooser dialog appears when opening Medium links; select Medium Unlocker and tap "Always" to set it as the default.
- **Smart banner** – The setup banner automatically hides once you've enabled link handling.

## Misc

- Fixed intent filter to correctly handle `medium.com` and `*.medium.com` links without the failed auto-verification that was silently blocking deep links on Android 12+.

---

# v1.3 (2026-02-08)

---

<div align='center'>

<img src=./assets/medium_unlock_logo.svg width='150px'>

</div>

---

## 🚀 New Features

### Medium & Custom Domain URLs
- **Medium article URLs** – Paste standard `medium.com` links as before.
- **Custom domain URLs** – Now supports article links from custom domains (e.g. publication subdomains and custom Medium sites).
- Use any article URL in the app or on the website; both are accepted and routed through Freedium.

## 📋 Misc

- **Update popup** – Update-available dialog now shows on app launch (once per new version) and when opening an article in WebView for the first time; also shown when opening from the share sheet using cached update info.
- **Launcher icon** – Icon scaled to fit the adaptive-icon safe zone so it no longer gets cropped on circular or squircle launcher masks.
- **Title font** – “Medium Unlocker.” title is explicitly bold for consistent appearance across devices.

---

# Release v1.2

## 🚀 New Features

### Mirror Server Support
- Added support for `freedium-mirror.cfd` as a robust alternative to the primary server.
- **Default Behavior**: The app now defaults to the mirror server to ensure maximum availability for all users.

### Smart Auto-Retry
- **Seamless Switching**: If a connection fails (e.g., due to DNS blocking), the app automatically detects the issue and switches to the working server.

## 📦 Installation
Download the `MediumUnlocker_v1.2.apk` below and install it on your Android device.
