package com.apkmanager.app.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Standard Android system package installer fallback.
 * Allows installing single APKs and split APKs without ADB privileges
 * by triggering the native Android Package Installer UI.
 */
object SystemPackageInstaller {

    private const val TAG = "SystemPackageInstaller"

    sealed class Result {
        data object Success : Result()
        data class PermissionRequired(val intent: Intent) : Result()
        data class Failure(val error: String) : Result()
    }

    /**
     * Checks if the app has permission to request package installs on Android 8.0+.
     */
    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Returns the intent to request UNKNOWN_APP_SOURCES permission.
     */
    fun createPermissionIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Installs a single APK file via Android's native system installer.
     */
    fun installApkFile(context: Context, apkFile: File): Result {
        if (!canRequestPackageInstalls(context)) {
            return Result.PermissionRequired(createPermissionIntent(context))
        }

        return try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            Result.Success
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to launch package installer")
        }
    }

    /**
     * Installs one or more APKs (including split APKs) using content URIs.
     */
    suspend fun installApkUris(context: Context, uris: List<Uri>): Result = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext Result.Failure("No APKs provided")

        if (!canRequestPackageInstalls(context)) {
            return@withContext Result.PermissionRequired(createPermissionIntent(context))
        }

        if (uris.size == 1) {
            val uri = uris[0]
            try {
                // If it's a file scheme or needs copying to FileProvider cache
                val targetFile = if (uri.scheme == "file") {
                    File(uri.path ?: "")
                } else {
                    val cacheFile = File(context.cacheDir, "install_temp_${System.currentTimeMillis()}.apk")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(cacheFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    cacheFile
                }

                if (!targetFile.exists() || targetFile.length() == 0L) {
                    return@withContext Result.Failure("APK file is empty or inaccessible")
                }

                return@withContext installApkFile(context, targetFile)
            } catch (e: Exception) {
                return@withContext Result.Failure(e.message ?: "Failed to prepare APK file")
            }
        }

        // Multiple APKs: Use PackageInstaller session
        try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            session.use { activeSession ->
                uris.forEachIndexed { index, uri ->
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        activeSession.openWrite("split_$index.apk", 0, -1).use { output ->
                            input.copyTo(output)
                            activeSession.fsync(output)
                        }
                    } ?: throw IllegalStateException("Cannot read input stream for split $index")
                }

                // Create a status intent for callback
                val intent = Intent(context, com.apkmanager.app.MainActivity::class.java).apply {
                    action = "com.apkmanager.app.INSTALL_COMPLETE"
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    sessionId,
                    intent,
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                activeSession.commit(pendingIntent.intentSender)
            }

            Result.Success
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to install split APKs via package installer")
        }
    }
}
