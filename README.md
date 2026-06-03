# 📱 Slim Launcher

[![Build Status](https://github.com/OpScaleHub/Slim/actions/workflows/android-build.yml/badge.svg)](https://github.com/OpScaleHub/Slim/actions)
[![F-Droid](https://img.shields.io/badge/F--Droid-Compliant-brightgreen)](docs/distribution/fdroid_publishing.md)
[![License](https://img.shields.io/github/license/OpScaleHub/Slim?color=blue)](LICENSE)

**Slim Launcher** is an ultra-minimalist, gesture-based Android launcher designed for speed, focus, and single-handed efficiency. Inspired by Niagara Launcher, it replaces your cluttered app drawer with an elegant, responsive vertical list and a dynamic side-scrolling alphabetical wave gesture.

👉 **View the live landing page at [opscalehub.github.io/Slim](https://opscalehub.github.io/Slim/)**

> **Note on URLs:** GitHub Pages paths are case-sensitive — the address is `/Slim/` (capital S), matching the repository name. The repository itself is reachable at [github.com/OpScaleHub/Slim](https://github.com/OpScaleHub/Slim).

---

## ✨ Features

*   **Wave Alphabet Scroll**: Drag along the screen edge to dynamically expand letters in an interactive wave, scrolling instantly to any application with micro-haptic feedback. Only letters that actually have apps are shown, keeping the index compact.
*   **Floating Search Panel**: Swipe up from the home screen to open a floating search panel with live, relevance-ranked results (prefix matches first, then word matches, then any part of the name) and quick-launch chips for your recently searched apps.
*   **Inline Notifications**: Read notification previews directly under your favorite apps on the home screen.
*   **Configurable Home Screen**: Toggle the clock, date, and weather independently; choose 12/24-hour time — all from Slim Settings.
*   **Weather, your way**: Off, *Ambient* (an offline seasonal estimate — no network), or *Real weather* (opt-in, powered by the key-less [Open-Meteo](https://open-meteo.com) API for the city you choose).
*   **Adaptive Readability**: Text colors automatically adapt to light or dark wallpapers, with Material You dynamic accent colors on Android 12+.
*   **Privacy-First**: No analytics, no tracking, no accounts. The launcher works fully offline — the *only* network call it can ever make is the optional Open-Meteo weather fetch, and only if you turn it on.

### Slim Settings

Slim has no persistent settings button cluttering the home screen. To open settings, simply **search for or scroll to the "Slim" app entry and tap it** — the launcher's own list entry opens its settings. From there you can configure:

| Section | Options |
|---|---|
| Home Screen | Show/hide clock, show/hide date, 12/24-hour format |
| Weather | Off / Ambient (offline) / Real (Open-Meteo), city, °C/°F |
| Search | Remember recent searches, clear history |
| Gestures | Enable/disable swipe-up search |
| System | Set default launcher, notification access |
| About | Version, who we are, GitHub, website, report a bug, contact |

---

## 🛠️ Project Structure

This repository is structured for Obsidian-based documentation and native Android development:

*   **`app/`**: Android application sources (Kotlin).
    *   `MainActivity.kt`: Home screen, search panel, gestures, adaptive theming.
    *   `SettingsActivity.kt`: Slim Settings screen (toggles, weather, about/contact).
    *   `SlimPreferences.kt`: All user-configurable options (SharedPreferences).
    *   `WeatherService.kt`: Optional Open-Meteo client (geocoding + current weather).
    *   `WaveGestureView.kt`: The wave alphabet index custom view.
    *   `AppRepository.kt` / `AppDatabase.kt`: Room-backed app cache and favorites.
*   **`docs/`**: Obsidian knowledge vault detailing the project structure.
    *   [docs/index.md](docs/index.md): Central dashboard for requirements, architecture, and agent guides.
    *   [docs/architecture/system_requirements.md](docs/architecture/system_requirements.md): System permissions and components.
    *   [docs/architecture/ui_ux_layout.md](docs/architecture/ui_ux_layout.md): Mathematical models for the wave scrolling.
    *   [docs/distribution/fdroid_publishing.md](docs/distribution/fdroid_publishing.md): F-Droid FOSS packaging and metadata guidelines.
*   **`website/`**: Sources for the GitHub Pages landing page.
*   **`.github/workflows/`**: Continuous Integration pipelines.
    *   `android-build.yml`: Verifies Kotlin compiling, lints, and builds debug APKs.
    *   `deploy-pages.yml`: Automatically deploys the landing page to GitHub Pages on merges to main.

---

## ⚙️ Building From Source

Prerequisites:
- Android SDK (API 34+)
- JDK 17

Run the following commands in your shell to build and run the debug APK:

```bash
# Clone the repository
git clone https://github.com/OpScaleHub/Slim.git
cd Slim

# Compile the application
./gradlew assembleDebug

# Install on a running emulator/device
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 👥 Who We Are

Slim is built in the open by **OpScaleHub** and community contributors. No company backing, no ads, no telemetry — just people who want a calmer phone.

*   🐛 Found a bug? [Open an issue](https://github.com/OpScaleHub/Slim/issues)
*   💡 Have a feature idea? [Start a discussion](https://github.com/OpScaleHub/Slim/issues/new)
*   ✉️ Contact: **lcommonid@gmail.com**

---

## 📄 License

This project is licensed under the Apache License 2.0. See the `LICENSE` file for details.
