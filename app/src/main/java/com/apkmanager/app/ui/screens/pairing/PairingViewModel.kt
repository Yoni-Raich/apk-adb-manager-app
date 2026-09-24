package com.apkmanager.app.ui.screens.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apkmanager.app.adb.AdbServiceDiscovery
import com.apkmanager.app.repository.AdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Pairing with just the 6-digit code; the pairing port comes from mDNS.
 */
class PairingViewModel(private val adb: AdbRepository) : ViewModel() {

    sealed class PairingState {
        data object Idle : PairingState()
        data object InProgress : PairingState()
        data object Connected : PairingState()
        data object PairedOnly : PairingState()
        data class Failed(val error: String) : PairingState()
    }

    private val _code = MutableStateFlow("")
    val code: StateFlow<String> = _code.asStateFlow()

    /** Port typed by the user; overrides discovery when set. */
    private val _manualPort = MutableStateFlow("")
    val manualPort: StateFlow<String> = _manualPort.asStateFlow()

    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    /** Port advertised by the system "Pair device with pairing code" dialog, if open. */
    val discoveredPort: StateFlow<Int?> = adb.pairingDiscovery.services
        .map { it.firstOrNull()?.port }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun startDiscovery() = adb.pairingDiscovery.startDiscovery(AdbServiceDiscovery.SERVICE_TYPE_PAIRING)

    fun stopDiscovery() = adb.pairingDiscovery.stopDiscovery()

    fun updateCode(value: String) {
        _code.value = value.filter(Char::isDigit).take(6)
        if (_state.value is PairingState.Failed) _state.value = PairingState.Idle
    }

    fun updateManualPort(value: String) {
        _manualPort.value = value.filter(Char::isDigit).take(5)
    }

    fun pair() {
        val code = _code.value
        if (code.length != 6 || _state.value == PairingState.InProgress) return
        val port = _manualPort.value.toIntOrNull()?.takeIf(AdbServiceDiscovery::isValidAdbPort)
            ?: discoveredPort.value
        viewModelScope.launch {
            _state.value = PairingState.InProgress
            _state.value = when (val outcome = adb.pairAndConnect(code, port)) {
                AdbRepository.PairOutcome.Connected -> PairingState.Connected
                AdbRepository.PairOutcome.PairedNotConnected -> PairingState.PairedOnly
                is AdbRepository.PairOutcome.Failed -> PairingState.Failed(outcome.message)
            }
        }
    }

    class Factory(private val adb: AdbRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PairingViewModel(adb) as T
    }
}
