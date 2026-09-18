package com.apkmanager.app.data.store

import com.apkmanager.app.data.updater.GitHubAsset
import com.apkmanager.app.data.updater.GitHubRelease
import com.apkmanager.app.data.updater.UpdateStatus

/**
 * Definition of an open-source app available in the Store catalog.
 */
data class StoreApp(
    val id: String,
    val name: String,
    val description: String,
    val githubRepo: String,
    val packageNames: List<String>,
    val category: String = "App",
    val icon: String = "default"
) {
    val primaryPackage: String
        get() = packageNames.firstOrNull() ?: id

    val repoUrl: String
        get() = "https://github.com/${githubRepo.trim().removePrefix("https://github.com/").trim('/')}"
}

/**
 * Live presentation model of a store app combining catalog metadata,
 * current installation status on device, and release status from GitHub.
 */
data class StoreAppItem(
    val app: StoreApp,
    val isInstalled: Boolean = false,
    val installedPackage: String? = null,
    val installedVersionName: String? = null,
    val installedVersionCode: Long = 0L,
    val latestRelease: GitHubRelease? = null,
    val latestAsset: GitHubAsset? = null,
    val isUpdateAvailable: Boolean = false,
    val status: UpdateStatus = UpdateStatus.Idle
)
