package com.apkmanager.app.ui.screens.packages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apkmanager.app.data.PackageInfo
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.repository.PackageRepository
import com.apkmanager.app.ui.components.PackageListItem

/**
 * Package Manager screen for browsing and managing installed applications.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackagesScreen(
    adbRepository: AdbRepository,
    packageRepository: PackageRepository,
    onNavigateBack: () -> Unit,
    viewModel: PackagesViewModel = viewModel(
        factory = PackagesViewModel.Factory(adbRepository, packageRepository)
    )
) {
    val packages by viewModel.packages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val showSystemApps by viewModel.showSystemApps.collectAsStateWithLifecycle()
    val selectedPackage by viewModel.selectedPackage.collectAsStateWithLifecycle()
    val uninstallResult by viewModel.uninstallResult.collectAsStateWithLifecycle()

    // Snackbar for uninstall results
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uninstallResult) {
        uninstallResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUninstallResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Package Manager") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleSystemApps) {
                        Icon(
                            if (showSystemApps) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showSystemApps) "Hide system apps" else "Show system apps"
                        )
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search packages...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true
            )

            // Package count
            Text(
                text = "${packages.size} packages${if (showSystemApps) " (including system)" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Loading packages...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(packages, key = { it.packageName }) { pkg ->
                        PackageListItem(
                            packageInfo = pkg,
                            onUninstall = { viewModel.selectPackage(pkg) },
                            onClick = { viewModel.selectPackage(pkg) }
                        )
                    }
                }
            }
        }
    }

    // Package detail / uninstall dialog
    selectedPackage?.let { pkg ->
        PackageDetailDialog(
            packageInfo = pkg,
            onDismiss = viewModel::clearSelection,
            onUninstall = { viewModel.uninstallPackage(pkg.packageName) }
        )
    }
}

/**
 * Dialog showing package details with an uninstall option.
 */
@Composable
fun PackageDetailDialog(
    packageInfo: PackageInfo,
    onDismiss: () -> Unit,
    onUninstall: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(packageInfo.displayName)
        },
        text = {
            Column {
                DetailRow("Package", packageInfo.packageName)
                DetailRow("Version", packageInfo.versionDisplay)
                if (packageInfo.apkPath.isNotEmpty()) {
                    DetailRow("APK Path", packageInfo.apkPath)
                }
                if (packageInfo.installerPackage.isNotEmpty()) {
                    DetailRow("Installer", packageInfo.installerPackage)
                }
                if (packageInfo.targetSdk.isNotEmpty()) {
                    DetailRow("Target SDK", packageInfo.targetSdk)
                }
                if (packageInfo.firstInstallTime.isNotEmpty()) {
                    DetailRow("Installed", packageInfo.firstInstallTime)
                }
                if (packageInfo.lastUpdateTime.isNotEmpty()) {
                    DetailRow("Updated", packageInfo.lastUpdateTime)
                }
                if (packageInfo.isSystemApp) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "System application",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (!packageInfo.isSystemApp) {
                TextButton(
                    onClick = onUninstall,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Uninstall")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
