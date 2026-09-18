package com.apkmanager.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apkmanager.app.ui.theme.StatusConnected
import com.apkmanager.app.ui.theme.StatusError

/**
 * State for the installation progress indicator.
 */
sealed class InstallProgress {
    data object Idle : InstallProgress()
    data class Installing(val message: String = "Installing...") : InstallProgress()
    data class Success(val message: String = "Installation successful") : InstallProgress()
    data class Failure(val message: String = "Installation failed") : InstallProgress()
}

/**
 * Animated progress indicator for installation operations.
 */
@Composable
fun InstallProgressIndicator(
    progress: InstallProgress,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (progress) {
            is InstallProgress.Idle -> { /* Nothing to show */ }

            is InstallProgress.Installing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = progress.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is InstallProgress.Success -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    modifier = Modifier.size(48.dp),
                    tint = StatusConnected
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = progress.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = StatusConnected
                )
            }

            is InstallProgress.Failure -> {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Error",
                    modifier = Modifier.size(48.dp),
                    tint = StatusError
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = progress.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = StatusError
                )
            }
        }
    }
}
