package com.apkmanager.app.ui.screens.packages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apkmanager.app.data.PackageInfo
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.repository.PackageRepository
import com.apkmanager.app.ui.animation.pressScaleEffect
import com.apkmanager.app.ui.components.ConnectionStatusBar
import com.apkmanager.app.ui.components.PackageListItem
import com.apkmanager.app.ui.components.ShimmerCardPlaceholder
import com.apkmanager.app.ui.theme.SecondaryCyan
import com.apkmanager.app.util.AppIconImage

/**
 * Premium Package Manager screen for browsing and inspecting installed applications.
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
    val connectionState by adbRepository.connectionState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
                title = {
                    Column {
                        Text(
                            "Package Manager",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Installed device applications",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.pressScaleEffect()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::toggleSystemApps,
                        modifier = Modifier.pressScaleEffect()
                    ) {
                        Icon(
                            if (showSystemApps) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showSystemApps) "Hide system apps" else "Show system apps",
                            tint = if (showSystemApps) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = viewModel::refresh, modifier = Modifier.pressScaleEffect()) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Connection Status
            ConnectionStatusBar(
                connectionState = connectionState,
                onVerifyClick = viewModel::verifyConnection,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search by name or package...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            // Package count chip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${packages.size} packages${if (showSystemApps) " (all)" else " (user)"}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (isLoading && packages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(6) {
                        ShimmerCardPlaceholder(height = 70.dp, shape = RoundedCornerShape(18.dp))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
            isAdbConnected = connectionState.isConnected,
            onDismiss = viewModel::clearSelection,
            onUninstall = { viewModel.uninstallPackage(context, pkg.packageName) }
        )
    }
}

/**
 * Dialog showing package details with an uninstall option.
 */
@Composable
fun PackageDetailDialog(
    packageInfo: PackageInfo,
    isAdbConnected: Boolean,
    onDismiss: () -> Unit,
    onUninstall: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconImage(
                    packageName = packageInfo.packageName,
                    modifier = Modifier.size(38.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    packageInfo.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "SYSTEM APPLICATION (CANNOT BE UNINSTALLED)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!packageInfo.isSystemApp) {
                Button(
                    onClick = onUninstall,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(if (isAdbConnected) "Uninstall via ADB" else "Uninstall", fontWeight = FontWeight.Bold)
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
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
