package com.apkmanager.app.data.updater

/**
 * Metadata for a known open-source app distributed via GitHub Releases.
 */
data class KnownApp(
    val packageName: String,
    val appName: String,
    val defaultRepo: String
)

/**
 * Built-in directory of popular open-source apps distributed on GitHub.
 * Allows instant zero-configuration detection when these apps are installed.
 */
object KnownAppsCatalog {

    private val catalog = listOf(
        // Self Update
        KnownApp("com.apkmanager.app", "APK Manager", "Yoni-Raich/apk-adb-manager-app"),

        // YouTube Downloader
        KnownApp("com.splendid.ytdl", "YouTube Downloader", "Yoni-Raich/youtube-downloader-releases"),

        // Streamflix
        KnownApp("com.streamflix", "Streamflix", "Haim098/streamflix-releases"),
        KnownApp("com.streamflix.dev", "Streamflix Dev", "Haim098/streamflix-releases"),
        KnownApp("com.streamflixreborn.streamflix", "Streamflix Reborn", "Haim098/streamflix-releases"),
        KnownApp("com.streamflixreborn", "Streamflix Reborn", "Haim098/streamflix-releases"),

        // Hey Mike (Android Agent)
        KnownApp("dev.androidagent.app.dev", "Hey Mike Dev", "Yoni-Raich/hey-mike"),
        KnownApp("dev.androidagent.app", "Hey Mike", "Yoni-Raich/hey-mike"),

        // ReVanced
        KnownApp("app.revanced.android.gms", "ReVanced GmsCore", "ReVanced/GmsCore"),
        KnownApp("app.revanced.manager.flutter", "ReVanced Manager", "ReVanced/revanced-manager"),
        KnownApp("app.revanced.manager", "ReVanced Manager", "ReVanced/revanced-manager"),

        // Media & Utilities
        KnownApp("org.schabi.newpipe", "NewPipe", "TeamNewPipe/NewPipe"),
        KnownApp("org.jellyfin.mobile", "Jellyfin", "jellyfin/jellyfin-android"),
        KnownApp("com.duckduckgo.mobile.android", "DuckDuckGo", "duckduckgo/Android"),
        KnownApp("com.termux", "Termux", "termux/termux-app"),
        KnownApp("eu.kanade.tachiyomi", "Tachiyomi", "tachiyomiorg/tachiyomi"),
        KnownApp("io.github.muntashirakon.AppManager", "App Manager", "MuntashirAkon/AppManager"),
        KnownApp("com.junkfood.seal", "Seal", "JunkFood02/Seal"),
        KnownApp("me.brouken.player", "Just Player", "brouken/JustPlayer"),
        KnownApp("com.aurora.store", "Aurora Store", "whyorean/AuroraStore"),
        KnownApp("dev.imranr.obtainium", "Obtainium", "ImranR98/Obtainium"),
        KnownApp("com.machiav3lli.backup", "Neo Backup", "NeoApplications/Neo-Backup"),
        KnownApp("ch.deletescape.lawnchair.plass", "Lawnchair", "LawnchairLauncher/lawnchair"),
        KnownApp("ch.deletescape.lawnchair", "Lawnchair", "LawnchairLauncher/lawnchair"),
        KnownApp("app.organicmaps", "Organic Maps", "organicmaps/organicmaps"),
        KnownApp("com.wireguard.android", "WireGuard", "WireGuard/wireguard-android"),
        KnownApp("com.tailscale.ipn", "Tailscale", "tailscale/tailscale-android"),
        KnownApp("com.syncthing.android", "Syncthing", "syncthing/syncthing-android")
    )

    private val catalogByPackage = catalog.associateBy { it.packageName }

    fun findKnownApp(packageName: String): KnownApp? {
        // 1. Direct match
        catalogByPackage[packageName]?.let { return it }

        // 2. Prefix / flavor match (e.g. com.streamflix.* or dev.androidagent.app.*)
        for (item in catalog) {
            if (packageName.startsWith(item.packageName) || item.packageName.startsWith(packageName)) {
                return item.copy(packageName = packageName)
            }
        }

        // 3. Heuristic for io.github.* or com.github.*
        if (packageName.startsWith("io.github.") || packageName.startsWith("com.github.")) {
            val parts = packageName.split('.')
            if (parts.size >= 4) {
                val owner = parts[2]
                val repo = parts[3]
                return KnownApp(packageName, repo.replaceFirstChar { it.uppercase() }, "$owner/$repo")
            }
        }

        return null
    }

    fun getAllKnownPackages(): Set<String> {
        return catalogByPackage.keys
    }
}
