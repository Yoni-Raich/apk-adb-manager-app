package com.apkmanager.app.ui.screens.updater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apkmanager.app.data.updater.TrackedApp
import com.apkmanager.app.data.updater.UpdateStatus
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.repository.AppUpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing GitHub app updates.
 */
class UpdaterViewModel(
    private val appUpdateRepository: AppUpdateRepository,
    private val adbRepository: AdbRepository
) : ViewModel() {

    private val _trackedApps = MutableStateFlow<List<TrackedApp>>(emptyList())
    val trackedApps: StateFlow<List<TrackedApp>> = _trackedApps.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _installedApps = MutableStateFlow<List<com.apkmanager.app.data.updater.InstalledAppOption>>(emptyList())
    val installedApps: StateFlow<List<com.apkmanager.app.data.updater.InstalledAppOption>> = _installedApps.asStateFlow()

    init {
        loadAndCheckUpdates()
        loadInstalledApps()
    }

    /**
     * Loads non-system installed apps on the device for the tracking dialog picker.
     */
    fun loadInstalledApps() {
        viewModelScope.launch {
            _installedApps.value = appUpdateRepository.getInstalledUserApps()
        }
    }

    /**
     * Edits an existing repository mapping for an app.
     */
    fun editRepo(app: TrackedApp, newRepo: String) {
        viewModelScope.launch {
            appUpdateRepository.updateCustomRepo(app.packageName, app.githubRepo, newRepo)
            loadAndCheckUpdates()
        }
    }

    /**
     * Discovers installed GitHub apps and checks for new releases.
     */
    fun loadAndCheckUpdates() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val discovered = appUpdateRepository.discoverTrackedApps()
            _trackedApps.value = discovered.map { it.copy(status = UpdateStatus.Checking) }
            _isRefreshing.value = false

            // Check updates concurrently for each app
            discovered.forEach { app ->
                launch {
                    val updated = appUpdateRepository.checkUpdate(app)
                    updateAppInList(updated)
                }
            }
        }
    }

    /**
     * Checks updates for all currently tracked apps.
     */
    fun checkForUpdates() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val currentList = _trackedApps.value
            _trackedApps.value = currentList.map { it.copy(status = UpdateStatus.Checking) }

            currentList.forEach { app ->
                launch {
                    val updated = appUpdateRepository.checkUpdate(app)
                    updateAppInList(updated)
                }
            }
            _isRefreshing.value = false
        }
    }

    /**
     * Downloads and installs update for a specific app.
     */
    fun updateApp(app: TrackedApp) {
        val updateInfo = app.status as? UpdateStatus.UpdateAvailable ?: return
        viewModelScope.launch {
            val success = appUpdateRepository.installUpdate(
                app = app,
                asset = updateInfo.asset,
                adbRepository = adbRepository,
                onProgress = { newStatus ->
                    updateAppInList(app.copy(status = newStatus))
                }
            )
            if (success) {
                val newVer = updateInfo.release.cleanVersion.ifBlank { updateInfo.release.tagName }
                updateAppInList(app.copy(installedVersionName = newVer, status = UpdateStatus.UpToDate))
            }
        }
    }

    /**
     * Updates all apps that have an update available.
     */
    fun updateAll() {
        val updatable = _trackedApps.value.filter { it.status is UpdateStatus.UpdateAvailable }
        if (updatable.isEmpty()) return

        viewModelScope.launch {
            for (app in updatable) {
                val updateInfo = app.status as? UpdateStatus.UpdateAvailable ?: continue
                val success = appUpdateRepository.installUpdate(
                    app = app,
                    asset = updateInfo.asset,
                    adbRepository = adbRepository,
                    onProgress = { newStatus ->
                        updateAppInList(app.copy(status = newStatus))
                    }
                )
                if (success) {
                    val newVer = updateInfo.release.cleanVersion.ifBlank { updateInfo.release.tagName }
                    updateAppInList(app.copy(installedVersionName = newVer, status = UpdateStatus.UpToDate))
                }
            }
        }
    }

    /**
     * Adds a custom GitHub repository to track.
     */
    fun addCustomRepo(packageName: String, repo: String) {
        viewModelScope.launch {
            appUpdateRepository.addCustomRepo(packageName, repo)
            loadAndCheckUpdates()
        }
    }

    /**
     * Removes a tracked app repository.
     */
    fun removeTrackedApp(app: TrackedApp) {
        viewModelScope.launch {
            appUpdateRepository.removeCustomRepo(app.packageName, app.githubRepo)
            _trackedApps.value = _trackedApps.value.filter { it.packageName != app.packageName }
        }
    }

    private fun updateAppInList(updatedApp: TrackedApp) {
        val current = _trackedApps.value.toMutableList()
        val index = current.indexOfFirst { it.packageName == updatedApp.packageName }
        if (index != -1) {
            current[index] = updatedApp
            _trackedApps.value = current
        }
    }

    class Factory(
        private val appUpdateRepository: AppUpdateRepository,
        private val adbRepository: AdbRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UpdaterViewModel(appUpdateRepository, adbRepository) as T
        }
    }
}
