package com.apkmanager.app.ui.screens.installer

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.ui.components.ConnectionStatusBar
import com.apkmanager.app.ui.components.InstallProgress
import com.apkmanager.app.ui.components.InstallProgressIndicator

/**
 * Installer screen for selecting and installing APK files.
 * Supports both single APK and split APK installations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallerScreen(
    adbRepository: AdbRepository,
    onNavigateBack: () -> Unit,
    incomingUris: List<Uri> = emptyList(),
    onIncomingUrisHandled: () -> Unit = {},
    viewModel: InstallerViewModel = viewModel(factory = InstallerViewModel.Factory(adbRepository))
) {
    val selectedApks by viewModel.selectedApks.collectAsStateWithLifecycle()
    val installProgress by viewModel.installProgress.collectAsStateWithLifecycle()
    val connectionState by adbRepository.connectionState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Process incoming APK URIs received from external apps (ACTION_VIEW / ACTION_SEND)
    LaunchedEffect(incomingUris) {
        if (incomingUris.isNotEmpty()) {
            incomingUris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
            }
            viewModel.onApksSelected(context, incomingUris)
            onIncomingUrisHandled()
        }
    }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            // Take persistent permission for the URIs
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
            }
            viewModel.onApksSelected(context, uris)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Install APK") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Connection Status
            ConnectionStatusBar(connectionState = connectionState)
            Spacer(modifier = Modifier.height(16.dp))
            if (selectedApks.isEmpty() && installProgress is InstallProgress.Idle) {
                // Empty state - prompt to select APK
                Spacer(modifier = Modifier.weight(1f))

                Icon(
                    Icons.Default.FileOpen,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Select APK files to install",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "You can select multiple files for split APK installation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        filePickerLauncher.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Browse Files", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.weight(1f))
            } else {
                // APK selected - show info and install button
                if (selectedApks.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = if (selectedApks.size == 1) "Selected APK" else "Selected ${selectedApks.size} APKs (Split)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            LazyColumn(
                                modifier = Modifier.heightIn(max = 200.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(selectedApks) { apk ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.InsertDriveFile,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = apk.fileName,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = apk.sizeDisplay,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Install progress
                InstallProgressIndicator(progress = installProgress)

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                when (installProgress) {
                    is InstallProgress.Idle -> {
                        Button(
                            onClick = viewModel::install,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            enabled = selectedApks.isNotEmpty() && connectionState.isConnected
                        ) {
                            Icon(Icons.Default.InstallMobile, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (connectionState.isConnected) "Install" else "ADB Not Connected")
                        }
                        if (!connectionState.isConnected) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connect Wireless Debugging")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                filePickerLauncher.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Select Different APK")
                        }
                    }
                    is InstallProgress.Installing -> {
                        // Show cancel-like option (just reset)
                    }
                    is InstallProgress.Success, is InstallProgress.Failure -> {
                        Button(
                            onClick = viewModel::reset,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Install Another")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Back to Home")
                        }
                    }
                }
            }
        }
    }
}
