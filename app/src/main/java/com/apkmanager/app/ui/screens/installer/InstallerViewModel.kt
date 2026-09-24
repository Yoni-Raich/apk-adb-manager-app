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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State of an install run.
 */
sealed class InstallProgress {
    data object Idle : InstallProgress()
    data class Installing(val message: String) : InstallProgress()
    data class Success(val message: String) : InstallProgress()
    data class Failure(val message: String) : InstallProgress()
}

/**
 * ViewModel for the Installer screen. Installs run over ADB only.
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
                    size = size
                )
            } catch (e: Exception) {
                null
            }
        }
        _selectedApks.value = apks
        _installProgress.value = InstallProgress.Idle
    }

    /**
     * Installs the selected APK(s) silently over ADB.
     */
    fun install() {
        val apks = _selectedApks.value
        if (apks.isEmpty() || _installProgress.value is InstallProgress.Installing) return

        viewModelScope.launch {
            _installProgress.value = InstallProgress.Installing(
                if (apks.size == 1) "Installing ${apks[0].fileName}" else "Installing ${apks.size} parts"
            )
            val result = if (apks.size == 1) {
                adbRepository.installApk(apks[0].uri)
            } else {
                adbRepository.installSplitApks(apks.map { it.uri })
            }
            _installProgress.value = when (result) {
                is AdbInstaller.InstallResult.Success -> InstallProgress.Success("Installed")
                is AdbInstaller.InstallResult.Failure -> InstallProgress.Failure(result.error)
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
