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
            val pm = context?.packageManager
            val packages = if (adbRepository.isConnected) {
                val entries = try {
                    adbRepository.listPackagesDetailed(includeSystemApps)
                } catch (e: Exception) {
                    android.util.Log.e("PackageRepository", "Failed to list packages via ADB", e)
                    emptyList()
                }

                entries.map { entry ->
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
            } else if (pm != null) {
                val localList = try {
                    pm.getInstalledPackages(0)
                } catch (e: Exception) {
                    emptyList()
                }
                localList.mapNotNull { pInfo ->
                    val appInfo = pInfo.applicationInfo
                    val isSystem = (appInfo?.flags?.let { it and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0 }) == true
                    if (!includeSystemApps && isSystem) return@mapNotNull null
                    PackageInfo(
                        packageName = pInfo.packageName,
                        versionName = pInfo.versionName ?: "",
                        versionCode = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pInfo).toString(),
                        apkPath = appInfo?.sourceDir ?: "",
                        installerPackage = try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                pm.getInstallSourceInfo(pInfo.packageName).installingPackageName ?: ""
                            } else {
                                @Suppress("DEPRECATION")
                                pm.getInstallerPackageName(pInfo.packageName) ?: ""
                            }
                        } catch (_: Exception) { "" },
                        firstInstallTime = pInfo.firstInstallTime.toString(),
                        lastUpdateTime = pInfo.lastUpdateTime.toString(),
                        targetSdk = appInfo?.targetSdkVersion?.toString() ?: "",
                        isSystemApp = isSystem
                    )
                }
            } else {
                emptyList()
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
}
