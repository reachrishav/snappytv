# SnappyTV 📺

> **Ultra-lightweight, zero-lag Live TV & DTH Player for Android TV / Google TV.**  
> Crafted specifically for low-RAM devices (e.g. 1GB RAM Xiaomi Mi TV 4A, Android 9 / API 28+).

---

## Features

- **⚡ Fast Channel Zapping**: Sub-500ms channel switching with optimized ExoPlayer pipeline.
- **🎯 DTH Remote UX**: Up and Down arrow keys reserved exclusively for channel switching.
- **🔄 Multi-Stream Switcher & Ranked Fallback**:
  - Live streams ordered by verified rank (Primary, Mirror 1, Mirror 2, etc.).
  - Non-intrusive stream selection popup with real-time stream status (`● ACTIVE`) and source badges (PiShow, TangoTV, Samsung FAST, CloudFront, WisePlay).
  - Minimal HUD indicator showing active stream index and total mirrors (e.g. `2/4`).
  - Automatic background fallback if a live stream stalls or fails.
- **⭐ Smart Favorites System**:
  - Favorited channels dynamically float to the top of category lists.
  - One-click Star toggle directly from the HUD.
  - JSON backup and restore supported (`/sdcard/Download/snappytv_backup.json`).
- **🎨 Glassmorphic HUD & Channel Drawer**:
  - Translucent gradient bottom HUD with channel info, real-time clock, date, and stream resolution.
  - Two-pane channel guide drawer with searchable catalog and category filtering.
- **🪶 Tiny Footprint**: Only **~2.4 MB** release APK size, zero bloat, native Android TV performance.

---

## Remote Control Guide

| Remote Key | Action |
| :--- | :--- |
| **D-Pad Up / Down** | Instant Channel Zapping (Switch previous / next channel) |
| **OK / Enter** (while playing) | Open bottom info HUD |
| **OK / Enter** (on Play/Pause icon) | Pause / Resume playback |
| **OK / Enter** (on Stream indicator) | Open **Stream Selection Popup** |
| **OK / Enter** (on Star icon) | Toggle Favorite status |
| **D-Pad Left** | Open Channel Guide & Category Drawer |
| **D-Pad Right** | Navigate HUD controls |
| **Back** | Dismiss popup / HUD / drawer (Double-press exits app) |

---

## Building from Source

### Prerequisites
- JDK 17
- Android SDK (API 26+)

### Build APK
```bash
# Debug build
./gradlew assembleDebug

# Release build (minified with R8 & shrinkResources)
./gradlew assembleRelease
```
The compiled APK will be generated at:
`app/build/outputs/apk/release/app-release.apk`

### Install on Android TV
```bash
adb connect <TV_IP_ADDRESS>:5555
adb -s <TV_IP_ADDRESS>:5555 install -r app/build/outputs/apk/release/app-release.apk
adb -s <TV_IP_ADDRESS>:5555 shell monkey -p com.gathin.snappytv -c android.intent.category.LAUNCHER 1
```

---

## License
MIT License
