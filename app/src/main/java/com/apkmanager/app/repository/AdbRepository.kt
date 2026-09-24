package com.apkmanager.app.repository

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.apkmanager.app.adb.AdbClient
import com.apkmanager.app.adb.AdbInstaller
import com.apkmanager.app.adb.AdbPairing
import com.apkmanager.app.adb.AdbServiceDiscovery
import com.apkmanager.app.data.ConnectionState
import com.apkmanager.app.data.PreferencesManager
import com.apkmanager.app.util.WirelessDebugging
import com.apkmanager.app.util.WirelessStatus
import com.flyfishxu.kadb.cert.KadbCert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the Wireless ADB connection for the whole app.
 *
 * The flow it drives, so no screen has to:
 * 1. [onForeground] refreshes [wirelessStatus], switches Wireless Debugging back on when
 *    allowed ([PreferencesManager.keepWirelessDebuggingOn] + WRITE_SECURE_SETTINGS) and
 *    starts mDNS discovery of the connect endpoint.
 * 2. Every newly advertised connect port is tried once automatically if the device is paired.
 * 3. [pairAndConnect] finds the pairing port by mDNS, pairs with just the 6-digit code,
 *    then waits for the connect endpoint and connects.
 * 4. After the first connection the app grants itself WRITE_SECURE_SETTINGS over ADB so
 *    step 1 can re-enable Wireless Debugging on its own later.
 */
class AdbRepository(private val context: Context) {

    companion object {
        private const val TAG = "AdbRepository"

        /** Shown whenever an install is attempted without a live ADB connection. */
        const val ADB_REQUIRED_MESSAGE = "Wireless ADB isn't connected. Connect it to install."

        private const val PAIRING_PORT_TIMEOUT_MS = 6_000L
        private const val CONNECT_PORT_TIMEOUT_MS = 12_000L
    }

    /** Outcome of [pairAndConnect]. */
    sealed class PairOutcome {
        data object Connected : PairOutcome()
        data object PairedNotConnected : PairOutcome()
        data class Failed(val message: String) : PairOutcome()
    }

