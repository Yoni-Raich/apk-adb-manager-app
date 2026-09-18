package com.apkmanager.app.repository

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.apkmanager.app.adb.AdbInstaller
import com.apkmanager.app.data.updater.GitHubAsset
import com.apkmanager.app.data.updater.GitHubClient
import com.apkmanager.app.data.updater.GitHubRelease
import com.apkmanager.app.data.updater.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages self-updating for APK Manager (com.apkmanager.app) directly from GitHub Releases
 * and performing silent ADB self-installation.
 */
class SelfUpdateRepository(
    private val context: Context,
    private val adbRepository: AdbRepository,
    private val gitHubClient: GitHubClient = GitHubClient()
) {

    companion object {
        private const val TAG = "SelfUpdateRepository"
        const val SELF_REPO = "Yoni-Raich/apk-adb-manager-app"
    }

    data class SelfUpdateInfo(
        val currentVersionName: String,
        val currentVersionCode: Long,
        val latestRelease: GitHubRelease? = null,
        val latestAsset: GitHubAsset? = null,
        val status: UpdateStatus = UpdateStatus.Idle
    )

    fun getCurrentVersionName(): String {
        return try {
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            pkg.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    fun getCurrentVersionCode(): Long {
        return try {
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            PackageInfoCompat.getLongVersionCode(pkg)
        } catch (_: Exception) {
            1L
        }
    }

    /**
     * Checks if a new release of APK Manager is available on GitHub.
     */
    suspend fun checkSelfUpdate(forceRefresh: Boolean = false): SelfUpdateInfo = withContext(Dispatchers.IO) {
        val currentVerName = getCurrentVersionName()
        val currentVerCode = getCurrentVersionCode()

        val result = gitHubClient.getLatestRelease(SELF_REPO, forceRefresh)

        result.fold(
            onSuccess = { release ->
                val bestAsset = gitHubClient.findBestAssetForDevice(release.assets)
                if (bestAsset == null) {
                    SelfUpdateInfo(
                        currentVersionName = currentVerName,
                        currentVersionCode = currentVerCode,
                        latestRelease = release,
                        status = UpdateStatus.Error("No compatible APK asset found in release ${release.tagName}")
                    )
                } else if (isNewerVersion(currentVerName, release.cleanVersion)) {
                    SelfUpdateInfo(
                        currentVersionName = currentVerName,
                        currentVersionCode = currentVerCode,
                        latestRelease = release,
                        latestAsset = bestAsset,
                        status = UpdateStatus.UpdateAvailable(release, bestAsset)
                    )
                } else {
                    SelfUpdateInfo(
                        currentVersionName = currentVerName,
                        currentVersionCode = currentVerCode,
                        latestRelease = release,
                        latestAsset = bestAsset,
                        status = UpdateStatus.UpToDate
                    )
                }
            },
            onFailure = { error ->
                SelfUpdateInfo(
                    currentVersionName = currentVerName,
                    currentVersionCode = currentVerCode,
                    status = UpdateStatus.Error(error.message ?: "Failed to check for self update")
                )
            }
        )
    }

    /**
     * Downloads and installs the update of APK Manager via ADB, restarting the app afterwards.
     */
    suspend fun installSelfUpdate(
        asset: GitHubAsset,
        onProgress: (UpdateStatus) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (!adbRepository.isConnected) {
            onProgress(UpdateStatus.Error("ADB is not connected. Connect Wireless Debugging first."))
            return@withContext false
        }

        val cacheDir = File(context.cacheDir, "self_update").apply { mkdirs() }
        val apkFile = File(cacheDir, "apk_manager_update.apk")

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

            onProgress(UpdateStatus.Installing("Installing update via ADB..."))

            val installResult = adbRepository.installApk(Uri.fromFile(apkFile))

            when (installResult) {
                is AdbInstaller.InstallResult.Success -> {
                    onProgress(UpdateStatus.Success("APK Manager updated successfully!"))
                    // Relaunch the app via ADB shell am start
                    try {
                        adbRepository.executeShell("am start -n com.apkmanager.app/.MainActivity")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to send am start command after update: ${e.message}")
                    }
                    true
                }
                is AdbInstaller.InstallResult.Failure -> {
                    onProgress(UpdateStatus.Error("ADB Install Error: ${installResult.error}"))
                    false
                }
            }
        } catch (e: Exception) {
            onProgress(UpdateStatus.Error(e.message ?: "Self update error"))
            false
        } finally {
            if (apkFile.exists()) apkFile.delete()
        }
    }

    private fun isNewerVersion(installed: String, remote: String): Boolean {
        if (installed.isBlank() || remote.isBlank()) return false
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
