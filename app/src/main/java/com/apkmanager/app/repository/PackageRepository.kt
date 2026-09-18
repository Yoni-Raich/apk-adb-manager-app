package com.apkmanager.app.repository

import com.apkmanager.app.data.PackageInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for managing the list of installed packages.
 * Provides caching and search/filter capabilities.
 */
class PackageRepository(
    private val adbRepository: AdbRepository,
    private val context: android.content.Context? = null
) {

    private val _packages = MutableStateFlow<List<PackageInfo>>(emptyList())
    val packages: StateFlow<List<PackageInfo>> = _packages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var cachedPackages: List<PackageInfo> = emptyList()

    /**
     * Refreshes the list of installed packages using high-speed single-command batch resolution.
     */
    suspend fun refresh(includeSystemApps: Boolean = false) {
        _isLoading.value = true
        try {
            if (!adbRepository.isConnected) {
                cachedPackages = emptyList()
                _packages.value = emptyList()
                return
            }

            val entries = try {
                adbRepository.listPackagesDetailed(includeSystemApps)
            } catch (e: Exception) {
                android.util.Log.e("PackageRepository", "Failed to list packages via ADB", e)
                emptyList()
            }

            val pm = context?.packageManager
            val packages = entries.map { entry ->
                val isSystem = entry.apkPath.let { path ->
                    path.startsWith("/system") ||
                    path.startsWith("/vendor") ||
                    path.startsWith("/product") ||
                    path.startsWith("/system_ext") ||
                    path.startsWith("/apex") ||
                    path.startsWith("/odm") ||
                    path.startsWith("/oem")
                }

                // Rapid in-process resolution from local PackageManager
                val localInfo = try {
                    pm?.getPackageInfo(entry.packageName, 0)
                } catch (_: Exception) {
                    null
                }

                PackageInfo(
                    packageName = entry.packageName,
                    versionName = localInfo?.versionName ?: "",
                    versionCode = localInfo?.let { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(it).toString() } ?: "",
                    apkPath = entry.apkPath,
                    installerPackage = entry.installer,
                    firstInstallTime = localInfo?.firstInstallTime?.toString() ?: "",
                    lastUpdateTime = localInfo?.lastUpdateTime?.toString() ?: "",
                    targetSdk = localInfo?.applicationInfo?.targetSdkVersion?.toString() ?: "",
                    isSystemApp = isSystem
                )
            }
            cachedPackages = packages
            _packages.value = packages
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Searches packages by name.
     */
    fun search(query: String) {
        if (query.isBlank()) {
            _packages.value = cachedPackages
        } else {
            _packages.value = cachedPackages.filter {
                it.packageName.contains(query, ignoreCase = true) ||
                it.displayName.contains(query, ignoreCase = true)
            }
        }
    }

    /**
     * Gets details for a single package.
     */
    suspend fun getPackageDetails(packageName: String): PackageInfo? {
        return cachedPackages.find { it.packageName == packageName }
            ?: try {
                val info = adbRepository.getPackageInfo(packageName)
                val apkPath = adbRepository.getApkPath(packageName)
                PackageInfo(
                    packageName = packageName,
                    versionName = info["versionName"] ?: "",
                    versionCode = info["versionCode"] ?: "",
                    apkPath = apkPath ?: info["codePath"] ?: "",
                    installerPackage = info["installerPackageName"] ?: "",
                    firstInstallTime = info["firstInstallTime"] ?: "",
                    lastUpdateTime = info["lastUpdateTime"] ?: "",
                    targetSdk = info["targetSdk"] ?: ""
                )
            } catch (e: Exception) {
                null
            }
    }
}
