package com.apkmanager.app.data.updater

/**
 * Represents a release asset from GitHub Releases.
 */
data class GitHubAsset(
    val name: String,
    val size: Long,
    val downloadUrl: String,
    val contentType: String
)

/**
 * Represents a release from GitHub Releases API.
 */
data class GitHubRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val publishedAt: String,
    val assets: List<GitHubAsset>,
    val htmlUrl: String
) {
    val cleanVersion: String
        get() = tagName.trimStart('v', 'V')
}

/**
 * Current status of an app update check or installation.
 */
sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class UpdateAvailable(val release: GitHubRelease, val asset: GitHubAsset) : UpdateStatus()
    object UpToDate : UpdateStatus()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateStatus()
    data class Installing(val message: String = "Installing via ADB...") : UpdateStatus()
    data class Success(val message: String = "Update installed successfully!") : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

/**
 * Represents an installed app that is tracked for GitHub updates.
 */
data class TrackedApp(
    val packageName: String,
    val appName: String,
    val githubRepo: String, // format: "owner/repo"
    val installedVersionName: String,
    val installedVersionCode: Long,
    val status: UpdateStatus = UpdateStatus.Idle
) {
    val repoUrl: String get() = "https://github.com/$githubRepo"
}

/**
 * Represents an installed app option for selection in the tracking dialog.
 */
data class InstalledAppOption(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val suggestedRepo: String = ""
)

