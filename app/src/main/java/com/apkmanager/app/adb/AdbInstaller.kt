package com.apkmanager.app.adb

import android.content.Context
import android.net.Uri
import android.util.Log
import com.flyfishxu.kadb.Kadb
import java.io.File
import java.io.FileOutputStream

/**
 * Handles APK installation and uninstallation through the active Kadb client.
 */
class AdbInstaller(
    private val client: Kadb,
    private val context: Context
) {
    companion object {
        private const val TAG = "AdbInstaller"
    }

    sealed class InstallResult {
        data class Success(val packageName: String = "") : InstallResult()
        data class Failure(val error: String) : InstallResult()
    }

    fun installApk(apkUri: Uri): InstallResult {
        val isLocalFile = apkUri.scheme == "file" && apkUri.path != null && File(apkUri.path!!).exists()
        val (targetFile, needsDelete) = if (isLocalFile) {
            File(apkUri.path!!) to false
        } else {
            val file = try {
                copyToTemp(apkUri, "install.apk")
            } catch (e: Exception) {
                return InstallResult.Failure(e.message ?: "Cannot read APK")
            }
            file to true
        }

        return try {
            client.install(targetFile, "-r", "-t")
            InstallResult.Success()
        } catch (e: Exception) {
            Log.e(TAG, "Installation failed", e)
            InstallResult.Failure(e.message ?: "Unknown error")
        } finally {
            if (needsDelete) {
                targetFile.delete()
            }
        }
    }

    fun installSplitApks(apkUris: List<Uri>): InstallResult {
        val tempFiles = mutableListOf<File>()

        return try {
            apkUris.forEachIndexed { index, uri ->
                tempFiles += copyToTemp(uri, "split_$index.apk")
            }
            client.installMultiple(tempFiles, "-r", "-t")
            InstallResult.Success()
        } catch (e: Exception) {
            Log.e(TAG, "Split APK installation failed", e)
            InstallResult.Failure(e.message ?: "Unknown error")
        } finally {
            tempFiles.forEach { it.delete() }
        }
    }

    fun uninstall(packageName: String): InstallResult {
        return try {
            client.uninstall(packageName)
            InstallResult.Success(packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Uninstall failed", e)
            InstallResult.Failure(e.message ?: "Unknown error")
        }
    }

    private fun copyToTemp(uri: Uri, fileName: String): File {
        val tempDir = File(context.cacheDir, "apk_temp").apply { mkdirs() }
        val tempFile = File(tempDir, fileName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw AdbException("Cannot read file: $uri")

        return tempFile
    }
}
