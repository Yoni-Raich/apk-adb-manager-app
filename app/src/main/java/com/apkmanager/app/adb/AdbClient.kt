package com.apkmanager.app.adb

import android.content.Context
import android.net.Uri
import android.util.Log
import com.flyfishxu.kadb.Kadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-level ADB client backed by Kadb for pairing, TLS authentication,
 * shell commands, and package operations.
 */
class AdbClient(private val context: Context) {

    companion object {
        private const val TAG = "AdbClient"
        private const val HOST = "127.0.0.1"
    }

    private var client: Kadb? = null
    private var installer: AdbInstaller? = null

    val isConnected: Boolean
        get() = client?.connectionCheck() == true

    suspend fun pair(
        port: Int,
        pairingCode: String
    ): AdbPairing.PairingResult = withContext(Dispatchers.IO) {
        AdbPairing.pair(
            host = HOST,
            port = port,
            pairingCode = pairingCode
        )
    }

    suspend fun connect(port: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            disconnect()

            Log.d(TAG, "Connecting to $HOST:$port")
            val newClient = Kadb.create(HOST, port, 10_000, 30_000)
            client = newClient
            val probe = newClient.shell("echo APK_MANAGER_CONNECTED")
            check(probe.exitCode == 0 && probe.output.contains("APK_MANAGER_CONNECTED")) {
                probe.allOutput.ifBlank { "ADB connection check failed" }
            }

            client = newClient
            installer = AdbInstaller(newClient, context)

            Log.d(TAG, "Connected to ADB daemon on port $port")
            Result.success(Unit)
        } catch (e: Exception) {
            disconnect()
            Log.e(TAG, "Connection to $HOST:$port failed", e)
            Result.failure(e)
        }
    }

    fun disconnect() {
        try {
            client?.close()
        } catch (_: Exception) {
        }
        client = null
        installer = null
    }

    suspend fun executeShell(command: String): ShellResult = withContext(Dispatchers.IO) {
        val adb = client ?: throw AdbException("Not connected")
        val response = adb.shell(command)
        ShellResult(
            output = response.allOutput.trim(),
            exitCode = response.exitCode
        )
    }

    suspend fun installApk(apkUri: Uri): AdbInstaller.InstallResult = withContext(Dispatchers.IO) {
        val activeInstaller = installer ?: throw AdbException("Not connected")
        activeInstaller.installApk(apkUri)
    }

    suspend fun installSplitApks(apkUris: List<Uri>): AdbInstaller.InstallResult = withContext(Dispatchers.IO) {
        val activeInstaller = installer ?: throw AdbException("Not connected")
        activeInstaller.installSplitApks(apkUris)
    }

    suspend fun uninstallPackage(packageName: String): AdbInstaller.InstallResult = withContext(Dispatchers.IO) {
        val activeInstaller = installer ?: throw AdbException("Not connected")
        activeInstaller.uninstall(packageName)
    }

    suspend fun listPackages(includeSystemApps: Boolean = false): List<String> = withContext(Dispatchers.IO) {
        val flag = if (includeSystemApps) "" else "-3"
        executeShell("pm list packages $flag").lines()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }
            .sorted()
    }

    suspend fun getPackageInfo(packageName: String): Map<String, String> = withContext(Dispatchers.IO) {
        val result = executeShell("dumpsys package $packageName")
        parsePackageInfo(result.output)
    }

    suspend fun getApkPath(packageName: String): String? = withContext(Dispatchers.IO) {
        executeShell("pm path $packageName").lines()
            .firstOrNull { it.startsWith("package:") }
            ?.removePrefix("package:")
    }

    private fun parsePackageInfo(dump: String): Map<String, String> {
        val info = mutableMapOf<String, String>()

        for (line in dump.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("versionCode=") -> {
                    for (part in trimmed.split(" ")) {
                        if (part.startsWith("versionCode=")) {
                            info["versionCode"] = part.removePrefix("versionCode=")
                        }
                        if (part.startsWith("targetSdk=")) {
                            info["targetSdk"] = part.removePrefix("targetSdk=")
                        }
                    }
                }
                trimmed.startsWith("versionName=") -> {
                    info["versionName"] = trimmed.removePrefix("versionName=")
                }
                trimmed.startsWith("firstInstallTime=") -> {
                    info["firstInstallTime"] = trimmed.removePrefix("firstInstallTime=")
                }
                trimmed.startsWith("lastUpdateTime=") -> {
                    info["lastUpdateTime"] = trimmed.removePrefix("lastUpdateTime=")
                }
                trimmed.startsWith("installerPackageName=") -> {
                    info["installerPackageName"] = trimmed.removePrefix("installerPackageName=")
                }
                trimmed.startsWith("codePath=") -> {
                    info["codePath"] = trimmed.removePrefix("codePath=")
                }
            }
        }

        return info
    }
}
