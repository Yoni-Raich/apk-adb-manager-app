# APK Manager

**APK Manager** is an open-source Android utility that empowers your device with on-device ADB capabilities over Wireless Debugging. It features silent APK installations, automated GitHub release updates, an in-app self-updater, and a curated real-time open-source App Store.

---

## Features

- **On-Device ADB via Wireless Debugging**: Connects to the local Android ADB daemon directly using TLS pairing and mDNS auto-discovery without requiring a computer or root.
- **Silent APK Installation**: Installs single or split APKs silently via ADB shell permissions without manual system prompt confirmations.
- **Package Manager**: Inspect installed applications, package names, version names/codes, and uninstall apps cleanly.
- **GitHub App Updater**: Automatically scans installed open-source apps, queries the GitHub Releases API, and silently updates apps via ADB with one tap.
- **Self-Update Engine**: Checks for updates to APK Manager itself from `Yoni-Raich/apk-adb-manager-app` and installs newer versions silently via ADB.
- **Curated App Store**: Discover and install open-source Android tools (YouTube Downloader, StreamFlix, Hey Mike) with live status tracking (Not Installed / Update Available / Up to date).
- **Dynamic Real-Time Catalog**: The Store catalog is powered by `store_apps.json` fetched directly from GitHub with offline fallback, enabling new apps to be added instantly without app updates.

---

## Included Store Apps

| App | Description | GitHub Repository |
| :--- | :--- | :--- |
| **YouTube Downloader** | Download YouTube and Suno videos, audio, and cover art | [`Yoni-Raich/youtube-downloader-releases`](https://github.com/Yoni-Raich/youtube-downloader-releases) |
| **StreamFlix** | Stream movies and TV series with multi-source scrapers | [`Haim098/streamflix-releases`](https://github.com/Haim098/streamflix-releases) |
| **Hey Mike** | Voice-enabled autonomous Android AI assistant | [`Yoni-Raich/hey-mike`](https://github.com/Yoni-Raich/hey-mike) |

---

## How It Works

1. **Enable Wireless Debugging**:
   - Go to **Settings > System > Developer Options**.
   - Turn on **Wireless Debugging**.
2. **Pair & Connect**:
   - Open **APK Manager**.
   - Tap **Pair** and enter the 6-digit pairing code shown in Wireless Debugging settings.
   - Tap **Connect** (the app automatically discovers the local port via mDNS).
3. **Install & Update**:
   - Install APKs from device storage.
   - Use the **App Store** or **GitHub App Updater** for one-click silent updates.

---

## Technical Stack

- **UI**: Jetpack Compose, Material 3, Material Icons Extended
- **Architecture**: Clean Architecture with MVVM, Kotlin Coroutines, and StateFlow
- **ADB Protocol**: Kadb (pure Kotlin on-device ADB protocol implementation over TLS with BouncyCastle certificate management)
- **Networking**: Real-time GitHub Releases REST API integration with CPU architecture APK matching (`arm64-v8a`, `armeabi-v7a`, `universal`)
- **Storage**: Jetpack DataStore Preferences

---

## Building from Source

```bash
git clone https://github.com/Yoni-Raich/apk-adb-manager-app.git
cd apk-adb-manager-app
./gradlew assembleDebug
```

Compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## License

Apache License 2.0
