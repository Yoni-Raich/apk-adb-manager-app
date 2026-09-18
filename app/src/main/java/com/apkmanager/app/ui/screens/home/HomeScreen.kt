package com.apkmanager.app.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apkmanager.app.data.ConnectionState
import com.apkmanager.app.data.updater.UpdateStatus
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.ui.components.*
import com.apkmanager.app.ui.theme.*
import com.apkmanager.app.util.WirelessDebuggingNavigator

/**
 * Google Play Store Home Dashboard:
 * Features Google Play Search Bar Capsule, Filter Chips,
 * Play Protect Status Card, Featured Update Banner, and App Recommendation Rows.
 */
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
    var selectedCategory by remember { mutableStateOf("For you") }
    val scrollState = rememberScrollState()

    val categories = listOf("For you", "Top Charts", "Updates", "Wireless ADB", "Installed")

    // Restart mDNS discovery on resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.startDiscovery()
                    viewModel.verifyConnection()
                }
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
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Google Play Search Bar Capsule
            GooglePlaySearchBar(
                placeholder = "Search apps, packages & updates",
                onSettingsClick = { WirelessDebuggingNavigator.openDeveloperSettings(context) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Google Play Horizontal Filter Chips
            GooglePlayFilterChips(
                categories = categories,
                selectedCategory = selectedCategory,
                onCategorySelected = { category ->
                    selectedCategory = category
                    when (category) {
                        "Top Charts" -> onNavigateToStore()
                        "Updates" -> onNavigateToUpdater()
                        "Installed" -> onNavigateToPackages()
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Self-Update Featured Banner (Google Play Hero Card)
            val selfInfo = selfUpdateInfo
            if (selfInfo != null && (selfInfo.status is UpdateStatus.UpdateAvailable || selfInfo.status is UpdateStatus.Downloading || selfInfo.status is UpdateStatus.Installing)) {
                GooglePlaySelfUpdateBanner(
                    selfInfo = selfInfo,
                    isConnected = connectionState.isConnected,
                    onUpdateClick = viewModel::installSelfUpdate
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Google Play Protect Style ADB Status Card
            ConnectionStatusBar(
                connectionState = connectionState,
                onVerifyClick = viewModel::verifyConnection
            )

            // Auto-discovered wireless debugging port hint
            if (!connectionState.isConnected) {
                val discovered = discoveredServices.firstOrNull()
                AnimatedVisibility(visible = discovered != null || discoveryError != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .then(
                                if (discovered != null) {
                                    Modifier.clickable {
                                        viewModel.updateConnectPort(discovered.port.toString())
                                    }
                                } else Modifier
                            ),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (discovered != null) Icons.Default.Sensors else Icons.Default.WifiTetheringError,
                                contentDescription = null,
                                tint = if (discovered != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (discovered != null) "Discovered Port: ${discovered.port} (Tap to auto-fill)"
                                else "Discovery: $discoveryError",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Wireless ADB Connection Management Card
            GooglePlayAdbCard(
                connectionState = connectionState,
                connectPort = connectPort,
                onPortChange = viewModel::updateConnectPort,
                onConnect = viewModel::connect,
                onDisconnect = viewModel::disconnect,
                onNavigateToPairing = onNavigateToPairing,
                navMessage = navMessage,
                onOpenWirelessSettings = {
                    when (WirelessDebuggingNavigator.openWirelessDebugging(context)) {
                        WirelessDebuggingNavigator.OpenResult.FAILED ->
                            navMessage = "Could not open Settings."
                        else -> navMessage = null
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Google Play Section: Core Features & Utilities
            GooglePlaySectionHeader(
                title = "Suggested Tools & Features",
                subtitle = "Manage APKs and on-device developer tools"
            )

            GooglePlayAppRow(
                title = "App Store",
                subtitle = "Curated open-source apps from GitHub",
                icon = {
                    Icon(
                        imageVector = Icons.Default.LocalMall,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                },
                actionLabel = "Open",
                isPrimaryAction = true,
                onActionClick = onNavigateToStore,
                onClick = onNavigateToStore
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            GooglePlayAppRow(
                title = "GitHub Updater",
                subtitle = "Automated release discovery and updates",
                icon = {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                },
                actionLabel = "Open",
                isPrimaryAction = false,
                onActionClick = onNavigateToUpdater,
                onClick = onNavigateToUpdater
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            GooglePlayAppRow(
                title = "Package Manager",
                subtitle = "Inspect, launch, or manage device packages",
                icon = {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                },
                actionLabel = "Manage",
                isPrimaryAction = false,
                onActionClick = onNavigateToPackages,
                onClick = onNavigateToPackages
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            GooglePlayAppRow(
                title = "Install APK",
                subtitle = "Select local APK or split APKS files",
                icon = {
                    Icon(
                        imageVector = Icons.Default.InstallMobile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                },
                actionLabel = "Install",
                isPrimaryAction = false,
                onActionClick = onNavigateToInstaller,
                onClick = onNavigateToInstaller
            )

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

/**
 * Featured Hero Card for App Self-Updates, styled exactly like Google Play's featured card.
 */
@Composable
private fun GooglePlaySelfUpdateBanner(
    selfInfo: com.apkmanager.app.repository.SelfUpdateRepository.SelfUpdateInfo,
    isConnected: Boolean,
    onUpdateClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.RocketLaunch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "APK Manager ${selfInfo.latestRelease?.cleanVersion ?: "Update"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (isConnected) "v${selfInfo.currentVersionName} installed • Ready for silent ADB update"
                        else "v${selfInfo.currentVersionName} installed • Package Installer ready",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (val status = selfInfo.status) {
                is UpdateStatus.Downloading -> {
                    LinearProgressIndicator(
                        progress = { status.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Downloading: ${(status.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                is UpdateStatus.Installing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = status.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                else -> {
                    Button(
                        onClick = onUpdateClick,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isConnected) "Update Now via ADB" else "Update (Package Installer)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Wireless ADB Connection Card with Google Material 3 ElevatedCard.
 */
@Composable
private fun GooglePlayAdbCard(
    connectionState: ConnectionState,
    connectPort: String,
    onPortChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onNavigateToPairing: () -> Unit,
    navMessage: String?,
    onOpenWirelessSettings: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Wireless ADB Connection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (connectionState.isConnected) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "Connected",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!connectionState.isConnected) {
                OutlinedTextField(
                    value = connectPort,
                    onValueChange = onPortChange,
                    label = { Text("Wireless Debugging Port") },
                    placeholder = { Text("e.g. 5555 or 37271") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Lan,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToPairing,
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pair", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onConnect,
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        enabled = connectPort.isNotEmpty() && !connectionState.isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Connect", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                FilledTonalButton(
                    onClick = onOpenWirelessSettings,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(Icons.Default.SettingsEthernet, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Wireless Debugging Settings", style = MaterialTheme.typography.labelLarge)
                }

                if (navMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = navMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onDisconnect,
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Disconnect ADB", fontWeight = FontWeight.Bold)
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
}
