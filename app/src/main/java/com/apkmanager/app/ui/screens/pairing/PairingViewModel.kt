package com.apkmanager.app.ui.screens.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apkmanager.app.adb.AdbServiceDiscovery
import com.apkmanager.app.adb.DiscoveredAdbService
import com.apkmanager.app.repository.AdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Pairing screen.
 */
class PairingViewModel(
    private val adbRepository: AdbRepository
) : ViewModel() {

    private val _pairingPort = MutableStateFlow("")
    val pairingPort: StateFlow<String> = _pairingPort.asStateFlow()

    private val _pairingCode = MutableStateFlow("")
    val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()

    private val _isPairing = MutableStateFlow(false)
    val isPairing: StateFlow<Boolean> = _isPairing.asStateFlow()

    private val _pairingResult = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairingResult: StateFlow<PairingState> = _pairingResult.asStateFlow()

    /**
     * Nearby `_adb-tls-pairing._tcp` advertisements. Visible only while
     * the system pairing UI is active; shown so the user can confirm the
     * device is in pairing mode. Never used as a connection port.
     */
    val pairingServices: StateFlow<List<DiscoveredAdbService>> =
        adbRepository.pairingDiscovery.services

    /** Starts pairing-mode discovery; call from onResume. Idempotent. */
    fun startPairingDiscovery() {
        adbRepository.pairingDiscovery.startDiscovery(AdbServiceDiscovery.SERVICE_TYPE_PAIRING)
    }

    /** Stops pairing-mode discovery; call from onPause. Safe when idle. */
    fun stopPairingDiscovery() {
        adbRepository.pairingDiscovery.stopDiscovery()
    }

    fun updatePort(port: String) {
        _pairingPort.value = port.filter { it.isDigit() }
    }

    fun updateCode(code: String) {
        _pairingCode.value = code.filter { it.isDigit() }.take(6)
    }

    fun startPairing() {
        val port = _pairingPort.value.toIntOrNull() ?: return
        val code = _pairingCode.value
        if (code.length < 6) return

        viewModelScope.launch {
            _isPairing.value = true
            _pairingResult.value = PairingState.InProgress

            val success = adbRepository.pair(port, code)

            _pairingResult.value = if (success) {
                PairingState.Success
            } else {
                PairingState.Failed("Pairing failed. Check port and code.")
            }
            _isPairing.value = false
        }
    }

    sealed class PairingState {
        data object Idle : PairingState()
        data object InProgress : PairingState()
        data object Success : PairingState()
        data class Failed(val error: String) : PairingState()
    }

    class Factory(private val adbRepository: AdbRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PairingViewModel(adbRepository) as T
        }
    }
}
