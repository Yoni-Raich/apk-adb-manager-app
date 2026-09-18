package com.apkmanager.app.data

/**
 * Represents information about an installed Android package.
 */
data class PackageInfo(
    val packageName: String,
    val versionName: String = "",
    val versionCode: String = "",
    val apkPath: String = "",
    val installerPackage: String = "",
    val firstInstallTime: String = "",
    val lastUpdateTime: String = "",
    val targetSdk: String = "",
    val isSystemApp: Boolean = false
) {
    /**
     * Returns a short display name derived from the package name.
     * e.g., "com.example.app" -> "app"
     */
    val displayName: String
        get() = packageName.substringAfterLast('.')
            .replaceFirstChar { it.uppercase() }

    /**
     * Returns the version display string.
     */
    val versionDisplay: String
        get() = when {
            versionName.isNotEmpty() && versionCode.isNotEmpty() -> "$versionName ($versionCode)"
            versionName.isNotEmpty() -> versionName
            versionCode.isNotEmpty() -> "Build $versionCode"
            else -> "Unknown"
        }
}
