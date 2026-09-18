package com.apkmanager.app.repository

import android.content.Context
import android.net.Uri
import com.apkmanager.app.adb.AdbClient
import com.apkmanager.app.adb.AdbInstaller
import com.apkmanager.app.adb.AdbPairing
import com.apkmanager.app.adb.AdbServiceDiscovery
import com.apkmanager.app.data.ConnectionState
import com.apkmanager.app.data.PreferencesManager
import com.flyfishxu.kadb.cert.KadbCert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Repository for ADB connection and command operations.
 * Provides a reactive state flow for connection status.
 */
class AdbRepository(private val context: Context) {

    private val adbClient = AdbClient(context)
    private val preferencesManager = PreferencesManager(context)

    /**
     * mDNS discovery for the normal ADB connection endpoint
     * (`_adb-tls-connect._tcp`). Driven by the UI lifecycle
     * (start on resume, stop on pause).
     */
    val connectDiscovery = AdbServiceDiscovery(context)

    /**
     * mDNS discovery for the temporary pairing endpoint
     * (`_adb-tls-pairing._tcp`). Kept separate: a pairing port must
     * never be used as the connection port.
     */
    val pairingDiscovery = AdbServiceDiscovery(context)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    val isConnected: Boolean get() = adbClient.isConnected

    /**
     * Pairs with the ADB daemon.
     */
    suspend fun pair(port: Int, pairingCode: String): Boolean {
        _connectionState.value = ConnectionState.Pairing("Pairing on port $port...")

        val result = adbClient.pair(port, pairingCode)

        return when (result) {
            is AdbPairing.PairingResult.Success -> {
                preferencesManager.saveIsPaired(true)
                preferencesManager.saveLastPairingPort(port)
                _connectionState.value = ConnectionState.Disconnected
                true
            }
            is AdbPairing.PairingResult.Failure -> {
                _connectionState.value = ConnectionState.Error("Pairing failed: ${result.error}")
                false
            }
        }
    }

    /**
     * Connects to the ADB daemon.
     */
    suspend fun connect(port: Int): Boolean {
        _connectionState.value = ConnectionState.Connecting(port)

        val result = adbClient.connect(port)

        return if (result.isSuccess) {
            preferencesManager.saveLastPort(port)
            _connectionState.value = ConnectionState.Connected(port)
            true
        } else {
            val exception = result.exceptionOrNull()
            val errorMessage = "127.0.0.1:$port: ${mapConnectionError(exception)}"
            _connectionState.value = ConnectionState.Error(errorMessage)
            false
        }
    }

    /**
     * Maps raw connection exceptions to user-friendly error messages.
     */
    private fun mapConnectionError(exception: Throwable?): String {
        if (exception == null) return "Connection failed"

        val message = exception.message ?: exception.toString()

        // SSL certificate rejected by adbd — most common cause is stale pairing
        // or using the pairing port instead of the debugging port
        if (message.contains("SSLV3_ALERT_CERTIFICATE_UNKNOWN", ignoreCase = true) ||
            message.contains("CERTIFICATE_UNKNOWN", ignoreCase = true)
        ) {
            return "Device rejected the certificate. " +
                    "Make sure you use the Wireless Debugging port (not the pairing port). " +
                    "If the problem persists, re-pair the device."
        }

        // Other SSL/TLS errors
        if (message.contains("SSL", ignoreCase = true) ||
            message.contains("TLS", ignoreCase = true)
        ) {
            return "TLS handshake failed. Try re-pairing the device."
        }

        // Connection refused — wrong port or adbd not running
        if (message.contains("Connection refused", ignoreCase = true) ||
            message.contains("ECONNREFUSED", ignoreCase = true)
        ) {
            return "Connection refused. Check that Wireless Debugging is enabled and the port is correct."
        }

        // Timeout
        if (message.contains("timed out", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true)
        ) {
            return "Connection timed out. Check that Wireless Debugging is enabled."
        }

        return message
    }

    /**
     * Disconnects from the ADB daemon.
     */
    fun disconnect() {
        adbClient.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Reconnects only to a port validated by current local discovery.
     */
    suspend fun autoConnect(port: Int): Boolean {
        val isPaired = preferencesManager.isPaired.first()
        if (isPaired && preferencesManager.autoConnect.first() &&
            !connectionState.value.isLoading && !isConnected &&
            connectDiscovery.services.value.any { it.port == port }
        ) {
            return connect(port)
        }
        return false
    }

    /**
     * Installs a single APK.
     */
    suspend fun installApk(apkUri: Uri): AdbInstaller.InstallResult {
        return adbClient.installApk(apkUri)
    }

    /**
     * Installs split APKs.
     */
    suspend fun installSplitApks(apkUris: List<Uri>): AdbInstaller.InstallResult {
        return adbClient.installSplitApks(apkUris)
    }

    /**
     * Uninstalls a package.
     */
    suspend fun uninstallPackage(packageName: String): AdbInstaller.InstallResult {
        return adbClient.uninstallPackage(packageName)
    }

    /**
     * Lists installed packages.
     */
    suspend fun listPackages(includeSystemApps: Boolean = false): List<String> {
        return adbClient.listPackages(includeSystemApps)
    }

    /**
     * Gets package details.
     */
    suspend fun getPackageInfo(packageName: String): Map<String, String> {
        return adbClient.getPackageInfo(packageName)
    }

    /**
     * Gets the APK path for a package.
     */
    suspend fun getApkPath(packageName: String): String? {
        return adbClient.getApkPath(packageName)
    }

    /**
     * Executes a raw shell command via ADB.
     */
    suspend fun executeShell(command: String): com.apkmanager.app.adb.ShellResult {
        return adbClient.executeShell(command)
    }

    /**
     * Gets the preferences manager.
     */
    fun getPreferences(): PreferencesManager = preferencesManager

    /**
     * Clears the pairing state and regenerates the TLS identity.
     * Use when the device rejects the certificate (stale pairing).
     */
    suspend fun clearPairingState() {
        disconnect()
        preferencesManager.saveIsPaired(false)
        preferencesManager.saveLastPort(0)
        preferencesManager.saveLastPairingPort(0)

        // Delete persisted identity files so a fresh key pair is generated on next pair
        val identityDir = java.io.File(context.filesDir, "kadb_identity")
        identityDir.listFiles()?.forEach { it.delete() }

        // Regenerate KadbCert in memory
        KadbCert.set(byteArrayOf(), byteArrayOf())
        val (cert, key) = KadbCert.get()
        val certFile = java.io.File(identityDir.apply { mkdirs() }, "certificate.pem")
        val keyFile = java.io.File(identityDir, "private_key.pem")
        certFile.writeBytes(cert)
        keyFile.writeBytes(key)

        _connectionState.value = ConnectionState.Disconnected
    }
}
