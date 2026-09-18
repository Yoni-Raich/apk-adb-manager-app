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
                } else if (com.apkmanager.app.util.VersionComparator.isNewerVersion(currentVerName, release.cleanVersion)) {
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
     * Uses detached background execution so Android OS package replacement (which SIGKILLs this process)
     * allows ADB to complete installation and relaunch the app cleanly.
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

            onProgress(UpdateStatus.Installing("Verifying cryptographic signature..."))

            // Verify signature before attempting install
            if (!verifySignatures(apkFile)) {
                onProgress(UpdateStatus.Error("Signature mismatch: The downloaded APK does not match current app signing key."))
                return@withContext false
            }

            onProgress(UpdateStatus.Installing("Staging update to device via ADB..."))

            val remoteTmpApk = "/data/local/tmp/apk_manager_update.apk"
            adbRepository.pushFile(apkFile, remoteTmpApk)

            // Delete local file immediately so it doesn't leak when SIGKILL happens
            if (apkFile.exists()) apkFile.delete()

            onProgress(UpdateStatus.Success("Update staged! Restarting APK Manager..."))

            // Detached execution: wait 2s to allow current process to finish output,
            // then pm install, then relaunch MainActivity, then remove tmp file.
            val script = "nohup sh -c 'sleep 2; pm install -r -d -t $remoteTmpApk && am start -n com.apkmanager.app/.MainActivity; rm -f $remoteTmpApk' >/dev/null 2>&1 &"
            adbRepository.executeShell(script)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Self update error", e)
            onProgress(UpdateStatus.Error(e.message ?: "Self update error"))
            false
        } finally {
            if (apkFile.exists()) apkFile.delete()
        }
    }

    private fun verifySignatures(downloadedApk: File): Boolean {
        return try {
            val pm = context.packageManager
            val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val archiveInfo = pm.getPackageArchiveInfo(downloadedApk.absolutePath, flags) ?: return false
            val currentInfo = pm.getPackageInfo(context.packageName, flags)

            val currentSignatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                currentInfo.signingInfo?.apkContentsSigners?.map { it.toByteArray() }
            } else {
                @Suppress("DEPRECATION")
                currentInfo.signatures?.map { it.toByteArray() }
            }

            val archiveSignatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                archiveInfo.signingInfo?.apkContentsSigners?.map { it.toByteArray() }
            } else {
                @Suppress("DEPRECATION")
                archiveInfo.signatures?.map { it.toByteArray() }
            }

            if (currentSignatures.isNullOrEmpty() || archiveSignatures.isNullOrEmpty()) {
                return false
            }

            currentSignatures.any { cur ->
                archiveSignatures.any { arc -> cur.contentEquals(arc) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Signature verification error", e)
            false
        }
    }
}
