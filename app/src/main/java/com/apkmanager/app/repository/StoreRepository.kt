package com.apkmanager.app.repository

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.apkmanager.app.adb.AdbInstaller
import com.apkmanager.app.data.store.StoreApp
import com.apkmanager.app.data.store.StoreAppItem
import com.apkmanager.app.data.updater.GitHubAsset
import com.apkmanager.app.data.updater.GitHubClient
import com.apkmanager.app.data.updater.GitHubRelease
import com.apkmanager.app.data.updater.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Repository for managing the Store catalog, fetching real-time updates from GitHub,
 * and performing silent ADB installations for catalog apps.
 */
class StoreRepository(
    private val context: Context,
    private val adbRepository: AdbRepository,
    private val gitHubClient: GitHubClient = GitHubClient()
) {

    companion object {
        private const val TAG = "StoreRepository"
        private const val REMOTE_CATALOG_URL =
            "https://raw.githubusercontent.com/Yoni-Raich/apk-adb-manager-app/main/store_apps.json"
        private const val GITHUB_API_CONTENTS_URL =
            "https://api.github.com/repos/Yoni-Raich/apk-adb-manager-app/contents/store_apps.json"
        private const val CONNECT_TIMEOUT_MS = 8000
        private const val READ_TIMEOUT_MS = 10000
    }

    private var cachedStoreApps: List<StoreApp>? = null

    /**
     * Loads the store catalog. Tries remote GitHub real-time endpoint first,
     * falling back to the bundled assets file.
     */
    suspend fun loadCatalog(forceRefresh: Boolean = false): List<StoreApp> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedStoreApps != null) {
            return@withContext cachedStoreApps!!
        }

        var jsonString: String? = null

        // 1. Try raw GitHub user content
        try {
            val url = URL(REMOTE_CATALOG_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "APKManager-Android")
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
            }
            if (connection.responseCode in 200..299) {
                jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "Successfully fetched store catalog from raw GitHub")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch catalog from raw GitHub: ${e.message}")
        }

        // 2. Try GitHub API contents endpoint as fallback if raw failed
        if (jsonString.isNullOrBlank()) {
            try {
                val url = URL(GITHUB_API_CONTENTS_URL)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "APKManager-Android")
                    setRequestProperty("Accept", "application/vnd.github+json")
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                }
                if (connection.responseCode in 200..299) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObj = JSONObject(response)
                    val contentBase64 = jsonObj.optString("content", "")
                    if (contentBase64.isNotBlank()) {
                        val decodedBytes = Base64.decode(contentBase64.replace("\n", ""), Base64.DEFAULT)
                        jsonString = String(decodedBytes, Charsets.UTF_8)
                        Log.d(TAG, "Successfully fetched store catalog from GitHub API")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch catalog from GitHub API: ${e.message}")
            }
        }

        // 3. Fallback to bundled assets
        if (jsonString.isNullOrBlank()) {
            try {
                jsonString = context.assets.open("store_apps.json").bufferedReader().use { it.readText() }
                Log.d(TAG, "Loaded store catalog from bundled assets")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load store catalog from assets: ${e.message}")
            }
        }

        val parsedApps = parseCatalogJson(jsonString ?: "[]")
        if (parsedApps.isNotEmpty()) {
            cachedStoreApps = parsedApps
        }
        parsedApps
    }

    /**
     * Parses the JSON catalog string into a list of [StoreApp].
     */
    fun parseCatalogJson(jsonString: String): List<StoreApp> {
        val result = mutableListOf<StoreApp>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val id = item.optString("id", "")
                val name = item.optString("name", id)
                val description = item.optString("description", "")
                val githubRepo = item.optString("githubRepo", "")
                val category = item.optString("category", "General")
                val icon = item.optString("icon", "default")

                val packageNames = mutableListOf<String>()
                val pkgsArray = item.optJSONArray("packageNames")
                if (pkgsArray != null) {
                    for (j in 0 until pkgsArray.length()) {
                        val p = pkgsArray.optString(j, "").trim()
                        if (p.isNotEmpty()) packageNames.add(p)
                    }
                } else {
                    val singlePkg = item.optString("packageName", "").trim()
                    if (singlePkg.isNotEmpty()) packageNames.add(singlePkg)
                }

                if (id.isNotBlank() && githubRepo.isNotBlank()) {
                    result.add(
                        StoreApp(
                            id = id,
                            name = name,
                            description = description,
                            githubRepo = githubRepo,
                            packageNames = packageNames,
                            category = category,
                            icon = icon
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing store catalog JSON", e)
        }
        return result
    }

    /**
     * Queries device installation status and GitHub releases concurrently for all catalog apps.
     */
    suspend fun getStoreItems(forceRefresh: Boolean = false): List<StoreAppItem> = coroutineScope {
        val catalog = loadCatalog(forceRefresh)
        val packageManager = context.packageManager

        val deferredList = catalog.map { storeApp ->
            async(Dispatchers.IO) {
                // Determine install status
                var isInstalled = false
                var installedPkg: String? = null
                var versionName: String? = null
                var versionCode: Long = 0L

                for (pkgName in storeApp.packageNames) {
                    try {
                        val pInfo = packageManager.getPackageInfo(pkgName, 0)
                        isInstalled = true
                        installedPkg = pkgName
                        versionName = pInfo.versionName ?: ""
                        versionCode = PackageInfoCompat.getLongVersionCode(pInfo)
                        break
                    } catch (_: PackageManager.NameNotFoundException) {
                        // Check via ADB shell if connected
                        if (adbRepository.isConnected) {
                            try {
                                val adbInfo = adbRepository.getPackageInfo(pkgName)
                                if (adbInfo.isNotEmpty() && !adbInfo["versionName"].isNullOrBlank()) {
                                    isInstalled = true
                                    installedPkg = pkgName
                                    versionName = adbInfo["versionName"] ?: ""
                                    versionCode = adbInfo["versionCode"]?.toLongOrNull() ?: 0L
                                    break
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }

                // Query GitHub for latest release
                var latestRelease: GitHubRelease? = null
                var latestAsset: GitHubAsset? = null
                var status: UpdateStatus = UpdateStatus.Idle
                var updateAvailable = false

                val releaseResult = gitHubClient.getLatestRelease(storeApp.githubRepo, forceRefresh)
                releaseResult.fold(
                    onSuccess = { release ->
                        latestRelease = release
                        latestAsset = gitHubClient.findBestAssetForDevice(release.assets)

                        if (latestAsset == null) {
                            status = UpdateStatus.Error("No compatible APK found in release ${release.tagName}")
                        } else if (isInstalled) {
                            val isNewer = isNewerVersion(versionName ?: "", release.cleanVersion)
                            if (isNewer) {
                                updateAvailable = true
                                status = UpdateStatus.UpdateAvailable(release, latestAsset!!)
                            } else {
                                status = UpdateStatus.UpToDate
                            }
                        } else {
                            // App not installed: ready to install
                            status = UpdateStatus.Idle
                        }
                    },
                    onFailure = { error ->
                        status = UpdateStatus.Error(error.message ?: "Failed to query GitHub")
                    }
                )

                StoreAppItem(
                    app = storeApp,
                    isInstalled = isInstalled,
                    installedPackage = installedPkg,
                    installedVersionName = versionName,
                    installedVersionCode = versionCode,
                    latestRelease = latestRelease,
                    latestAsset = latestAsset,
                    isUpdateAvailable = updateAvailable,
                    status = status
                )
            }
        }

        deferredList.awaitAll()
    }

    /**
     * Downloads the APK asset and installs it via ADB.
     */
    suspend fun installOrUpdateApp(
        item: StoreAppItem,
        onProgress: (UpdateStatus) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val asset = item.latestAsset
        if (asset == null) {
            onProgress(UpdateStatus.Error("No downloadable APK asset available"))
            return@withContext false
        }

        if (!adbRepository.isConnected) {
            onProgress(UpdateStatus.Error("ADB is not connected. Connect Wireless Debugging first."))
            return@withContext false
        }

        val cacheDir = File(context.cacheDir, "store_downloads").apply { mkdirs() }
        val apkFile = File(cacheDir, "${item.app.id}_${asset.name}")

        try {
            onProgress(UpdateStatus.Downloading(0f, 0L, asset.size))

            val downloadResult = gitHubClient.downloadAsset(asset, apkFile) { progress, downloaded, total ->
                onProgress(UpdateStatus.Downloading(progress, downloaded, total))
            }

            if (downloadResult.isFailure) {
                val errorMsg = downloadResult.exceptionOrNull()?.message ?: "Download failed"
                onProgress(UpdateStatus.Error(errorMsg))
                return@withContext false
            }

            onProgress(UpdateStatus.Installing("Installing via ADB..."))

            val installResult = adbRepository.installApk(Uri.fromFile(apkFile))

            when (installResult) {
                is AdbInstaller.InstallResult.Success -> {
                    onProgress(UpdateStatus.Success("Successfully installed ${item.app.name}!"))
                    true
                }
                is AdbInstaller.InstallResult.Failure -> {
                    onProgress(UpdateStatus.Error("ADB Install Error: ${installResult.error}"))
                    false
                }
            }
        } catch (e: Exception) {
            onProgress(UpdateStatus.Error(e.message ?: "Installation error"))
            false
        } finally {
            if (apkFile.exists()) apkFile.delete()
        }
    }

    /**
     * Compares installed version with latest GitHub release version.
     */
    private fun isNewerVersion(installed: String, remote: String): Boolean {
        if (installed.isBlank() || remote.isBlank()) return true
        val cleanInstalled = installed.trimStart('v', 'V').trim()
        val cleanRemote = remote.trimStart('v', 'V').trim()

        if (cleanInstalled == cleanRemote) return false

        val installedParts = cleanInstalled.split('.', '-', '_').mapNotNull { it.toIntOrNull() }
        val remoteParts = cleanRemote.split('.', '-', '_').mapNotNull { it.toIntOrNull() }

        val length = maxOf(installedParts.size, remoteParts.size)
        for (i in 0 until length) {
            val inst = installedParts.getOrElse(i) { 0 }
            val rem = remoteParts.getOrElse(i) { 0 }
            if (rem > inst) return true
            if (rem < inst) return false
        }

        return cleanInstalled != cleanRemote
    }
}
