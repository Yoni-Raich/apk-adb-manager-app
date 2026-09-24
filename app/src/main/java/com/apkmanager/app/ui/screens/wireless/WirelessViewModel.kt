package com.apkmanager.app.ui.screens.wireless

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apkmanager.app.adb.AdbServiceDiscovery
import com.apkmanager.app.data.ConnectionState
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.util.WirelessDebugging
import com.apkmanager.app.util.WirelessStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Setup checklist and connection settings for Wireless ADB.
 */
class WirelessViewModel(private val adb: AdbRepository) : ViewModel() {

    /** The first thing the user still has to do, in dependency order. */
    enum class Step { DEVELOPER_OPTIONS, WIFI, WIRELESS_DEBUGGING, PAIR, CONNECT }

    val connectionState: StateFlow<ConnectionState> = adb.connectionState
    val status: StateFlow<WirelessStatus> = adb.wirelessStatus
    val isPaired: StateFlow<Boolean> = adb.isPaired.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val autoConnect: StateFlow<Boolean> = adb.autoConnectEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val keepWirelessOn: StateFlow<Boolean> = adb.keepWirelessDebuggingOn.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val discoveredPort: StateFlow<Int?> = adb.connectDiscovery.services
        .map { it.firstOrNull()?.port }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun currentStep(status: WirelessStatus, paired: Boolean): Step = when {
        !status.developerOptions -> Step.DEVELOPER_OPTIONS
        !status.wifi -> Step.WIFI
        !status.wirelessDebugging -> Step.WIRELESS_DEBUGGING
        !paired -> Step.PAIR
        else -> Step.CONNECT
    }

    /** Turns Wireless Debugging on directly when allowed. Returns false if Settings must be opened. */
    fun enableWirelessDebugging(context: Context): Boolean {
        val enabled = WirelessDebugging.enable(context)
        adb.refreshWirelessStatus()
        return enabled
    }

    fun refresh() = adb.refreshWirelessStatus()

    fun connect(port: Int) {
        if (!AdbServiceDiscovery.isValidAdbPort(port) || connectionState.value.isLoading) return
        viewModelScope.launch { adb.connect(port) }
    }

    fun retry() {
        viewModelScope.launch { adb.reconnect() }
    }

    fun disconnect() = adb.disconnect()

    fun forgetPairing() {
        viewModelScope.launch { adb.clearPairingState() }
    }

    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch { adb.setAutoConnect(enabled) }
    }

    fun setKeepWirelessOn(enabled: Boolean) {
        viewModelScope.launch { adb.setKeepWirelessDebuggingOn(enabled) }
    }

    class Factory(private val adb: AdbRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = WirelessViewModel(adb) as T
    }
}
