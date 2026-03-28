# Release v1.4

## 🔗 New Features

### Open Medium Links Automatically
- Added deep link support — Medium article links clicked in email, WhatsApp, or any other app can now open directly in Medium Unlocker instead of the browser.
- On Android 12+, a banner appears on the home screen with a one-tap **"Enable in Settings"** button that takes you straight to the app's link-handling settings.
- Once enabled, the banner disappears automatically.
- On Android 11 and below, a system chooser dialog appears when opening Medium links — select Medium Unlocker and tap "Always" to set it as the default.

## 📋 Misc

- Fixed intent filter to correctly handle `medium.com` and `*.medium.com` links without the failed auto-verification that was silently blocking deep links on Android 12+.

---

# Release v1.3

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
