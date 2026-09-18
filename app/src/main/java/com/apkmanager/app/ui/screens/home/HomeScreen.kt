package com.apkmanager.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apkmanager.app.data.ConnectionState
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.ui.components.ConnectionStatusBar
import com.apkmanager.app.util.WirelessDebuggingNavigator

import com.apkmanager.app.data.updater.UpdateStatus
import androidx.compose.ui.text.font.FontWeight

/**
 * Home screen - the main entry point of the app.
 * Shows connection status and provides navigation to all features.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    adbRepository: AdbRepository,
    selfUpdateRepository: com.apkmanager.app.repository.SelfUpdateRepository? = null,
    onNavigateToPairing: () -> Unit,
    onNavigateToInstaller: () -> Unit,
    onNavigateToPackages: () -> Unit,
    onNavigateToUpdater: () -> Unit,
    onNavigateToStore: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(adbRepository, selfUpdateRepository))
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val selfUpdateInfo by viewModel.selfUpdateInfo.collectAsStateWithLifecycle()
    val connectPort by viewModel.connectPort.collectAsStateWithLifecycle()
    val discoveredServices by viewModel.discoveredServices.collectAsStateWithLifecycle()
    val discoveryError by viewModel.discoveryError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var navMessage by remember { mutableStateOf<String?>(null) }

    // Restart mDNS discovery every time the user returns (e.g. from
    // Settings after enabling Wireless Debugging); stop when leaving so
    // listeners cannot leak or double-register.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.startDiscovery()
                Lifecycle.Event.ON_PAUSE -> viewModel.stopDiscovery()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopDiscovery()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("APK Manager") },
                actions = {
                    IconButton(onClick = {
                        WirelessDebuggingNavigator.openDeveloperSettings(context)
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Developer Options")
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

            // Discovery includes only reachable endpoints on this device.
            if (!connectionState.isConnected) {
                val discovered = discoveredServices.firstOrNull()
                Text(
                    text = when {
                        discovered != null ->
                            "Wireless Debugging: Detected on this device\n" +
                                "Host: 127.0.0.1\n" +
                                "Port: ${discovered.port}"
                        discoveryError != null ->
                            "Wireless Debugging: $discoveryError"
                        else -> "Wireless Debugging: searching…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Self-Update Banner when an APK Manager release is available
            val selfInfo = selfUpdateInfo
            if (selfInfo != null && (selfInfo.status is UpdateStatus.UpdateAvailable || selfInfo.status is UpdateStatus.Downloading || selfInfo.status is UpdateStatus.Installing)) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.RocketLaunch,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "APK Manager ${selfInfo.latestRelease?.cleanVersion ?: "Update"} Available",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Installed: v${selfInfo.currentVersionName}. Silent self-update via ADB.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        when (val status = selfInfo.status) {
                            is UpdateStatus.Downloading -> {
                                LinearProgressIndicator(
                                    progress = { status.progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Downloading: ${(status.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            is UpdateStatus.Installing -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = status.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            else -> {
                                Button(
                                    onClick = viewModel::installSelfUpdate,
                                    enabled = connectionState.isConnected,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (connectionState.isConnected) "Update App Now via ADB" else "Connect ADB to Update")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Connection controls
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
                        text = "ADB Connection",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (!connectionState.isConnected) {
                        OutlinedTextField(
                            value = connectPort,
                            onValueChange = viewModel::updateConnectPort,
                            label = { Text("Wireless Debugging Port") },
                            placeholder = { Text("e.g. 37271") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onNavigateToPairing,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pair")
                            }
                            Button(
                                onClick = viewModel::connect,
                                modifier = Modifier.weight(1f),
                                enabled = connectPort.isNotEmpty() && !connectionState.isLoading
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Connect")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                when (WirelessDebuggingNavigator.openWirelessDebugging(context)) {
                                    WirelessDebuggingNavigator.OpenResult.FAILED ->
                                        navMessage = "Could not open Settings."
                                    else -> navMessage = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Enable Wireless Debugging")
                        }
                        if (navMessage != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = navMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = viewModel::disconnect,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.LinkOff, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Disconnect")
                        }
                    }

                    if (connectionState is ConnectionState.Error) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = (connectionState as ConnectionState.Error).message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main actions
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Install APK button - primary action
            Button(
                onClick = onNavigateToInstaller,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = connectionState.isConnected
            ) {
                Icon(Icons.Default.InstallMobile, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Install APK", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // App Store button
            OutlinedButton(
                onClick = onNavigateToStore,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Default.Storefront, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("App Store", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Package Manager button
            OutlinedButton(
                onClick = onNavigateToPackages,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = connectionState.isConnected
            ) {
                Icon(Icons.Default.Apps, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Package Manager", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // GitHub App Updater button
            OutlinedButton(
                onClick = onNavigateToUpdater,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Default.Update, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("GitHub App Updater", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.weight(1f))

            // Info text
            if (!connectionState.isConnected) {
                Text(
                    text = "Enable Developer Options and Wireless Debugging on your device.\nPair first, then connect using the port shown in Wireless Debugging settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
