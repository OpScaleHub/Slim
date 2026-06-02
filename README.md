# 📱 Slim Launcher

[![Build Status](https://github.com/opscalehub/slim/actions/workflows/android-build.yml/badge.svg)](https://github.com/opscalehub/slim/actions)
[![F-Droid](https://img.shields.io/badge/F--Droid-Compliant-brightgreen)](docs/distribution/fdroid_publishing.md)
[![License](https://img.shields.io/github/license/opscalehub/slim?color=blue)](LICENSE)

**Slim Launcher** is an ultra-minimalist, gesture-based Android launcher designed for speed, focus, and single-handed efficiency. Inspired by Niagara Launcher, it replaces your cluttered app drawer with an elegant, responsive vertical list and a dynamic side-scrolling alphabetical wave gesture.

👉 **View the live landing page at [opscalehub.github.io/slim](https://opscalehub.github.io/slim/)** *(configured via GitHub Pages)*.

---

## ✨ Features

*   **Wave Alphabet Scroll**: Drag along the screen edge to dynamically expand letters in an interactive wave, scrolling instantly to any application with micro-haptic feedback.
*   **Inline Notifications**: Read, dismiss, and reply to messages directly from your favorite apps list on the home screen.
*   **Zero Telemetry & Offline-First**: Free and open-source forever. No analytics, tracking services, or internet access required.
*   **Contextual Header**: Integrated dynamic widget area displaying time, calendar, and local weather alerts in a single line.
*   **Material You Customization**: The user interface matches your system wallpaper coloring dynamically.

---

## 🛠️ Project Structure

This repository is structured for Obsidian-based documentation and native Android development:

*   **`docs/`**: Obsidian knowledge vault detailing the project structure.
    *   [docs/index.md](docs/index.md): Central dashboard for requirements, architecture, and agent guides.
    *   [docs/architecture/system_requirements.md](docs/architecture/system_requirements.md): System permissions and components.
    *   [docs/architecture/ui_ux_layout.md](docs/architecture/ui_ux_layout.md): Mathematical models for the wave scrolling.
    *   [docs/distribution/fdroid_publishing.md](docs/distribution/fdroid_publishing.md): F-Droid FOSS packaging and metadata guidelines.
*   **`website/`**: Sources for the premium GitHub Pages landing page.
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
git clone https://github.com/opscalehub/slim.git
cd slim

# Compile the application
./gradlew assembleDebug

# Install on a running emulator/device
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License

This project is licensed under the Apache License 2.0. See the `LICENSE` file for details.
# Slim
