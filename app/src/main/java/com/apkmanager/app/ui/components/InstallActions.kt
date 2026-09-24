package com.apkmanager.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apkmanager.app.data.updater.UpdateStatus
import com.apkmanager.app.repository.AdbRepository
import kotlinx.coroutines.launch

/**
 * Installs only run over ADB. Returns a runner that performs the action when connected,
 * otherwise shows a snackbar whose "Connect" action opens Wireless ADB setup.
 */
@Composable
fun rememberAdbGate(
    isConnected: Boolean,
    snackbarHostState: SnackbarHostState,
    onConnect: () -> Unit
): (() -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    return { action ->
        if (isConnected) {
            action()
        } else {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = AdbRepository.ADB_REQUIRED_MESSAGE,
                    actionLabel = "Connect",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) onConnect()
            }
        }
    }
}

/** True while a download or install is running. */
val UpdateStatus.isBusy: Boolean
    get() = this is UpdateStatus.Downloading || this is UpdateStatus.Installing

/** One-line description of an in-flight or failed status, or null when there is nothing to say. */
fun UpdateStatus.progressText(): String? = when (this) {
    is UpdateStatus.Downloading -> "Downloading · ${(progress * 100).toInt()}%"
    is UpdateStatus.Installing -> "Installing…"
    is UpdateStatus.Success -> message
    is UpdateStatus.Error -> message
    UpdateStatus.Checking -> "Checking for updates…"
    else -> null
}

/** App icon with a Play-style progress ring while downloading or installing. */
@Composable
fun ProgressAppIcon(status: UpdateStatus, size: Dp, icon: @Composable () -> Unit) {
    Box(Modifier.size(size + 12.dp), contentAlignment = Alignment.Center) {
        when (status) {
            is UpdateStatus.Downloading -> CircularProgressIndicator(
                progress = { status.progress },
                modifier = Modifier.size(size + 12.dp),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeWidth = 3.dp,
                gapSize = 0.dp
            )
            is UpdateStatus.Installing -> CircularProgressIndicator(
                modifier = Modifier.size(size + 12.dp),
                color = MaterialTheme.colorScheme.tertiary,
                strokeWidth = 3.dp
            )
            else -> Unit
        }
        icon()
    }
}
