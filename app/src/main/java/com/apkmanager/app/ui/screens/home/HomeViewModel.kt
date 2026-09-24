package com.apkmanager.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apkmanager.app.data.ConnectionState
import com.apkmanager.app.data.updater.UpdateStatus
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.repository.SelfUpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Home: connection summary and APK Manager's own update.
 * Connecting itself is driven by [AdbRepository].
 */
class HomeViewModel(
    adbRepository: AdbRepository,
    private val selfUpdateRepository: SelfUpdateRepository
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = adbRepository.connectionState

    private val _selfUpdateInfo = MutableStateFlow<SelfUpdateRepository.SelfUpdateInfo?>(null)
    val selfUpdateInfo: StateFlow<SelfUpdateRepository.SelfUpdateInfo?> = _selfUpdateInfo.asStateFlow()

    init {
        viewModelScope.launch {
            _selfUpdateInfo.value = runCatching { selfUpdateRepository.checkSelfUpdate(false) }.getOrNull()
        }
    }

    fun installSelfUpdate() {
        val info = _selfUpdateInfo.value ?: return
        val asset = info.latestAsset ?: return
        viewModelScope.launch {
            _selfUpdateInfo.value = info.copy(status = UpdateStatus.Downloading(0f, 0L, asset.size))
            selfUpdateRepository.installSelfUpdate(asset) { status ->
                _selfUpdateInfo.value = _selfUpdateInfo.value?.copy(status = status)
            }
        }
    }

    class Factory(
        private val adbRepository: AdbRepository,
        private val selfUpdateRepository: SelfUpdateRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(adbRepository, selfUpdateRepository) as T
    }
}
