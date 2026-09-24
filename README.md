# APK Manager

**APK Manager** is an open-source Android utility that empowers your device with on-device ADB capabilities over Wireless Debugging. It features silent APK installations, automated GitHub release updates, an in-app self-updater, and a curated real-time open-source App Store.

---

## Features

- **On-Device ADB via Wireless Debugging**: Connects to the local Android ADB daemon directly using TLS pairing and mDNS auto-discovery without requiring a computer or root.
- **Guided Wireless Setup**: A live checklist (Developer options, Wi-Fi, Wireless debugging, pairing) that reads the real system state and connects automatically as soon as Wireless debugging is on.
- **Code-Only Pairing**: The pairing port is found over mDNS, so you only type the 6-digit code — in the app or as a reply to the pairing notification. Pairing connects right away.
- **Keeps Wireless Debugging On**: After the first connection the app grants itself `WRITE_SECURE_SETTINGS` over ADB and switches Wireless debugging back on by itself after reboots or Wi-Fi changes (can be turned off).
- **Silent APK Installation, ADB Only**: Installs single or split APKs silently over ADB. There is no system-installer fallback; without a connection the app asks you to connect.
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
   - Open **APK Manager** and tap the Wireless ADB card.
   - Follow the checklist. When asked to pair, open **Pair device with pairing code** and enter only the 6-digit code.
   - The app connects on its own — no port to type.
3. **Install & Update**:
   - Install APKs from device storage.
   - Use the **App Store** or **GitHub App Updater** for one-click silent updates.

---

## Technical Stack

- **UI**: Jetpack Compose, Material 3 (Google Play-style palette), Material Icons Extended, real launcher icons via PackageManager
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