    private val adbClient = AdbClient(context)
    private val preferencesManager = PreferencesManager(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** mDNS discovery of the ADB connection endpoint (`_adb-tls-connect._tcp`). */
    val connectDiscovery = AdbServiceDiscovery(context)

    /**
     * mDNS discovery of the temporary pairing endpoint (`_adb-tls-pairing._tcp`).
     * Kept separate: a pairing port must never be used as the connection port.
     */
    val pairingDiscovery = AdbServiceDiscovery(context)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _wirelessStatus = MutableStateFlow(WirelessDebugging.status(context))
    val wirelessStatus: StateFlow<WirelessStatus> = _wirelessStatus.asStateFlow()

    val isPaired: Flow<Boolean> = preferencesManager.isPaired
    val autoConnectEnabled: Flow<Boolean> = preferencesManager.autoConnect
    val keepWirelessDebuggingOn: Flow<Boolean> = preferencesManager.keepWirelessDebuggingOn

    val isConnected: Boolean get() = adbClient.isConnected

    /** Connect ports already tried automatically since the last foreground; avoids retry loops. */
    private val attemptedPorts = mutableSetOf<Int>()

    /** Set when the user disconnects on purpose; auto-connect stays off until they connect again. */
    private var autoConnectSuppressed = false

    init {
        scope.launch {
            connectDiscovery.services.collect { services ->
                val port = services.firstOrNull()?.port ?: return@collect
                if (attemptedPorts.add(port)) autoConnect(port)
            }
        }
        observeWirelessSettings()
    }

    private fun observeWirelessSettings() {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refreshWirelessStatus()
        }
        val resolver = context.contentResolver
        resolver.registerContentObserver(
            Settings.Global.getUriFor(WirelessDebugging.ADB_WIFI_ENABLED), false, observer
        )
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED), false, observer
        )
    }

    fun refreshWirelessStatus() {
        _wirelessStatus.value = WirelessDebugging.status(context)
    }

    /** Call when the app comes to the foreground. */
    fun onForeground() {
        attemptedPorts.clear()
        refreshWirelessStatus()
        connectDiscovery.startDiscovery(AdbServiceDiscovery.SERVICE_TYPE_CONNECT)
        scope.launch {
            ensureWirelessDebuggingOn()
            verifyOrReconnect()
        }
    }

    /** Call when the app leaves the foreground. */
    fun onBackground() {
        connectDiscovery.stopDiscovery()
    }

    /**
     * Switches Wireless Debugging on by itself when the user allows it and the app holds
     * WRITE_SECURE_SETTINGS. Discovery then picks up the new port and auto-connect runs.
     */
    private suspend fun ensureWirelessDebuggingOn() {
        val status = _wirelessStatus.value
        if (status.wirelessDebugging || !status.canSelfEnable || !status.wifi) return
        if (!preferencesManager.isPaired.first() || !preferencesManager.keepWirelessDebuggingOn.first()) return
        if (WirelessDebugging.enable(context)) {
            Log.d(TAG, "Wireless Debugging re-enabled automatically")
        }
        refreshWirelessStatus()
    }

    /**
     * Actively tests if the ADB connection is truly responsive.
     * If broken, reconnects to the discovered mDNS port or the last known port.
     */
    suspend fun verifyOrReconnect(): Boolean {
        if (adbClient.isConnected && adbClient.ping()) {
            if (_connectionState.value !is ConnectionState.Connected) {
                _connectionState.value = ConnectionState.Connected(preferencesManager.lastPort.first())
            }
            return true
        }
        if (_connectionState.value.isLoading) return false
        _connectionState.value = ConnectionState.Disconnected

        if (autoConnectSuppressed || !preferencesManager.isPaired.first()) return false

        val discoveredPort = connectDiscovery.services.value.firstOrNull()?.port
        val savedPort = preferencesManager.lastPort.first()
        val targetPort = discoveredPort ?: savedPort.takeIf { it > 0 } ?: return false
        return connect(targetPort) && adbClient.ping()
    }

    /** Explicit user request to connect again, also after a manual disconnect. */
    suspend fun reconnect(): Boolean {
        autoConnectSuppressed = false
        attemptedPorts.clear()
        return verifyOrReconnect()
    }

    /**
     * Pairs with just the 6-digit [code]. The pairing port comes from mDNS unless
     * [pairingPort] is given. On success, waits for the connect endpoint and connects.
     */
    suspend fun pairAndConnect(code: String, pairingPort: Int? = null): PairOutcome {
        val port = pairingPort ?: awaitPairingPort()
            ?: return PairOutcome.Failed(
                "Couldn't find the pairing screen. Open \"Pair device with pairing code\" in Wireless debugging and try again."
            )

        _connectionState.value = ConnectionState.Pairing("Pairing…")
        when (val result = adbClient.pair(port, code)) {
            is AdbPairing.PairingResult.Success -> {
                preferencesManager.saveIsPaired(true)
                autoConnectSuppressed = false
            }
            is AdbPairing.PairingResult.Failure -> {
                Log.w(TAG, "Pairing failed: ${result.error}")
                _connectionState.value = ConnectionState.Disconnected
                return PairOutcome.Failed("Pairing failed. Check the code — it changes every time the dialog opens.")
            }
        }

        _connectionState.value = ConnectionState.Pairing("Paired — connecting…")
        connectDiscovery.startDiscovery(AdbServiceDiscovery.SERVICE_TYPE_CONNECT)
        val connectPort = withTimeoutOrNull(CONNECT_PORT_TIMEOUT_MS) {
            connectDiscovery.services.first { it.isNotEmpty() }.first().port
        }
        _connectionState.value = ConnectionState.Disconnected
        if (connectPort == null) return PairOutcome.PairedNotConnected
        attemptedPorts.add(connectPort)
        return if (connect(connectPort)) PairOutcome.Connected else PairOutcome.PairedNotConnected
    }

    /** Waits briefly for the system pairing dialog to advertise its port. */
    suspend fun awaitPairingPort(): Int? {
        pairingDiscovery.startDiscovery(AdbServiceDiscovery.SERVICE_TYPE_PAIRING)
        return withTimeoutOrNull(PAIRING_PORT_TIMEOUT_MS) {
            pairingDiscovery.services.first { it.isNotEmpty() }.first().port
        }
    }

    /**
     * Connects to the ADB daemon on [port].
     */
    suspend fun connect(port: Int): Boolean {
        _connectionState.value = ConnectionState.Connecting(port)
        autoConnectSuppressed = false

        val result = adbClient.connect(port)

        return if (result.isSuccess) {
            preferencesManager.saveLastPort(port)
            _connectionState.value = ConnectionState.Connected(port)
            scope.launch { grantSecureSettingsIfNeeded() }
            true
        } else {
            _connectionState.value = ConnectionState.Error(mapConnectionError(result.exceptionOrNull()))
            false
        }
    }

    /**
     * Lets the app turn Wireless Debugging back on later without the Settings app.
     * `pm grant` of a development permission is allowed from the ADB shell.
     */
    private suspend fun grantSecureSettingsIfNeeded() {
        if (WirelessDebugging.canWriteSecureSettings(context)) return
        if (!preferencesManager.keepWirelessDebuggingOn.first()) return
        try {
            adbClient.executeShell("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
        } catch (e: Exception) {
            Log.w(TAG, "Could not grant WRITE_SECURE_SETTINGS", e)
        }
        refreshWirelessStatus()
    }

    suspend fun setKeepWirelessDebuggingOn(keepOn: Boolean) {
        preferencesManager.saveKeepWirelessDebuggingOn(keepOn)
        if (keepOn && adbClient.isConnected) grantSecureSettingsIfNeeded()
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        preferencesManager.saveAutoConnect(enabled)
    }

    /**
     * Maps raw connection exceptions to user-friendly error messages.
     */
    private fun mapConnectionError(exception: Throwable?): String {
        val message = exception?.message ?: exception?.toString() ?: return "Couldn't connect."
        return when {
            message.contains("CERTIFICATE_UNKNOWN", ignoreCase = true) ->
                "This phone no longer trusts APK Manager. Pair again."
            message.contains("SSL", ignoreCase = true) || message.contains("TLS", ignoreCase = true) ->
                "Secure handshake failed. Pair again."
            message.contains("refused", ignoreCase = true) ->
                "Wireless debugging isn't accepting connections. Turn it off and on."
            message.contains("timeout", ignoreCase = true) || message.contains("timed out", ignoreCase = true) ->
                "Connection timed out. Check that Wireless debugging is on."
            else -> message
        }
    }

    /**
     * Disconnects on purpose; auto-connect stays off until the user connects again.
     */
    fun disconnect() {
        autoConnectSuppressed = true
        adbClient.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Connects automatically to a port validated by current discovery.
     */
    private suspend fun autoConnect(port: Int): Boolean {
        if (autoConnectSuppressed || isConnected || connectionState.value.isLoading) return false
        if (!preferencesManager.isPaired.first() || !preferencesManager.autoConnect.first()) return false
        if (connectDiscovery.services.value.none { it.port == port }) return false
        return connect(port)
    }

    /**
     * Pushes a local file to a remote destination via ADB sync.
     */
    suspend fun pushFile(localFile: java.io.File, remotePath: String) {
        adbClient.pushFile(localFile, remotePath)
    }

    /**
     * Installs a single APK. Verifies active connection first.
     */
    suspend fun installApk(apkUri: Uri): AdbInstaller.InstallResult {
        if (!verifyOrReconnect()) {
            return AdbInstaller.InstallResult.Failure(ADB_REQUIRED_MESSAGE)
        }
        return adbClient.installApk(apkUri)
    }

    /**
     * Installs split APKs. Verifies active connection first.
     */
    suspend fun installSplitApks(apkUris: List<Uri>): AdbInstaller.InstallResult {
        if (!verifyOrReconnect()) {
            return AdbInstaller.InstallResult.Failure(ADB_REQUIRED_MESSAGE)
        }
        return adbClient.installSplitApks(apkUris)
    }

    /**
     * Uninstalls a package. Verifies active connection first.
     */
    suspend fun uninstallPackage(packageName: String): AdbInstaller.InstallResult {
        if (!verifyOrReconnect()) {
            return AdbInstaller.InstallResult.Failure(ADB_REQUIRED_MESSAGE)
        }
        return adbClient.uninstallPackage(packageName)
    }

    /**
     * Lists installed packages with paths and installers in a single batch command.
     */
    suspend fun listPackagesDetailed(includeSystemApps: Boolean = false): List<com.apkmanager.app.adb.PackageEntry> {
        verifyOrReconnect()
        return adbClient.listPackagesDetailed(includeSystemApps)
    }

    /**
     * Lists installed packages.
     */
    suspend fun listPackages(includeSystemApps: Boolean = false): List<String> {
        verifyOrReconnect()
        return adbClient.listPackages(includeSystemApps)
    }

    /**
     * Gets package details.
     */
    suspend fun getPackageInfo(packageName: String): Map<String, String> {
        verifyOrReconnect()
        return adbClient.getPackageInfo(packageName)
    }

    /**
     * Gets the APK path for a package.
     */
    suspend fun getApkPath(packageName: String): String? {
        verifyOrReconnect()
        return adbClient.getApkPath(packageName)
    }

    /**
     * Executes a raw shell command via ADB.
     */
    suspend fun executeShell(command: String): com.apkmanager.app.adb.ShellResult {
        verifyOrReconnect()
        return try {
            adbClient.executeShell(command)
        } catch (e: Exception) {
            if (!adbClient.isConnected) {
                _connectionState.value = ConnectionState.Disconnected
            }
            throw e
        }
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
        autoConnectSuppressed = false
        preferencesManager.saveIsPaired(false)
        preferencesManager.saveLastPort(0)

        // Delete persisted identity files so a fresh key pair is generated on next pair
        val identityDir = java.io.File(context.filesDir, "kadb_identity")
        identityDir.listFiles()?.forEach { it.delete() }

        // Regenerate KadbCert by calling get() which generates a fresh key pair internally
        try {
            val (cert, key) = KadbCert.get()
            val certFile = java.io.File(identityDir.apply { mkdirs() }, "certificate.pem")
            val keyFile = java.io.File(identityDir, "private_key.pem")
            certFile.writeBytes(cert)
            keyFile.writeBytes(key)
        } catch (e: Exception) {
            android.util.Log.e("AdbRepository", "Failed to regenerate TLS certificate", e)
        }

        _connectionState.value = ConnectionState.Disconnected
    }
}
