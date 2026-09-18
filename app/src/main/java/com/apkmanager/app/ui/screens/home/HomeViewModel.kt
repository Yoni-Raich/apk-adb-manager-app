package com.apkmanager.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apkmanager.app.adb.AdbServiceDiscovery
import com.apkmanager.app.adb.DiscoveredAdbService
import com.apkmanager.app.data.ConnectionState
import com.apkmanager.app.repository.AdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.apkmanager.app.data.updater.UpdateStatus
import com.apkmanager.app.repository.SelfUpdateRepository

/**
 * ViewModel for the Home screen.
 */
class HomeViewModel(
    private val adbRepository: AdbRepository,
    private val selfUpdateRepository: SelfUpdateRepository? = null
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = adbRepository.connectionState

    private val _selfUpdateInfo = MutableStateFlow<SelfUpdateRepository.SelfUpdateInfo?>(null)
    val selfUpdateInfo: StateFlow<SelfUpdateRepository.SelfUpdateInfo?> = _selfUpdateInfo.asStateFlow()

    private val _connectPort = MutableStateFlow("")
    val connectPort: StateFlow<String> = _connectPort.asStateFlow()

    /**
     * Discovered `_adb-tls-connect._tcp` endpoints. Non-empty means
     * Wireless Debugging is on and advertising; the port is dynamic so
     * the UI auto-fills it (until the user types their own value).
     */
    val discoveredServices: StateFlow<List<DiscoveredAdbService>> =
        adbRepository.connectDiscovery.services

    /** Discovery problem description, e.g. Wi-Fi off. Null when healthy. */
    val discoveryError: StateFlow<String?> = adbRepository.connectDiscovery.error

    /** True once the user has typed a port manually; auto-fill then stops. */
    private var portEditedByUser = false
    private val attemptedPorts = mutableSetOf<Int>()
    private var autoConnectSuppressed = false

    init {
        // Only connect after discovery has found a reachable local endpoint.
        viewModelScope.launch {
            adbRepository.connectDiscovery.services.collect { services ->
                if (!portEditedByUser) {
                    val port = services.firstOrNull()?.port
                    _connectPort.value = port?.toString().orEmpty()
                    if (port != null && !autoConnectSuppressed &&
                        !connectionState.value.isConnected && !connectionState.value.isLoading &&
                        attemptedPorts.add(port)
                    ) {
                        adbRepository.autoConnect(port)
                    }
                }
            }
        }
        // Check for self update on startup
        checkSelfUpdate()
        verifyConnection()
    }

    /**
     * Actively tests ADB connection health and attempts auto-reconnect if needed.
     */
    fun verifyConnection() {
        viewModelScope.launch {
            adbRepository.verifyOrReconnect()
        }
    }

    fun checkSelfUpdate(forceRefresh: Boolean = false) {
        if (selfUpdateRepository == null) return
        viewModelScope.launch {
            try {
                val info = selfUpdateRepository.checkSelfUpdate(forceRefresh)
                _selfUpdateInfo.value = info
            } catch (_: Exception) {}
        }
    }

    fun installSelfUpdate() {
        val info = _selfUpdateInfo.value ?: return
        val asset = info.latestAsset ?: return
        if (selfUpdateRepository == null) return

        viewModelScope.launch {
            _selfUpdateInfo.value = info.copy(status = UpdateStatus.Downloading(0f, 0L, asset.size))
            selfUpdateRepository.installSelfUpdate(asset) { progressStatus ->
                _selfUpdateInfo.value = _selfUpdateInfo.value?.copy(status = progressStatus)
            }
        }
    }

    /** Starts mDNS discovery; call from onResume. Idempotent. */
    fun startDiscovery() {
        attemptedPorts.clear()
        adbRepository.connectDiscovery.startDiscovery(AdbServiceDiscovery.SERVICE_TYPE_CONNECT)
    }

    /** Stops mDNS discovery; call from onPause. Safe when idle. */
    fun stopDiscovery() {
        adbRepository.connectDiscovery.stopDiscovery()
    }

    fun updateConnectPort(port: String) {
        val digits = port.filter { it.isDigit() }
        portEditedByUser = digits.isNotEmpty()
        _connectPort.value = if (portEditedByUser) digits else
            discoveredServices.value.firstOrNull()?.port?.toString().orEmpty()
    }

    fun connect() {
        val port = _connectPort.value.toIntOrNull() ?: return
        if (!AdbServiceDiscovery.isValidAdbPort(port) || connectionState.value.isLoading) return
        attemptedPorts.add(port)
        viewModelScope.launch {
            if (!adbRepository.connect(port) && !portEditedByUser) {
                // Refresh after refusal; do not repeatedly retry a dead endpoint.
                adbRepository.connectDiscovery.stopDiscovery()
                adbRepository.connectDiscovery.startDiscovery()
            }
        }
    }

    fun disconnect() {
        autoConnectSuppressed = true
        adbRepository.disconnect()
    }

    class Factory(
        private val adbRepository: AdbRepository,
        private val selfUpdateRepository: SelfUpdateRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(adbRepository, selfUpdateRepository) as T
        }
    }
}
