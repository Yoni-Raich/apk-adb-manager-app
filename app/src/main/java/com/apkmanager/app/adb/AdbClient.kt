package com.apkmanager.app.adb

import android.content.Context
import android.net.Uri
import android.util.Log
import com.flyfishxu.kadb.Kadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Data class representing package information retrieved in batch mode.
 */
data class PackageEntry(
    val packageName: String,
    val apkPath: String,
    val installer: String
)

/**
 * High-level ADB client backed by Kadb for pairing, TLS authentication,
 * shell commands, and package operations.
 */
class AdbClient(private val context: Context) {

    companion object {
        private const val TAG = "AdbClient"
        private const val HOST = "127.0.0.1"
        private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+$")

        fun isValidPackageName(packageName: String): Boolean = PACKAGE_NAME_REGEX.matches(packageName)
    }

    private var client: Kadb? = null
    private var installer: AdbInstaller? = null
    private val connectionMutex = Mutex()

    val isConnected: Boolean
        get() = client?.connectionCheck() == true

    /**
     * Actively verifies if the connection is live with a lightweight probe.
     * If adbd dropped or the socket closed, disconnects and returns false.
     */
    suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            val adb = client ?: return@withLock false
            try {
                val res = adb.shell("echo 1")
                if (res.exitCode == 0 && res.output.trim() == "1") {
                    true
                } else {
                    disconnectInternal()
                    false
                }
            } catch (e: Exception) {
                disconnectInternal()
                false
            }
        }
    }

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
        connectionMutex.withLock {
            try {
                disconnectInternal()

                Log.d(TAG, "Connecting to $HOST:$port")
                val newClient = Kadb.create(HOST, port, 10_000, 30_000)
                val probe = newClient.shell("echo APK_MANAGER_CONNECTED")
                check(probe.exitCode == 0 && probe.output.contains("APK_MANAGER_CONNECTED")) {
                    probe.allOutput.ifBlank { "ADB connection check failed" }
                }

                client = newClient
                installer = AdbInstaller(newClient, context)

                Log.d(TAG, "Connected to ADB daemon on port $port")
                Result.success(Unit)
            } catch (e: Exception) {
                disconnectInternal()
                Log.e(TAG, "Connection to $HOST:$port failed", e)
                Result.failure(e)
            }
        }
    }

    fun disconnect() {
        disconnectInternal()
    }

    private fun disconnectInternal() {
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

    suspend fun pushFile(localFile: java.io.File, remotePath: String): Unit = withContext(Dispatchers.IO) {
        val adb = client ?: throw AdbException("Not connected")
        adb.push(localFile, remotePath, 420, System.currentTimeMillis())
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
        require(isValidPackageName(packageName)) { "Invalid package name: $packageName" }
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

    suspend fun listPackagesDetailed(includeSystemApps: Boolean = false): List<PackageEntry> = withContext(Dispatchers.IO) {
        val flag = if (includeSystemApps) "-f -i" else "-f -i -3"
        val output = executeShell("pm list packages $flag").output
        val entries = mutableListOf<PackageEntry>()

        for (line in output.lines()) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("package:")) continue
            val content = trimmed.removePrefix("package:")
            val eqIndex = content.lastIndexOf('=')
            if (eqIndex == -1) continue

            val path = content.substring(0, eqIndex).trim()
            val remainder = content.substring(eqIndex + 1).trim()
            val parts = remainder.split("\\s+".toRegex())
            val pkg = parts.firstOrNull() ?: continue
            if (!isValidPackageName(pkg)) continue

            val installerPart = parts.find { it.startsWith("installer=") }
            val installer = installerPart?.removePrefix("installer=")?.takeIf { it != "null" } ?: ""

            entries.add(PackageEntry(packageName = pkg, apkPath = path, installer = installer))
        }
        entries.sortedBy { it.packageName }
    }

    suspend fun getPackageInfo(packageName: String): Map<String, String> = withContext(Dispatchers.IO) {
        require(isValidPackageName(packageName)) { "Invalid package name: $packageName" }
        val result = executeShell("dumpsys package $packageName")
        parsePackageInfo(result.output)
    }

    suspend fun getApkPath(packageName: String): String? = withContext(Dispatchers.IO) {
        require(isValidPackageName(packageName)) { "Invalid package name: $packageName" }
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
