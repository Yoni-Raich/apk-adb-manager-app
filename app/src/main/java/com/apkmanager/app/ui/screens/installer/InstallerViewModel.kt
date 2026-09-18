package com.apkmanager.app.ui.screens.installer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apkmanager.app.adb.AdbInstaller
import com.apkmanager.app.data.ApkFileInfo
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.ui.components.InstallProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Installer screen.
 */
class InstallerViewModel(
    private val adbRepository: AdbRepository
) : ViewModel() {

    private val _selectedApks = MutableStateFlow<List<ApkFileInfo>>(emptyList())
    val selectedApks: StateFlow<List<ApkFileInfo>> = _selectedApks.asStateFlow()

    private val _installProgress = MutableStateFlow<InstallProgress>(InstallProgress.Idle)
    val installProgress: StateFlow<InstallProgress> = _installProgress.asStateFlow()

    /**
     * Processes selected APK file URIs.
     */
    fun onApksSelected(context: Context, uris: List<Uri>) {
        val apks = uris.mapNotNull { uri ->
            try {
                var fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "unknown.apk"
                var size = 0L

                try {
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                            if (nameIndex >= 0) {
                                val name = it.getString(nameIndex)
                                if (!name.isNullOrBlank()) fileName = name
                            }
                            if (sizeIndex >= 0) size = it.getLong(sizeIndex)
                        }
                    }
                } catch (_: Exception) {
                    // Content resolver query might fail on third-party file providers or file:// URIs
                }

                // Fallback for file:// scheme if size was not obtained from ContentResolver
                if (size == 0L && uri.scheme == "file") {
                    try {
                        val file = java.io.File(uri.path ?: "")
                        if (file.exists()) {
                            size = file.length()
                        }
                    } catch (_: Exception) {}
                }

                ApkFileInfo(
                    uri = uri,
                    fileName = fileName,
                    size = size,
                    isSplitApk = uris.size > 1
                )
            } catch (e: Exception) {
                null
            }
        }
        _selectedApks.value = apks
        _installProgress.value = InstallProgress.Idle
    }

    init {
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

    /**
     * Starts the installation process with ADB or standard Package Installer fallback.
     */
    fun install(context: Context) {
        val apks = _selectedApks.value
        if (apks.isEmpty()) return

        viewModelScope.launch {
            _installProgress.value = InstallProgress.Installing("Verifying connection...")

            val isAdbReady = adbRepository.verifyOrReconnect()
            if (isAdbReady) {
                _installProgress.value = if (apks.size == 1) {
                    InstallProgress.Installing("Installing ${apks[0].fileName} via ADB (silent)...")
                } else {
                    InstallProgress.Installing("Installing ${apks.size} split APKs via ADB (silent)...")
                }

                val result = if (apks.size == 1) {
                    adbRepository.installApk(apks[0].uri)
                } else {
                    adbRepository.installSplitApks(apks.map { it.uri })
                }

                when (result) {
                    is AdbInstaller.InstallResult.Success -> {
                        _installProgress.value = InstallProgress.Success("Installation successful!")
                    }
                    is AdbInstaller.InstallResult.Failure -> {
                        _installProgress.value = InstallProgress.Installing("ADB install failed, launching Package Installer...")
                        launchPackageInstaller(context, apks)
                    }
                }
            } else {
                _installProgress.value = InstallProgress.Installing("Launching Android Package Installer...")
                launchPackageInstaller(context, apks)
            }
        }
    }

    private suspend fun launchPackageInstaller(context: Context, apks: List<ApkFileInfo>) {
        when (val sysResult = com.apkmanager.app.installer.SystemPackageInstaller.installApkUris(context, apks.map { it.uri })) {
            is com.apkmanager.app.installer.SystemPackageInstaller.Result.Success -> {
                _installProgress.value = InstallProgress.Success("Package installer launched")
            }
            is com.apkmanager.app.installer.SystemPackageInstaller.Result.PermissionRequired -> {
                context.startActivity(sysResult.intent)
                _installProgress.value = InstallProgress.Failure("Permission required to install unknown apps. Please allow and retry.")
            }
            is com.apkmanager.app.installer.SystemPackageInstaller.Result.Failure -> {
                _installProgress.value = InstallProgress.Failure(sysResult.error)
            }
        }
    }

    /**
     * Resets the installer state.
     */
    fun reset() {
        _selectedApks.value = emptyList()
        _installProgress.value = InstallProgress.Idle
    }

    class Factory(private val adbRepository: AdbRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InstallerViewModel(adbRepository) as T
        }
    }
}
