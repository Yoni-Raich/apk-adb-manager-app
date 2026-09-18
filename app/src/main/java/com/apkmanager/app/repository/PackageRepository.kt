package com.apkmanager.app.repository

import com.apkmanager.app.data.PackageInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for managing the list of installed packages.
 * Provides caching and search/filter capabilities.
 */
class PackageRepository(private val adbRepository: AdbRepository) {

    private val _packages = MutableStateFlow<List<PackageInfo>>(emptyList())
    val packages: StateFlow<List<PackageInfo>> = _packages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var cachedPackages: List<PackageInfo> = emptyList()

    /**
     * Refreshes the list of installed packages.
     */
    suspend fun refresh(includeSystemApps: Boolean = false) {
        _isLoading.value = true
        try {
            val packageNames = adbRepository.listPackages(includeSystemApps)
            val packages = packageNames.map { name ->
                try {
                    val info = adbRepository.getPackageInfo(name)
                    val apkPath = adbRepository.getApkPath(name)
                    PackageInfo(
                        packageName = name,
                        versionName = info["versionName"] ?: "",
                        versionCode = info["versionCode"] ?: "",
                        apkPath = apkPath ?: info["codePath"] ?: "",
                        installerPackage = info["installerPackageName"] ?: "",
                        firstInstallTime = info["firstInstallTime"] ?: "",
                        lastUpdateTime = info["lastUpdateTime"] ?: "",
                        targetSdk = info["targetSdk"] ?: "",
                        isSystemApp = apkPath?.startsWith("/system") == true
                    )
                } catch (e: Exception) {
                    PackageInfo(packageName = name)
                }
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
