package com.apkmanager.app.data

import android.net.Uri

/**
 * Represents information about an APK file selected for installation.
 */
data class ApkFileInfo(
    val uri: Uri,
    val fileName: String,
    val size: Long,
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: Long = 0
) {
    /**
     * Returns a human-readable file size string.
     */
    val sizeDisplay: String
        get() {
            val kb = size / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> "%.1f GB".format(gb)
                mb >= 1.0 -> "%.1f MB".format(mb)
                kb >= 1.0 -> "%.1f KB".format(kb)
                else -> "$size B"
            }
        }
}
