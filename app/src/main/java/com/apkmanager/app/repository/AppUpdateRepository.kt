package com.apkmanager.app.repository

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.pm.PackageInfoCompat
import com.apkmanager.app.adb.AdbInstaller
import com.apkmanager.app.data.PreferencesManager
import com.apkmanager.app.data.updater.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Repository for discovering installed GitHub apps, checking for updates,
 * and performing silent ADB installations.
 */
class AppUpdateRepository(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val adbRepository: AdbRepository,
    private val gitHubClient: GitHubClient = GitHubClient()
) {

    /**
     * Scans installed applications and returns those matched with GitHub repositories.
     */
    suspend fun discoverTrackedApps(): List<TrackedApp> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager

        // Read and migrate custom repository preferences
        val customRepoEntries = preferencesManager.customTrackedRepos.first()
        val customMap = mutableMapOf<String, String>()
        for (entry in customRepoEntries) {
            val parts = entry.split("|", limit = 2)
            if (parts.size == 2) {
                val pkgName = parts[0]
                var repo = parts[1].trim().removePrefix("https://github.com/").trim('/')
                // Automatic migration for obsolete/broken default repos
                if (repo.equals("streamflix-reborn/streamflix", ignoreCase = true)) {
                    repo = "streamflix-reborn2/streamflix"
                    preferencesManager.removeCustomTrackedRepo(entry)
                    preferencesManager.addCustomTrackedRepo("$pkgName|$repo")
                } else if (repo.equals("videolan/vlc-android", ignoreCase = true)) {
                    preferencesManager.removeCustomTrackedRepo(entry)
                    continue
                }
                customMap[pkgName] = repo
            }
        }

        val trackedList = mutableListOf<TrackedApp>()
        val seenPackages = mutableSetOf<String>()

        // 1. Explicitly query all known catalog packages & custom packages directly
        val targetPackages = (KnownAppsCatalog.getAllKnownPackages() + customMap.keys).toSet()
        for (pkgName in targetPackages) {
            try {
                val pkg = packageManager.getPackageInfo(pkgName, 0)
                val known = KnownAppsCatalog.findKnownApp(pkgName)
                val repo = customMap[pkgName] ?: known?.defaultRepo ?: continue

                val appName = try {
                    pkg.applicationInfo?.let { packageManager.getApplicationLabel(it).toString() }
                        ?: known?.appName
                        ?: pkgName
                } catch (_: Exception) {
                    known?.appName ?: pkgName
                }

                var versionName = pkg.versionName ?: ""
                var versionCode = PackageInfoCompat.getLongVersionCode(pkg)

                if (adbRepository.isConnected) {
                    try {
                        val adbInfo = adbRepository.getPackageInfo(pkgName)
                        val adbVersion = adbInfo["versionName"]
                        if (!adbVersion.isNullOrBlank()) {
                            versionName = adbVersion
                            versionCode = adbInfo["versionCode"]?.toLongOrNull() ?: versionCode
                        }
                    } catch (_: Exception) {}
                }

                trackedList.add(
                    TrackedApp(
                        packageName = pkgName,
                        appName = appName,
                        githubRepo = repo,
                        installedVersionName = versionName,
                        installedVersionCode = versionCode,
                        status = UpdateStatus.Idle
                    )
                )
                seenPackages.add(pkgName)
            } catch (_: PackageManager.NameNotFoundException) {
                // Check via ADB shell if connected
                if (adbRepository.isConnected) {
                    try {
                        val adbInfo = adbRepository.getPackageInfo(pkgName)
                        if (adbInfo.isNotEmpty() && !adbInfo["versionName"].isNullOrBlank()) {
                            val known = KnownAppsCatalog.findKnownApp(pkgName)
                            val repo = customMap[pkgName] ?: known?.defaultRepo ?: continue
                            trackedList.add(
                                TrackedApp(
                                    packageName = pkgName,
                                    appName = known?.appName ?: pkgName,
                                    githubRepo = repo,
                                    installedVersionName = adbInfo["versionName"] ?: "",
                                    installedVersionCode = adbInfo["versionCode"]?.toLongOrNull() ?: 0L,
                                    status = UpdateStatus.Idle
                                )
                            )
                            seenPackages.add(pkgName)
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }

        // 2. Query all installed non-system packages for known apps or dynamic GitHub links
        try {
            val allPackages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
            for (pkg in allPackages) {
                val pkgName = pkg.packageName
                if (seenPackages.contains(pkgName)) continue

                // Check known apps catalog (including prefix & io.github heuristics)
                val known = KnownAppsCatalog.findKnownApp(pkgName)
                var repo = customMap[pkgName] ?: known?.defaultRepo

                // Check AndroidManifest meta-data
                if (repo == null) {
                    repo = extractGitHubRepoFromMetaData(pkg)
                }

                // For non-system apps, check APK DEX strings if not found yet
                val isSystem = (pkg.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
                if (repo == null && !isSystem) {
                    val apkPath = pkg.applicationInfo?.sourceDir
                    if (apkPath != null) {
                        repo = extractGitHubRepoFromApk(apkPath)
                    }
                }

                if (repo == null) continue

                val appName = try {
                    pkg.applicationInfo?.let { packageManager.getApplicationLabel(it).toString() }
                        ?: known?.appName
                        ?: pkgName
                } catch (_: Exception) {
                    known?.appName ?: pkgName
                }

                val versionName = pkg.versionName ?: ""
                val versionCode = PackageInfoCompat.getLongVersionCode(pkg)

                trackedList.add(
                    TrackedApp(
                        packageName = pkgName,
                        appName = appName,
                        githubRepo = repo,
                        installedVersionName = versionName,
                        installedVersionCode = versionCode,
                        status = UpdateStatus.Idle
                    )
                )
                seenPackages.add(pkgName)
            }
        } catch (_: Exception) {}

        // 3. Add any custom tracked apps that might not be installed yet
        for ((pkgName, repo) in customMap) {
            if (!seenPackages.contains(pkgName)) {
                trackedList.add(
                    TrackedApp(
                        packageName = pkgName,
                        appName = repo.substringAfterLast('/'),
                        githubRepo = repo,
                        installedVersionName = "Not installed",
                        installedVersionCode = 0L,
                        status = UpdateStatus.Idle
                    )
                )
            }
        }

        trackedList.sortedBy { it.appName.lowercase() }.also {
            android.util.Log.d("AppUpdater", "Discovered ${it.size} apps: ${it.map { app -> app.packageName }}")
        }
    }

    /**
     * Checks for updates for a specific tracked app from GitHub Releases.
     */
    suspend fun checkUpdate(app: TrackedApp): TrackedApp = withContext(Dispatchers.IO) {
        android.util.Log.d("AppUpdater", "Checking GitHub for ${app.packageName} via ${app.githubRepo}...")
        val result = gitHubClient.getLatestRelease(app.githubRepo)

        result.fold(
            onSuccess = { release ->
                val bestAsset = gitHubClient.findBestAssetForDevice(release.assets)
                if (bestAsset == null) {
                    app.copy(status = UpdateStatus.Error("No compatible APK found in release ${release.tagName}"))
                } else if (isNewerVersion(app.installedVersionName, release.cleanVersion)) {
                    app.copy(status = UpdateStatus.UpdateAvailable(release, bestAsset))
                } else {
                    app.copy(status = UpdateStatus.UpToDate)
                }
            },
            onFailure = { error ->
                app.copy(status = UpdateStatus.Error(error.message ?: "Failed to check GitHub"))
            }
        )
    }

    /**
     * Downloads and installs an update silently using ADB.
     */
    suspend fun installUpdate(
        app: TrackedApp,
        asset: GitHubAsset,
        adbRepository: AdbRepository,
        onProgress: (UpdateStatus) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(cacheDir, "${app.packageName}_update.apk")

        try {
            if (!adbRepository.verifyOrReconnect()) {
                onProgress(UpdateStatus.Error(AdbRepository.ADB_REQUIRED_MESSAGE))
                return@withContext false
            }

            onProgress(UpdateStatus.Downloading(0f, 0L, asset.size))

            val downloadResult = gitHubClient.downloadAsset(asset, apkFile) { progress, downloaded, total ->
                onProgress(UpdateStatus.Downloading(progress, downloaded, total))
            }

            if (downloadResult.isFailure) {
                val errorMsg = downloadResult.exceptionOrNull()?.message ?: "Download failed"
                onProgress(UpdateStatus.Error(errorMsg))
                return@withContext false
            }

            onProgress(UpdateStatus.Installing("Installing silently over ADB..."))
            when (val installResult = adbRepository.installApk(Uri.fromFile(apkFile))) {
                is AdbInstaller.InstallResult.Success -> {
                    onProgress(UpdateStatus.Success("Updated"))
                    true
                }
                is AdbInstaller.InstallResult.Failure -> {
                    onProgress(UpdateStatus.Error("Update failed: ${installResult.error}"))
                    false
                }
            }
        } catch (e: Exception) {
            onProgress(UpdateStatus.Error(e.message ?: "Unknown installation error"))
            false
        }
    }

    /**
     * Adds a custom GitHub repository to track for an app.
     */
    suspend fun addCustomRepo(packageName: String, repo: String) {
        val cleanRepo = repo.trim().removePrefix("https://github.com/").trim('/')
        preferencesManager.addCustomTrackedRepo("$packageName|$cleanRepo")
    }

    /**
     * Updates an existing custom repository mapping.
     */
    suspend fun updateCustomRepo(packageName: String, oldRepo: String, newRepo: String) {
        val cleanOld = oldRepo.trim().removePrefix("https://github.com/").trim('/')
        val cleanNew = newRepo.trim().removePrefix("https://github.com/").trim('/')
        preferencesManager.removeCustomTrackedRepo("$packageName|$cleanOld")
        preferencesManager.addCustomTrackedRepo("$packageName|$cleanNew")
    }

    /**
     * Removes a custom tracked repository.
     */
    suspend fun removeCustomRepo(packageName: String, repo: String) {
        val cleanRepo = repo.trim().removePrefix("https://github.com/").trim('/')
        preferencesManager.removeCustomTrackedRepo("$packageName|$cleanRepo")
    }

    /**
     * Returns a list of installed non-system apps for the tracking dialog picker.
     */
    suspend fun getInstalledUserApps(): List<InstalledAppOption> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val result = mutableListOf<InstalledAppOption>()
        try {
            val all = packageManager.getInstalledPackages(0)
            for (pkg in all) {
                val isSystem = (pkg.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
                if (!isSystem && pkg.packageName != context.packageName) {
                    val label = try {
                        pkg.applicationInfo?.let { packageManager.getApplicationLabel(it).toString() } ?: pkg.packageName
                    } catch (_: Exception) {
                        pkg.packageName
                    }
                    val suggested = KnownAppsCatalog.findKnownApp(pkg.packageName)?.defaultRepo ?: ""
                    result.add(
                        InstalledAppOption(
                            packageName = pkg.packageName,
                            appName = label,
                            versionName = pkg.versionName ?: "",
                            suggestedRepo = suggested
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        result.sortedBy { it.appName.lowercase() }
    }

    private fun extractGitHubRepoFromMetaData(pkg: android.content.pm.PackageInfo): String? {
        val metaData = pkg.applicationInfo?.metaData ?: return null
        for (key in metaData.keySet()) {
            val value = metaData.get(key)?.toString() ?: continue
            val candidate = extractRepoFromCandidate(value)
            if (candidate != null) return candidate
        }
        return null
    }

    private val IGNORED_ORGS = setOf(
        "google", "android", "square", "squareup", "bumptech", "facebook",
        "airbnb", "reactivex", "jetbrains", "material-components", "jakewharton",
        "coil-kt", "junit-team", "mockito", "skydoves", "ktorio", "arrow-kt",
        "mockk", "grpc", "protocolbuffers", "firebase", "apache", "open-telemetry",
        "apollographql", "insert-koin-io", "topjohnwu"
    )

    private fun extractGitHubRepoFromApk(apkPath: String): String? {
        // Raw byte-by-byte DEX scanning causes severe GC freezes and false positives.
        // Apps are reliably identified via KnownAppsCatalog, StoreCatalog, user-added custom repos, and AndroidManifest metadata.
        return null
    }

    private fun extractRepoFromCandidate(text: String): String? {
        val apiPrefix = "api.github.com/repos/"
        if (text.contains(apiPrefix)) {
            val after = text.substringAfter(apiPrefix)
            val parts = after.split('/')
            if (parts.size >= 2) {
                val owner = parts[0]
                val repo = parts[1].substringBefore('?').substringBefore('#')
                if (isValidOwnerRepo(owner, repo)) return "$owner/$repo"
            }
        }
        val releasesPattern = "github.com/"
        if (text.contains(releasesPattern) && text.contains("/releases")) {
            val after = text.substringAfter(releasesPattern).substringBefore("/releases")
            val parts = after.split('/')
            if (parts.size == 2) {
                val owner = parts[0]
                val repo = parts[1].substringBefore('?').substringBefore('#')
                if (isValidOwnerRepo(owner, repo)) return "$owner/$repo"
            }
        }
        return null
    }

    private fun isValidOwnerRepo(owner: String, repo: String): Boolean {
        if (owner.length < 2 || repo.length < 2) return false
        if (IGNORED_ORGS.contains(owner.lowercase())) return false
        val validChars = Regex("^[a-zA-Z0-9._-]+$")
        return validChars.matches(owner) && validChars.matches(repo)
    }

    /**
     * Compares installed version with latest GitHub release version.
     */
    private fun isNewerVersion(installed: String, remote: String): Boolean {
        if (installed == "Not installed") return true
        return com.apkmanager.app.util.VersionComparator.isNewerVersion(installed, remote)
    }
}
