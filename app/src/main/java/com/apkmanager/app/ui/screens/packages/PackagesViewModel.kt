package com.apkmanager.app.ui.screens.packages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apkmanager.app.adb.AdbInstaller
import com.apkmanager.app.data.PackageInfo
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.repository.PackageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Package Manager screen.
 */
class PackagesViewModel(
    private val adbRepository: AdbRepository,
    private val packageRepository: PackageRepository
) : ViewModel() {

    val packages: StateFlow<List<PackageInfo>> = packageRepository.packages
    val isLoading: StateFlow<Boolean> = packageRepository.isLoading

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showSystemApps = MutableStateFlow(false)
    val showSystemApps: StateFlow<Boolean> = _showSystemApps.asStateFlow()

    private val _selectedPackage = MutableStateFlow<PackageInfo?>(null)
    val selectedPackage: StateFlow<PackageInfo?> = _selectedPackage.asStateFlow()

    private val _uninstallResult = MutableStateFlow<String?>(null)
    val uninstallResult: StateFlow<String?> = _uninstallResult.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            packageRepository.refresh(_showSystemApps.value)
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        packageRepository.search(query)
    }

    fun toggleSystemApps() {
        _showSystemApps.value = !_showSystemApps.value
        refresh()
    }

    fun selectPackage(pkg: PackageInfo) {
        _selectedPackage.value = pkg
    }

    fun clearSelection() {
        _selectedPackage.value = null
    }

    fun uninstallPackage(context: android.content.Context, packageName: String) {
        viewModelScope.launch {
            if (!adbRepository.verifyOrReconnect()) {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_DELETE, android.net.Uri.parse("package:$packageName"))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                _selectedPackage.value = null
                return@launch
            }
            _uninstallResult.value = when (val result = adbRepository.uninstallPackage(packageName)) {
                is AdbInstaller.InstallResult.Success -> "Uninstalled $packageName"
                is AdbInstaller.InstallResult.Failure -> result.error
            }
            _selectedPackage.value = null
            refresh()
        }
    }

    fun clearUninstallResult() {
        _uninstallResult.value = null
    }

    class Factory(
        private val adbRepository: AdbRepository,
        private val packageRepository: PackageRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PackagesViewModel(adbRepository, packageRepository) as T
        }
    }
}
