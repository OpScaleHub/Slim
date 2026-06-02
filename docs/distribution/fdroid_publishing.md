---
tags:
  - distribution
  - fdroid
  - publishing
created: 2026-06-02
status: Draft
---

# F-Droid Publishing & Compliance Guide

This document defines the packaging guidelines, licensing criteria, and metadata configurations required to submit **Slim Launcher** to [F-Droid](https://f-droid.org/), the premier repository for Free and Open Source Software (FOSS) on Android.

Back to **[[index|Main Hub]]**

---

## 🏛️ F-Droid Verification Principles

F-Droid builds all applications directly from source using their build servers. For Slim Launcher to be accepted, it must adhere to strict guidelines:

1. **No Proprietary Dependencies**: All libraries (e.g., UI widgets, image loading, databases) must have free licenses. Google Play Services dependencies must be completely excluded or modularized.
2. **Offline Analytics Policy**: Proprietary tracking kits (like Google Firebase, AppCenter, or Mixpanel) are strictly prohibited. Slim Launcher is built to be offline-first, meaning we do not require the `android.permission.INTERNET` permission at all.
3. **Reproducible Builds**: The build system must be structured so that compiling the code locally yields the exact same byte-for-byte binary as the build server.

---

## ⚙️ F-Droid Build Configuration (`com.slim.launcher.yml`)

This YAML metadata file is submitted to the F-Droid metadata repository (`fdroiddata`). It instructs F-Droid's build bot on how to compile our APK.

```yaml
Categories:
  - System
License: Apache-2.0
WebSite: https://opscalehub.github.io/slim
SourceCode: https://github.com/opscalehub/slim
IssueTracker: https://github.com/opscalehub/slim/issues

Summary: Minimalist, gesture-driven launcher focused on efficiency.
Description: |-
  Slim Launcher is a lightweight, gesture-centric home screen alternative.
  It lists your favorite apps alongside a dynamic, alphabetical side-scrolling wave index.
  
  Features:
  - Wave Alphabet Scroll: Easy single-handed navigation.
  - Inline Notifications: Reply and swipe directly from your favorites.
  - Offline-first: No internet permission, ensuring 100% privacy.

RepoType: git
Repo: https://github.com/opscalehub/slim.git

Builds:
  - versionName: 1.0.0
    versionCode: 1
    commit: v1.0.0
    subdir: app
    gradle:
      - yes
```

---

## 🛠️ Code Adaptations for F-Droid Compliance

### 1. Removing Proprietary SDKs via Gradle Flavors
If we ever decide to include optional services that rely on closed components (like proprietary location search for weather widgets), we must split the app into `foss` and `play` build flavors in `app/build.gradle`:

```gradle
android {
    flavorDimensions "distribution"
    productFlavors {
        foss {
            dimension "distribution"
            // Exclude services requiring proprietary Play Services
        }
        play {
            dimension "distribution"
            // Include Firebase / Google APIs if needed
        }
    }
}
```

### 2. Eliminating Internet Permissions
To enforce user trust, **do not** include the internet permission in the `foss` source set Manifest:
```xml
<!-- Manifest inside app/src/foss/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.slim.launcher">
    <!-- Zero network permissions -->
</manifest>
```
All weather, calendar, and clock metadata are fetched locally via system providers (`CalendarContract`, local system broadcasts).
